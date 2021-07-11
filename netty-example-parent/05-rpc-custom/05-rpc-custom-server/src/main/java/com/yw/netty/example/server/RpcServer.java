package com.yw.netty.example.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yangwei
 */
public class RpcServer {
    private RpcServer() {}
    private static final RpcServer INSTANCE = new RpcServer();

    public static RpcServer getInstance() {
        return INSTANCE;
    }

    /**
     * 服务提供者注册表，key：微服务名称，即业务接口；value：业务接口实例
     */
    private Map<String, Object> registerMap = new HashMap<>(32);
    /**
     * 用于缓存服务提供者的类名
     */
    private List<String> classCache = new ArrayList<>();

    /**
     * 发布服务
     */
    public RpcServer publish(String basePackage) throws Exception {
        // 将指定包下的业务接口实现类名写入到classCache中
        cacheClassName(basePackage);
        // 将指定包下的业务接口实现类写入到注册表
        doRegister();

        return this;
    }

    private void cacheClassName(String basePackage) {
        URL resource = this.getClass().getClassLoader()
                .getResource(basePackage.replaceAll("\\.", "/"));
        if (resource == null) {
            return;
        }
        File dir = new File(resource.getFile());
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                // 若当前file为目录，则递归
                cacheClassName(basePackage + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String fileName = file.getName().replace(".class", "").trim();
                classCache.add(basePackage + "." + fileName);
            }
        }
        System.out.println(classCache);
    }

    private void doRegister() throws Exception {
        if (classCache.size() == 0) {
            return;
        }
        for (String className : classCache) {
            // 将当前遍历的类加载到内存
            Class<?> clazz = Class.forName(className);
            registerMap.put(clazz.getInterfaces()[0].getName(), clazz.newInstance());
        }
        System.out.println(registerMap);
    }

    /**
     * 启动服务
     */
    public void start() throws InterruptedException {
        NioEventLoopGroup parentGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup childGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(parentGroup, childGroup)
                    .channel(NioServerSocketChannel.class)
                    // 用于指定当Server的连接请求处理线程全被占用时，临时存放已经完成了三次握手的请求的队列的长度。
                    // 默认是50
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    // 指定使用心跳机制来保证TCP长连接的存活性
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new ObjectEncoder());
                            pipeline.addLast(new ObjectDecoder(Integer.MAX_VALUE,
                                    // null: 使用默认的类加载器
                                    ClassResolvers.cacheDisabled(null)));
                            pipeline.addLast(new RpcServerHandler(registerMap));
                        }
                    });
            ChannelFuture future = bootstrap.bind(8888).sync();
            System.out.println("==>> 服务端已启动，监听的端口为：8888 <<==");
            future.channel().closeFuture().sync();
        } finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
}