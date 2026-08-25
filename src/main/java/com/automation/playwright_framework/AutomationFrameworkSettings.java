package com.automation.playwright_framework;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AutomationFrameworkSettings {

    private static final String CLASSPATH_RESOURCE = "automation-framework.properties";
    private static final Path WORKING_DIRECTORY_CONFIG = Path.of("automation-framework.properties")
            .toAbsolutePath()
            .normalize();

    private static volatile AutomationFrameworkSettings instance;

    private final LowTestingSettings lowTesting;
    private final ThresholdValidationSettings thresholdValidation;
    private final PenetrationTestingSettings penetrationTesting;
    private final ReportingSettings reporting;

    private AutomationFrameworkSettings(Properties properties) {
        this.lowTesting = new LowTestingSettings(
                readBoolean(properties, "tradenix.automation.low-testing.enabled", false),
                readBoolean(properties, "tradenix.automation.low-testing.fail-on-validation", false));
        this.thresholdValidation = new ThresholdValidationSettings(
                readBoolean(properties, "tradenix.automation.threshold-validation.enabled", false),
                readBoolean(properties, "tradenix.automation.threshold-validation.stop-on-violation", false),
                readInt(properties, "tradenix.automation.thresholds.max-retry-count", 3),
                readLong(properties, "tradenix.automation.thresholds.element-wait-timeout-ms", 15000L),
                readLong(properties, "tradenix.automation.thresholds.page-load-timeout-ms", 60000L),
                readLong(properties, "tradenix.automation.thresholds.max-execution-time-ms", 600000L),
                readInt(properties, "tradenix.automation.thresholds.max-validation-failures", 10),
                readInt(properties, "tradenix.automation.thresholds.max-skipped-fields", 20),
                readLong(properties, "tradenix.automation.thresholds.performance-threshold-ms", 3000L),
                readLong(properties, "tradenix.automation.thresholds.response-time-threshold-ms", 5000L));
        this.penetrationTesting = new PenetrationTestingSettings(
                readBoolean(properties, "tradenix.automation.penetration-testing.enabled", false),
                readBoolean(properties, "tradenix.automation.penetration-testing.stop-on-failure", false),
                readString(properties, "tradenix.automation.penetration-testing.dataset-resource",
                        "security/security-test-datasets.json"),
                readInt(properties, "tradenix.automation.penetration-testing.max-scenarios-per-declaration", 5));
        this.reporting = new ReportingSettings(
                readBoolean(properties, "tradenix.automation.reporting.capture-screenshots-on-threshold", true),
                readBoolean(properties, "tradenix.automation.reporting.include-recommendations", true),
                readBoolean(properties, "tradenix.automation.reporting.include-performance-metrics", true));
        applySystemDefaults();
    }

    public static AutomationFrameworkSettings load() {
        AutomationFrameworkSettings current = instance;
        if (current != null) {
            return current;
        }

        synchronized (AutomationFrameworkSettings.class) {
            if (instance == null) {
                instance = new AutomationFrameworkSettings(loadProperties());
            }
            return instance;
        }
    }

    public LowTestingSettings lowTesting() {
        return lowTesting;
    }

    public ThresholdValidationSettings thresholdValidation() {
        return thresholdValidation;
    }

    public PenetrationTestingSettings penetrationTesting() {
        return penetrationTesting;
    }

    public ReportingSettings reporting() {
        return reporting;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        loadClasspathResource(properties);
        loadWorkingDirectoryResource(properties);
        return properties;
    }

    private static void loadClasspathResource(Properties properties) {
        try (InputStream inputStream = AutomationFrameworkSettings.class.getClassLoader()
                .getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
        }
    }

    private static void loadWorkingDirectoryResource(Properties properties) {
        if (!Files.isRegularFile(WORKING_DIRECTORY_CONFIG)) {
            return;
        }
        try (InputStream inputStream = Files.newInputStream(WORKING_DIRECTORY_CONFIG)) {
            properties.load(inputStream);
        } catch (IOException ignored) {
        }
    }

    private void applySystemDefaults() {
        setSystemDefault("playwright.timeout.ms", thresholdValidation.elementWaitTimeoutMs());
        setSystemDefault("playwright.navigation.timeout.ms", thresholdValidation.pageLoadTimeoutMs());
        setSystemDefault("tradenix.job.completion.timeout.ms", thresholdValidation.maxExecutionTimeMs());
        setSystemDefault("tradenix.ui.lookup.wait.ms", thresholdValidation.responseTimeThresholdMs());
        setSystemDefault("tradenix.ui.post.save.ready.timeout.ms", thresholdValidation.pageLoadTimeoutMs());
    }

    private void setSystemDefault(String key, long value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, String.valueOf(value));
        }
    }

    private static String readString(Properties properties, String key, String defaultValue) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }
        String propertyValue = properties.getProperty(key);
        return propertyValue != null && !propertyValue.isBlank() ? propertyValue.trim() : defaultValue;
    }

    private static boolean readBoolean(Properties properties, String key, boolean defaultValue) {
        return Boolean.parseBoolean(readString(properties, key, String.valueOf(defaultValue)));
    }

    private static int readInt(Properties properties, String key, int defaultValue) {
        try {
            return Integer.parseInt(readString(properties, key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static long readLong(Properties properties, String key, long defaultValue) {
        try {
            return Long.parseLong(readString(properties, key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public record LowTestingSettings(
            boolean enabled,
            boolean failOnValidation) {
    }

    public record ThresholdValidationSettings(
            boolean enabled,
            boolean stopOnViolation,
            int maxRetryCount,
            long elementWaitTimeoutMs,
            long pageLoadTimeoutMs,
            long maxExecutionTimeMs,
            int maxValidationFailures,
            int maxSkippedFields,
            long performanceThresholdMs,
            long responseTimeThresholdMs) {
    }

    public record PenetrationTestingSettings(
            boolean enabled,
            boolean stopOnFailure,
            String datasetResource,
            int maxScenariosPerDeclaration) {
    }

    public record ReportingSettings(
            boolean captureScreenshotsOnThreshold,
            boolean includeRecommendations,
            boolean includePerformanceMetrics) {
    }
}
