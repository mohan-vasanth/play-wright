package com.automation.playwright_framework;

import com.automation.CooDeclarationPage;
import com.automation.DeclarationsPage;
import com.automation.IptDeclarationPage;
import com.automation.LoginPage;
import com.automation.OutDeclarationPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Service
public class LoadTestingDashboardService {

    private static final int MAX_LOG_LINES = 250;
    private static final int MAX_SCREENSHOTS = 24;
    private static final int LOGIN_RETRY_COUNT = Integer.getInteger("tradenix.login.retry.count", 3);
    private static final long LOGIN_RETRY_DELAY_MS = Long.getLong("tradenix.login.retry.delay.ms", 1500L);
    private static final String DEFAULT_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD");
    private static final String DEFAULT_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");
    private static final int LOGIN_PARALLELISM = Integer.getInteger("tradenix.login.parallelism", 12);
    private static final int BROWSER_LAUNCH_PARALLELISM = Integer.getInteger("tradenix.browser.launch.parallelism", 8);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JMeterExecutionService jMeterExecutionService;
    private final LoadTestingDeclarationCatalog declarationCatalog;
    private final DynamicJmxBuilder dynamicJmxBuilder = new DynamicJmxBuilder();
    private final Semaphore loginSemaphore = new Semaphore(LOGIN_PARALLELISM, true);
    private final Semaphore browserLaunchSemaphore = new Semaphore(BROWSER_LAUNCH_PARALLELISM, true);

    private final Deque<String> recentLogs = new ArrayDeque<>();
    private final List<ScreenshotEntry> screenshots = new ArrayList<>();
    private final List<UserJobResult> userJobResults = new CopyOnWriteArrayList<>();

    private volatile String runId;
    private volatile String status = "IDLE";
    private volatile String message = "Ready to run the load testing dashboard.";
    private volatile boolean running;
    private volatile boolean stopRequested;
    private volatile RunRequest currentRequest;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile int runningUsers;
    private volatile int openTabsCount;
    private volatile int totalTabsOpened;
    private volatile int completedUsers;
    private volatile int executedUsers;
    private volatile int successCount;
    private volatile int failureCount;
    private volatile int totalJobsCreated;
    private volatile int totalJobsSubmitted;
    private volatile int draftJobCount;
    private volatile long totalWorkflowDurationMs;
    private volatile Path runScreenshotsDirectory;
    private volatile Path runReportsDirectory;
    private volatile Path runPlaywrightLogPath;
    private volatile Thread coordinatorThread;
    private volatile ExecutorService workerPool;
    private volatile LoadTestingDeclarationCatalog.DeclarationDefinition currentDefinition;
    private volatile List<JsonNode> currentDeclarationPayloads = List.of();
    private volatile WorkflowResult latestWorkflowResult;

    public LoadTestingDashboardService(
            JMeterExecutionService jMeterExecutionService,
            LoadTestingDeclarationCatalog declarationCatalog) {
        this.jMeterExecutionService = jMeterExecutionService;
        this.declarationCatalog = declarationCatalog;
    }

    public synchronized StartResponse start(RunRequest request) {
        if (running) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A dashboard execution is already running.");
        }

        RunRequest normalizedRequest = normalizeRequest(request);
        validateRequest(normalizedRequest);
        LoadTestingDeclarationCatalog.DeclarationDefinition definition =
                declarationCatalog.resolveDefinition(normalizedRequest.declarationType());
        List<JsonNode> payloads = declarationCatalog.loadDeclarationPayloads(
                normalizedRequest.declarationType(),
                normalizedRequest.selectedJson());
        List<JsonNode> preparedPayloads = payloads.stream()
                .map(payload -> preparePayloadForWorkflow(definition, payload))
                .toList();
        List<String> payloadValidationIssues = validatePreparedPayloads(definition, preparedPayloads);
        if (!payloadValidationIssues.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selected declaration JSON is missing mandatory fields: " + String.join("; ", payloadValidationIssues));
        }

        try {
            ArtifactPaths.ensureBaseDirectories();
            this.runId = "load-dashboard-" + System.currentTimeMillis();
            this.runScreenshotsDirectory = ArtifactPaths.LOAD_DASHBOARD_SCREENSHOTS_DIR.resolve(runId);
            this.runReportsDirectory = ArtifactPaths.LOAD_DASHBOARD_REPORTS_DIR.resolve(runId);
            this.runPlaywrightLogPath = runReportsDirectory.resolve("playwright.log");
            ArtifactPaths.recreateDirectory(runScreenshotsDirectory);
            ArtifactPaths.recreateDirectory(runReportsDirectory);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to prepare dashboard artifact folders: " + exception.getMessage());
        }

        this.currentRequest = normalizedRequest;
        this.currentDefinition = definition;
        this.currentDeclarationPayloads = List.copyOf(preparedPayloads);
        this.latestWorkflowResult = null;
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.running = true;
        this.stopRequested = false;
        this.status = "RUNNING";
        this.message = "Starting declaration workflow workers and background JMeter execution.";
        this.runningUsers = 0;
        this.openTabsCount = 0;
        this.totalTabsOpened = 0;
        this.completedUsers = 0;
        this.executedUsers = 0;
        this.successCount = 0;
        this.failureCount = 0;
        this.totalJobsCreated = 0;
        this.totalJobsSubmitted = 0;
        this.draftJobCount = 0;
        this.totalWorkflowDurationMs = 0L;
        this.recentLogs.clear();
        this.screenshots.clear();
        this.userJobResults.clear();
        appendLog("Run requested for " + normalizedRequest.totalUsers() + " users with concurrency cap " + normalizedRequest.browserTabs() + " tabs.");
        appendLog("Declaration type selected: " + definition.moduleLabel() + ".");
        appendLog("Declaration JSON selected: " + normalizedRequest.selectedJson() + ".");
        appendLog("Loaded " + preparedPayloads.size() + " declaration payload(s) for cyclic execution.");
        appendLog("Browser execution is forced to headed mode so UI actions remain visible.");

