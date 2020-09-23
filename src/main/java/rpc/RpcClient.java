package rpc;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

/**
 * 客户端
 **/
public class RpcClient {

    Connection connection = null;
    Channel channel = null;
    // 回调队列：用来接受服务端的响应消息
    String queueName = "";

    //定义RpcClient
    public RpcClient() throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(5672);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setHandshakeTimeout(20000);

        connection = connectionFactory.newConnection();
        channel = connection.createChannel();
        queueName = channel.queueDeclare().getQueue();
    }


    /**
     * 正真的处理逻辑
     * @param msg 消息
     * @return 处理完成的值
     */
    public String call(String msg) throws IOException, InterruptedException {
        String uuid = UUID.randomUUID().toString();

        //后续，服务端根据"replyTo"来指定将返回信息写入到哪个队列
        //后续，服务端根据关联标识"correlationId"来指定返回的响应是哪个请求的
        AMQP.BasicProperties properties = new AMQP.BasicProperties().builder().replyTo(queueName).correlationId(uuid).build();
        // 发送消息
        /*
         *  basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body)
         *  exchange 指定交换机 不指定则默认(AMQP default交换机)
         *  routingKey 路由键，即发布消息时，该消息的路由键是什么
         *  props 消息属性
         *  body 消息体
         */
        channel.basicPublish("",RpcServer.QUEUE_NAME,properties,msg.getBytes());
        BlockingQueue<String> blockingQueue = new ArrayBlockingQueue<>(1);
        // 创建消费者
        channel.basicConsume(queueName, true, new DefaultConsumer(channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                if(properties.getCorrelationId().equals(uuid)){
                    String msg = new String(body);
                    blockingQueue.offer(msg);
                }
            }
        });
        return blockingQueue.take();
    }

    public void close() throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }


    public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {
        RpcClient rpcClient = new RpcClient();
        String result = rpcClient.call("6");
        System.out.println("fib(6)=" + result);
        rpcClient.close();
    }
}
