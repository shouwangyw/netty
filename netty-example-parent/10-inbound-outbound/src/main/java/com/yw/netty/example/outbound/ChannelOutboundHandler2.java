package com.yw.netty.example.outbound;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.TimeUnit;

/**
 * @author yangwei
 */
public class ChannelOutboundHandler2 extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        System.out.println("2222 " + msg);
        ctx.write(msg, promise);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {

        TimeUnit.SECONDS.sleep(1);
        ctx.channel().write("Hello World 2222");

        // ctx.executor().schedule(new Runnable() {
        //     @Override
        //     public void run() {
        //         ctx.channel().write("Hello World 2222");
        //     }
        // }, 1, TimeUnit.SECONDS);
    }
}








