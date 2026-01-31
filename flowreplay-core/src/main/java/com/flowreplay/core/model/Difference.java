package com.flowreplay.core.model;

/**
 * 差异记录
 */
public record Difference(
    String path,        // 差异路径（如JSON路径）
    String type,        // 差异类型：value/type/missing
    String expected,    // 期望值
    String actual       // 实际值
) {
}
