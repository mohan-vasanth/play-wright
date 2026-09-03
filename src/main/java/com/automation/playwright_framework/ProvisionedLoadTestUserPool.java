package com.automation.playwright_framework;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime-only credentials for users created by the provisioning page.
 * Passwords are never exposed by the API and are lost when the application stops.
 */
@Service
public class ProvisionedLoadTestUserPool {

    private final Map<String, LoadTestUser> usersByUsername = new LinkedHashMap<>();

    public synchronized void register(LoadTestUser user) {
        usersByUsername.put(normalizeUsername(user.username()), user);
    }

    public synchronized List<LoadTestUser> first(int count) {
        if (count < 1 || usersByUsername.size() < count) {
            throw new IllegalArgumentException("The load-test user pool contains " + usersByUsername.size()
                    + " user(s), but " + count + " are required.");
        }
        return usersByUsername.values().stream().limit(count).toList();
    }

    public synchronized List<AvailableUser> availableUsers() {
        return usersByUsername.values().stream()
                .map(user -> new AvailableUser(
                        user.username(), user.email(), user.organizationRole(), user.forwarder(), user.department(), user.provisionedAt()))
                .toList();
    }

    /**
     * Credentials are available only to the load runner and an explicit export.
     * The normal pool API never returns passwords.
     */
    public synchronized List<LoadTestUser> usersForExport() {
        return List.copyOf(usersByUsername.values());
    }

    public synchronized int size() {
        return usersByUsername.size();
    }

    private String normalizeUsername(String username) {
        return username.trim().toUpperCase(Locale.ROOT);
    }

    public record LoadTestUser(
            String username,
            String password,
            String email,
            String organizationRole,
            String forwarder,
            String department,
            Instant provisionedAt) {
    }

    public record AvailableUser(
            String username,
            String email,
            String organizationRole,
            String forwarder,
            String department,
            Instant provisionedAt) {
    }
}
