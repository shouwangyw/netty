package com.yw.netty.example.chat.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

/**
 * @author yangwei
 */
public class NioChatServer {
    /**
     * 开启Server的群聊功能
     */
    public void start(ServerSocketChannel serverChannel, Selector selector) throws Exception {
        System.out.println("Chat Server Started.");
        do {
            if (selector.select(1000) == 0) {
                System.out.println("等待连接...");
                continue;
            }
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                // 处理客户端上线
                if (key.isAcceptable()) {
                    SocketChannel clientChannel = serverChannel.accept();
                    clientChannel.configureBlocking(false);
                    clientChannel.register(selector, SelectionKey.OP_READ);
                    String msg = clientChannel.getRemoteAddress() + "-上线了";
                    // 将上线通知广播给所有在线的其它client
                    sendToOtherOnlineClient(selector, clientChannel, msg);
                }
                // 处理客户端发送消息
                if (key.isReadable()) {
                    SocketChannel clientChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    clientChannel.read(buffer);
                    String msgFromClient = new String(buffer.array()).trim();
                    if (msgFromClient.length() > 0) {
                        // 获取到客户端地址
                        SocketAddress clientAddr = clientChannel.getRemoteAddress();
                        String msgToSend = clientAddr + " say:" + msgFromClient;
                        if ("88".equals(msgFromClient)) {
                            msgToSend = clientAddr + "下线";
                            // 取消当前key，即放弃其所对应的channel，将其对应的channel从selector中去掉
                            key.cancel();
                        }
                        // 将client消息广播给所有在线的其它client
                        sendToOtherOnlineClient(selector, clientChannel, msgToSend);
                    }
                }
                // 一般不用下面这两个事件，因为其不是外部条件，是自己的主动行为
//                if (key.isWritable()){}
//                if (key.isConnectable()){}

                // 删除当key，防止重复处理
                keyIterator.remove();
            }
        } while (true);
    }

    private void sendToOtherOnlineClient(Selector selector, SocketChannel self, String msg) throws IOException {
        // 遍历所有注册到selector的channel，即所有在线的client
        for (SelectionKey key : selector.keys()) {
            SelectableChannel channel = key.channel();
            // 将消息发送给所有其它client
            if (channel instanceof SocketChannel && channel != self) {
                ((SocketChannel) channel).write(ByteBuffer.wrap(msg.trim().getBytes()));
            }
        }
    }
}
