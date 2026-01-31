package com.flowreplay.core.comparator;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.Difference;
import com.flowreplay.core.model.ResponseData;

import java.util.List;
import java.util.Map;

/**
 * HTTP状态码比对策略
 */
public class HttpStatusStrategy implements ComparisonStrategy {

    @Override
    public ComparisonResult compare(ResponseData recorded, ResponseData replayed) {
        if (recorded.statusCode() == replayed.statusCode()) {
            return ComparisonResult.success();
        }

        Difference diff = new Difference(
            "statusCode",
            "value",
            String.valueOf(recorded.statusCode()),
            String.valueOf(replayed.statusCode())
        );

        return new ComparisonResult(false, List.of(diff), Map.of());
    }

    @Override
    public String getName() {
        return "http-status";
    }
}
