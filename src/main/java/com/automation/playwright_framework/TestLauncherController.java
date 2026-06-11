package com.automation.playwright_framework;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/launcher")
public class TestLauncherController {

    private volatile Process activeProcess = null;
    private volatile String activeType = null;
    private volatile Integer lastExitCode = null;
    private final List<String> lineBuffer = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> activeEmitters = new CopyOnWriteArrayList<>();

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestParam String type) {
        synchronized (this) {
            if (activeProcess != null && activeProcess.isAlive()) {
                return ResponseEntity.status(409).body(Map.of("error", "already running", "type", activeType));
            }
            if (!type.equals("ipt") && !type.equals("out") && !type.equals("coo")) {
                return ResponseEntity.badRequest().body(Map.of("error", "type must be 'ipt', 'out' or 'coo'"));
            }

            lineBuffer.clear();
            lastExitCode = null;
            activeType = type;

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
    public ResponseEntity<Map<String, Object>> status() {
        boolean running = activeProcess != null && activeProcess.isAlive();
        return ResponseEntity.ok(Map.of(
                "running", running,
                "type", activeType != null ? activeType : "",
                "exitCode", lastExitCode != null ? lastExitCode : -999
        ));
    }
}
