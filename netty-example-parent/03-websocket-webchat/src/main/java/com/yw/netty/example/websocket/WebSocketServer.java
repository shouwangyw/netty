package com.yw.netty.example.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @author yangwei
 */
public class WebSocketServer {
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
                            // 添加Http编解码器
                            pipeline.addLast(new HttpServerCodec());
                            // 添加大块数据Chunk处理器
                            pipeline.addLast(new ChunkedWriteHandler());
                            // 添加Chunk聚合处理器
                            pipeline.addLast(new HttpObjectAggregator(4096));
                            // 添加WebSocket协议转换处理器
                            pipeline.addLast(new WebSocketServerProtocolHandler("/some"));
                            // 添加自定义处理器
                            pipeline.addLast(new TextWebSocketServerHandler());
                        }
                    });
            ChannelFuture future = bootstrap.bind(8888).sync();
            System.out.println("服务器启动成功，监听的端口号为：8888");
            future.channel().closeFuture().sync();
        } finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
}