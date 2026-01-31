package com.flowreplay.core.model;

import java.util.Map;

/**
 * 响应数据模型
 */
public record ResponseData(
    int statusCode,             // HTTP状态码或协议状态
    Map<String, String> headers, // 响应头
    byte[] body,                // 响应体
    long duration,              // 响应时间（毫秒）
    Map<String, Object> metadata // 元数据
) {
    public ResponseData {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
