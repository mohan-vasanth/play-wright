package com.automation.playwright_framework;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/load-dashboard")
public class LoadTestingDashboardController {

    private final LoadTestingDashboardService loadTestingDashboardService;

    public LoadTestingDashboardController(LoadTestingDashboardService loadTestingDashboardService) {
        this.loadTestingDashboardService = loadTestingDashboardService;
    }

    @PostMapping("/run")
    public ResponseEntity<LoadTestingDashboardService.StartResponse> run(
            @RequestBody LoadTestingDashboardService.RunRequest request) {
        return ResponseEntity.ok(loadTestingDashboardService.start(request));
    }

    @PostMapping("/stop")
    public ResponseEntity<LoadTestingDashboardService.StopResponse> stop() {
        return ResponseEntity.ok(loadTestingDashboardService.stop());
    }

    @GetMapping("/status")
    public ResponseEntity<LoadTestingDashboardService.StatusResponse> status() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate().sMaxAge(0, TimeUnit.SECONDS))
                .body(loadTestingDashboardService.status());
    }
}
