package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/launcher")
public class TestLauncherController {

    private static final int NO_EXIT_CODE = -999;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, LauncherTypeConfig> TYPE_CONFIGS = Map.ofEntries(
            Map.entry("ipt", new LauncherTypeConfig(
                    "ipt",
                    "In Payment (IPT)",
                    "ipt-batch-submit",
                    "IPT",
                    "IptDeclarationTestCase1Test",
                    "tradenix.ipt.test.data",
                    List.of(),
                    false,
                    true)),
            Map.entry("inp", new LauncherTypeConfig(
                    "inp",
                    "In Non-Payment (INP)",
                    null,
                    "INP",
                    null,
                    null,
                    List.of(),
                    false,
                    false)),
            Map.entry("tnp", new LauncherTypeConfig(
                    "tnp",
                    "Transhipment (TNP)",
                    null,
                    "TNP",
                    null,
                    null,
                    List.of(),
                    false,
                    false)),
            Map.entry("out", new LauncherTypeConfig(
                    "out",
                    "Out Declaration (OUT)",
                    "out-batch-submit",
                    "OUT",
                    "OutDeclarationTestCase1Test",
                    "tradenix.out.test.data",
                    List.of(),
                    false,
                    true)),
            Map.entry("coo", new LauncherTypeConfig(
                    "coo",
                    "COO Declaration",
                    "coo-batch-submit",
                    "COO",
                    "CooDeclarationTestCase1Test",
                    "tradenix.coo.test.data",
                    List.of("-Dplaywright.headless=false", "-Dplaywright.slowmo.ms=250"),
                    false,
                    true)));

    private volatile Process activeProcess = null;
    private volatile String activeType = null;
    private volatile String activePermitType = null;
    private volatile Integer lastExitCode = null;
    private volatile long activeRunStartedAtMillis = Long.MIN_VALUE;
    private final List<String> lineBuffer = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> activeEmitters = new CopyOnWriteArrayList<>();
    private final TestReportController testReportController;

    public TestLauncherController(TestReportController testReportController) {
        this.testReportController = testReportController;
    }

    @GetMapping("/json-options")
    public ResponseEntity<?> jsonOptions(@RequestParam String type) {
        LauncherTypeConfig config = resolveTypeConfig(type);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown declaration type: " + type));
        }

