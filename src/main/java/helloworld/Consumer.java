package helloworld;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * @Deacription TODO
 * @Author JGW
 * @Date 2020/9/22 10:38
 * @Version 1.0
 **/
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
