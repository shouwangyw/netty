package com.yw.netty.example.server;

/**
 * @author yangwei
 */
public class RpcStarter {
    public static void main(String[] args) throws Exception {
        RpcServer.getInstance().publish("com.yw.netty.example.service")
                .start();
    }
}