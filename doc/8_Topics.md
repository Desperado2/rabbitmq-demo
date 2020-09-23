## 主题路由器

在上一个教程中，我们改进了日志记录系统。代替使用仅能进行虚拟广播的扇出交换机，我们使用直接交换机，并有选择地接收日志的可能性。

尽管使用直接交换改进了我们的系统，但是它仍然存在局限性-它不能基于多个条件进行路由。

在我们的日志记录系统中，我们可能不仅要根据严重性订阅日志，还要根据发出日志的源订阅日志。您可能从[syslog](http://en.wikipedia.org/wiki/Syslog) unix工具中了解了这个概念，该 工具根据严重性（info / warn / crit ...）和工具（auth / cron / kern ...）路由日志。

这将为我们提供很大的灵活性-我们可能只想听听来自'cron'的严重错误，也可以听听'kern'的所有日志。

为了在日志系统中实现这一点，我们需要学习更复杂的主题交换器。



### 主题交换器

发送到主题交换机的消息不能具有任意的 routing_key-它必须是单词列表，以点分隔。这些词可以是任何东西，但通常它们指定与消息相关的某些功能。一些有效的路由关键示例：```“ stock.usd.nyse ”```，```“ nyse.vmw ”```，```“ quick.orange.rabbit ”```。路由关键字中可以包含任意多个单词，最多255个字节。

绑定密钥也必须采用相同的形式。主题交换背后的逻辑类似于直接交换的逻辑 -使用特定路由键发送的消息将被传递到所有与匹配的绑定键绑定的队列。但是，绑定键有两个重要的特殊情况：

- *（星号）可以代替一个单词。

- ＃（哈希）可以替代零个或多个单词。

  在一个示例中最容易解释这一点：

![img](https://www.rabbitmq.com/img/tutorials/python-five.png)

在此示例中，我们将发送所有描述动物的消息。将使用包含三个词（两个点）的路由密钥发送消息。路由键中的第一个单词将描述速度，第二个是颜色，第三个是物种：```“ <speed>.<color>.<species> ”```。

我们创建了三个绑定：Q1与绑定键“ * .orange.* ” 绑定，Q2与“ \*.\*.rabbit ”和“ lazy.＃ ” 绑定。

这些绑定可以总结为：

- Q1对所有橙色动物都感兴趣。
- 第2季想听听有关兔子的一切，以及有关懒惰动物的一切。

因此，路由键为quick.orange.rabbit的消息将发送到Q1和Q2，路由键为quick.orange.fox的消息将发送到Q1，路由键为lazy.brown.fox的消息将发送到Q2。路由键为lazy.pink.rabbit的消息将发送到Q2，但是注意，它只会到达Q2一次，尽管它匹配了两个绑定键。路由键为quick.brown.fox的消息因为不和任意的绑定键匹配，所以将会被丢弃。

如果有人手一抖发了个lazy.orange.male.rabbit这种四个单词的，这个怎么办呢？ 由于它和lazy.#匹配，因此将发送到Q2。

如果我们违约并发送一个或四个单词的消息，例如“ 橙色 ”或“ quick.orange.male.rabbit ”，会发生什么？好吧，这些消息将不匹配任何绑定，并且将会丢失。

主题路由器功能强大，可以像其他路由器一样使用。

当队列用“ ＃ ”（哈希）绑定键绑定时，它将接收所有消息，而与路由键无关，就像在扇出交换中一样。

当在绑定中不使用特殊字符“ * ”（星号）和“ ＃ ”（哈希）时，主题交换的行为就像直接的一样。



**direct路由器类似于sql语句中的精确查询；topic 路由器有点类似于sql语句中的模糊查询。**



### 实现

在生产者之中，将channel指定为topic类型的交换器。

```java
channel.exchangeDeclare(EXCHANGE_NAME, "topic");
```

在发送消息的时候指定路由键

```java
channel.basicPublish(EXCHANGE_NAME, routingKey, null, msg.getBytes());
```

在消费者之中，将channel指定为topic类型的交换器。

```java
 channel.exchangeDeclare(EXCHANGE_NAME, "topic");
```

在发送者之中绑定路由键

```java
channel.queueBind(queueName, EXCHANGE_NAME, '*.orange.*');
```



### 整合

生产者代码如下:

```java
public class TopicProducer {

    public static void main(String[] args) throws IOException, TimeoutException {
        // 1. 创建exchange的名字
        String exchangeName = "topicExchange";

        // 2. 创建连接工厂
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 3. 创建连接
        Connection connection = connectionFactory.newConnection();

        // 4. 创建通道
        Channel channel = connection.createChannel();

        // 5. 为通道声明exchange和exchange类型
        /*
         * exchangeDeclare(String exchangeName, String exchangeType)
         * exchangeName 交换机名称
         * exchangeType 交换机类型
         */
        channel.exchangeDeclare(exchangeName,"topic");

        // 6. 发送消息到指定的exchange，队列指定为空，由exchange根据情况判断需要发送到哪些队列
        /*
         *  basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body)
         *  exchange 指定交换机 不指定则默认(AMQP default交换机)
         *  routingKey 路由键，即发布消息时，该消息的路由键是什么
         *  props 消息属性
         *  body 消息体
         */
        String msg = "quick.orange.rabbit";
        channel.basicPublish(exchangeName,"quick.orange.rabbit",null, msg.getBytes());
        System.out.println("消息发送成功：" + msg);

        msg = "lazy.orange.elephant";
        channel.basicPublish(exchangeName,"lazy.orange.elephant",null, msg.getBytes());
        System.out.println("消息发送成功:" + msg);

        msg = "quick.orange.fox";
        channel.basicPublish(exchangeName,"quick.orange.fox",null, msg.getBytes());
        System.out.println("消息发送成功: "+ msg);

        msg = "lazy.brown.fox";
        channel.basicPublish(exchangeName,"lazy.brown.fox",null, msg.getBytes());
        System.out.println("消息发送成功: "+ msg);

        msg = "lazy.pink.rabbit";
        channel.basicPublish(exchangeName,"lazy.pink.rabbit",null, msg.getBytes());
        System.out.println("消息发送成功: "+ msg);

        msg = "quick.brown.fox";
        channel.basicPublish(exchangeName,"quick.brown.fox",null, msg.getBytes());
        System.out.println("消息发送成功: "+ msg);

        msg = "quick.orange.male.rabbit";
        channel.basicPublish(exchangeName,"quick.orange.male.rabbit",null, msg.getBytes());
        System.out.println("消息发送成功: "+ msg);

        msg = "lazy.orange.male.rabbit";
        channel.basicPublish(exchangeName,"lazy.orange.male.rabbit",null, msg.getBytes());
        System.out.println("消息发送成功: "+ msg);

        // 7. 关闭channel和连接
        channel.close();
        connection.close();
    }
}

```

消费者1代码如下:

```java
public class TopicConsumer {

    public static void main(String[] args) throws IOException, TimeoutException {

        // 1. 创建exchange的名字
        String exchangeName = "topicExchange";

        // 2. 创建连接工厂
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 3. 创建连接
        Connection connection = connectionFactory.newConnection();

        // 4. 创建通道
        Channel channel = connection.createChannel();

        // 5. 为通道声明exchange和exchange类型
        /*
         * exchangeDeclare(String exchangeName, String exchangeType)
         * exchangeName 交换机名称
         * exchangeType 交换机类型
         */
        channel.exchangeDeclare(exchangeName,"topic");

        // 6. 创建随机名字的queue
        String queue = channel.queueDeclare().getQueue();

        // 7. 创建exchange和queue的绑定关系
        /*
         * queueBind(String queueName, String exchangeName, String routingKey)
         * queueName 队列名称
         * exchangeName 交换机名称
         * routingKey 路由键
         */
        String[] bindingKeys = { "*.orange.*"};
        for (String bindingKey : bindingKeys) {
            channel.queueBind(queue, exchangeName, bindingKey);
            System.out.println("绑定路由键:" + bindingKey);
        }

        // 8. 创建消费者
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body);
                System.out.println("消费到消息:" + msg);
            }
        };

        // 9. 消费消息
        /*
         * String basicConsume(String queue, boolean autoAck, Consumer callback )
         * queue  队列名字，即要从哪个队列中接收消息
         * autoAck 是否自动确认，默认true
         * callback 消费者，即谁接收消息
         */
        channel.basicConsume(queue,true,consumer);
    }
}

```

消费者2代码如下:

```java
public class TopicConsumer1 {

    public static void main(String[] args) throws IOException, TimeoutException {

        // 1. 创建exchange的名字
        String exchangeName = "topicExchange";

        // 2. 创建连接工厂
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 3. 创建连接
        Connection connection = connectionFactory.newConnection();

        // 4. 创建通道
        Channel channel = connection.createChannel();

        // 5. 为通道声明exchange和exchange类型
        /*
         * exchangeDeclare(String exchangeName, String exchangeType)
         * exchangeName 交换机名称
         * exchangeType 交换机类型
         */
        channel.exchangeDeclare(exchangeName,"topic");

        // 6. 创建随机名字的queue
        String queue = channel.queueDeclare().getQueue();

        // 7. 创建exchange和queue的绑定关系
        /*
         * queueBind(String queueName, String exchangeName, String routingKey)
         * queueName 队列名称
         * exchangeName 交换机名称
         * routingKey 路由键
         */
        String[] bindingKeys = { "*.*.rabbit","lazy.#"};
        for (String bindingKey : bindingKeys) {
            channel.queueBind(queue, exchangeName, bindingKey);
            System.out.println("绑定路由键:" + bindingKey);
        }

        // 8. 创建消费者
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body);
                System.out.println("消费到消息:" + msg);
            }
        };

        // 9. 消费消息
        /*
         * String basicConsume(String queue, boolean autoAck, Consumer callback )
         * queue  队列名字，即要从哪个队列中接收消息
         * autoAck 是否自动确认，默认true
         * callback 消费者，即谁接收消息
         */
        channel.basicConsume(queue,true,consumer);
    }
}

```



### 运行结果

生产者运行结果:

消息发送成功：quick.orange.rabbit
消息发送成功:lazy.orange.elephant
消息发送成功: quick.orange.fox
消息发送成功: lazy.brown.fox
消息发送成功: lazy.pink.rabbit
消息发送成功: quick.brown.fox
消息发送成功: quick.orange.male.rabbit
消息发送成功: lazy.orange.male.rabbit



消费者1运行结果:

绑定路由键:*.orange.*
消费到消息:quick.orange.rabbit
消费到消息:lazy.orange.elephant
消费到消息:quick.orange.fox

消费者2运行结果:

绑定路由键:*.*.rabbit
绑定路由键:lazy.#
消费到消息:quick.orange.rabbit
消费到消息:lazy.orange.elephant
消费到消息:lazy.brown.fox
消费到消息:lazy.pink.rabbit
消费到消息:lazy.orange.male.rabbit



### 代码所在目录
[src/main/java/publishsubscribe/topic](../src/main/java/publishsubscribe/topic)