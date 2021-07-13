package com.yw.netty.example;

import com.yw.netty.example.client.RpcProxy;
import com.yw.netty.example.service.PayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author yangwei
 */
@SpringBootApplication
public class RpcConsumer implements CommandLineRunner {
    @Autowired
    private RpcProxy rpcProxy;

    public static void main(String[] args) {
        SpringApplication.run(RpcConsumer.class, args);
    }

    @Override
    public void run(String... args) {
        PayService service = rpcProxy.create(PayService.class, "WeChat");
        for (int i = 0; i < 10; i++) {
            System.out.println(service.pay("9.9元"));
        }
    }
}
