package com.yw.netty.example.loadbalance;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡
 *
 * @author yangwei
 */
@Component
public class RandomLoadBalance implements LoadBalance {

    @Override
    public String choose(List<String> providers) {
        return providers.get(new Random().nextInt(providers.size()));
    }
}