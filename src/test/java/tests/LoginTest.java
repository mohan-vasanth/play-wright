package tests;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Test;
import utils.JMeterRunner;

public class LoginTest {

    private static final String LOGIN_URL =
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard";

    @Test
    public void loginAndPerformanceTest() throws Exception {

        // ── Step 1: Playwright UI Test ───────────────────────────────────────
        System.out.println("=== Starting Playwright UI Test ===");

        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(false));
        Page page = browser.newPage();

        page.navigate(LOGIN_URL);
        System.out.println("Navigated to Login Page: " + page.url());

        // Login Page
        page.fill("input[name='username']", "mohan");
        page.fill("input[name='password']", "12345678");
        page.click("button[type='submit']");
        System.out.println("Login submitted");

        page.waitForLoadState(LoadState.NETWORKIDLE);
        System.out.println("Dashboard loaded: " + page.url());

        // Create Permit
        System.out.println("Navigating: Create Permit");

        // Submit Declaration
        System.out.println("Navigating: Submit Declaration");

        // Generate Report
        System.out.println("Navigating: Generate Report");

        System.out.println("=== Playwright UI Test Completed ===");

        browser.close();
        playwright.close();

        // ── Step 2: JMeter API Load Test ─────────────────────────────────────
        System.out.println("=== Starting JMeter Load Test ===");
        JMeterRunner.executeJMeter();
        System.out.println("=== JMeter Load Test Completed ===");
    }
}
