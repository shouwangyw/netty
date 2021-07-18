package com.yw.netty.example.chat.server;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

/**
 * @author yangwei
 */
public class NioChatServerStarter {
    public static void main(String[] args) throws Exception {
        // 创建一个服务端Channel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        // 指定channel采用的是非阻塞模式
        serverChannel.configureBlocking(false);
        // 指定要监听的端口
        serverChannel.bind(new InetSocketAddress(8888));
        // 创建一个多路复用器Selector
        Selector selector = Selector.open();
        // 将channel注册到selector
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 创建支持群聊的NIO Server
        NioChatServer chatServer = new NioChatServer();
        chatServer.start(serverChannel, selector);
    }
}
