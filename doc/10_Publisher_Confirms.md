## 发布者确认

发布者确认是实现可靠发布的RabbitMQ扩展。在通道上启用发布者确认后，代理将异步确认客户端发布的消息，这意味着它们已在服务器端处理。

### 不同的发布者确认策略

#### 1. 在channel上启用发布者确认

发布者确认是AMQP 0.9.1协议的RabbitMQ扩展，因此默认情况下未启用它们。发布者确认是使用confirmSelect方法在通道级别启用的：

```java
Channel channel = connection.createChannel();
channel.confirmSelect();
```

必须在希望使用发布者确认的每个频道上调用此方法。确认仅应启用一次，而不是对每个已发布的消息都启用。

#### 策略1：分别发布消息

让我们从使用确认发布的最简单方法开始，即发布消息并同步等待其确认：

```java
while (thereAreMessagesToPublish()) {
    byte[] body = ...;
    BasicProperties properties = ...;
    channel.basicPublish(exchange, queue, properties, body);
    // uses a 5 second timeout
    channel.waitForConfirmsOrDie(5_000);
}
```

在前面的示例中，我们像往常一样发布一条消息，并等待通过Channel＃waitForConfirmsOrDie（long）方法对其进行确认。确认消息后，该方法立即返回。如果未在超时时间内确认该消息或该消息没有被确认（这意味着代理出于某种原因无法处理该消息），则该方法将引发异常。异常的处理通常包括记录错误消息和/或重试发送消息。

不同的客户端库有不同的方式来同步处理发布者的确认，因此请确保仔细阅读所使用客户端的文档。

此技术非常简单，但也有一个主要缺点：由于消息的确认会阻止所有后续消息的**发布**，因此它会**大大降低发布速度**。这种方法不会提供每秒超过数百条已发布消息的吞吐量。但是，对于某些应用程序来说这可能已经足够了。

#### 发布者确认异步吗？

我们在一开始提到代理程序以异步方式确认发布的消息，但是在第一个示例中，代码同步等待直到消息被确认。客户端实际上异步接收确认，并相应地取消阻止对waitForConfirmsOrDie的调用 。可以将waitForConfirmsOrDie视为依赖于幕后异步通知的同步帮助器。



#### 策略2：批量发布消息

为了改进前面的示例，我们可以发布一批消息，并等待整个批次被确认。以下示例使用了100个批次：

```java
int batchSize = 100;
int outstandingMessageCount = 0;
while (thereAreMessagesToPublish()) {
    byte[] body = ...;
    BasicProperties properties = ...;
    channel.basicPublish(exchange, queue, properties, body);
    outstandingMessageCount++;
    if (outstandingMessageCount == batchSize) {
        ch.waitForConfirmsOrDie(5_000);
        outstandingMessageCount = 0;
    }
}
if (outstandingMessageCount > 0) {
    ch.waitForConfirmsOrDie(5_000);
}
```



与等待确认单个消息相比，等待一批消息被确认可以极大地提高吞吐量（对于远程RabbitMQ节点，这最多可以达到20-30次）。缺点之一是我们不知道发生故障时到底出了什么问题，因此我们可能必须将整个批处理保存在内存中，以记录有意义的内容或重新发布消息。而且该解决方案仍然是同步的，因此它阻止了消息的发布。



### 策略3：处理发布者异步确认

代理异步确认已发布的消息，只需在客户端上注册一个回调即可收到这些确认的通知：

```java
Channel channel = connection.createChannel();
channel.confirmSelect（）;
channel.addConfirmListener((sequenceNumber,multiple)-> {
    //确认消息时的代码
},（sequenceNumber，多个）-> {
    //短信不存在时的代码
});
```

有两种回调：一种用于确认消息，另一种用于小消息（代理可以认为丢失的消息）。每个回调都有2个参数：

- 序列号：标识已确认或未确认消息的数字。我们很快将看到如何将其与已发布的消息相关联。
- 整数：这是一个布尔值。如果为false，则仅确认/否定一条消息；如果为true，则将确认/无序列号较低或相等的所有消息。

可以 在发布之前使用Channel＃getNextPublishSeqNo（）获得序列号：

```java
int sequenceNumber = channel.getNextPublishSeqNo());
ch.basicPublish(exchange, queue, properties, body);
```

将消息与序列号关联的一种简单方法是使用映射。假设我们要发布字符串，因为它们很容易变成要发布的字节数组。这是一个使用映射将发布序列号与消息的字符串主体相关联的代码示例：

```java
ConcurrentNavigableMap <Long，String> excellentConfirms = new ConcurrentSkipListMap <>();
// ...确认回调的代码将稍后发布
String body = “ ...” ;
excellentConfirms.put(channel.getNextPublishSeqNo(),body);
channel.basicPublish(exchange, queue, properties, body.getBytes());
```

