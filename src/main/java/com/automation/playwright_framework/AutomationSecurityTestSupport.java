package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutomationSecurityTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern PATH_SEGMENT_PATTERN = Pattern.compile("([^\\[\\]]+)(?:\\[(\\d+)\\])?");

    private AutomationSecurityTestSupport() {
    }

    public static List<SecurityScenario> loadScenarios(AutomationFrameworkSettings settings) {
        List<SecurityScenario> scenarios = new ArrayList<>();
        if (settings == null || !settings.penetrationTesting().enabled()) {
            return scenarios;
        }

        String datasetResource = settings.penetrationTesting().datasetResource();
        if (datasetResource == null || datasetResource.isBlank()) {
            return scenarios;
        }

        try (InputStream inputStream = AutomationSecurityTestSupport.class.getClassLoader()
                .getResourceAsStream(datasetResource)) {
            if (inputStream == null) {
                return scenarios;
            }
            JsonNode root = OBJECT_MAPPER.readTree(inputStream);
            if (!root.isArray()) {
                return scenarios;
            }

            for (JsonNode node : root) {
                scenarios.add(new SecurityScenario(
                        text(node, "id"),
                        text(node, "category"),
                        text(node, "description"),
                        readStringList(node.path("jsonPaths")),
                        text(node, "replacementValue"),
                        node.path("removeValue").asBoolean(false)));
            }
        } catch (Exception ignored) {
        }

        int maxScenarios = Math.max(0, settings.penetrationTesting().maxScenariosPerDeclaration());
        if (maxScenarios == 0 || scenarios.size() <= maxScenarios) {
            return scenarios;
        }
        return List.copyOf(scenarios.subList(0, maxScenarios));
    }

    public static AppliedScenario applyScenario(JsonNode source, SecurityScenario scenario) {
        if (source == null || scenario == null) {
            return new AppliedScenario(source, List.of());
        }

        JsonNode deepCopy = source.deepCopy();
        if (!(deepCopy instanceof ObjectNode) && !(deepCopy instanceof ArrayNode)) {
            return new AppliedScenario(deepCopy, List.of());
        }

        List<String> appliedPaths = new ArrayList<>();
        for (String path : scenario.jsonPaths()) {
            if (path == null || path.isBlank()) {
                continue;
            }
            if (applyPath(deepCopy, path, scenario)) {
                appliedPaths.add(path);
            }
        }
        return new AppliedScenario(deepCopy, appliedPaths);
    }

    private static boolean applyPath(JsonNode root, String path, SecurityScenario scenario) {
        String[] segments = path.split("\\.");
        JsonNode current = root;
        JsonNode parent = null;
        String parentFieldName = null;
        Integer parentIndex = null;

        for (String segment : segments) {
            Matcher matcher = PATH_SEGMENT_PATTERN.matcher(segment);
            if (!matcher.matches()) {
                return false;
            }
            String fieldName = matcher.group(1);
            String indexText = matcher.group(2);

            parent = current;
            parentFieldName = fieldName;
            parentIndex = indexText != null ? Integer.parseInt(indexText) : null;

            current = current.path(fieldName);
            if (current.isMissingNode()) {
                return false;
            }

            if (parentIndex != null) {
                if (!current.isArray() || current.size() <= parentIndex) {
                    return false;
                }
                parent = current;
                current = current.get(parentIndex);
            }
        }

        if (parent == null) {
            return false;
        }

        if (parentIndex != null && parent instanceof ArrayNode arrayParent) {
            if (scenario.removeValue()) {
                arrayParent.set(parentIndex, OBJECT_MAPPER.nullNode());
            } else {
                arrayParent.set(parentIndex, OBJECT_MAPPER.valueToTree(scenario.replacementValue()));
            }
            return true;
        }

        if (!(parent instanceof ObjectNode objectParent) || parentFieldName == null) {
            return false;
        }

        if (scenario.removeValue()) {
            objectParent.putNull(parentFieldName);
        } else {
            objectParent.put(parentFieldName, scenario.replacementValue());
        }
        return true;
    }

    private static List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode child : node) {
            String value = text(child);
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return text(node.path(fieldName));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SecurityScenario(
            String id,
            String category,
            String description,
            List<String> jsonPaths,
            String replacementValue,
            boolean removeValue) {
    }

    public record AppliedScenario(
            JsonNode payload,
            List<String> appliedPaths) {

        public boolean hasAppliedPaths() {
            return appliedPaths != null && !appliedPaths.isEmpty();
        }
    }
}
