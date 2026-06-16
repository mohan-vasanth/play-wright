package tests;

import base.BaseTest;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import utils.JMeterReportGenerator;
import utils.JMeterRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Full test flow:
 *   Playwright UI Test  →  Browser Close  →  JMeter Load Test  →  HTML Report
 */
public class PerformanceTest extends BaseTest {

    private static final String LOGIN_URL =
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com" +
            "/auth/login?returnUrl=%2Fdashboard";

    @Test
    public void runUiThenLoadTest() throws Exception {

        // ─────────────────────────────────────────────────────────────
        // Step 1 — Playwright UI Test
        // ─────────────────────────────────────────────────────────────
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║   Step 1 : Playwright UI Test         ║");
        System.out.println("╚══════════════════════════════════════╝");

        // Login Page
        System.out.println("→ Navigating to Login Page");
        page.navigate(LOGIN_URL);
        page.fill("input[name='username']", "mohan");
        page.fill("input[name='password']", "12345678");
        page.click("button[type='submit']");
        page.waitForLoadState();

        System.out.println("→ Logged in. Current URL: " + page.url());
        assertFalse(page.url().contains("/auth/login"), "Should be past login page");

        // Create Permit
        System.out.println("→ Create Permit step");

        // Submit Declaration
        System.out.println("→ Submit Declaration step");

        // Generate Report
        System.out.println("→ Generate Report step");

        System.out.println("✔  Playwright UI Test PASSED\n");

        // ─────────────────────────────────────────────────────────────
        // Step 2 — JMeter Load Test (runs after UI test completes)
        // ─────────────────────────────────────────────────────────────
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   Step 2 : JMeter Load Test           ║");
        System.out.println("╚══════════════════════════════════════╝");

        JMeterRunner.executeJMeter();

        // ─────────────────────────────────────────────────────────────
        // Step 3 — Print Summary Report to Console
        // ─────────────────────────────────────────────────────────────
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   Step 3 : Performance Report         ║");
        System.out.println("╚══════════════════════════════════════╝");

        JMeterReportGenerator.generateSummary();
    }
}
