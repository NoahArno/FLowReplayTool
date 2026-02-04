package com.flowreplay.proxy;

import com.flowreplay.core.model.RequestData;
import com.flowreplay.core.model.ResponseData;
import com.flowreplay.core.model.TrafficRecord;
import com.flowreplay.core.recorder.TrafficRecorder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * TCP代理处理器
 */
public class TcpProxyHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TcpProxyHandler.class);
    private final String targetHost;
    private final int targetPort;
    private final TrafficRecorder recorder;
    private final String protocolParser;

    private Channel outboundChannel;
    private ByteBuf requestBuffer;
    private ByteBuf responseBuffer;
    private long startTime;

    public TcpProxyHandler(String targetHost, int targetPort,
                           TrafficRecorder recorder, String protocolParser) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.recorder = recorder;
        this.protocolParser = protocolParser;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        startTime = System.currentTimeMillis();
        requestBuffer = Unpooled.buffer();
        responseBuffer = Unpooled.buffer();

        final Channel inboundChannel = ctx.channel();

        // 连接到目标服务器
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
         .channel(ctx.channel().getClass())
         .handler(new TargetServerHandler(ctx.channel()))
         .option(ChannelOption.AUTO_READ, false);

        ChannelFuture f = b.connect(targetHost, targetPort);
        outboundChannel = f.channel();

        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                inboundChannel.read();
                outboundChannel.read();
            } else {
                inboundChannel.close();
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf data)) {
            return;
        }

        if (outboundChannel != null && outboundChannel.isActive()) {
            if (requestBuffer != null) {
                requestBuffer.writeBytes(data, data.readerIndex(), data.readableBytes());
            }
            outboundChannel.writeAndFlush(data).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    ctx.channel().read();
                    outboundChannel.read();
                } else {
                    future.channel().close();
                }
            });
            return;
        }

        data.release();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
        // 录制流量
        recordTraffic();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in TCP proxy handler", cause);
        closeOnFlush(ctx.channel());
    }

    private void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void recordTraffic() {
        try {
            byte[] requestBytes = new byte[requestBuffer != null ? requestBuffer.readableBytes() : 0];
            if (requestBuffer != null) {
                requestBuffer.readBytes(requestBytes);
            }

            byte[] responseBytes = new byte[responseBuffer != null ? responseBuffer.readableBytes() : 0];
            if (responseBuffer != null) {
                responseBuffer.readBytes(responseBytes);
            }

            RequestData requestData = new RequestData(
                protocolParser,
                targetHost + ":" + targetPort,
                Map.of(),
                requestBytes,
                Map.of()
            );

            long duration = System.currentTimeMillis() - startTime;
            ResponseData responseData = new ResponseData(
                0,
                Map.of(),
                responseBytes,
                duration,
                Map.of()
            );

            Map<String, Object> metadata = Map.of(
                "targetHost", targetHost,
                "targetPort", targetPort,
                "protocol", protocolParser,
                "duration", duration
            );

            TrafficRecord record = new TrafficRecord(
                UUID.randomUUID().toString(),
                "SOCKET",
                Instant.now(),
                requestData,
                responseData,
                metadata
            );

            recorder.record(record);
        } catch (Exception e) {
            log.error("Failed to record TCP traffic", e);
        } finally {
            if (requestBuffer != null) {
                requestBuffer.release();
            }
            if (responseBuffer != null) {
                responseBuffer.release();
            }
        }
    }

    /**
     * 目标服务器响应处理器
     */
    private class TargetServerHandler extends ChannelInboundHandlerAdapter {
        private final Channel inboundChannel;

        TargetServerHandler(Channel inboundChannel) {
            this.inboundChannel = inboundChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf data)) {
                return;
            }

            if (responseBuffer != null) {
                responseBuffer.writeBytes(data, data.readerIndex(), data.readableBytes());
            }
            inboundChannel.writeAndFlush(data).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    ctx.channel().read();
                    inboundChannel.read();
                } else {
                    future.channel().close();
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            closeOnFlush(inboundChannel);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Exception in target server handler", cause);
            closeOnFlush(ctx.channel());
        }
    }
}
