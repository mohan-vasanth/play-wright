package com.automation.playwright_framework;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class AutomaticReportLauncher {

    private static final Path DIAGNOSTICS_PATH = ArtifactPaths.REPORTS_DIR.resolve("report-launcher.log");

    private AutomaticReportLauncher() {
    }

    static void open(String jobId, String messageReference, String reportPrefix) {
        if (!Boolean.parseBoolean(System.getProperty("tradenix.report.auto.open", "false"))) {
            appendDiagnostic("Automatic report open skipped. Set tradenix.report.auto.open=true to enable desktop launch.");
            return;
        }
        URI reportUri = null;
        try {
            Path reportPath = Path.of("src", "main", "resources", "static", "report.html")
                    .toAbsolutePath()
                    .normalize();
            List<String> queryParameters = new ArrayList<>();
            if (jobId != null && !jobId.isBlank()) {
                queryParameters.add("jobId=" + URLEncoder.encode(jobId, StandardCharsets.UTF_8));
            }
            if (messageReference != null && !messageReference.isBlank()) {
                queryParameters.add("messageRef=" + URLEncoder.encode(messageReference, StandardCharsets.UTF_8));
            }
            if (reportPrefix != null && !reportPrefix.isBlank()) {
                queryParameters.add("reportPrefix=" + URLEncoder.encode(reportPrefix, StandardCharsets.UTF_8));
            }
            queryParameters.add("ts=" + System.currentTimeMillis());

            reportUri = queryParameters.isEmpty()
                    ? reportPath.toUri()
                    : URI.create(reportPath.toUri() + "?" + String.join("&", queryParameters));
            if (openWithDesktop(reportUri) || openWithWindowsShell(reportUri)) {
                appendDiagnostic("Opened report: " + reportUri);
                return;
            }

            appendDiagnostic("Unable to open report automatically: no supported launcher for " + reportUri);
        } catch (Exception exception) {
            appendDiagnostic("Failed to open report " + reportUri + ": " + exception.getMessage());
        }
    }

    private static boolean openWithDesktop(URI reportUri) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                return false;
            }
            Desktop.getDesktop().browse(reportUri);
            return true;
        } catch (Exception exception) {
            appendDiagnostic("Desktop browse failed for " + reportUri + ": " + exception.getMessage());
            return false;
        }
    }

    private static boolean openWithWindowsShell(URI reportUri) {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase().contains("win")) {
            return false;
        }

        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", reportUri.toString()).start();
            return true;
        } catch (Exception exception) {
            appendDiagnostic("Windows shell open failed for " + reportUri + ": " + exception.getMessage());
            return false;
        }
    }

    private static void appendDiagnostic(String message) {
        try {
            Files.createDirectories(DIAGNOSTICS_PATH.getParent());
            Files.writeString(
                    DIAGNOSTICS_PATH,
                    message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    Files.exists(DIAGNOSTICS_PATH)
                            ? new java.nio.file.OpenOption[] {
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND }
                            : new java.nio.file.OpenOption[] {
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.WRITE });
        } catch (Exception ignored) {
        }
    }
}
