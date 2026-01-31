package com.flowreplay.core.model;

import java.util.List;
import java.util.Map;

/**
 * 比对结果
 */
public record ComparisonResult(
    boolean matched,                // 是否匹配
    List<Difference> differences,   // 差异列表
    Map<String, Object> metrics     // 指标数据（响应时间差异等）
) {
    public ComparisonResult {
        differences = differences == null ? List.of() : List.copyOf(differences);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    public static ComparisonResult success() {
        return new ComparisonResult(true, List.of(), Map.of());
    }

    public static ComparisonResult error(String message, Exception e) {
        return new ComparisonResult(
            false,
            List.of(new Difference("error", "exception", message, e.getMessage())),
            Map.of()
        );
    }
}
