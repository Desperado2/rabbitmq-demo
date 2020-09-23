## 远程过程调用

在第二个教程中，我们学习了如何使用**工作队列**在多个工作人员之间分配耗时的任务。

但是，如果我们需要在远程计算机上运行功能并等待结果怎么办？好吧，那是一个不同的故事。这种模式通常称为**“远程过程调用”**或 **“RPC”**。

在本教程中，我们将使用RabbitMQ构建RPC系统：客户端和可伸缩RPC服务器。由于我们没有值得分配的耗时任务，因此我们将创建一个虚拟RPC服务，该服务返回斐波那契数。

### 客户端

为了说明如何使用RPC服务，我们将创建一个简单的客户端类。它将公开一个名为call的方法，该方法 发送RPC请求并阻塞，直到收到答案为止：

```java
RpcClient rpcClient = new RpcClient();
String result = rpcClient.call("6");
System.out.println("fib(6)=" + result);
```

### RPC说明
尽管RPC是计算中非常普遍的模式，但它经常受到批评。当程序员不知道函数调用是本地的还是缓慢的RPC时，就会出现问题。这样的混乱会导致系统变幻莫测，并给调试增加了不必要的复杂性。滥用RPC可能会导致无法维护的意大利面条代码，而不是简化软件。

牢记这一点，请考虑以下建议：

- 确保明显的是哪个函数调用是本地的，哪个是远程的。
- 记录您的系统。明确组件之间的依赖关系。
- 处理错误案例。RPC服务器长时间关闭后，客户端应如何反应？

如有疑问，请避免使用RPC。如果可以的话，应该使用异步管道-代替类似RPC的阻塞，将结果异步推送到下一个计算阶段。

### 回调队列

通常，通过RabbitMQ进行RPC很容易。客户端发送请求消息，服务器发送响应消息。为了接收响应，我们需要发送带有请求的“回调”队列地址。我们可以使用默认队列（在Java客户端中是唯一的）。让我们尝试一下：

```java
String uuid = UUID.randomUUID().toString();
String queueName = channel.queueDeclare().getQueue();
//后续，服务端根据"replyTo"来指定将返回信息写入到哪个队列
//后续，服务端根据关联标识"correlationId"来指定返回的响应是哪个请求的
AMQP.BasicProperties properties = new AMQP.BasicProperties().builder().replyTo(queueName).correlationId(uuid).build();
channel.basicPublish("",RpcServer.QUEUE_NAME,properties,msg.getBytes());
```

### 消息属性

AMQP 0-9-1协议预定义了消息附带的14个属性集。除以下属性外，大多数属性很少使用：

- deliveryMode：将消息标记为持久性（值为2）或瞬态（任何其他值）。您可能还记得第二个教程中的此属性。
- contentType：用于描述编码的mime类型。例如，对于经常使用的JSON编码，将此属性设置为application / json是一个好习惯。
- replyTo：通常用于命名回调队列。
- relatedId：用于将RPC响应与请求相关联。

### 关联Id

在上面介绍的方法中，我们建议为每个RPC请求创建一个回调队列。这是相当低效的，但是幸运的是，有一种更好的方法-让我们为每个客户端创建一个回调队列。

这引起了一个新问题，在该队列中收到响应后，尚不清楚响应属于哪个请求。那就是当使用correlationId属性时 。我们将为每个请求将其设置为唯一值。稍后，当我们在回调队列中收到消息时，我们将查看该属性，并基于此属性将响应与请求进行匹配。如果我们看到一个未知的 correlationId值，则可以安全地丢弃该消息-它不属于我们的请求。

您可能会问，为什么我们应该忽略回调队列中的未知消息，而不是因错误而失败？这是由于服务器端可能出现竞争状况。尽管可能性不大，但RPC服务器可能会在向我们发送答案之后但在发送请求的确认消息之前死亡。如果发生这种情况，重新启动的RPC服务器将再次处理该请求。这就是为什么在客户端上我们必须妥善处理重复的响应，并且理想情况下RPC应该是幂等的。



### 整合

