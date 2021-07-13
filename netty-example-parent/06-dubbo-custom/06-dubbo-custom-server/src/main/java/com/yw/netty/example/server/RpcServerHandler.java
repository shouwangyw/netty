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
    private String basePackage;

    public RpcServerHandler(Map<String, Object> registerMap, String basePackage) {
        this.registerMap = registerMap;
        this.basePackage = basePackage;
    }

    /**
     * 解析Client发送来的msg，然后从registerMap注册表中查看是否有对应的接口
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Invocation msg) throws Exception {
        Object result = "没有该提供者，或没有该方法";

        String key = getKey(msg);
        if (registerMap.containsKey(key)) {
            Object provider = registerMap.get(key);
            result = provider.getClass().getMethod(msg.getMethodName(), msg.getParamTypes())
                    .invoke(provider, msg.getParamValues());
        }
        ctx.writeAndFlush(result);
        ctx.close();
    }

    private String getKey(Invocation msg) {
        // 获取业务接口的简单类名
        String interfaceName = msg.getClassName();
        String simpleInterName = interfaceName.substring(interfaceName.lastIndexOf('.') + 1);
        String prefix = msg.getPrefix();

        for (String rKey : registerMap.keySet()) {
            if (rKey.endsWith(simpleInterName) || rKey.endsWith(prefix + simpleInterName)) {
                return rKey;
            }
        }
        return basePackage + "." + prefix + simpleInterName;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}