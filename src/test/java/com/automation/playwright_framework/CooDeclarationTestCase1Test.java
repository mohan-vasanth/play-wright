package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.CooDeclarationPage;
import com.automation.DeclarationsPage;
import com.automation.LoginPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CooDeclarationTestCase1Test extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "mohan");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "12345678");
    private static final String USER_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD");
    private static final String USER_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");
    private static final String COO_ROUTE = "/declarations/coo";
    private static final String COO_MENU_LABEL = "Certificate of Origin (COO)";
    private static final String TEST_DATA_RESOURCE = System.getProperty(
            "tradenix.coo.test.data",
            "data/coo-declaration-batch-test-case.json");
    private static final String REPORT_ARTIFACT_PREFIX = "coo-batch-submit";

    @Test
    void populateCooDeclarationUsingJsonData() {
        System.setProperty("tradenix.coo.test.data", TEST_DATA_RESOURCE);
        System.setProperty("tradenix.report.artifact.prefix", REPORT_ARTIFACT_PREFIX);
        deleteExistingArtifacts(REPORT_ARTIFACT_PREFIX);

        JsonNode testData = loadTestData(TEST_DATA_RESOURCE);

        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        CooDeclarationPage cooDeclarationPage = new CooDeclarationPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);
        loginPage.waitForAuthenticatedState();

        declarationsPage.autoAcceptUnsavedChanges();
        openDeclarationListWithRelogin(loginPage, declarationsPage);
        captureStepScreenshot(Paths.get("target", REPORT_ARTIFACT_PREFIX + "-after-navigation.png"));

        if (testData.isArray()) {
            submitBatchDeclarations(testData, loginPage, declarationsPage, cooDeclarationPage);
            return;
        }

        declarationsPage.createNewDeclarationDraft(COO_ROUTE, "Edit Declaration", "Job Info", "Header & Certificate");
        captureStepScreenshot(Paths.get("target", REPORT_ARTIFACT_PREFIX + "-form-visible-before-entry.png"));
        cooDeclarationPage.populateFrom(testData);
    }

    private static JsonNode loadTestData(String resourcePath) {
        InputStream resourceStream = CooDeclarationTestCase1Test.class.getClassLoader().getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            resourceStream = CooDeclarationTestCase1Test.class.getResourceAsStream("/" + resourcePath);
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

    private void submitBatchDeclarations(
            JsonNode declarationBatch,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            CooDeclarationPage cooDeclarationPage) {
        if (declarationBatch.isEmpty()) {
            throw new IllegalArgumentException("Batch test data must not be empty: " + TEST_DATA_RESOURCE);
        }

        for (int index = 0; index < declarationBatch.size(); index++) {
            JsonNode declaration = declarationBatch.get(index);
            String messageReference = declaration.path("header").path("messageReference").asText(null);
            openDeclarationListWithRelogin(loginPage, declarationsPage);
            captureStepScreenshot(Paths.get("target", REPORT_ARTIFACT_PREFIX + "-navigation-" + (index + 1) + ".png"));
            declarationsPage.createNewDeclarationDraft(COO_ROUTE, "Edit Declaration", "Job Info", "Header & Certificate");
            captureStepScreenshot(Paths.get("target", REPORT_ARTIFACT_PREFIX + "-form-open-" + (index + 1) + ".png"));

            try {
                cooDeclarationPage.populateFrom(declaration);
                Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-validation-" + (index + 1) + ".json");
                captureDiagnosticsArtifacts(
                        Paths.get("target", REPORT_ARTIFACT_PREFIX + "-" + (index + 1) + ".png"),
                        diagnosticsPath,
                        cooDeclarationPage);
                openDeclarationListWithRelogin(loginPage, declarationsPage);
                writeDeclarationOutcomeToDiagnostics(
                        diagnosticsPath,
                        declarationsPage.readDeclarationListEntry(messageReference));
            } catch (Exception exception) {
                Path diagnosticsPath = Paths.get("target", REPORT_ARTIFACT_PREFIX + "-failure-" + (index + 1) + ".json");
                captureDiagnosticsArtifacts(
                        Paths.get("target", REPORT_ARTIFACT_PREFIX + "-failure-" + (index + 1) + ".png"),
                        diagnosticsPath,
                        cooDeclarationPage);
                try {
                    openDeclarationListWithRelogin(loginPage, declarationsPage);
                    writeDeclarationOutcomeToDiagnostics(
                            diagnosticsPath,
                            declarationsPage.readDeclarationListEntry(messageReference));
                } catch (Exception ignored) {
                }
                throw exception;
            }

            if (index < declarationBatch.size() - 1) {
                openDeclarationListWithRelogin(loginPage, declarationsPage);
            }
        }
    }

    private void captureDiagnosticsArtifacts(
            Path screenshotPath,
            Path diagnosticsPath,
            CooDeclarationPage cooDeclarationPage) {
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(screenshotPath));
        } catch (Exception ignored) {
        }
        try {
            Files.writeString(diagnosticsPath, cooDeclarationPage.captureSubmitValidationDiagnostics());
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

    private void writeDeclarationOutcomeToDiagnostics(
            Path diagnosticsPath,
            DeclarationsPage.DeclarationListEntry declarationListEntry) {
        if (declarationListEntry == null) {
            return;
        }
        String jobId = declarationListEntry.jobId();
        String jobStatus = declarationListEntry.jobStatus();
        if ((jobId == null || jobId.isBlank()) && (jobStatus == null || jobStatus.isBlank())) {
            return;
        }
        try {
            JsonNode current = OBJECT_MAPPER.readTree(Files.readString(diagnosticsPath));
            com.fasterxml.jackson.databind.node.ObjectNode root = current != null && current.isObject()
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) current
                    : OBJECT_MAPPER.createObjectNode();
            if (jobId != null && !jobId.isBlank()) {
                root.put("jobId", jobId);
            }
            if (jobStatus != null && !jobStatus.isBlank()) {
                root.put("jobStatus", jobStatus);
            }
            Files.writeString(diagnosticsPath, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception ignored) {
        }
    }

    private void openDeclarationListWithRelogin(LoginPage loginPage, DeclarationsPage declarationsPage) {
        ensureLoggedIn(loginPage);
        declarationsPage.autoAcceptUnsavedChanges();
        declarationsPage.openDeclarationList(COO_MENU_LABEL, COO_ROUTE);
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
