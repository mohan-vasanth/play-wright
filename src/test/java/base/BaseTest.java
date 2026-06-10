package base;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class BaseTest {

    protected Playwright playwright;
    protected Browser browser;
    protected BrowserContext context;
    protected Page page;

    @BeforeEach
    public void setup() {
        String browserChannel = System.getProperty("playwright.channel", "chrome");
        boolean headless = Boolean.parseBoolean(System.getProperty("playwright.headless", "false"));
        double slowMoMs = Double.parseDouble(System.getProperty("playwright.slowmo.ms", "0"));
        long defaultTimeoutMs = Long.parseLong(System.getProperty("playwright.timeout.ms", "15000"));
        long defaultNavigationTimeoutMs = Long.parseLong(
                System.getProperty("playwright.navigation.timeout.ms", "60000"));

        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setChannel(browserChannel)
                        .setHeadless(headless)
                        .setSlowMo(slowMoMs));
        context = browser.newContext();
        page = context.newPage();
        page.setDefaultTimeout(defaultTimeoutMs);
        page.setDefaultNavigationTimeout(defaultNavigationTimeoutMs);
    }

    @AfterEach
    public void tearDown() {
        if (context != null) {
            context.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
