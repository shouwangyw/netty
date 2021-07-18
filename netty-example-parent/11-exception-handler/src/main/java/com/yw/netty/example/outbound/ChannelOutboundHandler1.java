package com.yw.netty.example.outbound;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;

/**
 * @author yangwei
 */
public class ChannelOutboundHandler1 extends ChannelOutboundHandlerAdapter {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("exception -- OutboundHandler111");
        ctx.fireExceptionCaught(cause);
    }
}








