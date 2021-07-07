package com.yw.netty.example.chat.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.SocketAddress;

/**
 * @author yangwei
 */
public class WebChatServerHandler extends ChannelInboundHandlerAdapter {
    /**
     * 创建一个ChannelGroup，其是一个线程安全的集合，其中存放着与当前服务器相连接的所有 Active 状态的Channel
     * GlobalEventExecutor 是一个单例、单线程的EventExecutor，是为了保证对当前group中的所有Channel的处理线程是同一个线程
     */
    private static ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /**
     * 只要有客户端Channel给当前的服务端发送了消息，那么就会触发该方法的执行
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel channel = ctx.channel();
        // 这里要实现将消息广播给所有group中的客户端Channel
        // 发送给自己的消息与发送给大家的消息是不一样的
        for (Channel ch : channelGroup) {
            if (channel != ch) {
                ch.writeAndFlush(channel.remoteAddress() + " : " + msg + "\n");
            } else {
                channel.writeAndFlush("Me: " + msg + "\n");
            }
        }
    }

    /**
     * 只要有客户端Channel与服务端连接成功就会执行这个方法
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        SocketAddress address = channel.remoteAddress();
        System.out.println(address + "---上线");

        channelGroup.writeAndFlush(address + "上线\n");
        channelGroup.add(channel);
    }

    /**
     * 只要有客户端Channel断开与服务端的连接就会执行这个方法
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        SocketAddress address = channel.remoteAddress();
        System.out.println(address + "---下线");
        channelGroup.writeAndFlush(address + "下线，在线人数：" + channelGroup.size() + "\n");

        // group中存放的都是Active状态的Channel，一旦某Channel的状态不再是Active，group会自动将其从集合中踢出，
        // 所以，下面的语句不用写。remove()方法的应用场景是，将一个Active状态的channel移出group时使用
        // group.remove(channel);
    }

    /**
     * 所有“规定动作”之外的所有事件都可以通过以下方法触发
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            String eventDes = null;
            switch (event.state()) {
                case READER_IDLE:
                    eventDes = "读空闲超时";
                    break;
                case WRITER_IDLE:
                    eventDes = "写空闲超时";
                    break;
                case ALL_IDLE:
                    eventDes = "读和写都空闲超时";
                    break;
            }
            System.out.println(eventDes);
        } else {
            // 其它事件触发
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
