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
    private static final String DEFAULT_ARTIFACT_PREFIX = "ipt-batch-submit";
    private static final String REPORT_ARTIFACT_PREFIX_PROPERTY = "tradenix.report.artifact.prefix";
    private static final List<String> TEST_DATA_PROPERTIES = List.of(
            "tradenix.out.test.data",
            "tradenix.ipt.test.data",
            "tradenix.coo.test.data");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping(value = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TestReportResponse> latest() {
        try {
            SurefireReportSummary surefireReport = readLatestSurefireReport();
            BatchReportSummary batchReport = readBatchReport(
                    surefireReport != null ? surefireReport.testDataResourcePath() : null,
                    surefireReport != null ? surefireReport.artifactPrefix() : null);

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
        String testDataResourcePath = readSuiteProperty(suite, TEST_DATA_PROPERTIES);
        String artifactPrefix = firstNonBlank(
                readSuiteProperty(suite, REPORT_ARTIFACT_PREFIX_PROPERTY),
                inferArtifactPrefix(testDataResourcePath, suiteName),
                DEFAULT_ARTIFACT_PREFIX);

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
                artifactPrefix,
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

    private String readSuiteProperty(Element suite, List<String> propertyNames) {
        for (String propertyName : propertyNames) {
            String value = readSuiteProperty(suite, propertyName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String inferArtifactPrefix(String testDataResourcePath, String suiteName) {
        String normalizedPath = testDataResourcePath == null ? "" : testDataResourcePath.toLowerCase();
        String normalizedSuite = suiteName == null ? "" : suiteName.toLowerCase();
        if (normalizedPath.contains("/out-") || normalizedPath.contains("\\out-") || normalizedPath.contains("out-declaration")
                || normalizedSuite.contains("outdeclaration")) {
            return "out-batch-submit";
        }
        if (normalizedPath.contains("/coo-") || normalizedPath.contains("\\coo-") || normalizedPath.contains("coo-declaration")
                || normalizedSuite.contains("coodeclaration")) {
            return "coo-batch-submit";
        }
        return DEFAULT_ARTIFACT_PREFIX;
    }

    private BatchReportSummary readBatchReport(String testDataResourcePath, String artifactPrefix) throws Exception {
        if (!Files.isDirectory(TARGET_DIR)) {
            return BatchReportSummary.empty();
        }

        Map<Integer, BatchMetaInfo> batchMetaByIndex = readBatchMetaByIndex(testDataResourcePath);
        Map<Integer, BatchCaseAccumulator> casesByIndex = new TreeMap<>();
        String resolvedArtifactPrefix = firstNonBlank(artifactPrefix, DEFAULT_ARTIFACT_PREFIX);
        Pattern validationPattern = artifactPattern(resolvedArtifactPrefix, "-validation-(\\d+)\\.json$");
        Pattern failurePattern = artifactPattern(resolvedArtifactPrefix, "-failure-(\\d+)\\.json$");
        Pattern successScreenshotPattern = artifactPattern(resolvedArtifactPrefix, "-(\\d+)\\.png$");
        Pattern failureScreenshotPattern = artifactPattern(resolvedArtifactPrefix, "-failure-(\\d+)\\.png$");
        Pattern statusScreenshotPattern = artifactPattern(resolvedArtifactPrefix, "-status-(\\d+)\\.png$");
        try (Stream<Path> files = Files.list(TARGET_DIR)) {
            files.filter(Files::isRegularFile).forEach(path -> accumulateBatchArtifact(
                    casesByIndex,
                    path,
                    validationPattern,
                    failurePattern,
                    successScreenshotPattern,
                    failureScreenshotPattern,
                    statusScreenshotPattern));
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
            if ("PMT".equals(displayStatus) || "SUB".equals(displayStatus)
                    || "REG".equals(displayStatus) || "SUCCESS".equals(displayStatus)) {
                successCount++;
            } else if ("DRF".equals(displayStatus)) {
                draftCount++;
            } else if ("FLD".equals(displayStatus) || "REJ".equals(displayStatus) || "FAILURE".equals(displayStatus)) {
                failureCount++;
            } else if ("ISSUE".equals(displayStatus) || "SNT".equals(displayStatus)) {
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

    private Pattern artifactPattern(String artifactPrefix, String suffixPattern) {
        return Pattern.compile("^" + Pattern.quote(artifactPrefix) + suffixPattern);
    }

    private void accumulateBatchArtifact(
            Map<Integer, BatchCaseAccumulator> casesByIndex,
            Path path,
            Pattern validationPattern,
            Pattern failurePattern,
            Pattern successScreenshotPattern,
            Pattern failureScreenshotPattern,
            Pattern statusScreenshotPattern) {
        String fileName = path.getFileName().toString();
        registerBatchPath(casesByIndex, path, fileName, validationPattern, BatchArtifactType.VALIDATION_JSON);
        registerBatchPath(casesByIndex, path, fileName, failurePattern, BatchArtifactType.FAILURE_JSON);
        registerBatchPath(casesByIndex, path, fileName, successScreenshotPattern, BatchArtifactType.SUCCESS_SCREENSHOT);
        registerBatchPath(casesByIndex, path, fileName, failureScreenshotPattern, BatchArtifactType.FAILURE_SCREENSHOT);
        registerBatchPath(casesByIndex, path, fileName, statusScreenshotPattern, BatchArtifactType.STATUS_SCREENSHOT);
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
            case STATUS_SCREENSHOT -> accumulator.statusScreenshot = path;
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
                    String code = resolveDeclarationTypeCode(declaration);
                    batchMeta.put(index + 1, new BatchMetaInfo(code, mapDeclarationTypeDisplay(code)));
                }
            } else if (root.isObject()) {
                String code = resolveDeclarationTypeCode(root);
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
            case "20" -> "20 - OUT";
            case "COO", "COODEC" -> "Certificate of Origin (COO)";
            default -> code;
        };
    }

    private String resolveDeclarationTypeCode(JsonNode declaration) {
        return firstNonBlank(
                blankToNull(declaration.path("header").path("declarationType").asText(null)),
                blankToNull(declaration.path("header").path("applicationType").asText(null)),
                blankToNull(declaration.path("header").path("commonAccessReference").asText(null)),
                blankToNull(declaration.path("type").asText(null)));
    }

    private BatchCaseResult toBatchCase(int index, BatchCaseAccumulator accumulator, BatchMetaInfo batchMetaInfo) {
        Path diagnosticsPath = accumulator.failureJson != null ? accumulator.failureJson : accumulator.validationJson;
        Path screenshotPath = accumulator.failureScreenshot != null ? accumulator.failureScreenshot : accumulator.successScreenshot;
        BatchDiagnostics diagnostics = readDiagnostics(diagnosticsPath);

        String status;
        String message;
        String jobStatus = normalizeJobStatus(diagnostics.jobStatus());
        if (accumulator.failureJson != null) {
            status = "FAILURE";
            message = firstNonBlank(
                    diagnostics.errorMessage(),
                    diagnostics.responseSummary(),
                    diagnostics.toastText(),
                    diagnostics.invalidCount() > 0 ? "Validation failed with " + diagnostics.invalidCount() + " invalid fields." : null,
                    "Declaration failed during submission.");
        } else if (diagnosticsPath != null) {
            if (diagnostics.toastText() == null && diagnostics.invalidCount() == 0) {
                status = "SUCCESS";
                message = firstNonBlank(diagnostics.responseMessage(), "Declaration submitted successfully.");
            } else {
                status = "ISSUE";
                message = firstNonBlank(
                        diagnostics.errorMessage(),
                        diagnostics.responseSummary(),
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
                case "SUCCESS" -> "SUB";
                case "FAILURE", "ISSUE" -> "FLD";
                default -> "NO_REPORT";
            };
        }

        long updatedAtMillis = Math.max(
                Math.max(lastModifiedMillis(diagnosticsPath), lastModifiedMillis(screenshotPath)),
                lastModifiedMillis(accumulator.statusScreenshot));

        return new BatchCaseResult(
                index,
                status,
                message,
                batchMetaInfo != null ? batchMetaInfo.declarationTypeCode() : null,
                batchMetaInfo != null ? batchMetaInfo.declarationTypeDisplay() : null,
                jobStatus,
                diagnostics.jobId(),
                diagnostics.declarationNumber(),
                diagnostics.jobCreatedBy(),
                diagnostics.responseMessage(),
                diagnostics.errorMessage(),
                diagnostics.responseSummary(),
                diagnostics.toastText(),
                diagnostics.invalidCount(),
                diagnostics.rawJson(),
                fileName(diagnosticsPath),
                fileName(screenshotPath),
                fileName(accumulator.statusScreenshot),
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
            String jobStatus = normalizeJobStatus(blankToNull(root.path("jobStatus").asText(null)));
            String declarationNumber = blankToNull(root.path("declarationNumber").asText(null));
            String jobCreatedBy = blankToNull(root.path("jobCreatedBy").asText(null));
            String toastText = blankToNull(root.path("toastText").asText(null));
            String responseMessage = blankToNull(root.path("responseMessage").asText(null));
            String errorMessage = blankToNull(root.path("errorMessage").asText(null));
            String responseSummary = blankToNull(root.path("responseSummary").asText(null));
            JsonNode invalidElementsNode = root.path("invalidElements");
            int invalidCount = invalidElementsNode.isArray() ? invalidElementsNode.size() : 0;
            return new BatchDiagnostics(jobId, jobStatus, declarationNumber, jobCreatedBy, toastText, responseMessage, errorMessage, responseSummary, invalidCount, rawJson);
        } catch (Exception exception) {
            return new BatchDiagnostics(null, null, null, null, null, null, null, null, 0, "Unable to parse diagnostics: " + exception.getMessage());
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

    private String fileName(Path path) {
        return path == null ? null : path.getFileName().toString();
    }

    private enum BatchArtifactType {
        VALIDATION_JSON,
        FAILURE_JSON,
        SUCCESS_SCREENSHOT,
        FAILURE_SCREENSHOT,
        STATUS_SCREENSHOT
    }

    private static final class BatchCaseAccumulator {
        private Path validationJson;
        private Path failureJson;
        private Path successScreenshot;
        private Path failureScreenshot;
        private Path statusScreenshot;
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
            String artifactPrefix,
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
            String declarationNumber,
            String jobCreatedBy,
            String toastText,
            String responseMessage,
            String errorMessage,
            String responseSummary,
            int invalidCount,
            String rawJson) {

        private static BatchDiagnostics empty() {
            return new BatchDiagnostics(null, null, null, null, null, null, null, null, 0, null);
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
            String declarationNumber,
            String jobCreatedBy,
            String responseMessage,
            String errorMessage,
            String responseSummary,
            String toastText,
            int invalidCount,
            String diagnosticsText,
            String diagnosticsFile,
            String screenshotFile,
            String statusScreenshotFile,
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
