package utils;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Reads results.jtl and prints a summary table to the console.
 * Also validates that the error rate is within the allowed threshold.
 */
public class JMeterReportGenerator {

    private static final String RESULTS_JTL        = "jmeter\\results.jtl";
    private static final double ERROR_THRESHOLD    = 5.0; // percent
    private static final double RESPONSE_THRESHOLD = 2000.0; // ms

    public static void generateSummary() throws IOException {
        Path jtl = Paths.get(RESULTS_JTL);
        if (!Files.exists(jtl)) {
            System.out.println("[Report] results.jtl not found — skipping summary.");
            return;
        }

        List<String> lines = Files.readAllLines(jtl);
        if (lines.size() < 2) {
            System.out.println("[Report] results.jtl is empty.");
            return;
        }

        // Parse CSV header
        String[] headers = lines.get(0).split(",", -1);
        int idxSuccess  = indexOf(headers, "success");
        int idxElapsed  = indexOf(headers, "elapsed");
        int idxLabel    = indexOf(headers, "label");

        Map<String, Stats> statsMap = new LinkedHashMap<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length <= Math.max(idxSuccess, Math.max(idxElapsed, idxLabel))) continue;

            String label   = cols[idxLabel].trim();
            boolean passed = "true".equalsIgnoreCase(cols[idxSuccess].trim());
            long    elapsed;
            try { elapsed = Long.parseLong(cols[idxElapsed].trim()); }
            catch (NumberFormatException e) { continue; }

            statsMap.computeIfAbsent(label, k -> new Stats()).record(elapsed, passed);
        }

        printSummary(statsMap);
        validateErrorRate(statsMap);
    }

    private static void printSummary(Map<String, Stats> map) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║              JMeter Performance Test Summary                     ║");
        System.out.println("╠═══════════════════════════╦═══════╦═════════╦══════════╦════════╣");
        System.out.printf( "║ %-25s ║ %5s ║ %7s ║ %8s ║ %6s ║%n",
                "Sampler", "Count", "Avg(ms)", "Error(%)", "Pass");
        System.out.println("╠═══════════════════════════╬═══════╬═════════╬══════════╬════════╣");

        long totalReqs   = 0;
        long totalErrors = 0;
        long totalElapsed = 0;

        for (Map.Entry<String, Stats> entry : map.entrySet()) {
            Stats s = entry.getValue();
            totalReqs   += s.count;
            totalErrors += s.errors;
            totalElapsed += s.totalMs;
            System.out.printf("║ %-25s ║ %5d ║ %7d ║ %7.2f%% ║ %6s ║%n",
                    truncate(entry.getKey(), 25),
                    s.count,
                    s.count > 0 ? s.totalMs / s.count : 0,
                    s.errorRate(),
                    s.errors == 0 ? "PASS" : "FAIL");
        }

        System.out.println("╠═══════════════════════════╬═══════╬═════════╬══════════╬════════╣");
        double overallErr = totalReqs > 0 ? (totalErrors * 100.0 / totalReqs) : 0;
        long overallAvg = totalReqs > 0 ? totalElapsed / totalReqs : 0;
        System.out.printf("║ %-25s ║ %5d ║ %7s ║ %7.2f%% ║ %6s ║%n",
                "TOTAL", totalReqs, overallAvg, overallErr,
                overallErr <= ERROR_THRESHOLD && overallAvg <= RESPONSE_THRESHOLD ? "PASS" : "FAIL");
        System.out.println("╚═══════════════════════════╩═══════╩═════════╩══════════╩════════╝");
        System.out.println("  HTML Report: html-report\\index.html\n");
    }

    private static void validateErrorRate(Map<String, Stats> map) {
        long total  = map.values().stream().mapToLong(s -> s.count).sum();
        long errors = map.values().stream().mapToLong(s -> s.errors).sum();
        double rate = total > 0 ? (errors * 100.0 / total) : 0;

        if (rate > ERROR_THRESHOLD) {
            throw new RuntimeException(String.format(
                "[JMeter] Error rate %.2f%% exceeds threshold %.2f%%", rate, ERROR_THRESHOLD));
        }
        long totalElapsed = map.values().stream().mapToLong(s -> s.totalMs).sum();
        double averageResponseTime = total > 0 ? (totalElapsed * 1.0 / total) : 0.0;
        if (averageResponseTime > RESPONSE_THRESHOLD) {
            throw new RuntimeException(String.format(
                "[JMeter] Average response time %.2fms exceeds threshold %.2fms", averageResponseTime, RESPONSE_THRESHOLD));
        }
    }

    private static int indexOf(String[] arr, String name) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static class Stats {
        long count, errors, totalMs;
        void record(long ms, boolean passed) {
            count++;
            totalMs += ms;
            if (!passed) errors++;
        }
        double errorRate() { return count > 0 ? (errors * 100.0 / count) : 0; }
    }
}
