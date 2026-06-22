package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestReportControllerParsingTest {

    @Test
    void readDiagnosticsClearsPmtNumberForNonPmtStatuses() throws Exception {
        Path diagnosticsFile = Files.createTempFile("out-batch-submit-validation-", ".json");
        Files.writeString(diagnosticsFile, """
                {
                  "toastText": "Validation Failed:",
                  "responseMessage": "Validation Failed: Please fill in all required fields before submitting",
                  "errorMessage": "Validation Failed:",
                  "jobStatus": "FLD",
                  "declarationType": "40",
                  "pmtNumber": "OD6F274299A",
                  "jobCreatedBy": "mohan",
                  "responseSummary": "Job ID: N/A\\r\\n\\r\\nMessage Ref: TDX2606150012\\r\\n\\r\\nDeclaration Type: 40\\r\\n\\r\\nStatus: FLD\\r\\n\\r\\nPMT Number: OD6F274299A\\r\\n\\r\\nJob Created By: mohan"
                }
                """);

        try {
            TestReportController controller = new TestReportController();
            Method method = TestReportController.class.getDeclaredMethod("readDiagnostics", Path.class);
            method.setAccessible(true);
            Object diagnostics = method.invoke(controller, diagnosticsFile);

            Method declarationTypeCode = diagnostics.getClass().getDeclaredMethod("declarationTypeCode");
            Method declarationNumber = diagnostics.getClass().getDeclaredMethod("declarationNumber");
            Method jobStatus = diagnostics.getClass().getDeclaredMethod("jobStatus");
            Method pmtNumber = diagnostics.getClass().getDeclaredMethod("pmtNumber");
            Method jobCreatedBy = diagnostics.getClass().getDeclaredMethod("jobCreatedBy");

            assertEquals("40", declarationTypeCode.invoke(diagnostics));
            assertEquals("TDX2606150012", declarationNumber.invoke(diagnostics));
            assertEquals("FLD", jobStatus.invoke(diagnostics));
            assertNull(pmtNumber.invoke(diagnostics));
            assertEquals("mohan", jobCreatedBy.invoke(diagnostics));
        } finally {
            Files.deleteIfExists(diagnosticsFile);
        }
    }

    @Test
    void readDiagnosticsUsesRawResponseDataWhenMessageFieldsArePlaceholders() throws Exception {
        Path diagnosticsFile = Files.createTempFile("out-batch-submit-validation-", ".json");
        Files.writeString(diagnosticsFile, """
                {
                  "responseMessage": "N/A",
                  "errorMessage": "N/A",
                  "jobStatus": "REJ",
                  "rawResponseData": "{ \\"responseMessage\\": \\"PERMIT/AMENDMENT/CANCELLATION/REFUND APPLICATION NOT APPROVED\\\\n1. PLS PROVIDE CORRECT DATE OF DEPARTURE\\", \\"errorMessage\\": \\"PERMIT/AMENDMENT/CANCELLATION/REFUND APPLICATION NOT APPROVED\\\\n1. PLS PROVIDE CORRECT DATE OF DEPARTURE\\", \\"permitNo\\": \\"OD6F274299A\\" }"
                }
                """);

        try {
            TestReportController controller = new TestReportController();
            Method method = TestReportController.class.getDeclaredMethod("readDiagnostics", Path.class);
            method.setAccessible(true);
            Object diagnostics = method.invoke(controller, diagnosticsFile);

            Method responseMessage = diagnostics.getClass().getDeclaredMethod("responseMessage");
            Method errorMessage = diagnostics.getClass().getDeclaredMethod("errorMessage");
            Method pmtNumber = diagnostics.getClass().getDeclaredMethod("pmtNumber");

            String expected = "PERMIT/AMENDMENT/CANCELLATION/REFUND APPLICATION NOT APPROVED\n1. PLS PROVIDE CORRECT DATE OF DEPARTURE";
            assertEquals(expected, responseMessage.invoke(diagnostics));
            assertEquals(expected, errorMessage.invoke(diagnostics));
            assertNull(pmtNumber.invoke(diagnostics));
        } finally {
            Files.deleteIfExists(diagnosticsFile);
        }
    }

    @Test
    void readDiagnosticsFallsBackToSummaryForPmtNumber() throws Exception {
        Path diagnosticsFile = Files.createTempFile("out-batch-submit-validation-", ".json");
        Files.writeString(diagnosticsFile, """
                {
                  "jobStatus": "PMT",
                  "responseSummary": "Job ID: 5341\\r\\n\\r\\nMessage Ref: TDX2606150030\\r\\n\\r\\nDeclaration Type: 40\\r\\n\\r\\nStatus: PMT\\r\\n\\r\\nPMT Number: OD6F274299A\\r\\n\\r\\nJob Created By: mohan"
                }
                """);

        try {
            TestReportController controller = new TestReportController();
            Method method = TestReportController.class.getDeclaredMethod("readDiagnostics", Path.class);
            method.setAccessible(true);
            Object diagnostics = method.invoke(controller, diagnosticsFile);

            Method pmtNumber = diagnostics.getClass().getDeclaredMethod("pmtNumber");
            assertEquals("OD6F274299A", pmtNumber.invoke(diagnostics));
        } finally {
            Files.deleteIfExists(diagnosticsFile);
        }
    }

    @Test
    void readDiagnosticsRetainsPmtNumberForSubmittedStatus() throws Exception {
        Path diagnosticsFile = Files.createTempFile("out-batch-submit-validation-", ".json");
        Files.writeString(diagnosticsFile, """
                {
                  "jobStatus": "SUB",
                  "responseSummary": "Job ID: 5341\\r\\n\\r\\nMessage Ref: TDX2606150030\\r\\n\\r\\nDeclaration Type: COO\\r\\n\\r\\nStatus: SUB\\r\\n\\r\\nPMT Number: OD6F274299A\\r\\n\\r\\nJob Created By: mohan"
                }
                """);

        try {
            TestReportController controller = new TestReportController();
            Method method = TestReportController.class.getDeclaredMethod("readDiagnostics", Path.class);
            method.setAccessible(true);
            Object diagnostics = method.invoke(controller, diagnosticsFile);

            Method pmtNumber = diagnostics.getClass().getDeclaredMethod("pmtNumber");
            assertEquals("OD6F274299A", pmtNumber.invoke(diagnostics));
        } finally {
            Files.deleteIfExists(diagnosticsFile);
        }
    }

    @Test
    void readDiagnosticsRejectsMessageReferenceAsPmtNumberEvenForPmtStatus() throws Exception {
        Path diagnosticsFile = Files.createTempFile("out-batch-submit-validation-", ".json");
        Files.writeString(diagnosticsFile, """
                {
                  "jobStatus": "PMT",
                  "declarationNumber": "TDX2606150066",
                  "pmtNumber": "TDX2606150066"
                }
                """);

        try {
            TestReportController controller = new TestReportController();
            Method method = TestReportController.class.getDeclaredMethod("readDiagnostics", Path.class);
            method.setAccessible(true);
            Object diagnostics = method.invoke(controller, diagnosticsFile);

            Method pmtNumber = diagnostics.getClass().getDeclaredMethod("pmtNumber");
            assertNull(pmtNumber.invoke(diagnostics));
        } finally {
            Files.deleteIfExists(diagnosticsFile);
        }
    }

    @Test
    void readDiagnosticsCapturesExecutionTimingFields() throws Exception {
        Path diagnosticsFile = Files.createTempFile("out-batch-submit-validation-", ".json");
        Files.writeString(diagnosticsFile, """
                {
                  "jobStatus": "PMT",
                  "startTime": "2026-06-19T03:00:00Z",
                  "endTime": "2026-06-19T03:01:05Z",
                  "durationMs": 65000
                }
                """);

        try {
            TestReportController controller = new TestReportController();
            Method method = TestReportController.class.getDeclaredMethod("readDiagnostics", Path.class);
            method.setAccessible(true);
            Object diagnostics = method.invoke(controller, diagnosticsFile);

            Method startTime = diagnostics.getClass().getDeclaredMethod("startTime");
            Method endTime = diagnostics.getClass().getDeclaredMethod("endTime");
            Method duration = diagnostics.getClass().getDeclaredMethod("duration");
            Method durationMillis = diagnostics.getClass().getDeclaredMethod("durationMillis");

            assertEquals("2026-06-19T03:00:00Z", startTime.invoke(diagnostics));
            assertEquals("2026-06-19T03:01:05Z", endTime.invoke(diagnostics));
            assertNotNull(duration.invoke(diagnostics));
            assertEquals(65000L, durationMillis.invoke(diagnostics));
        } finally {
            Files.deleteIfExists(diagnosticsFile);
        }
    }
}