现在，发布代码使用地图跟踪出站邮件。我们需要在确认到达时清理此地图，并做一些类似在消息不足时记录警告的操作：

```java
ConcurrentNavigableMap<Long, String> outstandingConfirms = new ConcurrentSkipListMap<>();
ConfirmCallback cleanOutstandingConfirms = (sequenceNumber, multiple) -> {
    if (multiple) {
        ConcurrentNavigableMap<Long, String> confirmed = outstandingConfirms.headMap(
          sequenceNumber, true
        );
        confirmed.clear();
    } else {
        outstandingConfirms.remove(sequenceNumber);
    }
};

channel.addConfirmListener(cleanOutstandingConfirms, (sequenceNumber, multiple) -> {
    String body = outstandingConfirms.get(sequenceNumber);
    System.err.format(
      "Message with body %s has been nack-ed. Sequence number: %d, multiple: %b%n",
      body, sequenceNumber, multiple
    );
    cleanOutstandingConfirms.handle(sequenceNumber, multiple);
});
// ... publishing code
```

前面的示例包含一个回调，当确认到达时，该回调将清除地图。请注意，此回调处理单个和多个确认。确认到达时使用此回调（作为Channel＃addConfirmListener的第一个参数 ）。缺少邮件的回调将检索邮件正文并发出警告。然后，它重新使用前一个回调来清理未完成确认的映射（无论消息是已确认还是未确认，都必须删除映射中的相应条目。）

#### 如何跟踪未完成的确认？

我们的示例使用ConcurrentNavigableMap跟踪未完成的确认。由于以下几个原因，此数据结构很方便。它允许轻松地将序列号与消息相关联（无论消息数据是什么），还可以轻松清除直到给定序列ID的条目（以处理多个确认/提示）。最后，它支持并发访问，因为在客户端库拥有的线程中调用了确认回调，该线程应与发布线程保持不同。

除了使用复杂的映射实现之外，还有其他跟踪未完成确认的方法，例如使用简单的并发哈希映射和变量来跟踪发布序列的下限，但是它们通常涉及更多且不属于教程。



综上所述，处理发布者异步确认通常需要执行以下步骤：

- 提供一种将发布序列号与消息相关联的方法。

- 在通道上注册一个确认侦听器，以便在发布者确认/通知到达后执行相应的操作（例如记录或重新发布未确认的消息）时收到通知。序列号与消息的关联机制在此步骤中可能还需要进行一些清洗。

- 在发布消息之前跟踪发布序列号。

  

> #### 重新发布nack-ed消息？
>
> 从相应的回调中重新发布一个nack-ed消息可能很诱人，但是应该避免这种情况，因为确认回调是在不应执行通道操作的I / O线程中分派的。更好的解决方案是将消息放入由发布线程轮询的内存队列中。诸如ConcurrentLinkedQueue之类的类 将是在确认回调和发布线程之间传输消息的理想选择。



### 总结

在某些应用程序中，确保将发布的消息发送给代理可能是必不可少的。发布者确认是RabbitMQ功能，可以帮助满足此要求。发布者确认本质上是异步的，但也可以同步处理它们。没有确定的方法可以实现发布者确认，这通常归结为应用程序和整个系统中的约束。典型的技术有：

- 单独发布消息，同步等待确认：简单，但吞吐量非常有限。
- 批量发布消息，同步等待批量确认：简单，合理的吞吐量，但很难推断出什么时候出了问题。
- 异步处理：最佳性能和资源使用，在发生错误的情况下可以很好地控制，但是可以正确实施。



### 代码

策略1代码：

```java
public class PublisherConfirms {

    private static final int MESSAGE_COUNT = 50_000;

    /**
     * 获取连接
     * @return 连接
     */
    public static Connection createConnection() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);
        return connectionFactory.newConnection();
    }

    /**
     * 发送消息
     */
    public static void publishMessagesIndividually() throws Exception {
        Connection connection = createConnection();
        Channel channel = connection.createChannel();

        String queue = UUID.randomUUID().toString();
        channel.queueDeclare(queue, false, false, true, null);

        channel.confirmSelect();
        long start = System.nanoTime();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            String body = String.valueOf(i);
            // 发送消息
            channel.basicPublish("", queue, null, body.getBytes());
            // 等待确认
            channel.waitForConfirmsOrDie(5_000);
        }
        long end = System.nanoTime();
        System.out.format("发布 %,d 消息 , 耗时 %,d ms", MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
    }

    public static void main(String[] args) throws Exception {
        publishMessagesIndividually();
    }
}

```

策略2代码:

