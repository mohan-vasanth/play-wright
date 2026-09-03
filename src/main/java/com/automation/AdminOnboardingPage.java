package com.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Page object for the admin Management > Onboard User workflow. */
public final class AdminOnboardingPage {

    private static final String INTERACTIVE_CONTROLS = "a, button, [role='button'], [routerlink], [mat-menu-item]";
    private static final String EDITABLE_CONTROLS = "input:not([type='hidden']), textarea, select, [contenteditable='true']";

    private final Page page;

    public AdminOnboardingPage(Page page) {
        this.page = page;
    }

    public void openOnboardUserForm() {
        openUsersPage();
        clickVisibleControl("Onboard User", "Onboard", "Add User", "Create User");
        page.locator("input[placeholder='Enter username']").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    public void openUsersPage() {
        if (!hasVisibleInteractiveControl("Users")) {
            clickVisibleControl("Management");
        }
        if (!page.locator("input[name='quickFilterUsername']").isVisible()) {
            clickVisibleControl("Users");
        }
        page.locator("input[name='quickFilterUsername']").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    public boolean userIsListed(String username) {
        Locator filter = page.locator("input[name='quickFilterUsername']");
        filter.fill(username);
        try {
            page.waitForFunction("""
                    username => (document.body?.innerText || '').toUpperCase().includes(username.toUpperCase())
                    """, username, new Page.WaitForFunctionOptions().setTimeout(10_000));
            return true;
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    public void createUser(LoadTestUser user) {
        fillRequired("Username", user.username());
        fillRequired("Password", user.password());
        fillRequired("Confirm Password", user.password());
        fillRequired("Email", user.email());
        String roleLabel = user.organizationRole().toUpperCase(Locale.ROOT);
        selectCheckboxRequired(roleLabel, 0, "organization role");
        openForwarderAssignment();
        chooseRequired("Forwarder", user.forwarder());
        chooseRequired("Department", user.department());
        selectCheckboxRequired(roleLabel, 1, "forwarder scoped role");

        submitAndVerifyCreateRequest(user.username());
    }

    public String visibleFormInventory() {
        return page.locator("form, [role='dialog'], body").first().evaluate("""
                root => Array.from(root.querySelectorAll('label, input, textarea, select, button, [role="button"]'))
                    .filter(element => !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length))
                    .map(element => ({
                        tag: element.tagName.toLowerCase(),
                        text: (element.innerText || element.textContent || '').replace(/\\s+/g, ' ').trim(),
                        name: element.getAttribute('name'),
                        id: element.id,
                        formControlName: element.getAttribute('formcontrolname'),
                        type: element.getAttribute('type'),
                        placeholder: element.getAttribute('placeholder'),
                        title: element.getAttribute('title'),
                        ariaLabel: element.getAttribute('aria-label'),
                        routerLink: element.getAttribute('routerlink'),
                        href: element.getAttribute('href'),
                        className: element.getAttribute('class')
                    }))
                    .map(item => JSON.stringify(item))
                    .join('\\n')
                """).toString();
    }

    public void openForwarderAssignment() {
        clickVisibleControl("Add Forwarder");
    }

    private void fillRequired(String label, String value) {
        Locator field = findField(label);
        if (field == null) {
            throw new IllegalStateException("Onboarding field was not found: " + label);
        }
        field.fill(value);
        String rendered = field.inputValue();
        if (!normalize(rendered).equals(normalize(value))) {
            field.fill(value);
            rendered = field.inputValue();
        }
        if (!normalize(rendered).equals(normalize(value))) {
            throw new IllegalStateException("Onboarding field did not retain its value: " + label);
        }
    }

    private void chooseRequired(String label, String requestedValue) {
        Locator field = findField(label);
        if (field == null) {
            throw new IllegalStateException("Onboarding option field was not found: " + label);
        }

        if ("select".equalsIgnoreCase(field.evaluate("element => element.tagName" ).toString())) {
            String optionValue = waitForOptionValue(field, label, requestedValue);
            field.selectOption(optionValue);
        } else {
            field.click();
            Locator option = page.getByText(requestedValue, new Page.GetByTextOptions().setExact(true));
            option.last().click();
        }

        String rendered = field.evaluate("""
                element => element.tagName === 'SELECT'
                    ? element.options[element.selectedIndex]?.textContent || ''
                    : element.value || element.textContent || ''
                """).toString();
        if (!normalize(rendered).contains(normalize(requestedValue))) {
            throw new IllegalStateException("Onboarding option did not retain its value for " + label + ": " + requestedValue);
        }
    }

    private String waitForOptionValue(Locator field, String label, String requestedValue) {
        for (int attempt = 0; attempt < 40; attempt++) {
            Object result = field.evaluate("""
                    (select, requested) => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const wanted = normalize(requested);
                        const option = Array.from(select.options || []).find(candidate =>
                            normalize(candidate.textContent) === wanted || normalize(candidate.textContent).includes(wanted));
                        return option ? option.value : null;
                    }
                    """, requestedValue);
            if (result != null && !result.toString().isBlank()) {
                return result.toString();
            }
            page.waitForTimeout(250);
        }
        throw new IllegalStateException("Onboarding option was not found for " + label + ": " + requestedValue);
    }

    private void selectCheckboxRequired(String label, int occurrence, String scope) {
        Locator checkbox = page.getByLabel(label, new Page.GetByLabelOptions().setExact(true));
        if (checkbox.count() <= occurrence || !checkbox.nth(occurrence).isVisible()) {
            throw new IllegalStateException("Onboarding " + scope + " checkbox was not found: " + label);
        }
        checkbox.nth(occurrence).check();
        if (!checkbox.nth(occurrence).isChecked()) {
            throw new IllegalStateException("Onboarding " + scope + " checkbox was not selected: " + label);
        }
    }

    private boolean hasVisibleInteractiveControl(String label) {
        Locator controls = page.locator(INTERACTIVE_CONTROLS);
        for (int index = 0; index < controls.count(); index++) {
            Locator candidate = controls.nth(index);
            if (candidate.isVisible() && normalize(candidate.innerText()).equals(normalize(label))) {
                return true;
            }
        }
        return false;
    }

    private Locator findField(String label) {
        Locator accessibleLabel = page.getByLabel(label, new Page.GetByLabelOptions().setExact(true));
        for (int index = 0; index < accessibleLabel.count(); index++) {
            Locator candidate = accessibleLabel.nth(index);
            if (candidate.isVisible() && candidate.isEditable()) {
                return candidate;
            }
        }

        String normalizedLabel = normalize(label);
        for (Locator candidate : visibleEditableControls()) {
            String metadata = candidate.evaluate("""
                    element => [element.getAttribute('name'), element.id, element.getAttribute('formcontrolname'),
                        element.getAttribute('placeholder'), element.getAttribute('aria-label')]
                        .filter(Boolean).join(' ')
                    """).toString();
            if (normalize(metadata).contains(normalizedLabel.replace(" ", ""))
                    || normalize(metadata).contains(normalizedLabel)) {
                return candidate;
            }
        }
        return null;
    }

    private List<Locator> visibleEditableControls() {
        List<Locator> visible = new ArrayList<>();
        Locator controls = page.locator(EDITABLE_CONTROLS);
        for (int index = 0; index < controls.count(); index++) {
            Locator candidate = controls.nth(index);
            if (candidate.isVisible() && candidate.isEditable()) {
                visible.add(candidate);
            }
        }
        return visible;
    }

    private void clickVisibleControl(String... labels) {
        for (String label : labels) {
            Locator exact = page.getByText(label, new Page.GetByTextOptions().setExact(true));
            for (int index = exact.count() - 1; index >= 0; index--) {
                Locator candidate = exact.nth(index);
                if (candidate.isVisible()) {
                    candidate.click();
                    return;
                }
            }
        }

        Locator controls = page.locator(INTERACTIVE_CONTROLS);
        for (int index = 0; index < controls.count(); index++) {
            Locator candidate = controls.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            String text = normalize(candidate.innerText());
            for (String label : labels) {
                if (text.contains(normalize(label))) {
                    candidate.click();
                    return;
                }
            }
        }
        throw new IllegalStateException("Admin control was not found: " + String.join(" / ", labels));
    }

    private void submitAndVerifyCreateRequest(String username) {
        List<Response> writeResponses = new CopyOnWriteArrayList<>();
        List<String> writeRequests = new CopyOnWriteArrayList<>();
        page.onRequest(request -> {
            if (isWriteMethod(request.method())) {
                writeRequests.add(request.method() + " " + request.url());
            }
        });
        page.onResponse(response -> {
            if (isWriteMethod(response.request().method())) {
                writeResponses.add(response);
            }
        });

        Locator createButton = page.locator("button[type='submit']");
        createButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        createButton.click();
        for (int attempt = 0; attempt < 40 && writeResponses.isEmpty(); attempt++) {
            page.waitForTimeout(250);
        }
        if (writeRequests.isEmpty()) {
            throw new IllegalStateException(
                    "No user-creation request was sent for " + username + ". " + submissionDiagnostics());
        }
        if (writeResponses.isEmpty()) {
            throw new IllegalStateException(
                    "User-creation request did not receive a response for " + username + ": " + writeRequests);
        }

        Response response = writeResponses.get(writeResponses.size() - 1);
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException(
                    "User creation request failed for " + username + " with HTTP " + response.status()
                            + " at " + response.url());
        }
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private String submissionDiagnostics() {
        return page.locator("body").evaluate("""
                body => {
                    const submit = Array.from(body.querySelectorAll('button[type="submit"]')).find(button =>
                        !!(button.offsetWidth || button.offsetHeight || button.getClientRects().length));
                    const values = Array.from(body.querySelectorAll('input, select')).map(element => ({
                        label: element.labels?.[0]?.textContent?.trim() || '',
                        value: element.type === 'password' ? '[redacted]' : element.value,
                        checked: element.type === 'checkbox' ? element.checked : undefined,
                        valid: element.checkValidity?.()
                    }));
                    return JSON.stringify({ submitDisabled: submit?.disabled, values, text: body.innerText });
                }
                """).toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toUpperCase(Locale.ROOT);
    }

    public record LoadTestUser(
            String username,
            String password,
            String email,
            String organizationRole,
            String forwarder,
            String department) {
    }
}
