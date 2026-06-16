package com.automation.playwright_framework;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/jmeter")
public class JMeterController {

    private final JMeterExecutionService jMeterExecutionService;

    public JMeterController(JMeterExecutionService jMeterExecutionService) {
        this.jMeterExecutionService = jMeterExecutionService;
    }

    @PostMapping("/run")
    public ResponseEntity<JMeterExecutionService.RunResponse> run(
            @RequestParam String targetUrl,
            @RequestParam int threads,
            @RequestParam int rampUpSeconds,
            @RequestParam int loopCount,
            @RequestParam(defaultValue = "0") int durationSeconds,
            @RequestParam(required = false) String declarationType,
            @RequestParam(required = false) String reportPrefix,
            @RequestParam(required = false) String selectedJson,
            @RequestParam(required = false) MultipartFile jmxFile) {
        JMeterExecutionService.RunRequest request = new JMeterExecutionService.RunRequest(
                targetUrl,
                threads,
                rampUpSeconds,
                loopCount,
                durationSeconds,
                declarationType,
                reportPrefix,
                selectedJson);
        return ResponseEntity.ok(jMeterExecutionService.run(request, jmxFile));
    }

    @PostMapping("/stop")
    public ResponseEntity<JMeterExecutionService.StopResponse> stop() {
        return ResponseEntity.ok(jMeterExecutionService.stop());
    }

    @GetMapping("/status")
    public ResponseEntity<JMeterExecutionService.StatusResponse> status() {
        return ResponseEntity.ok(jMeterExecutionService.status());
    }

    @GetMapping("/report")
    public ResponseEntity<JMeterExecutionService.ReportResponse> report(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(jMeterExecutionService.report(refresh));
    }

    @GetMapping("/report/download")
    public ResponseEntity<Resource> downloadReport() {
        Resource resource = new FileSystemResource(jMeterExecutionService.reportArchive());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jmeter-html-report.zip\"")
                .body(resource);
    }
}
