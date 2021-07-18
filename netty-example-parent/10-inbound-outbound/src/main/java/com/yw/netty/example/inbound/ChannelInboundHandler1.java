package com.yw.netty.example.inbound;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author yangwei
 */
public class ChannelInboundHandler1 extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("111 " + msg);
        ctx.fireChannelRead(msg);
    }
    /**
     * 当channel被激活时会触发该方法的执行，一个pipeline中只会执行第一个被重写的channelActive()
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().pipeline().fireChannelRead("Hello World 111");
    }
}
