package com.yw.netty.example;

import com.yw.netty.example.server.RpcServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author yangwei
 */
@SpringBootApplication
public class RpcProvider implements CommandLineRunner {
    @Autowired
    private RpcServer rpcServer;

    public static void main(String[] args) {
        SpringApplication.run(RpcProvider.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String basePackage = "com.yw.netty.example.service";
        rpcServer.publish(basePackage, "192.168.254.1:9999");
    }
}