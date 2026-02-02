package com.flowreplay.proxy;

import com.flowreplay.core.model.RequestData;
import com.flowreplay.core.model.ResponseData;
import com.flowreplay.core.model.TrafficRecord;
import com.flowreplay.core.recorder.TrafficRecorder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
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
public class HttpProxyHandler extends ChannelInboundHandlerAdapter {

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
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest)) {
            ReferenceCountUtil.release(msg);
            return;
        }

        FullHttpRequest request = (FullHttpRequest) msg;
        try {
            long startTime = System.currentTimeMillis();
            String recordId = UUID.randomUUID().toString();
            boolean keepAlive = HttpUtil.isKeepAlive(request);

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
            sendResponse(ctx, responseData, keepAlive);
        } finally {
            ReferenceCountUtil.release(request);
        }
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
            String url = "http://" + targetHost + ":" + targetPort + extractPathAndQuery(request.uri());
            log.info("Forwarding request to: {}", url);

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

            // 添加调试日志
            log.info("Received response - Status: {}, Body length: {}",
                response.statusCode(),
                response.body() != null ? response.body().length : 0);
            if (response.body() != null && response.body().length > 0) {
                log.info("Target response body: {}", new String(response.body()));
            }

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
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            return new ResponseData(500, Map.of(), message.getBytes(),
                System.currentTimeMillis() - startTime,
                Map.of());
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, ResponseData responseData, boolean keepAlive) {
        // 添加调试日志
        log.info("Sending response - Status: {}, Body length: {}",
            responseData.statusCode(),
            responseData.body() != null ? responseData.body().length : 0);
        if (responseData.body() != null && responseData.body().length > 0) {
            log.info("Response body: {}", new String(responseData.body()));
        }

        byte[] body = responseData.body();
        ByteBuf content = body != null && body.length > 0 ? Unpooled.wrappedBuffer(body) : Unpooled.EMPTY_BUFFER;

        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(responseData.statusCode()),
            content
        );

        // 添加响应头（在设置body之后，避免Content-Length被覆盖）
        responseData.headers().forEach((key, value) -> {
            // 跳过某些可能冲突的header
            if (!key.equalsIgnoreCase("content-length") &&
                !key.equalsIgnoreCase("transfer-encoding")) {
                response.headers().set(key, value);
            }
        });

        // 设置Content-Length
        if (body != null) {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        } else {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        }

        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        // 写入并刷新响应
        ctx.writeAndFlush(response).addListener(keepAlive ? ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE : ChannelFutureListener.CLOSE).addListener(future -> {
            if (future.isSuccess()) {
                log.info("Response sent successfully");
            } else {
                log.error("Failed to send response", future.cause());
            }
        });
    }

    private static String extractPathAndQuery(String uri) {
        if (uri == null || uri.isBlank() || "*".equals(uri)) {
            return "/";
        }
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            java.net.URI parsed = java.net.URI.create(uri);
            String path = parsed.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String query = parsed.getRawQuery();
            if (query != null && !query.isEmpty()) {
                return path + "?" + query;
            }
            return path;
        }
        if (uri.startsWith("/")) {
            return uri;
        }
        return "/" + uri;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in proxy handler", cause);
        ctx.close();
    }
}
