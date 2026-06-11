package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class StaticReportDataWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path REPORT_DATA_PATH = Path.of("target", "report-data.js");

    private StaticReportDataWriter() {
    }

    static void refresh() {
        try {
            TestReportController controller = new TestReportController();
            ResponseEntity<TestReportController.TestReportResponse> response = controller.latest();
            TestReportController.TestReportResponse body = response.getBody();
            if (body == null) {
                return;
            }

            Files.createDirectories(REPORT_DATA_PATH.getParent());
            String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);
            String script = "window.__REPORT_DATA__ = " + payload + ";" + System.lineSeparator();
            Files.writeString(REPORT_DATA_PATH, script, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
