package com.flowreplay.core.comparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 比对配置加载器
 */
public class ComparisonConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ComparisonConfigLoader.class);
    private final ObjectMapper yamlMapper;

    public ComparisonConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * 静态方法：从文件加载配置
     */
    public static List<ComparisonConfig> load(String filePath) {
        ComparisonConfigLoader loader = new ComparisonConfigLoader();
        return loader.loadFromFile(filePath);
    }

    /**
     * 静态方法：加载默认配置
     */
    public static List<ComparisonConfig> loadDefault() {
        return getDefaultConfigStatic();
    }

    /**
     * 从文件加载配置
     */
    public List<ComparisonConfig> loadFromFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("Config file not found: {}", filePath);
                return getDefaultConfig();
            }
            ConfigWrapper wrapper = yamlMapper.readValue(file, ConfigWrapper.class);
            return wrapper.getRules();
        } catch (IOException e) {
            log.error("Failed to load config from file: {}", filePath, e);
            return getDefaultConfig();
        }
    }

    /**
     * 获取默认配置
     */
    private List<ComparisonConfig> getDefaultConfig() {
        return getDefaultConfigStatic();
    }

    /**
     * 静态方法：获取默认配置
     */
    private static List<ComparisonConfig> getDefaultConfigStatic() {
        List<ComparisonConfig> configs = new ArrayList<>();

        // 默认配置：HTTP状态码 + JSON结构化比对
        List<StrategyConfig> strategies = new ArrayList<>();
        strategies.add(new StrategyConfig("http-status", null));
        strategies.add(new StrategyConfig("json-structure", null));

        configs.add(new ComparisonConfig("default", ".*", strategies));
        return configs;
    }

    /**
     * YAML配置包装类
     */
    public static class ConfigWrapper {
        private List<ComparisonConfig> rules;

        public List<ComparisonConfig> getRules() {
            return rules;
        }

        public void setRules(List<ComparisonConfig> rules) {
            this.rules = rules;
        }
    }
}
