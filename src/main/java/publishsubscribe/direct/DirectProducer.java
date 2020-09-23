package publishsubscribe.direct;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * @Deacription TODO
 * @Author JGW
 * @Date 2020/9/23 11:00
 * @Version 1.0
 **/
public class DirectProducer {

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

        // 6. 发送消息到指定的exchange，队列指定为空，由exchange根据情况判断需要发送到哪些队列
        /*
         *  basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body)
         *  exchange 指定交换机 不指定则默认(AMQP default交换机)
         *  routingKey 路由键，即发布消息时，该消息的路由键是什么
         *  props 消息属性
         *  body 消息体
         */
        String msg = "this is a debug info";
        channel.basicPublish(exchangeName,"debug",null, msg.getBytes());
        System.out.println("debug消息发送成功：" + msg);

        msg = "this is a warning info";
        channel.basicPublish(exchangeName,"warning",null, msg.getBytes());
        System.out.println("warning消息发送成功:" + msg);

        msg = "this is a error info";
        channel.basicPublish(exchangeName,"error",null, msg.getBytes());
        System.out.println("error消息发送成功: "+ msg);
    }
}