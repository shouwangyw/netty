package com.yw.netty.example.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author yangwei
 */
public class SomeServerHandler extends SimpleChannelInboundHandler<String> {
    private int counter;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        // 不用给客户端发送数据，用SimpleChannelInboundHandler比较合适，接收数据后就可以释放msg
        System.out.printf("Server端接收到的第 [%d] 个数据包: %s %n", counter++, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}