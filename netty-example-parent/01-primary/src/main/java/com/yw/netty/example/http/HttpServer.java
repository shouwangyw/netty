package com.yw.netty.example.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author yangwei
 */
public class HttpServer {
    public static void main(String[] args) throws InterruptedException {
        // 用于接收客户端请求，并将请求发送给childGroup
        NioEventLoopGroup parentGroup = new NioEventLoopGroup();
        // 用于对请求进行进行具体的处理
        NioEventLoopGroup childGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(parentGroup, childGroup)
                    // 指定使用NIO进行异步非阻塞通信
                    // OioServerSocketChannel 用于指定使用阻塞方式通信（一般不使用）
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new HttpChannelInitializer());

            // 指定当前服务器所要绑定的端口，即要监听的端口号
            // bind()是个异步操作，为了使当前主线程在bind()成功后再向下执行，这里调用sync()
            // 实现bind()线程与主线程的同步操作
            ChannelFuture future = bootstrap.bind(8888).sync();
            System.out.println("==>> 服务端启动成功~~");
            future.channel().closeFuture().sync();
        } finally {
            // 优雅关闭
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
}
