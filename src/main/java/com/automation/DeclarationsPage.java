package com.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.util.Map;

public class DeclarationsPage {

    private final Page page;
    private static final String DECLARATIONS_MENU = "text=Declarations";
    private static final String NEW_DECLARATION_BUTTON = "button:has-text('NEW DECLARATION')";
    private static final String IPT_MENU_ITEM = "a:has-text('In-Payment (IPT)')";

    public DeclarationsPage(Page page) {
        this.page = page;
    }

    public void autoAcceptUnsavedChanges() {
        page.evaluate("window.confirm = () => true");
    }

    public void openDeclarationList(String menuLabel, String route) {
        openDeclarationsMenuIfNeeded();
        clickDeclarationMenuItem(menuLabel, route);
        page.waitForURL("**" + route);
        waitForNewDeclarationButton();
    }

    public void createNewDeclarationDraft(String route) {
        clickNewDeclarationButton();
        page.waitForURL("**" + route + "/edit/*");
    }

    public DeclarationListEntry readDeclarationListEntry(String messageReference) {
        if (messageReference == null || messageReference.isBlank()) {
            return new DeclarationListEntry(null, null);
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate("""
                    (messageReference) => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const upper = value => normalize(value).toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const textOf = element => normalize(element?.innerText || element?.textContent || '');
                        const targetReference = upper(messageReference);

                        const headerCandidates = Array.from(document.querySelectorAll('th, [role="columnheader"], .mat-header-cell, .ag-header-cell'))
                            .filter(isVisible);
                        const headerTexts = headerCandidates.map(textOf).map(upper);
                        const jobIdIndex = headerTexts.findIndex(text => text === 'JOB ID');
                        const statusIndex = headerTexts.findIndex(text => text === 'STATUS');
                        const messageReferenceIndex = headerTexts.findIndex(text => text === 'MESSAGE REFERENCE');

                        const rowCandidates = Array.from(document.querySelectorAll('tr, [role="row"], .mat-row, .ag-row'))
                            .filter(row => isVisible(row) && !row.querySelector('th, [role="columnheader"], .mat-header-cell, .ag-header-cell'));

                        const extractStatus = cells => {
                            if (statusIndex >= 0 && statusIndex < cells.length) {
                                const explicitStatus = upper(textOf(cells[statusIndex]));
                                if (explicitStatus) {
                                    return explicitStatus;
                                }
                            }
                            return cells
                                .map(textOf)
                                .map(upper)
                                .find(text => ['PERMIT_ISSUED', 'FAILED', 'DRAFT', 'SUBMITTED'].includes(text)) || null;
                        };

                        for (const row of rowCandidates) {
                            const cells = Array.from(row.querySelectorAll('td, [role="cell"], .mat-cell, .ag-cell')).filter(isVisible);
                            const rowTexts = cells.map(textOf);
                            const normalizedRowTexts = rowTexts.map(upper);
                            const messageMatches = messageReferenceIndex >= 0 && messageReferenceIndex < normalizedRowTexts.length
                                ? normalizedRowTexts[messageReferenceIndex] === targetReference
                                : normalizedRowTexts.some(text => text === targetReference || text.includes(targetReference));
                            if (!messageMatches) {
                                continue;
                            }

                            let jobId = null;
                            if (jobIdIndex >= 0 && jobIdIndex < rowTexts.length) {
                                const candidate = rowTexts[jobIdIndex];
                                if (/^\\d{3,}$/.test(candidate || '')) {
                                    jobId = candidate;
                                }
                            }
                            if (!jobId) {
                                jobId = rowTexts.find(value => /^\\d{3,}$/.test(value || '')) || null;
                            }

                            return {
                                jobId,
                                jobStatus: extractStatus(cells)
                            };
                        }
                        return null;
                    }
                    """, messageReference);
            if (result != null) {
                String jobId = stringValue(result.get("jobId"));
                String jobStatus = stringValue(result.get("jobStatus"));
                if ((jobId != null && !jobId.isBlank()) || (jobStatus != null && !jobStatus.isBlank())) {
                    return new DeclarationListEntry(jobId, jobStatus);
                }
            }
            page.waitForTimeout(1000);
        }

        return new DeclarationListEntry(null, null);
    }

