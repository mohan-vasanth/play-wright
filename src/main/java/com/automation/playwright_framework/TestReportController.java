package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@RestController
@CrossOrigin(originPatterns = "*")
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
    public ResponseEntity<TestReportResponse> latest(
            @RequestParam(required = false) String reportPrefix,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false, name = "messageRef") String messageReference) {
        try {
            return ResponseEntity.ok(buildLatestReport(reportPrefix, jobId, messageReference));
        } catch (Exception exception) {
            return ResponseEntity.ok(TestReportResponse.noReport("Unable to read latest report: " + exception.getMessage()));
        }
    }

    TestReportResponse buildLatestReport(String reportPrefix, String jobId, String messageReference) throws Exception {
        String normalizedReportPrefix = blankToNull(reportPrefix);
        String normalizedJobId = normalizeFilterValue(jobId);
        String normalizedMessageReference = normalizeFilterValue(messageReference);

        SurefireReportSummary surefireReport = readLatestSurefireReport(normalizedReportPrefix);
        String resolvedArtifactPrefix = firstNonBlank(
                normalizedReportPrefix,
                surefireReport != null ? surefireReport.artifactPrefix() : null,
                DEFAULT_ARTIFACT_PREFIX);
        String testDataResourcePath = firstNonBlank(
                surefireReport != null ? surefireReport.testDataResourcePath() : null,
                inferTestDataResourcePath(resolvedArtifactPrefix));

        BatchReportSummary batchReport = readBatchReport(testDataResourcePath, resolvedArtifactPrefix);
        if (normalizedJobId != null || normalizedMessageReference != null) {
            batchReport = filterBatchReport(batchReport, normalizedJobId, normalizedMessageReference);
            if (batchReport.batchCases().isEmpty()) {
                surefireReport = null;
            }
        }

        if (surefireReport != null
                && normalizedReportPrefix != null
                && batchReport.updatedAtMillis() > surefireReport.updatedAtMillis()) {
            surefireReport = null;
        }

        if (surefireReport == null && batchReport.batchCases().isEmpty()) {
            return TestReportResponse.noReport("No test report artifacts found in target.");
        }

        String status = resolveOverallStatus(surefireReport, batchReport);
        String primaryIssue = resolvePrimaryIssue(status, surefireReport, batchReport);
        long updatedAtMillis = Math.max(
                surefireReport != null ? surefireReport.updatedAtMillis() : Long.MIN_VALUE,
                batchReport.updatedAtMillis());

        return new TestReportResponse(
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
                batchReport.batchCases());
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

    private SurefireReportSummary readLatestSurefireReport(String requestedArtifactPrefix) throws Exception {
        if (!Files.isDirectory(SUREFIRE_REPORTS_DIR)) {
            return null;
        }

        try (Stream<Path> reportPaths = Files.list(SUREFIRE_REPORTS_DIR)) {
            List<Path> candidateReports = reportPaths
                    .filter(path -> path.getFileName().toString().startsWith("TEST-"))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .toList();

            for (Path candidateReport : candidateReports) {
                SurefireReportSummary summary = parseSurefireReport(candidateReport);
                if (requestedArtifactPrefix == null
                        || requestedArtifactPrefix.equalsIgnoreCase(summary.artifactPrefix())) {
                    return summary;
                }
            }
        }

        return null;
    }

    private SurefireReportSummary parseSurefireReport(Path reportPath) throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(reportPath.toFile());
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
                inferArtifactPrefix(testDataResourcePath, suiteName));

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
                reportPath.getFileName().toString(),
                tests,
                failures,
                errors,
                skipped,
                duration,
                testDataResourcePath,
                artifactPrefix,
                lastModifiedMillis(reportPath),
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
        if (normalizedPath.contains("/ipt-") || normalizedPath.contains("\\ipt-") || normalizedPath.contains("ipt-declaration")
                || normalizedSuite.contains("iptdeclaration")) {
            return "ipt-batch-submit";
        }
        if (normalizedPath.contains("/inp-") || normalizedPath.contains("\\inp-") || normalizedPath.contains("inp-declaration")
                || normalizedSuite.contains("inpdeclaration")) {
            return "inp-batch-submit";
        }
        if (normalizedPath.contains("/tnp-") || normalizedPath.contains("\\tnp-") || normalizedPath.contains("tnp-declaration")
                || normalizedSuite.contains("tnpdeclaration")) {
            return "tnp-batch-submit";
        }
        return null;
    }

    private String inferTestDataResourcePath(String artifactPrefix) {
        if (artifactPrefix == null || artifactPrefix.isBlank()) {
            return null;
        }
        return switch (artifactPrefix.trim().toLowerCase()) {
            case "out-batch-submit" -> "OUT/out-declaration-batch-test-case.json";
            case "coo-batch-submit" -> "COO/coo-declaration-batch-test-case.json";
            case "ipt-batch-submit" -> "IPT/ipt-declaration-test-case-1.json";
            case "inp-batch-submit" -> "INP/inp-declaration-test-case-1.json";
            case "tnp-batch-submit" -> "TNP/tnp-declaration-test-case-1.json";
            default -> null;
        };
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
            if ("PMT".equals(displayStatus) || "SUCCESS".equals(displayStatus)) {
                successCount++;
            } else if ("DRF".equals(displayStatus)) {
                draftCount++;
            } else if ("FLD".equals(displayStatus) || "REJ".equals(displayStatus) || "FAILURE".equals(displayStatus)) {
                failureCount++;
            } else if ("ISSUE".equals(displayStatus) || "SNT".equals(displayStatus)
                    || "SUB".equals(displayStatus) || "REG".equals(displayStatus)) {
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

    private BatchReportSummary filterBatchReport(
            BatchReportSummary batchReport,
            String normalizedJobId,
            String normalizedMessageReference) {
        List<BatchCaseResult> matchingCases = batchReport.batchCases().stream()
                .filter(batchCase -> matchesFilter(batchCase, normalizedJobId, normalizedMessageReference))
                .toList();

        int successCount = 0;
        int issueCount = 0;
        int failureCount = 0;
        int draftCount = 0;
        long updatedAtMillis = Long.MIN_VALUE;
        for (BatchCaseResult batchCase : matchingCases) {
            updatedAtMillis = Math.max(updatedAtMillis, batchCase.updatedAtMillis());
            String displayStatus = firstNonBlank(batchCase.jobStatus(), batchCase.status(), "NO_REPORT");
            if ("PMT".equals(displayStatus) || "SUCCESS".equals(displayStatus)) {
                successCount++;
            } else if ("DRF".equals(displayStatus)) {
                draftCount++;
            } else if ("FLD".equals(displayStatus) || "REJ".equals(displayStatus) || "FAILURE".equals(displayStatus)) {
                failureCount++;
            } else if ("ISSUE".equals(displayStatus) || "SNT".equals(displayStatus)
                    || "SUB".equals(displayStatus) || "REG".equals(displayStatus)) {
                issueCount++;
            }
        }

        return new BatchReportSummary(
                new BatchSummary(
                        matchingCases.size(),
                        successCount,
                        issueCount,
                        failureCount,
                        draftCount),
                matchingCases,
                updatedAtMillis);
    }

    private boolean matchesFilter(
            BatchCaseResult batchCase,
            String normalizedJobId,
            String normalizedMessageReference) {
        if (normalizedJobId == null && normalizedMessageReference == null) {
            return true;
        }

        String batchJobId = normalizeFilterValue(batchCase.jobId());
        String batchMessageReference = normalizeFilterValue(batchCase.declarationNumber());
        if (normalizedJobId != null && !normalizedJobId.equals(batchJobId)) {
            return false;
        }
        return normalizedMessageReference == null || normalizedMessageReference.equals(batchMessageReference);
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
                    batchMeta.put(index + 1, new BatchMetaInfo(
                            code,
                            mapDeclarationTypeDisplay(code),
                            blankToNull(declaration.path("header").path("messageReference").asText(null))));
                }
            } else if (root.isObject()) {
                String code = resolveDeclarationTypeCode(root);
                batchMeta.put(1, new BatchMetaInfo(
                        code,
                        mapDeclarationTypeDisplay(code),
                        blankToNull(root.path("header").path("messageReference").asText(null))));
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
            case "40" -> "40 - DRT";
            case "INP" -> "In-Non-Payment (INP)";
            case "TNP" -> "Transhipment (TNP)";
            case "IPT" -> "In-Payment (IPT)";
            case "OUT" -> "Out Payment (OUT)";
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
        // Validation JSON captures the actual declaration state (job fields + validation result).
        // Failure JSON may be plain text (e.g. a Playwright error), so never use it as the primary source.
        BatchDiagnostics valDiag = readDiagnostics(accumulator.validationJson);
        BatchDiagnostics failDiag = accumulator.failureJson != null
                ? readDiagnostics(accumulator.failureJson)
                : BatchDiagnostics.empty();

        Path screenshotPath = accumulator.failureScreenshot != null
                ? accumulator.failureScreenshot
                : accumulator.successScreenshot;

        // Merge: validation JSON is authoritative; failure JSON fills any gaps
        String declarationTypeCode = firstNonBlank(
                valDiag.declarationTypeCode(),
                failDiag.declarationTypeCode(),
                batchMetaInfo != null ? batchMetaInfo.declarationTypeCode() : null);
        String jobStatus    = normalizeJobStatus(firstNonBlank(valDiag.jobStatus(), failDiag.jobStatus()));
        String jobId        = firstNonBlank(valDiag.jobId(), failDiag.jobId());
        String declNum      = firstNonBlank(
                valDiag.declarationNumber(),
                failDiag.declarationNumber(),
                batchMetaInfo != null ? batchMetaInfo.messageReference() : null);
        String pmtNumber    = sanitizePmtNumber(
                firstNonBlank(valDiag.pmtNumber(), failDiag.pmtNumber()),
                declNum,
                jobStatus);
        String createdBy    = firstNonBlank(valDiag.jobCreatedBy(), failDiag.jobCreatedBy());
        String responseMsg  = firstNonBlank(valDiag.responseMessage(), failDiag.responseMessage());
        String errorMsg     = firstNonBlank(valDiag.errorMessage(), failDiag.errorMessage());
        String summary      = firstNonBlank(valDiag.responseSummary(), failDiag.responseSummary());
        String toastText    = firstNonBlank(valDiag.toastText(), failDiag.toastText());
        String startTime    = firstNonBlank(valDiag.startTime(), failDiag.startTime());
        String endTime      = firstNonBlank(valDiag.endTime(), failDiag.endTime());
        Long durationMillis = firstNonNull(valDiag.durationMillis(), failDiag.durationMillis());
        String duration     = firstNonBlank(valDiag.duration(), failDiag.duration(), formatDuration(durationMillis));
        int    invalidCount = valDiag.invalidCount() > 0 ? valDiag.invalidCount() : failDiag.invalidCount();
        String rawJson      = firstNonBlank(valDiag.rawJson(), failDiag.rawJson());

        boolean hasValidationError = toastText != null || invalidCount > 0;

        String status;
        String message;
        if (hasValidationError) {
            // Declaration reached validation but was saved as draft — treat as ISSUE, not FAILURE
            status = "ISSUE";
            message = firstNonBlank(
                    responseMsg, errorMsg, toastText,
                    invalidCount > 0 ? "Validation found " + invalidCount + " invalid fields." : null,
                    "Validation issue detected.");
        } else if (accumulator.failureJson != null && accumulator.validationJson == null) {
            // Only a failure artifact and no validation data → hard submission failure
            status = "FAILURE";
            message = firstNonBlank(errorMsg, responseMsg, "Declaration failed during submission.");
        } else if (accumulator.validationJson != null) {
            // Validation JSON present with no errors → declaration submitted successfully
            status = "SUCCESS";
            message = firstNonBlank(responseMsg, "Declaration submitted successfully.");
        } else {
            status = "NO_REPORT";
            message = "No batch declaration artifacts found.";
        }

        // Derive job status from actual data; validation failures default to DRF (saved as draft)
        if (jobStatus == null || jobStatus.isBlank()) {
            jobStatus = switch (status) {
                case "SUCCESS"  -> "SUB";
                case "ISSUE"    -> "DRF";   // validation failure = declaration saved as draft
                case "FAILURE"  -> "FLD";
                default         -> "NO_REPORT";
            };
        }

        Path diagnosticsPath = accumulator.validationJson != null
                ? accumulator.validationJson
                : accumulator.failureJson;
        long updatedAtMillis = Math.max(
                Math.max(lastModifiedMillis(diagnosticsPath), lastModifiedMillis(screenshotPath)),
                lastModifiedMillis(accumulator.statusScreenshot));
        String updatedAt = updatedAtMillis > Long.MIN_VALUE ? Instant.ofEpochMilli(updatedAtMillis).toString() : null;
        String resolvedEndTime = firstNonBlank(endTime, updatedAt);

        return new BatchCaseResult(
                index, status, message,
                declarationTypeCode,
                mapDeclarationTypeDisplay(declarationTypeCode),
                jobStatus, jobId, declNum, pmtNumber, createdBy,
                responseMsg, errorMsg, summary, toastText, invalidCount, rawJson,
                fileName(diagnosticsPath), fileName(screenshotPath),
                fileName(accumulator.statusScreenshot),
                startTime,
                resolvedEndTime,
                duration,
                status,
                "SUCCESS".equals(status) ? "Pass" : "Fail",
                updatedAt,
                updatedAtMillis);
    }

    private BatchDiagnostics readDiagnostics(Path diagnosticsPath) {
        if (diagnosticsPath == null || !Files.isRegularFile(diagnosticsPath)) {
            return BatchDiagnostics.empty();
        }

        try {
            String rawJson = Files.readString(diagnosticsPath);
            JsonNode root = objectMapper.readTree(rawJson);
            String responseSummary = blankToNull(root.path("responseSummary").asText(null));
            String jobId = firstNonBlank(
                    blankToNull(root.path("jobId").asText(null)),
                    parseSummaryField(responseSummary, "Job ID"));
            String jobStatus = normalizeJobStatus(firstNonBlank(
                    blankToNull(root.path("jobStatus").asText(null)),
                    parseSummaryField(responseSummary, "Status")));
            String declarationNumber = firstNonBlank(
                    blankToNull(root.path("declarationNumber").asText(null)),
                    parseSummaryField(responseSummary, "Message Ref"));
            String rawResponseData = normalizePlaceholder(root.path("rawResponseData").asText(null));
            String pmtNumber = firstNonBlank(
                    normalizePlaceholder(root.path("pmtNumber").asText(null)),
                    normalizePlaceholder(root.path("permitNumber").asText(null)),
                    parseSummaryField(responseSummary, "PMT Number"),
                    parseSummaryField(responseSummary, "Permit Number"),
                    parseSummaryField(responseSummary, "Permit No"),
                    parseResponseField(rawResponseData, "pmtNumber"),
                    parseResponseField(rawResponseData, "permitNumber"),
                    parseResponseField(rawResponseData, "permitNo"),
                    parseResponseField(rawResponseData, "permitNum"));
            pmtNumber = sanitizePmtNumber(pmtNumber, declarationNumber, jobStatus);
            String jobCreatedBy = firstNonBlank(
                    blankToNull(root.path("jobCreatedBy").asText(null)),
                    parseSummaryField(responseSummary, "Job Created By"));
            String declarationTypeCode = firstNonBlank(
                    blankToNull(root.path("declarationType").asText(null)),
                    parseSummaryField(responseSummary, "Declaration Type"));
            String toastText = blankToNull(root.path("toastText").asText(null));
            String responseMessage = firstNonBlank(
                    normalizePlaceholder(root.path("responseMessage").asText(null)),
                    parseSummaryField(responseSummary, "Response Message"),
                    parseResponseField(rawResponseData, "responseMessage"),
                    parseResponseField(rawResponseData, "message"),
                    parseResponseField(rawResponseData, "description"),
                    parseResponseField(rawResponseData, "detail"),
                    rawResponseData);
            String errorMessage = firstNonBlank(
                    normalizePlaceholder(root.path("errorMessage").asText(null)),
                    parseSummaryField(responseSummary, "Error Message"),
                    parseResponseField(rawResponseData, "errorMessage"),
                    parseResponseField(rawResponseData, "reason"),
                    parseResponseField(rawResponseData, "remarks"),
                    responseMessage);
            String startTime = readTextField(root, "startTime", "executionStartTime");
            String endTime = readTextField(root, "endTime", "executionEndTime", "capturedAt");
            Long durationMillis = readLongField(root, "durationMs", "executionDurationMs", "executionTimeMs");
            String duration = firstNonBlank(
                    readTextField(root, "duration", "durationText", "executionDuration"),
                    formatDuration(durationMillis));
            JsonNode invalidElementsNode = root.path("invalidElements");
            int invalidCount = invalidElementsNode.isArray() ? invalidElementsNode.size() : 0;
            return new BatchDiagnostics(
                    jobId,
                    jobStatus,
                    declarationNumber,
                    pmtNumber,
                    jobCreatedBy,
                    declarationTypeCode,
                    toastText,
                    responseMessage,
                    errorMessage,
                    responseSummary,
                    startTime,
                    endTime,
                    duration,
                    durationMillis,
                    invalidCount,
                    rawJson);
        } catch (Exception exception) {
            // File is not valid JSON (e.g. a plain-text Playwright error); surface the raw content
            String rawContent = null;
            try { rawContent = Files.readString(diagnosticsPath); } catch (Exception ignored) {}
            return new BatchDiagnostics(null, null, null, null, null, null, null, null, rawContent, null, null, null, null, null, 0, rawContent);
        }
    }

    private String readTextField(JsonNode root, String... fieldNames) {
        if (root == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            String value = blankToNull(root.path(fieldName).asText(null));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long readLongField(JsonNode root, String... fieldNames) {
        if (root == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            JsonNode node = root.path(fieldName);
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isNumber()) {
                return node.asLong();
            }
            String text = blankToNull(node.asText(null));
            if (text == null) {
                continue;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String formatDuration(Long durationMillis) {
        if (durationMillis == null || durationMillis < 0L) {
            return null;
        }
        if (durationMillis < 1000L) {
            return durationMillis + " ms";
        }
        long totalSeconds = durationMillis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format("%dm %02ds", minutes, seconds);
        }
        if (durationMillis % 1000L == 0L) {
            return totalSeconds + " s";
        }
        return String.format("%.2f s", durationMillis / 1000.0d);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String parseSummaryField(String responseSummary, String label) {
        if (responseSummary == null || responseSummary.isBlank() || label == null || label.isBlank()) {
            return null;
        }

        String expectedPrefix = label.trim() + ":";
        for (String line : responseSummary.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.regionMatches(true, 0, expectedPrefix, 0, expectedPrefix.length())) {
                continue;
            }
            String value = blankToNull(trimmed.substring(expectedPrefix.length()).trim());
            if ("N/A".equalsIgnoreCase(value)) {
                return null;
            }
            return value;
        }
        return null;
    }

    private String parseResponseField(String rawResponseData, String fieldName) {
        if (rawResponseData == null || rawResponseData.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponseData);
            return findResponseField(root, fieldName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String findResponseField(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            JsonNode direct = node.get(fieldName);
            if (direct != null && direct.isValueNode()) {
                return normalizePlaceholder(direct.asText(null));
            }
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                String value = findResponseField(children.next(), fieldName);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String value = findResponseField(child, fieldName);
                if (value != null) {
                    return value;
                }
            }
        }

        return null;
    }

    private String sanitizePmtNumber(String pmtNumber, String declarationNumber, String jobStatus) {
        String normalizedJobStatus = normalizeJobStatus(jobStatus);
        String normalizedPmtNumber = normalizePlaceholder(pmtNumber);
        if (!"PMT".equals(normalizedJobStatus) || normalizedPmtNumber == null) {
            return null;
        }

        String normalizedDeclarationNumber = normalizePlaceholder(declarationNumber);
        if (normalizedDeclarationNumber != null && normalizedDeclarationNumber.equalsIgnoreCase(normalizedPmtNumber)) {
            return null;
        }
        if (normalizedPmtNumber.matches("(?i)^TDX\\d+$")) {
            return null;
        }
        return normalizedPmtNumber;
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
        // Declarations that ended as Draft (DRF) with validation errors are not successes
        if (batchReport.summary().drafts() > 0 && batchReport.summary().successes() == 0) {
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
                    .filter(batchCase -> "ISSUE".equals(batchCase.status()) || "DRAFT".equals(batchCase.status()))
                    .map(BatchCaseResult::message)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse("Validation issue detected — declaration saved as draft.");
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

    private String normalizePlaceholder(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        return "N/A".equalsIgnoreCase(normalized) ? null : normalized;
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

    private String normalizeFilterValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return normalized.isEmpty() ? null : normalized;
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
            String declarationTypeDisplay,
            String messageReference) {
    }

    private record BatchDiagnostics(
            String jobId,
            String jobStatus,
            String declarationNumber,
            String pmtNumber,
            String jobCreatedBy,
            String declarationTypeCode,
            String toastText,
            String responseMessage,
            String errorMessage,
            String responseSummary,
            String startTime,
            String endTime,
            String duration,
            Long durationMillis,
            int invalidCount,
            String rawJson) {

        private static BatchDiagnostics empty() {
            return new BatchDiagnostics(null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, null);
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
            String pmtNumber,
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
            String startTime,
            String endTime,
            String duration,
            String executionStatus,
            String result,
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
