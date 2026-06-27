package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class ArtifactPaths {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static final Path TARGET_DIR = Paths.get("target").toAbsolutePath().normalize();
    static final Path REPORTS_DIR = Paths.get("reports").toAbsolutePath().normalize();
    static final Path SCREENSHOTS_DIR = Paths.get("screenshots").toAbsolutePath().normalize();
    static final Path JMETER_DIR = Paths.get("jmeter").toAbsolutePath().normalize();
    static final Path HTML_REPORT_DIR = Paths.get("html-report").toAbsolutePath().normalize();
    static final Path LOAD_DASHBOARD_SCREENSHOTS_DIR = SCREENSHOTS_DIR.resolve("load-dashboard").toAbsolutePath().normalize();
    static final Path LOAD_DASHBOARD_REPORTS_DIR = REPORTS_DIR.resolve("load-dashboard").toAbsolutePath().normalize();
    static final Path EXECUTION_LOG = REPORTS_DIR.resolve("execution.log");
    static final Path REPORT_DATA = REPORTS_DIR.resolve("report-data.js");

    private ArtifactPaths() {
    }

    static void ensureBaseDirectories() throws IOException {
        Files.createDirectories(REPORTS_DIR);
        Files.createDirectories(SCREENSHOTS_DIR);
        Files.createDirectories(JMETER_DIR);
        Files.createDirectories(HTML_REPORT_DIR);
        Files.createDirectories(LOAD_DASHBOARD_SCREENSHOTS_DIR);
        Files.createDirectories(LOAD_DASHBOARD_REPORTS_DIR);
    }

    static void writeExecutionLog(List<String> lines) throws IOException {
        ensureBaseDirectories();
        Files.writeString(EXECUTION_LOG, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
    }

    static void syncDeclarationArtifacts(String artifactPrefix) throws IOException {
        ensureBaseDirectories();
        if (artifactPrefix == null || artifactPrefix.isBlank() || !Files.isDirectory(TARGET_DIR)) {
            return;
        }

        refreshStaticReportData(artifactPrefix);

        try (Stream<Path> files = Files.list(TARGET_DIR)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(artifactPrefix))
                    .forEach(path -> copyArtifact(path, destinationFor(path)));
        }

        Path targetReportData = TARGET_DIR.resolve("report-data.js");
        if (Files.isRegularFile(targetReportData)) {
            Files.copy(targetReportData, REPORT_DATA, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        aliasScreenshot(artifactPrefix, artifactPrefix + "-login.png", artifactPrefix + "-login.png");
        aliasScreenshot(artifactPrefix, artifactPrefix + "-progress-08-summary-complete.png", artifactPrefix + "-form-population.png");
        aliasScreenshot(artifactPrefix, artifactPrefix + "-status-1.png", artifactPrefix + "-submission-success.png");
        if (!Files.isRegularFile(SCREENSHOTS_DIR.resolve(artifactPrefix + "-submission-success.png"))) {
            aliasScreenshot(artifactPrefix, artifactPrefix + "-progress-10-after-submit.png", artifactPrefix + "-submission-success.png");
        }
    }

    static void refreshStaticReportData(String artifactPrefix) {
        if (artifactPrefix == null || artifactPrefix.isBlank()) {
            return;
        }
        try {
            TestReportController controller = new TestReportController();
            TestReportController.TestReportResponse body = controller.buildLatestReport(artifactPrefix, null, null);
            String payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);
            String script = "window.__REPORT_DATA__ = " + payload + ";" + System.lineSeparator();
            Files.writeString(TARGET_DIR.resolve("report-data.js"), script, StandardCharsets.UTF_8);
            ensureBaseDirectories();
            Files.writeString(REPORT_DATA, script, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    static void createSelectionScreenshot(String artifactPrefix, String moduleLabel, String selectedJson) throws IOException {
        ensureBaseDirectories();
        if (artifactPrefix == null || artifactPrefix.isBlank()) {
            return;
        }

        BufferedImage image = new BufferedImage(1400, 820, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setPaint(new Color(240, 247, 255));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

            graphics.setPaint(new Color(11, 31, 58));
            graphics.fillRoundRect(80, 80, 1240, 660, 32, 32);

            graphics.setPaint(new Color(0, 87, 255));
            graphics.fillRoundRect(120, 120, 1160, 120, 24, 24);

            graphics.setPaint(Color.WHITE);
            graphics.setFont(new Font("Segoe UI", Font.BOLD, 42));
            graphics.drawString("Permit JSON Selection", 160, 195);

            graphics.setFont(new Font("Segoe UI", Font.PLAIN, 28));
            drawWrappedText(graphics, "Module: " + normalizeText(moduleLabel, "Declaration Workflow"), 160, 320, 1040, 40);
            drawWrappedText(graphics, "Selected JSON: " + normalizeText(selectedJson, "Repository / Manual JSON"), 160, 410, 1040, 40);
            drawWrappedText(graphics, "Captured At: " + Instant.now(), 160, 500, 1040, 40);

            graphics.setFont(new Font("Segoe UI", Font.PLAIN, 24));
            graphics.setPaint(new Color(215, 228, 255));
            drawWrappedText(graphics,
                    "This image is generated by the launcher at the moment the JSON selection is submitted to the automation workflow.",
                    160,
                    610,
                    1040,
                    34);
        } finally {
            graphics.dispose();
        }

        ImageIO.write(image, "png", SCREENSHOTS_DIR.resolve(artifactPrefix + "-json-selection.png").toFile());
    }

    static void deleteExistingDeclarationArtifacts(String artifactPrefix) {
        deleteMatchingFiles(TARGET_DIR, artifactPrefix);
        deleteMatchingFiles(SCREENSHOTS_DIR, artifactPrefix);
        deleteMatchingFiles(REPORTS_DIR, artifactPrefix);
    }

    static void recreateDirectory(Path directory) throws IOException {
        deleteDirectory(directory);
        Files.createDirectories(directory);
    }

    static void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder())
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

    private static void deleteMatchingFiles(Path directory, String artifactPrefix) {
        if (artifactPrefix == null || artifactPrefix.isBlank() || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(artifactPrefix))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static void aliasScreenshot(String artifactPrefix, String sourceFileName, String aliasFileName) throws IOException {
        Path source = SCREENSHOTS_DIR.resolve(sourceFileName);
        if (Files.isRegularFile(source)) {
            Files.copy(source, SCREENSHOTS_DIR.resolve(aliasFileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        Path legacySource = TARGET_DIR.resolve(sourceFileName);
        if (Files.isRegularFile(legacySource)) {
            Files.copy(legacySource, SCREENSHOTS_DIR.resolve(aliasFileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path destinationFor(Path source) {
        String fileName = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".png")) {
            return SCREENSHOTS_DIR.resolve(source.getFileName().toString());
        }
        if (fileName.endsWith(".json") || fileName.endsWith(".js") || fileName.endsWith(".txt") || fileName.endsWith(".log")) {
            return REPORTS_DIR.resolve(source.getFileName().toString());
        }
        return REPORTS_DIR.resolve(source.getFileName().toString());
    }

    private static void copyArtifact(Path source, Path destination) {
        try {
            Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private static String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static void drawWrappedText(
            Graphics2D graphics,
            String text,
            int x,
            int startY,
            int maxWidth,
            int lineHeight) {
        FontMetrics metrics = graphics.getFontMetrics();
        StringBuilder currentLine = new StringBuilder();
        int y = startY;
        for (String word : text.split("\\s+")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
                continue;
            }
            graphics.drawString(currentLine.toString(), x, y);
            y += lineHeight;
            currentLine.setLength(0);
            currentLine.append(word);
        }
        if (!currentLine.isEmpty()) {
            graphics.drawString(currentLine.toString(), x, y);
        }
    }
}
