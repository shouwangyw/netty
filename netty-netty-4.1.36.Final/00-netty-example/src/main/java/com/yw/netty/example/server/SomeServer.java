package com.yw.netty.example.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

/**
 * @author yangwei
 */
public class SomeServer {
    public static void main(String[] args) throws InterruptedException {
        // parentGroup中的eventLoop将来是用于与处理客户端连接请求的parentChannel进行绑定的
        EventLoopGroup parentGroup = new NioEventLoopGroup();
        // childGroup中的eventLoop将来是用于与处理客户端读写请求的childChannel进行绑定的
        EventLoopGroup childGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(parentGroup, childGroup)
                    .channel(NioServerSocketChannel.class)
                    // .attr(AttributeKey.valueOf("depart"), "市场部")
                    // .childAttr(AttributeKey.valueOf("addr"), "北京海淀")
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {

                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new StringDecoder());
                            pipeline.addLast(new StringEncoder());
                            pipeline.addLast(new SomeSocketServerHandler());

                            // Object depart = ch.attr(AttributeKey.valueOf("depart")).get();
                            // Object addr = ch.attr(AttributeKey.valueOf("addr")).get();
                            // System.out.println("depart = " + depart);
                            // System.out.println("addr = " + addr);

                        }
                    });

            ChannelFuture future = bootstrap.bind(8888).sync();
            System.out.println("服务器已启动。。。");

            // future.channel().eventLoop().schedule(new Runnable() {
            //     @Override
            //     public void run() {
            //         System.out.println("这里添加了一个定时任务");
            //     }
            // }, 10, TimeUnit.SECONDS);


            future.channel().closeFuture().sync();
        } finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
}
