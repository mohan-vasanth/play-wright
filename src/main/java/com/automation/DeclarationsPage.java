package com.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private static final String NEW_DECLARATION_LABEL = "NEW DECLARATION";

    public DeclarationsPage(Page page) {
        this.page = page;
    }

    public String readCurrentJobIdFromUrl() {
        try {
            String url = page.url();
            if (url == null) {
                return null;
            }
            int editIndex = url.lastIndexOf("/edit/");
            if (editIndex < 0) {
                return null;
            }
            String afterEdit = url.substring(editIndex + 6);
            String candidate = afterEdit.split("[/?#]")[0].replaceAll("[^0-9]", "");
            return candidate.isEmpty() ? null : candidate;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void autoAcceptUnsavedChanges() {
        page.evaluate("window.confirm = () => true");
    }

    public void openDeclarationList(String menuLabel, String route) {
        openDeclarationsMenuIfNeeded(menuLabel);
        clickDeclarationMenuItem(menuLabel, route);
        page.waitForURL("**" + route);
        waitForDeclarationActionButton(route);
    }

    public void createNewDeclarationDraft(String route) {
        createNewDeclarationDraft(route, "Edit Declaration", "Job Info");
    }

    public void createNewDeclarationDraft(String route, String... expectedVisibleTexts) {
        clickNewDeclarationButton(route);
        waitForDeclarationEditScreenVisible(route, expectedVisibleTexts);
    }

    public void waitForDeclarationEditScreenVisible(String route, String... expectedVisibleTexts) {
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
                    const route = args.route || '';
                    const onEditorRoute = path.includes(route + '/edit/')
                        || path === route + '/create'
                        || path.endsWith(route + '/create');
                    if (!onEditorRoute) {
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
            Map<String, Object> result;
            try {
            @SuppressWarnings("unchecked")
            Map<String, Object> evaluated = (Map<String, Object>) page.evaluate("""
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
                        const isPermitLikeValue = value => {
                            const text = normalize(value);
                            if (!text || text === '-' || text === '--' || isDateValue(text) || isJobIdValue(text) || isMessageRefValue(text) || isStatusValue(text)) {
                                return false;
                            }
                            return /^[A-Z]{1,4}\\d[A-Z0-9-]{5,}$/i.test(text);
                        };
                        const isCreatedByValue = value => {
                            const text = normalize(value);
                            if (!text || text === '-' || isStatusValue(text) || isMessageRefValue(text) || isDateValue(text) || isJobIdValue(text)) {
                                return false;
                            }
                            return /^[A-Z][A-Z0-9._ -]*$/i.test(text);
                        };
                        const matchesHeader = (text, labels) => labels.some(label => text === label || text.startsWith(label));

                        const headerCandidates = Array.from(document.querySelectorAll(headerSelector))
                            .filter(isVisible);
                        const headerTexts = headerCandidates.map(textOf).map(upper);
                        const jobIdIndex = headerTexts.findIndex(text => text === 'JOB ID');
                        const statusIndex = headerTexts.findIndex(text => text === 'STATUS');
                        const messageReferenceIndex = headerTexts.findIndex(text => matchesHeader(text, ['MESSAGE REFERENCE']));
                        const messageRefIndex = messageReferenceIndex >= 0
                            ? messageReferenceIndex
                            : headerTexts.findIndex(text => matchesHeader(text, ['MESSAGE REF']));
                        const createdByIndex = headerTexts.findIndex(text => matchesHeader(text, ['CREATED BY']));
                        const permitNumberIndex = headerTexts.findIndex(text => matchesHeader(text, [
                            'PERMIT NUMBER',
                            'PERMIT NO.',
                            'PERMIT NO',
                            'PMT NUMBER',
                            'PMT NO.',
                            'PMT NO']));

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

                            let permitNumber = permitNumberIndex >= 0 && permitNumberIndex < rowTexts.length
                                ? rowTexts[permitNumberIndex]
                                : null;
                            if (!permitNumber || permitNumber === '-' || permitNumber === '--') {
                                const statusCellIndex = rowTexts.findIndex(value => upper(value) === upper(resolvedStatus));
                                if (statusCellIndex >= 0 && statusCellIndex + 1 < rowTexts.length) {
                                    const adjacentValue = rowTexts[statusCellIndex + 1];
                                    permitNumber = isPermitLikeValue(adjacentValue) ? adjacentValue : null;
                                } else {
                                    permitNumber = null;
                                }
                            }
                            if (!permitNumber || permitNumber === '-' || permitNumber === '--') {
                                permitNumber = rowTexts.find(isPermitLikeValue) || null;
                            }

                            return {
                                jobId,
                                jobStatus: resolvedStatus,
                                declarationNumber,
                                jobCreatedBy,
                                permitNumber
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

                        return null;
                    }
                    """, messageReference);
                result = evaluated;
            } catch (PlaywrightException exception) {
                result = null;
            }
            if (result != null) {
                String jobId = stringValue(result.get("jobId"));
                String jobStatus = normalizeJobStatus(stringValue(result.get("jobStatus")));
                String declarationNumber = stringValue(result.get("declarationNumber"));
                String jobCreatedBy = stringValue(result.get("jobCreatedBy"));
                String permitNumber = stringValue(result.get("permitNumber"));
                if ((jobId != null && !jobId.isBlank()) || (jobStatus != null && !jobStatus.isBlank())
                        || (declarationNumber != null && !declarationNumber.isBlank())
                        || (jobCreatedBy != null && !jobCreatedBy.isBlank())
                        || (permitNumber != null && !permitNumber.isBlank())) {
                    return new DeclarationListEntry(jobId, jobStatus, declarationNumber, jobCreatedBy, permitNumber);
                }
            }
            page.waitForTimeout(1000);
        }

        return null;
    }

    public DeclarationListEntry readDeclarationListEntryByJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return readLatestDeclarationListEntry();
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            Map<String, Object> result;
            try {
            @SuppressWarnings("unchecked")
            Map<String, Object> evaluated = (Map<String, Object>) page.evaluate("""
                    (jobId) => {
                        const headerSelector = 'th, [role="columnheader"], [role="gridcell"][aria-colindex], .mat-header-cell, .ag-header-cell, clr-dg-column, .datagrid-column, .datagrid-column-title, .datagrid-head-cell';
                        const rowSelector = 'tr, [role="row"], .mat-row, .ag-row, clr-dg-row, .datagrid-row, .datagrid-row-master';
                        const cellSelector = 'td, [role="cell"], [role="gridcell"], .mat-cell, .ag-cell, clr-dg-cell, .datagrid-cell';
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const upper = value => normalize(value).toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const textOf = element => normalize(element?.innerText || element?.textContent || '');
                        const targetJobId = normalize(jobId);
                        const knownStatuses = ['DRF', 'DRAFT', 'SUB', 'SUBMITTED', 'SNT', 'SENT', 'PMT', 'PERMIT ISSUED', 'PERMIT_ISSUED', 'FLD', 'FAILED', 'FAILURE', 'REJ', 'REJECTED', 'REG', 'REGISTERED'];
                        const isStatusValue = value => knownStatuses.includes(upper(value));
                        const isMessageRefValue = value => /^TDX\\d+/i.test(normalize(value));
                        const isDateValue = value => /^\\d{2}-\\d{2}-\\d{4}$/.test(normalize(value));
                        const isJobIdValue = value => /^\\d{3,}$/.test(normalize(value));
                        const isPermitLikeValue = value => {
                            const text = normalize(value);
                            if (!text || text === '-' || text === '--' || isDateValue(text) || isJobIdValue(text) || isMessageRefValue(text) || isStatusValue(text)) {
                                return false;
                            }
                            return /^[A-Z]{1,4}\\d[A-Z0-9-]{5,}$/i.test(text);
                        };
                        const isCreatedByValue = value => {
                            const text = normalize(value);
                            if (!text || text === '-' || isStatusValue(text) || isMessageRefValue(text) || isDateValue(text) || isJobIdValue(text)) {
                                return false;
                            }
                            return /^[A-Z][A-Z0-9._ -]*$/i.test(text);
                        };
                        const matchesHeader = (text, labels) => labels.some(label => text === label || text.startsWith(label));

                        const headerCandidates = Array.from(document.querySelectorAll(headerSelector)).filter(isVisible);
                        const headerTexts = headerCandidates.map(textOf).map(upper);
                        const jobIdIndex = headerTexts.findIndex(text => text === 'JOB ID');
                        const statusIndex = headerTexts.findIndex(text => text === 'STATUS');
                        const messageReferenceIndex = headerTexts.findIndex(text => matchesHeader(text, ['MESSAGE REFERENCE']));
                        const messageRefIndex = messageReferenceIndex >= 0
                            ? messageReferenceIndex
                            : headerTexts.findIndex(text => matchesHeader(text, ['MESSAGE REF']));
                        const createdByIndex = headerTexts.findIndex(text => matchesHeader(text, ['CREATED BY']));
                        const permitNumberIndex = headerTexts.findIndex(text => matchesHeader(text, [
                            'PERMIT NUMBER',
                            'PERMIT NO.',
                            'PERMIT NO',
                            'PMT NUMBER',
                            'PMT NO.',
                            'PMT NO']));

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

                        const rowCandidates = Array.from(document.querySelectorAll(rowSelector))
                            .filter(row => isVisible(row) && !row.querySelector(headerSelector));

                        for (const row of rowCandidates) {
                            const cells = Array.from(row.querySelectorAll(cellSelector)).filter(isVisible);
                            const rowTexts = cells.map(textOf);
                            const normalizedRowTexts = rowTexts.map(upper);
                            const candidateJobId = jobIdIndex >= 0 && jobIdIndex < rowTexts.length
                                ? rowTexts[jobIdIndex]
                                : (rowTexts.find(value => /^\\d{3,}$/.test(value || '')) || null);
                            if (normalize(candidateJobId) !== targetJobId) {
                                continue;
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
                            const resolvedStatus = extractStatus(cells);
                            if (!isCreatedByValue(jobCreatedBy)) {
                                const statusCellIndex = rowTexts.findIndex(value => upper(value) === upper(resolvedStatus));
                                if (statusCellIndex > 0 && isCreatedByValue(rowTexts[statusCellIndex - 1])) {
                                    jobCreatedBy = rowTexts[statusCellIndex - 1];
                                } else {
                                    jobCreatedBy = rowTexts.find(isCreatedByValue) || null;
                                }
                            }

                            let permitNumber = permitNumberIndex >= 0 && permitNumberIndex < rowTexts.length
                                ? rowTexts[permitNumberIndex]
                                : null;
                            if (!permitNumber || permitNumber === '-' || permitNumber === '--') {
                                const statusCellIndex = rowTexts.findIndex(value => upper(value) === upper(resolvedStatus));
                                if (statusCellIndex >= 0 && statusCellIndex + 1 < rowTexts.length) {
                                    const adjacentValue = rowTexts[statusCellIndex + 1];
                                    permitNumber = isPermitLikeValue(adjacentValue) ? adjacentValue : null;
                                } else {
                                    permitNumber = null;
                                }
                            }
                            if (!permitNumber || permitNumber === '-' || permitNumber === '--') {
                                permitNumber = rowTexts.find(isPermitLikeValue) || null;
                            }

                            return {
                                jobId: candidateJobId,
                                jobStatus: resolvedStatus,
                                declarationNumber,
                                jobCreatedBy,
                                permitNumber
                            };
                        }
                        return null;
                    }
                    """, jobId);
                result = evaluated;
            } catch (PlaywrightException exception) {
                result = null;
            }

            if (result != null) {
                String resolvedJobId = stringValue(result.get("jobId"));
                String jobStatus = normalizeJobStatus(stringValue(result.get("jobStatus")));
                String declarationNumber = stringValue(result.get("declarationNumber"));
                String jobCreatedBy = stringValue(result.get("jobCreatedBy"));
                String permitNumber = stringValue(result.get("permitNumber"));
                if ((resolvedJobId != null && !resolvedJobId.isBlank()) || (jobStatus != null && !jobStatus.isBlank())
                        || (declarationNumber != null && !declarationNumber.isBlank())
                        || (jobCreatedBy != null && !jobCreatedBy.isBlank())
                        || (permitNumber != null && !permitNumber.isBlank())) {
                    return new DeclarationListEntry(resolvedJobId, jobStatus, declarationNumber, jobCreatedBy, permitNumber);
                }
            }
            page.waitForTimeout(1000);
        }
        return null;
    }

    public DeclarationListEntry readLatestDeclarationListEntry() {
        for (int attempt = 0; attempt < 10; attempt++) {
            Map<String, Object> result;
            try {
            @SuppressWarnings("unchecked")
            Map<String, Object> evaluated = (Map<String, Object>) page.evaluate("""
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
                        const isPermitLikeValue = value => {
                            const text = normalize(value);
                            if (!text || text === '-' || text === '--' || isDateValue(text) || isJobIdValue(text) || isMessageRefValue(text) || isStatusValue(text)) {
                                return false;
                            }
                            return /^[A-Z]{1,4}\\d[A-Z0-9-]{5,}$/i.test(text);
                        };
                        const isCreatedByValue = value => {
                            const text = normalize(value);
                            if (!text || text === '-' || isStatusValue(text) || isMessageRefValue(text) || isDateValue(text) || isJobIdValue(text)) {
                                return false;
                            }
                            return /^[A-Z][A-Z0-9._ -]*$/i.test(text);
                        };
                        const matchesHeader = (text, labels) => labels.some(label => text === label || text.startsWith(label));

                        const headerCandidates = Array.from(document.querySelectorAll(headerSelector))
                            .filter(isVisible);
                        const headerTexts = headerCandidates.map(textOf).map(upper);
                        const jobIdIndex = headerTexts.findIndex(text => text === 'JOB ID');
                        const statusIndex = headerTexts.findIndex(text => text === 'STATUS');
                        const messageReferenceIndex = headerTexts.findIndex(text => matchesHeader(text, ['MESSAGE REFERENCE']));
                        const messageRefIndex = messageReferenceIndex >= 0
                            ? messageReferenceIndex
                            : headerTexts.findIndex(text => matchesHeader(text, ['MESSAGE REF']));
                        const createdByIndex = headerTexts.findIndex(text => matchesHeader(text, ['CREATED BY']));
                        const permitNumberIndex = headerTexts.findIndex(text => matchesHeader(text, [
                            'PERMIT NUMBER',
                            'PERMIT NO.',
                            'PERMIT NO',
                            'PMT NUMBER',
                            'PMT NO.',
                            'PMT NO']));

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

                        let permitNumber = permitNumberIndex >= 0 && permitNumberIndex < rowTexts.length
                            ? rowTexts[permitNumberIndex]
                            : null;
                        if (!permitNumber || permitNumber === '-' || permitNumber === '--') {
                            const statusCellIndex = rowTexts.findIndex(value => upper(value) === upper(jobStatus));
                            if (statusCellIndex >= 0 && statusCellIndex + 1 < rowTexts.length) {
                                const adjacentValue = rowTexts[statusCellIndex + 1];
                                permitNumber = isPermitLikeValue(adjacentValue) ? adjacentValue : null;
                            } else {
                                permitNumber = null;
                            }
                        }
                        if (!permitNumber || permitNumber === '-' || permitNumber === '--') {
                            permitNumber = rowTexts.find(isPermitLikeValue) || null;
                        }

                        return {
                            jobId,
                            jobStatus,
                            declarationNumber,
                            jobCreatedBy,
                            permitNumber
                        };
                    }
                    """);
                result = evaluated;
            } catch (PlaywrightException exception) {
                result = null;
            }
            if (result != null) {
                String jobId = stringValue(result.get("jobId"));
                String jobStatus = normalizeJobStatus(stringValue(result.get("jobStatus")));
                String declarationNumber = stringValue(result.get("declarationNumber"));
                String jobCreatedBy = stringValue(result.get("jobCreatedBy"));
                String permitNumber = stringValue(result.get("permitNumber"));
                if ((jobId != null && !jobId.isBlank()) || (jobStatus != null && !jobStatus.isBlank())
                        || (declarationNumber != null && !declarationNumber.isBlank())
                        || (jobCreatedBy != null && !jobCreatedBy.isBlank())
                        || (permitNumber != null && !permitNumber.isBlank())) {
                    return new DeclarationListEntry(jobId, jobStatus, declarationNumber, jobCreatedBy, permitNumber);
                }
            }
            page.waitForTimeout(1000);
        }
        return new DeclarationListEntry(null, null, null, null, null);
    }

    public void openDeclarationView(String messageReference) {
        if (messageReference == null || messageReference.isBlank()) {
            throw new IllegalArgumentException("Message reference is required to open declaration view.");
        }

        Boolean clicked = (Boolean) page.evaluate("""
                messageReference => {
                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                    const upper = value => normalize(value).toUpperCase();
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
                    const textOf = element => normalize(element?.innerText || element?.textContent || '');
                    const targetReference = upper(messageReference);
                    const headerSelector = 'th, [role="columnheader"], [role="gridcell"][aria-colindex], .mat-header-cell, .ag-header-cell, clr-dg-column, .datagrid-column, .datagrid-column-title, .datagrid-head-cell';
                    const rowSelector = 'tr, [role="row"], .mat-row, .ag-row, clr-dg-row, .datagrid-row, .datagrid-row-master';
                    const cellSelector = 'td, [role="cell"], [role="gridcell"], .mat-cell, .ag-cell, clr-dg-cell, .datagrid-cell';
                    const iconMatch = element => {
                        const className = upper(element.getAttribute('class'));
                        return className.includes('EYE')
                            || className.includes('VIEW')
                            || className.includes('VISIBILITY');
                    };

                    const actionTarget = row => {
                        const actionElements = Array.from(row.querySelectorAll('a, button, [role="button"]'))
                            .filter(isVisible);
                        const prioritized = actionElements.find(element => {
                            const text = upper(textOf(element));
                            const ariaLabel = upper(element.getAttribute('aria-label'));
                            const title = upper(element.getAttribute('title'));
                            return text.includes('VIEW')
                                || ariaLabel.includes('VIEW')
                                || ariaLabel.includes('OPEN')
                                || title.includes('VIEW')
                                || title.includes('OPEN')
                                || Array.from(element.querySelectorAll('*')).some(iconMatch)
                                || iconMatch(element);
                        });
                        return prioritized || actionElements[0] || null;
                    };

                    const rows = Array.from(document.querySelectorAll(rowSelector))
                        .filter(row => isVisible(row) && !row.querySelector(headerSelector));
                    for (const row of rows) {
                        const cells = Array.from(row.querySelectorAll(cellSelector)).filter(isVisible);
                        const texts = cells.map(textOf).map(upper);
                        const matches = texts.some(text => text === targetReference || text.includes(targetReference));
                        if (!matches) {
                            continue;
                        }
                        const target = actionTarget(row);
                        if (!target) {
                            return false;
                        }
                        target.scrollIntoView({ block: 'center' });
                        target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                        return true;
                    }
                    return false;
                }
                """, messageReference);

        if (!Boolean.TRUE.equals(clicked)) {
            throw new IllegalStateException("Unable to open declaration view for message reference: " + messageReference);
        }

        waitForDeclarationViewScreenVisible(messageReference);
    }

    public void openDeclarationViewByJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("Job ID is required to open declaration view.");
        }

        Boolean clicked = (Boolean) page.evaluate("""
                jobId => {
                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                    const upper = value => normalize(value).toUpperCase();
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
                    const textOf = element => normalize(element?.innerText || element?.textContent || '');
                    const targetJobId = normalize(jobId);
                    const headerSelector = 'th, [role="columnheader"], [role="gridcell"][aria-colindex], .mat-header-cell, .ag-header-cell, clr-dg-column, .datagrid-column, .datagrid-column-title, .datagrid-head-cell';
                    const rowSelector = 'tr, [role="row"], .mat-row, .ag-row, clr-dg-row, .datagrid-row, .datagrid-row-master';
                    const cellSelector = 'td, [role="cell"], [role="gridcell"], .mat-cell, .ag-cell, clr-dg-cell, .datagrid-cell';
                    const iconMatch = element => {
                        const className = upper(element.getAttribute('class'));
                        return className.includes('EYE')
                            || className.includes('VIEW')
                            || className.includes('VISIBILITY');
                    };

                    const actionTarget = row => {
                        const actionElements = Array.from(row.querySelectorAll('a, button, [role="button"]'))
                            .filter(isVisible);
                        return actionElements.find(element => iconMatch(element)
                            || upper(textOf(element)).includes('VIEW')
                            || upper(element.getAttribute('aria-label') || '').includes('VIEW'))
                            || actionElements[0]
                            || null;
                    };

                    const rowCandidates = Array.from(document.querySelectorAll(rowSelector))
                        .filter(row => isVisible(row) && !row.querySelector(headerSelector));
                    for (const row of rowCandidates) {
                        const cells = Array.from(row.querySelectorAll(cellSelector)).filter(isVisible);
                        const rowTexts = cells.map(textOf);
                        const candidateJobId = rowTexts.find(value => /^\\d{3,}$/.test(value || ''));
                        if (normalize(candidateJobId) !== targetJobId) {
                            continue;
                        }
                        const target = actionTarget(row);
                        if (!target) {
                            return false;
                        }
                        target.scrollIntoView({ block: 'center' });
                        target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                        return true;
                    }
                    return false;
                }
                """, jobId);

        if (!Boolean.TRUE.equals(clicked)) {
            throw new IllegalStateException("Unable to open declaration view for job ID: " + jobId);
        }

        waitForDeclarationViewScreenVisible(null);
    }

    public DeclarationResponseDetails readDeclarationResponseDetails(String messageReference) {
        openDeclarationView(messageReference);
        return readCurrentResponseDetails();
    }

    public DeclarationResponseDetails readCurrentResponseDetails() {
        openResponseTabIfPresent();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(500);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) page.evaluate("""
                () => {
                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                    const upper = value => normalize(value).toUpperCase();
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
                    const textOf = element => normalize(element?.innerText || element?.textContent || '');
                    const pickLongest = values => values
                        .filter(Boolean)
                        .sort((left, right) => right.length - left.length)[0] || null;
                    const readLabeledValue = labels => {
                        const normalizedLabels = labels.map(upper);
                        const labelElements = Array.from(document.querySelectorAll('label, dt, th, td, div, span, p, strong'))
                            .filter(isVisible);
                        for (const element of labelElements) {
                            const labelText = upper(textOf(element));
                            if (!normalizedLabels.some(label => labelText === label || labelText.startsWith(label + ':'))) {
                                continue;
                            }
                            const nextText = normalize(element.nextElementSibling?.innerText || element.nextElementSibling?.textContent || '');
                            if (nextText && nextText !== '-' && nextText !== '--') {
                                return nextText;
                            }
                            const parentText = normalize(element.parentElement?.innerText || element.parentElement?.textContent || '');
                            if (parentText) {
                                const pieces = parentText.split(/\\s{2,}|\\n/).map(normalize).filter(Boolean);
                                const labelIndex = pieces.findIndex(piece => {
                                    const normalizedPiece = upper(piece);
                                    return normalizedLabels.some(label => normalizedPiece === label || normalizedPiece.startsWith(label + ':'));
                                });
                                if (labelIndex >= 0 && labelIndex + 1 < pieces.length) {
                                    const candidate = pieces[labelIndex + 1];
                                    if (candidate && candidate !== '-' && candidate !== '--') {
                                        return candidate;
                                    }
                                }
                            }
                        }
                        return null;
                    };
                    const extractDateTime = value => {
                        const normalized = normalize(value);
                        if (!normalized) {
                            return null;
                        }
                        const match = normalized.match(/\\b\\d{2}\\/\\d{2}\\/\\d{4}(?:\\s+\\d{2}:\\d{2}(?::\\d{2})?\\s*[A-Z]{0,4})?\\b/i)
                            || normalized.match(/\\b\\d{2}-\\d{2}-\\d{4}(?:\\s+\\d{2}:\\d{2}(?::\\d{2})?\\s*[A-Z]{0,4})?\\b/i)
                            || normalized.match(/\\b\\d{2}-[A-Z]{3}-\\d{4}(?:\\s+\\d{2}:\\d{2}(?::\\d{2})?\\s*[A-Z]{0,4})?\\b/i);
                        return match ? normalize(match[0]) : normalized;
                    };
                    const parseStatusFromText = value => {
                        const normalized = upper(value);
                        if (!normalized) {
                            return null;
                        }
                        const knownStatuses = ['DRF', 'DRAFT', 'SUB', 'SUBMITTED', 'SNT', 'SENT', 'PMT', 'PERMIT ISSUED', 'PERMIT_ISSUED', 'FLD', 'FAILED', 'FAILURE', 'REJ', 'REJECTED', 'REG', 'REGISTERED'];
                        return knownStatuses.find(status => normalized.includes(status)) || null;
                    };

                    const responseTab = Array.from(document.querySelectorAll('[role="tab"], button, a, span, div'))
                        .filter(isVisible)
                        .find(element => upper(textOf(element)).startsWith('RESPONSE'));
                    if (responseTab) {
                        responseTab.scrollIntoView({ block: 'center' });
                        responseTab.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                    }

                    const viewModeTexts = ['VIEW MODE', 'ALL FIELDS ARE READ-ONLY'];
                    const detailsHeadings = ['REJECTION DETAILS', 'REGISTRATION DETAILS', 'RESPONSE DETAILS', 'ERROR DETAILS', 'DETAILS'];
                    const bannerKeywords = ['PERMIT REJECTED', 'REJECTED', 'FAILED', 'ERROR', 'REGISTERED', 'VALIDATION'];

                    const pageTexts = Array.from(document.querySelectorAll('body, body *'))
                        .filter(isVisible)
                        .map(textOf)
                        .filter(Boolean);

                    const detailHeading = Array.from(document.querySelectorAll('h1, h2, h3, h4, h5, strong, label, div, span'))
                        .filter(isVisible)
                        .find(element => detailsHeadings.includes(upper(textOf(element))));

                    let detailText = null;
                    if (detailHeading) {
                        let current = detailHeading.parentElement;
                        while (current) {
                            const candidates = Array.from(current.querySelectorAll('div, p, section, article, pre'))
                                .filter(isVisible)
                                .map(textOf)
                                .filter(text => {
                                    const normalized = upper(text);
                                    return text.length > 20
                                        && !detailsHeadings.includes(normalized)
                                        && !viewModeTexts.some(marker => normalized.includes(marker));
                                });
                            detailText = pickLongest(candidates);
                            if (detailText) {
                                break;
                            }
                            current = current.parentElement;
                        }
                    }

                    const bannerText = pickLongest(pageTexts.filter(text => {
                        const normalized = upper(text);
                        return bannerKeywords.some(keyword => normalized.includes(keyword))
                            && !viewModeTexts.some(marker => normalized.includes(marker));
                    }));

                    const rawJsonToggle = Array.from(document.querySelectorAll('button, a, [role="button"]'))
                        .filter(isVisible)
                        .find(element => upper(textOf(element)).includes('SHOW RAW JSON'));
                    if (rawJsonToggle) {
                        rawJsonToggle.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                    }

                    const rawResponseText = pickLongest(Array.from(document.querySelectorAll('pre, code, textarea, .json-viewer, [class*="json"]'))
                        .filter(isVisible)
                        .map(textOf)
                        .filter(Boolean));

                    const parseJsonMessage = rawText => {
                        if (!rawText) {
                            return null;
                        }
                        try {
                            const data = JSON.parse(rawText);
                            const keys = ['responseMessage', 'message', 'errorMessage', 'reason', 'description', 'remarks', 'detail'];
                            const queue = [data];
                            while (queue.length > 0) {
                                const current = queue.shift();
                                if (!current || typeof current !== 'object') {
                                    continue;
                                }
                                if (Array.isArray(current)) {
                                    queue.push(...current);
                                    continue;
                                }
                                for (const key of keys) {
                                    if (typeof current[key] === 'string' && normalize(current[key])) {
                                        return normalize(current[key]);
                                    }
                                }
                                queue.push(...Object.values(current));
                            }
                        } catch (_) {
                        }
                        return null;
                    };

                    const parseJsonPermitNumber = rawText => {
                        if (!rawText) {
                            return null;
                        }
                        try {
                            const data = JSON.parse(rawText);
                            const keys = ['pmtNumber', 'permitNumber', 'permitNo', 'permitNum'];
                            const queue = [data];
                            while (queue.length > 0) {
                                const current = queue.shift();
                                if (!current || typeof current !== 'object') {
                                    continue;
                                }
                                if (Array.isArray(current)) {
                                    queue.push(...current);
                                    continue;
                                }
                                for (const key of keys) {
                                    if (typeof current[key] === 'string' && normalize(current[key])) {
                                        return normalize(current[key]);
                                    }
                                }
                                queue.push(...Object.values(current));
                            }
                        } catch (_) {
                        }
                        return null;
                    };

                    const normalizePermitCandidate = value => {
                        const normalized = normalize(value);
                        if (!normalized || normalized === '-' || normalized === '--' || /^TDX\\d+$/i.test(normalized)) {
                            return null;
                        }
                        return normalized;
                    };

                    const parsePermitNumberFromText = rawText => {
                        const normalized = normalize(rawText);
                        if (!normalized) {
                            return null;
                        }
                        const labeledMatch = normalized.match(/(?:PMT NUMBER|PMT NO\\.?|PERMIT NUMBER|PERMIT NO\\.?)[\\s:]+([A-Z0-9-]+)/i);
                        const labeledPermitNumber = labeledMatch ? normalizePermitCandidate(labeledMatch[1]) : null;
                        if (labeledPermitNumber) {
                            return labeledPermitNumber;
                        }
                        const genericMatch = normalized.match(/\\b([A-Z]{1,4}\\d[A-Z0-9-]{5,})\\b/i);
                        const genericPermitNumber = genericMatch ? normalizePermitCandidate(genericMatch[1]) : null;
                        if (genericPermitNumber) {
                            return genericPermitNumber;
                        }
                        return null;
                    };

                    const pagePermitNumber = (() => {
                        const permitLabels = ['PMT NUMBER', 'PMT NO', 'PMT NO.', 'PERMIT NUMBER', 'PERMIT NO', 'PERMIT NO.'];
                        for (const element of Array.from(document.querySelectorAll('label, dt, th, td, div, span, p, strong'))) {
                            if (!isVisible(element)) {
                                continue;
                            }
                            const labelText = upper(textOf(element));
                            if (!permitLabels.some(label => labelText === label || labelText.startsWith(label + ':'))) {
                                continue;
                            }
                            const nextText = normalize(element.nextElementSibling?.innerText || element.nextElementSibling?.textContent || '');
                            if (nextText && nextText !== '-' && nextText !== '--') {
                                return normalizePermitCandidate(parsePermitNumberFromText(nextText) || nextText);
                            }
                            const parentText = normalize(element.parentElement?.innerText || element.parentElement?.textContent || '');
                            const parsedParentPermitNumber = parsePermitNumberFromText(parentText);
                            if (parsedParentPermitNumber) {
                                return parsedParentPermitNumber;
                            }
                        }
                        return parsePermitNumberFromText(pageTexts.join(' '));
                    })();

                    const rawJsonMessage = parseJsonMessage(rawResponseText);
                    const rawJsonPermitNumber = parseJsonPermitNumber(rawResponseText)
                        || parsePermitNumberFromText(rawResponseText);
                    const responseMessage = rawJsonMessage || detailText || bannerText || null;
                    const errorMessage = detailText || rawJsonMessage || bannerText || null;
                    const urn = readLabeledValue(['URN ID', 'URN']);
                    const submissionDate = extractDateTime(readLabeledValue(['APPROVAL DATE/TIME', 'SUBMISSION DATE', 'DATE SUBMITTED']));
                    const dateCreated = extractDateTime(readLabeledValue(['DATE CREATED', 'CREATED DATE', 'CREATED ON']));
                    const status = parseStatusFromText(readLabeledValue(['STATUS', 'JOB STATUS']) || bannerText || responseMessage || errorMessage || pageTexts.join(' '));

                    return {
                        responseMessage,
                        errorMessage,
                        bannerText,
                        detailText,
                        rawResponseText,
                        permitNumber: rawJsonPermitNumber || pagePermitNumber || null,
                        urn,
                        dateCreated,
                        submissionDate,
                        status
                    };
                }
                """);

        if (result == null) {
            return new DeclarationResponseDetails(null, null, null, null, null, null, null, null, null, null);
        }

        return new DeclarationResponseDetails(
                stringValue(result.get("responseMessage")),
                stringValue(result.get("errorMessage")),
                stringValue(result.get("bannerText")),
                stringValue(result.get("detailText")),
                stringValue(result.get("rawResponseText")),
                stringValue(result.get("permitNumber")),
                stringValue(result.get("urn")),
                stringValue(result.get("dateCreated")),
                stringValue(result.get("submissionDate")),
                normalizeJobStatus(stringValue(result.get("status"))));
    }

    public DeclarationListEntry waitForDeclarationCompletion(
            String messageReference,
            String expectedJobId,
            long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        DeclarationListEntry latestMatchingEntry = null;

        while (System.currentTimeMillis() <= deadline) {
            try {
                DeclarationListEntry currentEntry = resolveTrackedDeclarationEntry(messageReference, expectedJobId);
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

        if (latestMatchingEntry != null) {
            return latestMatchingEntry;
        }
        try {
            return resolveTrackedDeclarationEntry(messageReference, expectedJobId);
        } catch (PlaywrightException exception) {
            return null;
        }
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

    private boolean isPermitNumberPending(DeclarationListEntry entry) {
        return entry != null
                && "PMT".equals(normalizeJobStatus(entry.jobStatus()))
                && (entry.permitNumber() == null || entry.permitNumber().isBlank());
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

    private DeclarationListEntry resolveTrackedDeclarationEntry(
            String messageReference,
            String expectedJobId) {
        if (expectedJobId != null && !expectedJobId.isBlank()) {
            DeclarationListEntry byJobId = readDeclarationListEntryByJobId(expectedJobId);
            if (matchesTrackedDeclaration(byJobId, messageReference, expectedJobId)) {
                return byJobId;
            }
        }

        if (messageReference != null && !messageReference.isBlank()) {
            DeclarationListEntry byMessageReference = readDeclarationListEntry(messageReference);
            if (matchesTrackedDeclaration(byMessageReference, messageReference, expectedJobId)) {
                return byMessageReference;
            }
        }

        if (expectedJobId != null && !expectedJobId.isBlank()) {
            return readDeclarationListEntryByJobId(expectedJobId);
        }

        return messageReference != null && !messageReference.isBlank()
                ? readDeclarationListEntry(messageReference)
                : null;
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
            waitForDeclarationActionButton(page.url());
        } catch (PlaywrightException ignored) {
        }
        page.waitForTimeout(1000);
    }

    private void openDeclarationsMenuIfNeeded(String menuLabel) {
        // Check if the specific declaration type's menu item is already visible.
        // Using the exact type link (e.g. "Certificate of Origin (COO)") is reliable for
        // every declaration type; the old hardcoded IPT check failed for non-IPT runs.
        Locator specificItem = page.locator("a:has-text('" + menuLabel + "')").first();
        if (specificItem.count() > 0 && specificItem.isVisible()) {
            return;
        }
        page.locator(DECLARATIONS_MENU).first().click();
        page.waitForTimeout(500);
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

    private void waitForDeclarationViewScreenVisible(String messageReference) {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForFunction("""
                (messageReference) => {
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
                    const textOf = element => normalize(element?.innerText || element?.textContent || '');
                    const bodyTexts = Array.from(document.querySelectorAll('body, body *'))
                        .filter(isVisible)
                        .map(textOf)
                        .filter(Boolean);
                    const responseTabVisible = bodyTexts.some(text => text.startsWith('RESPONSE'));
                    const messageReferenceVisible = messageReference
                        ? bodyTexts.some(text => text.includes(normalize(messageReference)))
                        : true;
                    return responseTabVisible
                        && messageReferenceVisible
                        && bodyTexts.some(text => text.includes('JOB INFO') || text.includes('VIEW MODE'));
                }
                """, messageReference);
    }

    private void openResponseTabIfPresent() {
        Boolean clicked = (Boolean) page.evaluate("""
                () => {
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
                    const target = Array.from(document.querySelectorAll('[role="tab"], button, a, span, div'))
                        .filter(isVisible)
                        .find(element => normalize(element.innerText || element.textContent).startsWith('RESPONSE'));
                    if (!target) {
                        return false;
                    }
                    target.scrollIntoView({ block: 'center' });
                    target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                    return true;
                }
                """);
        if (Boolean.TRUE.equals(clicked)) {
            page.waitForTimeout(300);
        }
    }

    private void waitForDeclarationActionButton(String route) {
        Locator button = resolveNewDeclarationButton(route);
        if (button != null) {
            button.waitFor(new Locator.WaitForOptions().setTimeout(30000));
            return;
        }

        List<String> labels = declarationActionLabels(route);
        try {
            page.waitForFunction("""
                    labels => {
                        const normalize = value => (value || "").replace(/\\s+/g, " ").trim().toUpperCase();
                        const isVisible = element => !!element
                            && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        return Array.from(document.querySelectorAll("button, [role='button'], a"))
                            .filter(isVisible)
                            .some(element => {
                                const text = normalize(element.innerText || element.textContent);
                                return labels.some(label => text === label || text.includes(label));
                            });
                    }
                    """, labels);
        } catch (PlaywrightException exception) {
            System.out.println("DECLARATION_LIST_URL=" + page.url());
            System.out.println("DECLARATION_LIST_BODY=" + page.locator("body").innerText());
            throw new IllegalStateException(
                    "Declaration action button was not visible on declarations page. Expected one of: "
                            + String.join(", ", labels),
                    exception);
        }
    }

    private void clickNewDeclarationButton(String route) {
        Locator button = resolveNewDeclarationButton(route);
        List<String> labels = declarationActionLabels(route);
        if (button != null && button.isVisible() && clickDeclarationActionButton(labels)) {
            return;
        }

        if (clickDeclarationActionButton(labels)) {
            return;
        }

        throw new IllegalStateException("Unable to click declaration action button. Expected one of: "
                + String.join(", ", labels));
    }

    private boolean clickDeclarationActionButton(List<String> labels) {
        Boolean clicked = (Boolean) page.evaluate("""
                labels => {
                    const normalize = value => (value || "").replace(/\\s+/g, " ").trim().toUpperCase();
                    const isVisible = element => !!element
                        && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    const target = Array.from(document.querySelectorAll("button, [role='button'], a"))
                        .filter(isVisible)
                        .find(element => {
                            const text = normalize(element.innerText || element.textContent);
                            return labels.some(label => text === label || text.includes(label));
                        });
                    if (!target) {
                        return false;
                    }
                    target.scrollIntoView({ block: "center" });
                    target.click();
                    return true;
                }
                """, labels);
        return Boolean.TRUE.equals(clicked);
    }

    private Locator resolveNewDeclarationButton(String route) {
        for (String label : declarationActionLabels(route)) {
            String escapedLabel = label.replace("'", "\\'");
            String[] selectors = new String[] {
                    "button:has-text('" + escapedLabel + "')",
                    "[role='button']:has-text('" + escapedLabel + "')",
                    "a:has-text('" + escapedLabel + "')",
                    "text=" + label
            };

            for (String selector : selectors) {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0) {
                    return locator;
                }
            }
        }

        return null;
    }

    private List<String> declarationActionLabels(String route) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        labels.add(NEW_DECLARATION_LABEL);

        String key = declarationRouteKey(route);
        if (key != null) {
            labels.add("NEW " + key + " DECLARATION");
            labels.add("CREATE " + key + " DECLARATION");
            labels.add(key + " DECLARATION");
        }

        return new ArrayList<>(labels);
    }

    private String declarationRouteKey(String route) {
        if (route == null || route.isBlank()) {
            return null;
        }
        String normalized = route.replace('\\', '/').replaceAll("[?#].*$", "");
        int declarationsIndex = normalized.toLowerCase().lastIndexOf("/declarations/");
        if (declarationsIndex >= 0) {
            normalized = normalized.substring(declarationsIndex + "/declarations/".length());
        }
        String[] segments = normalized.split("/");
        for (int index = segments.length - 1; index >= 0; index--) {
            String segment = segments[index].replaceAll("[^A-Za-z0-9]", "").trim();
            if (!segment.isBlank() && !"edit".equalsIgnoreCase(segment)) {
                return segment.toUpperCase();
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
                        const route = args.route || '';
                        const onEditorRoute = path.includes(route + '/edit/')
                            || path === route + '/create'
                            || path.endsWith(route + '/create');
                        if (!onEditorRoute) {
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
            String jobCreatedBy,
            String permitNumber) {
    }

    public record DeclarationResponseDetails(
            String responseMessage,
            String errorMessage,
            String bannerText,
            String detailText,
            String rawResponseText,
            String permitNumber,
            String urn,
            String dateCreated,
            String submissionDate,
            String status) {
    }
}
