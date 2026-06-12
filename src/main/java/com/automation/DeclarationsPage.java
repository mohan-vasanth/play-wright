package com.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DeclarationsPage {

    private static final List<String> KNOWN_JOB_STATUSES = List.of(
            "DRF", "DRAFT",
            "SUB", "SUBMITTED",
            "SNT", "SENT",
            "PMT", "PERMIT ISSUED", "PERMIT_ISSUED",
            "FLD", "FAILED", "FAILURE",
            "REJ", "REJECTED",
            "REG", "REGISTERED");
    private static final List<String> TERMINAL_JOB_STATUSES = List.of(
            "DRF", "PMT", "FLD", "REJ", "REG");
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
        createNewDeclarationDraft(route, "Edit Declaration", "Job Info");
    }

    public void createNewDeclarationDraft(String route, String... expectedVisibleTexts) {
        clickNewDeclarationButton();
        waitForDeclarationEditScreenVisible(route, expectedVisibleTexts);
    }

    public void waitForDeclarationEditScreenVisible(String route, String... expectedVisibleTexts) {
        page.waitForURL("**" + route + "/edit/*");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForFunction("""
                args => {
                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                    const isVisible = element => {
                        if (!element) {
                            return false;
                        }
                        const style = window.getComputedStyle(element);
                        return !!style
                            && style.display !== 'none'
                            && style.visibility !== 'hidden'
                            && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    };

                    const path = window.location.pathname || '';
                    if (!path.includes(args.route + '/edit/')) {
                        return false;
                    }

                    const editableVisible = Array.from(document.querySelectorAll(
                        "input:not([type='hidden']), textarea, select, [role='combobox'], [role='textbox'], button"))
                        .some(isVisible);
                    if (!editableVisible) {
                        return false;
                    }

                    const requiredTexts = (args.expectedVisibleTexts || [])
                        .map(normalize)
                        .filter(Boolean);
                    const pageTexts = Array.from(document.querySelectorAll('body, body *'))
                        .filter(isVisible)
                        .map(element => normalize(element.innerText || element.textContent || ''))
                        .filter(Boolean);

                    return requiredTexts.every(required =>
                        pageTexts.some(text => text.includes(required)));
                }
                """, Map.of(
                "route", route,
                "expectedVisibleTexts", Arrays.asList(expectedVisibleTexts)));

        if (!isDeclarationEditScreenVisible(route, expectedVisibleTexts)) {
            throw new IllegalStateException("Declaration edit screen was not fully visible before data entry: " + route);
        }
    }

    public DeclarationListEntry readDeclarationListEntry(String messageReference) {
        if (messageReference == null || messageReference.isBlank()) {
            return readLatestDeclarationListEntry();
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate("""
                    (messageReference) => {
                        const headerSelector = 'th, [role="columnheader"], [role="gridcell"][aria-colindex], .mat-header-cell, .ag-header-cell, clr-dg-column, .datagrid-column, .datagrid-column-title, .datagrid-head-cell';
                        const rowSelector = 'tr, [role="row"], .mat-row, .ag-row, clr-dg-row, .datagrid-row, .datagrid-row-master';
                        const cellSelector = 'td, [role="cell"], [role="gridcell"], .mat-cell, .ag-cell, clr-dg-cell, .datagrid-cell';
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const upper = value => normalize(value).toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const textOf = element => normalize(element?.innerText || element?.textContent || '');
                        const targetReference = upper(messageReference);
                        const knownStatuses = ['DRF', 'DRAFT', 'SUB', 'SUBMITTED', 'SNT', 'SENT', 'PMT', 'PERMIT ISSUED', 'PERMIT_ISSUED', 'FLD', 'FAILED', 'FAILURE', 'REJ', 'REJECTED', 'REG', 'REGISTERED'];
                        const isStatusValue = value => knownStatuses.includes(upper(value));
                        const isMessageRefValue = value => /^TDX\\d+/i.test(normalize(value));
                        const isDateValue = value => /^\\d{2}-\\d{2}-\\d{4}$/.test(normalize(value));
                        const isJobIdValue = value => /^\\d{3,}$/.test(normalize(value));
                        const isCreatedByValue = value => {
                            const text = normalize(value);
                            if (!text || text === '-' || isStatusValue(text) || isMessageRefValue(text) || isDateValue(text) || isJobIdValue(text)) {
                                return false;
                            }
                            return /^[A-Z][A-Z0-9._ -]*$/i.test(text);
                        };

                        const headerCandidates = Array.from(document.querySelectorAll(headerSelector))
                            .filter(isVisible);
                        const headerTexts = headerCandidates.map(textOf).map(upper);
                        const jobIdIndex = headerTexts.findIndex(text => text === 'JOB ID');
                        const statusIndex = headerTexts.findIndex(text => text === 'STATUS');
                        const messageReferenceIndex = headerTexts.findIndex(text => text === 'MESSAGE REFERENCE');
                        const messageRefIndex = messageReferenceIndex >= 0
                            ? messageReferenceIndex
                            : headerTexts.findIndex(text => text === 'MESSAGE REF');
                        const createdByIndex = headerTexts.findIndex(text => text === 'CREATED BY');

                        const rowCandidates = Array.from(document.querySelectorAll(rowSelector))
                            .filter(row => isVisible(row) && !row.querySelector(headerSelector));

                        const buildEntry = cells => {
                            const rowTexts = cells.map(textOf);
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

                            let declarationNumber = messageRefIndex >= 0 && messageRefIndex < rowTexts.length
                                ? rowTexts[messageRefIndex]
                                : null;
                            if (!isMessageRefValue(declarationNumber)) {
                                declarationNumber = rowTexts.find(isMessageRefValue) || messageReference;
                            }

                            let jobCreatedBy = createdByIndex >= 0 && createdByIndex < rowTexts.length
                                ? rowTexts[createdByIndex]
                                : null;
                            const resolvedStatus = extractStatus(cells);
                            if (!isCreatedByValue(jobCreatedBy)) {
                                const statusCellIndex = rowTexts.findIndex(value => upper(value) === upper(resolvedStatus));
                                if (statusCellIndex > 0 && isCreatedByValue(rowTexts[statusCellIndex - 1])) {
                                    jobCreatedBy = rowTexts[statusCellIndex - 1];
                                } else {
                                    jobCreatedBy = rowTexts.find(isCreatedByValue) || null;
                                }
                            }

                            return {
                                jobId,
                                jobStatus: resolvedStatus,
                                declarationNumber,
                                jobCreatedBy
                            };
                        };

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
                                .find(text => knownStatuses.includes(text)) || null;
                        };

                        for (const row of rowCandidates) {
                            const cells = Array.from(row.querySelectorAll(cellSelector)).filter(isVisible);
                            const rowTexts = cells.map(textOf);
                            const normalizedRowTexts = rowTexts.map(upper);
                            const messageMatches = messageRefIndex >= 0 && messageRefIndex < normalizedRowTexts.length
                                ? normalizedRowTexts[messageRefIndex] === targetReference
                                : normalizedRowTexts.some(text => text === targetReference || text.includes(targetReference));
                            if (!messageMatches) {
                                continue;
                            }
                            return buildEntry(cells);
                        }

                        if (rowCandidates.length > 0) {
                            const latestCells = Array.from(rowCandidates[0].querySelectorAll(cellSelector))
                                .filter(isVisible);
                            if (latestCells.length > 0) {
                                return buildEntry(latestCells);
                            }
                        }
                        return null;
                    }
                    """, messageReference);
            if (result != null) {
                String jobId = stringValue(result.get("jobId"));
                String jobStatus = normalizeJobStatus(stringValue(result.get("jobStatus")));
                String declarationNumber = stringValue(result.get("declarationNumber"));
                String jobCreatedBy = stringValue(result.get("jobCreatedBy"));
                if ((jobId != null && !jobId.isBlank()) || (jobStatus != null && !jobStatus.isBlank())
                        || (declarationNumber != null && !declarationNumber.isBlank())
                        || (jobCreatedBy != null && !jobCreatedBy.isBlank())) {
                    return new DeclarationListEntry(jobId, jobStatus, declarationNumber, jobCreatedBy);
                }
            }
            page.waitForTimeout(1000);
        }

        return readLatestDeclarationListEntry();
    }

    public DeclarationListEntry readLatestDeclarationListEntry() {
        for (int attempt = 0; attempt < 10; attempt++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate("""
                    () => {
                        const headerSelector = 'th, [role="columnheader"], [role="gridcell"][aria-colindex], .mat-header-cell, .ag-header-cell, clr-dg-column, .datagrid-column, .datagrid-column-title, .datagrid-head-cell';
                        const rowSelector = 'tr, [role="row"], .mat-row, .ag-row, clr-dg-row, .datagrid-row, .datagrid-row-master';
                        const cellSelector = 'td, [role="cell"], [role="gridcell"], .mat-cell, .ag-cell, clr-dg-cell, .datagrid-cell';
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const upper = value => normalize(value).toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const textOf = element => normalize(element?.innerText || element?.textContent || '');
                        const knownStatuses = ['DRF', 'DRAFT', 'SUB', 'SUBMITTED', 'SNT', 'SENT', 'PMT', 'PERMIT ISSUED', 'PERMIT_ISSUED', 'FLD', 'FAILED', 'FAILURE', 'REJ', 'REJECTED', 'REG', 'REGISTERED'];
                        const isStatusValue = value => knownStatuses.includes(upper(value));
                        const isMessageRefValue = value => /^TDX\\d+/i.test(normalize(value));
                        const isDateValue = value => /^\\d{2}-\\d{2}-\\d{4}$/.test(normalize(value));
                        const isJobIdValue = value => /^\\d{3,}$/.test(normalize(value));
                        const isCreatedByValue = value => {
                            const text = normalize(value);
                            if (!text || text === '-' || isStatusValue(text) || isMessageRefValue(text) || isDateValue(text) || isJobIdValue(text)) {
                                return false;
                            }
                            return /^[A-Z][A-Z0-9._ -]*$/i.test(text);
                        };

                        const headerCandidates = Array.from(document.querySelectorAll(headerSelector))
                            .filter(isVisible);
                        const headerTexts = headerCandidates.map(textOf).map(upper);
                        const jobIdIndex = headerTexts.findIndex(text => text === 'JOB ID');
                        const statusIndex = headerTexts.findIndex(text => text === 'STATUS');
                        const messageReferenceIndex = headerTexts.findIndex(text => text === 'MESSAGE REFERENCE');
                        const messageRefIndex = messageReferenceIndex >= 0
                            ? messageReferenceIndex
                            : headerTexts.findIndex(text => text === 'MESSAGE REF');
                        const createdByIndex = headerTexts.findIndex(text => text === 'CREATED BY');

                        const row = Array.from(document.querySelectorAll(rowSelector))
                            .find(candidate => isVisible(candidate)
                                && !candidate.querySelector(headerSelector));
                        if (!row) {
                            return null;
                        }

                        const cells = Array.from(row.querySelectorAll(cellSelector)).filter(isVisible);
                        if (cells.length === 0) {
                            return null;
                        }

                        const rowTexts = cells.map(textOf);
                        const normalizedRowTexts = rowTexts.map(upper);
                        let jobId = jobIdIndex >= 0 && jobIdIndex < rowTexts.length ? rowTexts[jobIdIndex] : null;
                        if (!jobId || !/^\\d{3,}$/.test(jobId || '')) {
                            jobId = rowTexts.find(value => /^\\d{3,}$/.test(value || '')) || null;
                        }

                        let jobStatus = statusIndex >= 0 && statusIndex < rowTexts.length ? normalizedRowTexts[statusIndex] : null;
                        if (!jobStatus) {
                            jobStatus = normalizedRowTexts.find(text => knownStatuses.includes(text)) || null;
                        }

                        let declarationNumber = messageRefIndex >= 0 && messageRefIndex < rowTexts.length
                            ? rowTexts[messageRefIndex]
                            : null;
                        if (!isMessageRefValue(declarationNumber)) {
                            declarationNumber = rowTexts.find(isMessageRefValue) || null;
                        }

                        let jobCreatedBy = createdByIndex >= 0 && createdByIndex < rowTexts.length
                            ? rowTexts[createdByIndex]
                            : null;
                        if (!isCreatedByValue(jobCreatedBy)) {
                            const statusCellIndex = rowTexts.findIndex(value => upper(value) === upper(jobStatus));
                            if (statusCellIndex > 0 && isCreatedByValue(rowTexts[statusCellIndex - 1])) {
                                jobCreatedBy = rowTexts[statusCellIndex - 1];
                            } else {
                                jobCreatedBy = rowTexts.find(isCreatedByValue) || null;
                            }
                        }

                        return {
                            jobId,
                            jobStatus,
                            declarationNumber,
                            jobCreatedBy
                        };
                    }
                    """);
            if (result != null) {
                String jobId = stringValue(result.get("jobId"));
                String jobStatus = normalizeJobStatus(stringValue(result.get("jobStatus")));
                String declarationNumber = stringValue(result.get("declarationNumber"));
                String jobCreatedBy = stringValue(result.get("jobCreatedBy"));
                if ((jobId != null && !jobId.isBlank()) || (jobStatus != null && !jobStatus.isBlank())
                        || (declarationNumber != null && !declarationNumber.isBlank())
                        || (jobCreatedBy != null && !jobCreatedBy.isBlank())) {
                    return new DeclarationListEntry(jobId, jobStatus, declarationNumber, jobCreatedBy);
                }
            }
            page.waitForTimeout(1000);
        }
        return new DeclarationListEntry(null, null, null, null);
    }

    public DeclarationListEntry waitForDeclarationCompletion(
            String messageReference,
            String expectedJobId,
            long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        DeclarationListEntry latestMatchingEntry = null;

        while (System.currentTimeMillis() <= deadline) {
            try {
                DeclarationListEntry currentEntry = readDeclarationListEntry(messageReference);
                if (matchesTrackedDeclaration(currentEntry, messageReference, expectedJobId)) {
                    latestMatchingEntry = currentEntry;
                    if (isTerminalJobStatus(currentEntry.jobStatus())) {
                        return currentEntry;
                    }
                }

                refreshDeclarationList();
                page.waitForTimeout(1500);
            } catch (PlaywrightException exception) {
                if (latestMatchingEntry != null) {
                    return latestMatchingEntry;
                }
                throw exception;
            }
        }

        return latestMatchingEntry != null ? latestMatchingEntry : readDeclarationListEntry(messageReference);
    }

    public void refreshDeclarationList() {
        page.reload();
        waitForDeclarationListRefresh();
    }

    public String readLatestJobId() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String jobId = (String) page.evaluate("""
                    () => {
                        const headerSelector = 'th, [role="columnheader"], [role="gridcell"][aria-colindex], .mat-header-cell, .ag-header-cell, clr-dg-column, .datagrid-column, .datagrid-column-title, .datagrid-head-cell';
                        const rowSelector = 'tr, [role="row"], .mat-row, .ag-row, clr-dg-row, .datagrid-row, .datagrid-row-master';
                        const cellSelector = 'td, [role="cell"], [role="gridcell"], .mat-cell, .ag-cell, clr-dg-cell, .datagrid-cell';
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const upper = value => normalize(value).toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const textOf = element => normalize(element?.innerText || element?.textContent || '');

                        const headers = Array.from(document.querySelectorAll(headerSelector));
                        const jobHeader = headers.find(element => upper(textOf(element)) === 'JOB ID');
                        if (jobHeader) {
                            const headerRow = jobHeader.closest('tr, [role="row"], .mat-header-row, .ag-header-row, clr-dg-row, .datagrid-row');
                            const headerCells = headerRow
                                ? Array.from(headerRow.querySelectorAll(`${headerSelector}, ${cellSelector}`)).filter(isVisible)
                                : headers.filter(isVisible);
                            const columnIndex = headerCells.findIndex(cell => cell === jobHeader || upper(textOf(cell)) === 'JOB ID');
                            if (columnIndex >= 0) {
                                const rowCandidates = Array.from(document.querySelectorAll(rowSelector))
                                    .filter(row => isVisible(row) && !row.querySelector(headerSelector));
                                for (const row of rowCandidates) {
                                    const cells = Array.from(row.querySelectorAll(cellSelector)).filter(isVisible);
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

    private String normalizeJobStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        return switch (normalized) {
            case "DRF", "DRAFT" -> "DRF";
            case "SUB", "SUBMITTED" -> "SUB";
            case "SNT", "SENT" -> "SNT";
            case "FLD", "FAILED", "FAILURE" -> "FLD";
            case "REJ", "REJECTED" -> "REJ";
            case "PMT", "PERMIT_ISSUED", "PERMITISSUED" -> "PMT";
            case "REG", "REGISTERED" -> "REG";
            default -> normalized;
        };
    }

    private boolean isTerminalJobStatus(String value) {
        String normalized = normalizeJobStatus(value);
        return normalized != null && TERMINAL_JOB_STATUSES.contains(normalized);
    }

    public boolean hasTerminalJobStatus(String value) {
        return isTerminalJobStatus(value);
    }

    private boolean matchesTrackedDeclaration(
            DeclarationListEntry entry,
            String messageReference,
            String expectedJobId) {
        if (entry == null) {
            return false;
        }

        String normalizedExpectedJobId = normalizeValue(expectedJobId);
        String normalizedExpectedMessageReference = normalizeValue(messageReference);
        String normalizedActualJobId = normalizeValue(entry.jobId());
        String normalizedActualMessageReference = normalizeValue(entry.declarationNumber());

        if (normalizedExpectedJobId != null && normalizedExpectedJobId.equals(normalizedActualJobId)) {
            return true;
        }
        return normalizedExpectedMessageReference == null
                || normalizedExpectedMessageReference.equals(normalizedActualMessageReference);
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private void waitForDeclarationListRefresh() {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            waitForNewDeclarationButton();
        } catch (PlaywrightException ignored) {
        }
        page.waitForTimeout(1000);
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

    private boolean isDeclarationEditScreenVisible(String route, String... expectedVisibleTexts) {
        try {
            return Boolean.TRUE.equals(page.evaluate("""
                    args => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const isVisible = element => {
                            if (!element) {
                                return false;
                            }
                            const style = window.getComputedStyle(element);
                            return !!style
                                && style.display !== 'none'
                                && style.visibility !== 'hidden'
                                && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        };

                        const path = window.location.pathname || '';
                        if (!path.includes(args.route + '/edit/')) {
                            return false;
                        }

                        const requiredTexts = (args.expectedVisibleTexts || [])
                            .map(normalize)
                            .filter(Boolean);
                        const pageTexts = Array.from(document.querySelectorAll('body, body *'))
                            .filter(isVisible)
                            .map(element => normalize(element.innerText || element.textContent || ''))
                            .filter(Boolean);
                        const editableVisible = Array.from(document.querySelectorAll(
                            "input:not([type='hidden']), textarea, select, [role='combobox'], [role='textbox'], button"))
                            .some(isVisible);

                        return editableVisible && requiredTexts.every(required =>
                            pageTexts.some(text => text.includes(required)));
                    }
                    """, Map.of(
                    "route", route,
                    "expectedVisibleTexts", List.of(expectedVisibleTexts))));
        } catch (Exception ignored) {
            return false;
        }
    }

    public record DeclarationListEntry(
            String jobId,
            String jobStatus,
            String declarationNumber,
            String jobCreatedBy) {
    }
}
