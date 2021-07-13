package com.yw.netty.example.registry;

/**
 * 注册中心：定义注册接口规范
 *
 * @author yangwei
 */
public interface RegistryCenter {
    /**
     * 注册到注册中心
     *
     * @param serviceName    服务名，一般为接口名称
     * @param serviceAddress 服务提供者的ip:port
     */
    void register(String serviceName, String serviceAddress) throws Exception;
}
