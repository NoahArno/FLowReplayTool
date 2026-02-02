package com.flowreplay.core.report;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.ResponseData;
import com.flowreplay.core.model.TrafficRecord;

/**
 * 比对报告
 */
public record ComparisonReport(
    TrafficRecord record,
    ResponseData replayedResponse,
    ComparisonResult result
) {
}
