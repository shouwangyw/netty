package com.yw.netty.example.consumer;

import com.yw.netty.example.client.RpcProxy;
import com.yw.netty.example.service.PayService;

/**
 * @author yangwei
 */
public class RpcConsumer {
    public static void main(String[] args) {
        PayService service = RpcProxy.create(PayService.class, "WeChat");
        for (int i = 0; i < 10; i++) {
            System.out.println(service.pay("9.9元"));
        }
    }
}
