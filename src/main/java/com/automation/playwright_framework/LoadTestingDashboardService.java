package com.automation.playwright_framework;

import com.automation.CooDeclarationPage;
import com.automation.DeclarationPayloads;
import com.automation.DeclarationsPage;
import com.automation.InpDeclarationPage;
import com.automation.IptDeclarationPage;
import com.automation.LoginPage;
import com.automation.OutDeclarationPage;
import com.automation.TnpDeclarationPage;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoadTestingDashboardService {

    private static final int MAX_LOG_LINES = 250;
    private static final int MAX_SCREENSHOTS = 24;
    private static final int LOGIN_RETRY_COUNT = Integer.getInteger("tradenix.login.retry.count", 3);
    private static final long LOGIN_RETRY_DELAY_MS = Long.getLong("tradenix.login.retry.delay.ms", 1500L);
    private static final String DEFAULT_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE. LTD.");
    private static final String DEFAULT_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");
    private static final String DEFAULT_BG_INDICATOR = System.getProperty("tradenix.default.bg.indicator", "D");
    private static final int LOGIN_PARALLELISM = Integer.getInteger("tradenix.login.parallelism", 100);
    private static final int BROWSER_LAUNCH_PARALLELISM = Integer.getInteger("tradenix.browser.launch.parallelism", 100);
    private static final int MAX_CONCURRENT_BROWSER_USERS = Math.max(1,
            Integer.getInteger("tradenix.load.max.concurrent.users", 50));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter MESSAGE_REFERENCE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter ARTIFACT_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final DateTimeFormatter AUDIT_UI_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter AUDIT_UI_SLASH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final JMeterExecutionService jMeterExecutionService;
    private final LoadTestingDeclarationCatalog declarationCatalog;
    private final ProvisionedLoadTestUserPool provisionedUserPool;
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
    private volatile int startedUsers;
    private volatile int runningUsers;
    private volatile int activeBrowserSessions;
    private volatile int activeBrowserContexts;
    private volatile int openTabsCount;
    private volatile int totalTabsOpened;
    private volatile int populatedTabsCount;
    private volatile int completedUsers;
    private volatile int executedUsers;
    private volatile int successCount;
    private volatile int failureCount;
    private volatile int totalJobsCreated;
    private volatile int totalJobsSubmitted;
    private volatile int draftJobCount;
    private volatile int pendingVerificationCount;
    private volatile long totalWorkflowDurationMs;
    private volatile long totalFilledFieldCount;
    private volatile long totalMissingFieldCount;
    private volatile Path runScreenshotsDirectory;
    private volatile Path runReportsDirectory;
    private volatile Path runPlaywrightLogPath;
    private volatile Thread coordinatorThread;
    private volatile LoadTestingDeclarationCatalog.DeclarationDefinition currentDefinition;
    private volatile WorkflowResult latestWorkflowResult;
    private volatile int currentUser;
    private volatile int currentTab;
    private volatile String currentField;
    private volatile String currentDeclaration;
    private volatile String validationStatus = "IDLE";

    public LoadTestingDashboardService(
            JMeterExecutionService jMeterExecutionService,
            LoadTestingDeclarationCatalog declarationCatalog) {
        this(jMeterExecutionService, declarationCatalog, new ProvisionedLoadTestUserPool());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LoadTestingDashboardService(
            JMeterExecutionService jMeterExecutionService,
            LoadTestingDeclarationCatalog declarationCatalog,
            ProvisionedLoadTestUserPool provisionedUserPool) {
        this.jMeterExecutionService = jMeterExecutionService;
        this.declarationCatalog = declarationCatalog;
        this.provisionedUserPool = provisionedUserPool;
    }

    public synchronized StartResponse start(RunRequest request) {
        reconcileExecutionState();
        if (running) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A dashboard execution is already running.");
        }

        RunRequest normalizedRequest = normalizeRequest(request);
        validateRequest(normalizedRequest);
        List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsers = assignedUsersFor(normalizedRequest);
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
        this.latestWorkflowResult = null;
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.running = true;
        this.stopRequested = false;
        this.status = "RUNNING";
        int requestedWorkerTabs = workerCountFor(normalizedRequest);
        this.message = "Starting "
                + requestedWorkerTabs + " isolated user worker thread(s) for "
                + normalizedRequest.totalTabRuns() + " queued user run(s). Optional JMeter HTTP load signal will run in parallel.";
        this.startedUsers = 0;
        this.runningUsers = 0;
        this.activeBrowserSessions = 0;
        this.activeBrowserContexts = 0;
        this.openTabsCount = 0;
        this.totalTabsOpened = 0;
        this.populatedTabsCount = 0;
        this.completedUsers = 0;
        this.executedUsers = 0;
        this.successCount = 0;
        this.failureCount = 0;
        this.totalJobsCreated = 0;
        this.totalJobsSubmitted = 0;
        this.draftJobCount = 0;
        this.pendingVerificationCount = 0;
        this.totalWorkflowDurationMs = 0L;
        this.totalFilledFieldCount = 0L;
        this.totalMissingFieldCount = 0L;
        this.currentUser = 0;
        this.currentTab = 0;
        this.currentField = null;
        this.currentDeclaration = definition.moduleLabel();
        this.validationStatus = "RUNNING";
        this.recentLogs.clear();
        this.screenshots.clear();
        this.userJobResults.clear();
        appendLog("Run requested for " + normalizedRequest.totalTabRuns()
                + " isolated user execution(s).");
        appendLog("Requested active user concurrency: " + normalizedRequest.browserTabs()
                + "; effective isolated worker concurrency: " + requestedWorkerTabs + ".");
        appendLog("Testing mode: " + normalizedRequest.testingMode() + ".");
        if (normalizedRequest.concurrencyLimit() != null && normalizedRequest.concurrencyLimit() > 0) {
            appendLog("Future concurrency limit configured as " + normalizedRequest.concurrencyLimit()
                    + " but ignored for the current " + normalizedRequest.testingMode() + " execution mode.");
        }
        appendLog("Total planned tab executions: " + normalizedRequest.totalTabRuns() + ".");
        appendLog("Declaration type selected: " + definition.moduleLabel() + ".");
        appendLog("Declaration JSON selected: " + normalizedRequest.selectedJson() + ".");
        appendLog("Preflight validated " + preparedPayloads.size() + " declaration payload(s).");
        appendLog("Browser execution is forced to headed mode so UI actions remain visible.");
        appendLog("Each user reads the selected JSON once and owns its payload, job state, session, BrowserContext, Page, and page objects.");
        appendLog("Each active user execution stays on its own load-testing worker thread; queued users receive fresh state when a worker slot is available.");

        Path jmxPath = runReportsDirectory.resolve("dynamic-load-test.jmx");
        boolean jmeterPlanReady = false;
        try {
            dynamicJmxBuilder.writePlan(
                    jmxPath,
                    normalizedRequest.url(),
                    normalizedRequest.totalUsers(),
                    normalizedRequest.jmeterRampUpSeconds() > 0 ? normalizedRequest.jmeterRampUpSeconds() : defaultRampUp(normalizedRequest.totalUsers()),
                    normalizedRequest.jmeterLoopCount() > 0 ? normalizedRequest.jmeterLoopCount() : 1);
            jmeterPlanReady = true;
            appendLog("Generated dynamic JMX plan: " + jmxPath.getFileName());
        } catch (IOException exception) {
            appendLog("JMeter plan generation warning: " + exception.getMessage());
            appendLog("Browser workflow execution will continue without the optional JMeter HTTP load signal.");
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

        if (jmeterPlanReady) {
            try {
                jMeterExecutionService.runGeneratedPlan(jmeterRequest, jmxPath);
                appendLog("Started optional background JMeter HTTP load execution for target: " + normalizedRequest.url());
            } catch (ResponseStatusException exception) {
                appendLog("JMeter startup warning: " + exception.getReason());
                appendLog("Browser workflow execution will continue independently of JMeter.");
            }
        } else {
            appendLog("Skipped optional JMeter execution because the JMX plan was not available.");
        }

        coordinatorThread = new Thread(
                () -> executeRunWithCrashGuard(
                        normalizedRequest, definition, List.copyOf(preparedPayloads), List.copyOf(assignedUsers)),
                "load-dashboard-coordinator");
        coordinatorThread.setDaemon(true);
        coordinatorThread.start();

        return new StartResponse(true, runId, "Browser workflow run started with isolated per-user browser sessions, contexts, and pages. Optional JMeter HTTP load signal starts only when available.");
    }

    public synchronized StopResponse stop() {
        reconcileExecutionState();
        if (!running) {
            return new StopResponse(false, "No active load dashboard execution was found.");
        }

        stopRequested = true;
        status = "STOPPING";
        message = "Stop requested. Waiting for workflow workers to finish.";
        appendLog("Stop requested by operator.");

        try {
            jMeterExecutionService.stop();
        } catch (Exception ignored) {
        }

        return new StopResponse(true, "Stop signal sent to browser workflow workers and optional JMeter execution.");
    }

    public synchronized StatusResponse status() {
        reconcileExecutionState();
        JMeterExecutionService.StatusResponse jmeterStatus = safeJMeterStatus();
        double averageResponseTime = completedUsers > 0 ? totalWorkflowDurationMs / (double) completedUsers : 0.0d;
        double p95ResponseTime = percentileExecutionTime(95);
        double p99ResponseTime = percentileExecutionTime(99);
        double averagePartyTabTime = averagePartyTabTime();
        double p95PartyTabTime = percentilePartyTabTime(95);
        double p99PartyTabTime = percentilePartyTabTime(99);
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
                currentRequest != null ? currentRequest.testingMode() : "STANDARD_LOAD",
                currentRequest != null ? currentRequest.browserTabs() : 0,
                currentRequest != null ? currentRequest.concurrencyLimit() : null,
                currentRequest != null ? currentRequest.totalUsers() : 0,
                currentRequest != null ? currentRequest.tabsPerUser() : 1,
                currentRequest != null ? currentRequest.totalTabRuns() : 0,
                startedUsers,
                runningUsers,
                activeBrowserSessions,
                activeBrowserContexts,
                completedUsers,
                executedUsers,
                successCount,
                failureCount,
                openTabsCount,
                totalTabsOpened,
                populatedTabsCount,
                totalFilledFieldCount,
                totalMissingFieldCount,
                totalJobsCreated,
                totalJobsSubmitted,
                draftJobCount,
                pendingVerificationCount,
                round(averageResponseTime),
                round(p95ResponseTime),
                round(p99ResponseTime),
                round(averagePartyTabTime),
                round(p95PartyTabTime),
                round(p99PartyTabTime),
                round(throughput),
                round(throughput),
                round(errorPercentage),
                latestWorkflowResult != null ? latestWorkflowResult.jobStatus() : null,
                latestWorkflowResult != null ? latestWorkflowResult.jobId() : null,
                latestWorkflowResult != null ? latestWorkflowResult.messageReference() : null,
                latestWorkflowResult != null ? latestWorkflowResult.permitNumber() : null,
                currentDefinition != null ? currentDefinition.moduleLabel() : null,
                currentRequest != null ? currentRequest.selectedJson() : null,
                currentUser,
                currentTab,
                currentField,
                currentDeclaration,
                validationStatus,
                List.copyOf(recentLogs),
                screenshots.stream()
                        .sorted(Comparator.comparing(ScreenshotEntry::capturedAt).reversed())
                        .limit(MAX_SCREENSHOTS)
                        .toList(),
                userJobResults.stream()
                        .sorted(Comparator.comparingInt(UserJobResult::executionOrder))
                        .toList(),
                jmeterStatus,
                htmlReportLink);
    }

    private void executeRunWithCrashGuard(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            List<JsonNode> preparedPayloads,
            List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsers) {
        try {
            executeRun(request, definition, preparedPayloads, assignedUsers);
        } catch (Throwable throwable) {
            handleCoordinatorCrash(request, definition, preparedPayloads, throwable);
        }
    }

    private void executeRun(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            List<JsonNode> preparedPayloads,
            List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsers) {
        boolean coordinatorFailed = false;
        String coordinatorFailureMessage = null;
        ExecutorService workerPool = null;
        BlockingQueue<QueuedJob> jobQueue = null;
        try {
            int requestedJobs = Math.max(1, request.totalTabRuns());
            int workerTabCount = workerCountFor(request);
            appendLog("Preparing " + workerTabCount
                    + " isolated user worker thread(s) for " + requestedJobs + " queued execution(s).");

            BlockingQueue<QueuedJob> queuedJobs = buildJobQueue(request, preparedPayloads.size());
            jobQueue = queuedJobs;

            AtomicInteger taskCounter = new AtomicInteger(0);
            workerPool = Executors.newFixedThreadPool(workerTabCount, r -> {
                Thread t = new Thread(r, "load-dashboard-worker-" + taskCounter.incrementAndGet());
                t.setDaemon(true);
                return t;
            });

            List<Future<?>> futures = new ArrayList<>();
            for (int workerTabNumber = 1; workerTabNumber <= workerTabCount && !stopRequested; workerTabNumber++) {
                final int isolatedWorkerTabNumber = workerTabNumber;
                futures.add(workerPool.submit(
                        () -> executeIsolatedUserWorker(
                                request,
                                definition,
                                isolatedWorkerTabNumber,
                                queuedJobs,
                                assignedUsers)));
            }

            workerPool.shutdown();
            try {
                workerPool.awaitTermination(24L * 60 * 60, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                stopRequested = true;
            }
            if (!stopRequested && jobQueue != null && !jobQueue.isEmpty()) {
                appendLog("One or more queued executions were not processed by the isolated user workers.");
                recordUnprocessedJobs(request, definition, jobQueue, assignedUsers,
                        "Execution did not start because an isolated user worker terminated early.");
            }

        } catch (Throwable exception) {
            coordinatorFailed = true;
            coordinatorFailureMessage = rootMessage(exception);
            appendLog("Run failed: " + coordinatorFailureMessage);
            recordRunBootstrapFailure(request, definition, preparedPayloads, coordinatorFailureMessage);
        } finally {
            if (workerPool != null && !workerPool.isTerminated()) {
                workerPool.shutdownNow();
            }
            synchronized (this) {
                openTabsCount = 0;
                activeBrowserSessions = 0;
                activeBrowserContexts = 0;
                running = false;
                coordinatorThread = null;
                finishedAt = Instant.now();
                List<String> finalValidationIssues = validateCompletedRun(request.totalTabRuns());
                int unresolvedCount = Math.max(0, completedUsers - successCount - failureCount);
                if (stopRequested) {
                    status = "STOPPED";
                    message = "Execution stopped before all tab runs completed.";
                } else if (coordinatorFailed) {
                    status = "FAILED";
                    message = "Execution failed: " + coordinatorFailureMessage;
                } else if (!finalValidationIssues.isEmpty()) {
                    status = "FAILED";
                    message = "Execution failed final validation: " + String.join(" | ", finalValidationIssues);
                } else if (failureCount > 0) {
                    status = "FAILED";
                    message = "Declaration workflow completed with " + failureCount + " failed tab execution(s).";
                } else if (unresolvedCount > 0) {
                    status = "FAILED";
                    message = "Declaration workflow completed with " + unresolvedCount + " tab execution(s) pending verification.";
                } else {
                    status = "COMPLETED";
                    message = "Declaration workflow completed successfully across "
                            + request.totalTabRuns() + " tab execution(s).";
                }
            }
            appendLog(message);
            flushPlaywrightLog();
        }
    }

    private synchronized void reconcileExecutionState() {
        Thread activeCoordinator = coordinatorThread;
        if (activeCoordinator != null && !activeCoordinator.isAlive()) {
            coordinatorThread = null;
        }
        if (!running) {
            return;
        }
        if (activeCoordinator != null && activeCoordinator.isAlive()) {
            return;
        }

        running = false;
        activeBrowserSessions = 0;
        activeBrowserContexts = 0;
        openTabsCount = 0;
        finishedAt = finishedAt != null ? finishedAt : Instant.now();
        status = stopRequested ? "STOPPED" : "FAILED";
        message = stopRequested
                ? "Execution stopped after the coordinator thread exited."
                : "Execution ended unexpectedly before the coordinator thread reported completion.";
        appendLog("Recovered stale dashboard execution state after the coordinator thread was no longer alive.");
        flushPlaywrightLog();
    }

    private synchronized void handleCoordinatorCrash(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            List<JsonNode> preparedPayloads,
            Throwable throwable) {
        String crashMessage = rootMessage(throwable);
        appendLog("Coordinator thread crashed before normal completion: " + crashMessage);
        if (userJobResults.isEmpty()) {
            recordRunBootstrapFailure(request, definition, preparedPayloads, crashMessage);
        }
        running = false;
        activeBrowserSessions = 0;
        activeBrowserContexts = 0;
        openTabsCount = 0;
        finishedAt = finishedAt != null ? finishedAt : Instant.now();
        status = "FAILED";
        message = "Execution failed: " + crashMessage;
        coordinatorThread = null;
        flushPlaywrightLog();
    }

    private BlockingQueue<QueuedJob> buildJobQueue(
            RunRequest request,
            int payloadCount) {
        if (payloadCount < 1) {
            throw new IllegalStateException("No declaration JSON records are available for the current run.");
        }
        BlockingQueue<QueuedJob> jobQueue = new LinkedBlockingQueue<>();
        for (int userIndex = 1; userIndex <= request.totalUsers(); userIndex++) {
            int executionOrder = userIndex;
            int recordNumber = Math.floorMod(executionOrder - 1, payloadCount) + 1;
            jobQueue.add(new QueuedJob(executionOrder, userIndex, 1, recordNumber));
        }
        return jobQueue;
    }

    private void executeIsolatedUserWorker(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            int workerTabNumber,
            BlockingQueue<QueuedJob> jobQueue,
            List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsers) {
        try {
            while (!stopRequested) {
                QueuedJob queuedJob = jobQueue.poll();
                if (queuedJob == null) {
                    break;
                }

                JobWorkItem job = null;
                WorkerBrowserSession userSession = null;
                RunRequest jobRequest = request;
                boolean sessionOpened = false;
                boolean contextOpened = false;
                boolean tabOpened = false;
                boolean executionStarted = false;
                try {
                    jobRequest = requestForJob(request, queuedJob, assignedUsers);
                    job = loadUserJobWorkItem(jobRequest, definition, queuedJob);
                    appendUserLog(queuedJob.userIndex(), "JSON Loaded Once - " + jobRequest.selectedJson());
                    userSession = createWorkerBrowserSession(
                            jobRequest,
                            job.executionOrder(),
                            workerTabNumber);
                    Page userPage = userSession.page();
                    configurePage(userPage);
                    markBrowserSessionOpened(userSession.browserId());
                    sessionOpened = true;
                    markWorkerContextOpened(userSession.contextId());
                    contextOpened = true;
                    markWorkerTabOpened(workerTabNumber);
                    tabOpened = true;
                    appendLog("User " + queuedJob.userIndex() + " owns browser session "
                            + userSession.browserId() + ", context " + userSession.contextId()
                            + ", page, JSON payload, job state, and page objects on thread "
                            + Thread.currentThread().getName() + ".");

                    executionStarted = true;
                    executeSingleQueuedJob(
                            jobRequest,
                            definition,
                            job,
                            userSession.browserId(),
                            userSession.contextId(),
                            userPage,
                            false,
                            workerTabNumber);
                } catch (Throwable exception) {
                    appendUserLog(queuedJob.userIndex(), "Worker tab " + workerTabNumber
                            + " failed - " + rootMessage(exception));
                    if (!executionStarted) {
                        recordIsolatedJobFailure(
                                jobRequest,
                                definition,
                                queuedJob,
                                job,
                                workerTabNumber,
                                rootMessage(exception));
                    }
                } finally {
                    closeQuietly(userSession);
                    if (tabOpened) {
                        markWorkerTabClosed(workerTabNumber);
                    }
                    if (contextOpened && userSession != null) {
                        markWorkerContextClosed(userSession.contextId());
                    }
                    if (sessionOpened && userSession != null) {
                        markBrowserSessionClosed(userSession.browserId());
                    }
                }
            }
        } catch (Throwable exception) {
            appendLog("Isolated user worker " + workerTabNumber + " failed - " + rootMessage(exception));
        }
    }

    private RunRequest requestForJob(
            RunRequest request,
            JobWorkItem job,
            List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsers) {
        return requestForUserIndex(request, job.userIndex(), assignedUsers);
    }

    private RunRequest requestForJob(
            RunRequest request,
            QueuedJob job,
            List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsers) {
        return requestForUserIndex(request, job.userIndex(), assignedUsers);
    }

    private RunRequest requestForUserIndex(
            RunRequest request,
            int userIndex,
            List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsers) {
        if (!usesAssignedUsers(request)) {
            return request;
        }
        int assignedUserIndex = userIndex - 1;
        if (assignedUsers == null || assignedUserIndex < 0 || assignedUserIndex >= assignedUsers.size()) {
            throw new IllegalStateException("No assigned login credentials were found for user " + userIndex + ".");
        }
        return requestForProvisionedUser(request, assignedUsers.get(assignedUserIndex));
    }

    private JobWorkItem loadUserJobWorkItem(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            QueuedJob queuedJob) {
        List<JsonNode> userPayloads = declarationCatalog.loadDeclarationPayloads(
                request.declarationType(),
                request.selectedJson());
        if (userPayloads.isEmpty()) {
            throw new IllegalStateException("Selected declaration JSON contains no records.");
        }
        int payloadIndex = queuedJob.recordNumber() - 1;
        if (payloadIndex < 0 || payloadIndex >= userPayloads.size()) {
            throw new IllegalStateException(
                    "JSON record " + queuedJob.recordNumber()
                            + " is not available in the selected declaration JSON.");
        }

        // This is the only JSON read for this user. The selected record is prepared
        // through the same normal-mode preparation path, then remains the sole input
        // passed to validation, population, auditing, and result construction.
        JsonNode userPayload = preparePayloadForWorkflow(
                definition,
                userPayloads.get(payloadIndex));
        if (userPayload instanceof ObjectNode objectNode) {
            userPayload = personalizePayloadForUser(
                    objectNode,
                    queuedJob.userIndex(),
                    queuedJob.userTabNumber(),
                    queuedJob.executionOrder());
        }
        return new JobWorkItem(
                queuedJob.executionOrder(),
                queuedJob.userIndex(),
                queuedJob.userTabNumber(),
                queuedJob.recordNumber(),
                userPayload);
    }


    private void executeSingleQueuedJob(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            JobWorkItem job,
            int browserSessionId,
            int browserContextId,
            Page page,
            boolean sessionReady,
            int workerTabNumber) {
        long startedAtMillis = System.currentTimeMillis();
        Instant startedAtInstant = Instant.now();
        WorkflowResult result = null;
        ScreenshotEntry screenshotEntry = null;
        int userIndex = job.userIndex();
        int logicalTabNumber = workerTabNumber;

        markUserStarted(userIndex, logicalTabNumber);
        try {
            result = executeWorkflow(request, definition, job, browserSessionId, browserContextId, logicalTabNumber, page, sessionReady);
            if (result.submissionVerified()) {
                appendUserLog(userIndex, "Job Submitted Successfully"
                        + " | Creation Status=" + firstNonBlank(result.creationStatus(), "N/A")
                        + " | Submission Status=" + firstNonBlank(result.submissionStatus(), "N/A")
                        + " | Job ID=" + firstNonBlank(result.jobId(), "N/A")
                        + " | Message Ref=" + firstNonBlank(result.messageReference(), "N/A")
                        + " | Permit=" + firstNonBlank(result.permitNumber(), "N/A"));
            } else if (result.creationVerified()) {
                appendUserLog(userIndex, "Job Created But Submission Not Verified"
                        + " | Creation Status=" + firstNonBlank(result.creationStatus(), "N/A")
                        + " | Submission Status=" + firstNonBlank(result.submissionStatus(), "N/A")
                        + " | Job ID=" + firstNonBlank(result.jobId(), "N/A")
                        + " | Error=" + firstNonBlank(result.errorMessage(), "N/A"));
            } else {
                appendUserLog(userIndex, "Workflow Failed - " + firstNonBlank(result.errorMessage(), result.responseMessage(), result.summary(), "Unknown error"));
            }
            screenshotEntry = resolveCompletionScreenshot(page, userIndex, result);
        } catch (Throwable exception) {
            String errorMessage = rootMessage(exception);
            appendUserLog(userIndex, "Failure - " + errorMessage);
            result = buildWorkflowFailureResult(
                    request,
                    definition,
                    null,
                    null,
                    job.recordNumber(),
                    userIndex,
                    logicalTabNumber,
                    0,
                    0,
                    false,
                    errorMessage,
                    List.of());
            if (page != null) {
                screenshotEntry = resolveCompletionScreenshot(page, userIndex, result);
            }
        } finally {
            long finishedAtMillis = System.currentTimeMillis();
            userJobResults.add(buildUserJobResult(
                    job.executionOrder(),
                    userIndex,
                    logicalTabNumber,
                    browserSessionId,
                    browserContextId,
                    job.payload(),
                    result,
                    screenshotEntry,
                    startedAtInstant,
                    Instant.ofEpochMilli(finishedAtMillis),
                    finishedAtMillis - startedAtMillis));
            markUserFinished(userIndex, logicalTabNumber, result, finishedAtMillis - startedAtMillis);
        }
    }

    private void recordIsolatedJobFailure(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            QueuedJob queuedJob,
            JobWorkItem job,
            int workerTabNumber,
            String errorMessage) {
        long startedAtMillis = System.currentTimeMillis();
        Instant startedAtInstant = Instant.ofEpochMilli(startedAtMillis);
        int userIndex = queuedJob.userIndex();
        JsonNode payload = job != null && job.payload() != null
                ? job.payload()
                : OBJECT_MAPPER.createObjectNode();
        markUserStarted(userIndex, workerTabNumber);
        WorkflowResult result = buildWorkflowFailureResult(
                request,
                definition,
                null,
                payload.path("header").path("messageReference").asText(null),
                queuedJob.recordNumber(),
                userIndex,
                workerTabNumber,
                0,
                0,
                false,
                firstNonBlank(errorMessage, "Isolated user session could not be created."),
                List.of());
        long finishedAtMillis = System.currentTimeMillis();
        userJobResults.add(buildUserJobResult(
                queuedJob.executionOrder(),
                userIndex,
                workerTabNumber,
                0,
                0,
                payload,
                result,
                null,
                startedAtInstant,
                Instant.ofEpochMilli(finishedAtMillis),
                Math.max(0L, finishedAtMillis - startedAtMillis)));
        markUserFinished(userIndex, workerTabNumber, result,
                Math.max(0L, finishedAtMillis - startedAtMillis));
    }

    private WorkflowResult executeWorkflow(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            JobWorkItem job,
            int browserSessionId,
            int browserContextId,
            int tabNumber,
            Page page,
            boolean sessionReady) {
        int userIndex = job.userIndex();
        JsonNode payload = job.payload();
        int jsonRecordNumber = job.recordNumber();
        List<String> payloadIssues = validatePreparedPayload(definition, payload);
        if (!payloadIssues.isEmpty()) {
            throw new IllegalStateException("Missing Field: " + String.join(", ", payloadIssues));
        }
        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        IptDeclarationPage declarationPage = createDeclarationPage(definition, page);
        configureDeclarationFieldLogging(
                declarationPage,
                new ExecutionIdentity(
                        browserSessionId,
                        browserContextId,
                        tabNumber,
                        userIndex,
                        job.executionOrder(),
                        Thread.currentThread().getName()),
                definition,
                page);
        String initialJobId = null;
        String messageReference = payload.path("header").path("messageReference").asText(null);
        int filledFieldCount = 0;
        boolean submissionAttempted = false;
        EvidenceArtifacts evidenceArtifacts = null;
        FormAuditResult formAuditResult = FormAuditResult.empty();

        try {
            appendUserLog(userIndex, "Tab Number - " + tabNumber);
            appendUserLog(userIndex, "Browser ID - " + browserSessionId);
            appendUserLog(userIndex, "Context ID - " + browserContextId);
            appendUserLog(userIndex, "Thread ID - " + Thread.currentThread().getName());
            appendUserLog(userIndex, "Execution Order - " + job.executionOrder());
            appendUserLog(userIndex, "JSON File - " + request.selectedJson());
            appendUserLog(userIndex, "JSON Record Number - " + jsonRecordNumber);
            if (!sessionReady) {
                openWorkerPage(loginPage, request, userIndex);
            }
            appendUserLog(userIndex, "Login Session Ready");

            openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
            appendUserLog(userIndex, "Selected Declaration Type - " + definition.moduleLabel());
            appendUserLog(userIndex, "Selected JSON File - " + request.selectedJson());

            declarationsPage.createNewDeclarationDraft(
                    definition.route(),
                    definition.expectedVisibleTexts().toArray(String[]::new));
            initialJobId = declarationsPage.readCurrentJobIdFromUrl();
            if (initialJobId != null && !initialJobId.isBlank()) {
                appendUserLog(userIndex, "Declaration Created (Job ID: " + initialJobId + ")");
            } else {
                appendUserLog(userIndex, "Declaration Draft Opened - Job ID not generated yet");
            }

            messageReference = waitForCurrentMessageReference(declarationPage, payload);

            appendUserLog(userIndex, "Starting Form Population");
            declarationPage.populateDraftFrom(payload);
            filledFieldCount = declarationPage.validatedFieldEntryCount();
            appendUserLog(userIndex, "Form Filled - " + filledFieldCount + " fields validated");
            formAuditResult = auditJsonAgainstRenderedForm(payload, declarationPage);
            if (formAuditResult.hasFailures()) {
                appendUserLog(userIndex, "JSON Audit Warning - " + formAuditResult.failureCount()
                        + " value(s) were not rendered in the UI snapshot");
                for (FailureDetail failureDetail : formAuditResult.failureDetails()) {
                    appendUserLog(userIndex, "JSON Audit Detail - " + formatFailureDetail(failureDetail));
                }
                if (isStrictJsonAuditEnabled()) {
                    String auditSummary = summarizeFailureDetails(formAuditResult.failureDetails(), 5);
                    WorkflowResult auditFailureResult = buildWorkflowFailureResult(
                            request,
                            definition,
                            initialJobId,
                            messageReference,
                            jsonRecordNumber,
                            userIndex,
                            tabNumber,
                            filledFieldCount,
                            formAuditResult.failureCount(),
                            false,
                            "JSON-to-UI validation failed: " + auditSummary,
                            List.of(),
                            formAuditResult.failureDetails());
                    return withDiagnosticsMetrics(buildWorkflowFailureResult(
                            request,
                            definition,
                            initialJobId,
                            messageReference,
                            jsonRecordNumber,
                            userIndex,
                            tabNumber,
                            filledFieldCount,
                            formAuditResult.failureCount(),
                            false,
                            "JSON-to-UI validation failed: " + auditSummary,
                            List.of(),
                            formAuditResult.failureDetails(),
                            captureFailureEvidence(
                                    page,
                                    userIndex,
                                    auditFailureResult,
                                    formAuditResult.snapshot(),
                                    List.of(),
                                    formAuditResult.failureDetails())), safeDiagnostics(declarationPage));
                }
                appendUserLog(userIndex, "JSON Audit Override - continuing to Save Draft and Submit because server-side validation is authoritative");
            }

            // Pre-submission validation: check for invalid fields before submitting
            String preDiagnostics = safeDiagnostics(declarationPage);
            List<ValidationIssue> preValidationIssues = extractValidationIssues(preDiagnostics);
            if (!preValidationIssues.isEmpty()) {
                appendUserLog(userIndex, "Pre-Submit Check: " + preValidationIssues.size()
                        + " invalid field(s) detected - declaration will not be restarted or submitted");
                for (ValidationIssue issue : preValidationIssues) {
                    appendUserLog(userIndex, "Invalid Before Submit: " + formatValidationIssue(issue));
                }
                String validationSummary = summarizeValidationIssues(preValidationIssues, 5);
                appendUserLog(userIndex, "Pre-Submit Validation Failed - " + validationSummary);
                List<FailureDetail> failureDetails =
                        buildFailureDetails(preValidationIssues, "Pre-Submit Validation", Instant.now().toString());
                WorkflowResult validationFailure = buildWorkflowFailureResult(
                        request,
                        definition,
                        initialJobId,
                        messageReference,
                        jsonRecordNumber,
                        userIndex,
                        tabNumber,
                        filledFieldCount,
                        preValidationIssues.size(),
                        false,
                        "Pre-submit validation failed: " + validationSummary,
                        preValidationIssues,
                        failureDetails);
                EvidenceArtifacts validationEvidence = captureFailureEvidence(
                        page,
                        userIndex,
                        validationFailure,
                        preDiagnostics,
                        preValidationIssues,
                        failureDetails);
                return withDiagnosticsMetrics(buildWorkflowFailureResult(
                        request,
                        definition,
                        initialJobId,
                        messageReference,
                        jsonRecordNumber,
                        userIndex,
                        tabNumber,
                        filledFieldCount,
                        preValidationIssues.size(),
                        false,
                        "Pre-submit validation failed: " + validationSummary,
                        preValidationIssues,
                        failureDetails,
                        validationEvidence), preDiagnostics);
            } else {
                appendUserLog(userIndex, "Pre-Submit Check: all fields valid");
            }

            appendUserLog(userIndex, "Starting Save Draft and Submit Declaration workflow");
            declarationPage.submitDeclaration();
            submissionAttempted = true;
            appendUserLog(userIndex, "Submit Declaration clicked - waiting for final declaration status");

            String diagnostics = safeDiagnostics(declarationPage);
            List<ValidationIssue> validationIssues = extractValidationIssues(diagnostics);
            List<String> invalidFields = validationIssues.stream()
                    .map(this::validationIssueLabel)
                    .distinct()
                    .toList();
            int missingFieldCount = validationIssues.size();
            String immediateFailureMessage = firstNonBlank(
                    extractErrorMessage(diagnostics),
                    !validationIssues.isEmpty() ? "Validation Error - " + summarizeValidationIssues(validationIssues, 5) : null);
            if (immediateFailureMessage != null) {
                List<FailureDetail> validationFailureDetails =
                        buildFailureDetails(validationIssues, "Submission Validation", Instant.now().toString());
                evidenceArtifacts = captureFailureEvidence(
                        page,
                        userIndex,
                        buildWorkflowFailureResult(
                                request,
                                definition,
                                initialJobId,
                                messageReference,
                                jsonRecordNumber,
                                userIndex,
                                tabNumber,
                                filledFieldCount,
                                missingFieldCount,
                                true,
                                immediateFailureMessage,
                                validationIssues,
                                validationFailureDetails),
                        diagnostics,
                        validationIssues,
                        validationFailureDetails);
            }
            DeclarationsPage.DeclarationListEntry submittedEntry =
                    safeReadSubmittedDeclarationEntry(declarationsPage, messageReference, initialJobId);
            WorkflowResult result = finalizeSubmittedDeclaration(
                    page,
                    request,
                    loginPage,
                    declarationsPage,
                    definition,
                    payload,
                    initialJobId,
                    messageReference,
                    submittedEntry,
                    diagnostics,
                    validationIssues,
                    evidenceArtifacts,
                    jsonRecordNumber,
                    userIndex,
                    tabNumber,
                    filledFieldCount,
                    missingFieldCount);
            appendUserLog(userIndex, "Filled Fields Count - " + filledFieldCount);
            appendUserLog(userIndex, "Missing Fields Count - " + missingFieldCount);
            appendUserLog(userIndex, "Creation Status - " + firstNonBlank(result.creationStatus(), "FAILED"));
            appendUserLog(userIndex, "Submission Status - " + firstNonBlank(result.submissionStatus(), "FAILED"));
            for (String invalidField : invalidFields) {
                appendUserLog(userIndex, "Missing Field: " + invalidField);
            }
            if (!invalidFields.isEmpty()) {
                appendUserLog(userIndex, "Validation Diagnostics - " + truncate(diagnostics, 1200));
            }
            if (result.submissionVerified()) {
                appendUserLog(userIndex, "Verified Status - " + firstNonBlank(result.jobStatus(), "SUB"));
            } else if (result.creationVerified()) {
                appendUserLog(userIndex, "Verified Job ID - " + firstNonBlank(result.jobId(), initialJobId, "N/A"));
            } else if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
                appendUserLog(userIndex, "Error Details - " + truncate(result.errorMessage(), 220));
            }
            return withDiagnosticsMetrics(result, diagnostics);
        } catch (Exception exception) {
            String diagnostics = safeDiagnostics(declarationPage);
            List<ValidationIssue> validationIssues = extractValidationIssues(diagnostics);
            WorkflowResult failureResult = buildWorkflowFailureResult(
                    request,
                    definition,
                    initialJobId,
                    messageReference,
                    jsonRecordNumber,
                    userIndex,
                    tabNumber,
                    filledFieldCount,
                    validationIssues.size(),
                    submissionAttempted,
                    rootMessage(exception),
                    validationIssues,
                    buildFailureDetails(validationIssues, "Workflow Execution", Instant.now().toString(), rootMessage(exception)));
            EvidenceArtifacts capturedEvidence = captureFailureEvidence(
                    page,
                    userIndex,
                    failureResult,
                    diagnostics,
                    validationIssues,
                    failureResult.failureDetails());
            return withDiagnosticsMetrics(buildWorkflowFailureResult(
                    request,
                    definition,
                    initialJobId,
                    messageReference,
                    jsonRecordNumber,
                    userIndex,
                    tabNumber,
                    filledFieldCount,
                    validationIssues.size(),
                    submissionAttempted,
                    rootMessage(exception),
                    validationIssues,
                    failureResult.failureDetails(),
                    capturedEvidence), diagnostics);
        }
    }

    private WorkflowResult buildWorkflowFailureResult(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            String jobId,
            String messageReference,
            int jsonRecordNumber,
            int userIndex,
            int tabNumber,
            int filledFieldCount,
            int missingFieldCount,
            boolean submissionAttempted,
            String errorMessage,
            List<ValidationIssue> validationIssues) {
        return buildWorkflowFailureResult(
                request,
                definition,
                jobId,
                messageReference,
                jsonRecordNumber,
                userIndex,
                tabNumber,
                filledFieldCount,
                missingFieldCount,
                submissionAttempted,
                errorMessage,
                validationIssues,
                buildFailureDetails(validationIssues, "Workflow Failure", Instant.now().toString(), errorMessage),
                null);
    }

    private WorkflowResult buildWorkflowFailureResult(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            String jobId,
            String messageReference,
            int jsonRecordNumber,
            int userIndex,
            int tabNumber,
            int filledFieldCount,
            int missingFieldCount,
            boolean submissionAttempted,
            String errorMessage,
            List<ValidationIssue> validationIssues,
            List<FailureDetail> failureDetails) {
        return buildWorkflowFailureResult(
                request,
                definition,
                jobId,
                messageReference,
                jsonRecordNumber,
                userIndex,
                tabNumber,
                filledFieldCount,
                missingFieldCount,
                submissionAttempted,
                errorMessage,
                validationIssues,
                failureDetails,
                null);
    }

    private WorkflowResult buildWorkflowFailureResult(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            String jobId,
            String messageReference,
            int jsonRecordNumber,
            int userIndex,
            int tabNumber,
            int filledFieldCount,
            int missingFieldCount,
            boolean submissionAttempted,
            String errorMessage,
            List<ValidationIssue> validationIssues,
            List<FailureDetail> failureDetails,
            EvidenceArtifacts evidenceArtifacts) {
        boolean creationVerified = jobId != null && !jobId.isBlank();
        boolean draftStatus = false;
        boolean failureOccurred = true;
        String creationStatus = resolveCreationStatus(creationVerified);
        String submissionStatus = resolveSubmissionStatus(false, failureOccurred, submissionAttempted, draftStatus);
        String reportStatus = resolveReportStatus(false, failureOccurred, creationVerified, submissionAttempted);
        String validationSummary = summarizeValidationIssues(validationIssues, 5);
        String resolvedErrorMessage = firstNonBlank(
                errorMessage,
                !validationIssues.isEmpty() ? "Validation Error - " + validationSummary : null);
        String summary = buildSummary(
                definition.moduleLabel(),
                request.selectedJson(),
                jobId,
                tabNumber,
                userIndex,
                messageReference,
                creationVerified ? "DRF" : "FAILED",
                creationStatus,
                submissionStatus,
                null,
                null,
                resolvedErrorMessage);
        return new WorkflowResult(
                false,
                jobId,
                creationVerified ? "DRF" : "FAILED",
                messageReference,
                null,
                null,
                null,
                null,
                null,
                definition.moduleLabel(),
                request.selectedJson(),
                null,
                resolvedErrorMessage,
                summary,
                creationVerified,
                submissionAttempted,
                jsonRecordNumber,
                tabNumber,
                filledFieldCount,
                missingFieldCount,
                filledFieldCount > 0,
                null,
                draftStatus,
                failureOccurred,
                creationStatus,
                submissionStatus,
                reportStatus,
                validationSummary,
                failureDetails == null ? List.of() : List.copyOf(failureDetails),
                evidenceArtifacts != null && evidenceArtifacts.screenshotEntry() != null ? evidenceArtifacts.screenshotEntry().imageUrl() : null,
                evidenceArtifacts != null ? evidenceArtifacts.diagnosticsArtifactUrl() : null);
    }

    private WorkflowResult finalizeSubmittedDeclaration(
            Page page,
            RunRequest request,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            JsonNode payload,
            String initialJobId,
            String messageReference,
            DeclarationsPage.DeclarationListEntry submittedEntry,
            String diagnostics,
            List<ValidationIssue> validationIssues,
            EvidenceArtifacts evidenceArtifacts,
            int jsonRecordNumber,
            int userIndex,
            int tabNumber,
            int filledFieldCount,
            int missingFieldCount) {
        DeclarationsPage.DeclarationListEntry trackedEntry = submittedEntry;
        try {
            openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
            trackedEntry = refreshTrackedDeclarationEntry(declarationsPage, trackedEntry, messageReference);
            if (!hasTerminalJobStatus(declarationsPage, trackedEntry)) {
                trackedEntry = declarationsPage.waitForDeclarationCompletion(
                        firstNonBlank(
                                trackedEntry != null ? trackedEntry.declarationNumber() : null,
                                messageReference),
                        firstNonBlank(trackedEntry != null ? trackedEntry.jobId() : null, initialJobId),
                        Long.getLong("tradenix.job.completion.timeout.ms", 600000L));
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
                responseDetails != null ? responseDetails.status() : null,
                trackedEntry != null ? trackedEntry.jobStatus() : null,
                inferStatusFromDiagnostics(diagnostics, responseDetails));
        String jobId = firstNonBlank(trackedEntry != null ? trackedEntry.jobId() : null, initialJobId);
        String resolvedMessageReference = firstNonBlank(
                trackedEntry != null ? trackedEntry.declarationNumber() : null,
                messageReference,
                payload.path("header").path("messageReference").asText(null));
        String createdBy = trackedEntry != null ? trackedEntry.jobCreatedBy() : null;
        String permitNumber = firstNonBlank(
                trackedEntry != null ? trackedEntry.permitNumber() : null,
                responseDetails != null ? responseDetails.permitNumber() : null);
        String urn = responseDetails != null ? responseDetails.urn() : null;
        String dateCreated = responseDetails != null ? responseDetails.dateCreated() : null;
        String submissionDate = responseDetails != null ? responseDetails.submissionDate() : null;
        String responseMessage = firstNonBlank(
                responseDetails != null ? responseDetails.responseMessage() : null,
                responseDetails != null ? responseDetails.detailText() : null,
                responseDetails != null ? responseDetails.bannerText() : null,
                extractResponseMessage(diagnostics));
        String errorMessage = firstNonBlank(
                responseDetails != null ? responseDetails.errorMessage() : null,
                extractErrorMessage(diagnostics),
                !validationIssues.isEmpty() ? "Validation Error - " + summarizeValidationIssues(validationIssues, 5) : null);
        boolean creationVerified = jobId != null && !jobId.isBlank();
        boolean submissionVerified = creationVerified
                && isSuccessfulJobStatus(jobStatus)
                && firstNonBlank(errorMessage) == null
                && validationIssues.isEmpty();
        boolean draftStatus = isDraftJobStatus(jobStatus);
        boolean failureOccurred = hasActualFailure(jobStatus, errorMessage, creationVerified, submissionVerified, draftStatus, validationIssues);
        List<FailureDetail> failureDetails = failureOccurred
                ? buildFailureDetails(validationIssues, "Submission Verification", Instant.now().toString(), errorMessage)
                : List.of();
        String creationStatus = resolveCreationStatus(creationVerified);
        String submissionStatus = resolveSubmissionStatus(submissionVerified, failureOccurred, true, draftStatus);
        String reportStatus = resolveReportStatus(submissionVerified, failureOccurred, creationVerified, true);
        String validationSummary = summarizeValidationIssues(validationIssues, 5);
        String summary = buildSummary(
                definition.moduleLabel(),
                request.selectedJson(),
                jobId,
                tabNumber,
                userIndex,
                resolvedMessageReference,
                jobStatus,
                creationStatus,
                submissionStatus,
                permitNumber,
                responseMessage,
                errorMessage);

        if (!failureOccurred && !draftStatus) {
            prepareEvidenceView(page, loginPage, declarationsPage, definition, request, resolvedMessageReference, jobId);
        }

        return new WorkflowResult(
                submissionVerified,
                jobId,
                jobStatus,
                resolvedMessageReference,
                permitNumber,
                urn,
                createdBy,
                dateCreated,
                submissionDate,
                definition.moduleLabel(),
                request.selectedJson(),
                responseMessage,
                errorMessage,
                summary,
                creationVerified,
                true,
                jsonRecordNumber,
                tabNumber,
                filledFieldCount,
                missingFieldCount,
                filledFieldCount > 0,
                null,
                draftStatus,
                failureOccurred,
                creationStatus,
                submissionStatus,
                reportStatus,
                validationSummary,
                failureDetails,
                evidenceArtifacts != null && evidenceArtifacts.screenshotEntry() != null ? evidenceArtifacts.screenshotEntry().imageUrl() : null,
                evidenceArtifacts != null ? evidenceArtifacts.diagnosticsArtifactUrl() : null);
    }

    private void openWorkerPage(LoginPage loginPage, RunRequest request, int userIndex) {
        appendUserLog(userIndex, "Opening Authenticated Worker Tab");
        String workerLandingUrl = resolveWorkerLandingUrl(request.url());
        loginPage.open(workerLandingUrl);
        if (loginPage.isAuthenticated() || loginPage.waitForAuthenticatedState(10000)) {
            return;
        }

        if (loginPage.waitForLoginFormVisible(3000)) {
            appendUserLog(userIndex, "User session not authenticated; login required");
            performLogin(loginPage, request, userIndex);
            return;
        }

        loginPage.open(request.url());
        if (loginPage.isAuthenticated() || loginPage.waitForAuthenticatedState(10000)) {
            return;
        }

        if (loginPage.waitForLoginFormVisible(3000)) {
            appendUserLog(userIndex, "User session requires re-authentication");
            performLogin(loginPage, request, userIndex);
            return;
        }

        if (loginPage.isAuthenticated()) {
            return;
        }

        appendUserLog(userIndex, "Unable to verify authenticated state from user session; retrying login");
        performLogin(loginPage, request, userIndex);
    }

    private String resolveWorkerLandingUrl(String configuredUrl) {
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return configuredUrl;
        }
        try {
            java.net.URI uri = java.net.URI.create(configuredUrl);
            int port = uri.getPort();
            String origin = uri.getScheme() + "://" + uri.getHost() + (port > -1 ? ":" + port : "");
            return origin + "/dashboard";
        } catch (Exception ignored) {
            return configuredUrl;
        }
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
                    if (!loginPage.isAuthenticated()) {
                        if (!loginPage.isLoginFormVisible()) {
                            loginPage.open(request.url());
                        }
                        if (!loginPage.isAuthenticated()) {
                            if (!loginPage.isLoginFormVisible()) {
                                loginPage.waitForLoginFormVisible(5000);
                            }
                            if (!loginPage.isLoginFormVisible()) {
                                loginPage.navigate(request.url());
                            }
                        }
                    }
                    if (!loginPage.isAuthenticated()) {
                        if (!loginPage.isLoginFormVisible()) {
                            loginPage.navigate(request.url());
                        }
                        loginPage.loginAsUser(
                                request.username(),
                                request.password(),
                                firstNonBlank(request.forwarder(), preferredForwarder()),
                                firstNonBlank(request.department(), preferredDepartment()));
                        if (!loginPage.waitForAuthenticatedState(30000)) {
                            throw new IllegalStateException("Login completed but authenticated dashboard was not detected.");
                        }
                    }
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
            String trackedJobId = declarationListEntry != null ? declarationListEntry.jobId() : null;
            if (trackedJobId == null) {
                return null;
            }
            try {
                openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
                declarationsPage.openDeclarationViewByJobId(trackedJobId);
                return declarationsPage.readCurrentResponseDetails();
            } catch (Exception ignored) {
                return null;
            }
        }

        try {
            openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
            return declarationsPage.readDeclarationResponseDetails(trackedMessageReference);
        } catch (Exception ignored) {
            try {
                String trackedJobId = declarationListEntry != null ? declarationListEntry.jobId() : null;
                if (trackedJobId == null) {
                    return null;
                }
                openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
                declarationsPage.openDeclarationViewByJobId(trackedJobId);
                return declarationsPage.readCurrentResponseDetails();
            } catch (Exception ignoredAgain) {
                return null;
            }
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
            responseDetails = readTerminalResponseDetails(
                    page,
                    loginPage,
                    declarationsPage,
                    definition,
                    request,
                    currentEntry,
                    fallbackMessageReference);
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
                        fallbackMessageReference),
                currentEntry != null ? currentEntry.jobId() : null);
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
            String messageReference,
            String jobId) {
        try {
            DeclarationsPage.DeclarationListEntry byMessageReference = declarationsPage.readDeclarationListEntry(messageReference);
            if (hasTrackingDetails(byMessageReference)) {
                return byMessageReference;
            }
        } catch (Exception ignored) {
        }
        try {
            if (jobId != null && !jobId.isBlank()) {
                return declarationsPage.readDeclarationListEntryByJobId(jobId);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private IptDeclarationPage createDeclarationPage(
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            Page page) {
        return switch (definition.workflowKind()) {
            case OUT -> new OutDeclarationPage(page);
            case COO -> new CooDeclarationPage(page);
            case INP -> new InpDeclarationPage(page);
            case TNP -> new TnpDeclarationPage(page);
            case IPT -> new IptDeclarationPage(page);
        };
    }

    private void configureDeclarationFieldLogging(
            IptDeclarationPage declarationPage,
            ExecutionIdentity identity,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            Page page) {
        if (declarationPage == null || identity == null) {
            return;
        }
        declarationPage.configureExecutionLogging(
                formatExecutionIdentity(identity, definition, null),
                entry -> recordFieldMappingLog(identity, definition, page, entry));
    }

    private void recordFieldMappingLog(
            ExecutionIdentity identity,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            Page page,
            String entry) {
        String currentUrl = safePageUrl(page);
        updateCurrentFieldState(identity, definition, entry);
        appendLog(formatExecutionIdentity(identity, definition, currentUrl) + " | " + diagnosticMessage(entry));
    }

    private synchronized void updateCurrentFieldState(
            ExecutionIdentity identity,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            String entry) {
        currentUser = identity.userId();
        currentTab = identity.tabId();
        currentDeclaration = firstNonBlank(
                extractLogAttribute(entry, "section='"),
                definition != null ? definition.moduleLabel() : null,
                currentDeclaration);
        currentField = firstNonBlank(
                extractLogAttribute(entry, "label='"),
                extractLogAttribute(entry, "Field="),
                extractLogAttribute(entry, "field='"),
                truncate(entry, 160));
        validationStatus = entry != null && entry.startsWith("WARN:") ? "WARN" : "RUNNING";
    }

    private String formatExecutionIdentity(
            ExecutionIdentity identity,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            String currentUrl) {
        return String.join(" | ",
                "Thread=" + firstNonBlank(identity.threadName(), "unknown"),
                "User=" + formatTwoDigit(identity.userId()),
                "Browser=" + formatTwoDigit(identity.browserId()),
                "Context=" + formatTwoDigit(identity.contextId()),
                "Tab=" + formatTwoDigit(identity.tabId()),
                "Declaration=" + firstNonBlank(definition != null ? definition.moduleLabel() : null, "N/A"),
                "URL=" + firstNonBlank(currentUrl, "N/A"));
    }

    private String diagnosticMessage(String entry) {
        if (entry == null || entry.isBlank()) {
            return "";
        }
        int infoIndex = entry.indexOf(" | INFO: ");
        if (infoIndex >= 0) {
            return entry.substring(infoIndex + 3);
        }
        int warnIndex = entry.indexOf(" | WARN: ");
        if (warnIndex >= 0) {
            return entry.substring(warnIndex + 3);
        }
        return entry;
    }

    private String safePageUrl(Page page) {
        try {
            return page != null ? page.url() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractLogAttribute(String value, String marker) {
        if (value == null || marker == null || marker.isBlank()) {
            return null;
        }
        int start = value.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        if (marker.endsWith("'")) {
            int end = value.indexOf("'", start);
            return end > start ? value.substring(start, end).trim() : null;
        }
        int pipe = value.indexOf("|", start);
        int comma = value.indexOf(",", start);
        int end = value.length();
        if (pipe > start) {
            end = Math.min(end, pipe);
        }
        if (comma > start) {
            end = Math.min(end, comma);
        }
        String extracted = value.substring(start, end).trim();
        return extracted.isBlank() ? null : extracted;
    }

    private String formatTwoDigit(int value) {
        return String.format("%02d", Math.max(value, 0));
    }

    private JobWorkItem prepareJobWorkItem(int userIndex, int userTabNumber, List<JsonNode> preparedPayloads) {
        return prepareJobWorkItem(userIndex, userTabNumber, preparedPayloads, currentRequest);
    }

    private JobWorkItem prepareJobWorkItem(
            int userIndex,
            int userTabNumber,
            List<JsonNode> preparedPayloads,
            RunRequest request) {
        if (preparedPayloads == null || preparedPayloads.isEmpty()) {
            throw new IllegalStateException("No declaration payloads are loaded for the current run.");
        }
        int executionOrder = ((Math.max(1, userIndex) - 1) * Math.max(1, request != null ? request.tabsPerUser() : 1))
                + Math.max(1, userTabNumber);
        int index = Math.floorMod(executionOrder - 1, preparedPayloads.size());
        JsonNode template = preparedPayloads.get(index);
        JsonNode isolatedPayload = template.deepCopy();
        if (isolatedPayload instanceof ObjectNode objectNode) {
            isolatedPayload = personalizePayloadForUser(objectNode, userIndex, userTabNumber, executionOrder);
        }
        return new JobWorkItem(executionOrder, userIndex, userTabNumber, index + 1, isolatedPayload);
    }

    private JsonNode personalizePayloadForUser(
            ObjectNode payload,
            int userIndex,
            int userTabNumber,
            int executionOrder) {
        ObjectNode declarationPayload = DeclarationPayloads.unwrapObject(payload);
        String messageReference = generateMessageReference(executionOrder);
        ObjectNode header = objectNode(declarationPayload, "header");
        header.put("messageReference", messageReference);

        ObjectNode uniqueReferenceNumber = objectNode(header, "uniqueReferenceNumber");
        uniqueReferenceNumber.put("date", Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        uniqueReferenceNumber.put("sequenceNumeric", String.format("%06d", Math.floorMod(executionOrder - 1, 999999) + 1));

        Map<String, String> personalizedInvoiceNumbers = new HashMap<>();
        JsonNode invoicesNode = declarationPayload.path("invoice");
        if (invoicesNode instanceof ArrayNode invoices) {
            for (int index = 0; index < invoices.size(); index++) {
                JsonNode invoiceNode = invoices.get(index);
                if (invoiceNode instanceof ObjectNode invoice) {
                    String originalInvoiceNumber = blankToNull(text(invoice, "invoiceNumber"));
                    String personalizedInvoiceNumber = appendExecutionSuffix(
                            firstNonBlank(originalInvoiceNumber, "INV"),
                            userIndex,
                            userTabNumber,
                            index + 1,
                            20);
                    invoice.put("invoiceNumber", personalizedInvoiceNumber);
                    if (originalInvoiceNumber != null) {
                        personalizedInvoiceNumbers.put(normalizeReferenceKey(originalInvoiceNumber), personalizedInvoiceNumber);
                    }
                    personalizedInvoiceNumbers.put(normalizeReferenceKey(personalizedInvoiceNumber), personalizedInvoiceNumber);
                }
            }
        }

        JsonNode supportingDocumentsNode = declarationPayload.path("supportingDocumentReference");
        if (supportingDocumentsNode instanceof ArrayNode supportingDocuments) {
            for (int index = 0; index < supportingDocuments.size(); index++) {
                JsonNode documentNode = supportingDocuments.get(index);
                if (documentNode instanceof ObjectNode document) {
                    String documentId = firstNonBlank(text(document, "documentID"), "DOC");
                    document.put("documentID", appendExecutionSuffix(documentId, userIndex, userTabNumber, index + 1, 20));
                }
            }
        }

        JsonNode itemsNode = declarationPayload.path("item");
        if (itemsNode instanceof ArrayNode items) {
            for (int index = 0; index < items.size(); index++) {
                JsonNode itemNode = items.get(index);
                if (!(itemNode instanceof ObjectNode item)) {
                    continue;
                }
                String itemInvoiceNumber = blankToNull(text(item, "itemInvoiceNumber"));
                String personalizedInvoiceNumber = resolvePersonalizedInvoiceNumber(
                        personalizedInvoiceNumbers,
                        itemInvoiceNumber);
                if (personalizedInvoiceNumber != null) {
                    item.put("itemInvoiceNumber", personalizedInvoiceNumber);
                }
                JsonNode shippingMarksInformationNode = item.path("shippingMarksInformation");
                if (shippingMarksInformationNode instanceof ArrayNode shippingMarksInformation) {
                    for (int shippingIndex = 0; shippingIndex < shippingMarksInformation.size(); shippingIndex++) {
                        JsonNode shippingInfoNode = shippingMarksInformation.get(shippingIndex);
                        if (!(shippingInfoNode instanceof ObjectNode shippingInfo)) {
                            continue;
                        }
                        JsonNode shippingMarksNode = shippingInfo.path("shippingMarks");
                        if (shippingMarksNode instanceof ArrayNode shippingMarks && !shippingMarks.isEmpty()) {
                            String firstShippingMark = blankToNull(shippingMarks.get(0).asText(null));
                            if (firstShippingMark != null) {
                                shippingMarks.set(0, shippingMarks.textNode(
                                        appendExecutionSuffix(firstShippingMark, userIndex, userTabNumber, index + 1, 30)));
                            }
                        }
                    }
                }
            }
        }

        return payload;
    }

    private String resolvePersonalizedInvoiceNumber(Map<String, String> personalizedInvoiceNumbers, String itemInvoiceNumber) {
        if (personalizedInvoiceNumbers.isEmpty()) {
            return null;
        }
        if (itemInvoiceNumber != null) {
            String mappedInvoiceNumber = personalizedInvoiceNumbers.get(normalizeReferenceKey(itemInvoiceNumber));
            if (mappedInvoiceNumber != null) {
                return mappedInvoiceNumber;
            }
        }
        if (personalizedInvoiceNumbers.size() == 1) {
            return personalizedInvoiceNumbers.values().iterator().next();
        }
        return null;
    }

    private String normalizeReferenceKey(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toUpperCase(Locale.ROOT);
    }

    private String generateMessageReference(int userIndex) {
        String datePart = Instant.now().atZone(ZoneId.systemDefault()).format(MESSAGE_REFERENCE_DATE);
        int runSeed = Math.floorMod(runId != null ? runId.hashCode() : (int) System.currentTimeMillis(), 1_000_000);
        int perRunSequence = Math.floorMod(runSeed + Math.max(0, userIndex - 1), 1_000_000);
        return "TDX" + datePart + String.format("%06d", perRunSequence);
    }

    private String appendUserSuffix(String value, int userIndex, int itemIndex, int maxLength) {
        String base = value == null ? "" : value.replaceAll("\\s+", "");
        String suffix = "U" + userIndex + "I" + itemIndex;
        if (base.isEmpty()) {
            base = "REF";
        }
        if (maxLength <= 0) {
            return suffix;
        }
        if (suffix.length() >= maxLength) {
            return suffix.substring(Math.max(0, suffix.length() - maxLength));
        }
        int allowedBaseLength = Math.max(0, maxLength - suffix.length());
        String trimmedBase = base.length() > allowedBaseLength
                ? base.substring(0, allowedBaseLength)
                : base;
        return trimmedBase + suffix;
    }

    private String appendExecutionSuffix(String value, int userIndex, int userTabNumber, int itemIndex, int maxLength) {
        String base = value == null ? "" : value.replaceAll("\\s+", "");
        String suffix = "U" + userIndex + "T" + userTabNumber + "I" + itemIndex;
        if (base.isEmpty()) {
            base = "REF";
        }
        if (maxLength <= 0) {
            return suffix;
        }
        if (suffix.length() >= maxLength) {
            return suffix.substring(Math.max(0, suffix.length() - maxLength));
        }
        int allowedBaseLength = Math.max(0, maxLength - suffix.length());
        String trimmedBase = base.length() > allowedBaseLength
                ? base.substring(0, allowedBaseLength)
                : base;
        return trimmedBase + suffix;
    }

    private String waitForCurrentMessageReference(IptDeclarationPage declarationPage, JsonNode payload) {
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() <= deadline) {
            String currentMessageReference = declarationPage.readCurrentMessageReference();
            if (currentMessageReference != null && !currentMessageReference.isBlank()) {
                return currentMessageReference;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return payload.path("header").path("messageReference").asText(null);
    }

    private JsonNode preparePayloadForWorkflow(
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            return payload;
        }
        ObjectNode copy = payload.deepCopy();
        applyDefaultHeaderValues(definition, copy);
        if (definition.workflowKind() == LoadTestingDeclarationCatalog.WorkflowKind.COO) {
            normalizeCooPayload(copy);
        }
        return copy;
    }

    private void applyDefaultHeaderValues(
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            ObjectNode payload) {
        if (definition.workflowKind() != LoadTestingDeclarationCatalog.WorkflowKind.IPT
                && definition.workflowKind() != LoadTestingDeclarationCatalog.WorkflowKind.INP
                && definition.workflowKind() != LoadTestingDeclarationCatalog.WorkflowKind.TNP
                && definition.workflowKind() != LoadTestingDeclarationCatalog.WorkflowKind.OUT) {
            return;
        }
        if (DEFAULT_BG_INDICATOR == null || DEFAULT_BG_INDICATOR.isBlank()) {
            return;
        }
        ObjectNode header = objectNode(DeclarationPayloads.unwrapObject(payload), "header");
        if (isBlank(text(header, "bankerGuaranteeCode"))) {
            header.put("bankerGuaranteeCode", DEFAULT_BG_INDICATOR.trim());
        }
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
        JsonNode declarationPayload = DeclarationPayloads.unwrap(payload);
        List<String> issues = new ArrayList<>();
        requireText(declarationPayload, issues, "header.messageReference");
        requireAnyText(declarationPayload, issues, "header.applicationType", "header.declarationType", "header.commonAccessReference", "type");

        if (definition.workflowKind() == LoadTestingDeclarationCatalog.WorkflowKind.COO) {
            requireAnyText(declarationPayload, issues, "party.exporterParty.partyDetail.partyIdentification.id", "party.exporterParty.partyIdentification.id");
            requireAnyText(declarationPayload, issues, "party.exporterParty.partyDetail.partyName.name", "party.exporterParty.partyName.name");
            requireText(declarationPayload, issues, "transport.outwardTransport.transportMeans.transportMode.conveyanceReferenceNumber");
            requireText(declarationPayload, issues, "transport.outwardTransport.departureDate");
            requireText(declarationPayload, issues, "transport.outwardTransport.dischargePort");
            requireText(declarationPayload, issues, "transport.outwardTransport.finalDestinationCountry");
            requireText(declarationPayload, issues, "item[0].itemHarmonizedSystemCode");
            requireText(declarationPayload, issues, "item[0].goodsDescription");
            requireText(declarationPayload, issues, "item[0].originCountry");
            requireText(declarationPayload, issues, "item[0].harmonizedSystemQuantity.value");
            requireText(declarationPayload, issues, "item[0].harmonizedSystemQuantity.unitCode");
            requireText(declarationPayload, issues, "item[0].itemCertificate.itemCertificateQuantity.value");
            requireText(declarationPayload, issues, "item[0].itemCertificate.itemCertificateQuantity.unitCode");
            requireText(declarationPayload, issues, "item[0].itemCertificate.itemValue");
            requireText(declarationPayload, issues, "item[0].itemCertificate.itemInvoiceNumber");
            requireText(declarationPayload, issues, "item[0].itemCertificate.itemInvoiceDate");
            requireText(declarationPayload, issues, "item[0].itemCertificate.originCriterion[0]");
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
                || upper.equals("REG")
                || upper.equals("SUBMITTED")
                || upper.equals("REGISTERED")
                || upper.equals("PERMIT ISSUED")
                || upper.equals("PERMIT_ISSUED");
    }

    private boolean isDraftJobStatus(String jobStatus) {
        String normalized = firstNonBlank(jobStatus);
        if (normalized == null) {
            return false;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return upper.equals("DRF") || upper.equals("DRAFT");
    }

    private boolean isFailedJobStatus(String jobStatus) {
        String normalized = firstNonBlank(jobStatus);
        if (normalized == null) {
            return false;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return upper.equals("FLD")
                || upper.equals("FAILED")
                || upper.equals("FAILURE")
                || upper.equals("REJ")
                || upper.equals("REJECTED");
    }

    private boolean hasActualFailure(
            String jobStatus,
            String errorMessage,
            boolean creationVerified,
            boolean submissionVerified,
            boolean draftStatus,
            List<ValidationIssue> validationIssues) {
        if (submissionVerified) {
            return false;
        }
        if (validationIssues != null && !validationIssues.isEmpty()) {
            return true;
        }
        if (firstNonBlank(errorMessage) != null) {
            return true;
        }
        if (isFailedJobStatus(jobStatus) || draftStatus) {
            return true;
        }
        return !creationVerified;
    }

    private String resolveCreationStatus(boolean creationVerified) {
        return creationVerified ? "CREATED" : "FAILED";
    }

    private String resolveSubmissionStatus(
            boolean submissionVerified,
            boolean failureOccurred,
            boolean submissionAttempted,
            boolean draftStatus) {
        if (submissionVerified) {
            return "SUBMITTED";
        }
        if (failureOccurred) {
            return "FAILED";
        }
        if (submissionAttempted) {
            return "PENDING";
        }
        if (draftStatus) {
            return "DRAFT";
        }
        return "PENDING";
    }

    private String resolveReportStatus(
            boolean submissionVerified,
            boolean failureOccurred,
            boolean creationVerified,
            boolean submissionAttempted) {
        if (submissionVerified) {
            return "SUCCESS";
        }
        if (failureOccurred) {
            return "FAILED";
        }
        if (submissionAttempted) {
            return "PENDING";
        }
        return "PENDING";
    }

    private String extractResponseMessage(String diagnostics) {
        return extractDiagnosticsField(diagnostics, "responseMessage");
    }

    private String extractErrorMessage(String diagnostics) {
        return extractDiagnosticsField(diagnostics, "errorMessage");
    }

    private List<ValidationIssue> extractValidationIssues(String diagnostics) {
        if (diagnostics == null || diagnostics.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(diagnostics);
            JsonNode invalidElements = root.path("invalidElements");
            if (!invalidElements.isArray() || invalidElements.isEmpty()) {
                return List.of();
            }

            List<ValidationIssue> issues = new ArrayList<>();
            for (JsonNode invalidElementNode : invalidElements) {
                if (invalidElementNode == null || invalidElementNode.isNull() || invalidElementNode.isMissingNode()) {
                    continue;
                }

                JsonNode element = invalidElementNode;
                if (invalidElementNode.isTextual()) {
                    String rawElement = invalidElementNode.asText(null);
                    if (rawElement == null || rawElement.isBlank()) {
                        continue;
                    }
                    try {
                        element = OBJECT_MAPPER.readTree(rawElement);
                    } catch (Exception ignored) {
                        element = OBJECT_MAPPER.createObjectNode().put("text", rawElement);
                    }
                }

                ValidationIssue issue = new ValidationIssue(
                        normalizeFieldLabel(firstNonBlank(blankToNull(element.path("section").asText(null)))),
                        normalizeFieldLabel(firstNonBlank(
                                blankToNull(element.path("label").asText(null)),
                                blankToNull(element.path("placeholder").asText(null)),
                                blankToNull(element.path("formControlName").asText(null)),
                                blankToNull(element.path("name").asText(null)),
                                blankToNull(element.path("id").asText(null)),
                                blankToNull(element.path("text").asText(null)))),
                        normalizeFieldLabel(blankToNull(element.path("id").asText(null))),
                        normalizeFieldLabel(blankToNull(element.path("formControlName").asText(null))),
                        normalizeFieldLabel(blankToNull(element.path("name").asText(null))),
                        normalizeFieldLabel(firstNonBlank(
                                blankToNull(element.path("currentValue").asText(null)),
                                blankToNull(element.path("text").asText(null)))),
                        normalizeFieldLabel(blankToNull(element.path("validationMessage").asText(null))),
                        normalizeFieldLabel(blankToNull(element.path("placeholder").asText(null))),
                        normalizeFieldLabel(blankToNull(element.path("type").asText(null))),
                        normalizeFieldLabel(blankToNull(element.path("role").asText(null))),
                        normalizeFieldLabel(blankToNull(element.path("text").asText(null))));
                if (!containsEquivalentValidationIssue(issues, issue)) {
                    issues.add(issue);
                }
            }
            return List.copyOf(issues);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean containsEquivalentValidationIssue(List<ValidationIssue> existing, ValidationIssue candidate) {
        String candidateKey = normalizeValidationIssueKey(candidate);
        for (ValidationIssue issue : existing) {
            if (normalizeValidationIssueKey(issue).equals(candidateKey)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeValidationIssueKey(ValidationIssue issue) {
        return String.join("|",
                firstNonBlank(issue.section(), ""),
                firstNonBlank(issue.label(), ""),
                firstNonBlank(issue.domId(), ""),
                firstNonBlank(issue.formControlName(), ""),
                firstNonBlank(issue.name(), ""));
    }

    private String validationIssueLabel(ValidationIssue issue) {
        return firstNonBlank(
                issue.label(),
                issue.formControlName(),
                issue.name(),
                issue.domId(),
                issue.rawText(),
                "Unknown Field");
    }

    private String formatValidationIssue(ValidationIssue issue) {
        return String.join(" | ",
                "Section=" + firstNonBlank(issue.section(), "Unknown"),
                "Field=" + firstNonBlank(issue.label(), issue.formControlName(), issue.name(), issue.domId(), "Unknown"),
                "Control Id=" + firstNonBlank(issue.domId(), "N/A"),
                "Form Control=" + firstNonBlank(issue.formControlName(), "N/A"),
                "Name=" + firstNonBlank(issue.name(), "N/A"),
                "Value=" + firstNonBlank(issue.attemptedValue(), "N/A"),
                "Validation=" + firstNonBlank(issue.validationMessage(), issue.rawText(), "N/A"));
    }

    private String summarizeValidationIssues(List<ValidationIssue> issues, int limit) {
        if (issues == null || issues.isEmpty()) {
            return null;
        }
        List<String> fragments = new ArrayList<>();
        int capped = Math.max(1, limit);
        for (int index = 0; index < issues.size() && index < capped; index++) {
            fragments.add(formatValidationIssue(issues.get(index)));
        }
        if (issues.size() > capped) {
            fragments.add("+" + (issues.size() - capped) + " more");
        }
        return String.join(" ; ", fragments);
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

    private FormAuditResult auditJsonAgainstRenderedForm(JsonNode payload, IptDeclarationPage declarationPage) {
        List<JsonValueEntry> entries = flattenJsonValues(payload);
        if (entries.isEmpty()) {
            return FormAuditResult.empty();
        }

        String snapshot = declarationPage.captureRenderedFormAuditSnapshot();
        Set<String> normalizedUiValues = parseAuditSnapshot(snapshot);
        List<FailureDetail> failures = new ArrayList<>();
        String capturedAt = Instant.now().toString();
        for (JsonValueEntry entry : entries) {
            if (isJsonValueRepresentedInUi(entry.value(), normalizedUiValues)) {
                continue;
            }
            failures.add(new FailureDetail(
                    humanizeJsonPath(entry.jsonPath()),
                    entry.jsonPath(),
                    entry.value(),
                    "Rendered UI does not contain the JSON value after form population.",
                    "JSON Audit",
                    capturedAt));
        }

        return new FormAuditResult(
                entries.size() - failures.size(),
                failures.size(),
                List.copyOf(failures),
                snapshot);
    }

    private List<JsonValueEntry> flattenJsonValues(JsonNode node) {
        List<JsonValueEntry> entries = new ArrayList<>();
        collectJsonValues(node, "", entries);
        return List.copyOf(entries);
    }

    private void collectJsonValues(JsonNode node, String path, List<JsonValueEntry> entries) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (shouldSkipJsonAuditPath(path)) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String childPath = path.isBlank() ? field.getKey() : path + "." + field.getKey();
                collectJsonValues(field.getValue(), childPath, entries);
            });
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                collectJsonValues(node.get(index), path + "[" + index + "]", entries);
            }
            return;
        }

        if (node.isBoolean()) {
            return;
        }

        String value = blankToNull(textValue(node));
        if (value == null) {
            return;
        }
        entries.add(new JsonValueEntry(path, value));
    }

    private boolean shouldSkipJsonAuditPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.startsWith("formmetadata.")
                || normalized.contains(".filename")
                || normalized.endsWith(".submit")
                || normalized.endsWith(".headless");
    }

    private Set<String> parseAuditSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(snapshot);
            Set<String> values = new LinkedHashSet<>();
            collectNormalizedAuditValues(root.path("texts"), values);
            collectNormalizedAuditValues(root.path("values"), values);
            return Set.copyOf(values);
        } catch (Exception ignored) {
            return Set.of(normalizeAuditValue(snapshot));
        }
    }

    private void collectNormalizedAuditValues(JsonNode node, Set<String> values) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectNormalizedAuditValues(child, values);
            }
            return;
        }
        String value = blankToNull(node.asText(null));
        if (value == null) {
            return;
        }
        values.add(normalizeAuditValue(value));
    }

    private boolean isJsonValueRepresentedInUi(String value, Set<String> normalizedUiValues) {
        Set<String> candidates = auditCandidates(value);
        if (candidates.isEmpty()) {
            return true;
        }
        for (String candidate : candidates) {
            if (candidate.isBlank()) {
                continue;
            }
            for (String uiValue : normalizedUiValues) {
                if (uiValue.equals(candidate)
                        || uiValue.contains(candidate)
                        || candidate.contains(uiValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> auditCandidates(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return Set.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalizeAuditValue(normalized));

        try {
            candidates.add(normalizeAuditValue(new BigDecimal(normalized).stripTrailingZeros().toPlainString()));
        } catch (NumberFormatException ignored) {
        }

        LocalDate parsedDate = parseAuditDate(normalized);
        if (parsedDate != null) {
            candidates.add(normalizeAuditValue(parsedDate.format(AUDIT_UI_DATE_FORMAT)));
            candidates.add(normalizeAuditValue(parsedDate.format(AUDIT_UI_SLASH_DATE_FORMAT)));
            candidates.add(normalizeAuditValue(parsedDate.toString()));
            candidates.add(normalizeAuditValue(parsedDate.format(DateTimeFormatter.BASIC_ISO_DATE)));
        }

        return Set.copyOf(candidates);
    }

    private LocalDate parseAuditDate(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        String digitsOnly = normalized.replaceAll("\\D", "");
        if (digitsOnly.length() == 8) {
            try {
                if (digitsOnly.matches("(19|20)\\d{6}")) {
                    return LocalDate.parse(digitsOnly, DateTimeFormatter.BASIC_ISO_DATE);
                }
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(digitsOnly, DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(normalized.replace('/', '-').replace('.', '-'), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String normalizeAuditValue(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private String humanizeJsonPath(String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return "Unknown Field";
        }
        String normalized = jsonPath.replaceAll("\\[[0-9]+\\]", "")
                .replace('.', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank() ? jsonPath : normalized;
    }

    private List<FailureDetail> buildFailureDetails(
            List<ValidationIssue> validationIssues,
            String step,
            String timestamp) {
        return buildFailureDetails(validationIssues, step, timestamp, null);
    }

    private List<FailureDetail> buildFailureDetails(
            List<ValidationIssue> validationIssues,
            String step,
            String timestamp,
            String fallbackErrorMessage) {
        List<FailureDetail> failureDetails = new ArrayList<>();
        if (validationIssues != null) {
            for (ValidationIssue validationIssue : validationIssues) {
                failureDetails.add(new FailureDetail(
                        validationIssueLabel(validationIssue),
                        null,
                        validationIssue.attemptedValue(),
                        firstNonBlank(validationIssue.validationMessage(), validationIssue.rawText(), fallbackErrorMessage),
                        step,
                        timestamp));
            }
        }
        if (failureDetails.isEmpty() && fallbackErrorMessage != null) {
            failureDetails.add(new FailureDetail(
                    null,
                    null,
                    null,
                    fallbackErrorMessage,
                    step,
                    timestamp));
        }
        return List.copyOf(failureDetails);
    }

    private String summarizeFailureDetails(List<FailureDetail> failureDetails, int limit) {
        if (failureDetails == null || failureDetails.isEmpty()) {
            return null;
        }
        List<String> fragments = new ArrayList<>();
        int capped = Math.max(1, limit);
        for (int index = 0; index < failureDetails.size() && index < capped; index++) {
            fragments.add(formatFailureDetail(failureDetails.get(index)));
        }
        if (failureDetails.size() > capped) {
            fragments.add("+" + (failureDetails.size() - capped) + " more");
        }
        return String.join(" ; ", fragments);
    }

    private String formatFailureDetail(FailureDetail failureDetail) {
        if (failureDetail == null) {
            return "Unknown failure";
        }
        return String.join(" | ",
                "Field=" + firstNonBlank(failureDetail.fieldName(), "Unknown"),
                "JSON Key=" + firstNonBlank(failureDetail.jsonKey(), "N/A"),
                "JSON Value=" + firstNonBlank(failureDetail.jsonValue(), "N/A"),
                "Reason=" + firstNonBlank(failureDetail.errorMessage(), "N/A"),
                "Step=" + firstNonBlank(failureDetail.failedStep(), "N/A"));
    }

    private void acquirePermit(Semaphore semaphore, String purpose) {
        try {
            semaphore.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + purpose + " capacity.", exception);
        }
    }

    private boolean isStrictJsonAuditEnabled() {
        return Boolean.getBoolean("tradenix.strict.json.audit");
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

    private Long extractDiagnosticsLong(String diagnostics, String... path) {
        if (diagnostics == null || diagnostics.isBlank() || path == null || path.length == 0) {
            return null;
        }
        try {
            JsonNode current = OBJECT_MAPPER.readTree(diagnostics);
            for (String segment : path) {
                if (current == null || current.isMissingNode() || current.isNull()) {
                    return null;
                }
                current = current.path(segment);
            }
            if (current == null || current.isMissingNode() || current.isNull() || !current.canConvertToLong()) {
                return null;
            }
            return current.asLong();
        } catch (Exception ignored) {
            return null;
        }
    }

    private WorkflowResult withDiagnosticsMetrics(WorkflowResult result, String diagnostics) {
        if (result == null) {
            return null;
        }
        Long partyTabDurationMs = extractDiagnosticsLong(diagnostics, "partyPerformance", "partyStartToCompleteMs");
        return new WorkflowResult(
                result.success(),
                result.jobId(),
                result.jobStatus(),
                result.messageReference(),
                result.permitNumber(),
                result.urn(),
                result.createdBy(),
                result.dateCreated(),
                result.submissionDate(),
                result.declarationType(),
                result.selectedJson(),
                result.responseMessage(),
                result.errorMessage(),
                result.summary(),
                result.creationVerified(),
                result.submissionAttempted(),
                result.jsonRecordNumber(),
                result.tabNumber(),
                result.filledFieldCount(),
                result.missingFieldCount(),
                result.populationCompleted() || result.filledFieldCount() > 0,
                partyTabDurationMs != null ? partyTabDurationMs : result.partyTabDurationMs(),
                result.draftStatus(),
                result.failureOccurred(),
                result.creationStatus(),
                result.submissionStatus(),
                result.reportStatus(),
                result.validationSummary(),
                result.failureDetails(),
                result.evidenceScreenshotUrl(),
                result.diagnosticsArtifactUrl());
    }

    private String buildSummary(
            String declarationType,
            String selectedJson,
            String jobId,
            int tabNumber,
            int jobNumber,
            String messageReference,
            String jobStatus,
            String creationStatus,
            String submissionStatus,
            String permitNumber,
            String responseMessage,
            String errorMessage) {
        return String.join(" | ",
                "Type: " + firstNonBlank(declarationType, "N/A"),
                "JSON: " + firstNonBlank(selectedJson, "N/A"),
                "Tab Number: " + (tabNumber > 0 ? tabNumber : 0),
                "Job Number: " + (jobNumber > 0 ? jobNumber : 0),
                "Job ID: " + firstNonBlank(jobId, "N/A"),
                "Message Ref: " + firstNonBlank(messageReference, "N/A"),
                "Status: " + firstNonBlank(jobStatus, "N/A"),
                "Creation Status: " + firstNonBlank(creationStatus, "N/A"),
                "Submission Status: " + firstNonBlank(submissionStatus, "N/A"),
                "Permit: " + firstNonBlank(permitNumber, "N/A"),
                "Response: " + firstNonBlank(responseMessage, "N/A"),
                "Error: " + firstNonBlank(errorMessage, "N/A"));
    }

    private void configurePage(Page page) {
        page.setDefaultTimeout(Long.getLong("playwright.timeout.ms", 30000L));
        page.setDefaultNavigationTimeout(Long.getLong("playwright.navigation.timeout.ms", 60000L));
    }

    private WorkerBrowserSession createWorkerBrowserSession(
            RunRequest request,
            int browserSessionId,
            int workerTabNumber) {
        Playwright workerPlaywright = null;
        Browser workerBrowser = null;
        BrowserContext workerContext = null;
        acquirePermit(browserLaunchSemaphore, "browser launch");
        try {
            workerPlaywright = Playwright.create();
            workerBrowser = launchBrowser(workerPlaywright, request);
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setAcceptDownloads(true)
                    .setIgnoreHTTPSErrors(true);
            workerContext = workerBrowser.newContext(contextOptions);
            Page workerPage = workerContext.newPage();
            return new WorkerBrowserSession(
                    workerPlaywright,
                    workerBrowser,
                    workerContext,
                    workerPage,
                    browserSessionId,
                    browserSessionId,
                    workerTabNumber);
        } catch (Exception exception) {
            closeQuietly(workerContext);
            closeQuietly(workerBrowser);
            closeQuietly(workerPlaywright);
            throw exception;
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

    private void recordSessionBootstrapFailure(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            int userIndex,
            List<JsonNode> preparedPayloads,
            String errorMessage) {
        for (int userTabNumber = 1; userTabNumber <= request.tabsPerUser() && !stopRequested; userTabNumber++) {
            JobWorkItem job = prepareJobWorkItem(userIndex, userTabNumber, preparedPayloads, request);
            long startedAtMillis = System.currentTimeMillis();
            Instant startedAtInstant = Instant.ofEpochMilli(startedAtMillis);
            markUserStarted(userIndex, userTabNumber);
            WorkflowResult result = buildWorkflowFailureResult(
                    request,
                    definition,
                    null,
                    job.payload().path("header").path("messageReference").asText(null),
                    job.recordNumber(),
                    userIndex,
                    userTabNumber,
                    0,
                    0,
                    false,
                    errorMessage,
                    List.of());
            userJobResults.add(buildUserJobResult(
                    job.executionOrder(),
                    userIndex,
                    userTabNumber,
                    userIndex,
                    0,
                    job.payload(),
                    result,
                    null,
                    startedAtInstant,
                    startedAtInstant,
                    0L));
            markUserFinished(userIndex, userTabNumber, result, 0L);
        }
    }

    private void recordRunBootstrapFailure(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            List<JsonNode> preparedPayloads,
            String errorMessage) {
        if (request == null || definition == null || preparedPayloads == null || preparedPayloads.isEmpty()) {
            return;
        }
        if (!userJobResults.isEmpty()) {
            return;
        }
        for (int userIndex = 1; userIndex <= request.totalUsers() && !stopRequested; userIndex++) {
            recordSessionBootstrapFailure(request, definition, userIndex, preparedPayloads, errorMessage);
        }
    }

    private void recordUnprocessedJobs(
            RunRequest request,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            BlockingQueue<QueuedJob> jobQueue,
            List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsers,
            String errorMessage) {
        if (request == null || definition == null || jobQueue == null || jobQueue.isEmpty()) {
            return;
        }
        List<QueuedJob> unprocessedJobs = new ArrayList<>();
        jobQueue.drainTo(unprocessedJobs);
        for (QueuedJob queuedJob : unprocessedJobs) {
            if (queuedJob == null) {
                continue;
            }
            RunRequest jobRequest = request;
            JobWorkItem job = null;
            try {
                jobRequest = requestForJob(request, queuedJob, assignedUsers);
                job = loadUserJobWorkItem(jobRequest, definition, queuedJob);
                appendUserLog(queuedJob.userIndex(), "JSON Loaded Once - " + jobRequest.selectedJson());
            } catch (Throwable exception) {
                recordIsolatedJobFailure(
                        jobRequest,
                        definition,
                        queuedJob,
                        null,
                        0,
                        firstNonBlank(errorMessage, rootMessage(exception)));
                continue;
            }
            recordIsolatedJobFailure(jobRequest, definition, queuedJob, job, 0, errorMessage);
        }
    }

    private synchronized void markUserStarted(int userIndex, int logicalTabNumber) {
        startedUsers++;
        runningUsers++;
        currentUser = userIndex;
        currentTab = logicalTabNumber;
        currentField = "Starting declaration workflow";
        currentDeclaration = currentDefinition != null ? currentDefinition.moduleLabel() : currentDeclaration;
        validationStatus = "RUNNING";
        appendUserLog(userIndex, "Starting Declaration Workflow - Logical Tab " + logicalTabNumber);
    }

    private synchronized void markBrowserSessionOpened(int browserSessionId) {
        activeBrowserSessions++;
        appendLog("Browser session opened - Session " + browserSessionId);
    }

    private synchronized void markBrowserSessionClosed(int browserSessionId) {
        activeBrowserSessions = Math.max(0, activeBrowserSessions - 1);
        appendLog("Browser session closed - Session " + browserSessionId);
    }

    private synchronized void markWorkerContextOpened(int browserContextId) {
        activeBrowserContexts++;
        appendLog("Worker context opened - Context " + browserContextId);
    }

    private synchronized void markWorkerContextClosed(int browserContextId) {
        activeBrowserContexts = Math.max(0, activeBrowserContexts - 1);
        appendLog("Worker context closed - Context " + browserContextId);
    }

    private synchronized void markWorkerTabOpened(int logicalTabNumber) {
        openTabsCount++;
        totalTabsOpened++;
        appendLog("Worker tab opened - Tab " + logicalTabNumber);
    }

    private synchronized void markWorkerTabClosed(int logicalTabNumber) {
        openTabsCount = Math.max(0, openTabsCount - 1);
        appendLog("Worker tab closed - Tab " + logicalTabNumber);
    }

    private synchronized void markUserFinished(
            int userIndex,
            int logicalTabNumber,
            WorkflowResult result,
            long durationMs) {
        runningUsers = Math.max(0, runningUsers - 1);
        completedUsers++;
        executedUsers++;
        totalWorkflowDurationMs += Math.max(durationMs, 0L);
        totalFilledFieldCount += Math.max(result != null ? result.filledFieldCount() : 0, 0);
        totalMissingFieldCount += Math.max(result != null ? result.missingFieldCount() : 0, 0);
        if (result != null && result.populationCompleted()) {
            populatedTabsCount++;
        }

        boolean success = result != null && result.submissionVerified();
        if (success) {
            successCount++;
        } else {
            if (result != null && result.failureOccurred()) {
                failureCount++;
            }
        }
        if (result != null && result.creationVerified()) {
            totalJobsCreated++;
        }
        if (result != null && result.submissionVerified()) {
            totalJobsSubmitted++;
        }
        if (result != null && result.draftStatus()) {
            draftJobCount++;
        }
        if (result != null && !result.submissionVerified() && !result.failureOccurred()) {
            pendingVerificationCount++;
        }

        if (result != null) {
            latestWorkflowResult = result;
            currentUser = userIndex;
            currentTab = logicalTabNumber;
            currentDeclaration = result.declarationType();
            validationStatus = result.submissionVerified() ? "PASS" : result.failureOccurred() ? "FAIL" : "PENDING";
            currentField = result.submissionVerified()
                    ? "Submission verified"
                    : firstNonBlank(result.validationSummary(), result.errorMessage(), "Workflow completed without verified submission");
            message = "Latest result: user " + userIndex + ", tab " + logicalTabNumber + " - " + truncate(result.summary(), 180);
        } else {
            currentUser = userIndex;
            currentTab = logicalTabNumber;
            validationStatus = "FAIL";
            currentField = "Workflow did not return a result";
            message = "Latest result: user " + userIndex + ", tab " + logicalTabNumber + " - workflow did not return a result.";
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

    private List<String> validateCompletedRun(int requestedJobs) {
        List<String> issues = new ArrayList<>();
        if (completedUsers != requestedJobs) {
            issues.add("Processed tab runs mismatch: expected " + requestedJobs + ", completed " + completedUsers);
        }
        if (executedUsers != requestedJobs) {
            issues.add("Executed tab runs mismatch: expected " + requestedJobs + ", executed " + executedUsers);
        }
        if (userJobResults.size() != requestedJobs) {
            issues.add("Report rows mismatch: expected " + requestedJobs + ", actual " + userJobResults.size());
        }
        if ((successCount + failureCount) != requestedJobs) {
            issues.add("Final status count mismatch: success+failure=" + (successCount + failureCount)
                    + ", requested=" + requestedJobs);
        }
        return List.copyOf(issues);
    }

    private ScreenshotEntry resolveCompletionScreenshot(Page page, int userIndex, WorkflowResult result) {
        if (result != null && result.evidenceScreenshotUrl() != null && !result.evidenceScreenshotUrl().isBlank()) {
            return new ScreenshotEntry(
                    userIndex,
                    result.failureOccurred() ? "FAILURE" : "SUCCESS",
                    result.evidenceScreenshotUrl(),
                    truncate(result.summary(), 220),
                    Instant.now().toString());
        }
        return captureScreenshot(page, userIndex, result != null && result.success() ? "success" : "failure", result);
    }

    private ScreenshotEntry captureScreenshot(Page page, int userIndex, String outcome, WorkflowResult result) {
        try {
            renderScreenshotEvidenceBanner(page, userIndex, result);
            String fileName = buildScreenshotFileName(userIndex, outcome, result);
            Path outputPath = runScreenshotsDirectory.resolve(fileName);
            page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(outputPath));
            ScreenshotEntry entry = new ScreenshotEntry(
                    userIndex,
                    outcome.toUpperCase(Locale.ROOT),
                    "/dashboard-screenshots/load-dashboard/" + runId + "/" + fileName,
                    truncate(result != null ? result.summary() : null, 220),
                    Instant.now().toString());
            addScreenshot(entry);
            return entry;
        } catch (Exception exception) {
            appendUserLog(userIndex, "Screenshot Capture Failed - " + rootMessage(exception));
            return null;
        }
    }

    private EvidenceArtifacts captureFailureEvidence(
            Page page,
            int userIndex,
            WorkflowResult result,
            String diagnostics,
            List<ValidationIssue> validationIssues,
            List<FailureDetail> failureDetails) {
        if (page == null || result == null) {
            return null;
        }
        try {
            String diagnosticsArtifactUrl = writeDiagnosticsArtifact(userIndex, result, diagnostics, validationIssues, failureDetails);
            ScreenshotEntry screenshotEntry = captureScreenshot(page, userIndex, "failure-initial", result);
            return new EvidenceArtifacts(screenshotEntry, diagnosticsArtifactUrl);
        } catch (Exception exception) {
            appendUserLog(userIndex, "Failure Evidence Capture Failed - " + rootMessage(exception));
            return null;
        }
    }

    private String writeDiagnosticsArtifact(
            int userIndex,
            WorkflowResult result,
            String diagnostics,
            List<ValidationIssue> validationIssues,
            List<FailureDetail> failureDetails) {
        if (runReportsDirectory == null) {
            return null;
        }
        try {
            Files.createDirectories(runReportsDirectory);
            String fileName = buildDiagnosticsFileName(userIndex, result);
            Path outputPath = runReportsDirectory.resolve(fileName);
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("userIndex", userIndex);
            payloadMap.put("capturedAt", Instant.now().toString());
            payloadMap.put("summary", firstNonBlank(result.summary()));
            payloadMap.put("jobId", firstNonBlank(result.jobId()));
            payloadMap.put("messageReference", firstNonBlank(result.messageReference()));
            payloadMap.put("creationStatus", firstNonBlank(result.creationStatus()));
            payloadMap.put("submissionStatus", firstNonBlank(result.submissionStatus()));
            payloadMap.put("reportStatus", firstNonBlank(result.reportStatus()));
            payloadMap.put("applicationStatus", firstNonBlank(result.jobStatus()));
            payloadMap.put("selectedJson", firstNonBlank(result.selectedJson()));
            payloadMap.put("jsonRecordNumber", result.jsonRecordNumber());
            payloadMap.put("validationSummary", firstNonBlank(result.validationSummary(), summarizeValidationIssues(validationIssues, 10)));
            payloadMap.put("validationIssues", validationIssues == null ? List.of() : validationIssues);
            payloadMap.put("failureDetails", failureDetails == null ? List.of() : failureDetails);
            payloadMap.put("failedStep", failureDetails == null || failureDetails.isEmpty() ? null : failureDetails.get(0).failedStep());
            payloadMap.put("failureFieldName", failureDetails == null || failureDetails.isEmpty() ? null : failureDetails.get(0).fieldName());
            payloadMap.put("failureJsonKey", failureDetails == null || failureDetails.isEmpty() ? null : failureDetails.get(0).jsonKey());
            payloadMap.put("failureJsonValue", failureDetails == null || failureDetails.isEmpty() ? null : failureDetails.get(0).jsonValue());
            payloadMap.put("failureTimestamp", failureDetails == null || failureDetails.isEmpty() ? null : failureDetails.get(0).timestamp());
            payloadMap.put("rawDiagnostics", firstNonBlank(diagnostics, ""));
            String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payloadMap);
            Files.writeString(outputPath, payload, StandardCharsets.UTF_8);
            return "/dashboard-reports/load-dashboard/" + runId + "/" + fileName;
        } catch (Exception exception) {
            appendUserLog(userIndex, "Diagnostics Artifact Failed - " + rootMessage(exception));
            return null;
        }
    }

    private String buildDiagnosticsFileName(int userIndex, WorkflowResult result) {
        String base = firstNonBlank(result.jobId(), result.messageReference(), "user-" + String.format("%03d", userIndex));
        return sanitizeFileComponent(base)
                + "-user-" + formatArtifactUserId(userIndex)
                + "-" + currentArtifactTimestamp()
                + "-diagnostics.json";
    }

    private void renderScreenshotEvidenceBanner(Page page, int userIndex, WorkflowResult result) {
        if (page == null || result == null) {
            return;
        }
        try {
            String reportStatus = firstNonBlank(result.reportStatus(), result.success() ? "SUCCESS" : "FAILED");
            Map<String, Object> details = new HashMap<>();
            details.put("userIndex", userIndex);
            details.put("reportStatus", reportStatus);
            details.put("applicationStatus", firstNonBlank(result.jobStatus()));
            details.put("jobId", firstNonBlank(result.jobId()));
            details.put("messageReference", firstNonBlank(result.messageReference()));
            details.put("permitNumber", firstNonBlank(result.permitNumber()));
            details.put("urn", firstNonBlank(result.urn()));
            details.put("createdBy", firstNonBlank(result.createdBy(), currentRequest != null ? currentRequest.username() : null));
            details.put("dateCreated", firstNonBlank(result.dateCreated()));
            details.put("submissionDate", firstNonBlank(result.submissionDate()));
            details.put("creationStatus", firstNonBlank(result.creationStatus()));
            details.put("submissionStatus", firstNonBlank(result.submissionStatus()));
            page.evaluate("""
                    details => {
                        const existing = document.getElementById('load-dashboard-evidence-banner');
                        if (existing) {
                            existing.remove();
                        }
                        const banner = document.createElement('div');
                        banner.id = 'load-dashboard-evidence-banner';
                        banner.style.position = 'fixed';
                        banner.style.top = '16px';
                        banner.style.right = '16px';
                        banner.style.zIndex = '2147483647';
                        banner.style.maxWidth = '520px';
                        banner.style.padding = '12px 16px';
                        banner.style.background = 'rgba(7, 18, 34, 0.94)';
                        banner.style.color = '#ffffff';
                        banner.style.border = '2px solid #49b6ff';
                        banner.style.borderRadius = '12px';
                        banner.style.boxShadow = '0 12px 32px rgba(0,0,0,0.35)';
                        banner.style.fontFamily = 'Segoe UI, Arial, sans-serif';
                        banner.style.fontSize = '14px';
                        banner.style.lineHeight = '1.45';
                        banner.innerHTML = `
                            <div style="font-weight:700;font-size:16px;margin-bottom:8px">Execution Evidence - User ${details.userIndex}</div>
                            <div>Report Status: ${details.reportStatus || 'N/A'}</div>
                            <div>Application Status: ${details.applicationStatus || 'N/A'}</div>
                            <div>Creation Status: ${details.creationStatus || 'N/A'}</div>
                            <div>Submission Status: ${details.submissionStatus || 'N/A'}</div>
                            <div>Job ID: ${details.jobId || 'N/A'}</div>
                            <div>Message Ref: ${details.messageReference || 'N/A'}</div>
                            <div>Permit No: ${details.permitNumber || 'N/A'}</div>
                            <div>URN: ${details.urn || 'N/A'}</div>
                            <div>Created By: ${details.createdBy || 'N/A'}</div>
                            <div>Date Created: ${details.dateCreated || 'N/A'}</div>
                            <div>Submission Date: ${details.submissionDate || 'N/A'}</div>
                        `;
                        document.body.appendChild(banner);
                    }
                    """, details);
            page.waitForTimeout(300);
        } catch (Exception exception) {
            appendUserLog(userIndex, "Evidence Banner Failed - " + rootMessage(exception));
        }
    }

    private void prepareEvidenceView(
            Page page,
            LoginPage loginPage,
            DeclarationsPage declarationsPage,
            LoadTestingDeclarationCatalog.DeclarationDefinition definition,
            RunRequest request,
            String messageReference,
            String jobId) {
        if ((messageReference == null || messageReference.isBlank()) && (jobId == null || jobId.isBlank())) {
            return;
        }
        try {
            openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
            if (messageReference != null && !messageReference.isBlank()) {
                declarationsPage.openDeclarationView(messageReference);
            } else {
                declarationsPage.openDeclarationViewByJobId(jobId);
            }
            declarationsPage.readCurrentResponseDetails();
        } catch (Exception exception) {
            try {
                if (jobId != null && !jobId.isBlank()) {
                    openDeclarationListWithRelogin(page, loginPage, declarationsPage, definition, request);
                    declarationsPage.openDeclarationViewByJobId(jobId);
                    declarationsPage.readCurrentResponseDetails();
                    return;
                }
            } catch (Exception ignored) {
            }
            appendLog("Evidence view warning: " + rootMessage(exception));
        }
    }

    private String buildScreenshotFileName(int userIndex, String outcome, WorkflowResult result) {
        String base = result != null
                ? firstNonBlank(
                result.jobId() != null && !result.jobId().isBlank() ? "JOB" + result.jobId() : null,
                result.messageReference(),
                "user-" + formatArtifactUserId(userIndex))
                : "user-" + formatArtifactUserId(userIndex);
        String suffix = "success".equalsIgnoreCase(outcome)
                ? ".png"
                : "-" + sanitizeFileComponent(outcome) + ".png";
        return sanitizeFileComponent(base)
                + "-user-" + formatArtifactUserId(userIndex)
                + "-" + currentArtifactTimestamp()
                + suffix;
    }

    private String formatArtifactUserId(int userIndex) {
        return String.format("%03d", Math.max(userIndex, 0));
    }

    private String currentArtifactTimestamp() {
        return Instant.now().atZone(ZoneId.systemDefault()).format(ARTIFACT_TIMESTAMP_FORMAT);
    }

    private String sanitizeFileComponent(String value) {
        String normalized = value == null ? "artifact" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        return normalized.isBlank() ? "artifact" : normalized;
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
        int normalizedTotalUsers = Math.max(1, request.totalUsers());
        int requestedBrowserTabs = request.browserTabs() > 0
                ? request.browserTabs()
                : normalizedTotalUsers;
        int normalizedBrowserTabs = Math.max(1,
                Math.min(requestedBrowserTabs, MAX_CONCURRENT_BROWSER_USERS));
        return new RunRequest(
                normalizedTotalUsers,
                normalizedBrowserTabs,
                1,
                request.url(),
                request.username(),
                request.password(),
                request.declarationType(),
                request.selectedJson(),
                false,
                request.jmeterRampUpSeconds(),
                request.jmeterLoopCount(),
                firstNonBlank(request.testingMode(), "STANDARD_LOAD"),
                request.concurrencyLimit(),
                request.thresholds(),
                request.forwarder(),
                request.department(),
                request.useProvisionedUsers(),
                request.useSequentialUsers(),
                request.sequentialUsernamePrefix(),
                request.sequentialStartSequence());
    }

    private int workerCountFor(RunRequest request) {
        if (request == null) {
            return 1;
        }
        return Math.max(1, Math.min(
                Math.min(request.browserTabs(), MAX_CONCURRENT_BROWSER_USERS),
                Math.max(1, request.totalTabRuns())));
    }

    private List<ProvisionedLoadTestUserPool.LoadTestUser> assignedUsersFor(RunRequest request) {
        if (request.useProvisionedUsers()) {
            return provisionedUserPool.first(request.totalUsers());
        }
        if (!request.useSequentialUsers()) {
            return List.of();
        }
        List<ProvisionedLoadTestUserPool.LoadTestUser> users = new ArrayList<>();
        for (int offset = 0; offset < request.totalUsers(); offset++) {
            int sequence = request.sequentialStartSequence() + offset;
            users.add(new ProvisionedLoadTestUserPool.LoadTestUser(
                    request.sequentialUsernamePrefix().trim() + sequence,
                    request.password(), "", "", request.forwarder(), request.department(), Instant.now()));
        }
        return List.copyOf(users);
    }

    private boolean usesAssignedUsers(RunRequest request) {
        return request.useProvisionedUsers() || request.useSequentialUsers();
    }

    private RunRequest requestForProvisionedUser(
            RunRequest request,
            ProvisionedLoadTestUserPool.LoadTestUser user) {
        return new RunRequest(
                request.totalUsers(), request.browserTabs(), request.tabsPerUser(), request.url(),
                user.username(), user.password(), request.declarationType(), request.selectedJson(), request.headless(),
                request.jmeterRampUpSeconds(), request.jmeterLoopCount(), request.testingMode(), request.concurrencyLimit(),
                request.thresholds(), user.forwarder(), user.department(), request.useProvisionedUsers(),
                request.useSequentialUsers(), request.sequentialUsernamePrefix(), request.sequentialStartSequence());
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
        if (request.tabsPerUser() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Number of tabs per user must be greater than zero.");
        }
        if (request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required.");
        }
        if (!usesAssignedUsers(request) && (request.username() == null || request.username().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required.");
        }
        if (!usesAssignedUsers(request) && (request.password() == null || request.password().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
        }
        if (request.useProvisionedUsers() && provisionedUserPool.size() < request.totalUsers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The provisioned-user pool contains " + provisionedUserPool.size()
                            + " user(s), but this run requires " + request.totalUsers() + ".");
        }
        if (request.useProvisionedUsers() && request.useSequentialUsers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose either the provisioned-user pool or sequential user logins, not both.");
        }
        if (request.useSequentialUsers()) {
            if (request.sequentialUsernamePrefix() == null || request.sequentialUsernamePrefix().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sequential username prefix is required.");
            }
            if (request.sequentialStartSequence() < 1 || request.sequentialStartSequence() > Integer.MAX_VALUE - request.totalUsers()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sequential start sequence is outside the valid range.");
            }
            if (request.password() == null || request.password().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shared password is required for sequential users.");
            }
            if (request.forwarder() == null || request.forwarder().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forwarder is required for sequential users.");
            }
            if (request.department() == null || request.department().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is required for sequential users.");
            }
        }
        if (request.declarationType() == null || request.declarationType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Declaration type is required.");
        }
        if (request.selectedJson() == null || request.selectedJson().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a declaration JSON file.");
        }
        if (request.testingMode() == null || request.testingMode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Testing mode is required.");
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

    private void closeQuietly(Page page) {
        try {
            if (page != null) {
                page.close();
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

    private void closeQuietly(WorkerBrowserSession session) {
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

    private double percentileExecutionTime(int percentile) {
        List<Long> durations = userJobResults.stream()
                .map(UserJobResult::executionTimeMs)
                .filter(duration -> duration != null && duration >= 0L)
                .toList();
        return calculatePercentile(durations, percentile);
    }

    private double averagePartyTabTime() {
        List<Long> durations = partyTabDurations();
        if (durations.isEmpty()) {
            return 0.0d;
        }
        long total = 0L;
        for (Long duration : durations) {
            total += duration;
        }
        return total / (double) durations.size();
    }

    private double percentilePartyTabTime(int percentile) {
        return calculatePercentile(partyTabDurations(), percentile);
    }

    private List<Long> partyTabDurations() {
        return userJobResults.stream()
                .map(UserJobResult::partyTabDurationMs)
                .filter(duration -> duration != null && duration >= 0L)
                .toList();
    }

    private double calculatePercentile(List<Long> values, int percentile) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil((Math.max(1, percentile) / 100.0d) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private UserJobResult buildUserJobResult(
            int executionOrder,
            int userIndex,
            Integer logicalTabNumber,
            Integer browserSessionId,
            Integer browserContextId,
            JsonNode payload,
            WorkflowResult result,
            ScreenshotEntry screenshotEntry,
            Instant startedAt,
            Instant finishedAt,
            long executionTimeMs) {
        String applicationStatus = result != null ? firstNonBlank(result.jobStatus()) : null;
        String reportStatus = result != null ? firstNonBlank(result.reportStatus()) : null;
        boolean successful = result != null && result.submissionVerified();
        FailureDetail primaryFailureDetail = result != null && !result.failureDetails().isEmpty()
                ? result.failureDetails().get(0)
                : null;
        String failureReason = successful
                ? null
                : buildFailureReason(result, applicationStatus);
        return new UserJobResult(
                executionOrder,
                userIndex,
                logicalTabNumber != null ? logicalTabNumber : 0,
                browserSessionId != null ? browserSessionId : 0,
                browserContextId != null ? browserContextId : 0,
                executionOrder,
                result != null ? result.jobId() : null,
                result != null ? result.declarationType() : null,
                firstNonBlank(result != null ? result.createdBy() : null, currentRequest != null ? currentRequest.username() : null),
                firstNonBlank(reportStatus, "FAILED"),
                result != null ? result.creationStatus() : "FAILED",
                result != null ? result.submissionStatus() : "FAILED",
                startedAt != null ? startedAt.toString() : null,
                finishedAt != null ? finishedAt.toString() : null,
                Math.max(executionTimeMs, 0L),
                failureReason,
                screenshotEntry != null ? screenshotEntry.imageUrl() : null,
                result != null ? result.messageReference() : null,
                applicationStatus,
                result != null ? result.permitNumber() : null,
                result != null ? result.urn() : null,
                resolveUrnDate(payload, result),
                firstNonBlank(text(payload, "declarantId"), text(payload.path("header"), "declarantId")),
                successful,
                result != null ? result.errorMessage() : null,
                result != null ? result.selectedJson() : null,
                result != null ? result.jsonRecordNumber() : logicalTabNumber != null ? logicalTabNumber : userIndex,
                result != null ? result.filledFieldCount() : 0,
                result != null ? result.missingFieldCount() : 0,
                Math.max((result != null ? result.filledFieldCount() : 0) + (result != null ? result.missingFieldCount() : 0), 0),
                result != null ? result.partyTabDurationMs() : null,
                firstNonBlank(
                        primaryFailureDetail != null ? formatFailureDetail(primaryFailureDetail) : null,
                        result != null ? result.validationSummary() : null),
                primaryFailureDetail != null ? primaryFailureDetail.fieldName() : null,
                primaryFailureDetail != null ? primaryFailureDetail.jsonKey() : null,
                primaryFailureDetail != null ? primaryFailureDetail.jsonValue() : null,
                primaryFailureDetail != null ? primaryFailureDetail.failedStep() : null,
                primaryFailureDetail != null ? primaryFailureDetail.timestamp() : null,
                result != null ? result.diagnosticsArtifactUrl() : null,
                Instant.now().toString());
    }

    private String resolveUrnDate(JsonNode payload, WorkflowResult result) {
        return firstNonBlank(
                result != null ? normalizeDashboardDate(result.submissionDate()) : null,
                result != null ? normalizeDashboardDate(result.dateCreated()) : null,
                normalizeDashboardDate(text(payload.path("header").path("uniqueReferenceNumber"), "date")),
                normalizeDashboardDate(text(payload, "urnDate")),
                normalizeDashboardDate(text(payload.path("header"), "urnDate")));
    }

    private String normalizeDashboardDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.matches("\\d{8}")) {
            return normalized.substring(0, 4) + "-" + normalized.substring(4, 6) + "-" + normalized.substring(6, 8);
        }
        return normalized;
    }

    private String buildFailureReason(WorkflowResult result, String applicationStatus) {
        if (result == null) {
            return "Workflow did not return a result.";
        }
        String detailedReason = firstNonBlank(
                result.failureDetails() != null && !result.failureDetails().isEmpty()
                        ? formatFailureDetail(result.failureDetails().get(0))
                        : null,
                result.errorMessage(),
                result.validationSummary(),
                result.responseMessage(),
                result.summary());
        if (applicationStatus != null) {
            if (detailedReason == null) {
                return "Application Status: " + applicationStatus;
            }
            if (!detailedReason.toUpperCase(Locale.ROOT).contains(applicationStatus.toUpperCase(Locale.ROOT))) {
                return "Application Status: " + applicationStatus + " | " + detailedReason;
            }
        }
        return detailedReason;
    }

    private String rootMessage(Throwable exception) {
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
            int executionOrder,
            int userIndex,
            int tabNumber,
            int browserSessionId,
            int browserContextId,
            int jobNumber,
            String jobId,
            String permitType,
            String createdBy,
            String status,
            String creationStatus,
            String submissionStatus,
            String startTime,
            String endTime,
            long executionTimeMs,
            String failureReason,
            String screenshotUrl,
            String messageReference,
            String applicationStatus,
            String permitNumber,
            String urn,
            String urnDate,
            String declarantId,
            boolean success,
            String errorMessage,
            String selectedJson,
            int jsonRecordNumber,
            int filledFieldCount,
            int missingFieldCount,
            int expectedFieldCount,
            Long partyTabDurationMs,
            String failedFieldDetails,
            String failureFieldName,
            String failureJsonKey,
            String failureJsonValue,
            String failedStep,
            String failureTimestamp,
            String diagnosticsArtifactUrl,
            String capturedAt) {
    }

    public record RunRequest(
            int totalUsers,
            int browserTabs,
            int tabsPerUser,
            String url,
            String username,
            String password,
            String declarationType,
            String selectedJson,
            boolean headless,
            int jmeterRampUpSeconds,
            int jmeterLoopCount,
            String testingMode,
            Integer concurrencyLimit,
            ThresholdConfiguration thresholds,
            String forwarder,
            String department,
            boolean useProvisionedUsers,
            boolean useSequentialUsers,
            String sequentialUsernamePrefix,
            int sequentialStartSequence) {

        /**
         * Compatibility constructor for callers that predate per-user load credentials.
         * The legacy mode keeps using the supplied shared username and password.
         */
        public RunRequest(
                int totalUsers,
                int browserTabs,
                int tabsPerUser,
                String url,
                String username,
                String password,
                String declarationType,
                String selectedJson,
                boolean headless,
                int jmeterRampUpSeconds,
                int jmeterLoopCount,
                String testingMode,
                Integer concurrencyLimit,
                ThresholdConfiguration thresholds) {
            this(
                    totalUsers,
                    browserTabs,
                    tabsPerUser,
                    url,
                    username,
                    password,
                    declarationType,
                    selectedJson,
                    headless,
                    jmeterRampUpSeconds,
                    jmeterLoopCount,
                    testingMode,
                    concurrencyLimit,
                    thresholds,
                    null,
                    null,
                    false,
                    false,
                    null,
                    0);
        }

        int totalTabRuns() {
            return Math.max(0, totalUsers);
        }
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
            String testingMode,
            int requestedBrowserTabs,
            Integer concurrencyLimit,
            int totalUsers,
            int tabsPerUser,
            int totalTabRuns,
            int startedUsers,
            int activeUsers,
            int activeBrowserSessions,
            int activeBrowserContexts,
            int completedUsers,
            int executedUsers,
            int successCount,
            int failureCount,
            int openTabsCount,
            int totalTabsOpened,
            int populatedTabsCount,
            long totalFilledFieldCount,
            long totalMissingFieldCount,
            int totalJobsCreated,
            int totalJobsSubmitted,
            int draftJobCount,
            int pendingVerificationCount,
            double averageResponseTime,
            double p95ResponseTime,
            double p99ResponseTime,
            double averagePartyTabTime,
            double p95PartyTabTime,
            double p99PartyTabTime,
            double throughput,
            double transactionsPerSecond,
            double errorPercentage,
            String latestJobStatus,
            String latestJobId,
            String latestMessageReference,
            String latestPermitNumber,
            String declarationTypeLabel,
            String selectedJson,
            int currentUser,
            int currentTab,
            String currentField,
            String currentDeclaration,
            String validationStatus,
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
            String urn,
            String createdBy,
            String dateCreated,
            String submissionDate,
            String declarationType,
            String selectedJson,
            String responseMessage,
            String errorMessage,
            String summary,
            boolean creationVerified,
            boolean submissionAttempted,
            int jsonRecordNumber,
            int tabNumber,
            int filledFieldCount,
            int missingFieldCount,
            boolean populationCompleted,
            Long partyTabDurationMs,
            boolean draftStatus,
            boolean failureOccurred,
            String creationStatus,
            String submissionStatus,
            String reportStatus,
            String validationSummary,
            List<FailureDetail> failureDetails,
            String evidenceScreenshotUrl,
            String diagnosticsArtifactUrl) {

        boolean submissionVerified() {
            return success;
        }
    }

    private record EvidenceArtifacts(
            ScreenshotEntry screenshotEntry,
            String diagnosticsArtifactUrl) {
    }

    private record ValidationIssue(
            String section,
            String label,
            String domId,
            String formControlName,
            String name,
            String attemptedValue,
            String validationMessage,
            String placeholder,
            String type,
            String role,
            String rawText) {
    }

    private record FailureDetail(
            String fieldName,
            String jsonKey,
            String jsonValue,
            String errorMessage,
            String failedStep,
            String timestamp) {
    }

    private record JsonValueEntry(
            String jsonPath,
            String value) {
    }

    public record ThresholdConfiguration(
            Double maxResponseTimeSeconds,
            Double maxErrorRatePercent,
            Double minimumSuccessRatePercent,
            Double maxPartyTabTimeSeconds) {
    }

    private record FormAuditResult(
            int matchedCount,
            int failureCount,
            List<FailureDetail> failureDetails,
            String snapshot) {

        private static FormAuditResult empty() {
            return new FormAuditResult(0, 0, List.of(), null);
        }

        private boolean hasFailures() {
            return failureCount > 0;
        }
    }

    private record QueuedJob(
            int executionOrder,
            int userIndex,
            int userTabNumber,
            int recordNumber) {
    }

    /**
     * Immutable queue envelope for one user execution. The payload is copied again
     * at this boundary so a caller can never enqueue a JSON tree that another user
     * can observe or mutate.
     */
    private record JobWorkItem(
            int executionOrder,
            int userIndex,
            int userTabNumber,
            int recordNumber,
            JsonNode payload) {
        private JobWorkItem {
            payload = payload == null ? null : payload.deepCopy();
        }
    }

    private record ExecutionIdentity(
            int browserId,
            int contextId,
            int tabId,
            int userId,
            int executionOrder,
            String threadName) {
    }


    /**
     * Browser resources owned by exactly one user execution and one worker thread.
     */
    private record WorkerBrowserSession(
            Playwright playwright,
            Browser browser,
            BrowserContext context,
            Page page,
            int browserId,
            int contextId,
            int tabId) {
        void close() {
            try {
                if (page != null) {
                    page.close();
                }
            } catch (Exception ignored) {
            }
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
