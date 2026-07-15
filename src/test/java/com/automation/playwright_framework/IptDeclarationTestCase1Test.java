package com.automation.playwright_framework;

import base.BaseTest;
import base.TradenixLiveTest;
import com.automation.DeclarationPayloads;
import com.automation.DeclarationsPage;
import com.automation.InpDeclarationPage;
import com.automation.IptDeclarationPage;
import com.automation.LoginPage;
import com.automation.TnpDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;

@TradenixLiveTest
public class IptDeclarationTestCase1Test extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "mohan");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "12345678");
    private static final String USER_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD");
    private static final String USER_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");
    private static final String IPT_ROUTE = System.getProperty("tradenix.declaration.route", "/declarations/ipt");
    private static final String IPT_MENU_LABEL = System.getProperty("tradenix.declaration.menu.label", "In-Payment (IPT)");
    private static final String DECLARATION_FAMILY = System.getProperty("tradenix.declaration.type", "IPT")
            .trim()
            .toUpperCase(Locale.ROOT);
    private static final String TEST_DATA_RESOURCE = resolveTestDataResource();
    private static final String TEST_DATA_PROPERTY = resolveTestDataPropertyName();
    private static final String SELECTED_PERMIT_TYPE = normalizePermitType(
            System.getProperty("tradenix.ipt.permit.type"));
    private static final String REPORT_ARTIFACT_PREFIX = System.getProperty(
            "tradenix.report.artifact.prefix",
            "ipt-batch-submit");
    private static final String DEFAULT_DECLARATION_TYPE = System.getProperty("tradenix.declaration.type", "IPT");

    @Test
    void submitIptDeclarationTestCase1UsingJsonData() {
        System.setProperty(TEST_DATA_PROPERTY, TEST_DATA_RESOURCE);
        System.setProperty("tradenix.report.artifact.prefix", REPORT_ARTIFACT_PREFIX);
        deleteExistingArtifacts(REPORT_ARTIFACT_PREFIX);
        StaticReportDataWriter.clear();

        JsonNode testData = loadTestData(TEST_DATA_RESOURCE);

        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        IptDeclarationPage iptDeclarationPage = createDeclarationPage();

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);
        loginPage.waitForAuthenticatedState();
        captureStepScreenshot(Paths.get("target", REPORT_ARTIFACT_PREFIX + "-login.png"));

        declarationsPage.autoAcceptUnsavedChanges();
        openDeclarationListWithRelogin(loginPage, declarationsPage);
        if (testData.isArray()) {
            submitBatchDeclarations(testData, loginPage, declarationsPage, iptDeclarationPage);
            return;
        }

        boolean shouldSubmitDeclaration = shouldSubmitDeclaration(testData);
        Instant executionStartedAt = Instant.now();
        declarationsPage.createNewDeclarationDraft(IPT_ROUTE);
        String initialJobId = declarationsPage.readCurrentJobIdFromUrl();
        String messageReference = firstNonBlank(
                iptDeclarationPage.readCurrentMessageReference(),
                declarationMessageReference(testData));

        try {
            iptDeclarationPage.populateFrom(testData);
        } catch (Exception exception) {
            Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-failure-1.json");
            captureDiagnosticsArtifacts(
                    Paths.get("target", REPORT_ARTIFACT_PREFIX + "-failure-1.png"),
                    diagnosticsPath,
                    iptDeclarationPage);
            throw exception;
        }
        if (shouldSubmitDeclaration) {
            Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-validation-1.json");
            captureDiagnosticsArtifacts(
                    Paths.get("target", REPORT_ARTIFACT_PREFIX + "-1.png"),
                    diagnosticsPath,
                    iptDeclarationPage);
            DeclarationsPage.DeclarationListEntry submittedEntry = readSubmittedDeclarationEntry(
                    declarationsPage,
                    messageReference,
                    initialJobId);
            DeclarationsPage.DeclarationListEntry finalEntry = finalizeSubmittedDeclaration(
                    loginPage,
                    declarationsPage,
                    testData,
                    initialJobId,
                    messageReference,
                    submittedEntry,
                    executionStartedAt,
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
                initialJobId,
                firstNonBlank(messageReference, declarationMessageReference(testData)),
                executionStartedAt,
                Instant.now());
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
            Instant executionStartedAt = Instant.now();
            declarationsPage.createNewDeclarationDraft(IPT_ROUTE);
            String initialJobId = declarationsPage.readCurrentJobIdFromUrl();
            String messageReference = firstNonBlank(
                    iptDeclarationPage.readCurrentMessageReference(),
                    declarationMessageReference(declaration));

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
                        messageReference,
                        initialJobId);
                lastCompletedEntry = finalizeSubmittedDeclaration(
                        loginPage,
                        declarationsPage,
                        declaration,
                        initialJobId,
                        messageReference,
                        submittedEntry,
                        executionStartedAt,
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
                        messageReference,
                        initialJobId);
                try {
                    lastCompletedEntry = finalizeSubmittedDeclaration(
                            loginPage,
                            declarationsPage,
                            declaration,
                            initialJobId,
                            messageReference,
                            submittedEntry,
                            executionStartedAt,
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
        try {
            String diagnosticsFileName = diagnosticsPath.getFileName().toString();
            String auditFileName = diagnosticsFileName.endsWith(".json")
                    ? diagnosticsFileName.substring(0, diagnosticsFileName.length() - 5) + "-audit.json"
                    : diagnosticsFileName + "-audit.json";
            Path auditPath = diagnosticsPath.resolveSibling(auditFileName);
            Files.writeString(auditPath, iptDeclarationPage.captureRenderedFormAuditSnapshot());
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
                return DeclarationPayloads.annotatePermitType(OBJECT_MAPPER.readTree(inputStream), resourcePath);
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
            return DeclarationPayloads.annotatePermitType(OBJECT_MAPPER.readTree(inputStream), resourcePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read test data from: " + resourcePath, exception);
        }
    }

    private void writeDeclarationOutcomeToDiagnostics(
            Path diagnosticsPath,
            JsonNode declaration,
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            String initialJobId,
            String fallbackMessageReference,
            DeclarationsPage.DeclarationResponseDetails responseDetails,
            Instant executionStartedAt,
            Instant executionFinishedAt) {
        try {
            JsonNode current = OBJECT_MAPPER.readTree(Files.readString(diagnosticsPath));
            com.fasterxml.jackson.databind.node.ObjectNode root = current != null && current.isObject()
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) current
                    : OBJECT_MAPPER.createObjectNode();
            String jobId = firstNonBlank(
                    declarationListEntry != null ? declarationListEntry.jobId() : null,
                    initialJobId);
            String jobStatus = declarationListEntry != null ? declarationListEntry.jobStatus() : null;
            String declarationNumber = firstNonBlank(
                    declarationListEntry != null ? declarationListEntry.declarationNumber() : null,
                    fallbackMessageReference);
            String pmtNumber = firstNonBlank(
                    declarationListEntry != null ? declarationListEntry.permitNumber() : null,
                    responseDetails != null ? responseDetails.permitNumber() : null);
            String jobCreatedBy = sanitizeJobCreatedBy(
                    declarationListEntry != null ? declarationListEntry.jobCreatedBy() : null,
                    USER_USERNAME);
            String declarationType = resolveDeclarationType(declaration);
            String permitType = firstNonBlank(SELECTED_PERMIT_TYPE, declaration.path("permitType").asText(null));
            Boolean supplyIndicator = resolveSupplyIndicator(root, declaration);
            String resolvedJobStatus = firstNonBlank(
                    responseDetails != null ? responseDetails.status() : null,
                    jobStatus,
                    inferStatusFromDiagnostics(root));
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
            if (supplyIndicator != null) {
                root.put("supplyIndicator", supplyIndicator);
            }
            if (pmtNumber != null && !pmtNumber.isBlank()) {
                root.put("pmtNumber", pmtNumber);
            }
            if (responseDetails != null && responseDetails.rawResponseText() != null && !responseDetails.rawResponseText().isBlank()) {
                root.put("rawResponseData", responseDetails.rawResponseText());
            }
            root.put("responseMessage", firstNonBlank(responseMessage, "N/A"));
            root.put("errorMessage", firstNonBlank(errorMessage, "N/A"));
            applyExecutionTiming(root, executionStartedAt, executionFinishedAt);
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
        JsonNode payload = DeclarationPayloads.unwrap(declaration);
        return firstNonBlank(
                payload.path("header").path("declarationType").asText(null),
                payload.path("header").path("applicationType").asText(null),
                payload.path("header").path("commonAccessReference").asText(null),
                payload.path("type").asText(null),
                declaration.path("type").asText(null),
                DEFAULT_DECLARATION_TYPE);
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

    private Boolean resolveSupplyIndicator(JsonNode diagnosticsRoot, JsonNode declaration) {
        Boolean diagnosticsValue = asBooleanFlag(diagnosticsRoot != null ? diagnosticsRoot.path("supplyIndicator") : null);
        if (diagnosticsValue != null) {
            return diagnosticsValue;
        }
        JsonNode payload = DeclarationPayloads.unwrap(declaration);
        return asBooleanFlag(payload.path("cargo").path("supplyIndicator"));
    }

    private Boolean asBooleanFlag(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            String value = node.asText("").trim();
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return null;
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
            String initialJobId,
            String fallbackMessageReference,
            Instant executionStartedAt,
            Instant executionFinishedAt) {
        try {
            JsonNode current = OBJECT_MAPPER.readTree(Files.readString(diagnosticsPath));
            com.fasterxml.jackson.databind.node.ObjectNode root = current != null && current.isObject()
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) current
                    : OBJECT_MAPPER.createObjectNode();
            String declarationType = resolveDeclarationType(declaration);
            String declarationNumber = firstNonBlank(
                    fallbackMessageReference,
                    declarationMessageReference(declaration));
            String jobCreatedBy = firstNonBlank(USER_USERNAME);
            Boolean supplyIndicator = resolveSupplyIndicator(root, declaration);
            root.put("jobStatus", "DRF");
            if (initialJobId != null && !initialJobId.isBlank()) {
                root.put("jobId", initialJobId);
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
            if (supplyIndicator != null) {
                root.put("supplyIndicator", supplyIndicator);
            }
            root.put("responseMessage", "Declaration draft prepared successfully.");
            root.put("errorMessage", "N/A");
            applyExecutionTiming(root, executionStartedAt, executionFinishedAt);
            root.put("responseSummary", formatResponseSummary(
                    initialJobId,
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

    private static String resolveTestDataResource() {
        return switch (DECLARATION_FAMILY) {
            case "INP" -> System.getProperty(
                    "tradenix.inp.test.data",
                    System.getProperty("tradenix.ipt.test.data", "INP/IE PERMIT.JSON"));
            case "TNP" -> System.getProperty(
                    "tradenix.tnp.test.data",
                    System.getProperty("tradenix.ipt.test.data", "TNP/TW PERMIT.json"));
            default -> System.getProperty(
                    "tradenix.ipt.test.data",
                    "IPT/ipt-declaration-test-case-1.json");
        };
    }

    private static String resolveTestDataPropertyName() {
        return switch (DECLARATION_FAMILY) {
            case "INP" -> "tradenix.inp.test.data";
            case "TNP" -> "tradenix.tnp.test.data";
            default -> "tradenix.ipt.test.data";
        };
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
            String initialJobId,
            String messageReference,
            DeclarationsPage.DeclarationListEntry submittedEntry,
            Instant executionStartedAt,
            Path diagnosticsPath,
            Path statusScreenshotPath) {
        DeclarationsPage.DeclarationListEntry declarationListEntry = submittedEntry;
        try {
            openDeclarationListWithRelogin(loginPage, declarationsPage);
            declarationListEntry = refreshTrackedDeclarationEntry(
                    declarationsPage,
                    declarationListEntry,
                    messageReference,
                    initialJobId);
            if (!isTerminalJobStatus(declarationsPage, declarationListEntry)
                    && !hasTerminalDiagnosticStatus(diagnosticsPath)) {
                declarationListEntry = declarationsPage.waitForDeclarationCompletion(
                        firstNonBlank(
                                declarationListEntry != null ? declarationListEntry.declarationNumber() : null,
                                messageReference),
                        firstNonBlank(
                                declarationListEntry != null ? declarationListEntry.jobId() : null,
                                initialJobId),
                        Long.getLong("tradenix.job.completion.timeout.ms", 600000L));
            }
            if (declarationListEntry == null) {
                declarationListEntry = submittedEntry;
            }
            declarationListEntry = resolveDeclarationWithPermitNumber(
                    loginPage,
                    declarationsPage,
                    declarationListEntry,
                    messageReference);
            DeclarationsPage.DeclarationResponseDetails responseDetails = readTerminalResponseDetails(
                    loginPage,
                    declarationsPage,
                    declarationListEntry,
                    messageReference);
            captureStepScreenshot(statusScreenshotPath);
            writeDeclarationOutcomeToDiagnostics(
                    diagnosticsPath,
                    declaration,
                    declarationListEntry,
                    initialJobId,
                    messageReference,
                    responseDetails,
                    executionStartedAt,
                    Instant.now());
            return declarationListEntry;
        } finally {
            StaticReportDataWriter.refresh(REPORT_ARTIFACT_PREFIX);
        }
    }

    private DeclarationsPage.DeclarationListEntry readSubmittedDeclarationEntry(
            DeclarationsPage declarationsPage,
            String messageReference,
            String jobId) {
        try {
            if (jobId != null && !jobId.isBlank()) {
                DeclarationsPage.DeclarationListEntry byJobId = declarationsPage.readDeclarationListEntryByJobId(jobId);
                if (hasTrackingDetails(byJobId)) {
                    return byJobId;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (messageReference != null && !messageReference.isBlank()) {
                return declarationsPage.readDeclarationListEntry(messageReference);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private DeclarationsPage.DeclarationListEntry refreshTrackedDeclarationEntry(
            DeclarationsPage declarationsPage,
            DeclarationsPage.DeclarationListEntry currentEntry,
            String fallbackMessageReference,
            String fallbackJobId) {
        DeclarationsPage.DeclarationListEntry refreshedByReference = readSubmittedDeclarationEntry(
                declarationsPage,
                firstNonBlank(
                        currentEntry != null ? currentEntry.declarationNumber() : null,
                        fallbackMessageReference),
                firstNonBlank(
                        currentEntry != null ? currentEntry.jobId() : null,
                        fallbackJobId));
        if (hasTrackingDetails(refreshedByReference)) {
            return refreshedByReference;
        }

        DeclarationsPage.DeclarationListEntry latestEntry = declarationsPage.readLatestDeclarationListEntry();
        if (hasTrackingDetails(latestEntry)) {
            return latestEntry;
        }

        return currentEntry;
    }

    private boolean hasTrackingDetails(DeclarationsPage.DeclarationListEntry entry) {
        return entry != null
                && firstNonBlank(
                entry.jobId(),
                entry.declarationNumber(),
                entry.jobStatus(),
                entry.jobCreatedBy()) != null;
    }

    private DeclarationsPage.DeclarationListEntry resolveDeclarationWithPermitNumber(
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            String fallbackMessageReference) {
        DeclarationsPage.DeclarationListEntry currentEntry = declarationListEntry;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (!isPermitNumberPending(currentEntry)) {
                return currentEntry;
            }

            DeclarationsPage.DeclarationResponseDetails responseDetails = readTerminalResponseDetails(
                    loginPage,
                    declarationsPage,
                    currentEntry,
                    fallbackMessageReference);
            String pmtNumber = firstNonBlank(
                    currentEntry != null ? currentEntry.permitNumber() : null,
                    responseDetails != null ? responseDetails.permitNumber() : null);
            if (pmtNumber != null) {
                return withPermitNumber(currentEntry, fallbackMessageReference, pmtNumber);
            }

            page.waitForTimeout(1500);
        }
        return currentEntry;
    }

    private DeclarationsPage.DeclarationListEntry withPermitNumber(
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            String fallbackMessageReference,
            String pmtNumber) {
        if (pmtNumber == null || pmtNumber.isBlank()) {
            return declarationListEntry;
        }
        return new DeclarationsPage.DeclarationListEntry(
                declarationListEntry != null ? declarationListEntry.jobId() : null,
                declarationListEntry != null ? declarationListEntry.jobStatus() : "PMT",
                firstNonBlank(
                        declarationListEntry != null ? declarationListEntry.declarationNumber() : null,
                        fallbackMessageReference),
                declarationListEntry != null ? declarationListEntry.jobCreatedBy() : null,
                pmtNumber);
    }

    private boolean isTerminalJobStatus(
            DeclarationsPage declarationsPage,
            DeclarationsPage.DeclarationListEntry declarationListEntry) {
        return declarationListEntry != null
                && declarationsPage.hasTerminalJobStatus(declarationListEntry.jobStatus());
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
        String messageReference = firstNonBlank(
                declarationListEntry != null ? declarationListEntry.declarationNumber() : null,
                fallbackMessageReference);
        try {
            if (messageReference != null) {
                openDeclarationListWithRelogin(loginPage, declarationsPage);
                return declarationsPage.readDeclarationResponseDetails(messageReference);
            }
        } catch (Exception ignored) {
        }

        try {
            String trackedJobId = declarationListEntry != null ? declarationListEntry.jobId() : null;
            if (trackedJobId == null) {
                return null;
            }
            openDeclarationListWithRelogin(loginPage, declarationsPage);
            declarationsPage.openDeclarationViewByJobId(trackedJobId);
            return declarationsPage.readCurrentResponseDetails();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyExecutionTiming(
            com.fasterxml.jackson.databind.node.ObjectNode root,
            Instant executionStartedAt,
            Instant executionFinishedAt) {
        if (root == null || executionStartedAt == null || executionFinishedAt == null) {
            return;
        }
        root.put("startTime", executionStartedAt.toString());
        root.put("endTime", executionFinishedAt.toString());
        root.put("durationMs", Math.max(0L, executionFinishedAt.toEpochMilli() - executionStartedAt.toEpochMilli()));
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

    private IptDeclarationPage createDeclarationPage() {
        return switch (DECLARATION_FAMILY) {
            case "INP" -> new InpDeclarationPage(page);
            case "TNP" -> new TnpDeclarationPage(page);
            default -> new IptDeclarationPage(page);
        };
    }

    private String declarationMessageReference(JsonNode declaration) {
        JsonNode payload = DeclarationPayloads.unwrap(declaration);
        return firstNonBlank(
                payload.path("header").path("messageReference").asText(null),
                declaration.path("header").path("messageReference").asText(null));
    }

    private void deleteExistingArtifacts(String artifactPrefix) {
        ArtifactPaths.deleteExistingDeclarationArtifacts(artifactPrefix);
    }
}
