package com.automation.playwright_framework;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/launcher")
public class TestLauncherController {

    private static final int NO_EXIT_CODE = -999;

    private volatile Process activeProcess = null;
    private volatile String activeType = null;
    private volatile Integer lastExitCode = null;
    private volatile long activeRunStartedAtMillis = Long.MIN_VALUE;
    private final List<String> lineBuffer = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> activeEmitters = new CopyOnWriteArrayList<>();
    private final TestReportController testReportController;

    public TestLauncherController(TestReportController testReportController) {
        this.testReportController = testReportController;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestParam String type) {
        synchronized (this) {
            if (!type.equals("ipt") && !type.equals("out") && !type.equals("coo")) {
                return ResponseEntity.badRequest().body(Map.of("error", "type must be 'ipt', 'out' or 'coo'"));
            }
            LauncherStatusResponse currentStatus = buildStatusResponse();
            if (currentStatus.running()) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "already running",
                        "type", currentStatus.type(),
                        "running", currentStatus.running(),
                        "displayState", currentStatus.displayState(),
                        "jobId", currentStatus.jobId() != null ? currentStatus.jobId() : "",
                        "jobStatus", currentStatus.jobStatus() != null ? currentStatus.jobStatus() : ""));
            }

            lineBuffer.clear();
            lastExitCode = null;
            activeType = type;
            activeRunStartedAtMillis = System.currentTimeMillis();

            java.io.File projectRoot = Paths.get(System.getProperty("user.dir")).toFile();
            String mvnwPath = new java.io.File(projectRoot, "mvnw.cmd").getAbsolutePath();

            List<String> cmd;
            if (type.equals("ipt")) {
                cmd = List.of("cmd.exe", "/c", mvnwPath, "-Dtest=IptDeclarationTestCase1Test",
                        "-Dtradenix.ipt.test.data=data/ipt-declaration-test-case-1.json", "test");
            } else if (type.equals("out")) {
                cmd = List.of("cmd.exe", "/c", mvnwPath, "-Dtest=OutDeclarationTestCase1Test",
                        "-Dtradenix.out.test.data=data/out-declaration-batch-test-case.json", "test");
            } else {
                // COO Declaration test — matches run-coo-declaration.cmd
                cmd = List.of("cmd.exe", "/c", mvnwPath, "-Dtest=CooDeclarationTestCase1Test",
                        "-Dtradenix.coo.test.data=data/coo-declaration-batch-test-case.json",
                        "-Dplaywright.headless=false", "-Dplaywright.slowmo.ms=250", "test");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);

            try {
                activeProcess = pb.start();
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "Failed to start process: " + e.getMessage()));
            }

            Process process = activeProcess;
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lineBuffer.add(line);
                        final String finalLine = line;
                        for (SseEmitter emitter : activeEmitters) {
                            try {
                                emitter.send(SseEmitter.event().data(finalLine));
                            } catch (IOException ex) {
                                activeEmitters.remove(emitter);
                            }
                        }
                    }
                    lastExitCode = process.waitFor();
                } catch (IOException | InterruptedException e) {
                    lastExitCode = -1;
                    Thread.currentThread().interrupt();
                } finally {
                    for (SseEmitter emitter : activeEmitters) {
                        try {
                            emitter.send(SseEmitter.event().data("__DONE__"));
                            emitter.complete();
                        } catch (IOException ignored) {}
                    }
                    activeEmitters.clear();
                }
            });
            reader.setDaemon(true);
            reader.start();
        }

        return ResponseEntity.ok(Map.of("started", true, "type", type));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);

        List<String> snapshot = List.copyOf(lineBuffer);
        try {
            for (String line : snapshot) {
                emitter.send(SseEmitter.event().data(line));
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        synchronized (this) {
            boolean running = activeProcess != null && activeProcess.isAlive();
            if (!running) {
                try {
                    if (lastExitCode != null) {
                        emitter.send(SseEmitter.event().data("__DONE__"));
                    }
                    emitter.complete();
                } catch (IOException ignored) {}
                return emitter;
            }
            activeEmitters.add(emitter);
        }

        emitter.onCompletion(() -> activeEmitters.remove(emitter));
        emitter.onTimeout(() -> activeEmitters.remove(emitter));

        return emitter;
    }

    @GetMapping("/status")
    public ResponseEntity<LauncherStatusResponse> status() {
        return ResponseEntity.ok(buildStatusResponse());
    }

    private LauncherStatusResponse buildStatusResponse() {
        boolean running = activeProcess != null && activeProcess.isAlive();
        String type = activeType != null ? activeType : "";
        String moduleLabel = moduleLabel(type);
        int exitCode = lastExitCode != null ? lastExitCode : NO_EXIT_CODE;
        String reportPrefix = reportPrefixForType(type);
        JobReportSnapshot jobSnapshot = resolveJobSnapshot(type, reportPrefix, activeRunStartedAtMillis);
        String displayState = resolveDisplayState(running, exitCode, jobSnapshot);
        boolean executionActive = running || (jobSnapshot != null && !jobSnapshot.terminal());

        return new LauncherStatusResponse(
                running,
                type,
                moduleLabel,
                exitCode,
                reportPrefix,
                displayState,
                executionActive,
                jobSnapshot != null ? jobSnapshot.jobId() : null,
                jobSnapshot != null ? jobSnapshot.jobStatus() : null,
                jobSnapshot != null ? jobSnapshot.messageReference() : null,
                jobSnapshot != null ? jobSnapshot.reportStatus() : null);
    }

    private JobReportSnapshot resolveJobSnapshot(String type, String reportPrefix, long runStartedAtMillis) {
        if (type == null || type.isBlank() || reportPrefix == null || reportPrefix.isBlank()) {
            return null;
        }

        try {
            TestReportController.TestReportResponse report = testReportController.buildLatestReport(reportPrefix, null, null);
            return report.batchCases().stream()
                    .filter(batchCase -> batchCase.updatedAtMillis() >= runStartedAtMillis)
                    .max(Comparator.comparingLong(TestReportController.BatchCaseResult::updatedAtMillis))
                    .map(batchCase -> new JobReportSnapshot(
                            batchCase.jobId(),
                            batchCase.jobStatus(),
                            batchCase.declarationNumber(),
                            batchCase.status(),
                            isTerminalDisplayState(mapJobState(batchCase.jobStatus()))))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveDisplayState(boolean running, int exitCode, JobReportSnapshot jobSnapshot) {
        String jobState = jobSnapshot != null ? mapJobState(jobSnapshot.jobStatus()) : null;
        if (jobState != null) {
            return jobState;
        }
        if (running) {
            return "RUNNING";
        }
        if (jobSnapshot != null) {
            return "IN_PROGRESS";
        }
        if (exitCode == NO_EXIT_CODE) {
            return "IDLE";
        }
        return exitCode == 0 ? "SUCCESS" : "FAILED";
    }

    private String mapJobState(String jobStatus) {
        String normalizedJobStatus = normalizeJobStatus(jobStatus);
        if (normalizedJobStatus != null) {
            return switch (normalizedJobStatus) {
                case "SUB" -> "PENDING";
                case "SNT" -> "IN_PROGRESS";
                case "PMT", "REG" -> "SUCCESS";
                case "DRF", "FLD", "REJ" -> "FAILED";
                default -> null;
            };
        }
        return null;
    }

    private String normalizeJobStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        return switch (normalized) {
            case "DRF", "DRAFT" -> "DRF";
            case "SUB", "SUBMITTED", "SUCCESS" -> "SUB";
            case "SNT", "SENT" -> "SNT";
            case "FLD", "FAILED", "FAILURE", "ISSUE" -> "FLD";
            case "REJ", "REJECTED" -> "REJ";
            case "PMT", "PERMIT_ISSUED", "PERMITISSUED" -> "PMT";
            case "REG", "REGISTERED" -> "REG";
            default -> normalized;
        };
    }

    private String reportPrefixForType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.trim().toLowerCase()) {
            case "ipt" -> "ipt-batch-submit";
            case "out" -> "out-batch-submit";
            case "coo" -> "coo-batch-submit";
            default -> null;
        };
    }

    private String moduleLabel(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        return switch (type.trim().toLowerCase()) {
            case "ipt" -> "In Payment (IPT)";
            case "out" -> "Out Declaration (OUT)";
            case "coo" -> "COO Declaration";
            default -> type;
        };
    }

    private boolean isTerminalDisplayState(String value) {
        return "SUCCESS".equals(value) || "FAILED".equals(value);
    }

    public record LauncherStatusResponse(
            boolean running,
            String type,
            String moduleLabel,
            int exitCode,
            String reportPrefix,
            String displayState,
            boolean executionActive,
            String jobId,
            String jobStatus,
            String messageRef,
            String reportStatus) {
    }

    private record JobReportSnapshot(
            String jobId,
            String jobStatus,
            String messageReference,
            String reportStatus,
            boolean terminal) {
    }
}