```java
public class PublisherConfirms1 {

    private static final int MESSAGE_COUNT = 50_000;

    // 创建连接
    public static Connection createConnection() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);
        return connectionFactory.newConnection();
    }


    // 批量发送消息
    public static void publishMessagesInBatch() throws Exception {
        Connection connection = createConnection();
        Channel channel = connection.createChannel();

        String queue = UUID.randomUUID().toString();
        channel.queueDeclare(queue, false, false, true, null);

        channel.confirmSelect();

        int batchSize = 100;
        int outstandingMessageCount = 0;

        long start = System.nanoTime();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            String body = String.valueOf(i);
            channel.basicPublish("", queue, null, body.getBytes());
            outstandingMessageCount++;
            // 批量发送,等达到了批量值,在等待确认
            if (outstandingMessageCount == batchSize) {
                channel.waitForConfirmsOrDie(5_000);
                outstandingMessageCount = 0;
            }
        }

        if (outstandingMessageCount > 0) {
            channel.waitForConfirmsOrDie(5_000);
        }
        long end = System.nanoTime();
        System.out.format("发布 %,d 消息 , 耗时 %,d ms", MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
    }

    public static void main(String[] args) throws Exception {
        publishMessagesInBatch();
    }
}

```



策略3代码:

```java
public class PublisherConfirms2 {

    private static final int MESSAGE_COUNT = 50_000;

    // 创建连接
    public static Connection createConnection() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);
        return connectionFactory.newConnection();
    }


    // 发送消息
    public static void handlePublishConfirmsAsynchronously() throws Exception {
        Connection connection = createConnection();
        Channel channel = connection.createChannel();

        String queue = UUID.randomUUID().toString();
        channel.queueDeclare(queue, false, false, true, null);

        channel.confirmSelect();

        ConcurrentNavigableMap<Long, String> outstandingConfirms = new ConcurrentSkipListMap<>();
        // 异步确认
        ConfirmCallback cleanOutstandingConfirms = (sequenceNumber, multiple) -> {
            if (multiple) {
                ConcurrentNavigableMap<Long, String> confirmed = outstandingConfirms.headMap(sequenceNumber, true);
                confirmed.clear();
            } else {
                outstandingConfirms.remove(sequenceNumber);
            }
        };

        channel.addConfirmListener(cleanOutstandingConfirms, (sequenceNumber, multiple) -> {
            String body = outstandingConfirms.get(sequenceNumber);
            System.err.format(
                    "Message with body %s has been nack-ed. Sequence number: %d, multiple: %b%n",
                    body, sequenceNumber, multiple
            );
            cleanOutstandingConfirms.handle(sequenceNumber, multiple);
        });

        long start = System.nanoTime();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            String body = String.valueOf(i);
            outstandingConfirms.put(channel.getNextPublishSeqNo(), body);
            channel.basicPublish("", queue, null, body.getBytes());
        }

        if (!waitUntil(Duration.ofSeconds(60), () -> outstandingConfirms.isEmpty())) {
            throw new IllegalStateException("All messages could not be confirmed in 60 seconds");
        }

        long end = System.nanoTime();
        System.out.format("发布 %,d 消息 , 耗时 %,d ms", MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());

    }

    public static boolean waitUntil(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        int waited = 0;
        while (!condition.getAsBoolean() && waited < timeout.toMillis()) {
            Thread.sleep(100L);
            waited = +100;
        }
        return condition.getAsBoolean();
    }


    public static void main(String[] args) throws Exception {
        handlePublishConfirmsAsynchronously();
    }
}

```

运行结果如下:

```
策略1:
发布 50,000 消息 , 耗时 6,222 ms
策略2:
发布 50,000 消息 , 耗时 1,920 ms
策略3:
发布 50,000 消息 , 耗时 1,541 ms
```



如果客户端和服务器位于同一台计算机上，则计算机上的输出应看起来相似。单独发布消息的效果不理想，但是与批处理发布相比，异步处理的结果令人失望。

Publisher确认非常依赖于网络，因此我们最好尝试使用远程节点，因为客户端和服务器通常不在生产中的同一台计算机上，所以这是更现实的选择。 可以轻松地将PublisherConfirms.java更改为使用非本地节点：

```java
public static Connection createConnection() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(远程连接地址);
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);
        return connectionFactory.newConnection();
    }
```

运行时间：

```
策略1:
发布 50,000 消息 , 耗时 231,222 ms
策略2:
发布 50,000 消息 , 耗时 7,920 ms
策略3:
发布 50,000 消息 , 耗时 4,541 ms
```

我们看到现在单独发布的效果非常好。但是，通过客户端和服务器之间的网络，批处理发布和异步处理现在的执行方式类似，对于发布者确认的异步处理来说，这是一个很小的优势。

请记住，批量发布很容易实现，但是在发布者否定确认的情况下，不容易知道哪些消息无法发送给代理。处理发布者确认异步涉及更多的实现，但是提供更好的粒度和更好地控制在处理发布的消息时执行的操作。


### 代码所在目录

[src/main/java/publisherconfirms](../src/main/java/publisherconfirms)