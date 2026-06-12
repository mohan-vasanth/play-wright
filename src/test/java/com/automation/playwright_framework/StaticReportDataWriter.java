package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class StaticReportDataWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path REPORT_DATA_PATH = Path.of("target", "report-data.js");

    private StaticReportDataWriter() {
    }

    static void clear() {
        try {
            Files.deleteIfExists(REPORT_DATA_PATH);
        } catch (Exception ignored) {
        }
    }

    static void refresh(String reportPrefix) {
        try {
            TestReportController controller = new TestReportController();
            TestReportController.TestReportResponse body = controller.buildLatestReport(reportPrefix, null, null);

            Files.createDirectories(REPORT_DATA_PATH.getParent());
            String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);
            String script = "window.__REPORT_DATA__ = " + payload + ";" + System.lineSeparator();
            Files.writeString(REPORT_DATA_PATH, script, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
