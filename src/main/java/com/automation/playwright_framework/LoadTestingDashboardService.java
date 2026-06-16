package com.automation.playwright_framework;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class LoadTestingDashboardService {

    private static final int MAX_LOG_LINES = 250;
    private static final int MAX_SCREENSHOTS = 24;

    private final JMeterExecutionService jMeterExecutionService;
    private final DynamicJmxBuilder dynamicJmxBuilder = new DynamicJmxBuilder();

    private final Deque<String> recentLogs = new ArrayDeque<>();
    private final List<ScreenshotEntry> screenshots = new ArrayList<>();

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
    private volatile int completedUsers;
    private volatile int successCount;
    private volatile int failureCount;
    private volatile long totalWorkflowDurationMs;
    private volatile Path runScreenshotsDirectory;
    private volatile Path runReportsDirectory;
    private volatile Path runPlaywrightLogPath;
    private volatile Thread coordinatorThread;
    private volatile ExecutorService workerPool;

    public LoadTestingDashboardService(JMeterExecutionService jMeterExecutionService) {
        this.jMeterExecutionService = jMeterExecutionService;
    }

    public synchronized StartResponse start(RunRequest request) {
        if (running) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A dashboard execution is already running.");
        }

        validateRequest(request);

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

        this.currentRequest = request;
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.running = true;
        this.stopRequested = false;
        this.status = "RUNNING";
        this.message = "Starting Playwright browser workers and background JMeter execution.";
        this.runningUsers = 0;
        this.openTabsCount = 0;
        this.completedUsers = 0;
        this.successCount = 0;
        this.failureCount = 0;
        this.totalWorkflowDurationMs = 0L;
        this.recentLogs.clear();
        this.screenshots.clear();
        appendLog("Run requested for " + request.totalUsers() + " users with " + request.browserTabs() + " browser tabs.");

        Path jmxPath = runReportsDirectory.resolve("dynamic-load-test.jmx");
        try {
            dynamicJmxBuilder.writePlan(
                    jmxPath,
                    request.url(),
                    request.totalUsers(),
                    request.jmeterRampUpSeconds() > 0 ? request.jmeterRampUpSeconds() : defaultRampUp(request.totalUsers()),
                    request.jmeterLoopCount() > 0 ? request.jmeterLoopCount() : 1);
            appendLog("Generated dynamic JMX plan: " + jmxPath.getFileName());
        } catch (IOException exception) {
            resetToFailedState("Unable to generate JMX plan: " + exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
        }

        JMeterExecutionService.RunRequest jmeterRequest = new JMeterExecutionService.RunRequest(
                request.url(),
                request.totalUsers(),
                request.jmeterRampUpSeconds() > 0 ? request.jmeterRampUpSeconds() : defaultRampUp(request.totalUsers()),
                request.jmeterLoopCount() > 0 ? request.jmeterLoopCount() : 1,
                0,
                "load-dashboard",
                runId,
                request.url());

        try {
            jMeterExecutionService.runGeneratedPlan(jmeterRequest, jmxPath);
            appendLog("Started background JMeter execution for target: " + request.url());
        } catch (ResponseStatusException exception) {
            resetToFailedState("Unable to start JMeter: " + exception.getReason());
            throw exception;
        }

        workerPool = Executors.newFixedThreadPool(Math.max(1, Math.min(request.browserTabs(), request.totalUsers())));
        coordinatorThread = new Thread(() -> executeRun(request), "load-dashboard-coordinator");
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
        message = "Stop requested. Waiting for browser workers to finish.";
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
                successCount,
                failureCount,
                openTabsCount,
                round(averageResponseTime),
                round(throughput),
                List.copyOf(recentLogs),
                screenshots.stream()
                        .sorted(Comparator.comparing(ScreenshotEntry::capturedAt).reversed())
                        .limit(MAX_SCREENSHOTS)
                        .toList(),
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
                    message = "Execution stopped before all browser users completed.";
                } else if (failureCount > 0) {
                    status = "COMPLETED";
                    message = "Browser execution completed with " + failureCount + " failed users.";
                } else {
                    status = "COMPLETED";
                    message = "Browser execution completed successfully.";
                }
            }
            appendLog(message);
            flushPlaywrightLog();
        }
    }

    private void executeSingleUser(RunRequest request, int userIndex) {
        long startedAtMillis = System.currentTimeMillis();
        Page page = null;
        BrowserContext context = null;
        Browser browser = null;
        Playwright playwright = null;
        boolean success = false;
        String outcomeMessage = "Workflow completed.";

        markUserStarted(userIndex);
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(request.headless()));
            context = browser.newContext();
            page = context.newPage();

            appendLog("User " + userIndex + ": browser tab opened.");
            page.navigate(request.url(), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            performLogin(page, request, userIndex);
            navigateIntoWorkflowIfPresent(page);
            fillVisibleFields(page, userIndex);
            submitVisibleForm(page);

            String resultText = readResultSummary(page);
            outcomeMessage = resultText == null || resultText.isBlank()
                    ? "Submission completed without a visible status message."
                    : resultText;
            success = true;
            captureScreenshot(page, userIndex, "success", outcomeMessage);
            appendLog("User " + userIndex + ": success.");
        } catch (Exception exception) {
            outcomeMessage = rootMessage(exception);
            appendLog("User " + userIndex + ": failure - " + outcomeMessage);
            if (page != null) {
                captureScreenshot(page, userIndex, "failure", outcomeMessage);
            }
        } finally {
            closeQuietly(context);
            closeQuietly(browser);
            closeQuietly(playwright);
            markUserFinished(userIndex, success, System.currentTimeMillis() - startedAtMillis, outcomeMessage);
        }
    }

    private void performLogin(Page page, RunRequest request, int userIndex) {
        tryClick(page, "#btn-demo-radio-2");
        fillVisibleInput(page, request.username(),
                "input[formcontrolname='username']",
                "input[name='username']",
                "input[type='email']",
                "input[autocomplete='username']",
                "input[placeholder*='User' i]",
                "input[placeholder*='Email' i]");

        if (isVisible(page, "button.btn.btn-xl.btn-icon.btn-outlined")) {
            page.click("button.btn.btn-xl.btn-icon.btn-outlined");
            page.waitForTimeout(500);
            selectFirstVisibleOptions(page);
        }

        fillVisibleInput(page, request.password(),
                "input[formcontrolname='password']",
                "input[name='password']",
                "input[type='password']",
                "input[autocomplete='current-password']");

        clickFirstVisibleButton(page,
                "button[type='submit']",
                "button:has-text('Login')",
                "button:has-text('LOG IN')",
                "button:has-text('Sign In')");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(800);
        appendLog("User " + userIndex + ": login flow completed.");
    }

    private void navigateIntoWorkflowIfPresent(Page page) {
        clickFirstVisibleButton(page,
                "button:has-text('NEW DECLARATION')",
                "button:has-text('Create Permit')",
                "button:has-text('Permit Creation')",
                "a:has-text('Permit Creation')",
                "a:has-text('NEW DECLARATION')");
        page.waitForTimeout(600);
    }

    private void fillVisibleFields(Page page, int userIndex) {
        page.evaluate("""
                (payload) => {
                    const now = new Date();
                    const yyyy = String(now.getFullYear());
                    const mm = String(now.getMonth() + 1).padStart(2, '0');
                    const dd = String(now.getDate()).padStart(2, '0');
                    const isoDate = `${yyyy}-${mm}-${dd}`;
                    const slashDate = `${dd}/${mm}/${yyyy}`;
                    const dashDate = `${dd}-${mm}-${yyyy}`;
                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toLowerCase();
                    const isVisible = element => {
                        if (!element) {
                            return false;
                        }
                        const style = window.getComputedStyle(element);
                        return style.display !== 'none'
                            && style.visibility !== 'hidden'
                            && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    };

                    const allFields = Array.from(document.querySelectorAll('input, textarea, select'))
                        .filter(isVisible)
                        .slice(0, 120);

                    for (const field of allFields) {
                        const tag = field.tagName.toLowerCase();
                        const type = normalize(field.getAttribute('type')) || 'text';
                        const key = normalize(
                            field.getAttribute('name')
                            || field.getAttribute('id')
                            || field.getAttribute('placeholder')
                            || field.getAttribute('formcontrolname'));

                        if (type === 'hidden' || type === 'password' || type === 'submit' || type === 'button') {
                            continue;
                        }

                        if (tag === 'select') {
                            const option = Array.from(field.options || []).find(candidate => candidate.value);
                            if (option) {
                                field.value = option.value;
                                field.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                            continue;
                        }

                        if (type === 'checkbox' || type === 'radio') {
                            if (!field.checked) {
                                field.click();
                            }
                            continue;
                        }

                        if (field.value && String(field.value).trim().length > 0) {
                            continue;
                        }

                        let value = `Load Test User ${payload.userIndex}`;
                        if (type === 'email' || key.includes('email')) {
                            value = `load.user${payload.userIndex}@example.com`;
                        } else if (type === 'number' || key.includes('amount') || key.includes('qty') || key.includes('count')) {
                            value = '1';
                        } else if (type === 'date') {
                            value = isoDate;
                        } else if (key.includes('date')) {
                            value = key.includes('/') ? slashDate : dashDate;
                        } else if (type === 'tel' || key.includes('phone') || key.includes('mobile') || key.includes('tel')) {
                            value = '9000000000';
                        } else if (key.includes('reference') || key.includes('code')) {
                            value = `LD-${payload.userIndex}`;
                        } else if (tag === 'textarea' || key.includes('remark') || key.includes('description')) {
                            value = `Load testing dashboard auto-fill for user ${payload.userIndex}.`;
                        }

                        field.focus();
                        field.value = value;
                        field.dispatchEvent(new Event('input', { bubbles: true }));
                        field.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                }
                """, java.util.Map.of("userIndex", userIndex));
        page.waitForTimeout(700);
    }

    private void submitVisibleForm(Page page) {
        clickFirstVisibleButton(page,
                "button:has-text('SUBMIT DECLARATION')",
                "button:has-text('Submit')",
                "button:has-text('SUBMIT')",
                "button:has-text('Save')",
                "button:has-text('SAVE')",
                "button:has-text('Create')",
                "button:has-text('CREATE')",
                "button:has-text('Run Test')");
        page.waitForTimeout(1000);
    }

    private void clickFirstVisibleButton(Page page, String... selectors) {
        for (String selector : selectors) {
            try {
                if (page.locator(selector).first().isVisible()) {
                    page.locator(selector).first().click();
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void tryClick(Page page, String selector) {
        try {
            if (page.locator(selector).first().isVisible()) {
                page.locator(selector).first().click();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isVisible(Page page, String selector) {
        try {
            return page.locator(selector).first().isVisible();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void fillVisibleInput(Page page, String value, String... selectors) {
        if (value == null) {
            return;
        }
        for (String selector : selectors) {
            try {
                if (page.locator(selector).first().isVisible()) {
                    page.locator(selector).first().fill(value);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void selectFirstVisibleOptions(Page page) {
        page.evaluate("""
                () => {
                    const isVisible = element => {
                        if (!element) {
                            return false;
                        }
                        const style = window.getComputedStyle(element);
                        return style.display !== 'none'
                            && style.visibility !== 'hidden'
                            && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    };
                    const selects = Array.from(document.querySelectorAll('select')).filter(isVisible);
                    for (const select of selects) {
                        const option = Array.from(select.options || []).find(candidate => candidate.value);
                        if (!option) {
                            continue;
                        }
                        select.value = option.value;
                        select.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                }
                """);
    }

    private String readResultSummary(Page page) {
        try {
            Object result = page.evaluate("""
                    () => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const isVisible = element => {
                            if (!element) {
                                return false;
                            }
                            const style = window.getComputedStyle(element);
                            return style.display !== 'none'
                                && style.visibility !== 'hidden'
                                && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        };
                        const candidates = Array.from(document.querySelectorAll('[role="alert"], .alert, .toast, .notification, body, body *'))
                            .filter(isVisible)
                            .map(element => normalize(element.innerText || element.textContent))
                            .filter(Boolean)
                            .filter(text => text.length <= 400);
                        return candidates.find(text => /success|submitted|completed|saved|permit|declaration/i.test(text))
                            || candidates.find(text => /error|failed|invalid/i.test(text))
                            || '';
                    }
                    """);
            return result == null ? "" : String.valueOf(result).trim();
        } catch (PlaywrightException exception) {
            return exception.getMessage();
        }
    }

    private synchronized void markUserStarted(int userIndex) {
        runningUsers++;
        openTabsCount++;
        appendLog("User " + userIndex + ": starting browser workflow.");
    }

    private synchronized void markUserFinished(int userIndex, boolean success, long durationMs, String outcomeMessage) {
        runningUsers = Math.max(0, runningUsers - 1);
        openTabsCount = Math.max(0, openTabsCount - 1);
        completedUsers++;
        totalWorkflowDurationMs += Math.max(durationMs, 0L);
        if (success) {
            successCount++;
        } else {
            failureCount++;
        }
        message = "Latest result: user " + userIndex + " - " + truncate(outcomeMessage, 180);
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
                    truncate(summary, 200),
                    Instant.now().toString()));
        } catch (Exception exception) {
            appendLog("User " + userIndex + ": screenshot capture failed - " + rootMessage(exception));
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

    private double calculateThroughput() {
        if (startedAt == null) {
            return 0.0d;
        }
        Instant finished = finishedAt != null ? finishedAt : Instant.now();
        long elapsedMillis = Math.max(1000L, finished.toEpochMilli() - startedAt.toEpochMilli());
        return completedUsers / (elapsedMillis / 1000.0d);
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
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

    public record RunRequest(
            int totalUsers,
            int browserTabs,
            String url,
            String username,
            String password,
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
            int successCount,
            int failureCount,
            int openTabsCount,
            double averageResponseTime,
            double throughput,
            List<String> recentLogs,
            List<ScreenshotEntry> screenshots,
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
}
