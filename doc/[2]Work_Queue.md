## 工作队列

工作队列--用来将耗时的任务分发给多个消费者（工作者），主要解决这样的问题：处理资源密集型任务，并且还要等他完成。有了工作队列，我们就可以将具体的工作放到后面去做，将工作封装为一个消息，发送到队列中，一个工作进程就可以取出消息并完成工作。如果启动了多个工作进程，那么工作就可以在多个进程间共享。

### 代码

生产者代码如下

```java
public class QueueProducer {

    public static void main(String[] args) throws IOException, TimeoutException {

        // 1. 定义队列名称
        String queueName = "task_queue";

        // 2. 创建连接工厂
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 3. 创建连接
        Connection connection = connectionFactory.newConnection();

        // 4. 创建channel
        Channel channel = connection.createChannel();

        // 5. 为channel设置队列
        channel.queueDeclare(queueName,false,false,false,null);

        // 6. 发送消息
        for (int i = 0; i < 6; i++) {
            String msg = "task "+i;
            channel.basicPublish("",queueName,null,msg.getBytes());
            System.out.println("消息:" + msg +"发送完毕");
        }
    }
}
```

消费者代码如下:

```java
public class QueueConsumer {

    public static void main(String[] args) throws IOException, TimeoutException {

        // 1. 定义队列名称
        String queueName = "task_queue";

        // 2. 创建连接工厂
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 3. 创建连接
        Connection connection = connectionFactory.newConnection();

        // 4. 创建channel
        Channel channel = connection.createChannel();

        // 5. channel绑定queue
        channel.queueDeclare(queueName,false,false,false,null);

        // 6. 创建消费者
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body);
                doWork(msg);
            }
        };

        // 7. 消费消息
        channel.basicConsume(queueName,true,consumer);
    }


    /**
     * 处理消息
     * @param msg 消息内容
     */
    private static void doWork(String msg){
        try {
            System.out.println("消费到消息: " + msg);
            // 通过sleep()来模拟需要耗时的操作
            if("sleep".equals(msg)){
               Thread.sleep(1000 * 60);
            }else{
                Thread.sleep(1000);
            }
            System.out.println("消息: " + msg +" 处理完毕");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

将消费者的代码在拷贝一份，这样我们就有了两个消费者。


### 运行

首先我们先分表启动两个消费者，然后在启动生产者，去看看两个消费者消费的消息情况。

生产者运行结果:

![img](work_queue/1_queue_producer_run_result.jpg)

消费者1运行结果

![img](work_queue/2_queue_consumer_run_result.jpg)

消费者2运行结果

![img](work_queue/2_queue_consumer1_run_result.jpg)

### 循环分发

我们发现，消息生产者发送了6条消息，消费者1和2分别分到了3个消息，而且是循环轮流分发到的，这种分发的方式就是循环分发。