package com.flowreplay.core.model;

/**
 * 回放结果
 */
public record ReplayResult(
    String recordId,            // 原始记录ID
    ResponseData response,      // 回放得到的响应
    long duration,              // 回放耗时（毫秒）
    boolean success,            // 是否成功
    String errorMessage         // 错误信息
) {
    public static ReplayResult success(String recordId, ResponseData response, long duration) {
        return new ReplayResult(recordId, response, duration, true, null);
    }

    public static ReplayResult failure(String recordId, long duration, String errorMessage) {
        return new ReplayResult(recordId, null, duration, false, errorMessage);
    }
}
