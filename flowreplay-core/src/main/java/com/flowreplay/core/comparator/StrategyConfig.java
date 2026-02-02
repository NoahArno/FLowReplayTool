package com.flowreplay.core.comparator;

import java.util.Map;

/**
 * 策略配置
 */
public class StrategyConfig {
    private String type;
    private Map<String, Object> config;

    public StrategyConfig() {
    }

    public StrategyConfig(String type, Map<String, Object> config) {
        this.type = type;
        this.config = config;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}
