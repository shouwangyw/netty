package com.yw.netty.example.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author yangwei
 */
public class SomeClientHandler extends ChannelInboundHandlerAdapter {

    // region
    private String message = "Netty is a NIO client server framework which enables quick and easy development " +
            "of network applications such as protocol servers and clients. It greatly simplifies and streamlines " +
            "network programming such as TCP and UDP socket server. 'Quick and easy' doesn't mean that a resulting " +
            "application will suffer from a maintainability or a performance issue.Netty has been designed carefully " +
            "with the experiences earned from the implementation of a lot of protocols such as " +
            "FTP, SMTP, HTTP, and various binary and text-based legacy protocols. As a result, Netty has succeeded " +
            "to find a way to achieve ease of development, performance, stability, and flexibility without a compromise." +
            "of network applications such as protocol servers and clients. It greatly simplifies and streamlines " +
            "network programming such as TCP and UDP socket server. 'Quick and easy' doesn't mean that a resulting " +
            "application will suffer from a maintainability or a performance issue.Netty has been designed carefully " +
            "with the experiences earned from the implementation of a lot of protocols such as " +
            "FTP, SMTP, HTTP, and various binary and text-based legacy protocols. As a result, Netty has succeeded " +
            "of network applications such as protocol servers and clients. It greatly simplifies and streamlines " +
            "network programming such as TCP and UDP socket server. 'Quick and easy' doesn't mean that a resulting " +
            "application will suffer from a maintainability or a performance issue.Netty has been designed carefully " +
            "with the experiences earned from the implementation of a lot of protocols such as " +
            "FTP, SMTP, HTTP, and various binary and text-based legacy protocols. As a result, Netty has succeeded " +
            "of network applications such as protocol servers and clients. It greatly simplifies and streamlines " +
            "network programming such as TCP and UDP socket server. 'Quick and easy' doesn't mean that a resulting " +
            "application will suffer from a maintainability or a performance issue.Netty has been designed carefully " +
            "with the experiences earned from the implementation of a lot of protocols such as " +
            "FTP, SMTP, HTTP, and various binary and text-based legacy protocols. As a result, Netty has succeeded " +
            "================================================================================"; // endregion

    /**
     * ??????Channel????????????????????????????????????
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 1?????????????????????????????????2????????? ByteBuf
        ctx.writeAndFlush(message);
        ctx.writeAndFlush(message);

//        // 2?????????????????????????????????100????????? ByteBuf
//        for (int i = 0; i < 100; i++) {
//            // ????????????????????? 12 ?????????
//            ctx.writeAndFlush("Hello Netty!");
//        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}