package com.yw.netty.example.server;

/**
 * @author yangwei
 */
public class RpcStarter {
    public static void main(String[] args) throws Exception {
        String basePackage = "com.yw.netty.example.service";
//        RpcServer.getInstance().publish(basePackage, "localhost:7777");
//        RpcServer.getInstance().publish(basePackage, "127.0.0.1:8888");
        RpcServer.getInstance().publish(basePackage, "192.168.254.1:9999");
    }
}