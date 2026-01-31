package com.flowreplay.core.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.Difference;
import com.flowreplay.core.model.ResponseData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON结构化比对策略
 */
public class JsonStructureStrategy implements ComparisonStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> ignoreFields;

    public JsonStructureStrategy(Set<String> ignoreFields) {
        this.ignoreFields = ignoreFields != null ? ignoreFields : Set.of();
    }

    public JsonStructureStrategy() {
        this(Set.of());
    }

    @Override
    public ComparisonResult compare(ResponseData recorded, ResponseData replayed) {
        try {
            JsonNode recordedNode = objectMapper.readTree(recorded.body());
            JsonNode replayedNode = objectMapper.readTree(replayed.body());

            List<Difference> diffs = compareNodes("$", recordedNode, replayedNode);
            return new ComparisonResult(diffs.isEmpty(), diffs, Map.of());
        } catch (Exception e) {
            return ComparisonResult.error("JSON parse error", e);
        }
    }

    private List<Difference> compareNodes(String path, JsonNode n1, JsonNode n2) {
        List<Difference> diffs = new ArrayList<>();

        if (shouldIgnore(path)) {
            return diffs;
        }

        if (n1.getNodeType() != n2.getNodeType()) {
            diffs.add(new Difference(path, "type", n1.getNodeType().toString(), n2.getNodeType().toString()));
            return diffs;
        }

        if (n1.isObject()) {
            compareObjects(path, n1, n2, diffs);
        } else if (n1.isArray()) {
            compareArrays(path, n1, n2, diffs);
        } else if (!n1.equals(n2)) {
            diffs.add(new Difference(path, "value", n1.asText(), n2.asText()));
        }

        return diffs;
    }

    private void compareObjects(String path, JsonNode n1, JsonNode n2, List<Difference> diffs) {
        n1.fieldNames().forEachRemaining(fieldName -> {
            String fieldPath = path + "." + fieldName;
            if (!n2.has(fieldName)) {
                diffs.add(new Difference(fieldPath, "missing", "exists", "missing"));
            } else {
                diffs.addAll(compareNodes(fieldPath, n1.get(fieldName), n2.get(fieldName)));
            }
        });
    }

    private void compareArrays(String path, JsonNode n1, JsonNode n2, List<Difference> diffs) {
        if (n1.size() != n2.size()) {
            diffs.add(new Difference(path + ".length", "value",
                String.valueOf(n1.size()), String.valueOf(n2.size())));
            return;
        }

        for (int i = 0; i < n1.size(); i++) {
            String indexPath = path + "[" + i + "]";
            diffs.addAll(compareNodes(indexPath, n1.get(i), n2.get(i)));
        }
    }

    private boolean shouldIgnore(String path) {
        return ignoreFields.stream().anyMatch(pattern ->
            path.matches(pattern.replace("*", ".*").replace("$", "\\$"))
        );
    }

    @Override
    public String getName() {
        return "json-structure";
    }
}
