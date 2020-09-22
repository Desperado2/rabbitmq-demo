package helloworld;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;


public class Producer {

    public static void main(String[] args) throws IOException, TimeoutException {

        // 1. 定义队列的名称
        String queueName = "helloRabbitMQ";

        // 2. 创建一个连接工厂，并进行连接的配置
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 3. 通过连接工厂创建连接
        Connection connection = connectionFactory.newConnection();

        // 4. 通过连接创建一个channel
        Channel channel = connection.createChannel();

        // 5. 为通道声明队列
        /*
         * queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
         * queue 队列名
         * durable 该队列是否需要持久化
         * exclusive 该队列是否为该通道独占的（其他通道是否可以消费该队列）
         * autoDelete 该队列不再使用的时候，是否让RabbitMQ服务器自动删除掉
         * arguments 其他参数
         */
        channel.queueDeclare(queueName,false,false,false,null);

        // 6. 发送消息
        for (int i = 0; i < 5; i++) {
            System.out.println("发送消息:" + i);
            String msg = "Hello RabbitMQ " + i;
            /*
             *  basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body)
             *  exchange 指定交换机 不指定则默认(AMQP default交换机)
             *  routingKey 路由键，即发布消息时，该消息的路由键是什么
             *  props 消息属性
             *  body 消息体
             */
            channel.basicPublish("",queueName,null,msg.getBytes());
        }

        // 7. 关闭channel和连接
        channel.close();
        connection.close();
    }
}
