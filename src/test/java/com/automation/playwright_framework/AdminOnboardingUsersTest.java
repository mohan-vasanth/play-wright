package com.automation.playwright_framework;

import base.BaseTest;
import base.TradenixLiveTest;
import com.automation.AdminOnboardingPage;
import com.automation.LoginPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TradenixLiveTest
class AdminOnboardingUsersTest extends BaseTest {

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard");
    private static final String ADMIN_USERNAME = System.getProperty("tradenix.admin.username");
    private static final String ADMIN_PASSWORD = System.getProperty("tradenix.admin.password");

    @Test
    void adminCanOpenTheOnboardUserForm() {
        loginAsAdmin();
        AdminOnboardingPage onboarding = new AdminOnboardingPage(page);
        try {
            onboarding.openOnboardUserForm();
        } catch (RuntimeException failure) {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(Path.of("target", "admin-management-inspection.png"))
                    .setFullPage(true));
            System.out.println("Admin management controls after navigation:\n" + onboarding.visibleFormInventory());
            throw failure;
        }
        page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                .setPath(Path.of("target", "admin-onboarding-inspection.png"))
                .setFullPage(true));
        System.out.println("Admin onboarding form controls:\n" + onboarding.visibleFormInventory());
    }

    @Test
    void adminCanInspectForwarderAssignmentControls() {
        loginAsAdmin();
        AdminOnboardingPage onboarding = new AdminOnboardingPage(page);
        onboarding.openOnboardUserForm();
        onboarding.openForwarderAssignment();
        page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                .setPath(Path.of("target", "admin-forwarder-assignment-inspection.png"))
                .setFullPage(true));
        System.out.println("Forwarder assignment controls:\n" + onboarding.visibleFormInventory());
    }

    @Test
    void adminCanCheckWhetherLoadtest1WasCreated() {
        String username = System.getProperty("tradenix.onboarding.check.username", "loadtest1");
        loginAsAdmin();
        AdminOnboardingPage onboarding = new AdminOnboardingPage(page);
        onboarding.openUsersPage();
        System.out.println(username + " listed=" + onboarding.userIsListed(username));
    }

    @Test
    void createsConfiguredLoadTestUsers() {
        Assumptions.assumeTrue(Boolean.getBoolean("tradenix.onboarding.enabled"),
                "Set -Dtradenix.onboarding.enabled=true to provision load-test users.");
        String password = requiredProperty("tradenix.load.users.password");
        String emailDomain = System.getProperty("tradenix.load.users.email.domain", "email.com");
        int start = Integer.getInteger("tradenix.load.users.start", 1);
        int end = Integer.getInteger("tradenix.load.users.end", 50);
        if (start < 1 || end < start) {
            throw new IllegalArgumentException("Invalid load-user range: " + start + " to " + end);
        }

        loginAsAdmin();
        AdminOnboardingPage onboarding = new AdminOnboardingPage(page);
        for (int number = start; number <= end; number++) {
            onboarding.openOnboardUserForm();
            String username = "loadtest" + number;
            try {
                onboarding.createUser(new AdminOnboardingPage.LoadTestUser(
                        username,
                        password,
                        username + "@" + emailDomain,
                        System.getProperty("tradenix.load.users.organization.role", "Declarant"),
                        System.getProperty("tradenix.load.users.forwarder", "ADATACOMPANY PTE. LTD."),
                        System.getProperty("tradenix.load.users.department", "Import")));
            } catch (RuntimeException failure) {
                page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                        .setPath(Path.of("target", username + "-onboarding-failure.png"))
                        .setFullPage(true));
                System.out.println("Onboarding failure controls:\n" + onboarding.visibleFormInventory());
                throw failure;
            }
            onboarding.openUsersPage();
            if (!onboarding.userIsListed(username)) {
                throw new IllegalStateException("Created user was not listed in User Management: " + username);
            }
            System.out.println("Created load-test user " + username);
        }
    }

    private void loginAsAdmin() {
        Assumptions.assumeTrue(ADMIN_USERNAME != null && !ADMIN_USERNAME.isBlank()
                        && ADMIN_PASSWORD != null && !ADMIN_PASSWORD.isBlank(),
                "Admin onboarding requires tradenix.admin.username and tradenix.admin.password.");
        LoginPage login = new LoginPage(page);
        login.navigate(LOGIN_URL);
        login.loginAsAdmin(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertTrue(login.waitForAuthenticatedState(45_000), "Admin login did not reach an authenticated state.");
    }

    private String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required system property is missing: " + name);
        }
        return value;
    }
}
