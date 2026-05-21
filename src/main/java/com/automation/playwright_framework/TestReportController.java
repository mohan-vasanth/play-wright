package com.automation.playwright_framework;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class TestReportController {

    private static final Path SUREFIRE_REPORTS_DIR = Paths.get("target", "surefire-reports");

    @GetMapping(value = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TestReportResponse> latest() {
        if (!Files.isDirectory(SUREFIRE_REPORTS_DIR)) {
            return ResponseEntity.ok(TestReportResponse.noReport("No surefire report directory found."));
        }

        try {
            Path latestReport = Files.list(SUREFIRE_REPORTS_DIR)
                    .filter(path -> path.getFileName().toString().startsWith("TEST-"))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .max(Comparator.comparingLong(this::lastModifiedMillis))
                    .orElse(null);

            if (latestReport == null) {
                return ResponseEntity.ok(TestReportResponse.noReport("No surefire XML report found."));
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
            String status = (failures > 0 || errors > 0) ? "FAILURE" : "SUCCESS";

            List<TestCaseResult> testCases = new ArrayList<>();
            NodeList testCaseNodes = suite.getElementsByTagName("testcase");
            for (int index = 0; index < testCaseNodes.getLength(); index++) {
                Node current = testCaseNodes.item(index);
                if (!(current instanceof Element testCase)) {
                    continue;
                }
                testCases.add(parseTestCase(testCase));
            }

            String primaryIssue = testCases.stream()
                    .map(TestCaseResult::message)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse(status.equals("SUCCESS") ? "Completed successfully." : "Test failed.");

            return ResponseEntity.ok(new TestReportResponse(
                    status,
                    suiteName,
                    latestReport.getFileName().toString(),
                    tests,
                    failures,
                    errors,
                    skipped,
                    duration,
                    Instant.ofEpochMilli(lastModifiedMillis(latestReport)).toString(),
                    primaryIssue,
                    testCases));
        } catch (Exception exception) {
            return ResponseEntity.ok(TestReportResponse.noReport("Unable to read latest report: " + exception.getMessage()));
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

    private long lastModifiedMillis(Path path) {
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

    public record TestCaseResult(
            String name,
            String className,
            String status,
            String durationSeconds,
            String message,
            String detail) {
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
            List<TestCaseResult> testCases) {

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
                    List.of());
        }
    }
}
