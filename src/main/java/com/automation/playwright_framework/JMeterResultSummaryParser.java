package com.automation.playwright_framework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class JMeterResultSummaryParser {

    JMeterSummary parse(Path resultsPath) throws IOException {
        if (resultsPath == null || !Files.isRegularFile(resultsPath)) {
            return JMeterSummary.empty();
        }

        List<String> lines = Files.readAllLines(resultsPath);
        if (lines.size() < 2) {
            return JMeterSummary.empty();
        }

        List<String> headers = parseCsvLine(lines.get(0));
        int timeStampIndex = indexOf(headers, "timeStamp");
        int elapsedIndex = indexOf(headers, "elapsed");
        int latencyIndex = indexOf(headers, "Latency");
        int successIndex = indexOf(headers, "success");
        if (timeStampIndex < 0 || elapsedIndex < 0 || successIndex < 0) {
            return JMeterSummary.empty();
        }

        long totalRequests = 0L;
        long successCount = 0L;
        long failureCount = 0L;
        long totalElapsedMs = 0L;
        long totalLatencyMs = 0L;
        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = Long.MIN_VALUE;

        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            if (line == null || line.isBlank()) {
                continue;
            }

            List<String> columns = parseCsvLine(line);
            if (columns.size() <= Math.max(successIndex, Math.max(timeStampIndex, elapsedIndex))) {
                continue;
            }

            long timestamp = parseLong(columns.get(timeStampIndex));
            long elapsed = parseLong(columns.get(elapsedIndex));
            long latency = latencyIndex >= 0 && columns.size() > latencyIndex
                    ? parseLong(columns.get(latencyIndex))
                    : elapsed;
            boolean success = Boolean.parseBoolean(columns.get(successIndex).trim());

            totalRequests++;
            totalElapsedMs += Math.max(elapsed, 0L);
            totalLatencyMs += Math.max(latency, 0L);
            if (success) {
                successCount++;
            } else {
                failureCount++;
            }

            if (timestamp > 0L) {
                minTimestamp = Math.min(minTimestamp, timestamp);
                maxTimestamp = Math.max(maxTimestamp, timestamp);
            }
        }

        if (totalRequests == 0L) {
            return JMeterSummary.empty();
        }

        double averageResponseTimeMs = totalElapsedMs / (double) totalRequests;
        double averageLatencyMs = totalLatencyMs / (double) totalRequests;
        double errorPercentage = failureCount * 100.0d / totalRequests;
        double throughput = calculateThroughput(totalRequests, minTimestamp, maxTimestamp);

        return new JMeterSummary(
                totalRequests,
                successCount,
                failureCount,
                averageResponseTimeMs,
                averageLatencyMs,
                throughput,
                errorPercentage);
    }

    private double calculateThroughput(long totalRequests, long minTimestamp, long maxTimestamp) {
        if (minTimestamp == Long.MAX_VALUE || maxTimestamp == Long.MIN_VALUE) {
            return 0.0d;
        }
        double durationSeconds = Math.max((maxTimestamp - minTimestamp) / 1000.0d, 1.0d);
        return totalRequests / durationSeconds;
    }

    private int indexOf(List<String> headers, String name) {
        for (int index = 0; index < headers.size(); index++) {
            if (name.equalsIgnoreCase(headers.get(index).trim())) {
                return index;
            }
        }
        return -1;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (currentChar == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        columns.add(current.toString());
        return columns;
    }

    record JMeterSummary(
            long totalRequests,
            long successCount,
            long failureCount,
            double averageResponseTimeMs,
            double averageLatencyMs,
            double throughput,
            double errorPercentage) {

        static JMeterSummary empty() {
            return new JMeterSummary(0L, 0L, 0L, 0.0d, 0.0d, 0.0d, 0.0d);
        }
    }
}
