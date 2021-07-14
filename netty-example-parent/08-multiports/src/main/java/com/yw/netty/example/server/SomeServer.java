package com.yw.netty.example.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yangwei
 */
public class SomeServer {
    private NioEventLoopGroup parentGroup = new NioEventLoopGroup(1);
    private NioEventLoopGroup childGroup = new NioEventLoopGroup();
    private List<ChannelFuture> futures = new ArrayList<>();

    public void start(List<Integer> ports) throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(parentGroup, childGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 服务端需要接收客户端数据，所以这里要添加解码器
                        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                        // 服务端需要想客户端发送数据，所以这里要添加编码器
                        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new SomeServerHandler());
                    }
                });
        for (Integer port : ports) {
            // 生成一个future
            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("服务器正在启动中。。。");
            future.addListener(f -> {
                if (f.isSuccess()) {
                    System.out.println("服务器已启动，监听的端口为：" + port);
                }
            });
            // 将所有生成的future添加到集合中
            futures.add(future);
        }
    }

    /**
     * 关闭所有Channel
     */
    public void closeAllChannel() {
        System.out.println("所有Channel已经全部关闭");
        for (ChannelFuture future : futures) {
            future.channel().close();
        }
        parentGroup.shutdownGracefully();
        childGroup.shutdownGracefully();
    }
}