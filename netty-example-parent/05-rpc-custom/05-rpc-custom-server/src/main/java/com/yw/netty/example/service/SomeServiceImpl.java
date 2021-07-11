package com.yw.netty.example.service;

/**
 * @author yangwei
 */
public class SomeServiceImpl implements SomeService {
    @Override
    public String hello(String name) {
        return name + " 欢迎你";
    }
}
