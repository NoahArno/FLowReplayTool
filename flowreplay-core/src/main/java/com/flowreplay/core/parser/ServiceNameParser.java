package com.flowreplay.core.parser;

import com.flowreplay.core.model.TrafficRecord;

/**
 * 接口名解析器接口
 */
public interface ServiceNameParser {

    /**
     * 解析接口名
     * @param record 流量记录
     * @return 接口名
     */
    String parseServiceName(TrafficRecord record);

    /**
     * 获取解析器名称
     * @return 解析器名称
     */
    String getParserName();
}
