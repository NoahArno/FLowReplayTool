package com.flowreplay.core.report;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.ResponseData;
import com.flowreplay.core.model.TrafficRecord;

import java.time.Instant;

/**
 * 比对报告
 */
public record ComparisonReport(
    TrafficRecord record,
    ResponseData replayedResponse,
    ComparisonResult result,
    long replayDuration,      // 回放耗时（毫秒）
    Instant replayTimestamp   // 回放请求时间
) {
}
