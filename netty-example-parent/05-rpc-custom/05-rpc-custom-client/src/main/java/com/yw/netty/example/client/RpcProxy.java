package com.yw.netty.example.client;

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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author yangwei
 */
public class RpcProxy {
    private RpcProxy() {
    }

    public static <T> T create(Class<?> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // 若调用的是Object的方法，则直接进行本地调用
                if (Object.class.equals(method.getDeclaringClass())) {
                    return method.invoke(this, args);
                }
                // 远程调用在这里发生
                return rpcInvoke(clazz, method, args);
            }
        });
    }

    private static Object rpcInvoke(Class<?> clazz, Method method, Object[] args) throws InterruptedException {
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
            ChannelFuture future = bootstrap.connect("localhost", 8888).sync();

            // 形成远程调用的参数实例
            val invocation = new Invocation()
                    .setClassName(clazz.getName())
                    .setMethodName(method.getName())
                    .setParamTypes(method.getParameterTypes())
                    .setParamValues(args);
            // 将参数实例发送给Server
            future.channel().writeAndFlush(invocation).sync();

            future.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
        return rpcHandler.getResult();
    }
}