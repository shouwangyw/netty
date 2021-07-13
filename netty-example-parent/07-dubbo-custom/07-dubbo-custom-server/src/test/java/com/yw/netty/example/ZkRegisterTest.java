package com.yw.netty.example;

import com.yw.netty.example.registry.ZkRegistryCenter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RpcProvider.class)
public class ZkRegisterTest {
    @Autowired
    private ZkRegistryCenter registryCenter;

    @Test
    public void testRegister() throws Exception {
        registryCenter.register("com.yw.netty.example.service.PayService", "localhost:8888");
    }
}
