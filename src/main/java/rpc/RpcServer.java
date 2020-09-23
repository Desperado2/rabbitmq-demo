package rpc;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 *服务端
 **/
public class RpcServer {

    //RPC队列名
    public static final String QUEUE_NAME = "rpc_queue";

    //斐波那契数列，用来模拟工作任务
    public static  int fib(int num){
        if(num == 0){
            return 0;
        }
        if(num == 1){
            return 1;
        }
        return fib(num - 1) + fib(num - 2);
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        // 1. 创建连接工厂
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        // 2. 创建连接
        Connection connection = connectionFactory.newConnection();

        // 3. 创建通道
        Channel channel = connection.createChannel();

        // 4. channel绑定queue
        /*
         * queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
         * queue 队列名
         * durable 该队列是否需要持久化
         * exclusive 该队列是否为该通道独占的（其他通道是否可以消费该队列）
         * autoDelete 该队列不再使用的时候，是否让RabbitMQ服务器自动删除掉
         * arguments 其他参数
         */
        channel.queueDeclare(QUEUE_NAME,false,false,false,null);

        // 5. 每次只接收一个消息（任务）
        channel.basicQos(1);

        // 6. 创建消费者
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                AMQP.BasicProperties prop = new AMQP.BasicProperties().builder().correlationId(properties.getCorrelationId())
                        .build();
                String msg = new String(body);
                String resp = fib(Integer.valueOf(msg)) + "";
                System.out.println("消费到消息:" + msg);
                channel.basicPublish("", properties.getReplyTo(), prop, resp.getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        };

        // 7. 消费消息
        /*
         * String basicConsume(String queue, boolean autoAck, Consumer callback )
         * queue  队列名字，即要从哪个队列中接收消息
         * autoAck 是否自动确认，默认true
         * callback 消费者，即谁接收消息
         */
        channel.basicConsume(QUEUE_NAME,false,consumer);
    }
}
