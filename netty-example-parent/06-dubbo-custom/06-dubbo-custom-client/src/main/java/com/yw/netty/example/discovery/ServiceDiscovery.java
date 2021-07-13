package com.yw.netty.example.discovery;

/**
 * 服务发现接口
 *
 * @author yangwei
 */
public interface ServiceDiscovery {
    /**
     * @param serviceName 服务名称，即接口名
     * @return 返回经过负载均衡后的server
     */
    String discovery(String serviceName) throws Exception;
}