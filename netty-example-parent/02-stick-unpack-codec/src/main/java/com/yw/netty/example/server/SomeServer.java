package com.yw.netty.example.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.*;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * @author yangwei
 */
public class SomeServer {
    public static void main(String[] args) throws Exception {
        NioEventLoopGroup parentGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup childGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(parentGroup, childGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
//                            // 1、基于行分隔符的帧解码器
//                            pipeline.addLast(new LineBasedFrameDecoder(5000));
//                            pipeline.addLast(new LineBasedFrameDecoder(1000));

//                            // 2、基于分隔符的帧解码器
//                            ByteBuf delimiter = Unpooled.copiedBuffer("###--###".getBytes());
//                            pipeline.addLast(new DelimiterBasedFrameDecoder(6000, delimiter));

                            // 3、固定长度帧解码器
                            pipeline.addLast(new FixedLengthFrameDecoder(12));

//                            // 4、基于长度域的帧解码器
//                            pipeline.addLast(new LengthFieldBasedFrameDecoder(1024, 0,
//                                    4, 0, 4));
//                            pipeline.addLast(new LengthFieldPrepender(4));

                            pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                            pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
                            pipeline.addLast(new SomeServerHandler());
                        }
                    });
            ChannelFuture future = bootstrap.bind(8888).sync();
            System.out.println("==>> 服务端启动成功~~");
            future.channel().closeFuture().sync();
        } finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
}