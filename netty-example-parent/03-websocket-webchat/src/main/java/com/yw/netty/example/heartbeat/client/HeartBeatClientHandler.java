package com.yw.netty.example.heartbeat.client;

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
public class HeartBeatClientHandler extends ChannelInboundHandlerAdapter {
    private ScheduledFuture schedule;
    private GenericFutureListener listener;

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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 一旦连接被关闭，则将监听器移除，这样就不会再发生心跳方法的递归调用了，以防止栈溢出
        schedule.removeListener(listener);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}