package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Executes Apache JMeter in non-GUI mode.
 *
 * Flow:
 *   1. Clean old results
 *   2. Run .jmx  →  results.jtl + html-report/index.html
 */
public class JMeterRunner {

    private static final String JMETER_BAT  = "C:\\apache-jmeter-5.6.3\\bin\\jmeter.bat";
    private static final String TEST_PLAN   = "jmeter\\performance-test.jmx";
    private static final String RESULTS_JTL = "jmeter\\results.jtl";
    private static final String HTML_DIR    = "html-report";

    public static void executeJMeter() throws IOException, InterruptedException {

        prepareDirectories();

        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│  JMeter Load Test Starting...            │");
        System.out.println("│  Plan : " + TEST_PLAN);
        System.out.println("│  Users: 50 | Ramp: 10s | Loops: 5       │");
        System.out.println("└──────────────────────────────────────────┘");

        int exitCode = runProcess(
                JMETER_BAT,
                "-n",                     // non-GUI
                "-t", TEST_PLAN,          // test plan
                "-l", RESULTS_JTL,        // results file
                "-e",                     // generate HTML report
                "-o", HTML_DIR            // HTML report output dir
        );

        System.out.println("[JMeter] Exit code : " + exitCode);
        System.out.println("[JMeter] Results   : " + RESULTS_JTL);
        System.out.println("[JMeter] Report    : " + HTML_DIR + "\\index.html");

        if (exitCode != 0) {
            throw new RuntimeException(
                "[JMeter] Test failed — exit code: " + exitCode);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void prepareDirectories() throws IOException {
        deleteDirectory(new File(HTML_DIR));           // must be empty for -o flag
        Files.createDirectories(Paths.get("jmeter"));
        Files.createDirectories(Paths.get(HTML_DIR));
        Files.deleteIfExists(Paths.get(RESULTS_JTL));
    }

    private static int runProcess(String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        pb.directory(new File("."));
        return pb.start().waitFor();
    }

    private static void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
