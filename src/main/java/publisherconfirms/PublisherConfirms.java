package publisherconfirms;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BooleanSupplier;

/**
 * 发布者确认
 **/
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