        try {
            Path resourceFolderPath = resolveResourceFolderPath(config);
            return ResponseEntity.ok(Map.of(
                    "type", config.type(),
                    "moduleLabel", config.moduleLabel(),
                    "resourceFolder", config.resourceFolder(),
                    "configuredFolder", configuredResourceFolder(config),
                    "resolvedFolderPath", resourceFolderPath != null ? resourceFolderPath.toString() : "",
                    "folderFound", resourceFolderPath != null,
                    "requiresPermitType", config.requiresPermitType(),
                    "executable", config.executable(),
                    "files", listJsonFiles(resourceFolderPath)));
        } catch (IOException exception) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Unable to list JSON files for " + config.type() + ": " + exception.getMessage()));
        }
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(
            @RequestParam String type,
            @RequestParam(required = false) String jsonResource,
            @RequestParam(required = false) MultipartFile jsonFile) {
        synchronized (this) {
            LauncherTypeConfig config = resolveTypeConfig(type);
            if (config == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown declaration type: " + type));
            }
            if (!config.executable()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", config.moduleLabel() + " automation runner is not implemented in this project yet."));
            }

            LauncherStatusResponse currentStatus = buildStatusResponse();
            if (currentStatus.running()) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "already running",
                        "type", currentStatus.type(),
                        "permitType", currentStatus.permitType() != null ? currentStatus.permitType() : "",
                        "running", currentStatus.running(),
                        "displayState", currentStatus.displayState(),
                        "jobId", currentStatus.jobId() != null ? currentStatus.jobId() : "",
                        "jobStatus", currentStatus.jobStatus() != null ? currentStatus.jobStatus() : "",
                        "messageRef", currentStatus.messageRef() != null ? currentStatus.messageRef() : "",
                        "pmtNumber", currentStatus.pmtNumber() != null ? currentStatus.pmtNumber() : ""));
            }

            String resolvedJsonPath;
            try {
                resolvedJsonPath = resolveJsonInput(config, jsonResource, jsonFile);
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
            } catch (IOException exception) {
                return ResponseEntity.status(500).body(Map.of(
                        "error", "Failed to prepare JSON input: " + exception.getMessage()));
            }

            lineBuffer.clear();
            lastExitCode = null;
            activeType = config.type();
            activePermitType = null;
            activeRunStartedAtMillis = System.currentTimeMillis();

            Path projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            String mvnwPath = projectRoot.resolve("mvnw.cmd").toString();
            List<String> command = buildCommand(config, mvnwPath, resolvedJsonPath);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(projectRoot.toFile());
            processBuilder.redirectErrorStream(true);

            try {
                activeProcess = processBuilder.start();
            } catch (IOException exception) {
                return ResponseEntity.status(500).body(Map.of(
                        "error", "Failed to start process: " + exception.getMessage()));
            }

            Process process = activeProcess;
            Thread reader = new Thread(() -> {
                try (BufferedReader bufferedReader =
                             new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        lineBuffer.add(line);
                        String finalLine = line;
                        for (SseEmitter emitter : activeEmitters) {
                            try {
                                emitter.send(SseEmitter.event().data(finalLine));
                            } catch (IOException exception) {
                                activeEmitters.remove(emitter);
                            }
                        }
                    }
                    lastExitCode = process.waitFor();
                } catch (IOException | InterruptedException exception) {
                    lastExitCode = -1;
                    Thread.currentThread().interrupt();
                } finally {
                    for (SseEmitter emitter : activeEmitters) {
                        try {
                            emitter.send(SseEmitter.event().data("__DONE__"));
                            emitter.complete();
                        } catch (IOException ignored) {
                        }
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
        } catch (IOException exception) {
            emitter.completeWithError(exception);
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
                } catch (IOException ignored) {
                }
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
        String normalizedType = activeType != null ? activeType : "";
        LauncherTypeConfig config = TYPE_CONFIGS.get(normalizedType);
        String moduleLabel = config != null ? config.moduleLabel() : normalizedType;
        int exitCode = lastExitCode != null ? lastExitCode : NO_EXIT_CODE;
        String reportPrefix = config != null ? config.reportPrefix() : null;
        JobReportSnapshot jobSnapshot = resolveJobSnapshot(reportPrefix, activeRunStartedAtMillis);
        String displayState = resolveDisplayState(running, exitCode, jobSnapshot);
        boolean executionActive = jobSnapshot != null
                ? !jobSnapshot.terminal()
                : running;

        return new LauncherStatusResponse(
                running,
                normalizedType,
                moduleLabel,
                activePermitType,
                exitCode,
                reportPrefix,
                displayState,
                executionActive,
                jobSnapshot != null ? jobSnapshot.jobId() : null,
                jobSnapshot != null ? jobSnapshot.jobStatus() : null,
                jobSnapshot != null ? jobSnapshot.messageReference() : null,
                jobSnapshot != null ? jobSnapshot.pmtNumber() : null,
                jobSnapshot != null ? jobSnapshot.reportStatus() : null);
    }

    private JobReportSnapshot resolveJobSnapshot(String reportPrefix, long runStartedAtMillis) {
        if (reportPrefix == null || reportPrefix.isBlank()) {
            return null;
        }

        try {
            TestReportController.TestReportResponse report =
                    testReportController.buildLatestReport(reportPrefix, null, null);
            return report.batchCases().stream()
                    .filter(batchCase -> batchCase.updatedAtMillis() >= runStartedAtMillis)
                    .max(Comparator.comparingLong(TestReportController.BatchCaseResult::updatedAtMillis))
                    .map(batchCase -> new JobReportSnapshot(
                            batchCase.jobId(),
                            batchCase.jobStatus(),
                            batchCase.declarationNumber(),
                            batchCase.pmtNumber(),
                            batchCase.status(),
                            isTerminalSnapshot(batchCase.jobStatus(), batchCase.pmtNumber())))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveDisplayState(boolean running, int exitCode, JobReportSnapshot jobSnapshot) {
        String jobState = jobSnapshot != null ? mapJobState(jobSnapshot.jobStatus(), jobSnapshot.pmtNumber()) : null;
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

    private String mapJobState(String jobStatus, String pmtNumber) {
        String normalizedJobStatus = normalizeJobStatus(jobStatus);
        if (normalizedJobStatus == null) {
            return null;
        }
        return switch (normalizedJobStatus) {
            case "SUB" -> "PENDING";
            case "SNT" -> "IN_PROGRESS";
            case "PMT" -> hasPmtNumber(pmtNumber) ? "SUCCESS" : "IN_PROGRESS";
            case "DRF", "REG", "FLD", "REJ" -> "FAILED";
            default -> null;
        };
    }

    private boolean hasPmtNumber(String pmtNumber) {
        return pmtNumber != null
                && !pmtNumber.isBlank()
                && !"N/A".equalsIgnoreCase(pmtNumber.trim());
    }

    private boolean isTerminalSnapshot(String jobStatus, String pmtNumber) {
        String jobState = mapJobState(jobStatus, pmtNumber);
        return isTerminalDisplayState(jobState);
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

    private List<Map<String, String>> listJsonFiles(Path folder) throws IOException {
        if (folder == null || !Files.isDirectory(folder)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .map(path -> Map.of(
                            "name", folder.relativize(path).toString().replace('\\', '/'),
                            "resourcePath", folder.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
    }

    private List<String> buildCommand(
            LauncherTypeConfig config,
            String mvnwPath,
            String resolvedJsonPath) {
        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add(mvnwPath);
        command.add("-Dtest=" + config.testClass());
        command.add("-D" + config.testDataProperty() + "=" + resolvedJsonPath);
        command.addAll(config.extraArgs());
        command.add("test");
        return List.copyOf(command);
    }

    private String resolveJsonInput(
            LauncherTypeConfig config,
            String jsonResource,
            MultipartFile jsonFile) throws IOException {
        if (jsonFile != null && !jsonFile.isEmpty()) {
            return storeUploadedJson(config, jsonFile).toString();
        }
        if (jsonResource != null && !jsonResource.isBlank()) {
            return resolveJsonResourcePath(config, jsonResource).toString();
        }
        throw new IllegalArgumentException("Select a JSON from the folder or upload a JSON file manually.");
    }

    private Path resolveJsonResourcePath(LauncherTypeConfig config, String jsonResource) {
        String normalizedResource = jsonResource.trim().replace('\\', '/');
        if (normalizedResource.startsWith(config.resourceFolder() + "/")) {
            normalizedResource = normalizedResource.substring(config.resourceFolder().length() + 1);
        }
        if (normalizedResource.isBlank()) {
            throw new IllegalArgumentException("Selected JSON resource is empty.");
        }
        if (!normalizedResource.toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("Selected repository file must be a .json file.");
        }

        Path folder = resolveResourceFolderPath(config);
        if (folder == null) {
            throw new IllegalArgumentException("Configured JSON folder was not found: " + configuredResourceFolder(config));
        }
        Path candidate = folder.resolve(normalizedResource).normalize();
        if (!candidate.startsWith(folder)) {
            throw new IllegalArgumentException("Selected JSON resource is outside the allowed folder.");
        }
        if (!Files.isRegularFile(candidate)) {
            throw new IllegalArgumentException("Selected JSON resource was not found: " + normalizedResource);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private Path storeUploadedJson(
            LauncherTypeConfig config,
            MultipartFile jsonFile) throws IOException {
        String originalFileName = jsonFile.getOriginalFilename() != null
                ? jsonFile.getOriginalFilename().trim()
                : "";
        String fileName = originalFileName.isBlank() ? config.type() + "-input.json" : originalFileName;
        if (!fileName.toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("Uploaded file must be a .json file.");
        }

        byte[] fileBytes = jsonFile.getBytes();
        if (fileBytes.length == 0) {
            throw new IllegalArgumentException("Uploaded JSON file is empty.");
        }
        OBJECT_MAPPER.readTree(fileBytes);

        String sanitizedFileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path uploadDirectory = Paths.get("target", "launcher-uploads", config.type());
        Files.createDirectories(uploadDirectory);

        Path outputPath = uploadDirectory.resolve(System.currentTimeMillis() + "-" + sanitizedFileName)
                .toAbsolutePath()
                .normalize();
        Files.write(outputPath, fileBytes);
        return outputPath;
    }

    private LauncherTypeConfig resolveTypeConfig(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return TYPE_CONFIGS.get(type.trim().toLowerCase());
    }

    private Path resolveResourceFolderPath(LauncherTypeConfig config) {
        return resourceFolderCandidates(config).stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(null);
    }

    private List<Path> resourceFolderCandidates(LauncherTypeConfig config) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        candidates.add(Paths.get("src", "test", "resources", config.resourceFolder()).toAbsolutePath().normalize());
        candidates.add(Paths.get("target", "test-classes", config.resourceFolder()).toAbsolutePath().normalize());

        locateProjectRoot().ifPresent(projectRoot -> {
            candidates.add(projectRoot.resolve(Paths.get("src", "test", "resources", config.resourceFolder()))
                    .toAbsolutePath()
                    .normalize());
            candidates.add(projectRoot.resolve(Paths.get("target", "test-classes", config.resourceFolder()))
                    .toAbsolutePath()
                    .normalize());
        });

        return List.copyOf(candidates);
    }

    private Optional<Path> locateProjectRoot() {
        for (Path start : projectRootSearchStarts()) {
            Path current = Files.isDirectory(start) ? start : start.getParent();
            while (current != null) {
                if (Files.isRegularFile(current.resolve("pom.xml"))) {
                    return Optional.of(current.toAbsolutePath().normalize());
                }
                current = current.getParent();
            }
        }
        return Optional.empty();
    }

    private List<Path> projectRootSearchStarts() {
        LinkedHashSet<Path> starts = new LinkedHashSet<>();
        starts.add(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize());
        addCodeSourcePath(starts, TestLauncherController.class);
        addCodeSourcePath(starts, PlaywrightFrameworkApplication.class);
        return List.copyOf(starts);
    }

    private void addCodeSourcePath(LinkedHashSet<Path> starts, Class<?> sourceClass) {
        try {
            if (sourceClass.getProtectionDomain() == null
                    || sourceClass.getProtectionDomain().getCodeSource() == null
                    || sourceClass.getProtectionDomain().getCodeSource().getLocation() == null) {
                return;
            }
            Path location = Paths.get(sourceClass.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
            starts.add(location);
        } catch (URISyntaxException | IllegalArgumentException ignored) {
        }
    }

    private String configuredResourceFolder(LauncherTypeConfig config) {
        return Paths.get("src", "test", "resources", config.resourceFolder()).toString().replace('\\', '/');
    }

    private boolean isTerminalDisplayState(String value) {
        return "SUCCESS".equals(value) || "FAILED".equals(value);
    }

    private record LauncherTypeConfig(
            String type,
            String moduleLabel,
            String reportPrefix,
            String resourceFolder,
            String testClass,
            String testDataProperty,
            List<String> extraArgs,
            boolean requiresPermitType,
            boolean executable) {
    }

    public record LauncherStatusResponse(
            boolean running,
            String type,
            String moduleLabel,
            String permitType,
            int exitCode,
            String reportPrefix,
            String displayState,
            boolean executionActive,
            String jobId,
            String jobStatus,
            String messageRef,
            String pmtNumber,
            String reportStatus) {
    }

    private record JobReportSnapshot(
            String jobId,
            String jobStatus,
            String messageReference,
            String pmtNumber,
            String reportStatus,
            boolean terminal) {
    }
}
