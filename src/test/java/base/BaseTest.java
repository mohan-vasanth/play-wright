package base;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class BaseTest {

    private static final String BROWSER_CHANNEL = System.getProperty("playwright.channel", "chrome");
    private static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("playwright.headless", "false"));
    private static final long DEFAULT_TIMEOUT_MS = Long.parseLong(System.getProperty("playwright.timeout.ms", "15000"));
    private static final long DEFAULT_NAVIGATION_TIMEOUT_MS = Long.parseLong(
            System.getProperty("playwright.navigation.timeout.ms", "60000"));

    protected Playwright playwright;
    protected Browser browser;
    protected BrowserContext context;
    protected Page page;

    @BeforeEach
    public void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setChannel(BROWSER_CHANNEL)
                        .setHeadless(HEADLESS));
        context = browser.newContext();
        page = context.newPage();
        page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        page.setDefaultNavigationTimeout(DEFAULT_NAVIGATION_TIMEOUT_MS);
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
