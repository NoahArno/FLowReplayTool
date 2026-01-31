package com.flowreplay.core.comparator;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.ResponseData;

/**
 * 比对策略接口
 */
public interface ComparisonStrategy {

    /**
     * 比对两个响应
     */
    ComparisonResult compare(ResponseData recorded, ResponseData replayed);

    /**
     * 策略名称
     */
    String getName();
}
