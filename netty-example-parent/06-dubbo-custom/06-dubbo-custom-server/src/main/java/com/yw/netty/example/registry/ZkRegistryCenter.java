package com.yw.netty.example.registry;

import com.yw.netty.example.constant.ZkConstant;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

/**
 * zk 注册中心
 *
 * @author yangwei
 */
public class ZkRegistryCenter implements RegistryCenter {
    private volatile CuratorFramework client;

    private ZkRegistryCenter() {
        // 使用双重检查锁初始化zk client
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
                            // 指定重试策略，每重试一次，sleep 1 秒，最多重试10次
                            .retryPolicy(new ExponentialBackoffRetry(1000, 10))
                            .build();
                    // 启动zk客户端
                    client.start();
                }
            }
        }
    }

    public static ZkRegistryCenter getInstance() {
        return new ZkRegistryCenter();
    }

    @Override
    public void register(String serviceName, String serviceAddress) throws Exception {
        // 要创建的服务名称对应的节点路径
        String servicePath = ZkConstant.ZK_DUBBO_ROOT_PATH + "/" + serviceName;
        if (client.checkExists().forPath(servicePath) == null) {
            client.create()
                    // 若父节点不存在，则会自动创建
                    .creatingParentsIfNeeded()
                    // 指定要创建的节点类型：持久节点
                    .withMode(CreateMode.PERSISTENT)
                    // 指定要创建的节点名称
                    .forPath(servicePath);
        }
        // 要创建的主机对应的节点路径
        String hostPath = servicePath + "/" + serviceAddress;
        if (client.checkExists().forPath(hostPath) == null) {
            client.create()
                    // 临时节点
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(hostPath);
        }
    }
}