package com.yw.netty.example.loadbalance;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡
 *
 * @author yangwei
 */
public class RandomLoadBalance implements LoadBalance {
    private RandomLoadBalance() {}
    private static final RandomLoadBalance INSTANCE = new RandomLoadBalance();

    public static RandomLoadBalance getInstance() {
        return INSTANCE;
    }

    @Override
    public String choose(List<String> providers) {
        return providers.get(new Random().nextInt(providers.size()));
    }
}