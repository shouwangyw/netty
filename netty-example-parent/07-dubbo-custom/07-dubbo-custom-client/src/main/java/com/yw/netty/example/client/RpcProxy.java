package com.yw.netty.example.client;

import com.yw.netty.example.discovery.ServiceDiscovery;
import com.yw.netty.example.dto.Invocation;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author yangwei
 */
@Component
public class RpcProxy {
    @Autowired
    private ServiceDiscovery serviceDiscovery;

    public <T> T create(Class<?> clazz, String prefix) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // 若调用的是Object的方法，则直接进行本地调用
                if (Object.class.equals(method.getDeclaringClass())) {
                    return method.invoke(this, args);
                }
                // 远程调用在这里发生
                return rpcInvoke(clazz, method, args, prefix);
            }
        });
    }

    private Object rpcInvoke(Class<?> clazz, Method method, Object[] args, String prefix) throws Exception {
        // 根据服务名称能够负载均衡查找到一个提供者主机地址
        String serviceAddress = serviceDiscovery.discovery(clazz.getName());
        if (serviceAddress == null) {
            return null;
        }

        NioEventLoopGroup group = new NioEventLoopGroup();
        RpcClientHandler rpcHandler = new RpcClientHandler();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class)
                    // Nagle算法开关
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new ObjectEncoder());
                            pipeline.addLast(new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)));
                            pipeline.addLast(rpcHandler);
                        }
                    });
            // 将serviceAddress解析为IP和Port
            String[] addresses = serviceAddress.split(":");
            String ip = addresses[0];
            int port = Integer.parseInt(addresses[1]);
            ChannelFuture future = bootstrap.connect(ip, port).sync();

            // 形成远程调用的参数实例
            val invocation = new Invocation()
                    .setClassName(clazz.getName())
                    .setMethodName(method.getName())
                    .setParamTypes(method.getParameterTypes())
                    .setParamValues(args)
                    .setPrefix(prefix);
            // 将参数实例发送给Server
            future.channel().writeAndFlush(invocation).sync();

            future.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
        return rpcHandler.getResult() + "---" + serviceAddress;
    }
}