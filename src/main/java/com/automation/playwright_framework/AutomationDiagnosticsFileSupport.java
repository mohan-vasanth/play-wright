package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

public final class AutomationDiagnosticsFileSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AutomationDiagnosticsFileSupport() {
    }

    public static void mergeSecurityResults(
            Path diagnosticsPath,
            List<AutomationExecutionDiagnostics.SecurityValidationResult> results) {
        if (diagnosticsPath == null || results == null || results.isEmpty()) {
            return;
        }

        try {
            ObjectNode root = readRoot(diagnosticsPath);
            ArrayNode resultArray = root.putArray("securityValidationResults");
            long failureCount = 0L;
            long gracefulHandlingCount = 0L;

            for (AutomationExecutionDiagnostics.SecurityValidationResult result : results) {
                if (result == null) {
                    continue;
                }
                if (!"PASS".equalsIgnoreCase(result.status())) {
                    failureCount++;
                }
                if (result.handledGracefully()) {
                    gracefulHandlingCount++;
                }

                ObjectNode node = resultArray.addObject();
                putIfPresent(node, "scenarioId", result.scenarioId());
                putIfPresent(node, "category", result.category());
                putIfPresent(node, "status", result.status());
                node.put("handledGracefully", result.handledGracefully());
                putIfPresent(node, "message", result.message());
                putIfPresent(node, "payloadPath", result.payloadPath());
                putIfPresent(node, "observedResponse", result.observedResponse());
                putIfPresent(node, "screenshotFile", result.screenshotFile());
            }

            ObjectNode summary = root.putObject("securitySummary");
            summary.put("enabled", true);
            summary.put("executed", results.size());
            summary.put("handledGracefully", gracefulHandlingCount);
            summary.put("failures", failureCount);

            ArrayNode recommendations = ensureRecommendations(root);
            if (failureCount > 0L) {
                addUniqueRecommendation(recommendations,
                        "Investigate security scenarios that were accepted or crashed the workflow.");
            }

            if (failureCount > 0L && blank(root.path("rootCause").asText(null))) {
                root.put("rootCause", "One or more penetration-testing scenarios were not handled safely.");
            }

            Files.writeString(diagnosticsPath, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception ignored) {
        }
    }

    private static ObjectNode readRoot(Path diagnosticsPath) throws Exception {
        if (!Files.isRegularFile(diagnosticsPath)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        JsonNode current = OBJECT_MAPPER.readTree(Files.readString(diagnosticsPath));
        if (current instanceof ObjectNode objectNode) {
            return objectNode;
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    private static ArrayNode ensureRecommendations(ObjectNode root) {
        JsonNode existing = root.path("recommendations");
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        return root.putArray("recommendations");
    }

    private static void addUniqueRecommendation(ArrayNode recommendations, String recommendation) {
        if (recommendations == null || blank(recommendation)) {
            return;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode node : recommendations) {
            String existing = node.asText(null);
            if (!blank(existing)) {
                values.add(existing.trim());
            }
        }
        if (values.add(recommendation.trim())) {
            recommendations.removeAll();
            for (String value : values) {
                recommendations.add(value);
            }
        }
    }

    private static void putIfPresent(ObjectNode target, String fieldName, String value) {
        if (!blank(value)) {
            target.put(fieldName, value.trim());
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
