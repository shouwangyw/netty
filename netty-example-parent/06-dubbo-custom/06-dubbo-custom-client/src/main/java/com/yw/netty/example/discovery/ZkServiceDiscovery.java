package com.yw.netty.example.discovery;

import com.yw.netty.example.constant.ZkConstant;
import com.yw.netty.example.loadbalance.RandomLoadBalance;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.List;

/**
 * @author yangwei
 */
public class ZkServiceDiscovery implements ServiceDiscovery {
    private volatile CuratorFramework client;
    private List<String> providers;

    private ZkServiceDiscovery() {
        if (null == client) {
            synchronized (this) {
                if (null == client) {
                    // 创建并初始化zk客户端
                    client = CuratorFrameworkFactory.builder()
                            // 指定要连接的zk集群地址
                            .connectString(ZkConstant.ZK_CLUSTER)
                            // 指定连接超时
                            .connectionTimeoutMs(10000)
                            // 指定会话超时
                            .sessionTimeoutMs(4000)
                            // 指定重试策略：每重试一次，sleep 1秒，最多重试10次
                            .retryPolicy(new ExponentialBackoffRetry(1000, 10))
                            .build();
                    // 启动zk客户端
                    client.start();
                }
            }
        }
    }

    public static ZkServiceDiscovery getInstance() {
        return new ZkServiceDiscovery();
    }

    @Override
    public String discovery(String serviceName) throws Exception {
        // 要获取的服务在zk中的路径
        String servicePath = ZkConstant.ZK_DUBBO_ROOT_PATH + "/" + serviceName;
        // 获取到指定节点的所有子节点列表，并为该节点设置子节点列表更改的watcher监听
        providers = client.getChildren()
                // 一旦指定节点的子节点列表发生变更，则马上再获取所有子节点列表
                .usingWatcher((CuratorWatcher) evt -> providers = client.getChildren().forPath(servicePath))
                .forPath(servicePath);
        if (providers.size() == 0) {
            return null;
        }
        // 负载均衡选择一个主机
        return RandomLoadBalance.getInstance().choose(providers);
    }
}