        Path jmxPath = runReportsDirectory.resolve("dynamic-load-test.jmx");
        try {
            dynamicJmxBuilder.writePlan(
                    jmxPath,
                    normalizedRequest.url(),
                    normalizedRequest.totalUsers(),
                    normalizedRequest.jmeterRampUpSeconds() > 0 ? normalizedRequest.jmeterRampUpSeconds() : defaultRampUp(normalizedRequest.totalUsers()),
                    normalizedRequest.jmeterLoopCount() > 0 ? normalizedRequest.jmeterLoopCount() : 1);
            appendLog("Generated dynamic JMX plan: " + jmxPath.getFileName());
        } catch (IOException exception) {
            resetToFailedState("Unable to generate JMX plan: " + exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
        }

        JMeterExecutionService.RunRequest jmeterRequest = new JMeterExecutionService.RunRequest(
                normalizedRequest.url(),
                normalizedRequest.totalUsers(),
                normalizedRequest.jmeterRampUpSeconds() > 0 ? normalizedRequest.jmeterRampUpSeconds() : defaultRampUp(normalizedRequest.totalUsers()),
                normalizedRequest.jmeterLoopCount() > 0 ? normalizedRequest.jmeterLoopCount() : 1,
                0,
                normalizedRequest.declarationType(),
                runId,
                normalizedRequest.selectedJson());

        try {
            jMeterExecutionService.runGeneratedPlan(jmeterRequest, jmxPath);
            appendLog("Started background JMeter execution for target: " + normalizedRequest.url());
        } catch (ResponseStatusException exception) {
            resetToFailedState("Unable to start JMeter: " + exception.getReason());
            throw exception;
        }

        workerPool = Executors.newFixedThreadPool(Math.max(1, Math.min(normalizedRequest.browserTabs(), normalizedRequest.totalUsers())));
        coordinatorThread = new Thread(() -> executeRun(normalizedRequest), "load-dashboard-coordinator");
        coordinatorThread.setDaemon(true);
        coordinatorThread.start();

        return new StartResponse(true, runId, "Load testing dashboard run started.");
    }

    public synchronized StopResponse stop() {
        if (!running) {
            return new StopResponse(false, "No active load dashboard execution was found.");
        }

        stopRequested = true;
        status = "STOPPING";
        message = "Stop requested. Waiting for workflow workers to finish.";
        appendLog("Stop requested by operator.");

        if (workerPool != null) {
            workerPool.shutdownNow();
        }
        try {
            jMeterExecutionService.stop();
        } catch (Exception ignored) {
        }

        return new StopResponse(true, "Stop signal sent to Playwright workers and JMeter.");
    }

    public synchronized StatusResponse status() {
        JMeterExecutionService.StatusResponse jmeterStatus = safeJMeterStatus();
        double averageResponseTime = completedUsers > 0 ? totalWorkflowDurationMs / (double) completedUsers : 0.0d;
        double throughput = calculateThroughput();
        double errorPercentage = calculateErrorPercentage();
        String htmlReportLink = jmeterStatus != null ? jmeterStatus.openUrl() : null;
        return new StatusResponse(
                runId,
                status,
                running,
                stopRequested,
                message,
                currentRequest,
                startedAt != null ? startedAt.toString() : null,
                finishedAt != null ? finishedAt.toString() : null,
                currentRequest != null ? currentRequest.totalUsers() : 0,
                runningUsers,
                completedUsers,
                executedUsers,
                successCount,
                failureCount,
                openTabsCount,
                totalTabsOpened,
                totalJobsCreated,
                totalJobsSubmitted,
                draftJobCount,
                round(averageResponseTime),
                round(throughput),
                round(throughput),
                round(errorPercentage),
                latestWorkflowResult != null ? latestWorkflowResult.jobStatus() : null,
                latestWorkflowResult != null ? latestWorkflowResult.jobId() : null,
                latestWorkflowResult != null ? latestWorkflowResult.messageReference() : null,
                latestWorkflowResult != null ? latestWorkflowResult.permitNumber() : null,
                currentDefinition != null ? currentDefinition.moduleLabel() : null,
                currentRequest != null ? currentRequest.selectedJson() : null,
                List.copyOf(recentLogs),
                screenshots.stream()
                        .sorted(Comparator.comparing(ScreenshotEntry::capturedAt).reversed())
                        .limit(MAX_SCREENSHOTS)
                        .toList(),
                List.copyOf(userJobResults),
                jmeterStatus,
                htmlReportLink);
    }

    private void executeRun(RunRequest request) {
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int userIndex = 1; userIndex <= request.totalUsers(); userIndex++) {
                final int currentUserIndex = userIndex;
                futures.add(workerPool.submit(() -> executeSingleUser(request, currentUserIndex)));
            }

