package com.yw.netty.example.loadbalance;

import java.util.List;

/**
 * 负载均衡接口
 *
 * @author yangwei
 */
public interface LoadBalance {
    /**
     * 从providers中选择一个
     */
    String choose(List<String> providers);
}
