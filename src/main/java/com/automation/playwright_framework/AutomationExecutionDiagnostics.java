package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AutomationExecutionDiagnostics {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AutomationFrameworkSettings settings;
    private final Map<String, Long> activeSteps = new ConcurrentHashMap<>();
    private final List<ValidationEvent> lowTestingResults = new ArrayList<>();
    private final List<ThresholdViolation> thresholdViolations = new ArrayList<>();
    private final List<PerformanceMetric> performanceMetrics = new ArrayList<>();
    private final List<SecurityValidationResult> securityValidationResults = new ArrayList<>();

    private String flowLabel = "UNKNOWN";
    private Instant scenarioStartedAt;
    private int missingElementCount;
    private int validationFailureCount;
    private int skippedFieldCount;
    private int unexpectedUiChangeCount;
    private int validatedFieldCount;

    public AutomationExecutionDiagnostics(AutomationFrameworkSettings settings) {
        this.settings = settings;
    }

    public void resetForScenario(String flowLabel) {
        this.flowLabel = firstNonBlank(flowLabel, "UNKNOWN");
        this.scenarioStartedAt = Instant.now();
        this.activeSteps.clear();
        this.lowTestingResults.clear();
        this.thresholdViolations.clear();
        this.performanceMetrics.clear();
        this.securityValidationResults.clear();
        this.missingElementCount = 0;
        this.validationFailureCount = 0;
        this.skippedFieldCount = 0;
        this.unexpectedUiChangeCount = 0;
        this.validatedFieldCount = 0;
    }

    public void startStep(String stepName) {
        if (stepName == null || stepName.isBlank()) {
            return;
        }
        activeSteps.put(stepName, System.currentTimeMillis());
        if (settings.lowTesting().enabled()) {
            lowTestingResults.add(new ValidationEvent(
                    Instant.now().toString(),
                    "INFO",
                    "STEP_START",
                    stepName,
                    null,
                    "Automation step started.",
                    null,
                    null));
        }
    }

    public void finishStep(String stepName) {
        if (stepName == null || stepName.isBlank()) {
            return;
        }
        Long startedAt = activeSteps.remove(stepName);
        if (startedAt == null) {
            return;
        }
        long durationMs = Math.max(0L, System.currentTimeMillis() - startedAt);
        recordPerformanceMetric(stepName, durationMs);
    }

    public void recordPageReady(String stepName, boolean ready, String detail) {
        if (!settings.lowTesting().enabled()) {
            return;
        }
        if (ready) {
            lowTestingResults.add(new ValidationEvent(
                    Instant.now().toString(),
                    "INFO",
                    "PAGE_READY",
                    stepName,
                    null,
                    firstNonBlank(detail, "Page was ready before interaction."),
                    null,
                    null));
            return;
        }
        validationFailureCount++;
        lowTestingResults.add(new ValidationEvent(
                Instant.now().toString(),
                "WARN",
                "PAGE_NOT_READY",
                stepName,
                null,
                firstNonBlank(detail, "Page was not fully ready before interaction."),
                null,
                null));
    }

    public void recordControlState(String stepName, boolean visible, boolean enabled, String detail) {
        if (!settings.lowTesting().enabled()) {
            return;
        }
        if (visible && enabled) {
            lowTestingResults.add(new ValidationEvent(
                    Instant.now().toString(),
                    "INFO",
                    "CONTROL_READY",
                    stepName,
                    null,
                    firstNonBlank(detail, "Resolved control was visible and enabled."),
                    null,
                    null));
            return;
        }
        validationFailureCount++;
        lowTestingResults.add(new ValidationEvent(
                Instant.now().toString(),
                "WARN",
                "CONTROL_NOT_READY",
                stepName,
                null,
                firstNonBlank(detail, "Resolved control was not ready for interaction."),
                String.valueOf(visible),
                String.valueOf(enabled)));
    }

    public void recordJsonValueCheck(String stepName, String value) {
        if (!settings.lowTesting().enabled()) {
            return;
        }
        if (value != null && !value.isBlank()) {
            lowTestingResults.add(new ValidationEvent(
                    Instant.now().toString(),
                    "INFO",
                    "JSON_VALUE_PRESENT",
                    stepName,
                    null,
                    "JSON value was available before field entry.",
                    value,
                    null));
            return;
        }
        skippedFieldCount++;
        lowTestingResults.add(new ValidationEvent(
                Instant.now().toString(),
                "WARN",
                "JSON_VALUE_MISSING",
                stepName,
                null,
                "JSON value was empty or missing before field entry.",
                null,
                null));
        evaluateThreshold("MAX_SKIPPED_FIELDS", skippedFieldCount, settings.thresholdValidation().maxSkippedFields(),
                "Skipped-field threshold exceeded.");
    }

    public void recordValidatedField() {
        validatedFieldCount++;
    }

    public void recordFieldVerificationFailure(String detail, String expectedValue, String actualValue) {
        validationFailureCount++;
        lowTestingResults.add(new ValidationEvent(
                Instant.now().toString(),
                "WARN",
                "FIELD_VERIFICATION_FAILED",
                flowLabel,
                null,
                firstNonBlank(detail, "Field verification failed."),
                expectedValue,
                actualValue));
        evaluateThreshold("MAX_VALIDATION_FAILURES", validationFailureCount,
                settings.thresholdValidation().maxValidationFailures(),
                "Validation-failure threshold exceeded.");
    }

    public void recordMissingElement(String detail) {
        missingElementCount++;
        validationFailureCount++;
        lowTestingResults.add(new ValidationEvent(
                Instant.now().toString(),
                "WARN",
                "MISSING_ELEMENT",
                flowLabel,
                null,
                firstNonBlank(detail, "Required UI element was not found."),
                null,
                null));
        evaluateThreshold("MAX_VALIDATION_FAILURES", validationFailureCount,
                settings.thresholdValidation().maxValidationFailures(),
                "Validation-failure threshold exceeded.");
    }

    public void recordUnexpectedUiChange(String detail) {
        unexpectedUiChangeCount++;
        validationFailureCount++;
        lowTestingResults.add(new ValidationEvent(
                Instant.now().toString(),
                "WARN",
                "UNEXPECTED_UI_CHANGE",
                flowLabel,
                null,
                firstNonBlank(detail, "Unexpected UI behavior was detected."),
                null,
                null));
    }

    public void recordGenericWarning(String detail) {
        validationFailureCount++;
        lowTestingResults.add(new ValidationEvent(
                Instant.now().toString(),
                "WARN",
                "VALIDATION_WARNING",
                flowLabel,
                null,
                firstNonBlank(detail, "Validation warning detected."),
                null,
                null));
        evaluateThreshold("MAX_VALIDATION_FAILURES", validationFailureCount,
                settings.thresholdValidation().maxValidationFailures(),
                "Validation-failure threshold exceeded.");
    }

    public void recordRetryUsage(String actionName, int attemptsUsed) {
        if (!settings.thresholdValidation().enabled()) {
            return;
        }
        evaluateThreshold(
                "MAX_RETRY_COUNT",
                attemptsUsed,
                settings.thresholdValidation().maxRetryCount(),
                firstNonBlank(actionName, "Action") + " exceeded configured retry count.");
    }

    public void recordSecurityValidationResult(SecurityValidationResult result) {
        if (result == null) {
            return;
        }
        securityValidationResults.add(result);
    }

    public void recordSecurityValidationResults(List<SecurityValidationResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        securityValidationResults.addAll(results);
    }

    public boolean shouldStopExecution() {
        if (settings.thresholdValidation().enabled()
                && settings.thresholdValidation().stopOnViolation()
                && !thresholdViolations.isEmpty()) {
            return true;
        }
        return settings.lowTesting().enabled()
                && settings.lowTesting().failOnValidation()
                && validationFailureCount > 0;
    }

    public ObjectNode enrich(ObjectNode root) {
        ObjectNode target = root == null ? OBJECT_MAPPER.createObjectNode() : root;
        appendLowTestingSummary(target);
        appendThresholdSummary(target);
        appendPerformanceSummary(target);
        appendSecuritySummary(target);
        appendRecommendations(target);
        target.put("rootCause", resolveRootCause());
        return target;
    }

    private void appendLowTestingSummary(ObjectNode root) {
        ObjectNode summary = root.putObject("lowTestingSummary");
        summary.put("enabled", settings.lowTesting().enabled());
        summary.put("flowLabel", flowLabel);
        summary.put("validatedFields", validatedFieldCount);
        summary.put("validationFailures", validationFailureCount);
        summary.put("missingElements", missingElementCount);
        summary.put("skippedFields", skippedFieldCount);
        summary.put("unexpectedUiChanges", unexpectedUiChangeCount);

        ArrayNode results = root.putArray("lowTestingResults");
        for (ValidationEvent event : lowTestingResults) {
            ObjectNode node = results.addObject();
            node.put("timestamp", event.timestamp());
            node.put("level", event.level());
            node.put("category", event.category());
            node.put("step", event.step());
            putIfPresent(node, "field", event.field());
            putIfPresent(node, "message", event.message());
            putIfPresent(node, "expectedValue", event.expectedValue());
            putIfPresent(node, "actualValue", event.actualValue());
        }
    }

    private void appendThresholdSummary(ObjectNode root) {
        ObjectNode summary = root.putObject("thresholdSummary");
        summary.put("enabled", settings.thresholdValidation().enabled());
        summary.put("violations", thresholdViolations.size());
        summary.put("stopOnViolation", settings.thresholdValidation().stopOnViolation());

        ArrayNode results = root.putArray("thresholdViolations");
        for (ThresholdViolation violation : thresholdViolations) {
            ObjectNode node = results.addObject();
            node.put("timestamp", violation.timestamp());
            node.put("thresholdName", violation.thresholdName());
            node.put("actualValue", violation.actualValue());
            node.put("thresholdValue", violation.thresholdValue());
            node.put("message", violation.message());
        }
    }

    private void appendPerformanceSummary(ObjectNode root) {
        ObjectNode summary = root.putObject("performanceSummary");
        summary.put("enabled", settings.reporting().includePerformanceMetrics());
        summary.put("metricCount", performanceMetrics.size());

        ArrayNode results = root.putArray("performanceMetrics");
        for (PerformanceMetric metric : performanceMetrics.stream()
                .sorted(Comparator.comparingLong(PerformanceMetric::durationMs).reversed())
                .toList()) {
            ObjectNode node = results.addObject();
            node.put("step", metric.step());
            node.put("durationMs", metric.durationMs());
            node.put("status", metric.status());
        }
    }

    private void appendSecuritySummary(ObjectNode root) {
        long failures = securityValidationResults.stream()
                .filter(result -> !"PASS".equalsIgnoreCase(result.status()))
                .count();
        long gracefulHandlingCount = securityValidationResults.stream()
                .filter(SecurityValidationResult::handledGracefully)
                .count();

        ObjectNode summary = root.putObject("securitySummary");
        summary.put("enabled", settings.penetrationTesting().enabled());
        summary.put("executed", securityValidationResults.size());
        summary.put("handledGracefully", gracefulHandlingCount);
        summary.put("failures", failures);

        ArrayNode results = root.putArray("securityValidationResults");
        for (SecurityValidationResult result : securityValidationResults) {
            ObjectNode node = results.addObject();
            node.put("scenarioId", result.scenarioId());
            node.put("category", result.category());
            node.put("status", result.status());
            node.put("handledGracefully", result.handledGracefully());
            putIfPresent(node, "message", result.message());
            putIfPresent(node, "payloadPath", result.payloadPath());
            putIfPresent(node, "observedResponse", result.observedResponse());
            putIfPresent(node, "screenshotFile", result.screenshotFile());
        }
    }

    private void appendRecommendations(ObjectNode root) {
        if (!settings.reporting().includeRecommendations()) {
            return;
        }

        LinkedHashSet<String> recommendations = new LinkedHashSet<>();
        if (missingElementCount > 0) {
            recommendations.add("Stabilize locator strategies for fields that were not found or became hidden.");
        }
        if (unexpectedUiChangeCount > 0) {
            recommendations.add("Review recent UI changes for section/tab structure drift before retrying the workflow.");
        }
        if (!thresholdViolations.isEmpty()) {
            recommendations.add("Review configured threshold values or optimize slow sections that exceeded limits.");
        }
        if (securityValidationResults.stream().anyMatch(result -> !"PASS".equalsIgnoreCase(result.status()))) {
            recommendations.add("Investigate security scenarios that were accepted or crashed the workflow.");
        }
        if (validationFailureCount > 0 && recommendations.isEmpty()) {
            recommendations.add("Review validation diagnostics before rerunning the declaration flow.");
        }

        ArrayNode result = root.putArray("recommendations");
        for (String recommendation : recommendations) {
            result.add(recommendation);
        }
    }

    private void recordPerformanceMetric(String stepName, long durationMs) {
        String status = durationMs > settings.thresholdValidation().performanceThresholdMs()
                ? "SLOW"
                : "OK";
        performanceMetrics.add(new PerformanceMetric(stepName, durationMs, status));
        evaluateThreshold("PERFORMANCE_THRESHOLD_MS", durationMs,
                settings.thresholdValidation().performanceThresholdMs(),
                firstNonBlank(stepName, "Step") + " exceeded performance threshold.");
    }

    private void evaluateThreshold(String thresholdName, long actualValue, long thresholdValue, String message) {
        if (!settings.thresholdValidation().enabled()) {
            return;
        }
        if (actualValue <= thresholdValue) {
            return;
        }
        thresholdViolations.add(new ThresholdViolation(
                Instant.now().toString(),
                thresholdName,
                actualValue,
                thresholdValue,
                firstNonBlank(message, "Threshold exceeded.")));
    }

    private String resolveRootCause() {
        if (!thresholdViolations.isEmpty()) {
            return thresholdViolations.get(0).message();
        }
        return lowTestingResults.stream()
                .filter(result -> "WARN".equalsIgnoreCase(result.level()))
                .map(ValidationEvent::message)
                .findFirst()
                .orElseGet(() -> securityValidationResults.stream()
                        .filter(result -> !"PASS".equalsIgnoreCase(result.status()))
                        .map(SecurityValidationResult::message)
                        .findFirst()
                        .orElse("No framework-level root cause was captured."));
    }

    private void putIfPresent(ObjectNode target, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            target.put(fieldName, value);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public record ValidationEvent(
            String timestamp,
            String level,
            String category,
            String step,
            String field,
            String message,
            String expectedValue,
            String actualValue) {
    }

    public record ThresholdViolation(
            String timestamp,
            String thresholdName,
            long actualValue,
            long thresholdValue,
            String message) {
    }

    public record PerformanceMetric(
            String step,
            long durationMs,
            String status) {
    }

    public record SecurityValidationResult(
            String scenarioId,
            String category,
            String status,
            boolean handledGracefully,
            String message,
            String payloadPath,
            String observedResponse,
            String screenshotFile) {

        public static SecurityValidationResult pass(
                String scenarioId,
                String category,
                String message,
                String payloadPath,
                String observedResponse,
                String screenshotFile) {
            return new SecurityValidationResult(
                    scenarioId,
                    category == null ? "SECURITY" : category.toUpperCase(Locale.ROOT),
                    "PASS",
                    true,
                    message,
                    payloadPath,
                    observedResponse,
                    screenshotFile);
        }

        public static SecurityValidationResult fail(
                String scenarioId,
                String category,
                String message,
                String payloadPath,
                String observedResponse,
                String screenshotFile,
                boolean handledGracefully) {
            return new SecurityValidationResult(
                    scenarioId,
                    category == null ? "SECURITY" : category.toUpperCase(Locale.ROOT),
                    "FAIL",
                    handledGracefully,
                    message,
                    payloadPath,
                    observedResponse,
                    screenshotFile);
        }
    }
}
