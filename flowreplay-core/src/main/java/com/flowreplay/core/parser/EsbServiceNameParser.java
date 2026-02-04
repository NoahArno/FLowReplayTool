package com.flowreplay.core.parser;

import com.flowreplay.core.model.TrafficRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ESB系统的接口名解析器
 * 解析 <ServiceCode>接口名</ServiceCode> 或 JSON 中的 ServiceCode
 */
public class EsbServiceNameParser implements ServiceNameParser {

    private static final Pattern XML_SERVICE_CODE_PATTERN = Pattern.compile("<ServiceCode>([^<]+)</ServiceCode>");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String parseServiceName(TrafficRecord record) {
        byte[] body = record.request().body();
        if (body == null || body.length == 0) {
            return record.request().uri();
        }

        String bodyStr = new String(body, StandardCharsets.UTF_8);

        // 尝试解析 XML 格式的 ServiceCode
        String serviceCode = parseXmlServiceCode(bodyStr);
        if (serviceCode != null) {
            return serviceCode;
        }

        // 尝试解析 JSON 格式的 ServiceCode
        serviceCode = parseJsonServiceCode(bodyStr);
        if (serviceCode != null) {
            return serviceCode;
        }

        // 如果都解析不到，返回 URI
        return record.request().uri();
    }

    private String parseXmlServiceCode(String body) {
        Matcher matcher = XML_SERVICE_CODE_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String parseJsonServiceCode(String body) {
        try {
            JsonNode rootNode = objectMapper.readTree(body);
            JsonNode serviceCodeNode = rootNode.get("ServiceCode");
            if (serviceCodeNode != null && !serviceCodeNode.isNull()) {
                return serviceCodeNode.asText();
            }
        } catch (Exception e) {
            // 不是有效的 JSON，忽略
        }
        return null;
    }

    @Override
    public String getParserName() {
        return "esb";
    }
}
