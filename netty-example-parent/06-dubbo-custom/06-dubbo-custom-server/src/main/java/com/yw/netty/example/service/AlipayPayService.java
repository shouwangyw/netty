package com.yw.netty.example.service;

/**
 * @author yangwei
 */
public class AlipayPayService implements PayService {
    @Override
    public String pay(String info) {
        return "使用支付宝支付: " + info;
    }
}