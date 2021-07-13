package com.yw.netty.example;

import com.yw.netty.example.registry.ZkRegistryCenter;
import org.junit.Test;

public class ZkRegisterTest {
    @Test
    public void testRegister() throws Exception {
        ZkRegistryCenter.getInstance()
                .register("com.yw.netty.example.service.PayService", "localhost:8888");
        System.in.read();
    }
}
