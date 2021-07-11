package com.yw.netty.example.server;

import com.yw.netty.example.dto.Invocation;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Map;

/**
 * @author yangwei
 */
public class RpcServerHandler extends SimpleChannelInboundHandler<Invocation> {
    private Map<String, Object> registerMap;

    public RpcServerHandler(Map<String, Object> registerMap) {
        this.registerMap = registerMap;
    }

    /**
     * 解析Client发送来的msg，然后从registerMap注册表中查看是否有对应的接口
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Invocation msg) throws Exception {
        Object result = "没有该提供者，或没有该方法";
        if (registerMap.containsKey(msg.getClassName())) {
            System.out.println("Receive msg : " + msg);
            Object provider = registerMap.get(msg.getClassName());
            result = provider.getClass().getMethod(msg.getMethodName(), msg.getParamTypes())
                    .invoke(provider, msg.getParamValues());
        }
        ctx.writeAndFlush(result);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}