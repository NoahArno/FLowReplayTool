package com.flowreplay.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * 流量记录模型
 */
public record TrafficRecord(
    String id,                    // 唯一标识
    String protocol,              // 协议类型：HTTP/SOCKET/WEBSERVICE
    Instant timestamp,            // 时间戳
    RequestData request,          // 请求数据
    ResponseData response,        // 响应数据
    Map<String, Object> metadata  // 元数据（来源IP、目标IP等）
) {
    public TrafficRecord {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
