package com.flowreplay.core.parser;

import java.util.HashMap;
import java.util.Map;

/**
 * 接口名解析器工厂
 */
public class ServiceNameParserFactory {

    private static final Map<String, ServiceNameParser> PARSERS = new HashMap<>();

    static {
        // 注册默认解析器
        register(new UriServiceNameParser());
        register(new EsbServiceNameParser());
    }

    /**
     * 注册解析器
     */
    public static void register(ServiceNameParser parser) {
        PARSERS.put(parser.getParserName().toLowerCase(), parser);
    }

    /**
     * 获取解析器
     * @param parserName 解析器名称，如果为null则返回默认的URI解析器
     * @return 解析器实例
     */
    public static ServiceNameParser getParser(String parserName) {
        if (parserName == null || parserName.isEmpty()) {
            return PARSERS.get("uri");
        }
        ServiceNameParser parser = PARSERS.get(parserName.toLowerCase());
        if (parser == null) {
            throw new IllegalArgumentException("Unknown parser: " + parserName);
        }
        return parser;
    }
}
