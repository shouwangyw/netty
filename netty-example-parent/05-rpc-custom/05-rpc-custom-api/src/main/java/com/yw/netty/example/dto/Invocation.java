package com.yw.netty.example.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author yangwei
 */
@Data
@Accessors(chain = true)
public class Invocation implements Serializable {
    /**
     * 接口名，即微服务名称
     */
    private String className;
    /**
     * 要远程调用的方法名
     */
    private String methodName;
    /**
     * 参数类型列表
     */
    private Class<?>[] paramTypes;
    /**
     * 参数值列表
     */
    private Object[] paramValues;
}
