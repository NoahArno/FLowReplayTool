package com.flowreplay.proxy;

import com.flowreplay.core.model.TrafficRecord;
import com.flowreplay.core.recorder.TrafficRecorder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TcpProxyHandlerIntegrationTest {

    @Test
    void forwardsTcpPayloadToUpstreamAndBackToClient() throws Exception {
        int upstreamPort;
        try (ServerSocket upstream = new ServerSocket(0)) {
            upstreamPort = upstream.getLocalPort();

            Thread upstreamThread = new Thread(() -> {
                try (Socket s = upstream.accept()) {
                    byte[] buf = s.getInputStream().readNBytes(5);
                    assertEquals("hello", new String(buf, StandardCharsets.UTF_8));
                    s.getOutputStream().write("world".getBytes(StandardCharsets.UTF_8));
                    s.shutdownOutput();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            upstreamThread.setDaemon(true);
            upstreamThread.start();

            EventLoopGroup boss = new NioEventLoopGroup(1);
            EventLoopGroup worker = new NioEventLoopGroup();
            Channel serverChannel = null;
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.AUTO_READ, false)
                    .childHandler(new io.netty.channel.ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new TcpProxyHandler("localhost", upstreamPort, noopRecorder(), "raw"));
                        }
                    });

                serverChannel = b.bind(new InetSocketAddress("localhost", 0)).sync().channel();
                int proxyPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();

                try (Socket client = new Socket("localhost", proxyPort)) {
                    OutputStream os = client.getOutputStream();
                    os.write("hello".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    client.shutdownOutput();

                    InputStream is = client.getInputStream();
                    byte[] received = is.readAllBytes();
                    assertEquals("world", new String(received, StandardCharsets.UTF_8));
                }
            } finally {
                if (serverChannel != null) {
                    serverChannel.close().sync();
                }
                boss.shutdownGracefully();
                worker.shutdownGracefully();
            }
        }
    }

    private static TrafficRecorder noopRecorder() {
        return new TrafficRecorder() {
            @Override
            public void record(TrafficRecord record) {
            }

            @Override
            public void close() {
            }
        };
    }
}

