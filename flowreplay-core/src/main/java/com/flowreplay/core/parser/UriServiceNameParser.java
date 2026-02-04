package com.flowreplay.core.parser;

import com.flowreplay.core.model.TrafficRecord;

/**
 * 默认的URI解析器
 */
public class UriServiceNameParser implements ServiceNameParser {

    @Override
    public String parseServiceName(TrafficRecord record) {
        String uri = record.request().uri();
        if (uri == null || uri.isEmpty()) {
            return "unknown";
        }
        // 移除查询参数
        int queryIndex = uri.indexOf('?');
        if (queryIndex > 0) {
            uri = uri.substring(0, queryIndex);
        }
        return uri;
    }

    @Override
    public String getParserName() {
        return "uri";
    }
}
