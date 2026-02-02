package com.flowreplay.core.comparator;

import java.util.List;
import java.util.Map;

/**
 * 比对配置
 */
public class ComparisonConfig {
    private String name;
    private String urlPattern;
    private List<StrategyConfig> strategies;

    public ComparisonConfig() {
    }

    public ComparisonConfig(String name, String urlPattern, List<StrategyConfig> strategies) {
        this.name = name;
        this.urlPattern = urlPattern;
        this.strategies = strategies;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public void setUrlPattern(String urlPattern) {
        this.urlPattern = urlPattern;
    }

    public List<StrategyConfig> getStrategies() {
        return strategies;
    }

    public void setStrategies(List<StrategyConfig> strategies) {
        this.strategies = strategies;
    }
}
