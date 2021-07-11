package com.yw.netty.example.webapp.sub;

import com.yw.netty.example.servnet.NettyRequest;
import com.yw.netty.example.servnet.NettyResponse;
import com.yw.netty.example.servnet.Servnet;

/**
 * @author yangwei
 */
public class TwoServnet extends Servnet {
    @Override
    public void doGet(NettyRequest request, NettyResponse response) throws Exception {
        String uri = request.getUri();
        String path = request.getPath();
        String method = request.getMethod();
        String name = request.getParameter("name");

        String content = "uri = " + uri + "\n" +
                "path = " + path + "\n" +
                "method = " + method + "\n" +
                "param = " + name;
        response.write(content);
    }

    @Override
    public void doPost(NettyRequest request, NettyResponse response) throws Exception {
        doGet(request, response);
    }
}