package com.automation;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import java.net.URI;

public class LoginPage {

    private final Page page;
    private static final String ADMIN_TAB = "#btn-demo-radio-1";
    private static final String USER_TAB = "#btn-demo-radio-2";
    private static final String USERNAME = "input[formcontrolname='username']";
    private static final String PASSWORD = "input[formcontrolname='password']";
    private static final String FORWARDER = "select[formcontrolname='forwarder']";
    private static final String DEPARTMENT = "select[formcontrolname='department']";
    private static final String LOAD_USER_DETAILS_BUTTON = "button.btn.btn-xl.btn-icon.btn-outlined";
    private static final String LOGIN_BUTTON = "button[type='submit']";

    public LoginPage(Page page) {
        this.page = page;
    }

    public void navigate(String url) {
        navigateAndWait(url);
    }

    private void navigateAndWait(String url) {
        try {
            page.navigate(url, new NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.locator(USERNAME).waitFor();
            return;
        } catch (PlaywrightException ignored) {
        }

        page.navigate(extractOrigin(url), new NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.locator(USERNAME).waitFor();
    }

    private String extractOrigin(String url) {
        URI uri = URI.create(url);
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port > -1 ? ":" + port : "") + "/";
    }

    public void loginAsAdmin(String user, String pass) {
        page.locator(ADMIN_TAB).evaluate("element => element.click()");
        page.locator(USERNAME).fill(user);
        page.waitForFunction("selector => { const input = document.querySelector(selector); return !!input && !input.disabled; }", PASSWORD);
        page.locator(PASSWORD).fill(pass);
        page.locator(LOGIN_BUTTON).click();
    }

    public void loginAsUserWithFirstOptions(String user, String pass) {
        loginAsUser(user, pass, null, null);
    }

    public void loginAsUser(String user, String pass, String forwarderLabel, String departmentLabel) {
        page.locator(USER_TAB).evaluate("element => element.click()");
        page.locator(USERNAME).fill(user);
        page.locator(LOAD_USER_DETAILS_BUTTON).click();

        selectUserOption(FORWARDER, forwarderLabel, "Forwarder");
        selectUserOption(DEPARTMENT, departmentLabel, "Department");

        page.waitForFunction("selector => { const input = document.querySelector(selector); return !!input && !input.disabled; }", PASSWORD);
        page.locator(PASSWORD).fill(pass);
        page.locator(LOGIN_BUTTON).click();
    }

    private void selectUserOption(String selector, String requestedLabel, String fieldName) {
        page.waitForFunction(
                "selector => { const select = document.querySelector(selector); return !!select && !select.disabled && select.options.length > 1; }",
                selector);

        String optionValue = page.locator(selector).evaluate(
                """
                (select, requestedLabel) => {
                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                    const compact = value => normalize(value).replace(/[^A-Z0-9]/g, '');
                    const options = Array.from(select.options || []).filter(option => option.value);
                    if (!requestedLabel) {
                        return options[0]?.value || null;
                    }

                    const requested = normalize(requestedLabel);
                    const requestedCompact = compact(requestedLabel);
                    const exact = options.find(option => normalize(option.textContent) === requested);
                    if (exact) {
                        return exact.value;
                    }

                    const contains = options.find(option =>
                        normalize(option.textContent).includes(requested)
                        || requested.includes(normalize(option.textContent)));
                    if (contains) {
                        return contains.value;
                    }

                    const compactMatch = options.find(option => {
                        const optionCompact = compact(option.textContent);
                        return optionCompact === requestedCompact
                            || optionCompact.includes(requestedCompact)
                            || requestedCompact.includes(optionCompact);
                    });
                    return compactMatch?.value || null;
                }
                """,
                requestedLabel).toString();

        if (optionValue == null || optionValue.isBlank() || "null".equalsIgnoreCase(optionValue)) {
            throw new IllegalStateException(fieldName + " option was not found: " + requestedLabel);
        }

        page.locator(selector).selectOption(optionValue);
    }
}
