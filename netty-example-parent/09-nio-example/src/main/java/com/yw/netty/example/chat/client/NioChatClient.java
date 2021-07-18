package com.yw.netty.example.chat.client;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @author yangwei
 */
public class NioChatClient {
    /**
     * 开启Client的群聊功能
     */
    public void start(SocketChannel clientChannel) throws Exception {
        // 获取client自己的地址
        SocketAddress selfAddr = clientChannel.getLocalAddress();
        System.out.println(selfAddr + "，你已经成功上线了");

        // 创建一个线程用于不间断地接收来自于Server的消息
        new Thread(() -> {
            do {
                try {
                    if (!clientChannel.isConnected()) {
                        return;
                    }
                    receiveMsgFromServer(clientChannel);
                    TimeUnit.SECONDS.sleep(1);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            } while (true);
        }).start();
        /**
         * 注意，该方法不能写到前面的创建线程之前，这样会导致无法接收到来自于Server的消息，
         * 因为该方法中的Scanner是阻塞的向server发送消息
         */
        sendMsgToServer(clientChannel);
    }

    private void receiveMsgFromServer(SocketChannel clientChannel) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        clientChannel.read(buffer);
        String msg = new String(buffer.array()).trim();
        if (msg.length() > 0) {
            System.out.println(msg);
        }
    }

    private void sendMsgToServer(SocketChannel clientChannel) throws Exception {
        // 接收来自于键盘的输入
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String msg = scanner.nextLine();
            // 将消息写入到channel，其中有可能是下线请求消息88
            clientChannel.write(ByteBuffer.wrap(msg.trim().getBytes()));
            // 若消息为88，则表示当前client要下线，则将该channel关闭
            if ("88".equals(msg.trim())) {
                // 关闭客户端
                clientChannel.close();
                return;
            }
        }
    }
}