    public String readLatestJobId() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String jobId = (String) page.evaluate("""
                    () => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const upper = value => normalize(value).toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const textOf = element => normalize(element?.innerText || element?.textContent || '');

                        const headers = Array.from(document.querySelectorAll('th, [role="columnheader"], .mat-header-cell, .ag-header-cell'));
                        const jobHeader = headers.find(element => upper(textOf(element)) === 'JOB ID');
                        if (jobHeader) {
                            const headerRow = jobHeader.closest('tr, [role="row"], .mat-header-row, .ag-header-row');
                            const headerCells = headerRow
                                ? Array.from(headerRow.querySelectorAll('th, td, [role="columnheader"], .mat-header-cell, .ag-header-cell')).filter(isVisible)
                                : headers.filter(isVisible);
                            const columnIndex = headerCells.findIndex(cell => cell === jobHeader || upper(textOf(cell)) === 'JOB ID');
                            if (columnIndex >= 0) {
                                const rowCandidates = Array.from(document.querySelectorAll('tr, [role="row"], .mat-row, .ag-row'))
                                    .filter(row => isVisible(row) && !row.querySelector('th, [role="columnheader"], .mat-header-cell, .ag-header-cell'));
                                for (const row of rowCandidates) {
                                    const cells = Array.from(row.querySelectorAll('td, [role="cell"], .mat-cell, .ag-cell')).filter(isVisible);
                                    if (columnIndex < cells.length) {
                                        const value = textOf(cells[columnIndex]);
                                        if (value && /^\\d{3,}$/.test(value)) {
                                            return value;
                                        }
                                    }
                                }
                            }
                        }

                        const links = Array.from(document.querySelectorAll('a, button, [role="link"]'))
                            .filter(isVisible)
                            .map(element => textOf(element))
                            .filter(value => /^\\d{3,}$/.test(value));
                        return links[0] || null;
                    }
                    """);
            if (jobId != null && !jobId.isBlank()) {
                return jobId.trim();
            }
            page.waitForTimeout(1000);
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private void openDeclarationsMenuIfNeeded() {
        if (!page.locator(IPT_MENU_ITEM).first().isVisible()) {
            page.locator(DECLARATIONS_MENU).first().click();
            page.waitForTimeout(500);
        }
    }

    private void clickDeclarationMenuItem(String menuLabel, String route) {
        Locator visibleMenuItem = page.locator("a:visible:has-text('" + menuLabel + "')").first();
        if (visibleMenuItem.count() > 0 && visibleMenuItem.isVisible()) {
            visibleMenuItem.click();
            return;
        }

        Boolean clicked = (Boolean) page.evaluate("""
                ({ menuLabel, route }) => {
                    const normalize = value => (value || "").replace(/\\s+/g, " ").trim().toUpperCase();
                    const normalizedLabel = normalize(menuLabel);
                    const candidates = Array.from(document.querySelectorAll("a"));
                    const target = candidates.find(element => {
                        const href = element.getAttribute("href") || "";
                        const routerLink = element.getAttribute("routerLink") || "";
                        const text = normalize(element.innerText || element.textContent);
                        return href === route || routerLink === route || text.includes(normalizedLabel);
                    });
                    if (!target) {
                        return false;
                    }
                    target.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
                    return true;
                }
                """, Map.of("menuLabel", menuLabel, "route", route));

        if (!Boolean.TRUE.equals(clicked)) {
            throw new IllegalStateException("Unable to open declaration menu item: " + menuLabel);
        }
    }

    private void waitForNewDeclarationButton() {
        Locator button = resolveNewDeclarationButton();
        if (button != null) {
            button.waitFor(new Locator.WaitForOptions().setTimeout(30000));
            return;
        }

        try {
            page.waitForFunction("""
                    () => Array.from(document.querySelectorAll("button, [role='button'], a"))
                            .some(element => ((element.innerText || element.textContent || "").replace(/\\s+/g, " ").trim().toUpperCase()).includes("NEW DECLARATION"))
                    """);
        } catch (PlaywrightException exception) {
            System.out.println("DECLARATION_LIST_URL=" + page.url());
            System.out.println("DECLARATION_LIST_BODY=" + page.locator("body").innerText());
            throw new IllegalStateException("NEW DECLARATION button was not visible on declarations page.", exception);
        }
    }

    private void clickNewDeclarationButton() {
        Locator button = resolveNewDeclarationButton();
        if (button != null && button.isVisible()) {
            button.click();
            return;
        }
        

        Boolean clicked = (Boolean) page.evaluate("""
                () => {
                    const normalize = value => (value || "").replace(/\\s+/g, " ").trim().toUpperCase();
                    const target = Array.from(document.querySelectorAll("button, [role='button'], a"))
                        .find(element => normalize(element.innerText || element.textContent).includes("NEW DECLARATION"));
                    if (!target) {
                        return false;
                    }
                    target.scrollIntoView({ block: "center" });
                    target.click();
                    return true;
                }
                """);

        if (!Boolean.TRUE.equals(clicked)) {
            throw new IllegalStateException("Unable to click NEW DECLARATION button.");
        }
    }

    private Locator resolveNewDeclarationButton() {
        String[] selectors = new String[] {
                "button:has-text('NEW DECLARATION')",
                "button:has-text('New Declaration')",
                "[role='button']:has-text('NEW DECLARATION')",
                "[role='button']:has-text('New Declaration')",
                "text=NEW DECLARATION"
        };

        for (String selector : selectors) {
            Locator locator = page.locator(selector).first();
            if (locator.count() > 0) {
                return locator;
            }
        }

        return null;
    }

    public record DeclarationListEntry(String jobId, String jobStatus) {
    }
}