![img](https://www.rabbitmq.com/img/tutorials/python-six.png)

我们的RPC将像这样工作：

- 对于RPC请求，客户端发送一条消息，该消息具有两个属性： replyTo（设置为仅为该请求创建的匿名互斥队列）和correlationId（设置为每个请求的唯一值）。
- 该请求被发送到rpc_queue队列。
- RPC工作程序（又名：服务器）正在等待该队列上的请求。出现请求时，它会使用replyTo字段中的队列来完成工作，并将消息和结果发送回客户端。
- 客户端等待答复队列中的数据。出现消息时，它将检查correlationId属性。如果它与请求中的值匹配，则将响应返回给应用程序。

#### 服务器端

服务器代码非常简单：

- 像往常一样，我们首先建立连接，通道并声明队列。
- 我们可能要运行多个服务器进程。为了将负载平均分配到多个服务器，我们需要在channel.basicQos中设置 prefetchCount设置。
- 我们使用basicConsume访问队列，在队列中我们以对象（DeliverCallback）的形式提供回调，该回调将完成工作并将响应发送回去。

我们的RPC服务端的代码如下:

```java
public class RpcServer {

    //RPC队列名
    public static final String QUEUE_NAME = "rpc_queue";

    //斐波那契数列，用来模拟工作任务
    public static  int fib(int num){
        if(num == 0){
            return 0;
        }
        if(num == 1){
            return 1;
        }
        return fib(num - 1) + fib(num - 2);
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        // 1. 创建连接工厂
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 2. 创建连接
        Connection connection = connectionFactory.newConnection();

        // 3. 创建通道
        Channel channel = connection.createChannel();

        // 4. channel绑定queue
        /*
         * queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
         * queue 队列名
         * durable 该队列是否需要持久化
         * exclusive 该队列是否为该通道独占的（其他通道是否可以消费该队列）
         * autoDelete 该队列不再使用的时候，是否让RabbitMQ服务器自动删除掉
         * arguments 其他参数
         */
        channel.queueDeclare(QUEUE_NAME,false,false,false,null);

        // 5. 每次只接收一个消息（任务）
        channel.basicQos(1);

        // 6. 创建消费者
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                AMQP.BasicProperties prop = new AMQP.BasicProperties().builder().correlationId(properties.getCorrelationId())
                        .build();
                String msg = new String(body);
                String resp = fib(Integer.valueOf(msg)) + "";
                System.out.println("消费到消息:" + msg);
                channel.basicPublish("", properties.getReplyTo(), prop, resp.getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        };

        // 7. 消费消息
        /*
         * String basicConsume(String queue, boolean autoAck, Consumer callback )
         * queue  队列名字，即要从哪个队列中接收消息
         * autoAck 是否自动确认，默认true
         * callback 消费者，即谁接收消息
         */
        channel.basicConsume(QUEUE_NAME,false,consumer);
    }
}

```

#### 客户端

客户端代码稍微复杂一些：

- 我们建立连接和渠道。
- 我们的调用方法发出实际的RPC请求。
- 在这里，我们首先生成一个唯一的relatedId 编号并将其保存-我们的使用者回调将使用该值来匹配适当的响应。
- 然后，我们为回复创建一个专用的排他队列并订阅它。
- 接下来，我们发布具有两个属性的请求消息： replyTo和correlationId。
- 在这一点上，我们可以坐下来等到正确的响应到达。
- 由于我们的消费者交付处理是在单独的线程中进行的，因此在响应到达之前，我们将需要一些东西来挂起主线程。使用BlockingQueue是一种可行的解决方案。在这里，我们正在创建 容量设置为1的ArrayBlockingQueue，因为我们只需要等待一个响应即可。
- 使用者的工作很简单，对于每一个消耗的响应消息，它都会检查correlationId 是否为我们要寻找的消息。如果是这样，它将响应放入BlockingQueue。
- 同时，主线程正在等待响应，以将其从BlockingQueue中获取。
- 最后，我们将响应返回给用户。

我们的RPC客户端的代码如下:

```java
public class RpcClient {

    Connection connection = null;
    Channel channel = null;
    // 回调队列：用来接受服务端的响应消息
    String queueName = "";

    //定义RpcClient
    public RpcClient() throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        connection = connectionFactory.newConnection();
        channel = connection.createChannel();
        queueName = channel.queueDeclare().getQueue();
    }


    /**
     * 正真的处理逻辑
     * @param msg 消息
     * @return 处理完成的值
     */
    public String call(String msg) throws IOException, InterruptedException {
        String uuid = UUID.randomUUID().toString();

        //后续，服务端根据"replyTo"来指定将返回信息写入到哪个队列
        //后续，服务端根据关联标识"correlationId"来指定返回的响应是哪个请求的
        AMQP.BasicProperties properties = new AMQP.BasicProperties().builder().replyTo(queueName).correlationId(uuid).build();
        // 发送消息
        /*
         *  basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body)
         *  exchange 指定交换机 不指定则默认(AMQP default交换机)
         *  routingKey 路由键，即发布消息时，该消息的路由键是什么
         *  props 消息属性
         *  body 消息体
         */
        channel.basicPublish("",RpcServer.QUEUE_NAME,properties,msg.getBytes());
        BlockingQueue<String> blockingQueue = new ArrayBlockingQueue<>(1);
        // 创建消费者
        channel.basicConsume(queueName, true, new DefaultConsumer(channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                if(properties.getCorrelationId().equals(uuid)){
                    String msg = new String(body);
                    blockingQueue.offer(msg);
                }
            }
        });
        return blockingQueue.take();
    }

    public void close() throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }


    public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {
        RpcClient rpcClient = new RpcClient();
        String result = rpcClient.call("6");
        System.out.println("fib(6)=" + result);
        rpcClient.close();
    }
}

```



### 注意

我们值客户端的回调中使用了BlockQueue阻塞队列。

```java
BlockingQueue<String> blockingQueue = new ArrayBlockingQueue<>(1);
```

原因是handleDelivery()这个方法是在子线程中运行的，这个子线程运行的时候，主线程会继续往后执行直到执行了client.close();方法而结束了。

由于主线程终止了，导致没有打印出结果。加了阻塞队列之后将主线程阻塞不执行close()方法，问题就解决了。



### 代码所在目录

[src/main/java/rpc](../src/main/java/rpc)