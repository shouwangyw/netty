package com.yw.netty.example.socket.client;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @author yangwei
 */
public class NioClient {
    public static void main(String[] args) throws Exception {
        // 创建客户端channel
        SocketChannel clientChannel = SocketChannel.open();
        // 指定channel使用非阻塞模式
        clientChannel.configureBlocking(false);
        // 指定要连接的Server地址
        InetSocketAddress serverAddr = new InetSocketAddress("localhost", 8888);
        // 连接Server
        if (!clientChannel.connect(serverAddr)) { // 首次连接
            while (!clientChannel.finishConnect()) { // 完成重连
                continue;
            }
        }
        if (clientChannel.isConnected()) {
            // 将消息写入到channel
            clientChannel.write(ByteBuffer.wrap("hello".getBytes()));
            System.out.println("Client 消息已发送");
        }
        System.in.read();
    }
}