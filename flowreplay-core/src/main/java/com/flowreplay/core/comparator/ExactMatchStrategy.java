package com.flowreplay.core.comparator;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.Difference;
import com.flowreplay.core.model.ResponseData;

import java.util.Arrays;
import java.util.List;

/**
 * 完全匹配策略
 */
public class ExactMatchStrategy implements ComparisonStrategy {

    @Override
    public ComparisonResult compare(ResponseData recorded, ResponseData replayed) {
        if (Arrays.equals(recorded.body(), replayed.body())) {
            return ComparisonResult.success();
        }

        Difference diff = new Difference(
            "body",
            "value",
            new String(recorded.body()),
            new String(replayed.body())
        );

        return new ComparisonResult(false, List.of(diff), java.util.Map.of());
    }

    @Override
    public String getName() {
        return "exact-match";
    }
}
