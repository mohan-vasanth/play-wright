package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.DeclarationsPage;
import com.automation.IptDeclarationPage;
import com.automation.LoginPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IptDeclarationTestCase1Test extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "mohan");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "12345678");
    private static final String USER_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD");
    private static final String USER_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");
    private static final String IPT_ROUTE = "/declarations/ipt";
    private static final String IPT_MENU_LABEL = "In-Payment (IPT)";
    private static final String TEST_DATA_RESOURCE = System.getProperty(
            "tradenix.ipt.test.data",
            "IPT/ipt-declaration-test-case-1.json");
    private static final String SELECTED_PERMIT_TYPE = normalizePermitType(
            System.getProperty("tradenix.ipt.permit.type"));
    private static final String REPORT_ARTIFACT_PREFIX = "ipt-batch-submit";

    @Test
    void submitIptDeclarationTestCase1UsingJsonData() {
        System.setProperty("tradenix.ipt.test.data", TEST_DATA_RESOURCE);
        System.setProperty("tradenix.report.artifact.prefix", REPORT_ARTIFACT_PREFIX);
        deleteExistingArtifacts(REPORT_ARTIFACT_PREFIX);
        StaticReportDataWriter.clear();

        JsonNode testData = loadTestData(TEST_DATA_RESOURCE);

        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        IptDeclarationPage iptDeclarationPage = new IptDeclarationPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);
        loginPage.waitForAuthenticatedState();

        declarationsPage.autoAcceptUnsavedChanges();
        openDeclarationListWithRelogin(loginPage, declarationsPage);
        if (testData.isArray()) {
            submitBatchDeclarations(testData, loginPage, declarationsPage, iptDeclarationPage);
            return;
        }

        boolean shouldSubmitDeclaration = shouldSubmitDeclaration(testData);
        declarationsPage.createNewDeclarationDraft(IPT_ROUTE);
        String messageReference = firstNonBlank(
                iptDeclarationPage.readCurrentMessageReference(),
                testData.path("header").path("messageReference").asText(null));

        iptDeclarationPage.populateFrom(testData);
        if (shouldSubmitDeclaration) {
            Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-validation-1.json");
            captureDiagnosticsArtifacts(
                    Paths.get("target", REPORT_ARTIFACT_PREFIX + "-1.png"),
                    diagnosticsPath,
                    iptDeclarationPage);
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
                iptDeclarationPage);
        writeDraftOutcomeToDiagnostics(
                diagnosticsPath,
                testData,
                firstNonBlank(messageReference, testData.path("header").path("messageReference").asText(null)));
        StaticReportDataWriter.refresh(REPORT_ARTIFACT_PREFIX);
        openGeneratedReport(null, 1);
        iptDeclarationPage.openInvoiceInfoSection();
        page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                .setFullPage(true)
                .setPath(Paths.get("target", "invoice-supplier-manufacturer-verification.png")));
        String expectedSupplierManufacturerName = firstNonBlank(
                testData.path("invoice").isArray() && !testData.path("invoice").isEmpty()
                        ? testData.path("invoice").get(0).path("supplierManufacturerParty").path("name").asText(null)
                        : null,
                "NAME");
        Assertions.assertEquals(expectedSupplierManufacturerName, iptDeclarationPage.readSupplierManufacturerNameValue());
    }

    private void submitBatchDeclarations(
            JsonNode declarationBatch,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            IptDeclarationPage iptDeclarationPage) {
        if (declarationBatch.isEmpty()) {
            throw new IllegalArgumentException("Batch test data must not be empty: " + TEST_DATA_RESOURCE);
        }

        DeclarationsPage.DeclarationListEntry lastCompletedEntry = null;
        for (int index = 0; index < declarationBatch.size(); index++) {
            JsonNode declaration = declarationBatch.get(index);
            openDeclarationListWithRelogin(loginPage, declarationsPage);
            declarationsPage.createNewDeclarationDraft(IPT_ROUTE);
            String messageReference = firstNonBlank(
                    iptDeclarationPage.readCurrentMessageReference(),
                    declaration.path("header").path("messageReference").asText(null));

            try {
                iptDeclarationPage.populateDraftFrom(declaration);
                iptDeclarationPage.submitDeclaration();
                Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-validation-" + (index + 1) + ".json");
                captureDiagnosticsArtifacts(
                        Paths.get("target", REPORT_ARTIFACT_PREFIX + "-" + (index + 1) + ".png"),
                        diagnosticsPath,
                        iptDeclarationPage);
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
                        iptDeclarationPage);
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

    private boolean shouldSubmitDeclaration(JsonNode data) {
        if (data.path("summary").has("submitDeclaration")) {
            return data.path("summary").path("submitDeclaration").asBoolean(false);
        }
        if (data.path("formMetaData").has("submitDeclaration")) {
            return data.path("formMetaData").path("submitDeclaration").asBoolean(false);
        }
        return true;
    }

    private void captureDiagnosticsArtifacts(
            Path screenshotPath,
            Path diagnosticsPath,
            IptDeclarationPage iptDeclarationPage) {
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(screenshotPath));
        } catch (Exception ignored) {
        }
        try {
            Files.writeString(diagnosticsPath, iptDeclarationPage.captureSubmitValidationDiagnostics());
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

        InputStream resourceStream = IptDeclarationTestCase1Test.class.getClassLoader().getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            resourceStream = IptDeclarationTestCase1Test.class.getResourceAsStream("/" + resourcePath);
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
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            DeclarationsPage.DeclarationResponseDetails responseDetails) {
        try {
            JsonNode current = OBJECT_MAPPER.readTree(Files.readString(diagnosticsPath));
            com.fasterxml.jackson.databind.node.ObjectNode root = current != null && current.isObject()
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) current
                    : OBJECT_MAPPER.createObjectNode();
            String jobId = declarationListEntry != null ? declarationListEntry.jobId() : null;
            String jobStatus = declarationListEntry != null ? declarationListEntry.jobStatus() : null;
            String declarationNumber = declarationListEntry != null ? declarationListEntry.declarationNumber() : null;
            String pmtNumber = firstNonBlank(
                    declarationListEntry != null ? declarationListEntry.permitNumber() : null,
                    responseDetails != null ? responseDetails.permitNumber() : null);
            String jobCreatedBy = sanitizeJobCreatedBy(
                    declarationListEntry != null ? declarationListEntry.jobCreatedBy() : null,
                    USER_USERNAME);
            String declarationType = resolveDeclarationType(declaration);
            String permitType = firstNonBlank(SELECTED_PERMIT_TYPE, declaration.path("permitType").asText(null));
            String resolvedJobStatus = firstNonBlank(jobStatus, inferStatusFromDiagnostics(root));
            String toastText = firstNonBlank(root.path("toastText").asText(null));
            String capturedResponseMessage = firstNonBlank(root.path("responseMessage").asText(null));
            String capturedErrorMessage = firstNonBlank(root.path("errorMessage").asText(null));
            boolean terminalIssueStatus = "REG".equals(resolvedJobStatus)
                    || "FLD".equals(resolvedJobStatus)
                    || "REJ".equals(resolvedJobStatus);
            String backendResponseMessage = firstNonBlank(
                    responseDetails != null ? responseDetails.responseMessage() : null,
                    responseDetails != null ? responseDetails.detailText() : null,
                    responseDetails != null ? responseDetails.bannerText() : null);
            String backendErrorMessage = firstNonBlank(
                    responseDetails != null ? responseDetails.errorMessage() : null,
                    backendResponseMessage);
            boolean genericSubmitMessage = isGenericSubmitMessage(capturedResponseMessage);
            String responseMessage = firstNonBlank(
                    terminalIssueStatus ? backendResponseMessage : null,
                    terminalIssueStatus && genericSubmitMessage ? null : capturedResponseMessage,
                    "SUB".equals(resolvedJobStatus) || "PMT".equals(resolvedJobStatus)
                            ? "Declaration submitted successfully."
                            : null,
                    terminalIssueStatus ? toastText : null);
            String errorMessage = firstNonBlank(
                    terminalIssueStatus ? backendErrorMessage : null,
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
            if (permitType != null && !permitType.isBlank()) {
                root.put("permitType", permitType);
            }
            if (declarationNumber != null && !declarationNumber.isBlank()) {
                root.put("declarationNumber", declarationNumber);
            }
            if (jobCreatedBy != null && !jobCreatedBy.isBlank()) {
                root.put("jobCreatedBy", jobCreatedBy);
            }
            if (pmtNumber != null && !pmtNumber.isBlank()) {
                root.put("pmtNumber", pmtNumber);
            }
            if (responseDetails != null && responseDetails.rawResponseText() != null && !responseDetails.rawResponseText().isBlank()) {
                root.put("rawResponseData", responseDetails.rawResponseText());
            }
            root.put("responseMessage", firstNonBlank(responseMessage, "N/A"));
            root.put("errorMessage", firstNonBlank(errorMessage, "N/A"));
            root.put("responseSummary", formatResponseSummary(
                    jobId,
                    declarationNumber,
                    declarationType,
                    permitType,
                    resolvedJobStatus,
                    pmtNumber,
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
            String permitType,
            String jobStatus,
            String pmtNumber,
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
                "Permit Type: " + firstNonBlank(permitType, "N/A"),
                "",
                "Status: " + firstNonBlank(jobStatus, "N/A"),
                "",
                "PMT Number: " + firstNonBlank(pmtNumber, "N/A"),
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
                    SELECTED_PERMIT_TYPE,
                    "DRF",
                    null,
                    jobCreatedBy,
                    "Declaration draft prepared successfully.",
                    "N/A"));
            Files.writeString(diagnosticsPath, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception ignored) {
        }
    }

    private static String normalizePermitType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace("PERMIT", "").replaceAll("[^A-Z]", "");
        return switch (normalized) {
            case "IG" -> "IG Permit";
            case "ID" -> "ID Permit";
            case "DP" -> "DP Permit";
            default -> value.trim();
        };
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
            if (!isTerminalJobStatus(declarationsPage, declarationListEntry)
                    && !hasTerminalDiagnosticStatus(diagnosticsPath)) {
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
            DeclarationsPage.DeclarationResponseDetails responseDetails = readTerminalResponseDetails(
                    loginPage,
                    declarationsPage,
                    declarationListEntry,
                    messageReference);
            captureStepScreenshot(statusScreenshotPath);
            writeDeclarationOutcomeToDiagnostics(diagnosticsPath, declaration, declarationListEntry, responseDetails);
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
                && declarationsPage.hasTerminalJobStatus(declarationListEntry.jobStatus())
                && !isPermitNumberPending(declarationListEntry);
    }

    private boolean isPermitNumberPending(DeclarationsPage.DeclarationListEntry declarationListEntry) {
        return declarationListEntry != null
                && "PMT".equalsIgnoreCase(firstNonBlank(declarationListEntry.jobStatus()))
                && firstNonBlank(declarationListEntry.permitNumber()) == null;
    }

    private boolean hasTerminalDiagnosticStatus(Path diagnosticsPath) {
        try {
            JsonNode diagnostics = OBJECT_MAPPER.readTree(Files.readString(diagnosticsPath));
            String inferredStatus = inferStatusFromDiagnostics(diagnostics);
            return "FLD".equals(inferredStatus) || "REG".equals(inferredStatus);
        } catch (Exception ignored) {
            return false;
        }
    }

    private DeclarationsPage.DeclarationResponseDetails readTerminalResponseDetails(
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            String fallbackMessageReference) {
        String jobStatus = declarationListEntry != null ? declarationListEntry.jobStatus() : null;
        if (jobStatus == null || (!"FLD".equalsIgnoreCase(jobStatus)
                && !"REG".equalsIgnoreCase(jobStatus)
                && !"REJ".equalsIgnoreCase(jobStatus)
                && !"PMT".equalsIgnoreCase(jobStatus))) {
            return null;
        }

        String messageReference = firstNonBlank(
                declarationListEntry != null ? declarationListEntry.declarationNumber() : null,
                fallbackMessageReference);
        if (messageReference == null) {
            return null;
        }

        try {
            openDeclarationListWithRelogin(loginPage, declarationsPage);
            return declarationsPage.readDeclarationResponseDetails(messageReference);
        } catch (Exception ignored) {
            return null;
        }
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
        declarationsPage.openDeclarationList(IPT_MENU_LABEL, IPT_ROUTE);
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
