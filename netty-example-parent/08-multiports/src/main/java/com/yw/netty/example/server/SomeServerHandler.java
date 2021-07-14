package com.yw.netty.example.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author yangwei
 */
public class SomeServerHandler extends ChannelInboundHandlerAdapter {
    /**
     * @param ctx 表示当前处理器节点
     * @param msg 表示来自客户端数据
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel channel = ctx.channel();
        // 显示客户端ip与port
        System.out.println(channel.remoteAddress() + ", " + msg);
        // 显示当前Server的ip与port，就是当前监听的端口号
        System.out.println(channel.localAddress() + ", " + msg);
        // 向客户端发送数据
        channel.writeAndFlush("From Server : " + channel.localAddress() + "，" + UUID.randomUUID());
        TimeUnit.SECONDS.sleep(1);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}