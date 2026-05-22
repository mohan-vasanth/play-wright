package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/reports")
public class TestReportController {

    private static final Path TARGET_DIR = Paths.get("target");
    private static final Path SUREFIRE_REPORTS_DIR = TARGET_DIR.resolve("surefire-reports");
    private static final Pattern BATCH_VALIDATION_JSON = Pattern.compile("^ipt-batch-submit-validation-(\\d+)\\.json$");
    private static final Pattern BATCH_FAILURE_JSON = Pattern.compile("^ipt-batch-submit-failure-(\\d+)\\.json$");
    private static final Pattern BATCH_SUCCESS_SCREENSHOT = Pattern.compile("^ipt-batch-submit-(\\d+)\\.png$");
    private static final Pattern BATCH_FAILURE_SCREENSHOT = Pattern.compile("^ipt-batch-submit-failure-(\\d+)\\.png$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping(value = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TestReportResponse> latest() {
        try {
            SurefireReportSummary surefireReport = readLatestSurefireReport();
            BatchReportSummary batchReport = readBatchReport(
                    surefireReport != null ? surefireReport.testDataResourcePath() : null);

            if (surefireReport == null && batchReport.batchCases().isEmpty()) {
                return ResponseEntity.ok(TestReportResponse.noReport("No test report artifacts found in target."));
            }

            String status = resolveOverallStatus(surefireReport, batchReport);
            String primaryIssue = resolvePrimaryIssue(status, surefireReport, batchReport);
            long updatedAtMillis = Math.max(
                    surefireReport != null ? surefireReport.updatedAtMillis() : Long.MIN_VALUE,
                    batchReport.updatedAtMillis());

            return ResponseEntity.ok(new TestReportResponse(
                    status,
                    surefireReport != null ? surefireReport.suiteName() : null,
                    surefireReport != null ? surefireReport.sourceFile() : null,
                    surefireReport != null ? surefireReport.tests() : 0,
                    surefireReport != null ? surefireReport.failures() : 0,
                    surefireReport != null ? surefireReport.errors() : 0,
                    surefireReport != null ? surefireReport.skipped() : 0,
                    surefireReport != null ? surefireReport.durationSeconds() : null,
                    updatedAtMillis > Long.MIN_VALUE ? Instant.ofEpochMilli(updatedAtMillis).toString() : null,
                    primaryIssue,
                    surefireReport != null ? surefireReport.testCases() : List.of(),
                    batchReport.summary(),
                    batchReport.batchCases()));
        } catch (Exception exception) {
            return ResponseEntity.ok(TestReportResponse.noReport("Unable to read latest report: " + exception.getMessage()));
        }
    }

