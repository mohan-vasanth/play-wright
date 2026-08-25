package com.automation.playwright_framework;

import com.automation.IptDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

final class AutomationPenetrationRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AutomationPenetrationRunner() {
    }

    static List<AutomationExecutionDiagnostics.SecurityValidationResult> execute(
            AutomationFrameworkSettings settings,
            JsonNode baseDeclaration,
            String artifactPrefix,
            int caseIndex,
            ScenarioAdapter adapter) {
        if (settings == null || !settings.penetrationTesting().enabled() || baseDeclaration == null || adapter == null) {
            return List.of();
        }

        List<AutomationExecutionDiagnostics.SecurityValidationResult> results = new ArrayList<>();
        List<AutomationSecurityTestSupport.SecurityScenario> scenarios = AutomationSecurityTestSupport.loadScenarios(settings);
        if (scenarios.isEmpty()) {
            return results;
        }

        for (AutomationSecurityTestSupport.SecurityScenario scenario : scenarios) {
            AutomationSecurityTestSupport.AppliedScenario appliedScenario =
                    AutomationSecurityTestSupport.applyScenario(baseDeclaration, scenario);
            if (!appliedScenario.hasAppliedPaths()) {
                continue;
            }

            String screenshotFile = null;
            String payloadPath = String.join(", ", appliedScenario.appliedPaths());
            try {
                adapter.openFreshDraft();
                IptDeclarationPage declarationPage = adapter.currentPageObject();
                declarationPage.populateDraftFrom(appliedScenario.payload());
                declarationPage.submitDeclaration();

                String diagnosticsJson = declarationPage.captureSubmitValidationDiagnostics();
                JsonNode diagnostics = parseJson(diagnosticsJson);
                String observedResponse = firstNonBlank(
                        readText(diagnostics, "errorMessage"),
                        readText(diagnostics, "toastText"),
                        readText(diagnostics, "responseMessage"));
                int invalidCount = diagnostics.path("invalidElements").isArray()
                        ? diagnostics.path("invalidElements").size()
                        : 0;
                boolean gracefullyRejected = invalidCount > 0
                        || hasMeaningfulError(readText(diagnostics, "errorMessage"))
                        || hasMeaningfulError(readText(diagnostics, "toastText"));

                if (gracefullyRejected) {
                    screenshotFile = captureScenarioScreenshot(adapter.page(), artifactPrefix, caseIndex, scenario.id(), "handled");
                    results.add(AutomationExecutionDiagnostics.SecurityValidationResult.pass(
                            scenario.id(),
                            scenario.category(),
                            firstNonBlank(scenario.description(), "Application rejected invalid input safely."),
                            payloadPath,
                            observedResponse,
                            screenshotFile));
                    continue;
                }

                screenshotFile = captureScenarioScreenshot(adapter.page(), artifactPrefix, caseIndex, scenario.id(), "accepted");
                results.add(AutomationExecutionDiagnostics.SecurityValidationResult.fail(
                        scenario.id(),
                        scenario.category(),
                        "Security scenario was accepted without visible validation failure.",
                        payloadPath,
                        observedResponse,
                        screenshotFile,
                        true));
            } catch (Exception exception) {
                screenshotFile = captureScenarioScreenshot(adapter.page(), artifactPrefix, caseIndex, scenario.id(), "failure");
                results.add(AutomationExecutionDiagnostics.SecurityValidationResult.fail(
                        scenario.id(),
                        scenario.category(),
                        "Security scenario raised an exception: " + firstNonBlank(exception.getMessage(), exception.getClass().getSimpleName()),
                        payloadPath,
                        null,
                        screenshotFile,
                        false));
                if (settings.penetrationTesting().stopOnFailure()) {
                    break;
                }
            }
        }

        return List.copyOf(results);
    }

    private static JsonNode parseJson(String payload) {
        try {
            return OBJECT_MAPPER.readTree(payload);
        } catch (Exception ignored) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static String captureScenarioScreenshot(
            Page page,
            String artifactPrefix,
            int caseIndex,
            String scenarioId,
            String suffix) {
        if (page == null || page.isClosed()) {
            return null;
        }

        String sanitizedScenarioId = scenarioId == null || scenarioId.isBlank()
                ? "security"
                : scenarioId.replaceAll("[^A-Za-z0-9._-]+", "-");
        Path path = Paths.get("target",
                artifactPrefix + "-security-" + caseIndex + "-" + sanitizedScenarioId + "-" + suffix + ".png");
        try {
            page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(path));
            return path.getFileName().toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean hasMeaningfulError(String value) {
        String normalized = firstNonBlank(value);
        return normalized != null && !"N/A".equalsIgnoreCase(normalized);
    }

    private static String readText(JsonNode root, String fieldName) {
        if (root == null || fieldName == null) {
            return null;
        }
        JsonNode node = root.path(fieldName);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
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

    interface ScenarioAdapter {
        void openFreshDraft() throws Exception;

        IptDeclarationPage currentPageObject();

        Page page();
    }
}
