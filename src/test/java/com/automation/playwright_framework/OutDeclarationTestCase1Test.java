package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.DeclarationsPage;
import com.automation.LoginPage;
import com.automation.OutDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OutDeclarationTestCase1Test extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "mohan");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "12345678");
    private static final String USER_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD");
    private static final String USER_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");
    private static final String OUT_ROUTE = "/declarations/out";
    private static final String OUT_MENU_LABEL = "Out Payment (OUT)";
    private static final String TEST_DATA_RESOURCE = System.getProperty(
            "tradenix.out.test.data",
            "OUT/out-declaration-batch-test-case.json");
    private static final String REPORT_ARTIFACT_PREFIX = "out-batch-submit";

    @Test
    void submitOutDeclarationTestCase1UsingJsonData() {
        System.setProperty("tradenix.out.test.data", TEST_DATA_RESOURCE);
        System.setProperty("tradenix.report.artifact.prefix", REPORT_ARTIFACT_PREFIX);
        deleteExistingArtifacts(REPORT_ARTIFACT_PREFIX);
        StaticReportDataWriter.clear();

        JsonNode testData = loadTestData(TEST_DATA_RESOURCE);

        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        OutDeclarationPage outDeclarationPage = new OutDeclarationPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);
        loginPage.waitForAuthenticatedState();

        declarationsPage.autoAcceptUnsavedChanges();
        openDeclarationListWithRelogin(loginPage, declarationsPage);
        if (testData.isArray()) {
            submitBatchDeclarations(testData, loginPage, declarationsPage, outDeclarationPage);
            return;
        }

        boolean shouldSubmitDeclaration = testData.path("summary").path("submitDeclaration").asBoolean(false)
                || testData.path("formMetaData").path("submitDeclaration").asBoolean(false);
        declarationsPage.createNewDeclarationDraft(OUT_ROUTE);
        String messageReference = firstNonBlank(
                outDeclarationPage.readCurrentMessageReference(),
                testData.path("header").path("messageReference").asText(null));

        outDeclarationPage.populateDraftFrom(testData);
        if (shouldSubmitDeclaration) {
            outDeclarationPage.submitDeclaration();
            Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-validation-1.json");
            captureDiagnosticsArtifacts(
                    Paths.get("target", REPORT_ARTIFACT_PREFIX + "-1.png"),
                    diagnosticsPath,
                    outDeclarationPage);
            DeclarationsPage.DeclarationListEntry submittedEntry = readSubmittedDeclarationEntry(
                    declarationsPage,
                    messageReference);
            DeclarationsPage.DeclarationListEntry finalEntry = finalizeSubmittedDeclaration(
                    loginPage,
                    declarationsPage,
                    testData,
                    messageReference,
                    submittedEntry,
                    diagnosticsPath,
                    Paths.get("target", REPORT_ARTIFACT_PREFIX + "-status-1.png"));
            openGeneratedReport(finalEntry, 1);
            return;
        }
        Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-validation-1.json");
        captureDiagnosticsArtifacts(
                Paths.get("target", REPORT_ARTIFACT_PREFIX + "-1.png"),
                diagnosticsPath,
                outDeclarationPage);
        writeDraftOutcomeToDiagnostics(
                diagnosticsPath,
                testData,
                firstNonBlank(messageReference, testData.path("header").path("messageReference").asText(null)));
        StaticReportDataWriter.refresh(REPORT_ARTIFACT_PREFIX);
        openGeneratedReport(null, 1);
    }

    private void submitBatchDeclarations(
            JsonNode declarationBatch,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            OutDeclarationPage outDeclarationPage) {
        if (declarationBatch.isEmpty()) {
            throw new IllegalArgumentException("Batch test data must not be empty: " + TEST_DATA_RESOURCE);
        }

        DeclarationsPage.DeclarationListEntry lastCompletedEntry = null;
        for (int index = 0; index < declarationBatch.size(); index++) {
            JsonNode declaration = declarationBatch.get(index);
            openDeclarationListWithRelogin(loginPage, declarationsPage);
            declarationsPage.createNewDeclarationDraft(OUT_ROUTE);
            String messageReference = firstNonBlank(
                    outDeclarationPage.readCurrentMessageReference(),
                    declaration.path("header").path("messageReference").asText(null));

            try {
                outDeclarationPage.populateDraftFrom(declaration);
                outDeclarationPage.submitDeclaration();
                Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-validation-" + (index + 1) + ".json");
                captureDiagnosticsArtifacts(
                        Paths.get("target", REPORT_ARTIFACT_PREFIX + "-" + (index + 1) + ".png"),
                        diagnosticsPath,
                        outDeclarationPage);
                DeclarationsPage.DeclarationListEntry submittedEntry = readSubmittedDeclarationEntry(
                        declarationsPage,
                        messageReference);
                lastCompletedEntry = finalizeSubmittedDeclaration(
                        loginPage,
                        declarationsPage,
                        declaration,
                        messageReference,
                        submittedEntry,
                        diagnosticsPath,
                        Paths.get("target", REPORT_ARTIFACT_PREFIX + "-status-" + (index + 1) + ".png"));
            } catch (Exception exception) {
                Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-failure-" + (index + 1) + ".json");
                captureDiagnosticsArtifacts(
                        Paths.get("target", REPORT_ARTIFACT_PREFIX + "-failure-" + (index + 1) + ".png"),
                        diagnosticsPath,
                        outDeclarationPage);
                DeclarationsPage.DeclarationListEntry submittedEntry = readSubmittedDeclarationEntry(
                        declarationsPage,
                        messageReference);
                try {
                    lastCompletedEntry = finalizeSubmittedDeclaration(
                            loginPage,
                            declarationsPage,
                            declaration,
                            messageReference,
                            submittedEntry,
                            diagnosticsPath,
                            Paths.get("target", REPORT_ARTIFACT_PREFIX + "-status-" + (index + 1) + ".png"));
                } catch (Exception ignored) {
                }
                openGeneratedReport(lastCompletedEntry, declarationBatch.size());
                throw exception;
            }

            if (index < declarationBatch.size() - 1) {
                openDeclarationListWithRelogin(loginPage, declarationsPage);
            }
        }

        openGeneratedReport(lastCompletedEntry, declarationBatch.size());
    }

    private void captureDiagnosticsArtifacts(
            Path screenshotPath,
            Path diagnosticsPath,
            OutDeclarationPage outDeclarationPage) {
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(screenshotPath));
        } catch (Exception ignored) {
        }
        try {
            Files.writeString(diagnosticsPath, outDeclarationPage.captureSubmitValidationDiagnostics());
        } catch (Exception ignored) {
        }
    }

    private void captureStepScreenshot(Path screenshotPath) {
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(screenshotPath));
        } catch (Exception ignored) {
        }
    }

    private static JsonNode loadTestData(String resourcePath) {
        Path filePath = Paths.get(resourcePath);
        if (Files.exists(filePath)) {
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                return OBJECT_MAPPER.readTree(inputStream);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read test data from file: " + resourcePath, exception);
            }
        }

        InputStream resourceStream = OutDeclarationTestCase1Test.class.getClassLoader().getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            resourceStream = OutDeclarationTestCase1Test.class.getResourceAsStream("/" + resourcePath);
        }
        try (InputStream inputStream = resourceStream) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readTree(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read test data from: " + resourcePath, exception);
        }
    }

    private void writeDeclarationOutcomeToDiagnostics(
            Path diagnosticsPath,
            JsonNode declaration,
            DeclarationsPage.DeclarationListEntry declarationListEntry) {
        try {
            JsonNode current = OBJECT_MAPPER.readTree(Files.readString(diagnosticsPath));
            com.fasterxml.jackson.databind.node.ObjectNode root = current != null && current.isObject()
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) current
                    : OBJECT_MAPPER.createObjectNode();
            String jobId = declarationListEntry != null ? declarationListEntry.jobId() : null;
            String jobStatus = declarationListEntry != null ? declarationListEntry.jobStatus() : null;
            String declarationNumber = declarationListEntry != null ? declarationListEntry.declarationNumber() : null;
            String jobCreatedBy = sanitizeJobCreatedBy(
                    declarationListEntry != null ? declarationListEntry.jobCreatedBy() : null,
                    USER_USERNAME);
            String declarationType = resolveDeclarationType(declaration);
            String resolvedJobStatus = firstNonBlank(jobStatus, inferStatusFromDiagnostics(root));
            String toastText = firstNonBlank(root.path("toastText").asText(null));
            String capturedResponseMessage = firstNonBlank(root.path("responseMessage").asText(null));
            String capturedErrorMessage = firstNonBlank(root.path("errorMessage").asText(null));
            boolean terminalIssueStatus = "REG".equals(resolvedJobStatus) || "FLD".equals(resolvedJobStatus);
            boolean genericSubmitMessage = isGenericSubmitMessage(capturedResponseMessage);
            String responseMessage = firstNonBlank(
                    terminalIssueStatus && genericSubmitMessage ? null : capturedResponseMessage,
                    "SUB".equals(resolvedJobStatus) || "PMT".equals(resolvedJobStatus)
                            ? "Declaration submitted successfully."
                            : null,
                    terminalIssueStatus ? toastText : null);
            String errorMessage = firstNonBlank(
                    capturedErrorMessage,
                    terminalIssueStatus ? toastText : null,
                    terminalIssueStatus && !genericSubmitMessage ? capturedResponseMessage : null);

            if (jobId != null && !jobId.isBlank()) {
                root.put("jobId", jobId);
            }
            if (resolvedJobStatus != null && !resolvedJobStatus.isBlank() && !"N/A".equalsIgnoreCase(resolvedJobStatus)) {
                root.put("jobStatus", resolvedJobStatus);
            }
            if (declarationType != null && !declarationType.isBlank()) {
                root.put("declarationType", declarationType);
            }
            if (declarationNumber != null && !declarationNumber.isBlank()) {
                root.put("declarationNumber", declarationNumber);
            }
            if (jobCreatedBy != null && !jobCreatedBy.isBlank()) {
                root.put("jobCreatedBy", jobCreatedBy);
            }
            root.put("responseMessage", firstNonBlank(responseMessage, "N/A"));
            root.put("errorMessage", firstNonBlank(errorMessage, "N/A"));
            root.put("responseSummary", formatResponseSummary(
                    jobId,
                    declarationNumber,
                    declarationType,
                    resolvedJobStatus,
                    jobCreatedBy,
                    firstNonBlank(responseMessage, "N/A"),
                    firstNonBlank(errorMessage, "N/A")));
            Files.writeString(diagnosticsPath, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception ignored) {
        }
    }

    private String resolveDeclarationType(JsonNode declaration) {
        return firstNonBlank(
                declaration.path("header").path("declarationType").asText(null),
                declaration.path("header").path("applicationType").asText(null),
                declaration.path("header").path("commonAccessReference").asText(null),
                declaration.path("type").asText(null));
    }

    private String inferStatusFromDiagnostics(JsonNode root) {
        String errorMessage = firstNonBlank(root.path("errorMessage").asText(null), root.path("toastText").asText(null));
        String responseMessage = root.path("responseMessage").asText(null);
        if (errorMessage != null && !errorMessage.isBlank() && !"N/A".equalsIgnoreCase(errorMessage)) {
            return "FLD";
        }
        if (responseMessage != null && !responseMessage.isBlank() && !"N/A".equalsIgnoreCase(responseMessage)) {
            return "SUB";
        }
        return "N/A";
    }

    private boolean isGenericSubmitMessage(String value) {
        String normalized = firstNonBlank(value);
        return normalized != null
                && normalized.toUpperCase().replace(".", "").equals("DECLARATION SUBMITTED SUCCESSFULLY");
    }

    private String formatResponseSummary(
            String jobId,
            String declarationNumber,
            String declarationType,
            String jobStatus,
            String jobCreatedBy,
            String responseMessage,
            String errorMessage) {
        return String.join(System.lineSeparator(),
                "Job ID: " + firstNonBlank(jobId, "N/A"),
                "",
                "Message Ref: " + firstNonBlank(declarationNumber, "N/A"),
                "",
                "Declaration Type: " + firstNonBlank(declarationType, "N/A"),
                "",
                "Status: " + firstNonBlank(jobStatus, "N/A"),
                "",
                "Job Created By: " + firstNonBlank(jobCreatedBy, "N/A"),
                "",
                "Response Message: " + firstNonBlank(responseMessage, "N/A"),
                "",
                "Error Message: " + firstNonBlank(errorMessage, "N/A"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    private String sanitizeJobCreatedBy(String candidate, String fallbackUsername) {
        String normalizedCandidate = firstNonBlank(candidate);
        if (normalizedCandidate == null) {
            return firstNonBlank(fallbackUsername);
        }
        String upper = normalizedCandidate.toUpperCase();
        if (upper.matches("\\d{2}-\\d{2}-\\d{4}")
                || upper.matches("[A-Z]\\d+[A-Z]?")
                || upper.matches("DRF|SUB|SENT|PMT|FLD|REG")) {
            return firstNonBlank(fallbackUsername, normalizedCandidate);
        }
        return normalizedCandidate;
    }

    private void writeDraftOutcomeToDiagnostics(
            Path diagnosticsPath,
            JsonNode declaration,
            String fallbackMessageReference) {
        try {
            JsonNode current = OBJECT_MAPPER.readTree(Files.readString(diagnosticsPath));
            com.fasterxml.jackson.databind.node.ObjectNode root = current != null && current.isObject()
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) current
                    : OBJECT_MAPPER.createObjectNode();
            String declarationType = resolveDeclarationType(declaration);
            String declarationNumber = firstNonBlank(
                    fallbackMessageReference,
                    declaration.path("header").path("messageReference").asText(null));
            String jobCreatedBy = firstNonBlank(USER_USERNAME);
            root.put("jobStatus", "DRF");
            if (declarationType != null && !declarationType.isBlank()) {
                root.put("declarationType", declarationType);
            }
            if (declarationNumber != null && !declarationNumber.isBlank()) {
                root.put("declarationNumber", declarationNumber);
            }
            if (jobCreatedBy != null && !jobCreatedBy.isBlank()) {
                root.put("jobCreatedBy", jobCreatedBy);
            }
            root.put("responseMessage", "Declaration draft prepared successfully.");
            root.put("errorMessage", "N/A");
            root.put("responseSummary", formatResponseSummary(
                    null,
                    declarationNumber,
                    declarationType,
                    "DRF",
                    jobCreatedBy,
                    "Declaration draft prepared successfully.",
                    "N/A"));
            Files.writeString(diagnosticsPath, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception ignored) {
        }
    }

    private DeclarationsPage.DeclarationListEntry finalizeSubmittedDeclaration(
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            JsonNode declaration,
            String messageReference,
            DeclarationsPage.DeclarationListEntry submittedEntry,
            Path diagnosticsPath,
            Path statusScreenshotPath) {
        DeclarationsPage.DeclarationListEntry declarationListEntry = submittedEntry;
        try {
            if (!isTerminalJobStatus(declarationsPage, declarationListEntry)) {
                openDeclarationListWithRelogin(loginPage, declarationsPage);
                declarationListEntry = declarationsPage.waitForDeclarationCompletion(
                        firstNonBlank(
                                declarationListEntry != null ? declarationListEntry.declarationNumber() : null,
                                messageReference),
                        declarationListEntry != null ? declarationListEntry.jobId() : null,
                        Long.getLong("tradenix.job.completion.timeout.ms", 180000L));
            }
            if (declarationListEntry == null) {
                declarationListEntry = submittedEntry;
            }
            captureStepScreenshot(statusScreenshotPath);
            writeDeclarationOutcomeToDiagnostics(diagnosticsPath, declaration, declarationListEntry);
            return declarationListEntry;
        } finally {
            StaticReportDataWriter.refresh(REPORT_ARTIFACT_PREFIX);
        }
    }

    private DeclarationsPage.DeclarationListEntry readSubmittedDeclarationEntry(
            DeclarationsPage declarationsPage,
            String messageReference) {
        try {
            return declarationsPage.readDeclarationListEntry(messageReference);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isTerminalJobStatus(
            DeclarationsPage declarationsPage,
            DeclarationsPage.DeclarationListEntry declarationListEntry) {
        return declarationListEntry != null
                && declarationsPage.hasTerminalJobStatus(declarationListEntry.jobStatus());
    }

    private void openGeneratedReport(
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            int declarationCount) {
        String jobId = declarationCount == 1 && declarationListEntry != null ? declarationListEntry.jobId() : null;
        String messageReference = declarationCount == 1 && declarationListEntry != null
                ? declarationListEntry.declarationNumber()
                : null;
        AutomaticReportLauncher.open(jobId, messageReference, REPORT_ARTIFACT_PREFIX);
    }

    private void openDeclarationListWithRelogin(LoginPage loginPage, DeclarationsPage declarationsPage) {
        ensureLoggedIn(loginPage);
        declarationsPage.autoAcceptUnsavedChanges();
        declarationsPage.openDeclarationList(OUT_MENU_LABEL, OUT_ROUTE);
    }

    private void ensureLoggedIn(LoginPage loginPage) {
        String currentUrl = page.url();
        boolean onLoginPage = currentUrl != null && currentUrl.contains("/auth/login");
        if (!onLoginPage) {
            try {
                onLoginPage = page.locator("input[formcontrolname='username']").first().isVisible();
            } catch (Exception ignored) {
                onLoginPage = false;
            }
        }
        if (!onLoginPage) {
            return;
        }
        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);
        loginPage.waitForAuthenticatedState();
    }

    private void deleteExistingArtifacts(String artifactPrefix) {
        try (java.util.stream.Stream<Path> files = Files.list(Paths.get("target"))) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(artifactPrefix))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }
}
