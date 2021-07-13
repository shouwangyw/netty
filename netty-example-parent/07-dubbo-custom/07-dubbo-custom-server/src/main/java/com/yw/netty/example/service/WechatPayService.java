package com.yw.netty.example.service;

import org.springframework.stereotype.Service;

/**
 * @author yangwei
 */
@Service
public class WechatPayService implements PayService {
    @Override
    public String pay(String info) {
        return "使用微信支付: " + info;
    }
}