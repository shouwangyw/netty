package com.yw.netty.example.consumer;

import com.yw.netty.example.client.RpcProxy;
import com.yw.netty.example.service.SomeService;

/**
 * @author yangwei
 */
public class RpcConsumer {
    public static void main(String[] args) {
        SomeService service = RpcProxy.create(SomeService.class);
        System.out.println(service.hello("Netty RPC"));
        System.out.println(service.hashCode());
    }
}
