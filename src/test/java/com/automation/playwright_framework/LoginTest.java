package com.automation.playwright_framework;

import com.automation.LoginPage;
import base.BaseTest;
import base.TradenixLiveTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TradenixLiveTest
public class LoginTest extends BaseTest {

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard");
    private static final String ADMIN_USERNAME = System.getProperty("tradenix.admin.username");
    private static final String ADMIN_PASSWORD = System.getProperty("tradenix.admin.password");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "mohan");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "12345678");
    private static final String USER_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE. LTD.");
    private static final String USER_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");

    @Test
    public void adminCanLoginToTradenix() {
        Assumptions.assumeTrue(
                ADMIN_USERNAME != null && !ADMIN_USERNAME.isBlank()
                        && ADMIN_PASSWORD != null && !ADMIN_PASSWORD.isBlank(),
                "Admin UI login test requires tradenix.admin.username and tradenix.admin.password.");

        LoginPage loginPage = new LoginPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsAdmin(ADMIN_USERNAME, ADMIN_PASSWORD);

        loginPage.waitForAuthenticatedState();
        assertTrue(!page.url().contains("/auth/login"));
    }

    @Test
    public void userCanLoginToTradenixWithSelectedForwarderAndDepartment() {
        LoginPage loginPage = new LoginPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);

        loginPage.waitForAuthenticatedState();
        assertTrue(!page.url().contains("/auth/login"));
    }
}
