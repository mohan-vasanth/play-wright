package com.automation.playwright_framework;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

@Service
public class JMeterExecutionService {

    private static final int MAX_LOG_LINES = 120;
    private static final double MAX_ERROR_PERCENTAGE = 5.0d;
    private static final double MAX_AVERAGE_RESPONSE_TIME_MS = 2000.0d;

    private final JMeterResultSummaryParser summaryParser = new JMeterResultSummaryParser();
    private final JMeterTestPlanCustomizer testPlanCustomizer = new JMeterTestPlanCustomizer();
    private final String jmeterExecutableSetting;
    private final Path projectRoot;
    private final Path bundledTestPlan;
    private final Path runtimeDirectory;
    private final Path uploadsDirectory;
    private final Path resultsDirectory;
    private final Path resultsFile;
    private final Path reportDirectory;
    private final Path reportArchive;
    private final Path executionLogFile;

    private volatile Process activeProcess;
    private volatile ExecutionState state;
    private final Deque<String> recentLogLines = new ArrayDeque<>();

    public JMeterExecutionService(
            @Value("${jmeter.executable:jmeter}") String jmeterExecutable,
            @Value("${jmeter.default-test-plan:jmeter/performance-test.jmx}") String defaultTestPlan) {
        this.jmeterExecutableSetting = jmeterExecutable;
        this.projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        this.bundledTestPlan = projectRoot.resolve(defaultTestPlan).normalize();
        this.runtimeDirectory = projectRoot.resolve(Paths.get("temp", "jmeter-runtime")).normalize();
        this.uploadsDirectory = runtimeDirectory.resolve("uploads");
        this.resultsDirectory = ArtifactPaths.JMETER_DIR;
        this.resultsFile = resultsDirectory.resolve("results.jtl");
        this.reportDirectory = ArtifactPaths.HTML_REPORT_DIR;
        this.reportArchive = ArtifactPaths.REPORTS_DIR.resolve("html-report.zip");
        this.executionLogFile = ArtifactPaths.REPORTS_DIR.resolve("jmeter-execution.log");
        this.state = ExecutionState.idle(defaultReportUrls());
    }

    public synchronized RunResponse run(RunRequest request, MultipartFile jmxFile) {
        return runInternal(request, jmxFile, null);
    }

    public synchronized RunResponse runGeneratedPlan(RunRequest request, Path sourcePlan) {
        return runInternal(request, null, sourcePlan);
    }

    private RunResponse runInternal(RunRequest request, MultipartFile jmxFile, Path sourcePlanOverride) {
        if (isProcessAlive(activeProcess)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A JMeter execution is already running.");
        }

        validateRequest(request);

        try {
            Files.createDirectories(runtimeDirectory);
            Files.createDirectories(uploadsDirectory);
            ArtifactPaths.ensureBaseDirectories();
            Files.createDirectories(resultsDirectory);

            cleanupPreviousArtifacts();
            String resolvedExecutable = resolveJMeterExecutable();

            Path sourceTestPlan = sourcePlanOverride != null
                    ? sourcePlanOverride.toAbsolutePath().normalize()
                    : resolveSourceTestPlan(jmxFile);
            String runId = "jmeter-" + UUID.randomUUID();
            Path customizedPlan = runtimeDirectory.resolve(runId + ".jmx");
            testPlanCustomizer.customize(sourceTestPlan, customizedPlan, request);

            List<String> command = buildRunCommand(resolvedExecutable, customizedPlan);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(projectRoot.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            activeProcess = process;
            recentLogLines.clear();
            state = ExecutionState.running(runId, request, Instant.now(), command, defaultReportUrls());

            startLogReader(process);
            startCompletionWatcher(process, runId, request, command);

            return new RunResponse(
                    true,
                    runId,
                    "JMeter execution started.",
                    commandToDisplay(command));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            state = ExecutionState.failed(
                    request,
                    "Unable to start JMeter: " + exception.getMessage(),
                    null,
                    List.of(),
                    defaultReportUrls());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, state.message());
        }
    }

    public synchronized StopResponse stop() {
        if (!isProcessAlive(activeProcess)) {
            return new StopResponse(false, "No active JMeter execution was found.");
        }

        Process process = activeProcess;
        state = state.withStatus("STOPPING", "Stop requested. Waiting for JMeter to exit.");
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return new StopResponse(true, "Stop signal sent to JMeter.");
    }

