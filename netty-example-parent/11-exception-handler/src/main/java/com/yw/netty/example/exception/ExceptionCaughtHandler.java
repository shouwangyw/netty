package com.yw.netty.example.exception;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author yangwei
 */
public class ExceptionCaughtHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ArrayIndexOutOfBoundsException) {
            System.out.println("发生ArrayIndexOutOfBoundsException异常");
        }
        // 不再向下传递
        // ctx.fireExceptionCaught(cause);
    }
}