            for (Future<?> future : futures) {
                if (stopRequested) {
                    break;
                }
                try {
                    future.get();
                } catch (Exception exception) {
                    appendLog("Worker coordination error: " + rootMessage(exception));
                }
            }
        } finally {
            if (workerPool != null) {
                workerPool.shutdownNow();
            }
            synchronized (this) {
                running = false;
                finishedAt = Instant.now();
                if (stopRequested) {
                    status = "STOPPED";
                    message = "Execution stopped before all users completed.";
                } else if (failureCount > 0) {
                    status = "COMPLETED";
                    message = "Declaration workflow completed with " + failureCount + " failed users.";
                } else {
                    status = "COMPLETED";
                    message = "Declaration workflow completed successfully.";
                }
            }
            appendLog(message);
            flushPlaywrightLog();
        }
    }

    private void executeSingleUser(RunRequest request, int userIndex) {
        long startedAtMillis = System.currentTimeMillis();
        WorkerSession session = null;
        BrowserContext context = null;
        Page page = null;
        WorkflowResult result = null;
        boolean tabOpened = false;

        markUserStarted(userIndex);
        try {
            session = createWorkerSession(request);
            context = session.context();
            page = session.page();
            configurePage(page);
            tabOpened = true;
            markTabOpened(userIndex);
            result = executeWorkflow(request, userIndex, page);
            if (result.success()) {
                appendUserLog(userIndex, "Job Created Successfully"
                        + " | Status=" + firstNonBlank(result.jobStatus(), "N/A")
                        + " | Job ID=" + firstNonBlank(result.jobId(), "N/A")
                        + " | Message Ref=" + firstNonBlank(result.messageReference(), "N/A")
                        + " | Permit=" + firstNonBlank(result.permitNumber(), "N/A"));
            } else {
                appendUserLog(userIndex, "Workflow Failed - " + firstNonBlank(result.errorMessage(), result.responseMessage(), result.summary(), "Unknown error"));
            }
            captureScreenshot(page, userIndex, result.success() ? "success" : "failure", result.summary());
        } catch (Exception exception) {
            String errorMessage = rootMessage(exception);
            appendUserLog(userIndex, "Failure - " + errorMessage);
            result = new WorkflowResult(
                    false,
                    null,
                    "FAILED",
                    null,
                    null,
                    currentDefinition != null ? currentDefinition.moduleLabel() : request.declarationType(),
                    request.selectedJson(),
                    null,
                    errorMessage,
                    errorMessage,
                    false,
                    false,
                    false);
            if (page != null) {
                captureScreenshot(page, userIndex, "failure", errorMessage);
            }
        } finally {
            closeQuietly(session);
            markUserFinished(userIndex, result, System.currentTimeMillis() - startedAtMillis, tabOpened);
        }
    }

    private WorkflowResult executeWorkflow(RunRequest request, int userIndex, Page page) {
        LoadTestingDeclarationCatalog.DeclarationDefinition definition = declarationCatalog.resolveDefinition(request.declarationType());
        JsonNode payload = resolvePayloadForUser(userIndex);
        List<String> payloadIssues = validatePreparedPayload(definition, payload);
        if (!payloadIssues.isEmpty()) {
            throw new IllegalStateException("Missing Field: " + String.join(", ", payloadIssues));
        }
        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        IptDeclarationPage declarationPage = createDeclarationPage(definition, page);

        appendUserLog(userIndex, "JSON Loaded");
        appendUserLog(userIndex, "Opening Login Page");
        performLogin(loginPage, request, userIndex);
        appendUserLog(userIndex, "Login Success");

        openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
        appendUserLog(userIndex, "Selected Declaration Type - " + definition.moduleLabel());
        appendUserLog(userIndex, "Selected JSON File - " + request.selectedJson());

        declarationsPage.createNewDeclarationDraft(
                definition.route(),
                definition.expectedVisibleTexts().toArray(String[]::new));
        String initialJobId = declarationsPage.readCurrentJobIdFromUrl();
        appendUserLog(userIndex, "Declaration Created (Job ID: " + firstNonBlank(initialJobId, "pending") + ")");

        String messageReference = firstNonBlank(
                declarationPage.readCurrentMessageReference(),
                payload.path("header").path("messageReference").asText(null));

        declarationPage.populateDraftFrom(payload);
        appendUserLog(userIndex, "Form Filled");
        declarationPage.submitDeclaration();
        appendUserLog(userIndex, "Submitted");

        String diagnostics = safeDiagnostics(declarationPage);
        List<String> invalidFields = extractInvalidFieldLabels(diagnostics);
        DeclarationsPage.DeclarationListEntry submittedEntry =
                safeReadSubmittedDeclarationEntry(declarationsPage, messageReference);
        WorkflowResult result = finalizeSubmittedDeclaration(
                page,
                request,
                loginPage,
                declarationsPage,
                definition,
                payload,
                messageReference,
                submittedEntry,
                diagnostics);
        for (String invalidField : invalidFields) {
            appendUserLog(userIndex, "Missing Field: " + invalidField);
        }
        if (result.success()) {
            appendUserLog(userIndex, "Job Creation Status - " + firstNonBlank(result.jobStatus(), "SUB"));
        } else if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            appendUserLog(userIndex, "Error Details - " + truncate(result.errorMessage(), 220));
        }
        userJobResults.add(buildUserJobResult(userIndex, result));
        return result;
    }

    private WorkflowResult finalizeSubmittedDeclaration(
            Page page,
            RunRequest request,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            JsonNode payload,
            String messageReference,
            DeclarationsPage.DeclarationListEntry submittedEntry,
            String diagnostics) {
        DeclarationsPage.DeclarationListEntry trackedEntry = submittedEntry;
        try {
            openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
            trackedEntry = refreshTrackedDeclarationEntry(declarationsPage, trackedEntry, messageReference);
            if (!hasTerminalJobStatus(declarationsPage, trackedEntry)) {
                trackedEntry = declarationsPage.waitForDeclarationCompletion(
                        firstNonBlank(
                                trackedEntry != null ? trackedEntry.declarationNumber() : null,
                                messageReference),
                        trackedEntry != null ? trackedEntry.jobId() : null,
                        Long.getLong("tradenix.job.completion.timeout.ms", 180000L));
            }
        } catch (Exception exception) {
            appendLog("Declaration status tracking warning: " + rootMessage(exception));
        }

        if (trackedEntry == null) {
            trackedEntry = submittedEntry;
        }

        DeclarationsPage.DeclarationResponseDetails responseDetails =
                readTerminalResponseDetails(page, loginPage, declarationsPage, definition, request, trackedEntry, messageReference);
        trackedEntry = resolveDeclarationWithPermitNumber(
                page,
                loginPage,
                declarationsPage,
                definition,
                request,
                trackedEntry,
                messageReference,
                responseDetails);

        String jobStatus = firstNonBlank(
                trackedEntry != null ? trackedEntry.jobStatus() : null,
                inferStatusFromDiagnostics(diagnostics, responseDetails));
        String jobId = trackedEntry != null ? trackedEntry.jobId() : null;
        String resolvedMessageReference = firstNonBlank(
                trackedEntry != null ? trackedEntry.declarationNumber() : null,
                messageReference,
                payload.path("header").path("messageReference").asText(null));
        String permitNumber = firstNonBlank(
                trackedEntry != null ? trackedEntry.permitNumber() : null,
                responseDetails != null ? responseDetails.permitNumber() : null);
        String responseMessage = firstNonBlank(
                responseDetails != null ? responseDetails.responseMessage() : null,
                responseDetails != null ? responseDetails.detailText() : null,
                responseDetails != null ? responseDetails.bannerText() : null,
                extractResponseMessage(diagnostics));
        List<String> invalidFields = extractInvalidFieldLabels(diagnostics);
        String errorMessage = firstNonBlank(
                responseDetails != null ? responseDetails.errorMessage() : null,
                extractErrorMessage(diagnostics),
                !invalidFields.isEmpty() ? "Validation Error - Missing fields: " + String.join(", ", invalidFields) : null);
        boolean success = isSuccessfulJobStatus(jobStatus) || (errorMessage == null && responseMessage != null);
        String summary = buildSummary(
                definition.moduleLabel(),
                request.selectedJson(),
                jobId,
                resolvedMessageReference,
                jobStatus,
                permitNumber,
                responseMessage,
                errorMessage);

        return new WorkflowResult(
                success,
                jobId,
                jobStatus,
                resolvedMessageReference,
                permitNumber,
                definition.moduleLabel(),
                request.selectedJson(),
                responseMessage,
                errorMessage,
                summary,
                true,
                true,
                "DRF".equalsIgnoreCase(firstNonBlank(jobStatus))
                        || "DRAFT".equalsIgnoreCase(firstNonBlank(jobStatus)));
    }

    private void openDeclarationListWithRelogin(
            Page page,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            RunRequest request) {
        ensureLoggedIn(page, loginPage, request);
        declarationsPage.autoAcceptUnsavedChanges();
        declarationsPage.openDeclarationList(definition.menuLabel(), definition.route());
    }

    private void performLogin(LoginPage loginPage, RunRequest request, int userIndex) {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= LOGIN_RETRY_COUNT; attempt++) {
            try {
                acquirePermit(loginSemaphore, "login");
                try {
                    loginPage.navigate(request.url());
                    loginPage.loginAsUser(
                            request.username(),
                            request.password(),
                            preferredForwarder(),
                            preferredDepartment());
                    loginPage.waitForAuthenticatedState();
                } finally {
                    loginSemaphore.release();
                }
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                appendUserLog(userIndex, "Login Retry " + attempt + " Failed - " + rootMessage(exception));
                if (attempt < LOGIN_RETRY_COUNT) {
                    sleepQuietly(LOGIN_RETRY_DELAY_MS);
                }
            }
        }

        if (lastFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException("Login did not complete successfully.", lastFailure);
    }

    private void ensureLoggedIn(Page page, LoginPage loginPage, RunRequest request) {
        boolean onLoginPage;
        try {
            String currentUrl = page.url();
            onLoginPage = currentUrl != null && currentUrl.contains("/auth/login");
        } catch (Exception ignored) {
            onLoginPage = false;
        }

        if (!onLoginPage) {
            try {
                onLoginPage = page.locator("input[formcontrolname='username']").first().isVisible();
            } catch (Exception ignored) {
                onLoginPage = false;
            }
        }

        if (onLoginPage) {
            performLogin(loginPage, request, 0);
        }
    }

    private DeclarationsPage.DeclarationResponseDetails readTerminalResponseDetails(
            Page page,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            RunRequest request,
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            String fallbackMessageReference) {
        String trackedMessageReference = firstNonBlank(
                declarationListEntry != null ? declarationListEntry.declarationNumber() : null,
                fallbackMessageReference);
        if (trackedMessageReference == null) {
            return null;
        }

        try {
            openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
            return declarationsPage.readDeclarationResponseDetails(trackedMessageReference);
        } catch (Exception ignored) {
            return null;
        }
    }

    private DeclarationsPage.DeclarationListEntry resolveDeclarationWithPermitNumber(
            Page page,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            RunRequest request,
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            String fallbackMessageReference,
            DeclarationsPage.DeclarationResponseDetails responseDetails) {
        DeclarationsPage.DeclarationListEntry currentEntry = declarationListEntry;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (!isPermitNumberPending(currentEntry)) {
                return currentEntry;
            }

            String permitNumber = firstNonBlank(
                    currentEntry != null ? currentEntry.permitNumber() : null,
                    responseDetails != null ? responseDetails.permitNumber() : null);
            if (permitNumber != null) {
                return withPermitNumber(currentEntry, fallbackMessageReference, permitNumber);
            }

            try {
                Thread.sleep(1500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return currentEntry;
            }
            openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
            currentEntry = refreshTrackedDeclarationEntry(declarationsPage, currentEntry, fallbackMessageReference);
        }
        return currentEntry;
    }

    private DeclarationsPage.DeclarationListEntry refreshTrackedDeclarationEntry(
            DeclarationsPage declarationsPage,
            DeclarationsPage.DeclarationListEntry currentEntry,
            String fallbackMessageReference) {
        DeclarationsPage.DeclarationListEntry refreshedByReference = safeReadSubmittedDeclarationEntry(
                declarationsPage,
                firstNonBlank(
                        currentEntry != null ? currentEntry.declarationNumber() : null,
                        fallbackMessageReference));
        if (hasTrackingDetails(refreshedByReference)) {
            return refreshedByReference;
        }

        try {
            DeclarationsPage.DeclarationListEntry latestEntry = declarationsPage.readLatestDeclarationListEntry();
            if (hasTrackingDetails(latestEntry)) {
                return latestEntry;
            }
        } catch (Exception ignored) {
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

    private boolean hasTerminalJobStatus(
            DeclarationsPage declarationsPage,
            DeclarationsPage.DeclarationListEntry entry) {
        return entry != null && declarationsPage.hasTerminalJobStatus(entry.jobStatus());
    }

    private boolean isPermitNumberPending(DeclarationsPage.DeclarationListEntry entry) {
        return entry != null
                && "PMT".equalsIgnoreCase(firstNonBlank(entry.jobStatus()))
                && firstNonBlank(entry.permitNumber()) == null;
    }

    private DeclarationsPage.DeclarationListEntry withPermitNumber(
            DeclarationsPage.DeclarationListEntry declarationListEntry,
            String fallbackMessageReference,
            String permitNumber) {
        if (permitNumber == null || permitNumber.isBlank()) {
            return declarationListEntry;
        }
        return new DeclarationsPage.DeclarationListEntry(
                declarationListEntry != null ? declarationListEntry.jobId() : null,
                declarationListEntry != null ? declarationListEntry.jobStatus() : "PMT",
                firstNonBlank(
                        declarationListEntry != null ? declarationListEntry.declarationNumber() : null,
                        fallbackMessageReference),
                declarationListEntry != null ? declarationListEntry.jobCreatedBy() : null,
                permitNumber);
    }

    private DeclarationsPage.DeclarationListEntry safeReadSubmittedDeclarationEntry(
            DeclarationsPage declarationsPage,
            String messageReference) {
        try {
            return declarationsPage.readDeclarationListEntry(messageReference);
        } catch (Exception ignored) {
            return null;
        }
    }

    private IptDeclarationPage createDeclarationPage(
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            Page page) {
        return switch (definition.workflowKind()) {
            case OUT -> new OutDeclarationPage(page);
            case COO -> new CooDeclarationPage(page);
            case IPT_STYLE -> new IptDeclarationPage(page);
        };
    }

    private JsonNode resolvePayloadForUser(int userIndex) {
        if (currentDeclarationPayloads == null || currentDeclarationPayloads.isEmpty()) {
            throw new IllegalStateException("No declaration payloads are loaded for the current run.");
        }
        int index = Math.floorMod(userIndex - 1, currentDeclarationPayloads.size());
        return currentDeclarationPayloads.get(index);
    }

    private JsonNode preparePayloadForWorkflow(
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            return payload;
        }
        ObjectNode copy = payload.deepCopy();
        if (definition.workflowKind() == LoadTestingDeclarationCatalog.WorkflowKind.COO) {
            normalizeCooPayload(copy);
        }
        return copy;
    }

    private void normalizeCooPayload(ObjectNode payload) {
        ObjectNode party = objectNode(payload, "party");
        JsonNode exporterParty = party.path("exporterParty");
        if (isMissingOrBlankNode(exporterParty)) {
            JsonNode manufacturerParty = party.path("manufacturerParty");
            if (!isMissingOrBlankNode(manufacturerParty)) {
                party.set("exporterParty", manufacturerParty.deepCopy());
            }
        }

        JsonNode formMetaData = payload.path("formMetaData");
        JsonNode itemsNode = payload.path("item");
        if (!(itemsNode instanceof ArrayNode items)) {
            return;
        }

        for (int index = 0; index < items.size(); index++) {
            JsonNode itemNode = items.get(index);
            if (!(itemNode instanceof ObjectNode item)) {
                continue;
            }

            ObjectNode itemCertificate = objectNode(item, "itemCertificate");
            if (isBlank(text(item, "goodsDescription"))) {
                String descriptionFallback = firstNonBlank(
                        itemCertificateDescriptionText(itemCertificate.path("itemCertificateDescription")),
                        firstNonBlank(arrayText(item.path("shippingMarksInformation"), 0)));
                if (descriptionFallback != null) {
                    item.put("goodsDescription", descriptionFallback);
                }
            }
            if (isMissingOrBlankNode(itemCertificate.path("itemCertificateDescription"))) {
                String goodsDescription = text(item, "goodsDescription");
                if (goodsDescription != null) {
                    ArrayNode descriptionArray = OBJECT_MAPPER.createArrayNode();
                    ObjectNode descriptionEntry = OBJECT_MAPPER.createObjectNode();
                    ArrayNode lineArray = OBJECT_MAPPER.createArrayNode();
                    lineArray.add(goodsDescription);
                    descriptionEntry.set("line", lineArray);
                    descriptionArray.add(descriptionEntry);
                    itemCertificate.set("itemCertificateDescription", descriptionArray);
                }
            }
            if (isBlank(text(itemCertificate, "harmonizedSystemCode"))) {
                String hsCode = text(item, "itemHarmonizedSystemCode");
                if (hsCode != null) {
                    itemCertificate.put("harmonizedSystemCode", normalizeCooCertificateHsCode(hsCode));
                }
            } else {
                String certificateHsCode = text(itemCertificate, "harmonizedSystemCode");
                itemCertificate.put("harmonizedSystemCode", normalizeCooCertificateHsCode(certificateHsCode));
            }
            if (isBlank(text(itemCertificate, "itemValue"))) {
                String itemValue = firstNonBlank(
                        arrayText(formMetaData.path("itemValues"), index),
                        text(item, "itemCIFFOBValue"));
                if (itemValue != null) {
                    itemCertificate.put("itemValue", itemValue);
                }
            }
        }
    }

    private String normalizeCooCertificateHsCode(String hsCode) {
        if (hsCode == null) {
            return null;
        }
        String digitsOnly = hsCode.replaceAll("[^0-9A-Za-z]", "").trim();
        if (digitsOnly.isEmpty()) {
            return null;
        }
        return digitsOnly.length() > 6 ? digitsOnly.substring(0, 6) : digitsOnly;
    }

    private List<String> validatePreparedPayloads(
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            List<JsonNode> payloads) {
        List<String> issues = new ArrayList<>();
        for (int index = 0; index < payloads.size(); index++) {
            List<String> payloadIssues = validatePreparedPayload(definition, payloads.get(index));
            for (String payloadIssue : payloadIssues) {
                issues.add("Entry " + (index + 1) + ": " + payloadIssue);
            }
        }
        return issues;
    }

    private List<String> validatePreparedPayload(
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            JsonNode payload) {
        List<String> issues = new ArrayList<>();
        requireText(payload, issues, "header.messageReference");
        requireText(payload, issues, "header.applicationType");

        if (definition.workflowKind() == LoadTestingDeclarationCatalog.WorkflowKind.COO) {
            requireAnyText(payload, issues, "party.exporterParty.partyDetail.partyIdentification.id", "party.exporterParty.partyIdentification.id");
            requireAnyText(payload, issues, "party.exporterParty.partyDetail.partyName.name", "party.exporterParty.partyName.name");
            requireText(payload, issues, "transport.outwardTransport.transportMeans.transportMode.conveyanceReferenceNumber");
            requireText(payload, issues, "transport.outwardTransport.departureDate");
            requireText(payload, issues, "transport.outwardTransport.dischargePort");
            requireText(payload, issues, "transport.outwardTransport.finalDestinationCountry");
            requireText(payload, issues, "item[0].itemHarmonizedSystemCode");
            requireText(payload, issues, "item[0].goodsDescription");
            requireText(payload, issues, "item[0].originCountry");
            requireText(payload, issues, "item[0].harmonizedSystemQuantity.value");
            requireText(payload, issues, "item[0].harmonizedSystemQuantity.unitCode");
            requireText(payload, issues, "item[0].itemCertificate.itemCertificateQuantity.value");
            requireText(payload, issues, "item[0].itemCertificate.itemCertificateQuantity.unitCode");
            requireText(payload, issues, "item[0].itemCertificate.itemValue");
            requireText(payload, issues, "item[0].itemCertificate.itemInvoiceNumber");
            requireText(payload, issues, "item[0].itemCertificate.itemInvoiceDate");
            requireText(payload, issues, "item[0].itemCertificate.originCriterion[0]");
        }

        return issues;
    }

    private void requireText(JsonNode payload, List<String> issues, String pathExpression) {
        if (isBlank(readPathText(payload, pathExpression))) {
            issues.add(pathExpression);
        }
    }

    private void requireAnyText(JsonNode payload, List<String> issues, String... pathExpressions) {
        for (String pathExpression : pathExpressions) {
            if (!isBlank(readPathText(payload, pathExpression))) {
                return;
            }
        }
        issues.add(String.join(" or ", pathExpressions));
    }

    private String readPathText(JsonNode payload, String pathExpression) {
        if (payload == null || pathExpression == null || pathExpression.isBlank()) {
            return null;
        }
        JsonNode current = payload;
        for (String part : pathExpression.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }

            int bracketIndex = part.indexOf('[');
            if (bracketIndex < 0) {
                current = current.path(part);
                continue;
            }

            String fieldName = part.substring(0, bracketIndex);
            if (!fieldName.isBlank()) {
                current = current.path(fieldName);
            }

            while (bracketIndex >= 0) {
                int closingBracket = part.indexOf(']', bracketIndex);
                if (closingBracket < 0) {
                    return null;
                }
                String indexText = part.substring(bracketIndex + 1, closingBracket).trim();
                if (indexText.isBlank()) {
                    return null;
                }
                current = current.path(Integer.parseInt(indexText));
                bracketIndex = part.indexOf('[', closingBracket);
            }
        }
        return textValue(current);
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        if (node.isNumber() || node.isBoolean()) {
            return String.valueOf(node.asText());
        }
        return blankToNull(node.asText(null));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return blankToNull(value) == null;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        return textValue(node.path(fieldName));
    }

    private String arrayText(JsonNode arrayNode, int index) {
        if (arrayNode == null || !arrayNode.isArray() || index < 0 || index >= arrayNode.size()) {
            return null;
        }
        return textValue(arrayNode.get(index));
    }

    private String itemCertificateDescriptionText(JsonNode descriptionNode) {
        if (descriptionNode == null || descriptionNode.isNull() || descriptionNode.isMissingNode()) {
            return null;
        }
        if (descriptionNode.isArray()) {
            for (JsonNode descriptionEntry : descriptionNode) {
                String line = firstNonBlank(
                        arrayText(descriptionEntry.path("line"), 0),
                        text(descriptionEntry, "line"),
                        textValue(descriptionEntry));
                if (line != null) {
                    return line;
                }
            }
            return null;
        }
        return textValue(descriptionNode);
    }

    private ObjectNode objectNode(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.path(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = OBJECT_MAPPER.createObjectNode();
        parent.set(fieldName, created);
        return created;
    }

    private boolean isMissingOrBlankNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return true;
        }
        if (node.isTextual()) {
            return isBlank(node.asText());
        }
        if (node.isObject()) {
            return node.isEmpty();
        }
        if (node.isArray()) {
            return node.isEmpty();
        }
        return false;
    }

    private String safeDiagnostics(IptDeclarationPage declarationPage) {
        try {
            return declarationPage.captureSubmitValidationDiagnostics();
        } catch (Exception exception) {
            return exception.getMessage();
        }
    }

    private String inferStatusFromDiagnostics(
            String diagnostics,
            DeclarationsPage.DeclarationResponseDetails responseDetails) {
        String errorMessage = firstNonBlank(
                responseDetails != null ? responseDetails.errorMessage() : null,
                extractErrorMessage(diagnostics));
        if (errorMessage != null) {
            return "FLD";
        }

        String responseMessage = firstNonBlank(
                responseDetails != null ? responseDetails.responseMessage() : null,
                responseDetails != null ? responseDetails.detailText() : null,
                responseDetails != null ? responseDetails.bannerText() : null,
                extractResponseMessage(diagnostics));
        if (responseMessage != null) {
            return "SUB";
        }
        return "FAILED";
    }

    private boolean isSuccessfulJobStatus(String jobStatus) {
        String normalized = firstNonBlank(jobStatus);
        if (normalized == null) {
            return false;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return upper.equals("SUB")
                || upper.equals("SNT")
                || upper.equals("PMT")
                || upper.equals("SUBMITTED")
                || upper.equals("PERMIT ISSUED")
                || upper.equals("PERMIT_ISSUED");
    }

    private String extractResponseMessage(String diagnostics) {
        return extractDiagnosticsField(diagnostics, "responseMessage");
    }

    private String extractErrorMessage(String diagnostics) {
        return extractDiagnosticsField(diagnostics, "errorMessage");
    }

    private List<String> extractInvalidFieldLabels(String diagnostics) {
        if (diagnostics == null || diagnostics.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(diagnostics);
            JsonNode invalidElements = root.path("invalidElements");
            if (!invalidElements.isArray() || invalidElements.isEmpty()) {
                return List.of();
            }

            List<String> labels = new ArrayList<>();
            for (JsonNode invalidElementNode : invalidElements) {
                String rawElement = invalidElementNode.asText(null);
                if (rawElement == null || rawElement.isBlank()) {
                    continue;
                }

                String label = rawElement;
                try {
                    JsonNode element = OBJECT_MAPPER.readTree(rawElement);
                    label = firstNonBlank(
                            blankToNull(element.path("label").asText(null)),
                            blankToNull(element.path("placeholder").asText(null)),
                            blankToNull(element.path("formControlName").asText(null)),
                            blankToNull(element.path("name").asText(null)),
                            blankToNull(element.path("id").asText(null)),
                            blankToNull(element.path("text").asText(null)));
                } catch (Exception ignored) {
                }

                label = normalizeFieldLabel(label);
                if (label != null && !labels.contains(label)) {
                    labels.add(label);
                }
            }
            return List.copyOf(labels);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeFieldLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.replaceAll("[\\{\\}\\[\\]\"]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void acquirePermit(Semaphore semaphore, String purpose) {
        try {
            semaphore.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + purpose + " capacity.", exception);
        }
    }

    private String extractDiagnosticsField(String diagnostics, String fieldName) {
        if (diagnostics == null || diagnostics.isBlank()) {
            return null;
        }
        String marker = "\"" + fieldName + "\"";
        int markerIndex = diagnostics.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int colonIndex = diagnostics.indexOf(':', markerIndex);
        if (colonIndex < 0) {
            return null;
        }
        int firstQuote = diagnostics.indexOf('"', colonIndex + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = diagnostics.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return firstNonBlank(diagnostics.substring(firstQuote + 1, secondQuote)
                .replace("\\n", " ")
                .replace("\\\"", "\""));
    }

    private String buildSummary(
            String declarationType,
            String selectedJson,
            String jobId,
            String messageReference,
            String jobStatus,
            String permitNumber,
            String responseMessage,
            String errorMessage) {
        return String.join(" | ",
                "Type: " + firstNonBlank(declarationType, "N/A"),
                "JSON: " + firstNonBlank(selectedJson, "N/A"),
                "Job ID: " + firstNonBlank(jobId, "N/A"),
                "Message Ref: " + firstNonBlank(messageReference, "N/A"),
                "Status: " + firstNonBlank(jobStatus, "N/A"),
                "Permit: " + firstNonBlank(permitNumber, "N/A"),
                "Response: " + firstNonBlank(responseMessage, "N/A"),
                "Error: " + firstNonBlank(errorMessage, "N/A"));
    }

    private void configurePage(Page page) {
        page.setDefaultTimeout(Long.getLong("playwright.timeout.ms", 30000L));
        page.setDefaultNavigationTimeout(Long.getLong("playwright.navigation.timeout.ms", 60000L));
    }

    private WorkerSession createWorkerSession(RunRequest request) {
        acquirePermit(browserLaunchSemaphore, "browser launch");
        try {
            Playwright playwright = Playwright.create();
            Browser browser = launchBrowser(playwright, request);
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            return new WorkerSession(playwright, browser, context, page);
        } finally {
            browserLaunchSemaphore.release();
        }
    }

    private Browser launchBrowser(Playwright playwright, RunRequest request) {
        BrowserType browserType = playwright.chromium();
        String channel = System.getProperty("playwright.channel", "chrome");
        double slowMo = Double.parseDouble(System.getProperty("playwright.slowmo.ms", "0"));

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setSlowMo(slowMo);
        if (channel != null && !channel.isBlank()) {
            launchOptions.setChannel(channel.trim());
        }

        try {
            return browserType.launch(launchOptions);
        } catch (Exception primaryException) {
            if (channel == null || channel.isBlank()) {
                throw primaryException;
            }
            return browserType.launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setSlowMo(slowMo));
        }
    }

    private synchronized void markUserStarted(int userIndex) {
        runningUsers++;
        appendUserLog(userIndex, "Starting Declaration Workflow");
    }

    private synchronized void markTabOpened(int userIndex) {
        openTabsCount++;
        totalTabsOpened++;
        appendUserLog(userIndex, "Browser Tab Opened");
    }

    private synchronized void markUserFinished(int userIndex, WorkflowResult result, long durationMs, boolean tabOpened) {
        runningUsers = Math.max(0, runningUsers - 1);
        if (tabOpened) {
            openTabsCount = Math.max(0, openTabsCount - 1);
        }
        completedUsers++;
        executedUsers++;
        totalWorkflowDurationMs += Math.max(durationMs, 0L);

        boolean success = result != null && result.success();
        if (success) {
            successCount++;
        } else {
            failureCount++;
        }
        if (result != null && result.declarationCreated()) {
            totalJobsCreated++;
        }
        if (result != null && result.submissionAttempted()) {
            totalJobsSubmitted++;
        }
        if (result != null && result.draftStatus()) {
            draftJobCount++;
        }

        if (result != null) {
            latestWorkflowResult = result;
            message = "Latest result: user " + userIndex + " - " + truncate(result.summary(), 180);
        } else {
            message = "Latest result: user " + userIndex + " - workflow did not return a result.";
        }
    }

    private void appendUserLog(int userIndex, String line) {
        if (userIndex <= 0) {
            appendLog("[System] " + line);
            return;
        }
        appendLog("[User-" + userIndex + "] " + line);
    }

    private synchronized void appendLog(String line) {
        String stamped = Instant.now() + "  " + line;
        if (recentLogs.size() == MAX_LOG_LINES) {
            recentLogs.removeFirst();
        }
        recentLogs.addLast(stamped);
    }

    private synchronized void addScreenshot(ScreenshotEntry entry) {
        screenshots.add(entry);
        screenshots.sort(Comparator.comparing(ScreenshotEntry::capturedAt).reversed());
        while (screenshots.size() > MAX_SCREENSHOTS) {
            screenshots.remove(screenshots.size() - 1);
        }
    }

    private void captureScreenshot(Page page, int userIndex, String outcome, String summary) {
        try {
            String fileName = String.format("user-%03d-%s.png", userIndex, outcome);
            Path outputPath = runScreenshotsDirectory.resolve(fileName);
            page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(outputPath));
            addScreenshot(new ScreenshotEntry(
                    userIndex,
                    outcome.toUpperCase(Locale.ROOT),
                    "/dashboard-screenshots/load-dashboard/" + runId + "/" + fileName,
                    truncate(summary, 220),
                    Instant.now().toString()));
        } catch (Exception exception) {
            appendUserLog(userIndex, "Screenshot Capture Failed - " + rootMessage(exception));
        }
    }

    private void flushPlaywrightLog() {
        try {
            if (runPlaywrightLogPath == null) {
                return;
            }
            Files.createDirectories(runPlaywrightLogPath.getParent());
            Files.writeString(runPlaywrightLogPath, String.join(System.lineSeparator(), recentLogs), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private void resetToFailedState(String errorMessage) {
        this.running = false;
        this.status = "FAILED";
        this.message = errorMessage;
        this.finishedAt = Instant.now();
        appendLog(errorMessage);
    }

    private JMeterExecutionService.StatusResponse safeJMeterStatus() {
        try {
            return jMeterExecutionService.status();
        } catch (Exception ignored) {
            return null;
        }
    }

    private RunRequest normalizeRequest(RunRequest request) {
        if (request == null) {
            return null;
        }
        return new RunRequest(
                request.totalUsers(),
                request.browserTabs(),
                request.url(),
                request.username(),
                request.password(),
                request.declarationType(),
                request.selectedJson(),
                false,
                request.jmeterRampUpSeconds(),
                request.jmeterLoopCount());
    }

    private void validateRequest(RunRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        if (request.totalUsers() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Number of users must be greater than zero.");
        }
        if (request.browserTabs() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Number of browser tabs must be greater than zero.");
        }
        if (request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required.");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
        }
        if (request.declarationType() == null || request.declarationType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Declaration type is required.");
        }
        if (request.selectedJson() == null || request.selectedJson().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a declaration JSON file.");
        }
    }

    private void closeQuietly(BrowserContext context) {
        try {
            if (context != null) {
                context.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(Browser browser) {
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(WorkerSession session) {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(Playwright playwright) {
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ignored) {
        }
    }

    private int defaultRampUp(int totalUsers) {
        return Math.max(1, Math.min(60, totalUsers / 10 == 0 ? 1 : totalUsers / 10));
    }

    private String preferredForwarder() {
        return firstNonBlank(DEFAULT_FORWARDER);
    }

    private String preferredDepartment() {
        return firstNonBlank(DEFAULT_DEPARTMENT);
    }

    private void sleepQuietly(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry.", exception);
        }
    }

    private double calculateThroughput() {
        if (startedAt == null) {
            return 0.0d;
        }
        Instant finished = finishedAt != null ? finishedAt : Instant.now();
        long elapsedMillis = Math.max(1000L, finished.toEpochMilli() - startedAt.toEpochMilli());
        return completedUsers / (elapsedMillis / 1000.0d);
    }

    private double calculateErrorPercentage() {
        if (completedUsers <= 0) {
            return 0.0d;
        }
        return (failureCount * 100.0d) / completedUsers;
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private UserJobResult buildUserJobResult(int userIndex, WorkflowResult result) {
        String jobStatus = result != null ? firstNonBlank(result.jobStatus()) : null;
        return new UserJobResult(
                userIndex,
                result != null ? result.jobId() : null,
                result != null ? result.messageReference() : null,
                jobStatus,
                deriveFldStatus(jobStatus),
                deriveRegStatus(jobStatus),
                derivePmtStatus(jobStatus),
                result != null ? result.permitNumber() : null,
                result != null && result.success(),
                result != null ? result.errorMessage() : null,
                result != null ? result.declarationType() : null,
                result != null ? result.selectedJson() : null,
                Instant.now().toString());
    }

    private String deriveFldStatus(String jobStatus) {
        if (jobStatus == null) {
            return "PENDING";
        }
        return switch (jobStatus.toUpperCase(Locale.ROOT)) {
            case "FLD" -> "FAILED";
            case "REJ" -> "REJECTED";
            case "SUB", "SNT", "PMT", "REG" -> "COMPLETED";
            default -> "PENDING";
        };
    }

    private String deriveRegStatus(String jobStatus) {
        if (jobStatus == null) {
            return "PENDING";
        }
        return switch (jobStatus.toUpperCase(Locale.ROOT)) {
            case "REG", "PMT" -> "COMPLETED";
            case "FLD", "REJ" -> "FAILED";
            default -> "PENDING";
        };
    }

    private String derivePmtStatus(String jobStatus) {
        if (jobStatus == null) {
            return "PENDING";
        }
        return switch (jobStatus.toUpperCase(Locale.ROOT)) {
            case "PMT" -> "APPROVED";
            case "FLD", "REJ" -> "REJECTED";
            default -> "PENDING";
        };
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String messageText = current.getMessage();
        return messageText == null || messageText.isBlank()
                ? current.getClass().getSimpleName()
                : truncate(messageText, 220);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    public record UserJobResult(
            int userIndex,
            String jobId,
            String messageReference,
            String jobStatus,
            String fldStatus,
            String regStatus,
            String pmtStatus,
            String permitNumber,
            boolean success,
            String errorMessage,
            String declarationType,
            String selectedJson,
            String capturedAt) {
    }

    public record RunRequest(
            int totalUsers,
            int browserTabs,
            String url,
            String username,
            String password,
            String declarationType,
            String selectedJson,
            boolean headless,
            int jmeterRampUpSeconds,
            int jmeterLoopCount) {
    }

    public record StartResponse(
            boolean started,
            String runId,
            String message) {
    }

    public record StopResponse(
            boolean stopped,
            String message) {
    }

    public record StatusResponse(
            String runId,
            String status,
            boolean running,
            boolean stopRequested,
            String message,
            RunRequest configuration,
            String startedAt,
            String finishedAt,
            int totalUsers,
            int activeUsers,
            int completedUsers,
            int executedUsers,
            int successCount,
            int failureCount,
            int openTabsCount,
            int totalTabsOpened,
            int totalJobsCreated,
            int totalJobsSubmitted,
            int draftJobCount,
            double averageResponseTime,
            double throughput,
            double transactionsPerSecond,
            double errorPercentage,
            String latestJobStatus,
            String latestJobId,
            String latestMessageReference,
            String latestPermitNumber,
            String declarationTypeLabel,
            String selectedJson,
            List<String> recentLogs,
            List<ScreenshotEntry> screenshots,
            List<UserJobResult> userJobResults,
            JMeterExecutionService.StatusResponse jmeterStatus,
            String jmeterReportLink) {
    }

    public record ScreenshotEntry(
            int userIndex,
            String outcome,
            String imageUrl,
            String summary,
            String capturedAt) {
    }

    private record WorkflowResult(
            boolean success,
            String jobId,
            String jobStatus,
            String messageReference,
            String permitNumber,
            String declarationType,
            String selectedJson,
            String responseMessage,
            String errorMessage,
            String summary,
            boolean declarationCreated,
            boolean submissionAttempted,
            boolean draftStatus) {
    }

    private record WorkerSession(
            Playwright playwright,
            Browser browser,
            BrowserContext context,
            Page page) {
        void close() {
            try {
                if (context != null) {
                    context.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (browser != null) {
                    browser.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (playwright != null) {
                    playwright.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