    public synchronized StatusResponse status() {
        boolean running = isProcessAlive(activeProcess);
        JMeterResultSummaryParser.JMeterSummary summary = readSummaryQuietly();
        ExecutionState current = state.withSummary(summary);
        if (!running && "RUNNING".equals(current.status())) {
            current = current.withStatus("COMPLETED", "JMeter finished. Refreshing report metadata.");
        }
        ReportUrls reportUrls = resolveReportUrls();
        current = current.withReportUrls(reportUrls);
        state = current;

        ProgressSnapshot progress = progressFor(current, running);
        return new StatusResponse(
                current.runId(),
                current.status(),
                running,
                current.message(),
                current.request(),
                current.startedAt() != null ? current.startedAt().toString() : null,
                current.finishedAt() != null ? current.finishedAt().toString() : null,
                current.exitCode(),
                progress.progressPercentage(),
                progress.indeterminate(),
                progress.label(),
                current.summary().totalRequests(),
                current.summary().successCount(),
                current.summary().failureCount(),
                round(current.summary().averageResponseTimeMs()),
                round(current.summary().averageLatencyMs()),
                round(current.summary().throughput()),
                round(current.summary().errorPercentage()),
                current.thresholdsPassed(),
                current.thresholdMessage(),
                current.commandDisplay(),
                reportUrls.reportUrl(),
                reportUrls.downloadUrl(),
                reportUrls.openUrl(),
                reportUrls.available(),
                List.copyOf(recentLogLines));
    }

    public synchronized ReportResponse report(boolean refresh) {
        if (refresh && Files.isRegularFile(resultsFile) && !Files.isDirectory(reportDirectory)) {
            generateHtmlReport();
        }

        JMeterResultSummaryParser.JMeterSummary summary = readSummaryQuietly();
        ReportUrls reportUrls = resolveReportUrls();
        state = state.withSummary(summary).withReportUrls(reportUrls);

        return new ReportResponse(
                reportUrls.available(),
                reportUrls.reportUrl(),
                reportUrls.downloadUrl(),
                reportUrls.openUrl(),
                Files.isRegularFile(resultsFile) ? resultsFile.toString() : null,
                summary.totalRequests(),
                summary.successCount(),
                summary.failureCount(),
                round(summary.averageResponseTimeMs()),
                round(summary.averageLatencyMs()),
                round(summary.throughput()),
                round(summary.errorPercentage()),
                evaluateThresholds(summary).passed(),
                evaluateThresholds(summary).message(),
                reportUrls.available()
                        ? "HTML report is ready."
                        : (Files.isRegularFile(resultsFile)
                        ? "JMeter finished, but the HTML report has not been generated yet."
                        : "No JMeter results are available yet."));
    }

    public Path reportArchive() {
        if (!Files.isDirectory(reportDirectory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "HTML report is not available.");
        }
        try {
            createReportArchive();
            return reportArchive;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create report archive: " + exception.getMessage());
        }
    }

