package com.yw.netty.example.socket.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author yangwei
 */
public class SomeServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 显示客户端发送来的数据
        System.out.println(ctx.channel().remoteAddress() + ", " + msg);
        // 给客户端发送数据
        ctx.channel().writeAndFlush("From Server : " + UUID.randomUUID());
        TimeUnit.SECONDS.sleep(1);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
