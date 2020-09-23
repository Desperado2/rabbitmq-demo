> ## 前置条件
>
> 1. 已经正确的安装了rabbitMQ。
> 2. rabbitMQ安装在本机的5672端口(默认端口)，如果不是请修改代码中对应的配置

## 介绍

RabbitMQ 是一个消息代理：它用来接收消息，并将其进行转发。 你可以把它想象成一个邮局：当你把想要邮寄的邮件放到邮箱里后，邮递员就会把邮件最终送达到目的地。 在这个比喻中，RabbitMQ既代表了邮箱，也同时扮演着邮局和邮递员的角色.

RabbitMQ和邮局主要区别在于，RabbitMQ不处理纸质信件，取而代之，它接收、存储和转发的是被称为*消息*的二进制数据块。

下面介绍下通常情况下会用到的一些RabbitMQ和messaging术语：

- **生产**就是指的发送。一个用来发送消息的*生产者*程序：

  ![img](http://www.rabbitmq.com/img/tutorials/producer.png)

- **队列 **指的是存在于RabbitMQ当中的邮箱。虽然消息是在RabbbitMQ和你的应用程序之间流转，但他们是存储在*队列*中的。*队列*只收到主机内存和磁盘的限制，它实质上是存在于主机内存和硬盘中的消息缓冲区。多个*生产者*可以发送消息到同一个*队列*中，多个*消费者*也可以从同一个*队列*中接收消息。我们这样来描述一个队列：

  ![img](http://www.rabbitmq.com/img/tutorials/queue.png)

- **消费**跟接收基本是一个意思。一个*消费者*基本上就是一个用来等待接收消息的程序：

  ![img](http://www.rabbitmq.com/img/tutorials/consumer.png)

需要注意的是，生产者、消费者和代理不需要存在于同一个主机上; 实际上，大多数应用中也确实如此。另外，一个应用程序也可以同时充当生产者和消费者两个角色。



## Hello World 

### 创建项目

创建一个普通的maven项目，在项目中引入对应的amqp开发包。

``` xml
<dependency>
            <groupId>com.rabbitmq</groupId>
            <artifactId>amqp-client</artifactId>
            <version>3.6.5</version>
</dependency>
```

### 发送

![(P) -> [|||]](http://www.rabbitmq.com/img/tutorials/sending.png)

我们将消息发布者（发送者）命名为Producer.java，将消息消费者（接收者）命名为Consumer.java。发布者将会连接到RabbitMQ，发送一条消息，然后退出。

在 [Producer.java](https://github.com/Desperado2/rabbitmq-demo/blob/master/src/main/java/helloworld/Producer.java) 中, 我们来进行消息的发送。

首先创建一个连接工厂，配置相应的连接信息

```java
ConnectionFactory connectionFactory = new ConnectionFactory();
connectionFactory.setHost("127.0.0.1");
connectionFactory.setPort(5672);
connectionFactory.setVirtualHost("/");
connectionFactory.setHandshakeTimeout(20000);
```

通过连接工厂创建一个连接

```java
Connection connection = connectionFactory.newConnection();
```

通过连接创建一个Channel，用户发送消息

```java
Channel channel = connection.createChannel();
```

发送对应的消息

```java
for (int i = 0; i < 5; i++) {    
    System.out.println("发送消息:" + i);    
    String msg = "Hello RabbitMQ " + i;    
    /**  
    * basicPublish(String exchange, String routingKey, BasicProperties props,   	  byte[] body)     
    *  exchange 指定交换机 不指定则默认(AMQP default交换机)     
    *  routingKey 通过routingKey进行匹配     
    *  props 消息属性     
    *  body 消息体     
    */    
    channel.basicPublish("","test",null,msg.getBytes());
}
```

关闭相应的连接

```java
channel.close();
connection.close();
```

完整发送者的代码如下：

```java
public class Producer {

    public static void main(String[] args) throws IOException, TimeoutException {
        // 1. 创建一个连接工厂，并进行连接的配置
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);
        // 2. 通过连接工厂创建连接
        Connection connection = connectionFactory.newConnection();
        // 3. 通过连接创建一个channel
        Channel channel = connection.createChannel();
        // 4. 发送消息
        for (int i = 0; i < 5; i++) {
            System.out.println("发送消息:" + i);
            String msg = "Hello RabbitMQ " + i;
            /*
             *  basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body)
             *  exchange 指定交换机 不指定则默认(AMQP default交换机)
             *  routingKey 通过routingKey进行匹配
             *  props 消息属性
             *  body 消息体
             */
            channel.basicPublish("","test",null,msg.getBytes());
        }
        // 5. 关闭channel和连接
        channel.close();
        connection.close();
    }
}
```

### 接收

消费者会监听来自与RabbitMQ的消息。所以不同于发布者只发送一条消息，我们会让消费者保持持续运行来监听消息，并将消息打印出来。

![[|||] -> (C)](http://www.rabbitmq.com/img/tutorials/receiving.png)

此代码(在 [Consumer.java](https://github.com/Desperado2/rabbitmq-demo/blob/master/src/main/java/helloworld/Consumer.java)中) 跟`Producer`代码非常相似：

首先创建一个连接工厂，配置相应的连接信息

```java
ConnectionFactory connectionFactory = new ConnectionFactory();
connectionFactory.setHost("127.0.0.1");
connectionFactory.setPort(5672);
connectionFactory.setVirtualHost("/");
connectionFactory.setHandshakeTimeout(20000);
```

通过连接工厂创建一个连接

```java
Connection connection = connectionFactory.newConnection();
```

通过连接创建一个Channel，用户发送消息

```java
Channel channel = connection.createChannel();
```

声明创建一个queue队列

```java
// 4. 声明创建一个队列
String queueName = "test";

// 4.1. 创建队列，参数：{队列名称，是否持久化，是否独占，是否自动删除，参数}
channel.queueDeclare(queueName,true,false,false,null);
```

创建一个消费者

```java
QueueingConsumer queueingConsumer = new QueueingConsumer(channel);
```

设置channel

```
 channel.basicConsume(queueName,true,queueingConsumer);
```

消费消息

```java
while (true){
            QueueingConsumer.Delivery delivery = queueingConsumer.nextDelivery();
            String msg = new String(delivery.getBody());
            System.out.println("消费到消息:" + msg);
        }
```

完整代码如下

```java
public class Consumer {

    public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {

        // 1. 创建一个连接工厂，并进行连接的配置
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 2. 创建一个连接
        Connection connection = connectionFactory.newConnection();

        // 3. 创建一个Channel
        Channel channel = connection.createChannel();

        // 4. 声明创建一个队列
        String queueName = "test";

        // 4.1. 创建队列，参数：{队列名称，是否持久化，是否独占，是否自动删除，参数}
        channel.queueDeclare(queueName,true,false,false,null);

        // 5. 创建一个消费者
        QueueingConsumer queueingConsumer = new QueueingConsumer(channel);

        // 6. 设置channel,参数{队列名称，是否自动ack，消费者}
        channel.basicConsume(queueName,true,queueingConsumer);

        // 7. 获取消息
        while (true){
            QueueingConsumer.Delivery delivery = queueingConsumer.nextDelivery();
            String msg = new String(delivery.getBody());
            System.out.println("消费到消息:" + msg);
        }

    }
}
```

### 将它们整合到一起

分别运行两个程序。

运行消费者：

```powershell
javac Consumer.java
java Consumer
```

然后运行生产者：

```powershell
javac Producer.java
java Producer
```

消费者会接收到发布者通过RabbitMQ发送的消息，并将其打印出来。消费者会一直保持运行状态来等待接受消息（可以使用Ctrl-C 来将其停止），接下来我们可以试着在另一个终端里运行发布者代码来尝试发送消息了。


### 代码所在目录
[src/main/java/helloworld](../src/main/java/helloworld)