package com.yw.netty.example.heartbeat.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author yangwei
 */
public class HeartBeatClientHandler2 extends ChannelInboundHandlerAdapter {
    private ScheduledFuture schedule;
    private GenericFutureListener listener;
    private Bootstrap bootstrap;

    public HeartBeatClientHandler2(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 发送心跳
        sendHeartbeat(ctx.channel());
    }

    private void sendHeartbeat(Channel channel) {
        // 生成一个[1，8)的随机数作为心跳发送的时间间隔
        int interval = new Random().nextInt(7) + 1;
        System.out.println(interval + "秒后会向服务端发送心跳");

        schedule = channel.eventLoop().schedule(() -> {
            if (channel.isActive()) {
                System.out.println("PING Server");
                channel.writeAndFlush("~PING~");
            } else {
                System.out.println("与服务端之间的连接已经关闭");
                schedule.removeListener(listener);
                System.out.println("重新连接Server...");
                bootstrap.connect("localhost", 8888);
            }
        }, interval, TimeUnit.SECONDS);
        listener = (future) -> {
            // 再次发送心跳
            sendHeartbeat(channel);
        };
        // 向定时器添加监听器
        schedule.addListener(listener);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}