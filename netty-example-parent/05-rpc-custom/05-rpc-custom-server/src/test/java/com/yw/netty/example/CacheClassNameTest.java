package com.yw.netty.example;

import com.yw.netty.example.server.RpcServer;
import org.junit.Test;

public class CacheClassNameTest {
    @Test
    public void testRpcServerPublish() throws Exception {
        RpcServer.getInstance().publish("com.yw.netty.example.service");
    }
}
