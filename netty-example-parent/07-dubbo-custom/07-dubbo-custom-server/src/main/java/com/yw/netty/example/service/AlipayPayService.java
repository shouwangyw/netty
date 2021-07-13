package com.yw.netty.example.service;

import org.springframework.stereotype.Service;

/**
 * @author yangwei
 */
@Service
public class AlipayPayService implements PayService {
    @Override
    public String pay(String info) {
        return "使用支付宝支付: " + info;
    }
}