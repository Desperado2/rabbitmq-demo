package helloworld;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;


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
