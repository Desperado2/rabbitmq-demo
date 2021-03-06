package publishsubscribe.topic;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * 生产者
 **/
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
