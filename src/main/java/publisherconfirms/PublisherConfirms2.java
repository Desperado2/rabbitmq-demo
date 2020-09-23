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
