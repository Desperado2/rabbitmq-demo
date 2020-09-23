package publishsubscribe.direct;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * 消费者
 **/
public class DirectConsumer1 {

    public static void main(String[] args) throws IOException, TimeoutException {

        // 1. 创建exchange的名字
        String exchangeName = "directExchange";

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
        channel.exchangeDeclare(exchangeName,"direct");

        // 6. 创建随机名字的queue
        String queue = channel.queueDeclare().getQueue();

        // 7. 创建exchange和queue的绑定关系
        /*
         * queueBind(String queueName, String exchangeName, String routingKey)
         * queueName 队列名称
         * exchangeName 交换机名称
         * routingKey 路由键
         */
        String[] bindingKeys = { "error"};
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
