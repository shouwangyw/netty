package com.yw.netty.example.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author yangwei
 */
public class SomeSocketServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        System.out.println(ctx.channel().remoteAddress() + " : " + msg);
        TimeUnit.MILLISECONDS.sleep(500);
        ctx.channel().writeAndFlush("from server:" + UUID.randomUUID());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