    @GetMapping("/artifacts/{fileName:.+}")
    public ResponseEntity<Resource> artifact(@PathVariable String fileName) {
        try {
            if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
                return ResponseEntity.notFound().build();
            }

            Path resolved = TARGET_DIR.resolve(fileName).normalize();
            if (!resolved.startsWith(TARGET_DIR) || !Files.isRegularFile(resolved)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(resolved.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception exception) {
            return ResponseEntity.notFound().build();
        }
    }

    private SurefireReportSummary readLatestSurefireReport() throws Exception {
        if (!Files.isDirectory(SUREFIRE_REPORTS_DIR)) {
            return null;
        }

        Path latestReport;
        try (Stream<Path> reportPaths = Files.list(SUREFIRE_REPORTS_DIR)) {
            latestReport = reportPaths
                    .filter(path -> path.getFileName().toString().startsWith("TEST-"))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .max(Comparator.comparingLong(this::lastModifiedMillis))
                    .orElse(null);
        }

        if (latestReport == null) {
            return null;
        }

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(latestReport.toFile());
        document.getDocumentElement().normalize();

        Element suite = document.getDocumentElement();
        int tests = parseInt(suite.getAttribute("tests"));
        int failures = parseInt(suite.getAttribute("failures"));
        int errors = parseInt(suite.getAttribute("errors"));
        int skipped = parseInt(suite.getAttribute("skipped"));
        String duration = blankToNull(suite.getAttribute("time"));
        String suiteName = blankToNull(suite.getAttribute("name"));
        String testDataResourcePath = readSuiteProperty(suite, "tradenix.ipt.test.data");

        List<TestCaseResult> testCases = new ArrayList<>();
        NodeList testCaseNodes = suite.getElementsByTagName("testcase");
        for (int index = 0; index < testCaseNodes.getLength(); index++) {
            Node current = testCaseNodes.item(index);
            if (current instanceof Element testCase) {
                testCases.add(parseTestCase(testCase));
            }
        }

        return new SurefireReportSummary(
                suiteName,
                latestReport.getFileName().toString(),
                tests,
                failures,
                errors,
                skipped,
                duration,
                testDataResourcePath,
                lastModifiedMillis(latestReport),
                testCases);
    }

    private String readSuiteProperty(Element suite, String propertyName) {
        NodeList propertyNodes = suite.getElementsByTagName("property");
        for (int index = 0; index < propertyNodes.getLength(); index++) {
            Node current = propertyNodes.item(index);
            if (current instanceof Element propertyElement
                    && propertyName.equals(propertyElement.getAttribute("name"))) {
                return blankToNull(propertyElement.getAttribute("value"));
            }
        }
        return null;
    }

    private BatchReportSummary readBatchReport(String testDataResourcePath) throws Exception {
        if (!Files.isDirectory(TARGET_DIR)) {
            return BatchReportSummary.empty();
        }

        Map<Integer, BatchMetaInfo> batchMetaByIndex = readBatchMetaByIndex(testDataResourcePath);
        Map<Integer, BatchCaseAccumulator> casesByIndex = new TreeMap<>();
        try (Stream<Path> files = Files.list(TARGET_DIR)) {
            files.filter(Files::isRegularFile).forEach(path -> accumulateBatchArtifact(casesByIndex, path));
        }

        List<BatchCaseResult> batchCases = new ArrayList<>();
        int successCount = 0;
        int issueCount = 0;
        int failureCount = 0;
        int draftCount = 0;
        long updatedAtMillis = Long.MIN_VALUE;

        for (Map.Entry<Integer, BatchCaseAccumulator> entry : casesByIndex.entrySet()) {
            BatchCaseResult batchCase = toBatchCase(
                    entry.getKey(),
                    entry.getValue(),
                    batchMetaByIndex.get(entry.getKey()));
            batchCases.add(batchCase);
            updatedAtMillis = Math.max(updatedAtMillis, batchCase.updatedAtMillis());
            String displayStatus = firstNonBlank(batchCase.jobStatus(), batchCase.status(), "NO_REPORT");
            if ("PERMIT_ISSUED".equals(displayStatus) || "SUCCESS".equals(displayStatus)) {
                successCount++;
            } else if ("DRAFT".equals(displayStatus)) {
                draftCount++;
            } else if ("FAILED".equals(displayStatus) || "FAILURE".equals(displayStatus)) {
                failureCount++;
            } else if ("ISSUE".equals(displayStatus)) {
                issueCount++;
            }
        }

        return new BatchReportSummary(
                new BatchSummary(
                        batchCases.size(),
                        successCount,
                        issueCount,
                        failureCount,
                        draftCount),
                batchCases,
                updatedAtMillis);
    }

    private void accumulateBatchArtifact(Map<Integer, BatchCaseAccumulator> casesByIndex, Path path) {
        String fileName = path.getFileName().toString();
        registerBatchPath(casesByIndex, path, fileName, BATCH_VALIDATION_JSON, BatchArtifactType.VALIDATION_JSON);
        registerBatchPath(casesByIndex, path, fileName, BATCH_FAILURE_JSON, BatchArtifactType.FAILURE_JSON);
        registerBatchPath(casesByIndex, path, fileName, BATCH_SUCCESS_SCREENSHOT, BatchArtifactType.SUCCESS_SCREENSHOT);
        registerBatchPath(casesByIndex, path, fileName, BATCH_FAILURE_SCREENSHOT, BatchArtifactType.FAILURE_SCREENSHOT);
    }

    private void registerBatchPath(
            Map<Integer, BatchCaseAccumulator> casesByIndex,
            Path path,
            String fileName,
            Pattern pattern,
            BatchArtifactType artifactType) {
        Matcher matcher = pattern.matcher(fileName);
        if (!matcher.matches()) {
            return;
        }

        int index = parseInt(matcher.group(1));
        BatchCaseAccumulator accumulator = casesByIndex.computeIfAbsent(index, ignored -> new BatchCaseAccumulator());
        switch (artifactType) {
            case VALIDATION_JSON -> accumulator.validationJson = path;
            case FAILURE_JSON -> accumulator.failureJson = path;
            case SUCCESS_SCREENSHOT -> accumulator.successScreenshot = path;
            case FAILURE_SCREENSHOT -> accumulator.failureScreenshot = path;
        }
    }

    private Map<Integer, BatchMetaInfo> readBatchMetaByIndex(String testDataResourcePath) {
        if (testDataResourcePath == null || testDataResourcePath.isBlank()) {
            return Map.of();
        }

        try {
            Path directPath = Paths.get(testDataResourcePath);
            Path resourcePath = Paths.get("src", "test", "resources").resolve(testDataResourcePath).normalize();
            Path sourcePath = Files.isRegularFile(directPath) ? directPath : resourcePath;
            if (!Files.isRegularFile(sourcePath)) {
                return Map.of();
            }

            JsonNode root = objectMapper.readTree(Files.readString(sourcePath));
            Map<Integer, BatchMetaInfo> batchMeta = new HashMap<>();
            if (root.isArray()) {
                for (int index = 0; index < root.size(); index++) {
                    JsonNode declaration = root.path(index);
                    String code = blankToNull(declaration.path("header").path("declarationType").asText(null));
                    batchMeta.put(index + 1, new BatchMetaInfo(code, mapDeclarationTypeDisplay(code)));
                }
            } else if (root.isObject()) {
                String code = blankToNull(root.path("header").path("declarationType").asText(null));
                batchMeta.put(1, new BatchMetaInfo(code, mapDeclarationTypeDisplay(code)));
            }
            return batchMeta;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String mapDeclarationTypeDisplay(String code) {
        if (code == null) {
            return null;
        }
        return switch (code.trim()) {
            case "10" -> "10 - GST";
            case "11" -> "11 - DUT";
            case "12" -> "12 - DNG";
            case "90" -> "90 - BKT";
            default -> code;
        };
    }

    private BatchCaseResult toBatchCase(int index, BatchCaseAccumulator accumulator, BatchMetaInfo batchMetaInfo) {
        Path diagnosticsPath = accumulator.failureJson != null ? accumulator.failureJson : accumulator.validationJson;
        Path screenshotPath = accumulator.failureScreenshot != null ? accumulator.failureScreenshot : accumulator.successScreenshot;
        BatchDiagnostics diagnostics = readDiagnostics(diagnosticsPath);

        String status;
        String message;
        String jobStatus = diagnostics.jobStatus();
        if (accumulator.failureJson != null) {
            status = "FAILURE";
            message = firstNonBlank(
                    diagnostics.toastText(),
                    diagnostics.invalidCount() > 0 ? "Validation failed with " + diagnostics.invalidCount() + " invalid fields." : null,
                    "Declaration failed during submission.");
        } else if (diagnosticsPath != null) {
            if (diagnostics.toastText() == null && diagnostics.invalidCount() == 0) {
                status = "SUCCESS";
                message = "Declaration submitted successfully.";
            } else {
                status = "ISSUE";
                message = firstNonBlank(
                        diagnostics.toastText(),
                        diagnostics.invalidCount() > 0 ? "Validation found " + diagnostics.invalidCount() + " invalid fields." : null,
                        "Validation issue detected.");
            }
        } else {
            status = "NO_REPORT";
            message = "No batch declaration artifacts found.";
        }

        if (jobStatus == null || jobStatus.isBlank()) {
            jobStatus = switch (status) {
                case "SUCCESS" -> "PERMIT_ISSUED";
                case "FAILURE", "ISSUE" -> "FAILED";
                default -> "NO_REPORT";
            };
        }

        long updatedAtMillis = Math.max(lastModifiedMillis(diagnosticsPath), lastModifiedMillis(screenshotPath));

        return new BatchCaseResult(
                index,
                status,
                message,
                batchMetaInfo != null ? batchMetaInfo.declarationTypeCode() : null,
                batchMetaInfo != null ? batchMetaInfo.declarationTypeDisplay() : null,
                jobStatus,
                diagnostics.jobId(),
                diagnostics.toastText(),
                diagnostics.invalidCount(),
                diagnostics.rawJson(),
                fileName(diagnosticsPath),
                fileName(screenshotPath),
                updatedAtMillis > Long.MIN_VALUE ? Instant.ofEpochMilli(updatedAtMillis).toString() : null,
                updatedAtMillis);
    }

    private BatchDiagnostics readDiagnostics(Path diagnosticsPath) {
        if (diagnosticsPath == null || !Files.isRegularFile(diagnosticsPath)) {
            return BatchDiagnostics.empty();
        }

        try {
            String rawJson = Files.readString(diagnosticsPath);
            JsonNode root = objectMapper.readTree(rawJson);
            String jobId = blankToNull(root.path("jobId").asText(null));
            String jobStatus = blankToNull(root.path("jobStatus").asText(null));
            String toastText = blankToNull(root.path("toastText").asText(null));
            JsonNode invalidElementsNode = root.path("invalidElements");
            int invalidCount = invalidElementsNode.isArray() ? invalidElementsNode.size() : 0;
            return new BatchDiagnostics(jobId, jobStatus, toastText, invalidCount, rawJson);
        } catch (Exception exception) {
            return new BatchDiagnostics(null, null, null, 0, "Unable to parse diagnostics: " + exception.getMessage());
        }
    }

    private TestCaseResult parseTestCase(Element testCase) {
        String name = blankToNull(testCase.getAttribute("name"));
        String className = blankToNull(testCase.getAttribute("classname"));
        String duration = blankToNull(testCase.getAttribute("time"));
        String status = "SUCCESS";
        String message = null;
        String detail = null;

        NodeList children = testCase.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node current = children.item(index);
            if (!(current instanceof Element child)) {
                continue;
            }
            String tagName = child.getTagName();
            if ("failure".equalsIgnoreCase(tagName) || "error".equalsIgnoreCase(tagName)) {
                status = "FAILURE";
                message = blankToNull(child.getAttribute("message"));
                detail = blankToNull(child.getTextContent());
                break;
            }
            if ("skipped".equalsIgnoreCase(tagName)) {
                status = "SKIPPED";
            }
        }

        return new TestCaseResult(name, className, status, duration, message, detail);
    }

    private String resolveOverallStatus(SurefireReportSummary surefireReport, BatchReportSummary batchReport) {
        if (batchReport.summary().failures() > 0) {
            return "FAILURE";
        }
        if (surefireReport != null && (surefireReport.failures() > 0 || surefireReport.errors() > 0)) {
            return "FAILURE";
        }
        if (batchReport.summary().issues() > 0) {
            return "ISSUE";
        }
        if (surefireReport == null && batchReport.summary().total() == 0) {
            return "NO_REPORT";
        }
        return "SUCCESS";
    }

    private String resolvePrimaryIssue(String status, SurefireReportSummary surefireReport, BatchReportSummary batchReport) {
        if ("FAILURE".equals(status)) {
            return batchReport.batchCases().stream()
                    .filter(batchCase -> "FAILURE".equals(batchCase.status()))
                    .map(BatchCaseResult::message)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElseGet(() -> firstTestCaseMessage(surefireReport, "Declaration failed."));
        }
        if ("ISSUE".equals(status)) {
            return batchReport.batchCases().stream()
                    .filter(batchCase -> "ISSUE".equals(batchCase.status()))
                    .map(BatchCaseResult::message)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse("Validation issue detected.");
        }
        if (batchReport.summary().total() > 0) {
            return batchReport.summary().successes() + " of " + batchReport.summary().total() + " declarations passed.";
        }
        return firstTestCaseMessage(surefireReport, "Completed successfully.");
    }

    private String firstTestCaseMessage(SurefireReportSummary surefireReport, String fallback) {
        if (surefireReport == null) {
            return fallback;
        }
        return surefireReport.testCases().stream()
                .map(TestCaseResult::message)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private long lastModifiedMillis(Path path) {
        if (path == null) {
            return Long.MIN_VALUE;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception exception) {
            return Long.MIN_VALUE;
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception exception) {
            return 0;
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String fileName(Path path) {
        return path == null ? null : path.getFileName().toString();
    }

    private enum BatchArtifactType {
        VALIDATION_JSON,
        FAILURE_JSON,
        SUCCESS_SCREENSHOT,
        FAILURE_SCREENSHOT
    }

    private static final class BatchCaseAccumulator {
        private Path validationJson;
        private Path failureJson;
        private Path successScreenshot;
        private Path failureScreenshot;
    }

    private record SurefireReportSummary(
            String suiteName,
            String sourceFile,
            int tests,
            int failures,
            int errors,
            int skipped,
            String durationSeconds,
            String testDataResourcePath,
            long updatedAtMillis,
            List<TestCaseResult> testCases) {
    }

    private record BatchMetaInfo(
            String declarationTypeCode,
            String declarationTypeDisplay) {
    }

    private record BatchDiagnostics(
            String jobId,
            String jobStatus,
            String toastText,
            int invalidCount,
            String rawJson) {

        private static BatchDiagnostics empty() {
            return new BatchDiagnostics(null, null, null, 0, null);
        }
    }

    private record BatchReportSummary(
            BatchSummary summary,
            List<BatchCaseResult> batchCases,
            long updatedAtMillis) {

        private static BatchReportSummary empty() {
            return new BatchReportSummary(new BatchSummary(0, 0, 0, 0, 0), List.of(), Long.MIN_VALUE);
        }
    }

    public record TestCaseResult(
            String name,
            String className,
            String status,
            String durationSeconds,
            String message,
            String detail) {
    }

    public record BatchSummary(
            int total,
            int successes,
            int issues,
            int failures,
            int drafts) {
    }

    public record BatchCaseResult(
            int index,
            String status,
            String message,
            String declarationTypeCode,
            String declarationTypeDisplay,
            String jobStatus,
            String jobId,
            String toastText,
            int invalidCount,
            String diagnosticsText,
            String diagnosticsFile,
            String screenshotFile,
            String updatedAt,
            long updatedAtMillis) {
    }

    public record TestReportResponse(
            String status,
            String suiteName,
            String sourceFile,
            int tests,
            int failures,
            int errors,
            int skipped,
            String durationSeconds,
            String updatedAt,
            String primaryIssue,
            List<TestCaseResult> testCases,
            BatchSummary batchSummary,
            List<BatchCaseResult> batchCases) {

        static TestReportResponse noReport(String message) {
            return new TestReportResponse(
                    "NO_REPORT",
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    message,
                    List.of(),
                    new BatchSummary(0, 0, 0, 0, 0),
                    List.of());
        }
    }
}
