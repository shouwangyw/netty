package com.yw.netty.example.tomcat;

import com.yw.netty.example.servnet.Servnet;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TomcatServer {
    /**
     * key为servnet的简单类名，value为对应servnet实例
     */
    private Map<String, Servnet> nameToServnetMap = new ConcurrentHashMap<>();
    /**
     * key为servnet的简单类名，value为对应servnet类的全限定性类名
     */
    private Map<String, String> nameToClassNameMap = new HashMap<>();
    private String basePackage;

    public TomcatServer(String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * 启动Tomcat
     */
    public void start() throws Exception {
        // 加载指定包中所有的Servnet的类名
        cacheClassName(basePackage);
        // 启动server
        runServer();
    }

    private void cacheClassName(String basePackage) {
        // 获取指定包中的资源
        URL resource = this.getClass().getClassLoader()
                // com.yw.netty.webapp  =>  com/yw/netty/webapp
                .getResource(basePackage.replaceAll("\\.", "/"));
        // 若目录中没有任何资源，则直接结束
        if (resource == null) {
            return;
        }
        // 将URL资源转换为File资源
        File dir = new File(resource.getFile());
        // 遍历指定包及其子孙包中的所有文件，查找所有.class文件
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                // 若当前遍历的file为目录，则递归调用当前方法
                cacheClassName(basePackage + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String simpleClassName = file.getName().replace(".class", "").trim();
                // key为简单类名，value为全限定性类名
                nameToClassNameMap.put(simpleClassName.toLowerCase(), basePackage + "." + simpleClassName);
            }
        }
        // System.out.println(nameToClassNameMap);
    }

    private void runServer() throws Exception {
        NioEventLoopGroup parentGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup childGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(parentGroup, childGroup)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new TomcatServerHandler(nameToServnetMap, nameToClassNameMap));
                        }
                    });
            ChannelFuture future = bootstrap.bind(8888).sync();
            System.out.println("Netty-Tomcat启动成功，监听端口号8888");
            future.channel().closeFuture().sync();
        } finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
}