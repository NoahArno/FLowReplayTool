package com.flowreplay.proxy;

import com.flowreplay.core.model.RequestData;
import com.flowreplay.core.model.ResponseData;
import com.flowreplay.core.model.TrafficRecord;
import com.flowreplay.core.recorder.TrafficRecorder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HTTP代理处理器
 */
public class HttpProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyHandler.class);
    private final String targetHost;
    private final int targetPort;
    private final TrafficRecorder recorder;

    // Java HttpClient受限的header列表
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
        "host", "connection", "content-length", "expect", "upgrade"
    );

    public HttpProxyHandler(String targetHost, int targetPort, TrafficRecorder recorder) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.recorder = recorder;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        long startTime = System.currentTimeMillis();
        String recordId = UUID.randomUUID().toString();

        // 提取请求数据
        RequestData requestData = extractRequestData(request);

        // 转发请求到目标服务器
        ResponseData responseData = forwardRequest(request, startTime);

        // 录制流量
        TrafficRecord record = new TrafficRecord(
            recordId,
            "HTTP",
            Instant.now(),
            requestData,
            responseData,
            Map.of("targetHost", targetHost, "targetPort", targetPort)
        );
        recorder.record(record);

        // 返回响应给客户端
        sendResponse(ctx, responseData);
    }

    private RequestData extractRequestData(FullHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        request.headers().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));

        byte[] body = new byte[request.content().readableBytes()];
        request.content().getBytes(request.content().readerIndex(), body);

        return new RequestData(
            request.method().name(),
            request.uri(),
            headers,
            body,
            Map.of()
        );
    }

    private ResponseData forwardRequest(FullHttpRequest request, long startTime) {
        try {
            // 简化实现：使用Java HttpClient转发请求
            String url = "http://" + targetHost + ":" + targetPort + request.uri();
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url));

            // 添加请求头，过滤受限的header
            request.headers().forEach(entry -> {
                String headerName = entry.getKey();
                if (!RESTRICTED_HEADERS.contains(headerName.toLowerCase())) {
                    builder.header(headerName, entry.getValue());
                }
            });

            // 设置请求体
            byte[] body = new byte[request.content().readableBytes()];
            request.content().getBytes(request.content().readerIndex(), body);
            builder.method(request.method().name(),
                java.net.http.HttpRequest.BodyPublishers.ofByteArray(body));

            java.net.http.HttpResponse<byte[]> response = client.send(
                builder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofByteArray()
            );

            long duration = System.currentTimeMillis() - startTime;

            // 转换headers: Map<String, List<String>> -> Map<String, String>
            Map<String, String> headers = new HashMap<>();
            response.headers().map().forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    headers.put(key, String.join(", ", values));
                }
            });

            return new ResponseData(
                response.statusCode(),
                headers,
                response.body(),
                duration,
                Map.of()
            );
        } catch (Exception e) {
            log.error("Failed to forward request", e);
            return new ResponseData(500, Map.of(),
                e.getMessage().getBytes(),
                System.currentTimeMillis() - startTime,
                Map.of());
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, ResponseData responseData) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(responseData.statusCode())
        );

        // 添加响应头
        responseData.headers().forEach((key, value) ->
            response.headers().set(key, value));

        // 添加响应体
        if (responseData.body() != null && responseData.body().length > 0) {
            ByteBuf content = ctx.alloc().buffer(responseData.body().length);
            content.writeBytes(responseData.body());
            response.replace(content);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseData.body().length);
        }

        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in proxy handler", cause);
        ctx.close();
    }
}

