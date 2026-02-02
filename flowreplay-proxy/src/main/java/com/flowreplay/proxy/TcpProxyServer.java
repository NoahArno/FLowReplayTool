package com.flowreplay.proxy;

import com.flowreplay.core.recorder.TrafficRecorder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP代理服务器
 */
public class TcpProxyServer {

    private static final Logger log = LoggerFactory.getLogger(TcpProxyServer.class);
    private final int port;
    private final String targetHost;
    private final int targetPort;
    private final TrafficRecorder recorder;
    private final String protocolParser;

    public TcpProxyServer(int port, String targetHost, int targetPort,
                          TrafficRecorder recorder, String protocolParser) {
        this.port = port;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.recorder = recorder;
        this.protocolParser = protocolParser;
    }

    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childOption(ChannelOption.AUTO_READ, false)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(new TcpProxyHandler(
                         targetHost, targetPort, recorder, protocolParser));
                 }
             });

            log.info("Starting TCP proxy server on port {}", port);
            ChannelFuture f = b.bind(port).sync();
            log.info("TCP proxy server started successfully");
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
