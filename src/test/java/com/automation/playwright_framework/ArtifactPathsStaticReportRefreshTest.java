package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "user.dir", mode = ResourceAccessMode.READ_WRITE)
class ArtifactPathsStaticReportRefreshTest {

    @Test
    void syncDeclarationArtifactsRebuildsStaticReportDataAfterSurefireReportExists() throws Exception {
        String artifactPrefix = "report-refresh-regression";
        Path surefireDir = ArtifactPaths.TARGET_DIR.resolve("surefire-reports");
        Path surefireReport = surefireDir.resolve("TEST-ReportRefreshRegression.xml");
        Path validationFile = ArtifactPaths.TARGET_DIR.resolve(artifactPrefix + "-validation-1.json");
        Path screenshotFile = ArtifactPaths.TARGET_DIR.resolve(artifactPrefix + "-1.png");
        Path reportDataFile = ArtifactPaths.TARGET_DIR.resolve("report-data.js");
        Path syncedReportDataFile = ArtifactPaths.REPORT_DATA;

        Files.createDirectories(surefireDir);
        Files.writeString(surefireReport, """
                <testsuite name="com.automation.playwright_framework.IptDeclarationTestCase1Test"
                           tests="1"
                           failures="0"
                           errors="0"
                           skipped="0"
                           time="4.200">
                    <properties>
                        <property name="tradenix.ipt.test.data" value="IPT/ipt-declaration-test-case-1.json" />
                        <property name="tradenix.report.artifact.prefix" value="report-refresh-regression" />
                    </properties>
                    <testcase name="submitIptDeclarationTestCase1UsingJsonData"
                              classname="com.automation.playwright_framework.IptDeclarationTestCase1Test"
                              time="4.200" />
                </testsuite>
                """);
        Files.writeString(validationFile, """
                {
                  "jobId": "7777",
                  "jobStatus": "SUB",
                  "declarationType": "12",
                  "declarationNumber": "TDX2606250999",
                  "jobCreatedBy": "mohan",
                  "responseMessage": "Declaration submitted successfully.",
                  "errorMessage": "N/A",
                  "responseSummary": "Job ID: 7777\\r\\n\\r\\nMessage Ref: TDX2606250999\\r\\n\\r\\nDeclaration Type: 12\\r\\n\\r\\nStatus: SUB",
                  "startTime": "2026-06-25T08:00:00Z",
                  "endTime": "2026-06-25T08:04:12Z",
                  "durationMs": 252000
                }
                """);
        Files.write(screenshotFile, new byte[] { 1, 2, 3 });

        try {
            ArtifactPaths.syncDeclarationArtifacts(artifactPrefix);

            String reportData = Files.readString(reportDataFile);
            String syncedReportData = Files.readString(syncedReportDataFile);

            assertTrue(reportData.contains("\"suiteName\" : \"com.automation.playwright_framework.IptDeclarationTestCase1Test\""));
            assertTrue(reportData.contains("\"sourceFile\" : \"TEST-ReportRefreshRegression.xml\""));
            assertTrue(reportData.contains("\"jobId\" : \"7777\""));
            assertTrue(reportData.contains("\"declarationNumber\" : \"TDX2606250999\""));
            assertTrue(syncedReportData.contains("\"jobId\" : \"7777\""));
        } finally {
            Files.deleteIfExists(surefireReport);
            Files.deleteIfExists(validationFile);
            Files.deleteIfExists(screenshotFile);
            Files.deleteIfExists(ArtifactPaths.SCREENSHOTS_DIR.resolve(artifactPrefix + "-1.png"));
            Files.deleteIfExists(ArtifactPaths.REPORTS_DIR.resolve(artifactPrefix + "-validation-1.json"));
            Files.deleteIfExists(reportDataFile);
            Files.deleteIfExists(syncedReportDataFile);
        }
    }
}
