package com.automation.playwright_framework;

import com.automation.AdminOnboardingPage;
import com.automation.LoginPage;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserProvisioningService {

    private static final int MAX_USERS_PER_REQUEST = 500;
    private final ProvisionedLoadTestUserPool userPool;

    public UserProvisioningService(ProvisionedLoadTestUserPool userPool) {
        this.userPool = userPool;
    }

    public synchronized ProvisioningResponse provision(ProvisioningRequest request) {
        validateRequest(request);
        List<UserProvisioningResult> results = new ArrayList<>();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchBrowser(playwright);
            try {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
                try {
                    Page page = context.newPage();
                    page.setDefaultTimeout(45_000);
                    page.setDefaultNavigationTimeout(60_000);
                    LoginPage login = new LoginPage(page);
                    login.navigate(request.loginUrl().trim());
                    login.loginAsAdmin(request.adminUsername().trim(), request.adminPassword());
                    if (!login.waitForAuthenticatedState(45_000)) {
                        throw new IllegalStateException("Admin login did not reach an authenticated state.");
                    }

                    AdminOnboardingPage onboarding = new AdminOnboardingPage(page);
                    for (UserInput input : request.users()) {
                        try {
                            onboarding.openUsersPage();
                            if (onboarding.userIsListed(input.username())) {
                                register(input);
                                results.add(UserProvisioningResult.existing(input.username()));
                                continue;
                            }
                            onboarding.openOnboardUserForm();
                            onboarding.createUser(new AdminOnboardingPage.LoadTestUser(
                                    input.username(), input.password(), input.email(), input.organizationRole(), input.forwarder(), input.department()));
                            onboarding.openUsersPage();
                            if (!onboarding.userIsListed(input.username())) {
                                throw new IllegalStateException("The user was created but was not listed in User Management.");
                            }
                            register(input);
                            results.add(UserProvisioningResult.created(input.username()));
                        } catch (Exception exception) {
                            results.add(UserProvisioningResult.failed(input.username(), rootMessage(exception)));
                        }
                    }
                } finally {
                    context.close();
                }
            } finally {
                browser.close();
            }
        }
        return ProvisioningResponse.from(results, userPool.size());
    }

    public List<ProvisionedLoadTestUserPool.AvailableUser> availableUsers() {
        return userPool.availableUsers();
    }

    public byte[] exportUsers() {
        return UserProvisioningWorkbook.exportUsers(userPool.usersForExport());
    }

    private void register(UserInput input) {
        userPool.register(new ProvisionedLoadTestUserPool.LoadTestUser(
                input.username().trim(), input.password(), input.email().trim(), input.organizationRole().trim(),
                input.forwarder().trim(), input.department().trim(), Instant.now()));
    }

    private Browser launchBrowser(Playwright playwright) {
        String channel = System.getProperty("playwright.channel", "chrome");
        boolean headless = Boolean.parseBoolean(System.getProperty("tradenix.onboarding.headless", "true"));
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
        if (channel != null && !channel.isBlank()) {
            options.setChannel(channel.trim());
        }
        try {
            return playwright.chromium().launch(options);
        } catch (Exception primaryFailure) {
            if (channel == null || channel.isBlank()) {
                throw primaryFailure;
            }
            return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
        }
    }

    private void validateRequest(ProvisioningRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Provisioning request is required.");
        }
        require(request.loginUrl(), "Target login URL");
        require(request.adminUsername(), "Admin username");
        require(request.adminPassword(), "Admin password");
        if (request.users() == null || request.users().isEmpty()) {
            throw new IllegalArgumentException("Add at least one user to create.");
        }
        if (request.users().size() > MAX_USERS_PER_REQUEST) {
            throw new IllegalArgumentException("A maximum of " + MAX_USERS_PER_REQUEST + " users can be created in one request.");
        }
        Set<String> usernames = new HashSet<>();
        for (UserInput input : request.users()) {
            if (input == null) {
                throw new IllegalArgumentException("A user row is empty.");
            }
            require(input.username(), "Username");
            require(input.password(), "Password for " + input.username());
            require(input.email(), "Email for " + input.username());
            require(input.organizationRole(), "Organization role for " + input.username());
            require(input.forwarder(), "Forwarder for " + input.username());
            require(input.department(), "Department for " + input.username());
            if (!input.email().contains("@")) {
                throw new IllegalArgumentException("Email is invalid for " + input.username());
            }
            if (!usernames.add(input.username().trim().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate username in this request: " + input.username());
            }
        }
    }

    private void require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    public record ProvisioningRequest(
            String loginUrl,
            String adminUsername,
            String adminPassword,
            List<UserInput> users) {
    }

    public record UserInput(
            String username,
            String password,
            String email,
            String organizationRole,
            String forwarder,
            String department) {
    }

    public record UserProvisioningResult(String username, String status, String message) {
        static UserProvisioningResult created(String username) {
            return new UserProvisioningResult(username, "CREATED", "Created and verified in User Management.");
        }

        static UserProvisioningResult existing(String username) {
            return new UserProvisioningResult(username, "EXISTS", "Already exists and is available to the load-test pool.");
        }

        static UserProvisioningResult failed(String username, String message) {
            return new UserProvisioningResult(username, "FAILED", message);
        }
    }

    public record ProvisioningResponse(
            int requested,
            int created,
            int existing,
            int failed,
            int loadTestPoolSize,
            List<UserProvisioningResult> users) {
        static ProvisioningResponse from(List<UserProvisioningResult> results, int poolSize) {
            return new ProvisioningResponse(
                    results.size(),
                    (int) results.stream().filter(result -> "CREATED".equals(result.status())).count(),
                    (int) results.stream().filter(result -> "EXISTS".equals(result.status())).count(),
                    (int) results.stream().filter(result -> "FAILED".equals(result.status())).count(),
                    poolSize,
                    List.copyOf(results));
        }
    }
}
