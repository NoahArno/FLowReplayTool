package com.flowreplay.core.replayer;

import com.flowreplay.core.model.ReplayResult;
import com.flowreplay.core.model.RequestData;
import com.flowreplay.core.model.ResponseData;
import com.flowreplay.core.model.TrafficRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * 流量回放引擎
 */
public class TrafficReplayer {

    private static final Logger log = LoggerFactory.getLogger(TrafficReplayer.class);
    private final HttpClient httpClient;
    private final String targetUrl;
    private final boolean sequentialMode;  // 是否顺序回放

    // Java HttpClient受限的header列表
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
        "host", "connection", "content-length", "expect", "upgrade"
    );

    public TrafficReplayer(String targetUrl) {
        this(targetUrl, true);  // 默认使用顺序回放
    }

    public TrafficReplayer(String targetUrl, boolean sequentialMode) {
        this.targetUrl = targetUrl;
        this.sequentialMode = sequentialMode;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * 回放流量
     * 支持顺序回放和并发回放两种模式
     */
    public List<ReplayResult> replay(List<TrafficRecord> records) {
        if (sequentialMode) {
            return replaySequential(records);
        } else {
            return replayConcurrent(records);
        }
    }

    /**
     * 顺序回放：按照录制顺序依次执行
     * 保证请求的执行顺序与录制时一致
     */
    private List<ReplayResult> replaySequential(List<TrafficRecord> records) {
        log.info("Starting sequential replay for {} records", records.size());
        List<ReplayResult> results = new java.util.ArrayList<>();

        for (int i = 0; i < records.size(); i++) {
            TrafficRecord record = records.get(i);
            log.info("Replaying record {}/{}: {}", i + 1, records.size(), record.id());

            try {
                ReplayResult result;
                if ("SOCKET".equals(record.protocol())) {
                    result = replayTcp(record);
                } else {
                    result = replayHttp(record);
                }
                results.add(result);
            } catch (Exception e) {
                log.error("Replay failed for record: {}", record.id(), e);
                results.add(ReplayResult.failure(record.id(), 0, e.getMessage()));
            }
        }

        log.info("Sequential replay completed");
        return results;
    }

    /**
     * 并发回放：使用Virtual Threads并发执行
     * 注意：不保证执行顺序，但保证结果顺序与请求顺序一致
     */
    private List<ReplayResult> replayConcurrent(List<TrafficRecord> records) {
        log.info("Starting concurrent replay for {} records", records.size());
        // 使用数组来保证索引对应关系
        ReplayResult[] resultsArray = new ReplayResult[records.size()];

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < records.size(); i++) {
                final int index = i;
                final TrafficRecord record = records.get(i);

                executor.submit(() -> {
                    try {
                        ReplayResult result;
                        if ("SOCKET".equals(record.protocol())) {
                            result = replayTcp(record);
                        } else {
                            result = replayHttp(record);
                        }
                        resultsArray[index] = result;
                    } catch (Exception e) {
                        log.error("Replay failed for record: {}", record.id(), e);
                        resultsArray[index] = ReplayResult.failure(record.id(), 0, e.getMessage());
                    }
                });
            }
        }

        log.info("Concurrent replay completed");
        // 转换为List返回
        return List.of(resultsArray);
    }

    private ReplayResult replayHttp(TrafficRecord record) throws Exception {
        long startTime = System.currentTimeMillis();

        HttpRequest request = buildHttpRequest(record.request());
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        long duration = System.currentTimeMillis() - startTime;

        // 转换headers: Map<String, List<String>> -> Map<String, String>
        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(key, String.join(", ", values));
            }
        });

        ResponseData responseData = new ResponseData(
            response.statusCode(),
            headers,
            response.body(),
            duration,
            Map.of()
        );

        return ReplayResult.success(record.id(), responseData, duration);
    }

    private ReplayResult replayTcp(TrafficRecord record) throws Exception {
        long startTime = System.currentTimeMillis();

        // 解析目标地址
        String[] parts = targetUrl.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 80;

        try (java.net.Socket socket = new java.net.Socket(host, port)) {
            socket.setSoTimeout(30000);

            // 获取请求数据
            byte[] requestBody = record.request().body();

            // 发送请求数据
            socket.getOutputStream().write(requestBody);
            socket.getOutputStream().flush();

            // 读取响应数据
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream responseStream = new java.io.ByteArrayOutputStream();
            int bytesRead;

            socket.setSoTimeout(5000); // 设置读取超时
            try {
                while ((bytesRead = socket.getInputStream().read(buffer)) != -1) {
                    responseStream.write(buffer, 0, bytesRead);
                }
            } catch (java.net.SocketTimeoutException e) {
                // 读取超时，认为响应已完成
            }

            long duration = System.currentTimeMillis() - startTime;

            ResponseData responseData = new ResponseData(
                0,
                Map.of(),
                responseStream.toByteArray(),
                duration,
                Map.of()
            );

            return ReplayResult.success(record.id(), responseData, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("TCP replay failed", e);
            return ReplayResult.failure(record.id(), duration, e.getMessage());
        }
    }

    private HttpRequest buildHttpRequest(RequestData requestData) {
        // 构建完整URL
        String fullUrl = targetUrl;
        if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
            fullUrl = "http://" + fullUrl;
        }

        // 规范化URL拼接
        String uri = normalizeRecordedUri(requestData.uri());
        if (!fullUrl.endsWith("/") && !uri.startsWith("/")) {
            fullUrl = fullUrl + "/" + uri;
        } else if (fullUrl.endsWith("/") && uri.startsWith("/")) {
            fullUrl = fullUrl + uri.substring(1);
        } else {
            fullUrl = fullUrl + uri;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(fullUrl))
            .timeout(Duration.ofSeconds(30));

        // 添加请求头，过滤受限的header
        requestData.headers().forEach((key, value) -> {
            if (!RESTRICTED_HEADERS.contains(key.toLowerCase())) {
                builder.header(key, value);
            }
        });

        // 设置请求方法和Body
        HttpRequest.BodyPublisher bodyPublisher = requestData.body() != null
            ? HttpRequest.BodyPublishers.ofByteArray(requestData.body())
            : HttpRequest.BodyPublishers.noBody();

        builder.method(requestData.method(), bodyPublisher);

        return builder.build();
    }

    static String normalizeRecordedUri(String uri) {
        if (uri == null || uri.isBlank() || "*".equals(uri)) {
            return "/";
        }
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            URI parsed = URI.create(uri);
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
}
