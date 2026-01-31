package com.flowreplay.proxy;

import com.flowreplay.core.recorder.TrafficRecorder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP代理服务器
 */
public class HttpProxyServer {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyServer.class);
    private final int port;
    private final String targetHost;
    private final int targetPort;
    private final TrafficRecorder recorder;

    public HttpProxyServer(int port, String targetHost, int targetPort, TrafficRecorder recorder) {
        this.port = port;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.recorder = recorder;
    }

    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(new HttpServerCodec());
                     ch.pipeline().addLast(new HttpObjectAggregator(10 * 1024 * 1024));
                     ch.pipeline().addLast(new HttpProxyHandler(targetHost, targetPort, recorder));
                 }
             });

            log.info("Starting HTTP proxy server on port {}", port);
            ChannelFuture f = b.bind(port).sync();
            log.info("HTTP proxy server started successfully");
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