    private void validateRequest(RunRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is empty.");
        }
        validateTargetUrl(request.targetUrl());
        validatePositive(request.threads(), "Number of users");
        validatePositive(request.rampUpSeconds(), "Ramp up time");
        validatePositive(request.loopCount(), "Loop count");
        if (request.durationSeconds() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Test duration must be zero or greater.");
        }
    }

    private void validateTargetUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target URL is required.");
        }
        try {
            URI uri = URI.create(targetUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Target URL must include protocol and host.");
            }
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target URL must be a valid absolute URL.");
        }
    }

    private void validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than zero.");
        }
    }

    private Path resolveSourceTestPlan(MultipartFile jmxFile) throws IOException {
        if (jmxFile == null || jmxFile.isEmpty()) {
            if (!Files.isRegularFile(bundledTestPlan)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No JMX file was uploaded and the default test plan was not found.");
            }
            return bundledTestPlan;
        }

        String originalFileName = Objects.requireNonNullElse(jmxFile.getOriginalFilename(), "uploaded-test-plan.jmx");
        if (!originalFileName.toLowerCase().endsWith(".jmx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file must be a .jmx test plan.");
        }

        Path uploadedFile = uploadsDirectory.resolve(System.currentTimeMillis() + "-" + sanitizeFileName(originalFileName))
                .normalize();
        Files.write(uploadedFile, jmxFile.getBytes());
        return uploadedFile;
    }

    private List<String> buildRunCommand(String executable, Path testPlan) {
        List<String> command = baseExecutableCommand(executable);
        command.add("-n");
        command.add("-t");
        command.add(testPlan.toString());
        command.add("-l");
        command.add(resultsFile.toString());
        command.add("-e");
        command.add("-o");
        command.add(reportDirectory.toString());
        return command;
    }

    private List<String> buildReportCommand(String executable) {
        List<String> command = baseExecutableCommand(executable);
        command.add("-g");
        command.add(resultsFile.toString());
        command.add("-o");
        command.add(reportDirectory.toString());
        return command;
    }

    private List<String> baseExecutableCommand(String executable) {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(executable);
        return command;
    }

    private void cleanupPreviousArtifacts() throws IOException {
        deleteDirectory(reportDirectory);
        Files.deleteIfExists(resultsFile);
        Files.deleteIfExists(reportArchive);
        Files.deleteIfExists(executionLogFile);
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private void startLogReader(Process process) {
        Thread reader = new Thread(() -> {
            try (BufferedReader bufferedReader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    synchronized (this) {
                        if (recentLogLines.size() == MAX_LOG_LINES) {
                            recentLogLines.removeFirst();
                        }
                        recentLogLines.addLast(line);
                    }
                    appendExecutionLog(line);
                }
            } catch (IOException ignored) {
            }
        }, "jmeter-log-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void startCompletionWatcher(Process process, String runId, RunRequest request, List<String> command) {
        Thread watcher = new Thread(() -> {
            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exitCode = -1;
            }

            synchronized (this) {
                if (activeProcess == process) {
                    activeProcess = null;
                }

                JMeterResultSummaryParser.JMeterSummary summary = readSummaryQuietly();
                ReportUrls reportUrls = resolveReportUrls();
                ThresholdAssessment thresholdAssessment = evaluateThresholds(summary);
                String status = exitCode == 0 && thresholdAssessment.passed() ? "COMPLETED" : "FAILED";
                String message = exitCode == 0
                        ? thresholdAssessment.message()
                        : "JMeter execution failed with exit code " + exitCode + ".";

                if (exitCode == 0 && !reportUrls.available() && Files.isRegularFile(resultsFile)) {
                    generateHtmlReport();
                    reportUrls = resolveReportUrls();
                }

                state = new ExecutionState(
                        runId,
                        status,
                        false,
                        message,
                        request,
                        state.startedAt(),
                        Instant.now(),
                        exitCode,
                        commandToDisplay(command),
                        summary,
                        reportUrls,
                        thresholdAssessment.passed(),
                        thresholdAssessment.message());
            }
        }, "jmeter-completion-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void generateHtmlReport() {
        try {
            deleteDirectory(reportDirectory);
            Files.deleteIfExists(reportArchive);
            String resolvedExecutable = resolveJMeterExecutable();
            ProcessBuilder processBuilder = new ProcessBuilder(buildReportCommand(resolvedExecutable));
            processBuilder.directory(projectRoot.toFile());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            try (BufferedReader bufferedReader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (bufferedReader.readLine() != null) {
                    // Consume output to avoid blocking.
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Report generation exited with code " + exitCode + ".");
            }
        } catch (Exception exception) {
            state = state.withStatus(
                    "FAILED",
                    "JMeter finished, but HTML report generation failed: " + exception.getMessage());
        }
    }

    private ReportUrls resolveReportUrls() {
        boolean available = Files.isDirectory(reportDirectory) && Files.isRegularFile(reportDirectory.resolve("index.html"));
        return new ReportUrls(
                available,
                available ? "/jmeter-reports/index.html" : null,
                available ? "/api/jmeter/report/download" : null,
                available ? "/jmeter-reports/index.html" : null);
    }

    private ReportUrls defaultReportUrls() {
        return new ReportUrls(false, null, null, null);
    }

    private void createReportArchive() throws IOException {
        Files.createDirectories(reportArchive.getParent());
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(reportArchive))) {
            try (Stream<Path> paths = Files.walk(reportDirectory)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    ZipEntry entry = new ZipEntry(reportDirectory.relativize(path).toString().replace('\\', '/'));
                    try {
                        zipOutputStream.putNextEntry(entry);
                        Files.copy(path, zipOutputStream);
                        zipOutputStream.closeEntry();
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
            } catch (RuntimeException exception) {
                if (exception.getCause() instanceof IOException ioException) {
                    throw ioException;
                }
                throw exception;
            }
        }
    }

    private JMeterResultSummaryParser.JMeterSummary readSummaryQuietly() {
        try {
            return summaryParser.parse(resultsFile);
        } catch (Exception ignored) {
            return JMeterResultSummaryParser.JMeterSummary.empty();
        }
    }

    private ProgressSnapshot progressFor(ExecutionState current, boolean running) {
        if (current.request() == null || current.startedAt() == null) {
            return new ProgressSnapshot(current.completed() ? 100 : 0, true, "Waiting for execution");
        }

        if (!running && current.completed()) {
            return new ProgressSnapshot(100, false, "Execution finished");
        }

        int durationSeconds = current.request().durationSeconds();
        if (durationSeconds <= 0) {
            return new ProgressSnapshot(0, true, "Running without a fixed duration");
        }

        long elapsedSeconds = Math.max(0L, Instant.now().getEpochSecond() - current.startedAt().getEpochSecond());
        int progress = (int) Math.min(99L, Math.round((elapsedSeconds * 100.0d) / durationSeconds));
        return new ProgressSnapshot(progress, false, elapsedSeconds + "s / " + durationSeconds + "s");
    }

    private boolean isProcessAlive(Process process) {
        return process != null && process.isAlive();
    }

    private void appendExecutionLog(String line) {
        try {
            Files.writeString(
                    executionLogFile,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private ThresholdAssessment evaluateThresholds(JMeterResultSummaryParser.JMeterSummary summary) {
        if (summary.totalRequests() <= 0L) {
            return new ThresholdAssessment(true, "JMeter execution completed. No requests were recorded.");
        }
        if (summary.errorPercentage() > MAX_ERROR_PERCENTAGE) {
            return new ThresholdAssessment(
                    false,
                    String.format("JMeter failed: error percentage %.2f%% exceeded %.2f%%.", summary.errorPercentage(), MAX_ERROR_PERCENTAGE));
        }
        if (summary.averageResponseTimeMs() > MAX_AVERAGE_RESPONSE_TIME_MS) {
            return new ThresholdAssessment(
                    false,
                    String.format(
                            "JMeter failed: average response time %.2f ms exceeded %.2f ms.",
                            summary.averageResponseTimeMs(),
                            MAX_AVERAGE_RESPONSE_TIME_MS));
        }
        return new ThresholdAssessment(true, "JMeter execution completed within the configured thresholds.");
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String commandToDisplay(List<String> command) {
        return String.join(" ", command);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String resolveJMeterExecutable() {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, jmeterExecutableSetting);
        addCandidate(candidates, System.getenv("JMETER_BIN"));

        String jmeterHome = System.getenv("JMETER_HOME");
        if (jmeterHome != null && !jmeterHome.isBlank()) {
            addCandidate(candidates, Paths.get(jmeterHome, "bin", executableFileName()).toString());
        }

        if (isWindows()) {
            addCandidate(candidates, "C:\\apache-jmeter-5.6.3\\bin\\jmeter.bat");
            addCandidate(candidates, "C:\\apache-jmeter-5.6.3\\bin\\jmeter.exe");
            addCandidate(candidates, "C:\\Program Files\\Apache JMeter\\bin\\jmeter.bat");
            addCandidate(candidates, "C:\\Program Files\\Apache JMeter\\bin\\jmeter.exe");
        }

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Path candidatePath = safePath(candidate);
            if (candidatePath != null && Files.isRegularFile(candidatePath)) {
                return candidatePath.toString();
            }
            if (candidatePath == null && isCommandAvailable(candidate)) {
                return candidate;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "JMeter executable was not found. Set 'jmeter.executable' in application.properties or define JMETER_HOME.");
    }

    private void addCandidate(List<String> candidates, String candidate) {
        if (candidate != null && !candidate.isBlank() && !candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private Path safePath(String candidate) {
        try {
            if (candidate.contains("/") || candidate.contains("\\") || candidate.contains(":")) {
                return Paths.get(candidate).toAbsolutePath().normalize();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isCommandAvailable(String command) {
        try {
            List<String> lookup = new ArrayList<>();
            if (isWindows()) {
                lookup.add("where.exe");
            } else {
                lookup.add("which");
            }
            lookup.add(command);
            Process process = new ProcessBuilder(lookup)
                    .directory(projectRoot.toFile())
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String executableFileName() {
        return isWindows() ? "jmeter.bat" : "jmeter";
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    public record RunRequest(
            String targetUrl,
            int threads,
            int rampUpSeconds,
            int loopCount,
            int durationSeconds,
            String declarationType,
            String reportPrefix,
            String selectedJson) {
    }

    public record RunResponse(
            boolean started,
            String runId,
            String message,
            String command) {
    }

    public record StopResponse(
            boolean stopped,
            String message) {
    }

    public record StatusResponse(
            String runId,
            String status,
            boolean running,
            String message,
            RunRequest configuration,
            String startedAt,
            String finishedAt,
            Integer exitCode,
            int progressPercentage,
            boolean progressIndeterminate,
            String progressLabel,
            long totalRequests,
            long successCount,
            long failureCount,
            double averageResponseTime,
            double averageLatency,
            double throughput,
            double errorPercentage,
            boolean thresholdsPassed,
            String thresholdMessage,
            String command,
            String reportUrl,
            String downloadUrl,
            String openUrl,
            boolean reportAvailable,
            List<String> recentLogs) {
    }

    public record ReportResponse(
            boolean reportAvailable,
            String reportUrl,
            String downloadUrl,
            String openUrl,
            String resultsFile,
            long totalRequests,
            long successCount,
            long failureCount,
            double averageResponseTime,
            double averageLatency,
            double throughput,
            double errorPercentage,
            boolean thresholdsPassed,
            String thresholdMessage,
            String message) {
    }

    private record ReportUrls(
            boolean available,
            String reportUrl,
            String downloadUrl,
            String openUrl) {
    }

    private record ProgressSnapshot(
            int progressPercentage,
            boolean indeterminate,
            String label) {
    }

    private record ExecutionState(
            String runId,
            String status,
            boolean running,
            String message,
            RunRequest request,
            Instant startedAt,
            Instant finishedAt,
            Integer exitCode,
            String commandDisplay,
            JMeterResultSummaryParser.JMeterSummary summary,
            ReportUrls reportUrls,
            boolean thresholdsPassed,
            String thresholdMessage) {

        static ExecutionState idle(ReportUrls reportUrls) {
            return new ExecutionState(
                    null,
                    "IDLE",
                    false,
                    "Ready to execute a JMeter test.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    JMeterResultSummaryParser.JMeterSummary.empty(),
                    reportUrls,
                    true,
                    "Waiting for execution.");
        }

        static ExecutionState running(
                String runId,
                RunRequest request,
                Instant startedAt,
                List<String> command,
                ReportUrls reportUrls) {
            return new ExecutionState(
                    runId,
                    "RUNNING",
                    true,
                    "JMeter is running.",
                    request,
                    startedAt,
                    null,
                    null,
                    String.join(" ", command),
                    JMeterResultSummaryParser.JMeterSummary.empty(),
                    reportUrls,
                    true,
                    "Thresholds will be evaluated after completion.");
        }

        static ExecutionState failed(
                RunRequest request,
                String message,
                Integer exitCode,
                List<String> command,
                ReportUrls reportUrls) {
            return new ExecutionState(
                    null,
                    "FAILED",
                    false,
                    message,
                    request,
                    null,
                    Instant.now(),
                    exitCode,
                    command.isEmpty() ? null : String.join(" ", command),
                    JMeterResultSummaryParser.JMeterSummary.empty(),
                    reportUrls,
                    false,
                    message);
        }

        ExecutionState withStatus(String nextStatus, String nextMessage) {
            return new ExecutionState(
                    runId,
                    nextStatus,
                    "RUNNING".equals(nextStatus) || "STOPPING".equals(nextStatus),
                    nextMessage,
                    request,
                    startedAt,
                    finishedAt,
                    exitCode,
                    commandDisplay,
                    summary,
                    reportUrls,
                    thresholdsPassed,
                    thresholdMessage);
        }

        ExecutionState withSummary(JMeterResultSummaryParser.JMeterSummary nextSummary) {
            return new ExecutionState(
                    runId,
                    status,
                    running,
                    message,
                    request,
                    startedAt,
                    finishedAt,
                    exitCode,
                    commandDisplay,
                    nextSummary,
                    reportUrls,
                    thresholdsPassed,
                    thresholdMessage);
        }

        ExecutionState withReportUrls(ReportUrls nextReportUrls) {
            return new ExecutionState(
                    runId,
                    status,
                    running,
                    message,
                    request,
                    startedAt,
                    finishedAt,
                    exitCode,
                    commandDisplay,
                    summary,
                    nextReportUrls,
                    thresholdsPassed,
                    thresholdMessage);
        }

        boolean completed() {
            return "COMPLETED".equals(status) || "FAILED".equals(status) || "STOPPED".equals(status);
        }
    }

    private record ThresholdAssessment(
            boolean passed,
            String message) {
    }
}
