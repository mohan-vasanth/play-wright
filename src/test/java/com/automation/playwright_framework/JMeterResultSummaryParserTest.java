package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JMeterResultSummaryParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesSummaryMetricsFromJtl() throws Exception {
        Path results = tempDir.resolve("results.jtl");
        Files.writeString(results, """
                timeStamp,elapsed,Latency,label,success
                1000,120,80,Home,true
                2000,180,120,Login,false
                3000,300,200,Search,true
                """);

        JMeterResultSummaryParser.JMeterSummary summary = new JMeterResultSummaryParser().parse(results);

        assertEquals(3L, summary.totalRequests());
        assertEquals(2L, summary.successCount());
        assertEquals(1L, summary.failureCount());
        assertEquals(200.0d, summary.averageResponseTimeMs());
        assertEquals(133.33d, Math.round(summary.averageLatencyMs() * 100.0d) / 100.0d);
        assertEquals(1.5d, summary.throughput());
        assertEquals(33.33d, Math.round(summary.errorPercentage() * 100.0d) / 100.0d);
    }
}
