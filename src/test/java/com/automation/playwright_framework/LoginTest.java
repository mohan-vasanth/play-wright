package com.automation.playwright_framework;

import com.automation.LoginPage;
import base.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoginTest extends BaseTest {

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-52-74-80-143.ap-southeast-1.compute.amazonaws.com:9000/auth/login");
    private static final String ADMIN_USERNAME = System.getProperty("tradenix.admin.username", "prasanna");
    private static final String ADMIN_PASSWORD = System.getProperty("tradenix.admin.password", "123456");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "mohan");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "12345678");
    private static final String USER_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD");
    private static final String USER_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");

    @Test
    public void adminCanLoginToTradenix() {
        LoginPage loginPage = new LoginPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsAdmin(ADMIN_USERNAME, ADMIN_PASSWORD);

        page.waitForURL("**/dashboard");
        assertTrue(page.url().contains("/dashboard"));
    }

    @Test
    public void userCanLoginToTradenixWithSelectedForwarderAndDepartment() {
        LoginPage loginPage = new LoginPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);

        page.waitForURL("**/dashboard");
        assertTrue(page.url().contains("/dashboard"));
    }
}
