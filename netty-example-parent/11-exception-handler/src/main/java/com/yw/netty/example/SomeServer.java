package com.yw.netty.example;

import com.yw.netty.example.exception.ExceptionCaughtHandler;
import com.yw.netty.example.inbound.ChannelInboundHandler1;
import com.yw.netty.example.inbound.ChannelInboundHandler2;
import com.yw.netty.example.inbound.ChannelInboundHandler3;
import com.yw.netty.example.outbound.ChannelOutboundHandler1;
import com.yw.netty.example.outbound.ChannelOutboundHandler2;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author yangwei
 */
public class SomeServer {
    public static void main(String[] args) throws Exception {
        EventLoopGroup parentGroup = new NioEventLoopGroup();
        EventLoopGroup childGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(parentGroup, childGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new ChannelInboundHandler1());
                            pipeline.addLast(new ChannelInboundHandler2());
                            pipeline.addLast(new ChannelInboundHandler3());
                            pipeline.addLast(new ChannelOutboundHandler1());
                            pipeline.addLast(new ChannelOutboundHandler2());
                            pipeline.addLast(new ExceptionCaughtHandler());
                        }
                    });
            ChannelFuture future = bootstrap.bind(8888).sync();
            System.out.println("服务器已启动。。。");
            future.channel().closeFuture().sync();
        } finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
}
