package helloworld;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * @Deacription TODO
 * @Author JGW
 * @Date 2020/9/22 10:38
 * @Version 1.0
 **/
public class HelloConsumer {

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
        String queueName = "helloRabbitMQ";

        // 4.1. 创建队列
        /*
         * queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
         * queue 队列名
         * durable 该队列是否需要持久化
         * exclusive 该队列是否为该通道独占的（其他通道是否可以消费该队列）
         * autoDelete 该队列不再使用的时候，是否让RabbitMQ服务器自动删除掉
         * arguments 其他参数
         */
        channel.queueDeclare(queueName,true,false,false,null);

        // 5. 创建一个消费者
        Consumer queueingConsumer = new DefaultConsumer(channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String msg = new String(body);
                System.out.println("消费到了消息:"+msg);
            }
        };

        // 6. 设置channel
        /*
         * String basicConsume(String queue, boolean autoAck, Consumer callback )
         * queue  队列名字，即要从哪个队列中接收消息
         * autoAck 是否自动确认，默认true
         * callback 消费者，即谁接收消息
         */
        channel.basicConsume(queueName,true,queueingConsumer);

    }
}
