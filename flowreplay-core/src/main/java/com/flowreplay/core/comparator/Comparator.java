package com.flowreplay.core.comparator;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.ResponseData;
import com.flowreplay.core.model.TrafficRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 比对器
 */
public class Comparator {

    private static final Logger log = LoggerFactory.getLogger(Comparator.class);
    private final List<ComparisonConfig> configs;
    private final Map<String, ComparisonStrategy> strategyMap;

    public Comparator(List<ComparisonConfig> configs) {
        this.configs = configs;
        this.strategyMap = new HashMap<>();
        initializeStrategies();
    }

    private void initializeStrategies() {
        strategyMap.put("http-status", new HttpStatusStrategy());
        strategyMap.put("exact-match", new ExactMatchStrategy());
        strategyMap.put("json-structure", new JsonStructureStrategy());
    }

    public ComparisonResult compare(TrafficRecord record, ResponseData replayedResponse) {
        // 查找匹配的配置
        ComparisonConfig config = findMatchingConfig(record.request().uri());
        
        List<ComparisonResult> results = new ArrayList<>();
        for (StrategyConfig strategyConfig : config.getStrategies()) {
            ComparisonStrategy strategy = getStrategy(strategyConfig);
            if (strategy != null) {
                results.add(strategy.compare(record.response(), replayedResponse));
            }
        }
        
        return mergeResults(results);
    }

    private ComparisonConfig findMatchingConfig(String uri) {
        for (ComparisonConfig config : configs) {
            if (uri.matches(config.getUrlPattern())) {
                return config;
            }
        }
        return configs.isEmpty() ? getDefaultConfig() : configs.get(0);
    }

    private ComparisonStrategy getStrategy(StrategyConfig config) {
        return strategyMap.get(config.getType());
    }

    private ComparisonResult mergeResults(List<ComparisonResult> results) {
        if (results.isEmpty()) {
            return ComparisonResult.success();
        }
        
        boolean allMatched = results.stream().allMatch(ComparisonResult::matched);
        List<com.flowreplay.core.model.Difference> allDiffs = new ArrayList<>();
        results.forEach(r -> allDiffs.addAll(r.differences()));
        
        return new ComparisonResult(allMatched, allDiffs, Map.of());
    }

    private ComparisonConfig getDefaultConfig() {
        List<StrategyConfig> strategies = new ArrayList<>();
        strategies.add(new StrategyConfig("http-status", null));
        return new ComparisonConfig("default", ".*", strategies);
    }
}
