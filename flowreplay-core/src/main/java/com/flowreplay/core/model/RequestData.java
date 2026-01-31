package com.flowreplay.core.model;

import java.util.Map;

/**
 * 请求数据模型
 */
public record RequestData(
    String method,              // HTTP方法或协议命令
    String uri,                 // URI或目标地址
    Map<String, String> headers, // 请求头
    byte[] body,                // 请求体
    Map<String, Object> metadata // 元数据
) {
    public RequestData {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
