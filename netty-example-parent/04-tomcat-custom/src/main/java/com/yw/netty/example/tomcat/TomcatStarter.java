package com.yw.netty.example.tomcat;

/**
 * @author yangwei
 */
public class TomcatStarter {
    public static void main(String[] args) throws Exception {
        TomcatServer server = new TomcatServer("com.yw.netty.example.webapp");
        server.start();
    }
}