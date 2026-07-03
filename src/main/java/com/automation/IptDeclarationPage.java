package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class IptDeclarationPage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter UI_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter UI_SLASH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SOURCE_DDMMYYYY_FORMAT =
            DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SOURCE_YYYYMMDD_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final int UI_ACTION_PAUSE_MS =
            Integer.getInteger("tradenix.ui.action.pause.ms", 1000);
    private static final int UI_LOOKUP_WAIT_MS =
            Integer.getInteger("tradenix.ui.lookup.wait.ms", 1000);
    private static final int UI_NEXT_FIELD_PAUSE_MS =
            Integer.getInteger("tradenix.ui.next.field.pause.ms", 1000);
    private static final int UI_POST_SAVE_READY_TIMEOUT_MS =
            Integer.getInteger("tradenix.ui.post.save.ready.timeout.ms", 45000);
    private static final int UI_SUBMIT_CLICK_WAIT_MS = 2000;
    private static final int UI_POST_SUBMIT_WAIT_MS = 3000;

    protected final Page page;
    private final List<String> fieldMappingDiagnostics = new ArrayList<>();
    private boolean summaryDraftSaved;
    private int validatedFieldEntryCount;

    public IptDeclarationPage(Page page) {
        this.page = page;
    }

    public void populateFrom(JsonNode data) {
        populateDraftFrom(data);
        if (shouldSubmitDeclaration(normalizeDeclarationPayload(data))) {
            submitDeclaration();
        }
    }

    public void populateDraftFrom(JsonNode data) {
        JsonNode payload = normalizeDeclarationPayload(data);
        resetFieldMappingDiagnostics();
        validateDeclarationPayload(payload);
        summaryDraftSaved = false;
        validatedFieldEntryCount = 0;
        logFieldMappingInfo("Executing declaration flow: " + declarationFlowLabel());
        if (DeclarationPayloads.isWrapped(data)) {
            logFieldMappingInfo("Using nested inboundMessage payload for declaration field mapping.");
        }
        logFieldMappingInfo("Resolved declaration payload type: "
                + firstNonBlank(
                text(payload.path("header"), "commonAccessReference"),
                text(payload.path("header"), "declarationType"),
                text(payload, "type"),
                "UNKNOWN"));
        fillSectionAndAdvance("Shipment Info (S)", () -> fillShipmentInfo(payload));
        fillSectionAndAdvance("Transport Info (T)", () -> fillTransportInfo(payload));
        fillSectionAndAdvance("Party Info (P)", () -> fillPartyInfo(payload));
        String invoiceSectionName = invoiceSectionName();
        if (invoiceSectionName != null && !invoiceSectionName.isBlank()) {
            fillSectionAndAdvance(invoiceSectionName, () -> fillInvoiceInfo(payload));
        }
        fillSectionAndAdvance("Items (I)", () -> fillItemInfo(payload));
        String cpcSectionName = cpcSectionName();
        if (cpcSectionName != null && !cpcSectionName.isBlank()) {
            fillSectionAndAdvance(cpcSectionName, () -> fillDeclarationSpecificCpcInfo(payload));
        }
        fillSummary(payload);
    }

    public int validatedFieldEntryCount() {
        return validatedFieldEntryCount;
    }

    public void submitDeclaration() {
        openSection("Summary (Y)");
        if (!summaryDraftSaved) {
            saveDraftAndWaitForCompletion();
        } else {
            waitForPostSaveReadyState(UI_POST_SAVE_READY_TIMEOUT_MS);
        }
        waitForActionButtonEnabled("SUBMIT DECLARATION", UI_POST_SAVE_READY_TIMEOUT_MS);
        clickActionButtonExactWithRetry("SUBMIT DECLARATION", 3);
        page.waitForTimeout(5000);
    }

    public void openInvoiceInfoSection() {
        openSection("Invoice Info (V)");
    }

    public String readCurrentMessageReference() {
        try {
            Object result = page.evaluate("""
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

                        const labels = Array.from(document.querySelectorAll('label, .form-label, p, span, div'))
                            .filter(isVisible);
                        const label = labels.find(element => upper(element.innerText || element.textContent) === 'MESSAGE REFERENCE');
                        if (!label) {
                            return null;
                        }

                        const container = label.closest('div, section, aside') || label.parentElement;
                        const candidates = Array.from((container || document).querySelectorAll('p, span, div'))
                            .filter(isVisible)
                            .map(element => normalize(element.innerText || element.textContent))
                            .filter(Boolean)
                            .filter(value => upper(value) !== 'MESSAGE REFERENCE');
                        return candidates.find(value => /^TDX\\d+/i.test(value)) || null;
                    }
                    """);
            return result == null ? null : String.valueOf(result).trim();
        } catch (PlaywrightException exception) {
            return null;
        }
    }

    public String readSupplierManufacturerNameValue() {
        return normalize(readRenderedFieldValue(resolveSupplierManufacturerNameField()));
    }

    public String captureSubmitValidationDiagnostics() {
        try {
            String uiDiagnostics = String.valueOf(page.evaluate("""
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
                        const describeElement = element => {
                            if (!element) {
                                return '';
                            }

                            const ownText = normalize(element.innerText || element.textContent);
                            const section = normalize(
                                element.closest('app-card, section, .card, .accordion-body, .accordion-item, .tab-pane')
                                    ?.querySelector('h1, h2, h3, h4, h5, .card-title, .accordion-header, .tab-title, [class*="title"]')
                                    ?.textContent);
                            const label = normalize(
                                element.closest('div, td, tr, section, form')?.querySelector('label, .form-label, span, p, div')
                                    ?.textContent);
                            const placeholder = normalize(element.getAttribute('placeholder'));
                            const name = normalize(element.getAttribute('name'));
                            const formControlName = normalize(element.getAttribute('formcontrolname'));
                            const id = normalize(element.getAttribute('id'));
                            const type = normalize(element.getAttribute('type'));
                            const role = normalize(element.getAttribute('role'));
                            const currentValue = normalize(
                                element instanceof HTMLSelectElement
                                    ? (element.selectedOptions?.[0]?.textContent || element.value)
                                    : ('value' in element ? element.value : ownText));
                            const validationMessage = normalize(
                                element.closest('.clr-form-control, .form-group, td, tr, section, form, .card, .accordion-body')
                                    ?.querySelector('.clr-subtext, .clr-form-control-error, .invalid-feedback, .error, .errors, [class*="error"], [aria-live="assertive"]')
                                    ?.textContent);
                            return {
                                section,
                                label,
                                text: ownText,
                                placeholder,
                                name,
                                formControlName,
                                id,
                                type,
                                role,
                                currentValue,
                                validationMessage,
                                ariaInvalid: normalize(element.getAttribute('aria-invalid')),
                                classes: normalize(element.getAttribute('class'))
                            };
                        };
                        const uniqueTexts = values => {
                            const seen = new Set();
                            return values.filter(value => {
                                const key = upper(value);
                                if (!key || seen.has(key)) {
                                    return false;
                                }
                                seen.add(key);
                                return true;
                            });
                        };
                        const textOf = element => normalize(element?.innerText || element?.textContent || '');
                        const resolveCheckboxFromElement = element => {
                            if (!element) {
                                return null;
                            }

                            if (element.tagName === 'LABEL') {
                                if (element.control && element.control.type === 'checkbox') {
                                    return element.control;
                                }
                                const htmlFor = element.getAttribute('for');
                                if (htmlFor) {
                                    const associated = document.getElementById(htmlFor);
                                    if (associated && associated.type === 'checkbox') {
                                        return associated;
                                    }
                                }
                            }

                            return element.querySelector?.('input[type="checkbox"], [role="checkbox"]')
                                || element.closest?.('clr-checkbox-wrapper, label, div, section, form')?.querySelector?.('input[type="checkbox"], [role="checkbox"]')
                                || null;
                        };
                        const readCheckboxStateByLabel = labelText => {
                            const expected = upper(labelText);
                            const label = Array.from(document.querySelectorAll('label, span, div, p'))
                                .filter(isVisible)
                                .find(element => {
                                    const text = upper(textOf(element));
                                    return text === expected || text.includes(expected);
                                });
                            const checkbox = resolveCheckboxFromElement(label);
                            if (!checkbox) {
                                return null;
                            }
                            return checkbox.getAttribute('aria-checked') === 'true'
                                || checkbox.checked === true;
                        };
                        const containsAny = (value, candidates) => {
                            const normalized = upper(value);
                            return candidates.some(candidate => normalized.includes(candidate));
                        };

                        const invalidSelectors = [
                            'input.ng-invalid',
                            'textarea.ng-invalid',
                            'select.ng-invalid',
                            '[role="combobox"].ng-invalid',
                            '[aria-invalid="true"]',
                            'input:invalid',
                            'textarea:invalid',
                            'select:invalid'
                        ];

                        const invalidElements = Array.from(new Set(
                            invalidSelectors.flatMap(selector => Array.from(document.querySelectorAll(selector)))
                        )).filter(element => {
                            const style = window.getComputedStyle(element);
                            return style && style.display !== 'none' && style.visibility !== 'hidden';
                        });

                        const notificationSelectors = [
                            '[role="alert"]',
                            '[aria-live]',
                            '.notification-container',
                            '.notification-container *',
                            '.alert',
                            '.toast',
                            '[class*="notification"]',
                            '[class*="toast"]'
                        ];
                        const notificationTexts = uniqueTexts(Array.from(new Set(
                                notificationSelectors.flatMap(selector => Array.from(document.querySelectorAll(selector)))
                            ))
                            .filter(isVisible)
                            .map(textOf)
                            .filter(Boolean));

                        const errorCandidates = uniqueTexts(Array.from(document.querySelectorAll('*'))
                            .filter(element => isVisible(element) && !['HTML', 'BODY'].includes(element.tagName))
                            .map(textOf)
                            .filter(Boolean)
                            .filter(text => text.length <= 2000)
                            .filter(text => containsAny(text, [
                                'VALIDATION FAILED',
                                'FAILED TO UPLOAD',
                                'ERROR',
                                'UNSUPPORTED FILE TYPE',
                                'MANDATORY',
                                'PLEASE FILL IN ALL REQUIRED FIELDS'
                            ])))
                            .sort((left, right) => {
                                const score = value => {
                                    const normalized = upper(value);
                                    if (normalized.includes('VALIDATION FAILED')) {
                                        return 0;
                                    }
                                    if (normalized.includes('FAILED TO UPLOAD')) {
                                        return 1;
                                    }
                                    if (normalized.includes('UNSUPPORTED FILE TYPE')) {
                                        return 2;
                                    }
                                    return 3;
                                };
                                const scoreDiff = score(left) - score(right);
                                if (scoreDiff !== 0) {
                                    return scoreDiff;
                                }
                                return left.length - right.length;
                            });

                        const successCandidates = notificationTexts.filter(text => !containsAny(text, [
                            'VALIDATION FAILED',
                            'FAILED',
                            'ERROR',
                            'UNSUPPORTED FILE TYPE'
                        ]));

                        const responseMessage = successCandidates[0]
                            || notificationTexts[0]
                            || '';
                        const errorMessage = errorCandidates[0]
                            || notificationTexts.find(text => containsAny(text, ['FAILED', 'ERROR', 'VALIDATION']))
                            || '';
                        const toastText = errorMessage || responseMessage || '';

                        return JSON.stringify({
                            toastText,
                            responseMessage,
                            errorMessage,
                            notificationMessages: notificationTexts,
                            invalidElements: invalidElements.map(describeElement),
                            supplyIndicator: readCheckboxStateByLabel('Supply Indicator')
                        }, null, 2);
                    }
                    """));
            ObjectNode diagnosticsRoot = parseDiagnosticsRoot(uiDiagnostics);
            ArrayNode mappingDiagnosticsNode = diagnosticsRoot.putArray("mappingDiagnostics");
            for (String entry : fieldMappingDiagnostics) {
                mappingDiagnosticsNode.add(entry);
            }
            diagnosticsRoot.put("validatedFieldEntryCount", validatedFieldEntryCount);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(diagnosticsRoot);
        } catch (PlaywrightException exception) {
            return "Unable to capture submit validation diagnostics: " + exception.getMessage()
                    + System.lineSeparator()
                    + String.join(System.lineSeparator(), fieldMappingDiagnostics);
        } catch (Exception exception) {
            return "Unable to serialize submit validation diagnostics: " + exception.getMessage()
                    + System.lineSeparator()
                    + String.join(System.lineSeparator(), fieldMappingDiagnostics);
        }
    }

    public String captureRenderedFormAuditSnapshot() {
        try {
            return String.valueOf(page.evaluate("""
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
                        const uniqueValues = values => {
                            const seen = new Set();
                            return values.filter(value => {
                                const key = upper(value);
                                if (!key || seen.has(key)) {
                                    return false;
                                }
                                seen.add(key);
                                return true;
                            });
                        };

                        const texts = uniqueValues(Array.from(document.querySelectorAll('body, body *'))
                            .filter(isVisible)
                            .map(element => normalize(element.innerText || element.textContent))
                            .filter(value => value && value.length <= 500));

                        const values = uniqueValues(Array.from(document.querySelectorAll(
                                "input:not([type='hidden']), textarea, select, [role='combobox'], [role='textbox'], [contenteditable='true']"))
                            .filter(isVisible)
                            .flatMap(element => {
                                const renderedValues = [];
                                const directValue = normalize(
                                    element instanceof HTMLSelectElement
                                        ? (element.selectedOptions?.[0]?.textContent || element.value)
                                        : ('value' in element ? element.value : element.textContent));
                                if (directValue) {
                                    renderedValues.push(directValue);
                                }
                                const ariaValue = normalize(element.getAttribute('aria-label'));
                                if (ariaValue) {
                                    renderedValues.push(ariaValue);
                                }
                                const placeholder = normalize(element.getAttribute('placeholder'));
                                if (placeholder) {
                                    renderedValues.push(placeholder);
                                }
                                return renderedValues;
                            }));

                        return JSON.stringify({
                            texts,
                            values
                        });
                    }
                    """));
        } catch (PlaywrightException exception) {
            return "{}";
        }
    }

    private void fillShipmentInfo(JsonNode data) {
        JsonNode header = data.path("header");
        JsonNode cargo = data.path("cargo");
        JsonNode releaseLocation = cargo.path("releaseLocation");
        JsonNode receiptLocation = cargo.path("receiptLocation");
        JsonNode transportMode = data.path("transport").path("inwardTransport").path("transportMeans").path("transportMode");
        JsonNode license = firstArrayItem(data.path("licence"));

        String declarationType = text(header, "declarationType");
        fillDeclarationType(data, declarationType);

        String cargoType = text(cargo, "cargoPackingType");
        fillLookupFieldInSection("Declaration Info", "Cargo Type", cargoType, cargoType, "5", "OTHER", "Other");

        String inwardTransportId = text(transportMode, "modeCode");
        fillLookupFieldInSectionByAnyLabelIfPresent(
                "Declaration Info",
                inwardTransportId,
                inwardTransportId,
                "4 - Air",
                "AIR",
                "Inward Transport ID",
                "Transport Type",
                "Transport Mode",
                "Mode Of Transport");
        fillDeclarationSpecificShipmentInfo(data);

        page.waitForTimeout(500);
        fillLookupFieldInSection(
                "Declaration Info",
                "Release Location",
                text(releaseLocation, "locationCode"),
                text(releaseLocation, "locationCode"),
                text(releaseLocation, "locationName"),
                "CZ");
        fillLookupFieldInSection(
                "Declaration Info",
                "Receipt Location",
                text(receiptLocation, "locationCode"),
                text(receiptLocation, "locationCode"),
                text(receiptLocation, "locationName"),
                "O",
                "OTHERS");
        fillFieldInSectionIfPresent(
                "Prev Permit Number",
                "Previous Permit Number",
                text(header, "previousPermitNumber"));
        fillChecksSection(header, cargo);
        fillAdditionalRecipients(header, data.path("formMetaData"));
        fillLicense(text(license, "referenceID"));
        fillSupportingDocuments(data);
    }

    private void fillChecksSection(JsonNode header, JsonNode cargo) {
        String bgIndicator = text(header, "bankerGuaranteeCode");
        if (bgIndicator != null && !bgIndicator.isBlank()) {
            selectBgIndicator(bgIndicator);
        }

        String blanketStartDate = formatUiDate(text(cargo, "blanketStartDate"));
        if (blanketStartDate != null && !blanketStartDate.isBlank()) {
            fillDateFieldInSectionIfPresent("Checks", "Blanket Start Date", blanketStartDate);
        }

        Boolean supplyIndicator = readOptionalJsonBoolean(cargo.path("supplyIndicator"));
        if (supplyIndicator != null) {
            setCheckboxByLabelIfDifferent("Supply Indicator", supplyIndicator);
        }
    }

    private void selectBgIndicator(String bgIndicator) {
        if (bgIndicator == null || bgIndicator.isBlank()) {
            return;
        }

        String[] exactSelectionHints = compactValues(bgIndicator);
        String[] selectionHints = bgIndicatorHints(bgIndicator);
        Locator field = resolveBgIndicatorField();
        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();

        if (trySelectNativeDropdown(field, bgIndicator, selectionHints)) {
            if (!waitForAnyRenderedFieldValue(field, 1000, selectionHints)) {
                throw new IllegalStateException("BG Indicator value was not rendered. Expected one of: "
                        + String.join(", ", selectionHints) + ", Actual: " + readRenderedFieldValue(field));
            }
            page.keyboard().press("Tab");
            pauseUi(UI_NEXT_FIELD_PAUSE_MS);
            return;
        }

        clickDropdownActivator(field);
        pauseUi(UI_ACTION_PAUSE_MS);

        boolean optionSelected = waitForVisibleSuggestionExact(UI_LOOKUP_WAIT_MS, exactSelectionHints)
                && clickVisibleSuggestionExact(exactSelectionHints);
        if (!optionSelected) {
            try {
                page.keyboard().press("ArrowDown");
                pauseUi(UI_ACTION_PAUSE_MS);
            } catch (PlaywrightException ignored) {
            }
            optionSelected = clickVisibleSuggestionExact(exactSelectionHints);
        }
        if (!optionSelected) {
            optionSelected = waitForVisibleSuggestion(UI_LOOKUP_WAIT_MS, selectionHints)
                    && clickVisibleSuggestion(selectionHints);
        }
        if (!optionSelected) {
            openLookupAndChooseOption(field, selectionHints);
        }

        if (!waitForAnyRenderedFieldValue(field, 1500, exactSelectionHints[0], selectionHints[0])) {
            closeTransientOverlays();
            clickDropdownActivator(field);
            pauseUi(UI_ACTION_PAUSE_MS);
            if (waitForVisibleSuggestionExact(UI_LOOKUP_WAIT_MS, exactSelectionHints)) {
                clickVisibleSuggestionExact(exactSelectionHints);
            } else if (waitForVisibleSuggestion(UI_LOOKUP_WAIT_MS, selectionHints)) {
                clickVisibleSuggestion(selectionHints);
            }
        }

        if (!waitForAnyRenderedFieldValue(field, 1500, exactSelectionHints[0], selectionHints[0])) {
            throw new IllegalStateException("BG Indicator value was not rendered. Expected one of: "
                    + String.join(", ", selectionHints) + ", Actual: " + readRenderedFieldValue(field));
        }
    }

    private void fillAdditionalRecipients(JsonNode header, JsonNode formMetaData) {
        setCheckboxByLabel("Additional Recipients", false);
        page.waitForTimeout(200);

        // Only proceed if the feature is explicitly enabled in JSON
        if (formMetaData == null || !formMetaData.path("additionalRecipientIdIsActive").asBoolean(false)) {
            return;
        }

        JsonNode additionalRecipientIds = header.path("additionalRecipientId");
        if (additionalRecipientIds == null || !additionalRecipientIds.isArray() || additionalRecipientIds.isEmpty()) {
            return;
        }

        String additionalRecipientId = normalize(additionalRecipientIds.get(0).asText());
        if (additionalRecipientId.isBlank()) {
            return;
        }

        setCheckboxByLabel("Additional Recipients", true);
        pauseUi(UI_ACTION_PAUSE_MS);

        Locator additionalRecipientsSection = resolveAdditionalRecipientsSection();
        Locator field = resolveAdditionalRecipientsInputBoxOrNull(additionalRecipientsSection, 1, 0);
        if (field == null) {
            Locator activator = resolveAdditionalRecipientsActivatorOrNull(additionalRecipientsSection, 1);
            if (activator != null) {
                closeTransientOverlays();
                activator.scrollIntoViewIfNeeded();
                activator.click(new Locator.ClickOptions().setForce(true));
                pauseUi(UI_ACTION_PAUSE_MS);
                field = resolveAdditionalRecipientsInputBoxOrNull(additionalRecipientsSection, 1, 0);
                if (field == null) {
                    page.keyboard().press("Control+A");
                    page.keyboard().press("Backspace");
                    page.keyboard().type(additionalRecipientId);
                    page.keyboard().press("Tab");
                    pauseUi(UI_NEXT_FIELD_PAUSE_MS);
                    return;
                }
            }
        }
        if (field == null && clickButtonInScopeIfVisible(additionalRecipientsSection, "ADD")) {
            pauseUi(UI_ACTION_PAUSE_MS);
            field = resolveAdditionalRecipientsInputBoxOrNull(additionalRecipientsSection, 1, 0);
        }
        if (field == null) {
            throw new IllegalStateException("Additional Recipients Input Box 1 was not visible.");
        }
        focusAndType(field, additionalRecipientId, false);
    }

    private void fillSupportingDocuments(JsonNode data) {
        if (!hasDocumentData(data)) {
            return;
        }

        setCheckboxByLabel("Document", true);
        pauseUi(UI_ACTION_PAUSE_MS);
        fillSupportingDocumentReferences(data);
    }

    private boolean hasDocumentData(JsonNode data) {
        JsonNode supportingDocuments = supportingDocumentReferences(data);
        JsonNode formMetaData = data.path("formMetaData");
        return (supportingDocuments.isArray() && !supportingDocuments.isEmpty())
                || formMetaData.path("supportingDocumentIsActive").asBoolean(false)
                || formMetaData.path("documentIsActive").asBoolean(false);
    }

    private JsonNode supportingDocumentReferences(JsonNode data) {
        JsonNode supportingDocumentReference = data.path("supportingDocumentReference");
        if (supportingDocumentReference.isArray() && !supportingDocumentReference.isEmpty()) {
            return supportingDocumentReference;
        }

        JsonNode supportingDocument = data.path("supportingDocument");
        if (supportingDocument.isArray() && !supportingDocument.isEmpty()) {
            return supportingDocument;
        }

        JsonNode documents = data.path("documents");
        if (documents.isArray() && !documents.isEmpty()) {
            return documents;
        }

        return MissingNode.getInstance();
    }

    private void fillSupportingDocumentReferences(JsonNode data) {
        JsonNode supportingDocuments = supportingDocumentReferences(data);
        if (!supportingDocuments.isArray() || supportingDocuments.isEmpty()) {
            return;
        }

        Locator documentSection = waitForDocumentSectionOrNull(3000);
        if (documentSection == null) {
            throw new IllegalStateException("Document section did not expand after selecting the Document checkbox.");
        }

        JsonNode firstDocument = firstArrayItem(supportingDocuments);
        if (firstDocument == null || firstDocument.isMissingNode() || firstDocument.isNull()) {
            return;
        }

        fillSupportingDocumentRow(documentSection, firstDocument);
    }

    private void fillSupportingDocumentRow(Locator documentSection, JsonNode documentNode) {
        String documentId = firstNonBlank(
                text(documentNode, "documentID"),
                text(documentNode, "documentId"),
                text(documentNode, "referenceID"),
                text(documentNode, "referenceId"));
        String filename = firstNonBlank(text(documentNode, "filename"), text(documentNode, "fileName"));
        String displayFileName = fileNameOnly(filename);

        if ((documentId == null || documentId.isBlank()) && (filename == null || filename.isBlank())) {
            return;
        }

        ensureDocumentRowVisible(documentSection);
        page.waitForTimeout(250);

        List<Locator> orderedFields = orderedVisibleEditableFields(documentSection);
        Locator documentIdField = orderedFields.isEmpty() ? null : orderedFields.get(0);
        Locator filenameField = orderedFields.size() > 1 ? orderedFields.get(1) : null;

        if (documentId != null && !documentId.isBlank() && documentIdField != null) {
            fillVerifiedLookupField(documentIdField, documentId, "Document ID", documentId);
        }

        if (filename == null || filename.isBlank()) {
            clickDocumentUploadButtonIfVisible(documentSection);
            return;
        }

        if (filenameField != null) {
            focusAndType(filenameField, displayFileName, false);
            waitForAnyRenderedFieldValue(filenameField, 1500, displayFileName);
        }

        if (!populateDocumentUploadControl(documentSection, filename)) {
            throw new IllegalStateException("Document filename control was not available for: " + filename);
        }

        waitForDocumentFileNameVisible(documentSection, displayFileName);
        clickDocumentUploadButton(documentSection);
    }

    private void ensureDocumentRowVisible(Locator documentSection) {
        if (hasDocumentDataEntryControls(documentSection)) {
            return;
        }

        clickDocumentAddButtonIfVisible(documentSection);
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() <= deadline) {
            if (hasDocumentDataEntryControls(documentSection)) {
                return;
            }
            page.waitForTimeout(100);
        }
    }

    private boolean hasDocumentDataEntryControls(Locator documentSection) {
        try {
            return Boolean.TRUE.equals(documentSection.evaluate("""
                    section => {
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
                        return Array.from(section.querySelectorAll(
                                "input:not([type='checkbox']):not([type='hidden']), textarea, select, [role='combobox'], [role='textbox'], input[type='file']"))
                            .some(isVisible);
                    }
                    """));
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void clickDocumentAddButtonIfVisible(Locator documentSection) {
        try {
            Boolean clicked = (Boolean) documentSection.evaluate("""
                    section => {
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
                        const button = Array.from(section.querySelectorAll(
                                "button, [role='button'], input[type='button'], input[type='submit'], a"))
                            .find(element => isVisible(element)
                                && normalize(element.innerText || element.textContent || element.value || element.getAttribute('aria-label')) === 'ADD');
                        if (!button) {
                            return false;
                        }
                        button.scrollIntoView({ block: 'center' });
                        button.click();
                        return true;
                    }
                    """);
            if (Boolean.TRUE.equals(clicked)) {
                page.waitForTimeout(250);
            }
        } catch (PlaywrightException ignored) {
        }
    }

    private boolean populateDocumentUploadControl(Locator documentSection, String filename) {
        try {
            Locator fileInput = resolveDocumentFileInputOrNull(documentSection);
            if (fileInput == null) {
                return false;
            }

            Path uploadFile = createTemporaryUploadFile(filename);
            fileInput.setInputFiles(uploadFile);
            page.waitForTimeout(500);
            return true;
        } catch (PlaywrightException | IOException ignored) {
            return false;
        }
    }

    private void clickDocumentUploadButton(Locator documentSection) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() <= deadline) {
            if (clickDocumentUploadButtonIfVisible(documentSection)) {
                verifyDocumentUploadFeedback();
                return;
            }
            page.waitForTimeout(150);
        }
        throw new IllegalStateException("Document upload button was not visible after entering document values.");
    }

    private boolean clickDocumentUploadButtonIfVisible(Locator documentSection) {
        try {
            Boolean clicked = (Boolean) documentSection.evaluate("""
                    section => {
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
                        const buttons = Array.from(section.querySelectorAll(
                                "button, [role='button'], input[type='button'], input[type='submit'], a"))
                            .filter(isVisible);
                        const uploadButton = buttons.find(element => {
                            const text = normalize(element.innerText || element.textContent);
                            const ariaLabel = normalize(element.getAttribute('aria-label'));
                            const title = normalize(element.getAttribute('title'));
                            const value = normalize(element.getAttribute('value'));
                            return text === 'UPLOAD ALL'
                                || ariaLabel === 'UPLOAD ALL'
                                || title === 'UPLOAD ALL'
                                || value === 'UPLOAD ALL'
                                || text.includes('UPLOAD ALL')
                                || ariaLabel.includes('UPLOAD ALL')
                                || title.includes('UPLOAD ALL')
                                || value.includes('UPLOAD ALL')
                                || text.includes('UPLOAD')
                                || ariaLabel.includes('UPLOAD')
                                || title.includes('UPLOAD')
                                || value.includes('UPLOAD');
                        });
                        if (!uploadButton) {
                            return false;
                        }
                        uploadButton.scrollIntoView({ block: 'center' });
                        uploadButton.click();
                        return true;
                    }
                    """);
            return Boolean.TRUE.equals(clicked);
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void verifyDocumentUploadFeedback() {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() <= deadline) {
            String visibleText = readVisiblePageText();
            if (visibleText.contains("FAILED TO UPLOAD DOCUMENTS")) {
                throw new IllegalStateException("Document upload failed after clicking Upload All.");
            }
            if (visibleText.contains("UNSUPPORTED FILE TYPE")) {
                throw new IllegalStateException("Document upload was rejected due to unsupported file type.");
            }
            page.waitForTimeout(150);
        }
    }

    private String readVisiblePageText() {
        try {
            Object text = page.evaluate("""
                    () => (document.body?.innerText || '').replace(/\\s+/g, ' ').trim().toUpperCase()
                    """);
            return text == null ? "" : String.valueOf(text);
        } catch (PlaywrightException ignored) {
            return "";
        }
    }

    private void waitForDocumentFileNameVisible(Locator documentSection, String displayFileName) {
        if (displayFileName == null || displayFileName.isBlank()) {
            return;
        }

        long deadline = System.currentTimeMillis() + 5000;
        String expected = displayFileName.trim().toUpperCase();
        while (System.currentTimeMillis() <= deadline) {
            try {
                Boolean visible = (Boolean) documentSection.evaluate("""
                        (section, expected) => {
                            const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                            return normalize(section.innerText || section.textContent).includes(expected);
                        }
                        """, expected);
                if (Boolean.TRUE.equals(visible)) {
                    return;
                }
            } catch (PlaywrightException ignored) {
            }
            page.waitForTimeout(150);
        }
    }

    private Locator resolveDocumentFileInputOrNull(Locator documentSection) {
        Locator scoped = documentSection.locator("input[type='file']");
        if (scoped.count() > 0) {
            return scoped.last();
        }

        Locator sibling = documentSection.locator("xpath=ancestor::form[1]//input[@type='file']");
        if (sibling.count() > 0) {
            return sibling.last();
        }

        return null;
    }

    private Path createTemporaryUploadFile(String filename) throws IOException {
        String simpleFileName = fileNameOnly(filename);
        Path directory = Paths.get("target", "upload-fixtures", String.valueOf(System.nanoTime()));
        Files.createDirectories(directory);
        Path output = directory.resolve(simpleFileName);

        String lowerName = simpleFileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".docx")) {
            writeMinimalDocx(output);
            return output;
        }
        if (lowerName.endsWith(".pdf")) {
            Files.writeString(output, "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n", StandardCharsets.UTF_8);
            return output;
        }

        Files.writeString(output, "placeholder upload", StandardCharsets.UTF_8);
        return output;
    }

    private void writeMinimalDocx(Path output) throws IOException {
        try (OutputStream stream = Files.newOutputStream(output);
             ZipOutputStream zip = new ZipOutputStream(stream, StandardCharsets.UTF_8)) {
            writeZipEntry(zip, "[Content_Types].xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            writeZipEntry(zip, "_rels/.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            writeZipEntry(zip, "word/document.xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p>
                          <w:r>
                            <w:t>Tradenix test upload</w:t>
                          </w:r>
                        </w:p>
                      </w:body>
                    </w:document>
                    """);
        }
    }

    private void writeZipEntry(ZipOutputStream zip, String entryName, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String fileNameOnly(String filename) {
        if (filename == null || filename.isBlank()) {
            return filename;
        }

        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return lastSlash >= 0 && lastSlash + 1 < filename.length()
                ? filename.substring(lastSlash + 1)
                : filename;
    }

    private Locator waitForDocumentSectionOrNull(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Locator section = resolveDocumentSectionOrNull();
            if (section != null) {
                return section;
            }
            page.waitForTimeout(100);
        }
        return null;
    }

    private Locator resolveDocumentSectionOrNull() {
        String documentLabel = toXpathLiteral("Document");
        Locator documentSection = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + documentLabel + "])[last()]"
                        + "/ancestor::*[(.//*[self::button or @role='button' or self::a]"
                        + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'ADD')]"
                        + " or .//input[(not(@type) or @type='text' or @type='search' or @type='file') and not(@disabled)]"
                        + " or .//textarea[not(@disabled)]"
                        + " or .//select[not(@disabled)]"
                        + " or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
        return firstVisible(documentSection);
    }

    private void fillTransportInfo(JsonNode data) {
        JsonNode cargo = data.path("cargo");
        JsonNode summary = data.path("summary");
        JsonNode totalOuterPack = summary.path("totalOuterPack");
        JsonNode totalGrossWeight = summary.path("totalGrossWeight");
        fillFieldInSection("Cargo Details", "Total Package", text(totalOuterPack, "value"));
        fillNthLookupFieldInSection("Cargo Details", 1, text(totalOuterPack, "unitCode"), text(totalOuterPack, "unitCode"));
        fillFieldInSection("Cargo Details", "Gross Weight", text(totalGrossWeight, "value"));
        fillNthLookupFieldInSection("Cargo Details", 3, text(totalGrossWeight, "unitCode"), text(totalGrossWeight, "unitCode"));

        JsonNode inwardTransport = data.path("transport").path("inwardTransport");
        JsonNode transportMeans = inwardTransport.path("transportMeans");
        JsonNode transportMode = transportMeans.path("transportMode");
        waitForAnyVisibleText(
                "Inward Flight Number",
                "Flight Number",
                "Conveyance Reference Number",
                "Transport Identifier",
                "Inward Aircraft Registration Number",
                "Inward Voyage Number",
                "Inward Vessel Name",
                "Vehicle Licence/Registration Number",
                "Inward Master Air Waybill",
                "Master Air Waybill",
                "MAWB/UCR/OBL Number",
                "Inward Ocean Bill of Lading Number",
                "Inward Ocean Bill Of Lading Number");
        fillFieldInSectionByAnyLabelIfPresent(
                "Inward Transport Means",
                text(transportMode, "conveyanceReferenceNumber"),
                "Inward Flight Number",
                "Flight Number",
                "Conveyance Reference Number",
                "Inward Voyage Number");
        fillFieldInSectionByAnyLabelIfPresent(
                "Inward Transport Means",
                text(transportMode, "transportIdentifier"),
                "Transport Identifier",
                "Inward Aircraft Registration Number",
                "Inward Vessel Name",
                "Vehicle Licence/Registration Number");
        fillFieldInSectionByAnyLabelIfPresent(
                "Inward Transport Means",
                text(transportMeans, "mawboucroblNumber"),
                "Inward Master Air Waybill",
                "Master Air Waybill",
                "MAWB/UCR/OBL Number",
                "Inward Ocean Bill of Lading Number",
                "Inward Ocean Bill Of Lading Number");
        fillDateFieldInSection("Inward Transport Means", "Arrival Date", formatUiDate(text(inwardTransport, "arrivalDate")));
        fillLookupFieldIfPresent(
                "Loading Port",
                text(inwardTransport, "loadingPort"),
                text(inwardTransport, "loadingPort"));
        fillDeclarationSpecificTransportInfo(data);
        fillTransportEquipmentDetails(cargo);
    }

    private void fillTransportEquipmentDetails(JsonNode cargo) {
        JsonNode transportEquipment = firstArrayItem(cargo.path("transportEquipment"));
        if (isMissingOrEmpty(transportEquipment)) {
            return;
        }

        Locator containerDetailsSection = resolveContainerDetailsSection();
        String containerNumber = normalize(text(transportEquipment, "equipmentID"));
        String sizeTypeCode = text(transportEquipment, "sizeTypeCode");
        String equipmentWeight = text(transportEquipment, "equipmentWeightMeasureNumeric");
        String sealNumber = text(transportEquipment.path("transportEquipmentSeal"), "sealID");

        fillNthFieldInScopeIfPresent(containerDetailsSection, 0, containerNumber);
        fillNthLookupFieldInScopeIfPresent(containerDetailsSection, 1, sizeTypeCode, sizeTypeCode);
        fillNthFieldInScopeIfPresent(containerDetailsSection, 2, equipmentWeight);
        fillNthFieldInScopeIfPresent(containerDetailsSection, 3, sealNumber);
    }

    private void fillPartyInfo(JsonNode data) {
        JsonNode party = data.path("party");
        fillPartyRow("Importer", party.path("importerParty"));
        fillPartyRow("Inward Carrier", party.path("inwardCarrierAgentParty"));
        fillPartyRow("Freight Forwarder", party.path("freightForwarderParty"));
        fillDeclarationSpecificPartyInfo(data, party);
    }

    private void fillPartyRow(String rowLabel, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        String partyName = normalize(text(identityNode.path("partyName"), "name"));
        String partyId = normalize(text(identityNode.path("partyIdentification"), "id"));
        if ((partyName == null || partyName.isBlank()) && (partyId == null || partyId.isBlank())) {
            return;
        }

        logPartyMappingState(rowLabel, "JSON value", partyName, partyId, null);

        if (partyName == null || partyName.isBlank()) {
            if (partyId != null && !partyId.isBlank() && !fillPartyIdFieldIfPresent(rowLabel, null, partyId)) {
                throw new IllegalStateException("Party UEN value was not populated for row: "
                        + rowLabel + " Expected UEN: " + partyId);
            }
            return;
        }

        Locator componentField = resolvePartyNameFieldFromComponentOrNull(rowLabel);
        Locator field = componentField != null ? componentField : resolvePartyNameField(rowLabel);
        Locator idField = resolvePartyIdFieldOrNull(rowLabel);
        field.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        logFieldMappingInfo("Party row '" + rowLabel + "' controls"
                + " -> nameControl={" + describeControl(field) + "},"
                + " idControl={" + (idField == null ? "N/A" : describeControl(idField)) + "}");

        field.scrollIntoViewIfNeeded();
        field.click(new Locator.ClickOptions().setForce(true));
        String[] selectionHints = partySelectionHints(partyName);
        String[] searchCandidates = partySearchCandidates(partyName);

        boolean matched = false;
        for (String searchCandidate : searchCandidates) {
            clearAndTypePartyField(field, searchCandidate);
            boolean suggestionSelected = attemptPartySuggestionSelection(selectionHints);
            try {
                page.keyboard().press("Tab");
            } catch (PlaywrightException ignored) {
            }
            pauseUi(UI_NEXT_FIELD_PAUSE_MS);
            boolean committedSelection = waitForCommittedPartySelection(field, 2500);
            boolean resolvedSelection = waitForResolvedPartySelection(field, partyName, null, searchCandidate, 2500);
            boolean rowValuesMatch = waitForPartyRowValues(rowLabel, partyName, null, 1500);
            boolean fieldValueMatches = waitForPartyFieldValue(field, partyName, 2500);
            String finalFieldValue = readRenderedFieldValue(field);
            String finalRowValue = readPartyRowText(rowLabel);
            logPartyMappingAttempt(
                    rowLabel,
                    searchCandidate,
                    partyName,
                    partyId,
                    committedSelection,
                    resolvedSelection,
                    rowValuesMatch,
                    fieldValueMatches,
                    finalFieldValue,
                    finalRowValue);
            boolean nameResolved = (suggestionSelected || componentField != null)
                    && (committedSelection || resolvedSelection || rowValuesMatch || fieldValueMatches);
            boolean idResolved = partyId == null
                    || partyId.isBlank()
                    || waitForStablePartyIdFieldValue(idField, partyId, 600, 1200)
                    || waitForPartyRowValues(rowLabel, partyName, partyId, 1200);
            if (!idResolved && partyId != null && !partyId.isBlank()) {
                idResolved = (componentField != null && syncPartyLookupComponentSelection(rowLabel, partyName, partyId))
                        || fillPartyIdFieldIfPresent(rowLabel, partyName, partyId)
                        || waitForStablePartyIdFieldValue(idField, partyId, 600, 1200)
                        || waitForPartyRowValues(rowLabel, partyName, partyId, 1200);
            }
            if (nameResolved && idResolved) {
                matched = true;
                logPartyMappingState(
                        rowLabel,
                        "UI final value",
                        readRenderedFieldValue(field),
                        partyId,
                        readPartyRowText(rowLabel));
                break;
            }
        }

        if (!matched) {
            capturePartyRowFailureArtifacts(rowLabel);
            throw new IllegalStateException("Party dropdown suggestion was not selected for row: "
                    + rowLabel + " Expected: " + partyName
                    + ", Actual row text: " + readPartyRowText(rowLabel)
                    + ", Current field value: " + readRenderedFieldValue(field));
        }
    }

    protected void fillPartyRowIfPresent(String rowLabel, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        String partyName = normalize(text(identityNode.path("partyName"), "name"));
        String partyId = normalize(text(identityNode.path("partyIdentification"), "id"));
        if ((partyName == null || partyName.isBlank()) && (partyId == null || partyId.isBlank())) {
            return;
        }

        try {
            fillPartyRow(rowLabel, partyNode);
        } catch (IllegalStateException exception) {
            logFieldMappingWarning("Party mapping skipped for row '" + rowLabel + "' with value '"
                    + firstNonBlank(partyName, partyId, "N/A") + "': " + exception.getMessage());
        }
    }

    private void reconcilePartyRowIfNeeded(String rowLabel, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        String partyName = normalize(text(identityNode.path("partyName"), "name"));
        String partyId = normalize(text(identityNode.path("partyIdentification"), "id"));
        if ((partyName == null || partyName.isBlank()) && (partyId == null || partyId.isBlank())) {
            return;
        }

        if (isPartyRowResolved(rowLabel, partyName, partyId)) {
            return;
        }

        logFieldMappingWarning("Party row '" + rowLabel + "' drifted after initial mapping. Reapplying lookup selection.");
        fillPartyRow(rowLabel, partyNode);
    }

    private boolean isPartyRowResolved(String rowLabel, String partyName, String partyId) {
        Locator nameField = resolvePartyNameField(rowLabel);
        Locator idField = resolvePartyIdFieldOrNull(rowLabel);

        boolean nameResolved = partyName == null
                || partyName.isBlank()
                || waitForResolvedPartyNameFieldValue(nameField, partyName, partyId, 500)
                || waitForPartyFieldValue(nameField, partyName, 500)
                || waitForPartyRowValues(rowLabel, partyName, partyId, 800);
        boolean idResolved = partyId == null
                || partyId.isBlank()
                || waitForStablePartyIdFieldValue(idField, partyId, 600, 1200)
                || waitForPartyRowValues(rowLabel, partyName, partyId, 800);
        return nameResolved && idResolved;
    }

    private JsonNode partyIdentityNode(JsonNode partyNode) {
        JsonNode partyDetail = partyNode.path("partyDetail");
        return isMissingOrEmpty(partyDetail) ? partyNode : partyDetail;
    }

    private boolean waitForPartyFieldValue(Locator field, String expectedName, int timeoutMs) {
        List<String> expectedValues = new ArrayList<>();
        appendCandidate(expectedValues, expectedName);
        return waitForAnyRenderedFieldValue(field, timeoutMs, expectedValues.toArray(String[]::new));
    }

    private boolean waitForCommittedPartySelection(Locator field, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            if (hasCommittedPartySelection(field)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean waitForResolvedPartySelection(
            Locator field,
            String partyName,
            String partyId,
            String searchCandidate,
            int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            if (isResolvedPartySelectionValue(readRenderedFieldValue(field), partyName, partyId, searchCandidate)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean isResolvedPartySelectionValue(
            String renderedValue,
            String partyName,
            String partyId,
            String searchCandidate) {
        String normalizedValue = normalize(renderedValue);
        if (normalizedValue.isBlank()) {
            return false;
        }

        String normalizedSearchCandidate = normalize(searchCandidate);
        String normalizedPartyName = normalize(partyName);
        String normalizedPartyId = normalize(partyId);
        if (!normalizedPartyName.isBlank()
                && (normalizedValue.equalsIgnoreCase(normalizedPartyName)
                || normalizedValue.contains(normalizedPartyName)
                || normalizedPartyName.contains(normalizedValue))
                && ((normalizedPartyId == null || normalizedPartyId.isBlank())
                || !looksLikePartyLookupCode(normalizedPartyName)
                || !normalizedValue.equalsIgnoreCase(normalizedSearchCandidate))) {
            return true;
        }

        if (!looksLikePartyLookupCode(normalizedSearchCandidate)) {
            return false;
        }

        if (normalizedValue.equalsIgnoreCase(normalizedSearchCandidate)) {
            return false;
        }

        if (!normalizedPartyId.isBlank() && normalizedValue.equalsIgnoreCase(normalizedPartyId)) {
            return false;
        }

        return normalizedValue.length() > normalizedSearchCandidate.length();
    }

    private boolean looksLikePartyLookupCode(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return false;
        }
        return normalizedValue.length() <= 12
                && !normalizedValue.contains(" ")
                && normalizedValue.matches("[A-Z0-9._&/-]+");
    }

    private boolean hasCommittedPartySelection(Locator field) {
        try {
            return Boolean.TRUE.equals(field.evaluate("""
                    element => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const container = element.closest('.ng-select, [role="combobox"], [class*="select"], [class*="combobox"]')
                            || element.parentElement;
                        if (!container) {
                            return false;
                        }

                        const selectedText = normalize(
                            container.querySelector('.ng-value-label, .selected-item, .mat-mdc-select-value-text, [class*="single-label"], [class*="value-label"]')
                                ?.textContent);
                        const rawValue = normalize(element.value);
                        const hasClearAction = !!container.querySelector(
                            '.ng-clear-wrapper, .ng-clear, .ng-value-icon, [aria-label*="clear" i], [title*="clear" i]');
                        const ariaExpanded = normalize(container.getAttribute('aria-expanded'));

                        return !!selectedText && (selectedText !== rawValue || hasClearAction || ariaExpanded === 'FALSE');
                    }
                    """));
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void ensurePartyRowValues(String rowLabel, String expectedName) {
        if (!waitForPartyRowValues(rowLabel, expectedName, 1500)) {
            Locator nameField = resolveFirstFieldInRow(rowLabel);
            throw new IllegalStateException(
                    "Party value was not rendered for row " + rowLabel + ". Expected name: "
                            + expectedName
                            + ", Actual name: " + readRenderedFieldValue(nameField)
                            + ", Actual row text: " + readPartyRowText(rowLabel));
        }
    }

    private boolean waitForPartyRowValues(String rowLabel, String expectedName, int timeoutMs) {
        return waitForPartyRowValues(rowLabel, expectedName, null, timeoutMs);
    }

    private boolean waitForPartyRowValues(String rowLabel, String expectedName, String expectedId, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String normalizedExpectedName = normalize(expectedName);
        String normalizedExpectedId = normalize(expectedId);
        while (System.currentTimeMillis() <= deadline) {
            String rowText = readPartyRowText(rowLabel);
            boolean nameMatches = normalizedExpectedName.isBlank()
                    || rowText.equalsIgnoreCase(normalizedExpectedName)
                    || rowText.contains(normalizedExpectedName)
                    || normalizedExpectedName.contains(rowText);
            boolean idMatches = normalizedExpectedId.isBlank()
                    || rowText.equalsIgnoreCase(normalizedExpectedId)
                    || rowText.contains(normalizedExpectedId)
                    || normalizedExpectedId.contains(rowText);
            if (nameMatches && idMatches) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean fillPartyIdFieldIfPresent(String rowLabel, String partyName, String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return false;
        }

        Locator idField = resolvePartyIdFieldOrNull(rowLabel);
        if (idField == null || !idField.isVisible()) {
            return false;
        }

        Locator nameField = resolvePartyNameField(rowLabel);

        boolean nameAlreadyMatches = waitForResolvedPartyNameFieldValue(nameField, partyName, partyId, 500)
                || waitForPartyFieldValue(nameField, partyName, 500)
                || waitForPartyRowValues(rowLabel, partyName, null, 800);
        if (waitForStablePartyIdFieldValue(idField, partyId, 600, 1200)
                && nameAlreadyMatches) {
            return true;
        }

        boolean idMatches = false;
        for (int attempt = 0; attempt < 2 && !idMatches; attempt++) {
            focusAndType(idField, partyId, false);
            if (!waitForStablePartyIdFieldValue(idField, partyId, 600, 1800)) {
                ensureTextFieldValue(idField, partyId);
                idMatches = waitForStablePartyIdFieldValue(idField, partyId, 600, 1200);
            } else {
                idMatches = true;
            }
        }
        boolean nameMatches = waitForResolvedPartyNameFieldValue(nameField, partyName, partyId, 1500)
                || waitForPartyFieldValue(nameField, partyName, 1500)
                || waitForPartyRowValues(rowLabel, partyName, null, 1500);
        boolean rowMatches = waitForPartyRowValues(rowLabel, partyName, partyId, 1500);
        return idMatches && (nameMatches || rowMatches);
    }

    private boolean waitForStablePartyIdFieldValue(Locator field, String expectedValue, int stableMs, int timeoutMs) {
        if (field == null || expectedValue == null || expectedValue.isBlank()) {
            return false;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        long stableSince = -1L;
        while (System.currentTimeMillis() <= deadline) {
            String currentValue = normalize(inputValueOrEmpty(field));
            if (!currentValue.isBlank() && renderedFieldValueMatches(currentValue, expectedValue)) {
                if (stableSince < 0) {
                    stableSince = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - stableSince >= stableMs) {
                    return true;
                }
            } else {
                stableSince = -1L;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean waitForResolvedPartyNameFieldValue(Locator field, String partyName, String partyId, int timeoutMs) {
        if (field == null || partyName == null || partyName.isBlank()) {
            return false;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            String currentValue = readRenderedFieldValue(field);
            if (isResolvedPartySelectionValue(currentValue, partyName, partyId, partyName)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean hasVisiblePartyIdField(String rowLabel) {
        Locator idField = resolvePartyIdFieldOrNull(rowLabel);
        return idField != null && idField.isVisible();
    }

    private boolean syncPartyLookupComponentSelection(String rowLabel, String partyName, String partyId) {
        String selector = partyNameComponentSelector(rowLabel);
        if (selector == null || selector.isBlank()) {
            return false;
        }

        try {
            Object synced = page.evaluate("""
                    args => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const hosts = Array.from(document.querySelectorAll(args.selector))
                            .filter(element => !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length));
                        const host = hosts[0];
                        if (!host || typeof window.ng === 'undefined' || typeof window.ng.getComponent !== 'function') {
                            return false;
                        }

                        const component = window.ng.getComponent(host);
                        if (!component || !Array.isArray(component.allOptions) || component.allOptions.length === 0) {
                            return false;
                        }

                        const expectedName = normalize(args.partyName);
                        const expectedId = normalize(args.partyId);
                        const scoreOption = option => {
                            const code = normalize(option?.code);
                            const description = normalize(option?.description);
                            const combined = normalize(`${option?.code || ''} ${option?.description || ''}`);
                            let score = 0;
                            if (expectedId && code === expectedId) {
                                score += 1000;
                            } else if (expectedId && combined.includes(expectedId)) {
                                score += 500;
                            }
                            if (expectedName && description === expectedName) {
                                score += 400;
                            } else if (expectedName && (description.includes(expectedName) || expectedName.includes(description))) {
                                score += 200;
                            }
                            return score;
                        };

                        const ranked = component.allOptions
                            .map(option => ({ option, score: scoreOption(option) }))
                            .filter(entry => entry.score > 0)
                            .sort((left, right) => right.score - left.score);
                        const selected = ranked[0]?.option;
                        if (!selected) {
                            return false;
                        }

                        const display = selected.description || selected.code || args.partyName || args.partyId || '';
                        component._value = selected.code || component._value;
                        if ('inputDisplayValue' in component) {
                            component.inputDisplayValue = display;
                        }
                        if ('displayValue' in component) {
                            component.displayValue = display;
                        }

                        const field = host.querySelector('input, textarea, select');
                        if (field && 'value' in field) {
                            field.value = display;
                            field.dispatchEvent(new Event('input', { bubbles: true }));
                            field.dispatchEvent(new Event('change', { bubbles: true }));
                            field.dispatchEvent(new Event('blur', { bubbles: true }));
                        }
                        if (typeof component.selectOptionItem === 'function') {
                            component.selectOptionItem(selected);
                        }
                        if (typeof component.selectOptionLabel === 'function') {
                            component.selectOptionLabel(selected);
                        }
                        if (typeof component.onChange === 'function') {
                            component.onChange(selected.code || display);
                        }
                        if (typeof component.onTouched === 'function') {
                            component.onTouched();
                        }
                        return true;
                    }
                    """, java.util.Map.of(
                    "selector", selector,
                    "partyName", firstNonBlank(partyName, ""),
                    "partyId", firstNonBlank(partyId, "")));

            if (!Boolean.TRUE.equals(synced)) {
                return false;
            }

            pauseUi(UI_ACTION_PAUSE_MS);
            Locator idField = resolvePartyIdFieldOrNull(rowLabel);
            return partyId == null
                    || partyId.isBlank()
                    || waitForStablePartyIdFieldValue(idField, partyId, 600, 1500);
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void fillInvoiceInfo(JsonNode data) {
        JsonNode invoice = firstArrayItem(data.path("invoice"));
        String supplierManufacturerName = text(invoice.path("supplierManufacturerParty"), "name");
        fillField("Invoice Number", text(invoice, "invoiceNumber"));
        fillDateField("Invoice Date", formatUiDate(text(invoice, "invoiceDate")));
        fillInvoiceTermType(text(invoice, "unitPriceTermType"));
        fillSupplierManufacturerName(supplierManufacturerName);

        JsonNode totalInvoiceValue = invoice.path("totalInvoiceValue");
        fillLookupFieldInChargeRow("A. Total Invoice", "Currency", text(totalInvoiceValue.path("amount"), "currencyID"));
        fillFieldInChargeRow("A. Total Invoice", "Amount", text(totalInvoiceValue.path("amount"), "value"));

        JsonNode freightCharge = invoice.path("freightCharge");
        fillLookupFieldInChargeRow("C. Freight Charge", "Currency", text(freightCharge.path("amount"), "currencyID"));
        fillFieldInChargeRow("C. Freight Charge", "Amount", text(freightCharge.path("amount"), "value"));

        JsonNode insuranceCharge = invoice.path("insuranceCharge");
        fillLookupFieldInChargeRow("E. Insurance Charge", "Currency", text(insuranceCharge.path("amount"), "currencyID"));
    }

    protected void fillInvoiceTermType(String termType) {
        if (termType == null || termType.isBlank()) {
            return;
        }

        Locator field = resolveFieldByLabel("Term Type", 0);
        String normalizedTermType = normalize(termType).toUpperCase();
        switch (normalizedTermType) {
            case "CIF" -> openLookupAndChooseOption(
                    field,
                    "CIF",
                    "COST INSURANCE AND FREIGHT",
                    "COST, INSURANCE AND FREIGHT");
            case "FOB" -> openLookupAndChooseOption(
                    field,
                    "FOB",
                    "FREE ON BOARD");
            case "CFR" -> openLookupAndChooseOption(
                    field,
                    "CFR",
                    "COST AND FREIGHT");
            case "EXW" -> openLookupAndChooseOption(
                    field,
                    "EXW",
                    "EX WORKS");
            default -> fillLookupField("Term Type", termType, termType);
        }
    }

    private void fillItemInfo(JsonNode data) {
        JsonNode formMetaData = data.path("formMetaData");
        JsonNode invoice = firstArrayItem(data.path("invoice"));
        JsonNode item = firstArrayItem(data.path("item"));
        JsonNode itemQuantity = item.path("itemQuantity");
        JsonNode packingDescription = item.path("packingDescription");
        JsonNode motorVehicle = item.path("motorVehicle");
        JsonNode lotIdentification = item.path("lotIdentification");
        JsonNode shippingMarksInformation = firstArrayItem(item.path("shippingMarksInformation"));
        JsonNode cascProduct = item.path("cascProduct");

        fillField("Inward HAWB", text(item, "inHawbHucrHblNumber"));
        fillSelectLikeLookupFieldIfPresent(
                "Invoice Number",
                firstNonBlank(text(item, "itemInvoiceNumber"), text(invoice, "invoiceNumber")),
                firstNonBlank(text(item, "itemInvoiceNumber"), text(invoice, "invoiceNumber")));
        fillSelectLikeLookupFieldIfPresent(
                "HS Code",
                text(item, "itemHarmonizedSystemCode"),
                text(item, "itemHarmonizedSystemCode"));
        fillFirstMatchingFieldIfPresent(
                text(item, "goodsDescription"),
                "Goods Description",
                "Item description",
                "Item Description");
        fillField("Brand", text(item, "brandName"));
        fillLookupFieldIfPresent("COO", text(item, "originCountry"), text(item, "originCountry"));
        fillFieldIfPresent("Model", text(item, "modelDescription"));
        boolean hasPackingDescription = !isMissingOrEmpty(packingDescription);
        if (hasPackingDescription || formMetaData.path("itemPackingIsActive").path(0).asBoolean(false)) {
            setCheckboxByLabel("Item Packing", true);
            pauseUi(UI_ACTION_PAUSE_MS);
        }
        if (hasPackingDescription) {
            fillPackingDescription(packingDescription);
        }
        fillItemQuantityDetails(itemQuantity);
        fillVehicleDetails(motorVehicle);
        JsonNode transactionValue = item.path("transactionValue");
        fillItemValues(item, transactionValue, formMetaData);
        fillLotIdentification(item, lotIdentification);
        fillShippingMarks(shippingMarksInformation);

        fillCascDetails(cascProduct);
        fillDeclarationSpecificItemInfo(data, item);
    }

    protected void fillVehicleDetails(JsonNode motorVehicle) {
        if (isMissingOrEmpty(motorVehicle)) {
            return;
        }

        JsonNode engineCapacity = motorVehicle.path("engineCapacity");
        String engineCapacityValue = text(engineCapacity, "value");
        String engineCapacityUnitCode = text(engineCapacity, "unitCode");
        String originalRegistrationDate = formatUiDate(text(motorVehicle, "originalRegistrationDate"));

        if ((engineCapacityValue == null || engineCapacityValue.isBlank())
                && (engineCapacityUnitCode == null || engineCapacityUnitCode.isBlank())
                && (originalRegistrationDate == null || originalRegistrationDate.isBlank())) {
            return;
        }

        Locator vehicleDetailsSection = resolveSection("Vehicle Details");
        fillFieldAfterScopeLabelIfPresent(vehicleDetailsSection, "Engine Capacity", 0, engineCapacityValue);
        fillLookupFieldAfterScopeLabelIfPresent(
                vehicleDetailsSection,
                "Unit",
                0,
                engineCapacityUnitCode,
                engineCapacityUnitCode);
        fillDateFieldInSectionIfPresent("Vehicle Details", "Registration Date", originalRegistrationDate);
    }

    private void fillPackingDescription(JsonNode packingDescription) {
        ensurePackingDescriptionExpanded();
        Locator packingDescriptionSection = resolvePackingDescriptionSection();
        JsonNode outerPackQuantity = packingDescription.path("outerPackQuantity");
        JsonNode inPackQuantity = packingDescription.path("inPackQuantity");
        JsonNode innerPackQuantity = packingDescription.path("innerPackQuantity");
        JsonNode inmostPackQuantity = packingDescription.path("inmostPackQuantity");
        String defaultPackingUnitCode = firstNonBlank(
                text(outerPackQuantity, "unitCode"),
                text(inPackQuantity, "unitCode"),
                text(innerPackQuantity, "unitCode"),
                text(inmostPackQuantity, "unitCode"));

        fillPackingQuantityRowInScope(packingDescriptionSection, "Outer Pack Qty", outerPackQuantity, defaultPackingUnitCode);
        fillPackingQuantityRowInScope(packingDescriptionSection, "In Pack Qty", inPackQuantity, defaultPackingUnitCode);
        fillPackingQuantityRowInScope(packingDescriptionSection, "Inner Pack Qty", innerPackQuantity, defaultPackingUnitCode);
        fillPackingQuantityRowInScope(packingDescriptionSection, "Inmost Pack Qty", inmostPackQuantity, defaultPackingUnitCode);
    }

    private void ensurePackingDescriptionExpanded() {
        String[] titles = new String[] { "Packing Description", "Packing Details" };
        for (String title : titles) {
            try {
                ensureAccordionExpanded(
                        title,
                        "Outer Pack Qty",
                        "In Pack Qty",
                        "Inner Pack Qty",
                        "Inmost Pack Qty");
                Locator section = resolvePackingDescriptionSectionOrNull(title);
                if (section != null) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void fillPackingQuantityRowInScope(
            Locator packingDescriptionSection,
            String rowLabel,
            JsonNode quantityNode,
            String defaultUnitCode) {
        if (isMissingOrEmpty(quantityNode)) {
            return;
        }

        String value = text(quantityNode, "value");
        String unitCode = firstNonBlank(text(quantityNode, "unitCode"), defaultUnitCode);
        Locator valueField = resolvePackingQuantityFieldOrNull(packingDescriptionSection, rowLabel, 0);
        if (valueField != null && value != null && !value.isBlank()) {
            focusAndType(valueField, value, false);
        }

        Locator unitField = resolvePackingQuantityFieldOrNull(packingDescriptionSection, rowLabel, 1);
        if (unitField != null && unitCode != null && !unitCode.isBlank()) {
            focusAndType(unitField, unitCode, true, unitCode);
        }
    }

    protected void fillItemQuantityDetails(JsonNode itemQuantity) {
        Locator itemQuantitySection = resolveItemQuantitySection();
        JsonNode dutiableQuantity = itemQuantity.path("dutiableQuantity");
        JsonNode totalDutiableQuantity = itemQuantity.path("totalDutiableQuantity");
        JsonNode hsQuantity = firstNonBlankNode(itemQuantity.path("hsQuantity"), itemQuantity.path("harmonizedSystemQuantity"));
        boolean hasOtherQtyFields = hasOtherQuantityFields(itemQuantity);

        setCheckboxByLabelIfDifferent("Other Qty Fields", hasOtherQtyFields);
        fillQuantityRowInScope(itemQuantitySection, "Dutiable Quantity", dutiableQuantity);
        fillQuantityRowInScope(itemQuantitySection, "Total Dutiable Qty", totalDutiableQuantity);
        fillQuantityRowInScope(itemQuantitySection, "Total Dutiable Quantity", totalDutiableQuantity);
        fillQuantityRowInScope(itemQuantitySection, "HS Quantity", hsQuantity);
        fillFieldAfterScopeLabelIfPresent(itemQuantitySection, "Alcohol %", 0, text(itemQuantity, "alcoholPercent"));
    }

    protected boolean hasOtherQuantityFields(JsonNode itemQuantity) {
        if (isMissingOrEmpty(itemQuantity)) {
            return false;
        }
        return !isMissingOrEmpty(itemQuantity.path("dutiableQuantity"))
                || !isMissingOrEmpty(itemQuantity.path("totalDutiableQuantity"));
    }

    protected void fillQuantityRowInScope(Locator scope, String rowLabel, JsonNode quantityNode) {
        fillQuantityRowInScope(scope, rowLabel, quantityNode, null);
    }

    protected void fillQuantityRowInScope(Locator scope, String rowLabel, JsonNode quantityNode, String defaultUnitCode) {
        if (isMissingOrEmpty(quantityNode)) {
            return;
        }

        fillFieldAfterScopeLabelIfPresent(scope, rowLabel, 0, text(quantityNode, "value"));
        String unitCode = firstNonBlank(text(quantityNode, "unitCode"), defaultUnitCode);
        fillLookupFieldAfterScopeLabelIfPresent(scope, rowLabel, 1, unitCode, unitCode);
    }

    protected void fillItemValues(JsonNode item, JsonNode transactionValue, JsonNode formMetaData) {
        if (isMissingOrEmpty(transactionValue) && isMissingOrEmpty(item)) {
            return;
        }

        Locator itemValuesSection = resolveItemValuesSection();
        JsonNode unitPriceValue = transactionValue.path("unitPriceValue");
        JsonNode optionalItemCharge = transactionValue.path("optionalItemCharge");
        String lastSellingPrice = firstNonBlank(
                text(transactionValue, "lastSellingPriceValue", "lastSellingPrice", "lastsellingPriceValue", "lastsellingPrice"),
                text(item, "lastSellingPriceValue", "lastSellingPrice", "lastsellingPriceValue", "lastsellingPrice"));
        boolean hasOtherAmountFields = hasOtherAmountFields(formMetaData, optionalItemCharge, lastSellingPrice);

        setCheckboxByLabelIfDifferent("Other Amount Fields", hasOtherAmountFields);
        if (hasOtherAmountFields) {
            pauseUi(UI_ACTION_PAUSE_MS);
        }

        fillFieldAfterScopeLabelIfPresent(
                itemValuesSection,
                "Item Value",
                0,
                normalizeNumericForEntry(text(unitPriceValue.path("amount"), "value")));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemValuesSection,
                "Item Value",
                1,
                text(unitPriceValue.path("amount"), "currencyID"),
                text(unitPriceValue.path("amount"), "currencyID"));
        fillFieldAfterScopeLabelIfPresent(
                itemValuesSection,
                "Optional Charges",
                0,
                normalizeNumericForEntry(text(optionalItemCharge.path("amount"), "value")));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemValuesSection,
                "Optional Charges",
                1,
                text(optionalItemCharge.path("amount"), "currencyID"),
                text(optionalItemCharge.path("amount"), "currencyID"));
        fillValidatedFieldAfterScopeLabelIfPresent(
                itemValuesSection,
                "Last Selling Price",
                0,
                normalizeNumericForEntry(lastSellingPrice));
    }

    protected void fillItemValues(JsonNode transactionValue) {
        fillItemValues(MissingNode.getInstance(), transactionValue, MissingNode.getInstance());
    }

    protected boolean hasOtherAmountFields(JsonNode formMetaData, JsonNode optionalItemCharge, String lastSellingPrice) {
        boolean metadataEnabled = formMetaData != null
                && formMetaData.path("otherAmountFieldsIsActive").path(0).asBoolean(false);
        return metadataEnabled
                || !isMissingOrEmpty(optionalItemCharge)
                || (lastSellingPrice != null && !lastSellingPrice.isBlank());
    }

    private Locator resolveItemValuesSection() {
        waitForFormControls();
        String[] titles = new String[] { "Item Values", "Item Quantity & value", "Item Quantity & Value" };
        for (String title : titles) {
            String escapedTitle = toXpathLiteral(title);
            Locator section = page.locator(
                    "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                            + "/ancestor::*[(.//*[contains(normalize-space(translate(., '*', '')), 'Item Value')]"
                            + " or .//*[contains(normalize-space(translate(., '*', '')), 'Optional Charges')]"
                            + " or .//*[contains(normalize-space(translate(., '*', '')), 'Item CIF/FOB Value (SGD)')]"
                            + " or .//*[contains(normalize-space(translate(., '*', '')), 'Last Selling Price')])"
                            + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
            Locator visibleSection = firstVisible(section);
            if (visibleSection != null) {
                return visibleSection;
            }

            Locator titledContainer = page.locator(
                    "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                            + "/ancestor::*[.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]");
            Locator visibleTitledContainer = firstVisible(titledContainer);
            if (visibleTitledContainer != null) {
                return visibleTitledContainer;
            }
        }
        throw new IllegalStateException("Item Values section was not visible.");
    }

    protected void fillLotIdentification(JsonNode item, JsonNode lotIdentification) {
        JsonNode tariff = item.path("tariff");
        String preferentialCode = text(tariff, "preferentialCode");
        String marking = text(lotIdentification, "marking");
        String currentLotNumber = text(lotIdentification, "currentLotNumber");
        String previousLotNumber = text(lotIdentification, "previousLotNumber");

        if ((preferentialCode == null || preferentialCode.isBlank())
                && (marking == null || marking.isBlank())
                && (currentLotNumber == null || currentLotNumber.isBlank())
                && (previousLotNumber == null || previousLotNumber.isBlank())) {
            return;
        }

        setCheckboxByLabel("Tariff & Lot Identification", true);
        pauseUi(UI_ACTION_PAUSE_MS);

        Locator lotIdentificationSection = resolveSection("Tariff & Lot Identification");
        fillLookupFieldAfterScopeLabelIfPresent(
                lotIdentificationSection,
                "Preferential Code",
                0,
                preferentialCode,
                preferentialCode);
        fillLookupFieldAfterScopeLabelIfPresent(
                lotIdentificationSection,
                "Markings",
                0,
                marking,
                marking);
        fillFieldAfterScopeLabelIfPresent(
                lotIdentificationSection,
                "Current Lot",
                0,
                currentLotNumber);
        fillFieldAfterScopeLabelIfPresent(
                lotIdentificationSection,
                "Previous Lot",
                0,
                previousLotNumber);
    }

    protected void fillShippingMarks(JsonNode shippingMarksInformation) {
        List<String> shippingMarks = new ArrayList<>();
        JsonNode shippingMarksNode = shippingMarksInformation.path("shippingMarks");
        if (shippingMarksNode.isArray()) {
            for (JsonNode shippingMark : shippingMarksNode) {
                String value = shippingMark == null || shippingMark.isMissingNode() || shippingMark.isNull()
                        ? null
                        : normalize(shippingMark.asText());
                if (value != null && !value.isBlank()) {
                    shippingMarks.add(value);
                }
            }
        } else {
            String value = shippingMarksNode == null || shippingMarksNode.isMissingNode() || shippingMarksNode.isNull()
                    ? null
                    : normalize(shippingMarksNode.asText());
            if (value != null && !value.isBlank()) {
                shippingMarks.add(value);
            }
        }

        if (shippingMarks.isEmpty()) {
            return;
        }

        setCheckboxByLabel("Shipping Marks", true);
        pauseUi(UI_ACTION_PAUSE_MS);

        Locator shippingMarksSection = resolveShippingMarksSection();
        Locator field = resolveShippingMarksInputBoxOrNull(shippingMarksSection, 0);
        if (field == null && clickButtonInScopeIfVisible(shippingMarksSection, "ADD GROUP")) {
            pauseUi(UI_ACTION_PAUSE_MS);
            field = resolveShippingMarksInputBoxOrNull(shippingMarksSection, 0);
        }

        for (int index = 0; index < shippingMarks.size(); index++) {
            if (index > 0) {
                clickButtonInScope(shippingMarksSection, "ADD LINE");
                pauseUi(UI_ACTION_PAUSE_MS);
            }

            field = waitForShippingMarksInputBoxOrNull(shippingMarksSection, index, 3000);
            if (field == null && index == 0) {
                Locator target = resolveShippingMarksActivatorOrNull(shippingMarksSection);
                if (target == null) {
                    target = firstVisible(shippingMarksSection);
                }
                if (target != null) {
                    closeTransientOverlays();
                    target.scrollIntoViewIfNeeded();
                    target.click(new Locator.ClickOptions().setForce(true));
                    pauseUi(UI_ACTION_PAUSE_MS);
                    field = waitForShippingMarksInputBoxOrNull(shippingMarksSection, index, 3000);
                }
            }

            if (field != null) {
                focusAndType(field, shippingMarks.get(index), false);
                continue;
            }

            if (index == 0) {
                typeIntoFocusedEditor(shippingMarks.get(index));
                continue;
            }

            throw new IllegalStateException("Shipping Marks Line " + (index + 1) + " input was not visible.");
        }
    }

    protected void fillCascDetails(JsonNode cascProduct) {
        if (isMissingOrEmpty(cascProduct)) {
            return;
        }

        Locator cascSection = resolveSection("CASC Details");
        if (cascProduct.isArray()) {
            for (int index = 0; index < cascProduct.size(); index++) {
                JsonNode cascProductEntry = cascProduct.get(index);
                if (isMissingOrEmpty(cascProductEntry)) {
                    continue;
                }
                if (index > 0) {
                    clickAddCascProductButton(cascSection);
                    pauseUi(UI_ACTION_PAUSE_MS);
                }
                Locator cascRow = waitForCascRow(cascSection, index, 5000);
                fillSingleCascProduct(cascSection, cascRow, cascProductEntry);
            }
            return;
        }

        Locator cascRow = resolveCascRow(cascSection, 0);
        fillSingleCascProduct(cascSection, cascRow, cascProduct);
    }

    private void fillSingleCascProduct(Locator cascSection, Locator cascRow, JsonNode cascProduct) {
        String productCode = text(cascProduct, "cascProductCode");
        if (productCode != null && !productCode.isBlank()) {
            Locator productCodeField = resolveCascPrimaryEditableField(cascRow, 0);
            fillVerifiedLookupField(productCodeField, productCode, productCode);
        }

        JsonNode cascQuantity = cascProduct.path("cascProductQuantity");
        String quantityValue = text(cascQuantity, "value");
        if (quantityValue != null && !quantityValue.isBlank()) {
            Locator quantityField = resolveCascPrimaryEditableField(cascRow, 1);
            focusAndType(quantityField, quantityValue, false);
        }

        String quantityUom = text(cascQuantity, "unitCode");
        if (quantityUom != null && !quantityUom.isBlank()) {
            Locator uomField = resolveCascPrimaryEditableField(cascRow, 2);
            focusAndType(uomField, quantityUom, true, quantityUom);
        }

        Locator cascBlock = resolveCascProductBlock(cascRow);
        JsonNode additionalCascIdentifications = cascProduct.path("additionalCascIdentification");
        if (!isMissingOrEmpty(additionalCascIdentifications)) {
            fillAdditionalCascIdentifications(cascRow, cascBlock, additionalCascIdentifications);
        }

        String endUseDescription = text(cascProduct.path("endUseDescription"), "endUseLine");
        if (endUseDescription != null && !endUseDescription.isBlank()) {
            fillCascEndUseDescription(cascRow, cascBlock, endUseDescription);
        }
    }

    private void fillCascEndUseDescription(Locator cascRow, Locator cascBlock, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        Locator field = resolveCascEndUseDescriptionFieldOrNull(cascRow, cascBlock);
        if (field == null) {
            throw new IllegalStateException("End User Description (Strategic Goods) field was not visible.");
        }

        focusAndType(field, value, false);
        if (!waitForAnyRenderedFieldValue(field, 1000, value)) {
            ensureTextFieldValue(field, value);
        }
        if (!waitForAnyRenderedFieldValue(field, 1500, value)) {
            throw new IllegalStateException("End User Description (Strategic Goods) was not rendered. Expected: "
                    + value + ", Actual: " + readRenderedFieldValue(field));
        }
    }

    private void fillAdditionalCascIdentifications(
            Locator cascRow,
            Locator cascBlock,
            JsonNode additionalCascIdentifications) {
        if (isMissingOrEmpty(additionalCascIdentifications)) {
            return;
        }

        if (hasButtonInScopeVisible(cascRow, "ADDITIONAL CASC")) {
            clickButtonInScope(cascRow, "ADDITIONAL CASC", "ADDITIONAL", "ADDITIONAL CA");
            pauseUi(UI_ACTION_PAUSE_MS);
            cascBlock = resolveCascProductBlock(cascRow);
        }

        waitForAdditionalCascSection(cascRow, cascBlock, 5000);
        for (int index = 0; index < additionalCascIdentifications.size(); index++) {
            JsonNode additionalCascIdentification = additionalCascIdentifications.get(index);
            if (isMissingOrEmpty(additionalCascIdentification)) {
                continue;
            }

            Locator additionalCascRow = waitForAdditionalCascEntryRow(cascBlock, index, 5000);
            fillAdditionalCascIdentification(additionalCascRow, additionalCascIdentification);

            if (index < additionalCascIdentifications.size() - 1) {
                clickAdditionalCascAddButton(cascBlock);
                pauseUi(UI_ACTION_PAUSE_MS);
            }
        }

        verifyAdditionalCascIdentifications(cascBlock, additionalCascIdentifications);
    }

    private void fillAdditionalCascIdentification(Locator additionalCascRow, JsonNode additionalCascIdentification) {
        String cascCodeOne = text(additionalCascIdentification, "cascCodeOne");
        if (cascCodeOne != null && !cascCodeOne.isBlank()) {
            fillAdditionalCascCodeField(additionalCascRow, "Code 1", cascCodeOne);
        }

        fillAdditionalCascCodeField(additionalCascRow, "Code 2", text(additionalCascIdentification, "cascCodeTwo"));
        fillAdditionalCascCodeField(additionalCascRow, "Code 3", text(additionalCascIdentification, "cascCodeThree"));
    }

    private void fillAdditionalCascCodeField(Locator additionalCascRow, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        Locator field = waitForAdditionalCascEditableFieldOrNull(additionalCascRow, label, 3000);
        if (field == null) {
            throw new IllegalStateException("Additional CASC " + label + " field was not visible.");
        }
        fillAdditionalCascTextField(field, value, label);
    }

    private Locator waitForAdditionalCascEditableFieldOrNull(Locator additionalCascRow, String label, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            Locator field = resolveAdditionalCascFieldOrNull(additionalCascRow, label);
            if (field != null) {
                return field;
            }
            page.waitForTimeout(100);
        }
        return null;
    }

    private void fillAdditionalCascTextField(Locator field, String value, String label) {
        fillVerifiedTextField(field, value, "Additional CASC " + label);
    }

    private void verifyAdditionalCascIdentifications(Locator cascBlock, JsonNode additionalCascIdentifications) {
        if (isMissingOrEmpty(additionalCascIdentifications)) {
            return;
        }

        for (int index = 0; index < additionalCascIdentifications.size(); index++) {
            JsonNode expectedRow = additionalCascIdentifications.get(index);
            if (isMissingOrEmpty(expectedRow)) {
                continue;
            }

            Locator actualRow = waitForAdditionalCascEntryRow(cascBlock, index, 3000);
            assertAdditionalCascFieldValue(actualRow, "Code 1", text(expectedRow, "cascCodeOne"));
            assertAdditionalCascFieldValue(actualRow, "Code 2", text(expectedRow, "cascCodeTwo"));
            assertAdditionalCascFieldValue(actualRow, "Code 3", text(expectedRow, "cascCodeThree"));
        }
    }

    private void assertAdditionalCascFieldValue(Locator additionalCascRow, String label, String expectedValue) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return;
        }

        Locator field = waitForAdditionalCascEditableFieldOrNull(additionalCascRow, label, 2000);
        if (field == null) {
            throw new IllegalStateException("Additional CASC " + label + " field was not visible for verification.");
        }

        if (!waitForAnyRenderedFieldValue(field, 1500, expectedValue)) {
            throw new IllegalStateException("Additional CASC " + label + " row verification failed. Expected: "
                    + expectedValue + ", Actual: " + readRenderedFieldValue(field));
        }
    }

    private void fillSummary(JsonNode data) {
        openSection("Summary (Y)");

        JsonNode formMetaData = data.path("formMetaData");
        Locator remarksCard = resolveSummaryCard(
                "Remarks",
                "Internal Remarks",
                "Customer Remarks");
        fillFieldAfterScopeLabelIfPresent(
                remarksCard,
                "Remarks",
                0,
                text(data.path("header").path("remarks"), "freeText"));
        fillFieldAfterScopeLabelIfPresent(
                remarksCard,
                "Internal Remarks",
                0,
                firstNonBlank(
                        text(formMetaData, "internalRemarks"),
                        text(data.path("header").path("remarks"), "internalText")));
        fillFieldAfterScopeLabelIfPresent(
                remarksCard,
                "Customer Remarks",
                0,
                firstNonBlank(
                        text(formMetaData, "customerRemarks"),
                        text(data.path("header").path("remarks"), "customerText")));

        if (data.path("header").path("declarationIndicator").asBoolean(false)
                || formMetaData.path("declarationIndicator").asBoolean(false)) {
            setDeclarationIndicatorInSummary(true);
        }
        saveDraftAndWaitForCompletion();
    }

    private Locator resolveSummaryCard(String title, String... expectedTexts) {
        String escapedTitle = toXpathLiteral(title);
        StringBuilder xpath = new StringBuilder(
                "(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[");
        for (int index = 0; index < expectedTexts.length; index++) {
            if (index > 0) {
                xpath.append(" and ");
            }
            xpath.append(".//*[contains(normalize-space(translate(., '*', '')), ")
                    .append(toXpathLiteral(expectedTexts[index]))
                    .append(")]");
        }
        xpath.append("][1]");
        Locator locator = page.locator("xpath=" + xpath);
        return firstVisible(locator);
    }

    private boolean shouldSubmitDeclaration(JsonNode data) {
        if (data.path("summary").has("submitDeclaration")) {
            return data.path("summary").path("submitDeclaration").asBoolean(false);
        }
        if (data.path("formMetaData").has("submitDeclaration")) {
            return data.path("formMetaData").path("submitDeclaration").asBoolean(false);
        }
        return true;
    }

    protected void fillDeclarationType(String declarationType) {
        fillDeclarationType(MissingNode.getInstance(), declarationType);
    }

    protected void fillDeclarationType(JsonNode data, String declarationType) {
        if (declarationType == null || declarationType.isBlank()) {
            return;
        }

        Locator field = resolveFieldByLabelInSection("Declaration Info", "Declaration Type", 0);
        String permitType = DeclarationPayloads.resolvePermitType(data, null);
        logFieldMappingInfo("Declaration Type source -> declarationType='" + declarationType
                + "', permitType='" + firstNonBlank(permitType, "N/A") + "'");

        String normalizedDeclarationType = normalize(declarationType);
        switch (normalizedDeclarationType) {
            case "10" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("10 GST", "GST (including Duty Exemption)"),
                        compactValues("10 GST", "GST (including Duty Exemption)", "10", "GST"));
                return;
            }
            case "11" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("11 Duty", "Duty"),
                        compactValues("11 Duty", "Duty", "11"));
                return;
            }
            case "12" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("12 Duty & GST", "Duty & GST", "ID Permit"),
                        compactValues("12 Duty & GST", "Duty & GST", "ID Permit", "12"));
                return;
            }
            case "90" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("90 Blanket", "Blanket (including blanket GST payment and duty exemption)"),
                        compactValues("90 Blanket", "Blanket (including blanket GST payment and duty exemption)", "90"));
                return;
            }
            case "40" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("40 DRT", "DRT"),
                        compactValues("40 DRT", "DRT", "40"));
                return;
            }
            case "20" -> {
                if (permitTypeMatches(permitType, "ME")) {
                    selectDeclarationTypeOption(
                            field,
                            declarationType,
                            permitType,
                            compactValues("20 - ME Permit", "ME Permit"),
                            compactValues("20 - ME Permit", "ME Permit", "20 - ME", "ME", "20"));
                    return;
                }
                if (permitTypeMatches(permitType, "II")) {
                    selectDeclarationTypeOption(
                            field,
                            declarationType,
                            permitType,
                            compactValues("20 - II Permit", "II Permit"),
                            compactValues("20 - II Permit", "II Permit", "20 - II", "II", "20"));
                    return;
                }
                logFieldMappingWarning("Declaration Type 20 is ambiguous for the current payload. Falling back to generic lookup hints.");
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues(),
                        compactValues("20", "20 -", "ME", "II", "OUT"));
                return;
            }
            case "21" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("21 - IE Permit", "IE Permit"),
                        compactValues("21 - IE Permit", "IE Permit", "21 - IE", "IE", "21"));
                return;
            }
            case "22" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("22 - IN Permit", "IN Permit"),
                        compactValues("22 - IN Permit", "IN Permit", "22 - IN", "IN", "22"));
                return;
            }
            case "24" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("24 - IR Permit", "IR Permit"),
                        compactValues("24 - IR Permit", "IR Permit", "24 - IR", "IR", "24"));
                return;
            }
            case "25" -> {
                if (permitTypeMatches(permitType, "IM")) {
                    selectDeclarationTypeOption(
                            field,
                            declarationType,
                            permitType,
                            compactValues("25 - IM Permit", "IM Permit"),
                            compactValues("25 - IM Permit", "IM Permit", "25 - IM", "IM", "25"));
                    return;
                }
                if (permitTypeMatches(permitType, "IT")) {
                    selectDeclarationTypeOption(
                            field,
                            declarationType,
                            permitType,
                            compactValues("25 - IT Permit", "IT Permit"),
                            compactValues("25 - IT Permit", "IT Permit", "25 - IT", "IT", "25"));
                    return;
                }
                logFieldMappingWarning("Declaration Type 25 is ambiguous for the current payload. Falling back to generic lookup hints.");
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues(),
                        compactValues("25", "25 -", "IM", "IT"));
                return;
            }
            case "70" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("70 - TT Permit", "TT Permit"),
                        compactValues("70 - TT Permit", "TT Permit", "70 - TT", "TT", "70"));
                return;
            }
            case "72" -> {
                selectDeclarationTypeOption(
                        field,
                        declarationType,
                        permitType,
                        compactValues("72 - TW Permit", "TW Permit"),
                        compactValues("72 - TW Permit", "TW Permit", "72 - TW", "TW", "72"));
                return;
            }
        }

        focusAndType(field, declarationType, true, declarationType);
        logDeclarationTypeRenderedValue(field, declarationType, permitType);
    }

    private void selectDeclarationTypeOption(
            Locator field,
            String declarationType,
            String permitType,
            String[] exactHints,
            String[] selectionHints) {
        String[] expectedValues = compactValues(
                firstNonBlank(permitType),
                declarationType,
                exactHints.length > 0 ? exactHints[0] : null,
                exactHints.length > 1 ? exactHints[1] : null,
                selectionHints.length > 0 ? selectionHints[0] : null,
                selectionHints.length > 1 ? selectionHints[1] : null,
                selectionHints.length > 2 ? selectionHints[2] : null,
                selectionHints.length > 3 ? selectionHints[3] : null,
                selectionHints.length > 4 ? selectionHints[4] : null);

        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();

        boolean optionSelected = false;
        if (trySelectNativeDropdown(field, declarationType, expectedValues)) {
            optionSelected = waitForAnyRenderedFieldValue(field, 1500, expectedValues);
        }

        if (!optionSelected) {
            clickDropdownActivator(field);
            pauseUi(UI_ACTION_PAUSE_MS);

            optionSelected = exactHints.length > 0
                    && waitForVisibleSuggestionExact(UI_LOOKUP_WAIT_MS, exactHints)
                    && clickVisibleSuggestionExact(exactHints);
            if (!optionSelected) {
                try {
                    page.keyboard().press("ArrowDown");
                    pauseUi(UI_ACTION_PAUSE_MS);
                } catch (PlaywrightException ignored) {
                }
                optionSelected = exactHints.length > 0 && clickVisibleSuggestionExact(exactHints);
            }
            if (!optionSelected && selectionHints.length > 0) {
                optionSelected = waitForVisibleSuggestion(UI_LOOKUP_WAIT_MS, selectionHints)
                        && clickVisibleSuggestion(selectionHints);
            }
            if (!optionSelected) {
                openLookupAndChooseOption(
                        field,
                        selectionHints.length > 0 ? selectionHints : expectedValues);
            } else {
                page.keyboard().press("Tab");
                pauseUi(UI_NEXT_FIELD_PAUSE_MS);
            }
        }

        if (!waitForAnyRenderedFieldValue(field, 1800, expectedValues)) {
            throw new IllegalStateException(buildFieldVerificationFailure(
                    "Declaration Type value was not rendered",
                    field,
                    firstNonBlank(permitType, declarationType),
                    expectedValues));
        }
        logDeclarationTypeRenderedValue(field, declarationType, permitType);
    }

    private void logDeclarationTypeRenderedValue(Locator field, String declarationType, String permitType) {
        logFieldMappingInfo("Declaration Type rendered -> declarationType='"
                + declarationType
                + "', permitType='"
                + firstNonBlank(permitType, "N/A")
                + "', uiValue='"
                + firstNonBlank(readRenderedFieldValue(field), "N/A")
                + "'");
    }

    private boolean permitTypeMatches(String permitType, String permitCode) {
        String normalizedPermitType = normalize(permitType);
        String normalizedPermitCode = normalize(permitCode);
        return !normalizedPermitCode.isBlank()
                && (normalizedPermitType.equals(normalizedPermitCode)
                || normalizedPermitType.equals(normalizedPermitCode + "PERMIT")
                || normalizedPermitType.startsWith(normalizedPermitCode + "PERMIT"));
    }

    protected void fillSectionAndAdvance(String sectionName, Runnable filler) {
        openSection(sectionName);
        filler.run();
        completeSection(sectionName);
    }

    protected void completeSection(String sectionName) {
        if (sectionTabMatches("Party Info (P)", sectionName)) {
            saveDraftAndAdvanceToNextSection();
            return;
        }
        saveDraft();
        goToNextSection();
    }

    protected void validateDeclarationPayload(JsonNode data) {
    }

    protected String declarationFlowLabel() {
        return "IPT";
    }

    protected void fillDeclarationSpecificShipmentInfo(JsonNode data) {
    }

    protected void fillDeclarationSpecificTransportInfo(JsonNode data) {
    }

    protected void fillDeclarationSpecificPartyInfo(JsonNode data, JsonNode party) {
    }

    protected void fillDeclarationSpecificItemInfo(JsonNode data, JsonNode item) {
    }

    protected void fillDeclarationSpecificCpcInfo(JsonNode data) {
    }

    protected String invoiceSectionName() {
        return "Invoice Info (V)";
    }

    protected String cpcSectionName() {
        return null;
    }

    protected void fillLicense(String licenseValue) {
        if (licenseValue == null || licenseValue.isBlank()) {
            return;
        }
        Set<Integer> visibleFieldIndexesBeforeOpening = captureVisibleTextEntryIndexes();
        setCheckboxByLabel("License", true);
        ensureLicenseEditorVisible(visibleFieldIndexesBeforeOpening);

        Locator licenseField = waitForLicenseFieldOrNull(visibleFieldIndexesBeforeOpening, 1500);
        if (licenseField == null) {
            Locator licenseSection = waitForLicenseSectionOrNull(1000);
            if (licenseSection != null && clickButtonInScopeIfVisible(licenseSection, "ADD")) {
                pauseUi(UI_ACTION_PAUSE_MS);
                licenseField = waitForLicenseFieldOrNull(visibleFieldIndexesBeforeOpening, 2000);
            }
        }
        if (licenseField == null) {
            throw new IllegalStateException("License input did not open after checking License.");
        }
        if (isFieldInsideText(licenseField, "Prev Permit Number", "Previous Permit Number")) {
            throw new IllegalStateException("Resolved License input belongs to Previous Permit Number.");
        }
        focusAndType(licenseField, licenseValue, true, licenseValue);
        ensureTextFieldValue(licenseField, licenseValue);
        if (!waitForRenderedFieldValue(licenseField, licenseValue, 800)
                && !waitForLicenseValue(licenseValue, 1500)) {
            throw new IllegalStateException("License value was not rendered. Expected: " + licenseValue
                    + ", Actual: " + readRenderedFieldValue(licenseField));
        }
    }

    private void ensureLicenseEditorVisible(Set<Integer> visibleFieldIndexesBeforeOpening) {
        if (resolveLicenseFieldOrNull(visibleFieldIndexesBeforeOpening) != null) {
            return;
        }

        Locator licenseSection = waitForLicenseSectionOrNull(750);
        if (licenseSection != null && hasButtonInScopeVisible(licenseSection, "ADD")) {
            return;
        }

        clickLicensePanelBodyIfPresent();
        pauseUi(UI_ACTION_PAUSE_MS);
    }

    private Locator resolveLicenseFieldOrNull(Set<Integer> visibleFieldIndexesBeforeOpening) {
        Locator licenseSection = resolveLicenseSectionOrNull();
        Locator scopedField = resolveLicenseInputBoxOrNull(licenseSection);
        if (scopedField != null && !isFieldInsideText(scopedField, "Prev Permit Number", "Previous Permit Number")) {
            return scopedField;
        }

        Locator panelField = lastVisible(page.locator(
                "xpath=(//*[normalize-space()='License'])[last()]/ancestor::*[.//input or .//textarea][1]"
                        + "//*[self::input[not(@type) or @type='text' or @type='search'] or self::textarea]"));
        if (panelField != null && !isFieldInsideText(panelField, "Prev Permit Number", "Previous Permit Number")) {
            return panelField;
        }

        Locator newlyVisibleField = resolveNewlyVisibleTextEntry(visibleFieldIndexesBeforeOpening);
        if (newlyVisibleField != null && !isFieldInsideText(newlyVisibleField, "Prev Permit Number", "Previous Permit Number")) {
            return newlyVisibleField;
        }

        Locator withinExpandedLicenseArea = firstVisible(page.locator(
                "xpath=(//*[normalize-space()='License'])[last()]/ancestor::*[.//input or .//textarea or .//button][1]"
                        + "//*[self::input[not(@type) or @type='text' or @type='search'] or self::textarea]"));
        if (withinExpandedLicenseArea != null
                && !isFieldInsideText(withinExpandedLicenseArea, "Prev Permit Number", "Previous Permit Number")) {
            return withinExpandedLicenseArea;
        }

        return null;
    }

    private Locator waitForLicenseFieldOrNull(Set<Integer> visibleFieldIndexesBeforeOpening, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            Locator field = resolveLicenseFieldOrNull(visibleFieldIndexesBeforeOpening);
            if (field != null) {
                return field;
            }
            page.waitForTimeout(100);
        }
        return null;
    }

    private Locator waitForLicenseSectionOrNull(int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 500);
        while (System.currentTimeMillis() <= deadline) {
            Locator section = resolveLicenseSectionOrNull();
            if (section != null) {
                return section;
            }
            page.waitForTimeout(100);
        }
        return null;
    }

    private Locator resolveLicenseSectionOrNull() {
        waitForFormControls();

        Locator expandedSection = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='License'])[last()]"
                        + "/ancestor::*[(.//*[self::button or @role='button' or self::a]"
                        + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'ADD')]"
                        + " or .//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'LIST (1)')]"
                        + " or .//input[(not(@type) or @type='text' or @type='search') and not(@readonly) and not(@disabled)]"
                        + " or .//textarea[not(@readonly) and not(@disabled)])][1]");
        Locator visibleExpandedSection = firstVisible(expandedSection);
        if (visibleExpandedSection != null) {
            return visibleExpandedSection;
        }

        return resolveLicenseCardOrNull();
    }

    private Locator resolveLicenseCardOrNull() {
        Locator visibleCard = firstVisible(page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='License'])[last()]"
                        + "/ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' card ')][1]"));
        if (visibleCard != null) {
            return visibleCard;
        }

        Locator checkboxWrapper = firstVisible(page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='License'])[last()]"
                        + "/ancestor::*[self::clr-checkbox-wrapper or contains(@class, 'checkbox') or contains(@class, 'control')][1]"));
        if (checkboxWrapper != null) {
            return checkboxWrapper;
        }

        return null;
    }

    private Locator resolveLicenseInputBoxOrNull(Locator licenseSection) {
        Locator visibleScope = firstVisible(licenseSection);
        if (visibleScope == null) {
            return null;
        }

        Locator field = visibleScope.locator(
                "input:not([type='checkbox']):not([readonly]):not([disabled]), "
                        + "textarea:not([readonly]):not([disabled])");
        return firstVisible(field);
    }

    private void clickLicensePanelBodyIfPresent() {
        Locator licenseCard = resolveLicenseCardOrNull();
        if (licenseCard == null) {
            return;
        }

        try {
            licenseCard.scrollIntoViewIfNeeded();
            BoundingBox box = licenseCard.boundingBox();
            if (box == null) {
                licenseCard.click(new Locator.ClickOptions().setForce(true));
                return;
            }

            double safeX = box.x + Math.max(24, Math.min(box.width - 24, box.width * 0.78));
            double safeY = box.y + Math.max(18, Math.min(box.height - 18, box.height * 0.55));
            page.mouse().click(safeX, safeY);
        } catch (PlaywrightException ignored) {
        }
    }

    private boolean waitForLicenseValue(String expectedValue, int timeoutMs) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return true;
        }

        Locator licenseContainer = page.locator(
                "xpath=(//*[normalize-space()='License'])[last()]/ancestor::*[.//*[contains(normalize-space(.), 'License')] and (.//input or .//textarea or .//button)][1]");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Locator visibleContainer = firstVisible(licenseContainer);
            if (visibleContainer != null) {
                String containerText = normalize(visibleContainer.innerText());
                if (containerText.contains(normalize(expectedValue))) {
                    return true;
                }
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private Set<Integer> captureVisibleTextEntryIndexes() {
        Object indexesObject = page.evaluate("""
                selector => {
                    const isVisible = element => element && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    return Array.from(document.querySelectorAll(selector))
                        .map((element, index) => ({ element, index }))
                        .filter(entry => isVisible(entry.element))
                        .map(entry => entry.index);
                }
                """, textEntrySelector());
        Set<Integer> indexes = new HashSet<>();
        if (indexesObject instanceof List<?> rawIndexes) {
            for (Object rawIndex : rawIndexes) {
                if (rawIndex instanceof Number number) {
                    indexes.add(number.intValue());
                }
            }
        }
        return indexes;
    }

    private Locator resolveNewlyVisibleTextEntry(Set<Integer> previousIndexes) {
        Locator fields = page.locator(textEntrySelector());
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            if (previousIndexes.contains(index)) {
                continue;
            }
            Locator candidate = fields.nth(index);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        return null;
    }

    private String textEntrySelector() {
        return "input:not([type]):not([readonly]):not([disabled]), "
                + "input[type='text']:not([readonly]):not([disabled]), "
                + "input[type='search']:not([readonly]):not([disabled]), "
                + "textarea:not([readonly]):not([disabled])";
    }

    protected void fillField(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabel(label, 0);
        focusAndType(field, value, false);
    }

    protected void fillFieldIfPresent(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelOrNull(label, 0);
        if (field == null) {
            logFieldMappingWarning("UI field not found for label '" + label + "' while JSON value was '" + value + "'.");
            return;
        }
        focusAndType(field, value, false);
    }

    protected void fillDateField(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabel(label, 0);
        if ("date".equalsIgnoreCase(fieldInputType(field))) {
            setNativeDateFieldValue(field, value);
            return;
        }
        focusAndType(field, value, false);
        ensureDateFieldValue(field, value);
    }

    protected void fillSupplierManufacturerName(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveSupplierManufacturerNameField();
        fillVerifiedTextField(field, value, "Supplier / Manufacturer Name");
    }

    private Locator resolveSupplierManufacturerNameField() {
        Locator section = resolveSupplierManufacturerPartySection();

        String nameLabelQuery =
                ".//*[self::label or self::span or self::div or self::p]"
                        + "[contains(normalize-space(translate(., '*', '')), 'Name')]"
                        + "[not(contains(normalize-space(translate(., '*', '')), 'UEN'))]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), 'Name')])]";
        String textFieldQuery =
                "self::input[not(@type) or @type='text' or @type='search'] or self::textarea";

        Locator fromNameLabel = section.locator(
                "xpath=((" + nameLabelQuery + ")[1]/following::*[" + textFieldQuery + "][1])");
        Locator visibleFromNameLabel = firstVisible(fromNameLabel);
        if (visibleFromNameLabel != null) {
            return visibleFromNameLabel;
        }

        Locator fields = section.locator("input:not([type='checkbox']), textarea");
        List<PositionedField> positionedFields = new ArrayList<>();
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            BoundingBox box;
            try {
                box = candidate.boundingBox();
            } catch (PlaywrightException ignored) {
                continue;
            }
            if (box == null) {
                continue;
            }
            positionedFields.add(new PositionedField(index, box.x, box.y));
        }

        if (!positionedFields.isEmpty()) {
            PositionedField leftmostField = positionedFields.stream()
                    .min(Comparator.comparingDouble(PositionedField::x).thenComparingDouble(PositionedField::y))
                    .orElse(null);
            if (leftmostField != null) {
                return fields.nth(leftmostField.index());
            }
        }

        throw new IllegalStateException("Unable to resolve Supplier / Manufacturer Name field.");
    }

    private Locator resolveSupplierManufacturerPartySection() {
        waitForFormControls();
        Locator section = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Supplier / Manufacturer Party'])[last()]"
                        + "/ancestor::*[.//input and .//*[contains(normalize-space(translate(., '*', '')), 'Name')] and .//*[normalize-space()='UEN']][1]");
        Locator visibleSection = firstVisible(section);
        if (visibleSection != null) {
            return visibleSection;
        }

        Locator siblingPanel = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Supplier / Manufacturer Party'])[last()]"
                        + "/following::*[.//input and .//*[contains(normalize-space(translate(., '*', '')), 'Name')] and .//*[normalize-space()='UEN']][1]");
        Locator visibleSiblingPanel = firstVisible(siblingPanel);
        if (visibleSiblingPanel != null) {
            return visibleSiblingPanel;
        }

        throw new IllegalStateException("Supplier / Manufacturer Party section was not visible.");
    }

    protected void fillLookupField(String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabel(label, 0);
        focusAndType(field, value, true, suggestionHints);
    }

    protected void fillFieldInSection(String sectionTitle, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelInSection(sectionTitle, label, 0);
        focusAndType(field, value, false);
    }

    protected void fillFieldInSectionIfPresent(String sectionTitle, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
        if (field == null) {
            logFieldMappingWarning("UI field not found for section '" + sectionTitle + "' and label '" + label
                    + "' while JSON value was '" + value + "'.");
            return;
        }
        focusAndType(field, value, false);
    }

    protected void fillFieldInSectionByAnyLabelIfPresent(String sectionTitle, String value, String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }

        for (String label : labels) {
            Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
            if (field != null) {
                focusAndType(field, value, false);
                return;
            }
        }
        logFieldMappingWarning("UI field not found for section '" + sectionTitle + "' and labels '"
                + String.join(", ", labels) + "' while JSON value was '" + value + "'.");
    }

    protected void fillDateFieldInSection(String sectionTitle, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveDateFieldInSection(sectionTitle, label, "arrivalDate");
        if ("date".equalsIgnoreCase(fieldInputType(field))) {
            setNativeDateFieldValue(field, value);
            return;
        }
        focusAndType(field, value, false);
        ensureDateFieldValue(field, value);
    }

    protected void fillDateFieldInSectionIfPresent(String sectionTitle, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
        if (field == null) {
            logFieldMappingWarning("UI date field not found for section '" + sectionTitle + "' and label '" + label
                    + "' while JSON value was '" + value + "'.");
            return;
        }
        if ("date".equalsIgnoreCase(fieldInputType(field))) {
            setNativeDateFieldValue(field, value);
            return;
        }
        focusAndType(field, value, false);
        ensureDateFieldValue(field, value);
    }

    private void typeIntoFocusedEditor(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        page.keyboard().press("Control+A");
        page.keyboard().press("Backspace");
        page.keyboard().type(value);
        page.keyboard().press("Tab");
        pauseUi(UI_NEXT_FIELD_PAUSE_MS);
    }

    protected void fillLookupFieldInSection(String sectionTitle, String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelInSection(sectionTitle, label, 0);
        focusAndType(field, value, true, suggestionHints);
    }

    protected void fillNthLookupFieldInSection(String sectionTitle, int occurrence, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthFieldInSection(sectionTitle, occurrence);
        focusAndType(field, value, true, suggestionHints);
    }

    protected void fillNthLookupFieldInSectionIfPresent(String sectionTitle, int occurrence, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthFieldInSectionOrNull(sectionTitle, occurrence);
        if (field == null) {
            logFieldMappingWarning("UI lookup field not found for section '" + sectionTitle + "' at occurrence "
                    + occurrence + " while JSON value was '" + value + "'.");
            return;
        }
        focusAndType(field, value, true, suggestionHints);
    }

    private void fillLookupFieldInSectionByAnyLabelIfPresent(
            String sectionTitle,
            String value,
            String primaryHint,
            String secondaryHint,
            String tertiaryHint,
            String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }

        for (String label : labels) {
            Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
            if (field != null) {
                focusAndType(field, value, true, primaryHint, secondaryHint, tertiaryHint);
                return;
            }
        }
    }

    protected void fillFirstFieldInSection(String sectionTitle, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthFieldInSection(sectionTitle, 0);
        focusAndType(field, value, false);
    }

    protected void fillLookupFieldInSectionIfPresent(String sectionTitle, String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
        if (field == null) {
            logFieldMappingWarning("UI lookup field not found for section '" + sectionTitle + "' and label '" + label
                    + "' while JSON value was '" + value + "'.");
            return;
        }
        focusAndType(field, value, true, suggestionHints);
    }

    protected void fillLookupFieldIfPresent(String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelOrNull(label, 0);
        if (field == null) {
            logFieldMappingWarning("UI lookup field not found for label '" + label + "' while JSON value was '"
                    + value + "'.");
            return;
        }
        focusAndType(field, value, true, suggestionHints);
    }

    protected void fillSelectLikeLookupFieldIfPresent(String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveSelectLikeFieldByLabelOrNull(label, 0);
        if (field == null) {
            fillLookupFieldIfPresent(label, value, suggestionHints);
            return;
        }
        focusAndType(field, value, true, suggestionHints);
    }

    private void fillOneOfLabels(String value, String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String label : labels) {
            Locator field = resolveFieldByLabelOrNull(label, 0);
            if (field != null) {
                focusAndType(field, value, false);
                return;
            }
        }
        throw new IllegalStateException("Unable to resolve field for labels: " + String.join(", ", labels));
    }

    private void fillFirstMatchingFieldIfPresent(String value, String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String label : labels) {
            Locator field = resolveFieldByLabelOrNull(label, 0);
            if (field != null) {
                focusAndType(field, value, false);
                return;
            }
        }
        logFieldMappingWarning("UI field not found for labels '" + String.join(", ", labels)
                + "' while JSON value was '" + value + "'.");
    }

    private void fillOneOfLabelsIfPresent(String value, String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String label : labels) {
            Locator field = resolveFieldByLabelOrNull(label, 0);
            if (field != null) {
                focusAndType(field, value, false);
                return;
            }
        }
    }

    private void fillNthField(String label, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabel(label, occurrence);
        focusAndType(field, value, false);
    }

    protected void fillNthFieldInSectionIfPresent(String sectionTitle, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthFieldInSectionOrNull(sectionTitle, occurrence);
        if (field == null) {
            return;
        }
        focusAndType(field, value, false);
    }

    protected void fillNthFieldInScopeIfPresent(Locator scope, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthVisibleEditableFieldInScopeOrNull(scope, occurrence);
        if (field == null) {
            return;
        }
        focusAndType(field, value, false);
    }

    protected void fillNthLookupFieldInScopeIfPresent(Locator scope, int occurrence, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthVisibleEditableFieldInScopeOrNull(scope, occurrence);
        if (field == null) {
            return;
        }
        focusAndType(field, value, true, suggestionHints);
    }

    protected void fillNthLookupFieldInScopeByClickOnlyIfPresent(
            Locator scope,
            int occurrence,
            String value,
            String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthVisibleEditableFieldInScopeOrNull(scope, occurrence);
        if (field == null) {
            return;
        }
        focusAndTypeByClickOnly(field, value, suggestionHints);
    }

    protected void fillFieldInScopeRowIfPresent(Locator scope, String rowLabel, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldInScopeRowOrNull(scope, rowLabel, occurrence);
        if (field == null) {
            return;
        }
        focusAndType(field, value, false);
    }

    protected void fillLookupFieldInScopeRowIfPresent(
            Locator scope,
            String rowLabel,
            int occurrence,
            String value,
            String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldInScopeRowOrNull(scope, rowLabel, occurrence);
        if (field == null) {
            return;
        }
        focusAndType(field, value, true, suggestionHints);
    }

    protected void fillFieldAfterScopeLabelIfPresent(Locator scope, String rowLabel, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldAfterScopeLabelOrNull(scope, rowLabel, occurrence);
        if (field == null) {
            logFieldMappingWarning("Scoped UI field not found for row label '" + rowLabel
                    + "' while JSON value was '" + value + "'.");
            return;
        }
        focusAndType(field, value, false);
    }

    protected void fillValidatedFieldAfterScopeLabelIfPresent(Locator scope, String rowLabel, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldAfterScopeLabelOrNull(scope, rowLabel, occurrence);
        if (field == null) {
            logFieldMappingWarning("Scoped validated UI field not found for row label '" + rowLabel
                    + "' while JSON value was '" + value + "'.");
            return;
        }
        focusAndType(field, value, false);
        if (!waitForAnyRenderedFieldValue(field, 1000, value)) {
            ensureTextFieldValue(field, value);
        }
        if (!waitForAnyRenderedFieldValue(field, 1500, value)) {
            throw new IllegalStateException("Field value was not rendered for " + rowLabel + ". Expected: "
                    + value + ", Actual: " + readRenderedFieldValue(field));
        }
    }

    protected void fillLookupFieldAfterScopeLabelIfPresent(
            Locator scope,
            String rowLabel,
            int occurrence,
            String value,
            String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldAfterScopeLabelOrNull(scope, rowLabel, occurrence);
        if (field == null) {
            logFieldMappingWarning("Scoped lookup UI field not found for row label '" + rowLabel
                    + "' while JSON value was '" + value + "'.");
            return;
        }
        focusAndType(field, value, true, suggestionHints);
    }

    private void fillNthLookupField(String label, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabel(label, occurrence);
        focusAndType(field, value, true, value);
    }

    protected void fillLookupFieldInRow(String rowLabel, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFirstFieldInRow(rowLabel);
        focusAndType(field, value, true, suggestionHints.length == 0 ? new String[] { value } : suggestionHints);
    }

    private void fillFieldInRowByIndex(String rowLabel, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldInRowByContains(rowLabel, occurrence);
        focusAndType(field, value, false);
    }

    protected void fillFieldInRowByIndexIfPresent(String rowLabel, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldInRowByContainsOrNull(rowLabel, occurrence);
        if (field == null) {
            return;
        }
        focusAndType(field, value, false);
    }

    protected void fillLookupFieldInRowByIndex(String rowLabel, int occurrence, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldInRowByContains(rowLabel, occurrence);
        focusAndType(field, value, true, suggestionHints.length == 0 ? new String[] { value } : suggestionHints);
    }

    protected void fillLookupFieldInRowByIndexIfPresent(String rowLabel, int occurrence, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldInRowByContainsOrNull(rowLabel, occurrence);
        if (field == null) {
            return;
        }
        focusAndType(field, value, true, suggestionHints.length == 0 ? new String[] { value } : suggestionHints);
    }

    protected void fillFieldInChargeRow(String rowLabel, String columnLabel, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveInvoiceChargeField(rowLabel, columnLabel);
        focusAndType(field, value, false);
    }

    protected void fillLookupFieldInChargeRow(String rowLabel, String columnLabel, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveInvoiceChargeField(rowLabel, columnLabel);
        focusAndType(field, value, true, suggestionHints.length == 0 ? new String[] { value } : suggestionHints);
    }

    protected void focusAndType(Locator field, String value, boolean selectSuggestion) {
        focusAndType(field, value, selectSuggestion, value);
    }

    protected void focusAndType(Locator field, String value, boolean selectSuggestion, String... suggestionHints) {
        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        List<String> expectedValues = expectedFieldValues(value, suggestionHints);

        if (trySelectNativeDropdown(field, value, suggestionHints)) {
            ensureFieldEntryCommitted(field, value, selectSuggestion, expectedValues);
            page.keyboard().press("Tab");
            pauseUi(UI_NEXT_FIELD_PAUSE_MS);
            return;
        }

        field.click(new Locator.ClickOptions().setForce(true));
        page.keyboard().press("Control+A");
        page.keyboard().press("Backspace");
        page.keyboard().type(value);
        pauseUi(UI_ACTION_PAUSE_MS);
        if (selectSuggestion) {
            commitSuggestionSelection(field, value, suggestionHints, expectedValues);
        } else {
            ensureFieldEntryCommitted(field, value, false, expectedValues);
        }
        page.keyboard().press("Tab");
        pauseUi(UI_NEXT_FIELD_PAUSE_MS);
    }

    protected void focusAndTypeByClickOnly(Locator field, String value, String... suggestionHints) {
        if (field == null || value == null || value.isBlank()) {
            return;
        }

        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        List<String> expectedValues = expectedFieldValues(value, suggestionHints);

        if (trySelectNativeDropdown(field, value, suggestionHints)) {
            ensureFieldEntryCommitted(field, value, true, expectedValues);
            page.keyboard().press("Tab");
            pauseUi(UI_NEXT_FIELD_PAUSE_MS);
            return;
        }

        field.click(new Locator.ClickOptions().setForce(true));
        page.keyboard().press("Control+A");
        page.keyboard().press("Backspace");
        page.keyboard().type(value);
        pauseUi(UI_ACTION_PAUSE_MS);
        commitSuggestionSelectionByClickOnly(field, value, suggestionHints, expectedValues);
        page.keyboard().press("Tab");
        pauseUi(UI_NEXT_FIELD_PAUSE_MS);
    }

    protected void fillVerifiedTextField(Locator field, String value, String fieldLabel) {
        fillVerifiedTextField(field, value, fieldLabel, true);
    }

    protected void fillVerifiedTextField(Locator field, String value, String fieldLabel, boolean moveToNextField) {
        if (field == null || value == null || value.isBlank()) {
            return;
        }

        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        field.click(new Locator.ClickOptions().setForce(true));
        clearFieldForEntry(field);
        try {
            field.type(value, new Locator.TypeOptions().setDelay(60));
        } catch (PlaywrightException ignored) {
            page.keyboard().type(value);
        }
        pauseUi(UI_ACTION_PAUSE_MS);
        try {
            ensureFieldEntryCommitted(field, value, false, List.of(value));
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(buildFieldVerificationFailure(
                    fieldLabel + " value was not rendered",
                    field,
                    value), exception);
        }
        if (moveToNextField) {
            page.keyboard().press("Tab");
            pauseUi(UI_NEXT_FIELD_PAUSE_MS);
        }
    }

    private List<String> expectedFieldValues(String value, String... suggestionHints) {
        List<String> expectedValues = new ArrayList<>();
        appendCandidate(expectedValues, value);
        if (suggestionHints != null) {
            for (String suggestionHint : suggestionHints) {
                appendCandidate(expectedValues, suggestionHint);
            }
        }
        return expectedValues;
    }

    private void commitSuggestionSelection(
            Locator field,
            String value,
            String[] suggestionHints,
            List<String> expectedValues) {
        waitForVisibleSuggestion(UI_LOOKUP_WAIT_MS, suggestionHints);
        boolean suggestionClicked = clickVisibleSuggestion(suggestionHints);
        if (!suggestionClicked) {
            try {
                page.keyboard().press("ArrowDown");
                pauseUi(UI_ACTION_PAUSE_MS);
                suggestionClicked = clickVisibleSuggestion(suggestionHints);
                if (!suggestionClicked) {
                    suggestionClicked = clickFirstVisibleSuggestion();
                    if (!suggestionClicked) {
                        page.keyboard().press("Enter");
                        pauseUi(UI_ACTION_PAUSE_MS);
                    }
                }
            } catch (PlaywrightException ignored) {
            }
        }
        pauseUi(UI_ACTION_PAUSE_MS);
        ensureLookupValue(field, value);
        ensureFieldEntryCommitted(field, value, true, expectedValues);
    }

    private void commitSuggestionSelectionByClickOnly(
            Locator field,
            String value,
            String[] suggestionHints,
            List<String> expectedValues) {
        waitForVisibleSuggestion(UI_LOOKUP_WAIT_MS, suggestionHints);
        boolean suggestionClicked = clickVisibleSuggestion(suggestionHints);
        if (!suggestionClicked) {
            try {
                page.keyboard().press("ArrowDown");
                pauseUi(UI_ACTION_PAUSE_MS);
                suggestionClicked = clickVisibleSuggestion(suggestionHints);
            } catch (PlaywrightException ignored) {
            }
        }
        if (!suggestionClicked) {
            suggestionClicked = clickFirstVisibleSuggestion();
        }
        pauseUi(UI_ACTION_PAUSE_MS);

        boolean componentSynced = syncLookupComponentSelection(field, value, suggestionHints);
        if (componentSynced) {
            pauseUi(UI_ACTION_PAUSE_MS);
        }

        if (lookupClickOnlySelectionResolved(field, value, suggestionHints, expectedValues)) {
            validatedFieldEntryCount++;
            return;
        }

        if (!suggestionClicked) {
            throw new IllegalStateException(buildFieldVerificationFailure(
                    "Lookup suggestion was not selected by click",
                    field,
                    value,
                    expectedValues.toArray(String[]::new)));
        }

        throw new IllegalStateException(buildFieldVerificationFailure(
                "Lookup suggestion click did not resolve field",
                field,
                value,
                expectedValues.toArray(String[]::new)));
    }

    private void ensureFieldEntryCommitted(
            Locator field,
            String value,
            boolean lookupField,
            List<String> expectedValues) {
        String[] candidates = expectedValues.toArray(String[]::new);
        if (!waitForAnyRenderedFieldValue(field, 1200, candidates)) {
            if (isNativeSelectField(field)) {
                trySelectNativeDropdown(field, value, candidates);
            } else if (lookupField) {
                ensureLookupValue(field, value);
            } else {
                ensureTextFieldValue(field, value);
            }
        }
        if (!waitForAnyRenderedFieldValue(field, 1800, candidates)) {
            throw new IllegalStateException(buildFieldVerificationFailure(
                    "Field value was not rendered",
                    field,
                    value,
                    candidates));
        }
        validatedFieldEntryCount++;
    }

    private void fillVerifiedLookupField(Locator field, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }

        List<String> expectedValuesList = new ArrayList<>();
        appendCandidate(expectedValuesList, value);
        if (suggestionHints != null) {
            for (String suggestionHint : suggestionHints) {
                appendCandidate(expectedValuesList, suggestionHint);
            }
        }
        String[] expectedValues = expectedValuesList.toArray(String[]::new);

        focusAndType(field, value, true, suggestionHints);
        if (waitForAnyRenderedFieldValue(field, 1500, expectedValues)) {
            return;
        }

        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        field.click(new Locator.ClickOptions().setForce(true));
        page.keyboard().press("Control+A");
        page.keyboard().press("Backspace");
        page.keyboard().type(value);
        pauseUi(UI_ACTION_PAUSE_MS);

        if (!clickVisibleSuggestion(suggestionHints)) {
            try {
                page.keyboard().press("ArrowDown");
                pauseUi(UI_ACTION_PAUSE_MS);
                if (!clickVisibleSuggestion(suggestionHints)) {
                    page.keyboard().press("Enter");
                    pauseUi(UI_ACTION_PAUSE_MS);
                }
            } catch (PlaywrightException ignored) {
            }
        }

        ensureLookupValue(field, value);
        page.keyboard().press("Tab");
        pauseUi(UI_NEXT_FIELD_PAUSE_MS);

        if (!waitForAnyRenderedFieldValue(field, 1500, expectedValues)) {
            throw new IllegalStateException(buildFieldVerificationFailure(
                    "Lookup value was not rendered",
                    field,
                    value,
                    expectedValues));
        }
    }

    private void openLookupAndChooseOption(Locator field, String... optionHints) {
        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        clickDropdownActivator(field);
        page.waitForTimeout(250);

        boolean optionClicked = clickVisibleSuggestion(optionHints);
        if (!optionClicked) {
            try {
                page.keyboard().press("ArrowDown");
                page.waitForTimeout(100);
                optionClicked = clickVisibleSuggestion(optionHints);
            } catch (PlaywrightException ignored) {
            }
        }

        if (!optionClicked) {
            optionClicked = clickFirstVisibleSuggestion();
        }

        if (!optionClicked) {
            page.keyboard().press("Enter");
            page.waitForTimeout(150);
        }

        page.keyboard().press("Tab");
        page.waitForTimeout(150);
    }

    private void clickDropdownActivator(Locator field) {
        try {
            Boolean clicked = (Boolean) field.evaluate("""
                    element => {
                        const isVisible = candidate =>
                            candidate && (candidate.offsetWidth || candidate.offsetHeight || candidate.getClientRects().length);
                        const candidates = [
                            element,
                            element.querySelector?.('[role="combobox"]'),
                            element.querySelector?.('[aria-haspopup="listbox"]'),
                            element.querySelector?.('[aria-haspopup="menu"]'),
                            element.querySelector?.('.ng-select-container'),
                            element.querySelector?.('.mat-mdc-select-trigger'),
                            element.querySelector?.('.mat-select-trigger'),
                            element.querySelector?.('[class*="select-trigger"]'),
                            element.querySelector?.('[class*="dropdown-toggle"]'),
                            element.querySelector?.('button')
                        ].filter(isVisible);

                        if (candidates.length === 0) {
                            return false;
                        }

                        candidates[0].scrollIntoView({ block: 'center' });
                        candidates[0].click();
                        return true;
                    }
                    """);
            if (Boolean.TRUE.equals(clicked)) {
                return;
            }
        } catch (PlaywrightException ignored) {
        }

        field.click(new Locator.ClickOptions().setForce(true));
    }

    private boolean trySelectNativeDropdown(Locator field, String value, String... suggestionHints) {
        String tagName;
        try {
            tagName = String.valueOf(field.evaluate("element => element.tagName"));
        } catch (PlaywrightException ignored) {
            return false;
        }

        if (!"SELECT".equalsIgnoreCase(normalize(tagName))) {
            return false;
        }

        List<String> candidates = new ArrayList<>();
        appendCandidate(candidates, value);
        if (suggestionHints != null) {
            for (String suggestionHint : suggestionHints) {
                appendCandidate(candidates, suggestionHint);
            }
        }

        try {
            if (value != null && !value.isBlank()) {
                try {
                    List<String> byValue = field.selectOption(value);
                    if (byValue != null && !byValue.isEmpty()) {
                        return true;
                    }
                } catch (PlaywrightException ignored) {
                }
            }

            // Brief wait for dynamically-loaded SELECT options (e.g. invoice numbers populated after save)
            page.waitForTimeout(600);

            // Second attempt via Playwright after the wait
            if (value != null && !value.isBlank()) {
                try {
                    List<String> byValue = field.selectOption(value);
                    if (byValue != null && !byValue.isEmpty()) {
                        return true;
                    }
                } catch (PlaywrightException ignored) {
                }
                // Also try matching each hint as a label
                for (String hint : candidates) {
                    if (hint == null || hint.isBlank() || hint.equalsIgnoreCase(value)) {
                        continue;
                    }
                    try {
                        List<String> byHint = field.selectOption(hint);
                        if (byHint != null && !byHint.isEmpty()) {
                            return true;
                        }
                    } catch (PlaywrightException ignored) {
                    }
                }
            }

            Boolean matched = (Boolean) field.evaluate("""
                    (element, expectedValues) => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const expected = expectedValues.map(normalize).filter(Boolean);
                        const options = Array.from(element.options || []);
                        const option = options.find(candidate => {
                            const optionText = normalize(candidate.textContent);
                            const optionValue = normalize(candidate.value);
                            if (!optionValue) {
                                return false;
                            }
                            return expected.some(current =>
                                optionText === current
                                || optionValue === current
                                || optionText.includes(current)
                                || current.includes(optionText)
                                || optionValue.includes(current)
                                || current.includes(optionValue));
                        });
                        if (!option) {
                            return false;
                        }
                        element.value = option.value;
                        option.selected = true;
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        return true;
                    }
                    """, candidates);
            if (Boolean.TRUE.equals(matched)) {
                // Re-select via Playwright's native API after the JavaScript path to ensure Angular's
                // SelectControlValueAccessor receives a proper browser change event and updates the FormControl
                try {
                    Object currentVal = field.evaluate("el => el.value");
                    if (currentVal instanceof String s && !s.isBlank()) {
                        try {
                            field.selectOption(s);
                        } catch (PlaywrightException ignored) {
                        }
                    }
                } catch (PlaywrightException ignored) {
                }
                return true;
            }
            return false;
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private boolean isNativeSelectField(Locator field) {
        try {
            String tagName = String.valueOf(field.evaluate("element => element.tagName"));
            return "SELECT".equalsIgnoreCase(normalize(tagName));
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void ensureLookupValue(Locator field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            String currentValue = normalize(readRenderedFieldValue(field));
            String normalizedExpected = normalize(value);
            if (!currentValue.isBlank()
                    && (currentValue.equalsIgnoreCase(normalizedExpected)
                    || currentValue.contains(normalizedExpected)
                    || normalizedExpected.contains(currentValue))) {
                return;
            }
            field.evaluate("""
                    (element, newValue) => {
                        const target = element.closest('.ng-select, [role="combobox"], [class*="select"], [class*="combobox"]')
                            ?.querySelector?.("input:not([type='hidden']), textarea, [contenteditable='true']")
                            || element;
                        if ('value' in target) {
                            target.value = newValue;
                            target.setAttribute?.('value', newValue);
                        } else if (target.isContentEditable) {
                            target.textContent = newValue;
                        }
                        target.dispatchEvent(new Event('input', { bubbles: true }));
                        target.dispatchEvent(new Event('change', { bubbles: true }));
                        target.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    """, value);
        } catch (PlaywrightException ignored) {
        }
    }

    private String inputValueOrEmpty(Locator field) {
        try {
            return field.inputValue();
        } catch (PlaywrightException ignored) {
            return "";
        }
    }

    private String fieldInputType(Locator field) {
        try {
            return String.valueOf(field.evaluate("element => element.getAttribute('type') || ''"));
        } catch (PlaywrightException ignored) {
            return "";
        }
    }

    private boolean waitForRenderedFieldValue(Locator field, String expectedValue, int timeoutMs) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return true;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            String currentValue = normalize(readRenderedFieldValue(field));
            String normalizedExpected = normalize(expectedValue);
            if (!currentValue.isBlank()
                    && (currentValue.equalsIgnoreCase(normalizedExpected)
                    || currentValue.contains(normalizedExpected)
                    || normalizedExpected.contains(currentValue))) {
                return true;
            }

            page.waitForTimeout(100);
        }
        return false;
    }

    protected String readRenderedFieldValue(Locator field) {
        try {
            return String.valueOf(field.evaluate("""
                    element => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const ariaValueText = normalize(element.getAttribute('aria-valuetext'));
                        if (ariaValueText) {
                            return ariaValueText;
                        }

                        const selectedText = normalize(
                            element.querySelector?.('.ng-value-label, .mat-mdc-select-value-text, .selected-item, [class*=\"single-label\"]')
                                ?.textContent);
                        if (selectedText) {
                            return selectedText;
                        }

                        const parent = element.closest('td, tr, [role=\"row\"], .ng-select, [class*=\"select\"], [class*=\"combobox\"]')
                            || element.parentElement;
                        const parentSelectedText = normalize(
                            parent?.querySelector?.('.ng-value-label, .mat-mdc-select-value-text, .selected-item, [class*=\"single-label\"]')
                                ?.textContent);
                        if (parentSelectedText) {
                            return parentSelectedText;
                        }

                        if (element instanceof HTMLSelectElement && element.selectedOptions?.length > 0) {
                            const selectedOptionText = normalize(element.selectedOptions[0].textContent);
                            if (selectedOptionText) {
                                return selectedOptionText;
                            }
                        }

                        const directValue = normalize(element.value);
                        if (directValue) {
                            return directValue;
                        }

                        return normalize(element.innerText || element.textContent);
                    }
                    """));
        } catch (PlaywrightException ignored) {
            return inputValueOrEmpty(field);
        }
    }

    protected String describeControl(Locator field) {
        try {
            return String.valueOf(field.evaluate("""
                    element => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const ownLabel = normalize(
                            element.closest('label')?.innerText
                            || element.getAttribute('aria-label')
                            || element.getAttribute('placeholder'));
                        const nearbyLabel = normalize(
                            element.closest('td, tr, [role="row"], .clr-form-control, .form-group, .ng-select, .mat-mdc-form-field')
                                ?.querySelector?.('label, .clr-control-label, .clr-form-control-label, .form-label, [class*="label"]')
                                ?.textContent);
                        const section = normalize(
                            element.closest('app-card, section, .card, .accordion-body, .accordion-item, .tab-pane')
                                ?.querySelector?.('h1, h2, h3, h4, h5, .card-title, .accordion-header, .tab-title, [class*="title"]')
                                ?.textContent);
                        const role = normalize(element.getAttribute('role'));
                        const tag = normalize(element.tagName);
                        const type = normalize(element.getAttribute('type'));
                        const formControlName = normalize(element.getAttribute('formcontrolname'));
                        const name = normalize(element.getAttribute('name'));
                        const id = normalize(element.getAttribute('id'));
                        const placeholder = normalize(element.getAttribute('placeholder'));
                        const classes = normalize(element.className);
                        const selectedOption = element instanceof HTMLSelectElement && element.selectedOptions?.length > 0
                            ? normalize(element.selectedOptions[0].textContent || element.selectedOptions[0].value)
                            : '';
                        const parts = [
                            section ? `section=${section}` : '',
                            (ownLabel || nearbyLabel) ? `label=${ownLabel || nearbyLabel}` : '',
                            tag ? `tag=${tag}` : '',
                            role ? `role=${role}` : '',
                            type ? `type=${type}` : '',
                            formControlName ? `formControl=${formControlName}` : '',
                            name ? `name=${name}` : '',
                            id ? `id=${id}` : '',
                            placeholder ? `placeholder=${placeholder}` : '',
                            selectedOption ? `selected=${selectedOption}` : '',
                            classes ? `class=${classes}` : ''
                        ].filter(Boolean);
                        return parts.join(', ');
                    }
                    """));
        } catch (PlaywrightException ignored) {
            return "<control-details-unavailable>";
        }
    }

    protected String buildFieldVerificationFailure(String context, Locator field, String expectedValue, String... expectedAlternatives) {
        List<String> expected = new ArrayList<>();
        appendCandidate(expected, expectedValue);
        if (expectedAlternatives != null) {
            for (String expectedAlternative : expectedAlternatives) {
                appendCandidate(expected, expectedAlternative);
            }
        }
        return context
                + ". Expected: " + String.join(" | ", expected)
                + ", Actual: " + readRenderedFieldValue(field)
                + ", Control: " + describeControl(field);
    }

    private void clearFieldForEntry(Locator field) {
        try {
            field.fill("");
            return;
        } catch (PlaywrightException ignored) {
        }

        try {
            page.keyboard().press("Control+A");
            page.keyboard().press("Backspace");
        } catch (PlaywrightException ignored) {
        }
    }

    protected void ensureTextFieldValue(Locator field, String expectedValue) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return;
        }

        try {
            String currentValue = normalize(inputValueOrEmpty(field));
            String normalizedExpected = normalize(expectedValue);
            if (!currentValue.isBlank() && renderedFieldValueMatches(currentValue, normalizedExpected)) {
                return;
            }

            field.evaluate("""
                    (element, newValue) => {
                        element.value = newValue;
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    """, expectedValue);
        } catch (PlaywrightException ignored) {
        }
    }

    private void ensureDateFieldValue(Locator field, String expectedValue) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return;
        }

        String htmlDateValue = toHtmlDateValue(expectedValue);
        if (waitForAnyRenderedFieldValue(field, 500, expectedValue, htmlDateValue)) {
            return;
        }

        try {
            field.evaluate("""
                    (element, newValue) => {
                        element.value = newValue;
                        element.setAttribute('value', newValue);
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        element.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    """, expectedValue);
        } catch (PlaywrightException ignored) {
        }

        if (!waitForAnyRenderedFieldValue(field, 1000, expectedValue, htmlDateValue)) {
            throw new IllegalStateException(buildFieldVerificationFailure(
                    "Date value was not rendered",
                    field,
                    expectedValue,
                    htmlDateValue));
        }
    }

    private void setNativeDateFieldValue(Locator field, String expectedUiValue) {
        String htmlDateValue = toHtmlDateValue(expectedUiValue);
        if (htmlDateValue.isBlank()) {
            focusAndType(field, expectedUiValue, false);
            ensureDateFieldValue(field, expectedUiValue);
            return;
        }

        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        try {
            field.evaluate("""
                    (element, newValue) => {
                        element.value = newValue;
                        element.setAttribute('value', newValue);
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        element.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    """, htmlDateValue);
        } catch (PlaywrightException ignored) {
        }

        if (!waitForAnyRenderedFieldValue(field, 1000, expectedUiValue, htmlDateValue)) {
            throw new IllegalStateException(buildFieldVerificationFailure(
                    "Native date value was not rendered",
                    field,
                    expectedUiValue,
                    htmlDateValue));
        }
    }

    protected boolean waitForAnyRenderedFieldValue(Locator field, int timeoutMs, String... expectedValues) {
        List<String> candidates = new ArrayList<>();
        if (expectedValues != null) {
            for (String expectedValue : expectedValues) {
                appendCandidate(candidates, expectedValue);
            }
        }
        if (candidates.isEmpty()) {
            return true;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            String currentValue = normalize(readRenderedFieldValue(field));
            if (!currentValue.isBlank()) {
                for (String candidate : candidates) {
                    if (renderedFieldValueMatches(currentValue, candidate)) {
                        return true;
                    }
                }
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean renderedFieldValueMatches(String actualValue, String expectedValue) {
        String normalizedActual = normalize(actualValue);
        String normalizedExpected = normalize(expectedValue);
        if (normalizedActual.isBlank() || normalizedExpected.isBlank()) {
            return false;
        }
        if (normalizedActual.equalsIgnoreCase(normalizedExpected)
                || normalizedActual.contains(normalizedExpected)
                || normalizedExpected.contains(normalizedActual)) {
            return true;
        }

        String commaInsensitiveActual = normalizeCommaInsensitive(normalizedActual);
        String commaInsensitiveExpected = normalizeCommaInsensitive(normalizedExpected);
        return !commaInsensitiveActual.isBlank()
                && !commaInsensitiveExpected.isBlank()
                && (commaInsensitiveActual.equalsIgnoreCase(commaInsensitiveExpected)
                || commaInsensitiveActual.contains(commaInsensitiveExpected)
                || commaInsensitiveExpected.contains(commaInsensitiveActual));
    }

    private boolean lookupClickOnlySelectionResolved(
            Locator field,
            String value,
            String[] suggestionHints,
            List<String> expectedValues) {
        if (waitForCommittedPartySelection(field, 1200)) {
            return true;
        }

        String displayHint = preferredLookupDisplayHint(value, suggestionHints, expectedValues);
        String codeHint = preferredLookupCodeHint(displayHint, value, suggestionHints, expectedValues);
        if (waitForResolvedClickOnlyLookupValue(field, displayHint, codeHint, value, 1500)) {
            return true;
        }

        return !looksLikePartyLookupCode(value)
                && waitForAnyRenderedFieldValue(field, 1200, expectedValues.toArray(String[]::new));
    }

    private String preferredLookupDisplayHint(
            String value,
            String[] suggestionHints,
            List<String> expectedValues) {
        List<String> candidates = new ArrayList<>();
        appendCandidate(candidates, value);
        if (suggestionHints != null) {
            for (String suggestionHint : suggestionHints) {
                appendCandidate(candidates, suggestionHint);
            }
        }
        if (expectedValues != null) {
            for (String expectedValue : expectedValues) {
                appendCandidate(candidates, expectedValue);
            }
        }

        for (String candidate : candidates) {
            if (!looksLikePartyLookupCode(candidate)) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? value : candidates.get(0);
    }

    private String preferredLookupCodeHint(
            String displayHint,
            String value,
            String[] suggestionHints,
            List<String> expectedValues) {
        List<String> candidates = new ArrayList<>();
        appendCandidate(candidates, value);
        if (suggestionHints != null) {
            for (String suggestionHint : suggestionHints) {
                appendCandidate(candidates, suggestionHint);
            }
        }
        if (expectedValues != null) {
            for (String expectedValue : expectedValues) {
                appendCandidate(candidates, expectedValue);
            }
        }

        String normalizedDisplayHint = normalize(displayHint);
        for (String candidate : candidates) {
            if (!looksLikePartyLookupCode(candidate)) {
                continue;
            }
            if (!normalizedDisplayHint.isBlank() && normalizedDisplayHint.equalsIgnoreCase(normalize(candidate))) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private boolean waitForResolvedClickOnlyLookupValue(
            Locator field,
            String displayHint,
            String codeHint,
            String searchCandidate,
            int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            if (isResolvedPartySelectionValue(readRenderedFieldValue(field), displayHint, codeHint, searchCandidate)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean syncLookupComponentSelection(Locator field, String value, String... suggestionHints) {
        if (field == null || value == null || value.isBlank()) {
            return false;
        }

        List<String> sanitizedSuggestionHints = new ArrayList<>();
        if (suggestionHints != null) {
            for (String suggestionHint : suggestionHints) {
                if (suggestionHint != null) {
                    sanitizedSuggestionHints.add(suggestionHint);
                }
            }
        }

        try {
            return Boolean.TRUE.equals(field.evaluate("""
                    (element, args) => {
                        const normalize = input => (input || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const hints = [args.value, ...(args.suggestionHints || [])]
                            .map(normalize)
                            .filter(Boolean);
                        const unique = candidates => candidates.filter((candidate, index) =>
                            !!candidate && candidates.indexOf(candidate) === index);
                        const visible = candidate =>
                            !!candidate && !!(candidate.offsetWidth || candidate.offsetHeight || candidate.getClientRects().length);
                        const scoreOption = option => {
                            const code = normalize(option?.code);
                            const description = normalize(option?.description);
                            const combined = normalize(`${option?.code || ''} ${option?.description || ''}`);
                            return hints.reduce((best, hint) => {
                                let score = 0;
                                if (hint === code) {
                                    score = Math.max(score, 1000);
                                } else if (code && (code.includes(hint) || hint.includes(code))) {
                                    score = Math.max(score, 400);
                                }
                                if (hint === description) {
                                    score = Math.max(score, 900);
                                } else if (description && (description.includes(hint) || hint.includes(description))) {
                                    score = Math.max(score, 350);
                                }
                                if (hint === combined) {
                                    score = Math.max(score, 800);
                                } else if (combined && (combined.includes(hint) || hint.includes(combined))) {
                                    score = Math.max(score, 300);
                                }
                                return Math.max(best, score);
                            }, 0);
                        };

                        const candidateElements = unique([
                            element,
                            element.closest?.('[formcontrolname]'),
                            element.closest?.('app-lookup, app-dropdown, ng-select, mat-select, .ng-select, [role="combobox"], [class*="lookup"], [class*="select"]'),
                            element.parentElement,
                            element.parentElement?.parentElement,
                            element.parentElement?.parentElement?.parentElement
                        ]).filter(visible);

                        for (const candidate of candidateElements) {
                            const component = typeof window.ng !== 'undefined' && typeof window.ng.getComponent === 'function'
                                ? window.ng.getComponent(candidate)
                                : null;
                            if (!component) {
                                continue;
                            }

                            const allOptions = Array.isArray(component.allOptions)
                                ? component.allOptions
                                : Array.isArray(component.options)
                                    ? component.options
                                    : [];
                            if (allOptions.length === 0) {
                                continue;
                            }

                            const ranked = allOptions
                                .map(option => ({ option, score: scoreOption(option) }))
                                .filter(entry => entry.score > 0)
                                .sort((left, right) => right.score - left.score);
                            const selected = ranked[0]?.option;
                            if (!selected) {
                                continue;
                            }

                            const selectedValue = selected.code || args.value;
                            const selectedDisplay = selected.description || selected.code || args.value;
                            if ('value' in component) {
                                component.value = selectedValue;
                            }
                            if ('_value' in component) {
                                component._value = selectedValue;
                            }
                            if ('inputDisplayValue' in component) {
                                component.inputDisplayValue = selectedDisplay;
                            }
                            if ('displayValue' in component) {
                                component.displayValue = selectedDisplay;
                            }

                            const nativeInput = component.inputElement?.nativeElement
                                || candidate.querySelector?.("input:not([type='hidden']), textarea, [contenteditable='true']")
                                || element;
                            if (nativeInput && 'value' in nativeInput) {
                                nativeInput.value = selectedDisplay;
                                nativeInput.setAttribute?.('value', selectedDisplay);
                            } else if (nativeInput?.isContentEditable) {
                                nativeInput.textContent = selectedDisplay;
                            }
                            nativeInput?.dispatchEvent?.(new Event('input', { bubbles: true }));
                            nativeInput?.dispatchEvent?.(new Event('change', { bubbles: true }));
                            nativeInput?.dispatchEvent?.(new Event('blur', { bubbles: true }));

                            if (typeof component.selectOptionItem === 'function') {
                                component.selectOptionItem(selected);
                            }
                            if (typeof component.selectOptionLabel === 'function') {
                                component.selectOptionLabel(selected);
                            }
                            if (typeof component.onChange === 'function') {
                                component.onChange(selectedValue);
                            }
                            if (typeof component.onTouched === 'function') {
                                component.onTouched();
                            }
                            return true;
                        }
                        return false;
                    }
                    """, java.util.Map.of(
                    "value", value,
                    "suggestionHints", sanitizedSuggestionHints)));
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private boolean clickVisibleSuggestion(String... values) {
        return Boolean.TRUE.equals(page.evaluate("""
                expectedValues => {
                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                    const expectedList = expectedValues.map(normalize).filter(Boolean);
                    const matchScore = candidateText => expectedList.reduce((best, expected) => {
                        if (candidateText === expected) {
                            return Math.max(best, 10000 + expected.length);
                        }
                        if (candidateText.includes(expected) || expected.includes(candidateText)) {
                            return Math.max(best, Math.min(candidateText.length, expected.length));
                        }
                        return best;
                    }, 0);
                    const isVisible = element => element && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    const overlayRoots = [
                        ...document.querySelectorAll(
                            '[role="listbox"], [role="menu"], .ng-dropdown-panel, .cdk-overlay-pane, .cdk-overlay-container, .mat-mdc-autocomplete-panel, .mat-mdc-select-panel, .ui-autocomplete-panel, .dropdown-menu')
                    ].filter(element => isVisible(element));
                    const optionQuery = '[role="option"], .ng-option, .mat-mdc-option, li, [class*="option"], [class*="menu-item"], [class*="dropdown-item"]';
                    const optionLikeCandidates = (overlayRoots.length > 0
                            ? overlayRoots.flatMap(root => Array.from(root.querySelectorAll(optionQuery)))
                            : Array.from(document.querySelectorAll(optionQuery)))
                        .filter(element => isVisible(element))
                        .filter(element => !['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName))
                        .map(element => ({
                            element,
                            text: normalize(element.innerText || element.textContent)
                        }))
                        .filter(candidate => expectedList.some(expected =>
                            candidate.text === expected
                            || candidate.text.includes(expected)
                            || expected.includes(candidate.text)))
                        .sort((left, right) => {
                            return matchScore(right.text) - matchScore(left.text) || right.text.length - left.text.length;
                        });

                    if (optionLikeCandidates.length === 0) {
                        return false;
                    }

                    optionLikeCandidates[0].element.scrollIntoView({ block: 'center' });
                    optionLikeCandidates[0].element.click();
                    return true;
                }
                """, values));
    }

    private boolean clickVisibleSuggestionExact(String... values) {
        return Boolean.TRUE.equals(page.evaluate("""
                expectedValues => {
                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                    const expectedList = expectedValues.map(normalize).filter(Boolean);
                    const isVisible = element => element && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    const overlayRoots = [
                        ...document.querySelectorAll(
                            '[role="listbox"], [role="menu"], .ng-dropdown-panel, .cdk-overlay-pane, .cdk-overlay-container, .mat-mdc-autocomplete-panel, .mat-mdc-select-panel, .ui-autocomplete-panel, .dropdown-menu')
                    ].filter(element => isVisible(element));
                    const optionQuery = '[role="option"], .ng-option, .mat-mdc-option, li, [class*="option"], [class*="menu-item"], [class*="dropdown-item"]';
                    const optionLikeCandidates = (overlayRoots.length > 0
                            ? overlayRoots.flatMap(root => Array.from(root.querySelectorAll(optionQuery)))
                            : Array.from(document.querySelectorAll(optionQuery)))
                        .filter(element => isVisible(element))
                        .filter(element => !['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName))
                        .map(element => ({
                            element,
                            text: normalize(element.innerText || element.textContent)
                        }))
                        .filter(candidate => expectedList.some(expected => candidate.text === expected));

                    if (optionLikeCandidates.length === 0) {
                        return false;
                    }

                    optionLikeCandidates[0].element.scrollIntoView({ block: 'center' });
                    optionLikeCandidates[0].element.click();
                    return true;
                }
                """, values));
    }

    private boolean waitForVisibleSuggestion(int timeoutMs, String... values) {
        List<String> candidates = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                appendCandidate(candidates, value);
            }
        }
        if (candidates.isEmpty()) {
            return true;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Boolean visible = (Boolean) page.evaluate("""
                    expectedValues => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const expectedList = expectedValues.map(normalize).filter(Boolean);
                        const isVisible = element => element && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const overlayRoots = [
                            ...document.querySelectorAll(
                                '[role="listbox"], [role="menu"], .ng-dropdown-panel, .cdk-overlay-pane, .cdk-overlay-container, .mat-mdc-autocomplete-panel, .mat-mdc-select-panel, .ui-autocomplete-panel, .dropdown-menu')
                        ].filter(element => isVisible(element));
                        const optionQuery = '[role="option"], .ng-option, .mat-mdc-option, li, [class*="option"], [class*="menu-item"], [class*="dropdown-item"]';
                        const candidates = (overlayRoots.length > 0
                                ? overlayRoots.flatMap(root => Array.from(root.querySelectorAll(optionQuery)))
                                : Array.from(document.querySelectorAll(optionQuery)))
                            .filter(element => isVisible(element))
                            .filter(element => !['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName))
                            .map(element => normalize(element.innerText || element.textContent));
                        return candidates.some(candidate =>
                            expectedList.some(expected =>
                                candidate === expected
                                || candidate.includes(expected)
                                || expected.includes(candidate)));
                    }
                    """, candidates);
            if (Boolean.TRUE.equals(visible)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean clickFirstVisibleSuggestion() {
        return Boolean.TRUE.equals(page.evaluate("""
                () => {
                    const isVisible = element => element && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    const overlayRoots = [
                        ...document.querySelectorAll(
                            '[role="listbox"], [role="menu"], .ng-dropdown-panel, .cdk-overlay-pane, .cdk-overlay-container, .mat-mdc-autocomplete-panel, .mat-mdc-select-panel, .ui-autocomplete-panel, .dropdown-menu')
                    ].filter(element => isVisible(element));
                    const optionQuery = '[role="option"], .ng-option, .mat-mdc-option, li, [class*="option"], [class*="menu-item"], [class*="dropdown-item"]';
                    const candidates = (overlayRoots.length > 0
                            ? overlayRoots.flatMap(root => Array.from(root.querySelectorAll(optionQuery)))
                            : Array.from(document.querySelectorAll(optionQuery)))
                        .filter(element => isVisible(element))
                        .filter(element => !['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName));

                    if (candidates.length === 0) {
                        return false;
                    }

                    candidates[0].scrollIntoView({ block: 'center' });
                    candidates[0].click();
                    return true;
                }
                """));
    }

    private void focusNextPartyRow(String nextRowLabel) {
        if (nextRowLabel == null || nextRowLabel.isBlank()) {
            return;
        }
        Locator nextField = resolveFirstFieldInRow(nextRowLabel);
        nextField.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        nextField.scrollIntoViewIfNeeded();
        nextField.click(new Locator.ClickOptions().setForce(true));
        page.waitForTimeout(500);
    }

    private void clearAndTypePartyField(Locator field, String value) {
        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        field.click(new Locator.ClickOptions().setForce(true));
        clearFieldForEntry(field);
        try {
            field.fill(value);
        } catch (PlaywrightException ignored) {
            try {
                field.type(value, new Locator.TypeOptions().setDelay(40));
            } catch (PlaywrightException ignoredAgain) {
                page.keyboard().type(value);
            }
        }
        page.waitForTimeout(300);
        page.waitForTimeout(500);
    }

    private boolean attemptPartySuggestionSelection(String... selectionHints) {
        if (waitForVisibleSuggestion(2000, selectionHints) && clickVisibleSuggestion(selectionHints)) {
            page.waitForTimeout(1000);
            return true;
        }

        if (waitForAnyVisibleSuggestion(2500)
                && clickVisibleSuggestion(selectionHints)) {
            page.waitForTimeout(1000);
            return true;
        }

        try {
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(300);
            if (waitForAnyVisibleSuggestion(1500)
                    && clickVisibleSuggestion(selectionHints)) {
                page.waitForTimeout(1000);
                return true;
            }
        } catch (PlaywrightException ignored) {
        }

        page.waitForTimeout(1000);
        return false;
    }

    private Locator resolvePartyRowContainerOrNull(String rowLabel) {
        waitForFormControls();
        String escapedRowLabel = toXpathLiteral(rowLabel);

        Locator tableRow = page.locator(
                "xpath=(//*[normalize-space(.)=" + escapedRowLabel + "])[1]/ancestor::tr[1]");
        Locator fromTableRow = firstVisible(tableRow);
        if (fromTableRow != null) {
            return fromTableRow;
        }

        Locator roleRow = page.locator(
                "xpath=(//*[normalize-space(.)=" + escapedRowLabel + "])[1]/ancestor::*[@role='row'][1]");
        Locator fromRoleRow = firstVisible(roleRow);
        if (fromRoleRow != null) {
            return fromRoleRow;
        }

        Locator genericRow = page.locator(
                "xpath=(//*[normalize-space(.)=" + escapedRowLabel + "])[1]"
                        + "/ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > 1][1]");
        return firstVisible(genericRow);
    }

    private String readPartyRowText(String rowLabel) {
        try {
            Locator partyRow = resolvePartyRowContainerInSectionOrNull(rowLabel);
            if (partyRow != null) {
                String rowText = normalizedInnerText(partyRow);
                if (!rowText.isBlank()) {
                    return rowText;
                }
            }
            Locator field = resolvePartyNameField(rowLabel);
            return normalize(String.valueOf(field.evaluate("""
                    (element, expectedRowLabel) => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const expected = normalize(expectedRowLabel).toUpperCase();
                        let current = element;
                        while (current) {
                            const text = normalize(current.innerText || current.textContent);
                            const upperText = text.toUpperCase();
                            const fieldCount = current.querySelectorAll(
                                'input, textarea, select, [role="combobox"], [role="textbox"]').length;
                            if (upperText.includes(expected) && fieldCount >= 1) {
                                return text;
                            }
                            current = current.parentElement;
                        }
                        return '';
                    }
                    """, rowLabel)));
        } catch (PlaywrightException ignored) {
            return "";
        }
    }

    private String normalizedInnerText(Locator locator) {
        try {
            return normalize(locator.innerText());
        } catch (PlaywrightException ignored) {
            return "";
        }
    }

    private void capturePartyRowFailureArtifacts(String rowLabel) {
        if (page == null) {
            return;
        }

        String safeRowLabel = normalize(rowLabel).replaceAll("[^A-Za-z0-9]+", "-");
        try {
            page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(Paths.get("target", "party-row-failure-" + safeRowLabel + ".png")));
        } catch (PlaywrightException ignored) {
        }

        try {
            Path output = Paths.get("target", "party-row-failure-" + safeRowLabel + ".txt");
            Files.writeString(output, readPartyRowText(rowLabel));
        } catch (Exception ignored) {
        }
    }

    private String[] partySelectionHints(String partyName) {
        List<String> hints = new ArrayList<>();
        appendCandidate(hints, partyName);
        return hints.toArray(String[]::new);
    }

    private String[] partySearchCandidates(String partyName) {
        List<String> candidates = new ArrayList<>();
        appendCandidate(candidates, partyName);
        return candidates.stream().distinct().toArray(String[]::new);
    }

    private void logPartyMappingState(
            String rowLabel,
            String stage,
            String partyName,
            String partyId,
            String renderedValue) {
        logFieldMappingInfo("Party row '" + rowLabel + "' " + stage
                + " -> name='" + firstNonBlank(partyName, "N/A")
                + "', id='" + firstNonBlank(partyId, "N/A")
                + "', rowText='" + firstNonBlank(renderedValue, "N/A") + "'");
    }

    private void logPartyMappingAttempt(
            String rowLabel,
            String searchCandidate,
            String expectedName,
            String expectedId,
            boolean committedSelection,
            boolean resolvedSelection,
            boolean rowValuesMatch,
            boolean fieldValueMatches,
            String finalFieldValue,
            String finalRowValue) {
        logFieldMappingInfo("Party row '" + rowLabel + "' attempt"
                + " -> search='" + firstNonBlank(searchCandidate, "N/A")
                + "', expectedName='" + firstNonBlank(expectedName, "N/A")
                + "', expectedId='" + firstNonBlank(expectedId, "N/A")
                + "', committed=" + committedSelection
                + ", resolved=" + resolvedSelection
                + ", rowMatch=" + rowValuesMatch
                + ", fieldMatch=" + fieldValueMatches
                + ", fieldValue='" + firstNonBlank(normalize(finalFieldValue), "N/A")
                + "', rowText='" + firstNonBlank(normalize(finalRowValue), "N/A") + "'");
    }

    private Locator resolveFieldByLabel(String label, int occurrence) {
        Locator resolved = resolveFieldByLabelOrNull(label, occurrence);
        if (resolved != null) {
            return resolved;
        }
        throw new IllegalStateException("Unable to resolve field for label: " + label + " at occurrence " + occurrence);
    }

    private Locator resolveFieldByLabelInSection(String sectionTitle, String label, int occurrence) {
        Locator resolved = resolveFieldByLabelInSectionOrNull(sectionTitle, label, occurrence);
        if (resolved != null) {
            return resolved;
        }
        throw new IllegalStateException("Unable to resolve field for label: " + label + " in section " + sectionTitle);
    }

    private Locator resolveDateFieldInSection(String sectionTitle, String label, String formControlName) {
        Locator directDatePickerField = resolveDatePickerInputByFormControlNameOrNull(formControlName);
        if (directDatePickerField != null) {
            return directDatePickerField;
        }
        return resolveFieldByLabelInSection(sectionTitle, label, 0);
    }

    private Locator resolveNthFieldInSection(String sectionTitle, int occurrence) {
        Locator resolved = resolveNthFieldInSectionOrNull(sectionTitle, occurrence);
        if (resolved != null) {
            return resolved;
        }
        throw new IllegalStateException("Unable to resolve field at occurrence " + occurrence + " in section " + sectionTitle);
    }

    protected Locator resolveNthFieldInSectionOrNull(String sectionTitle, int occurrence) {
        waitForFormControls();
        Locator section = resolveSectionOrNull(sectionTitle);
        if (section == null) {
            return null;
        }
        Locator fields = section.locator("input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']");
        int visibleIndex = 0;
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            if (visibleIndex++ == occurrence) {
                return resolveConcreteEditableFieldOrNull(candidate);
            }
        }
        return null;
    }

    private Locator resolveFirstVisibleFieldInSectionByLayout(String sectionTitle) {
        waitForFormControls();
        Locator section = resolveSection(sectionTitle);
        Locator fields = section.locator(
                "input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']");
        List<PositionedField> positionedFields = new ArrayList<>();
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            BoundingBox box;
            try {
                box = candidate.boundingBox();
            } catch (PlaywrightException ignored) {
                continue;
            }
            if (box == null) {
                continue;
            }
            positionedFields.add(new PositionedField(index, box.x, box.y));
        }

        if (!positionedFields.isEmpty()) {
            PositionedField firstField = positionedFields.stream()
                    .min(Comparator.comparingDouble(PositionedField::y).thenComparingDouble(PositionedField::x))
                    .orElse(null);
            if (firstField != null) {
                return fields.nth(firstField.index());
            }
        }

        return resolveNthFieldInSection(sectionTitle, 0);
    }

    private Locator resolveDatePickerInputByFormControlNameOrNull(String formControlName) {
        if (formControlName == null || formControlName.isBlank()) {
            return null;
        }
        waitForFormControls();
        String escapedFormControlName = escapeForSelector(formControlName);
        Locator directInput = page.locator(
                "app-date-picker[formcontrolname='" + escapedFormControlName + "'] input:not([type='checkbox'])");
        Locator visibleDirectInput = firstVisible(directInput);
        if (visibleDirectInput != null) {
            return visibleDirectInput;
        }

        Locator nestedInput = page.locator(
                "[formcontrolname='" + escapedFormControlName + "'] input:not([type='checkbox']), "
                        + "input[formcontrolname='" + escapedFormControlName + "']:not([type='checkbox'])");
        return firstVisible(nestedInput);
    }

    protected Locator resolveFirstFieldInRow(String rowLabel) {
        return resolveEditableFieldInRowByExactText(rowLabel, 0);
    }

    private Locator resolvePartyNameField(String rowLabel) {
        Locator componentField = resolvePartyNameFieldFromComponentOrNull(rowLabel);
        if (componentField != null) {
            return componentField;
        }

        Locator directRowField = resolvePartyFieldInRowOrNull(rowLabel, 0);
        if (directRowField != null) {
            return directRowField;
        }

        waitForFormControls();
        Locator section = resolveSection("Party Info (P)");
        Locator fields = section.locator(
                "input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']");

        List<PositionedField> positionedFields = new ArrayList<>();
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            BoundingBox box;
            try {
                box = candidate.boundingBox();
            } catch (PlaywrightException ignored) {
                continue;
            }
            if (box == null) {
                continue;
            }
            positionedFields.add(new PositionedField(index, box.x, box.y));
        }

        if (!positionedFields.isEmpty()) {
            double minX = positionedFields.stream().mapToDouble(PositionedField::x).min().orElse(0);
            double maxX = positionedFields.stream().mapToDouble(PositionedField::x).max().orElse(0);
            double columnThreshold = minX + ((maxX - minX) / 2.0d);

            List<PositionedField> leftColumnFields = positionedFields.stream()
                    .filter(field -> field.x() <= columnThreshold)
                    .sorted(Comparator.comparingDouble(PositionedField::y).thenComparingDouble(PositionedField::x))
                    .toList();
            int targetIndex = partyRowOrder(rowLabel);
            if (targetIndex >= 0 && targetIndex < leftColumnFields.size()) {
                return fields.nth(leftColumnFields.get(targetIndex).index());
            }
        }

        return resolveFirstFieldInRow(rowLabel);
    }

    private Locator resolvePartyNameFieldFromComponentOrNull(String rowLabel) {
        String selector = partyNameComponentSelector(rowLabel);
        if (selector == null || selector.isBlank()) {
            return null;
        }

        waitForFormControls();
        Locator components = page.locator(selector);
        int count = components.count();
        for (int index = 0; index < count; index++) {
            Locator component = components.nth(index);
            Locator nestedConcreteField = firstVisible(component.locator(concreteEditableSelector()));
            if (nestedConcreteField != null) {
                return nestedConcreteField;
            }

            Locator nestedTextbox = firstVisible(component.locator("[role='combobox'], [role='textbox']"));
            if (nestedTextbox != null) {
                return nestedTextbox;
            }
        }
        return null;
    }

    private String partyNameComponentSelector(String rowLabel) {
        return switch (normalize(rowLabel).toUpperCase()) {
            case "IMPORTER" -> "app-importer-lookup[formcontrolname='name']";
            case "EXPORTER" -> "app-exporter-lookup[formcontrolname='name']";
            case "INWARD CARRIER" -> "app-inward-carrier-lookup[formcontrolname='name']";
            case "OUTWARD CARRIER" -> "app-outward-carrier-agent-lookup[formcontrolname='name']";
            case "FREIGHT FORWARDER" -> "app-freight-forwarder-lookup[formcontrolname='name']";
            case "DECLARING AGENT" -> "app-declaring-agent-lookup[formcontrolname='name']";
            case "CLAIMANT PARTY" -> "app-claimant-party-lookup[formcontrolname='name'], app-claimant-lookup[formcontrolname='name']";
            default -> null;
        };
    }

    private Locator resolvePartyIdFieldOrNull(String rowLabel) {
        Locator nameField = resolvePartyNameFieldFromComponentOrNull(rowLabel);
        Locator componentField = resolvePartyIdFieldFromComponentOrNull(rowLabel);
        if (componentField != null && !sameEditableField(componentField, nameField)) {
            return componentField;
        }

        Locator directRowField = resolvePartyFieldInRowOrNull(rowLabel, 1);
        if (directRowField != null && !sameEditableField(directRowField, nameField)) {
            return directRowField;
        }

        Locator exactRowField = resolveEditableFieldInRowByExactText(rowLabel, 1);
        if (exactRowField != null && !sameEditableField(exactRowField, nameField)) {
            return exactRowField;
        }

        return resolveNearestPartySiblingFieldOrNull(rowLabel, nameField, partyNameComponentSelector(rowLabel));
    }

    private Locator resolvePartyLookupComponentOrNull(String rowLabel) {
        String selector = partyNameComponentSelector(rowLabel);
        if (selector == null || selector.isBlank()) {
            return null;
        }

        waitForFormControls();
        Locator components = page.locator(selector);
        int count = components.count();
        for (int index = 0; index < count; index++) {
            Locator component = components.nth(index);
            Locator nestedField = firstVisible(component.locator(combinedEditableSelector()));
            if (nestedField != null) {
                return component;
            }
        }
        return count > 0 ? components.first() : null;
    }

    private Locator resolvePartyIdFieldFromComponentOrNull(String rowLabel) {
        Locator nameField = resolvePartyNameFieldFromComponentOrNull(rowLabel);
        String selector = partyNameComponentSelector(rowLabel);
        if (nameField == null || selector == null || selector.isBlank()) {
            return null;
        }

        Locator directRow = resolvePartyRowContainerInSectionOrNull(rowLabel);
        Locator rowSibling = resolveNearestEditableFieldToRightOrNull(directRow, nameField, selector);
        if (rowSibling != null) {
            return rowSibling;
        }

        Locator component = resolvePartyLookupComponentOrNull(rowLabel);
        if (component != null) {
            Locator ancestorScopes = component.locator(
                    "xpath=ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > 1]");
            int scopeCount = ancestorScopes.count();
            for (int scopeIndex = 0; scopeIndex < scopeCount; scopeIndex++) {
                Locator ancestorSibling = resolveNearestEditableFieldToRightOrNull(
                        ancestorScopes.nth(scopeIndex),
                        nameField,
                        selector);
                if (ancestorSibling != null) {
                    return ancestorSibling;
                }
            }
        }

        return resolveNearestPartySiblingFieldOrNull(rowLabel, nameField, selector);
    }

    private Locator resolveNearestPartySiblingFieldOrNull(String rowLabel, Locator nameField, String selector) {
        if (nameField == null) {
            return null;
        }

        Locator section = resolveSection("Party Info (P)");
        Locator sectionSibling = resolveNearestEditableFieldToRightOrNull(section, nameField, selector);
        if (sectionSibling != null) {
            return sectionSibling;
        }

        Locator rowScope = resolvePartyRowContainerOrNull(rowLabel);
        return resolveNearestEditableFieldToRightOrNull(rowScope, nameField, selector);
    }

    private Locator resolveNearestEditableFieldToRightOrNull(Locator scope, Locator anchorField, String excludedSelector) {
        if (scope == null || anchorField == null) {
            return null;
        }

        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return null;
        }

        BoundingBox anchorBox;
        try {
            anchorBox = anchorField.boundingBox();
        } catch (PlaywrightException ignored) {
            return null;
        }
        if (anchorBox == null) {
            return null;
        }

        Locator fields = visibleScope.locator(combinedEditableSelector());
        List<PositionedElement> positionedFields = collectDistinctVisibleEditableElements(fields);
        if (positionedFields.isEmpty()) {
            return null;
        }

        Locator bestField = null;
        double bestScore = Double.MAX_VALUE;
        double anchorMidY = anchorBox.y + (anchorBox.height / 2.0d);
        double anchorRightX = anchorBox.x + anchorBox.width;

        for (PositionedElement positionedField : positionedFields) {
            Locator candidate = resolveConcreteEditableFieldOrNull(fields.nth(positionedField.index()));
            if (candidate == null
                    || sameEditableField(candidate, anchorField)
                    || isFieldInsideSelector(candidate, excludedSelector)) {
                continue;
            }

            BoundingBox candidateBox;
            try {
                candidateBox = candidate.boundingBox();
            } catch (PlaywrightException ignored) {
                continue;
            }
            if (candidateBox == null) {
                continue;
            }

            double candidateMidY = candidateBox.y + (candidateBox.height / 2.0d);
            double deltaY = Math.abs(candidateMidY - anchorMidY);
            if (deltaY > 44.0d || candidateBox.x < anchorRightX - 8.0d) {
                continue;
            }

            double deltaX = Math.abs(candidateBox.x - anchorRightX);
            double score = deltaY + (deltaX / 1000.0d);
            if (score < bestScore) {
                bestScore = score;
                bestField = candidate;
            }
        }

        return bestField;
    }

    private boolean isFieldInsideSelector(Locator field, String selector) {
        if (selector == null || selector.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(field.evaluate(
                    "(element, cssSelector) => !!element.closest(cssSelector)",
                    selector));
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private boolean sameEditableField(Locator left, Locator right) {
        if (left == null || right == null) {
            return false;
        }

        String leftId = normalize(left.getAttribute("id"));
        String rightId = normalize(right.getAttribute("id"));
        if (!leftId.isBlank() && !rightId.isBlank()) {
            return leftId.equalsIgnoreCase(rightId);
        }

        try {
            BoundingBox leftBox = left.boundingBox();
            BoundingBox rightBox = right.boundingBox();
            if (leftBox == null || rightBox == null) {
                return false;
            }
            return Math.abs(leftBox.x - rightBox.x) <= 2.0d
                    && Math.abs(leftBox.y - rightBox.y) <= 2.0d
                    && Math.abs(leftBox.width - rightBox.width) <= 2.0d
                    && Math.abs(leftBox.height - rightBox.height) <= 2.0d;
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private Locator resolvePartyFieldInRowOrNull(String rowLabel, int occurrence) {
        waitForFormControls();
        Locator section = resolveSection("Party Info (P)");
        Locator directRow = resolvePartyRowContainerInSectionOrNull(rowLabel);
        Locator fromDirectRow = resolveVisibleEditableFieldInRowOrNull(directRow, occurrence);
        if (fromDirectRow != null) {
            return fromDirectRow;
        }

        Locator label = resolvePartyRowLabelOrNull(section, rowLabel);
        if (label == null) {
            return null;
        }

        BoundingBox labelBox;
        try {
            labelBox = label.boundingBox();
        } catch (PlaywrightException ignored) {
            return null;
        }
        if (labelBox == null) {
            return null;
        }

        Locator fields = section.locator(combinedEditableSelector());
        List<PositionedElement> positionedElements = collectDistinctVisibleEditableElements(fields);
        if (positionedElements.isEmpty()) {
            return null;
        }

        double labelMidY = labelBox.y + (labelBox.height / 2.0d);
        List<PositionedElement> rowFields = positionedElements.stream()
                .filter(field -> Math.abs((field.y() + (field.height() / 2.0d)) - labelMidY) <= 28.0d)
                .sorted(Comparator.comparingDouble(PositionedElement::x).thenComparingDouble(PositionedElement::y))
                .toList();
        if (occurrence < 0 || occurrence >= rowFields.size()) {
            return null;
        }
        return fields.nth(rowFields.get(occurrence).index());
    }

    private Locator resolvePartyRowContainerInSectionOrNull(String rowLabel) {
        waitForFormControls();
        Locator section = resolveSection("Party Info (P)");
        Locator label = resolvePartyRowLabelOrNull(section, rowLabel);
        if (label == null) {
            return null;
        }

        Locator nearestAncestorRow = label.locator(
                "xpath=ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > 1][1]");
        Locator visibleNearestAncestorRow = firstVisible(nearestAncestorRow);
        if (visibleNearestAncestorRow != null) {
            return visibleNearestAncestorRow;
        }

        return resolvePartyRowContainerOrNull(rowLabel);
    }

    private Locator resolvePartyRowLabelOrNull(Locator section, String rowLabel) {
        Locator visibleSection = firstVisible(section);
        if (visibleSection == null || rowLabel == null || rowLabel.isBlank()) {
            return null;
        }

        String escapedRowLabel = toXpathLiteral(rowLabel);
        Locator exactLabel = visibleSection.locator(
                "xpath=(.//*[normalize-space(translate(., '*', ''))=" + escapedRowLabel + "]"
                        + "[not(.//*[normalize-space(translate(., '*', ''))=" + escapedRowLabel + "])])[1]");
        Locator visibleExactLabel = firstVisible(exactLabel);
        if (visibleExactLabel != null) {
            return visibleExactLabel;
        }

        Locator containsLabel = visibleSection.locator(
                "xpath=(.//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")])])[1]");
        return firstVisible(containsLabel);
    }

    private int partyRowOrder(String rowLabel) {
        return switch (normalize(rowLabel).toUpperCase()) {
            case "IMPORTER" -> 0;
            case "EXPORTER" -> 1;
            case "INWARD CARRIER" -> 1;
            case "OUTWARD CARRIER" -> 3;
            case "FREIGHT FORWARDER" -> 2;
            case "CLAIMANT PARTY" -> 4;
            default -> -1;
        };
    }

    public static JsonNode normalizeDeclarationPayload(JsonNode data) {
        return DeclarationPayloads.unwrap(data);
    }

    private void resetFieldMappingDiagnostics() {
        fieldMappingDiagnostics.clear();
    }

    protected void logFieldMappingInfo(String message) {
        String entry = "INFO: " + message;
        fieldMappingDiagnostics.add(entry);
        System.out.println("[IptDeclarationPage] " + entry);
    }

    protected void logFieldMappingWarning(String message) {
        String entry = "WARN: " + message;
        fieldMappingDiagnostics.add(entry);
        System.out.println("[IptDeclarationPage] " + entry);
    }

    private ObjectNode parseDiagnosticsRoot(String diagnosticsJson) {
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(diagnosticsJson);
            if (parsed instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (Exception ignored) {
        }

        ObjectNode fallback = OBJECT_MAPPER.createObjectNode();
        fallback.put("rawDiagnostics", firstNonBlank(diagnosticsJson, "{}"));
        return fallback;
    }

    private record PositionedField(int index, double x, double y) {
    }

    private record PositionedElement(
            int index,
            double x,
            double y,
            double width,
            double height,
            int priority) {
    }

    private Locator resolveEditableFieldInRowByExactText(String rowLabel, int occurrence) {
        waitForFormControls();
        String escapedRowLabel = toXpathLiteral(rowLabel);
        Locator tableRow = page.locator(
                "xpath=(//*[normalize-space(.)=" + escapedRowLabel + "])[1]/ancestor::tr[1]");
        Locator fromTableRow = resolveVisibleEditableFieldInRowOrNull(tableRow, occurrence);
        if (fromTableRow != null) {
            return fromTableRow;
        }

        Locator roleRow = page.locator(
                "xpath=(//*[normalize-space(.)=" + escapedRowLabel + "])[1]/ancestor::*[@role='row'][1]");
        Locator fromRoleRow = resolveVisibleEditableFieldInRowOrNull(roleRow, occurrence);
        if (fromRoleRow != null) {
            return fromRoleRow;
        }

        Locator genericRow = page.locator(
                "xpath=(//*[normalize-space(.)=" + escapedRowLabel + "])[1]"
                        + "/ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > 1][1]");
        return resolveVisibleEditableFieldInRow(genericRow, rowLabel, occurrence);
    }

    private Locator resolveEditableFieldInRowByContains(String rowLabel, int occurrence) {
        Locator resolved = resolveEditableFieldInRowByContainsOrNull(rowLabel, occurrence);
        if (resolved != null) {
            return resolved;
        }
        throw new IllegalStateException("No editable field found in row: " + rowLabel + " at occurrence " + occurrence);
    }

    private Locator resolveEditableFieldInRowByContainsOrNull(String rowLabel, int occurrence) {
        waitForFormControls();
        String escapedRowLabel = toXpathLiteral(rowLabel);
        Locator fromFollowingControls = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")])])[1]"
                        + "/following::*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']["
                        + (occurrence + 1) + "]");
        Locator visibleFromFollowingControls = firstVisible(fromFollowingControls);
        if (visibleFromFollowingControls != null) {
            return visibleFromFollowingControls;
        }

        Locator tableRow = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")])])[1]/ancestor::tr[1]");
        Locator fromTableRow = resolveVisibleEditableFieldInRowOrNull(tableRow, occurrence);
        if (fromTableRow != null) {
            return fromTableRow;
        }

        Locator roleRow = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")])])[1]/ancestor::*[@role='row'][1]");
        Locator fromRoleRow = resolveVisibleEditableFieldInRowOrNull(roleRow, occurrence);
        if (fromRoleRow != null) {
            return fromRoleRow;
        }

        Locator genericRow = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")])])[1]"
                        + "/ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > 1][1]");
        return resolveVisibleEditableFieldInRowOrNull(genericRow, occurrence);
    }

    private Locator resolveInvoiceChargeField(String rowLabel, String columnLabel) {
        waitForFormControls();
        String escapedRowLabel = toXpathLiteral(rowLabel);
        int columnIndex = invoiceChargeColumnIndex(columnLabel);
        Locator cell = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")])])[1]"
                        + "/ancestor::tr[1]/td[" + columnIndex + "]");
        Locator field = resolveVisibleEditableFieldInRowOrNull(cell, 0);
        if (field != null) {
            return field;
        }
        throw new IllegalStateException("No editable field found in invoice charge row: " + rowLabel
                + " for column: " + columnLabel);
    }

    private int invoiceChargeColumnIndex(String columnLabel) {
        return switch (normalize(columnLabel).toUpperCase()) {
            case "CHARGE %" -> 2;
            case "CURRENCY" -> 3;
            case "EXCHANGE RATE" -> 4;
            case "AMOUNT" -> 5;
            case "AMOUNT (S$)" -> 6;
            default -> throw new IllegalArgumentException("Unsupported invoice charge column: " + columnLabel);
        };
    }

    private Locator resolveVisibleEditableFieldInRow(Locator row, String rowLabel, int occurrence) {
        Locator resolved = resolveVisibleEditableFieldInRowOrNull(row, occurrence);
        if (resolved != null) {
            return resolved;
        }
        Locator visibleRow = firstVisible(row);
        if (visibleRow == null) {
            throw new IllegalStateException("Row was not visible: " + rowLabel);
        }
        throw new IllegalStateException("No editable field found in row: " + rowLabel + " at occurrence " + occurrence);
    }

    private Locator resolveCascPrimaryEditableField(Locator cascRow, int occurrence) {
        Locator resolved = resolveCascPrimaryEditableFieldOrNull(cascRow, occurrence);
        if (resolved != null) {
            return resolved;
        }
        return resolveVisibleEditableFieldInRow(cascRow, "CASC Product", occurrence);
    }

    private Locator resolveCascPrimaryEditableFieldOrNull(Locator cascRow, int occurrence) {
        Locator visibleRow = firstVisible(cascRow);
        if (visibleRow == null) {
            return null;
        }

        Locator fields = visibleRow.locator(combinedEditableSelector());
        List<PositionedElement> positionedFields = collectDistinctVisibleEditableElements(fields);
        if (positionedFields.isEmpty()) {
            return null;
        }

        double minY = positionedFields.stream()
                .mapToDouble(PositionedElement::y)
                .min()
                .orElse(Double.MAX_VALUE);
        double primaryRowThreshold = minY + 24.0d;

        List<PositionedElement> primaryRowFields = positionedFields.stream()
                .filter(field -> field.y() <= primaryRowThreshold)
                .sorted(Comparator.comparingDouble(PositionedElement::x).thenComparingDouble(PositionedElement::y))
                .toList();
        if (occurrence < 0 || occurrence >= primaryRowFields.size()) {
            return null;
        }

        return fields.nth(primaryRowFields.get(occurrence).index());
    }

    protected Locator resolveVisibleEditableFieldInRowOrNull(Locator row, int occurrence) {
        if (row == null) {
            return null;
        }
        Locator visibleRow = firstVisible(row);
        if (visibleRow == null) {
            return null;
        }

        return resolveNthVisibleEditableFieldInScopeOrNull(visibleRow, occurrence);
    }

    private Locator resolveCascRow(Locator cascSection, int occurrence) {
        Locator rows = cascSection.locator(
                "xpath=.//*[self::button or @role='button' or self::a]"
                        + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'ADDITIONAL CASC')"
                        + " or contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'CLOSE')]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]");
        int visibleIndex = 0;
        int count = rows.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = rows.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            if (visibleIndex++ == occurrence) {
                return candidate;
            }
        }
        throw new IllegalStateException("CASC product row was not visible.");
    }

    private Locator waitForCascRow(Locator cascSection, int occurrence, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            try {
                Locator cascRow = resolveCascRow(cascSection, occurrence);
                if (cascRow != null) {
                    return cascRow;
                }
            } catch (Exception ignored) {
            }
            page.waitForTimeout(100);
        }
        throw new IllegalStateException("CASC product row was not visible for occurrence " + occurrence + ".");
    }

    private Locator resolveCascProductBlock(Locator cascRow) {
        Locator visibleRow = firstVisible(cascRow);
        if (visibleRow == null) {
            return cascRow;
        }

        Locator block = visibleRow.locator(
                "xpath=(ancestor::*[.//*[contains(normalize-space(translate(., '*', '')), 'Code 1')]"
                        + " and .//*[contains(normalize-space(translate(., '*', '')), 'Code 2')]"
                        + " and .//*[contains(normalize-space(translate(., '*', '')), 'Code 3')]][1])");
        Locator visibleBlock = firstVisible(block);
        if (visibleBlock != null) {
            return visibleBlock;
        }
        return cascRow;
    }

    private Locator resolveCascEndUseDescriptionFieldOrNull(Locator cascRow, Locator cascBlock) {
        String label = toXpathLiteral("End User Description (Strategic Goods)");
        for (Locator scope : new Locator[] { cascRow, cascBlock }) {
            Locator visibleScope = firstVisible(scope);
            if (visibleScope == null) {
                continue;
            }

            Locator fieldAfterLabel = visibleScope.locator(
                    "xpath=((.//*[normalize-space(translate(., '*', ''))=" + label + "]"
                            + "[not(.//*[normalize-space(translate(., '*', ''))=" + label + "])])[1]"
                            + "/following::*[self::textarea or self::input[not(@type='checkbox')] or @role='textbox'][1])[1]");
            Locator visibleFieldAfterLabel = firstVisible(fieldAfterLabel);
            if (visibleFieldAfterLabel != null) {
                return visibleFieldAfterLabel;
            }

            Locator textarea = visibleScope.locator("textarea:not([readonly]):not([disabled]), [role='textbox']");
            Locator visibleTextarea = firstVisible(textarea);
            if (visibleTextarea != null) {
                return visibleTextarea;
            }

            Locator fieldByScopeLabel = resolveEditableFieldAfterScopeLabelOrNull(
                    visibleScope,
                    "End User Description (Strategic Goods)",
                    0);
            if (fieldByScopeLabel != null) {
                return fieldByScopeLabel;
            }
        }

        Locator surroundingContainer = firstVisible(cascRow);
        if (surroundingContainer == null) {
            surroundingContainer = firstVisible(cascBlock);
        }
        if (surroundingContainer == null) {
            return null;
        }

        Locator nearestTextareaContainer = surroundingContainer.locator(
                "xpath=(ancestor::*[.//textarea or .//*[@role='textbox']][1])");
        Locator visibleNearestTextareaContainer = firstVisible(nearestTextareaContainer);
        if (visibleNearestTextareaContainer != null) {
            Locator visibleTextarea = firstVisible(
                    visibleNearestTextareaContainer.locator("textarea:not([readonly]):not([disabled]), [role='textbox']"));
            if (visibleTextarea != null) {
                return visibleTextarea;
            }
        }

        return null;
    }

    private void waitForAdditionalCascSection(Locator cascRow, Locator cascBlock, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            boolean closeVisible = hasButtonInScopeVisible(cascRow, "CLOSE");
            if (closeVisible || hasVisibleTextInScope(cascBlock, "Code 1")) {
                return;
            }
            page.waitForTimeout(100);
        }
        captureAdditionalCascFailureArtifacts("section-not-visible");
        throw new IllegalStateException("Additional CASC section was not visible.");
    }

    private Locator waitForAdditionalCascEntryRow(Locator cascBlock, int occurrence, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            Locator entryRow = resolveAdditionalCascEntryRowOrNull(cascBlock, occurrence);
            if (entryRow != null) {
                return entryRow;
            }
            page.waitForTimeout(100);
        }
        captureAdditionalCascFailureArtifacts("entry-row-not-visible");
        throw new IllegalStateException("Additional CASC entry row was not visible after clicking Add.");
    }

    private Locator resolveFirstVisibleEditableFieldInScopeOrNull(Locator scope) {
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return null;
        }

        Locator concreteField = firstVisible(visibleScope.locator(concreteEditableSelector()));
        if (concreteField != null) {
            return concreteField;
        }

        Locator wrapperField = visibleScope.locator("[role='combobox'], [role='textbox']");
        return firstVisible(wrapperField);
    }

    private Locator resolveNthVisibleEditableFieldInScopeOrNull(Locator scope, int occurrence) {
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return null;
        }

        Locator fields = visibleScope.locator(combinedEditableSelector());
        List<PositionedElement> positionedElements = collectDistinctVisibleEditableElements(fields);
        if (occurrence < 0 || occurrence >= positionedElements.size()) {
            return null;
        }
        List<PositionedElement> orderedElements = positionedElements.stream()
                .sorted(Comparator.comparingDouble(PositionedElement::y).thenComparingDouble(PositionedElement::x))
                .toList();
        return resolveConcreteEditableFieldOrNull(fields.nth(orderedElements.get(occurrence).index()));
    }

    private String concreteEditableSelector() {
        return "input:not([type='checkbox']):not([readonly]):not([disabled]), "
                + "textarea:not([readonly]):not([disabled]), "
                + "select:not([disabled]), "
                + "[contenteditable='true']";
    }

    private String combinedEditableSelector() {
        return concreteEditableSelector() + ", [role='combobox'], [role='textbox']";
    }

    private Locator resolveConcreteEditableFieldOrNull(Locator candidate) {
        Locator visibleCandidate = firstVisible(candidate);
        if (visibleCandidate == null) {
            return null;
        }
        if (isConcreteEditableField(visibleCandidate)) {
            return visibleCandidate;
        }

        Locator nestedConcreteField = firstVisible(visibleCandidate.locator(concreteEditableSelector()));
        if (nestedConcreteField != null) {
            return nestedConcreteField;
        }

        Locator nestedTextbox = firstVisible(visibleCandidate.locator("[role='combobox'], [role='textbox']"));
        if (nestedTextbox != null) {
            return nestedTextbox;
        }

        return visibleCandidate;
    }

    private int editableCandidatePriority(Locator candidate) {
        return isConcreteEditableField(candidate) ? 2 : 1;
    }

    private boolean isConcreteEditableField(Locator field) {
        try {
            Object editable = field.evaluate("""
                    element => {
                        const tagName = (element.tagName || '').toUpperCase();
                        if (tagName === 'INPUT') {
                            return element.type !== 'checkbox' && !element.readOnly && !element.disabled;
                        }
                        if (tagName === 'TEXTAREA') {
                            return !element.readOnly && !element.disabled;
                        }
                        if (tagName === 'SELECT') {
                            return !element.disabled;
                        }
                        return element.getAttribute('contenteditable') === 'true';
                    }
                    """);
            return Boolean.TRUE.equals(editable);
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private Locator resolveEditableFieldInScopeRowOrNull(Locator scope, String rowLabel, int occurrence) {
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return null;
        }

        Locator exactRow = resolveEditableFieldInScopeRowByLabelOrNull(visibleScope, rowLabel, occurrence, false);
        if (exactRow != null) {
            return exactRow;
        }

        return resolveEditableFieldInScopeRowByContainsOrNull(visibleScope, rowLabel, occurrence);
    }

    private Locator resolveEditableFieldAfterScopeLabelOrNull(Locator scope, String rowLabel, int occurrence) {
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return null;
        }

        Locator visibleField = resolveEditableFieldAfterScopeLabelByMatchOrNull(visibleScope, rowLabel, occurrence, false);
        if (visibleField != null) {
            return visibleField;
        }

        visibleField = resolveEditableFieldAfterScopeLabelByMatchOrNull(visibleScope, rowLabel, occurrence, true);
        if (visibleField != null) {
            return visibleField;
        }

        return resolveEditableFieldInScopeRowOrNull(scope, rowLabel, occurrence);
    }

    private Locator resolveEditableFieldInScopeRowByLabelOrNull(
            Locator visibleScope,
            String rowLabel,
            int occurrence,
            boolean containsMatch) {
        Locator label = resolveScopeLabelOrNull(visibleScope, rowLabel, containsMatch);
        if (label == null) {
            return null;
        }

        Locator row = label.locator(
                "xpath=(ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > "
                        + occurrence + "][1])");
        return resolveVisibleEditableFieldInRowOrNull(row, occurrence);
    }

    private Locator resolveEditableFieldInScopeRowByContainsOrNull(Locator visibleScope, String rowLabel, int occurrence) {
        return resolveEditableFieldInScopeRowByLabelOrNull(visibleScope, rowLabel, occurrence, true);
    }

    private Locator resolveEditableFieldAfterScopeLabelByMatchOrNull(
            Locator visibleScope,
            String rowLabel,
            int occurrence,
            boolean containsMatch) {
        Locator label = resolveScopeLabelOrNull(visibleScope, rowLabel, containsMatch);
        if (label == null) {
            return null;
        }

        Locator field = label.locator(
                "xpath=(following::*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']["
                        + (occurrence + 1) + "])[1]");
        return resolveConcreteEditableFieldOrNull(field);
    }

    private Locator resolveScopeLabelOrNull(Locator visibleScope, String rowLabel, boolean containsMatch) {
        String escapedRowLabel = toXpathLiteral(rowLabel);
        String matchExpression = containsMatch
                ? "contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")"
                : "normalize-space(translate(., '*', ''))=" + escapedRowLabel;
        Locator label = visibleScope.locator(
                "xpath=(.//*[" + matchExpression + "]"
                        + "[not(.//*[" + matchExpression + "])])[1]");
        return firstVisible(label);
    }

    private Locator resolveAdditionalRecipientsInputBoxOrNull(
            Locator additionalRecipientsSection,
            int rowNumber,
            int inputOccurrence) {
        Locator visibleScope = firstVisible(additionalRecipientsSection);
        if (visibleScope == null) {
            return null;
        }

        String rowNumberText = String.valueOf(rowNumber);
        Locator numberedRow = visibleScope.locator(
                "xpath=(.//*[normalize-space(.)=" + toXpathLiteral(rowNumberText) + "]"
                        + "[not(ancestor::*[self::thead or @role='columnheader'])]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1])[last()]");
        Locator fieldInNumberedRow = resolveNthVisibleEditableFieldInScopeOrNull(numberedRow, inputOccurrence);
        if (fieldInNumberedRow != null) {
            return fieldInNumberedRow;
        }

        Locator listPanel = visibleScope.locator(
                "xpath=(.//*[contains(normalize-space(translate(., '*', '')), 'LIST (1)')]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1])[last()]");
        Locator fieldInListPanel = resolveNthVisibleEditableFieldInScopeOrNull(listPanel, inputOccurrence);
        if (fieldInListPanel != null) {
            return fieldInListPanel;
        }

        return null;
    }

    private Locator resolveAdditionalRecipientsSection() {
        waitForFormControls();
        Locator section = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Additional Recipients'])[last()]"
                        + "/ancestor::*[.//*[contains(normalize-space(translate(., '*', '')), 'List (1)')]"
                        + " and .//*[self::button or @role='button' or self::a][contains(normalize-space(translate(., '*', '')), 'ADD')]"
                        + " and (.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
        Locator visibleSection = firstVisible(section);
        if (visibleSection != null) {
            return visibleSection;
        }
        throw new IllegalStateException("Additional Recipients section was not visible.");
    }

    private Locator resolveAdditionalRecipientsActivatorOrNull(Locator additionalRecipientsSection, int rowNumber) {
        Locator visibleScope = firstVisible(additionalRecipientsSection);
        if (visibleScope == null) {
            return null;
        }

        String rowNumberText = String.valueOf(rowNumber);
        Locator row = visibleScope.locator(
                "xpath=(.//*[normalize-space(.)=" + toXpathLiteral(rowNumberText) + "]"
                        + "[not(ancestor::*[self::thead or @role='columnheader'])]"
                        + "/ancestor::*[.//*[self::button or @role='button' or self::a]"
                        + " and (.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']"
                        + " or .//*[contains(@class, 'clr-input')]"
                        + " or .//*[contains(@class, 'form-control')])][1])[last()]");
        Locator visibleRow = firstVisible(row);
        if (visibleRow != null) {
            return visibleRow;
        }

        Locator lineLikeTarget = visibleScope.locator(
                "xpath=(.//*[normalize-space(.)=" + toXpathLiteral(rowNumberText) + "]"
                        + "[not(ancestor::*[self::thead or @role='columnheader'])]"
                        + "/following::*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox' or self::div or self::span][1])[1]");
        return firstVisible(lineLikeTarget);
    }

    private Locator resolveShippingMarksSection() {
        waitForFormControls();
        Locator section = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Shipping Marks'])[last()]"
                        + "/ancestor::*[((.//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'GROUP 1')]"
                        + " and .//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'LINE 1')])"
                        + " or .//*[self::button or @role='button' or self::a][contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'ADD GROUP')])][1]");
        Locator visibleSection = firstVisible(section);
        if (visibleSection != null) {
            return visibleSection;
        }

        Locator genericSection = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Shipping Marks'])[last()]"
                        + "/ancestor::*[.//*[self::button or @role='button' or self::a]"
                        + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'ADD GROUP')"
                        + " or contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'ADD LINE')]][1]");
        Locator visibleGenericSection = firstVisible(genericSection);
        if (visibleGenericSection != null) {
            return visibleGenericSection;
        }

        throw new IllegalStateException("Shipping Marks section was not visible.");
    }

    private Locator resolveShippingMarksInputBoxOrNull(Locator shippingMarksSection) {
        return resolveShippingMarksInputBoxOrNull(shippingMarksSection, 0);
    }

    private Locator resolveShippingMarksInputBoxOrNull(Locator shippingMarksSection, int occurrence) {
        Locator visibleScope = firstVisible(shippingMarksSection);
        if (visibleScope == null) {
            return null;
        }

        if (occurrence == 0) {
            Locator lineOneField = visibleScope.locator(
                    "xpath=((.//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'LINE 1')]"
                            + "[not(.//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'LINE 1')])])[1]"
                            + "/following::*[self::input or self::textarea or @role='textbox'][1])[1]");
            Locator visibleLineOneField = firstVisible(lineOneField);
            if (visibleLineOneField != null) {
                return visibleLineOneField;
            }

            Locator placeholderLineOneField = visibleScope.locator(
                    "input[placeholder*='Line 1'], textarea[placeholder*='Line 1'], input[placeholder*='max 17 chars'], textarea[placeholder*='max 17 chars']");
            Locator visiblePlaceholderLineOneField = firstVisible(placeholderLineOneField);
            if (visiblePlaceholderLineOneField != null) {
                return visiblePlaceholderLineOneField;
            }

            Locator firstTextbox = visibleScope.locator(
                    "xpath=(.//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'GROUP 1')]"
                            + "/following::*[self::input or self::textarea or @role='textbox'])[1]");
            Locator visibleFirstTextbox = firstVisible(firstTextbox);
            if (visibleFirstTextbox != null) {
                return visibleFirstTextbox;
            }
        }

        return resolveNthVisibleEditableFieldInScopeOrNull(visibleScope, occurrence);
    }

    private Locator waitForShippingMarksInputBoxOrNull(Locator shippingMarksSection, int occurrence, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            Locator field = resolveShippingMarksInputBoxOrNull(shippingMarksSection, occurrence);
            if (field != null) {
                return field;
            }
            page.waitForTimeout(100);
        }
        return null;
    }

    private Locator resolveShippingMarksActivatorOrNull(Locator shippingMarksSection) {
        Locator visibleScope = firstVisible(shippingMarksSection);
        if (visibleScope == null) {
            return null;
        }

        Locator lineOneLabel = visibleScope.locator(
                "xpath=(.//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'LINE 1')])[last()]");
        Locator visibleLineOneLabel = firstVisible(lineOneLabel);
        if (visibleLineOneLabel != null) {
            return visibleLineOneLabel;
        }

        Locator groupPanel = visibleScope.locator(
                "xpath=(.//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'GROUP 1')]"
                        + "/ancestor::*[.//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'LINE 1')]][1])[1]");
        return firstVisible(groupPanel);
    }

    private Locator resolveContainerDetailsSection() {
        waitForFormControls();
        Locator section = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Container Details (1)'])[last()]"
                        + "/ancestor::*[.//*[normalize-space(translate(., '*', ''))='Container Number']"
                        + " and .//*[normalize-space(translate(., '*', ''))='Size / Type']"
                        + " and .//*[contains(normalize-space(translate(., '*', '')), 'Weight (TNE)')]"
                        + " and .//*[normalize-space(translate(., '*', ''))='Seal Number']"
                        + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
        Locator visibleSection = firstVisible(section);
        if (visibleSection != null) {
            return visibleSection;
        }
        throw new IllegalStateException("Container Details (1) section was not visible.");
    }

    private Locator resolvePackingDescriptionSection() {
        waitForFormControls();
        String[] titles = new String[] { "Packing Description", "Packing Details" };
        for (String title : titles) {
            Locator section = resolvePackingDescriptionSectionOrNull(title);
            if (section != null) {
                return section;
            }
        }
        throw new IllegalStateException("Packing Description / Packing Details section was not visible.");
    }

    private Locator resolvePackingDescriptionSectionOrNull(String title) {
        String escapedTitle = toXpathLiteral(title);
        Locator section = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[(.//*[contains(normalize-space(translate(., '*', '')), 'Outer Pack Qty')]"
                        + " or .//*[contains(normalize-space(translate(., '*', '')), 'In Pack Qty')]"
                        + " or .//*[contains(normalize-space(translate(., '*', '')), 'Inner Pack Qty')]"
                        + " or .//*[contains(normalize-space(translate(., '*', '')), 'Inmost Pack Qty')])"
                        + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
        Locator visibleSection = firstVisible(section);
        if (visibleSection != null) {
            return visibleSection;
        }

        Locator titledContainer = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]");
        return firstVisible(titledContainer);
    }

    private Locator resolvePackingQuantityFieldOrNull(Locator scope, String rowLabel, int occurrence) {
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return null;
        }

        String escapedRowLabel = toXpathLiteral(rowLabel);
        Locator row = visibleScope.locator(
                "xpath=(.//*[normalize-space(translate(., '*', ''))=" + escapedRowLabel + "]"
                        + "[not(.//*[normalize-space(translate(., '*', ''))=" + escapedRowLabel + "])]"
                        + "/ancestor::*[count(.//input[not(@type='checkbox')] | .//textarea | .//select | .//*[@role='combobox'] | .//*[@role='textbox']) >= 2][1])");
        Locator fieldInRow = resolveNthVisibleEditableFieldInScopeOrNull(row, occurrence);
        if (fieldInRow != null) {
            return fieldInRow;
        }

        return resolveEditableFieldAfterScopeLabelOrNull(visibleScope, rowLabel, occurrence);
    }

    private Locator resolveItemQuantitySection() {
        waitForFormControls();
        String[] titles = new String[] { "Item Quantity", "Item Quantity & value", "Item Quantity & Value" };
        for (String title : titles) {
            String escapedTitle = toXpathLiteral(title);
            Locator section = page.locator(
                    "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                            + "/ancestor::*[(.//*[contains(normalize-space(translate(., '*', '')), 'Dutiable Quantity')]"
                            + " or .//*[contains(normalize-space(translate(., '*', '')), 'Total Dutiable Qty')]"
                            + " or .//*[contains(normalize-space(translate(., '*', '')), 'Total Dutiable Quantity')]"
                            + " or .//*[contains(normalize-space(translate(., '*', '')), 'HS Quantity')]"
                            + " or .//*[contains(normalize-space(translate(., '*', '')), 'Alcohol %')])"
                            + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
            Locator visibleSection = firstVisible(section);
            if (visibleSection != null) {
                return visibleSection;
            }

            Locator titledContainer = page.locator(
                    "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                            + "/ancestor::*[.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]");
            Locator visibleTitledContainer = firstVisible(titledContainer);
            if (visibleTitledContainer != null) {
                return visibleTitledContainer;
            }
        }
        throw new IllegalStateException("Item Quantity section was not visible.");
    }

    private Locator resolveAdditionalCascEntryRowOrNull(Locator cascRow, int occurrence) {
        Locator visibleScope = firstVisible(cascRow);
        if (visibleScope == null) {
            return null;
        }

        String rowNumber = String.valueOf(occurrence + 1);
        Locator numberedRow = visibleScope.locator(
                "xpath=(.//*[normalize-space(.)=" + toXpathLiteral(rowNumber) + "]"
                        + "[not(ancestor::*[self::thead or @role='columnheader'])]"
                        + "/ancestor::*[.//*[self::button or @role='button' or self::a or self::div or self::span]"
                        + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'COPY')]"
                        + " and count(.//input[not(@type='checkbox') and not(@readonly) and not(@disabled)]"
                        + " | .//textarea[not(@readonly) and not(@disabled)]"
                        + " | .//select[not(@disabled)]"
                        + " | .//*[@contenteditable='true']) >= 3][1])[last()]");
        Locator visibleRow = firstVisible(numberedRow);
        if (visibleRow != null) {
            return visibleRow;
        }

        Locator rows = visibleScope.locator(
                "xpath=.//*[self::button or @role='button' or self::a or self::div or self::span]"
                        + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'COPY')]"
                        + "/ancestor::*[count(.//input[not(@type='checkbox') and not(@readonly) and not(@disabled)]"
                        + " | .//textarea[not(@readonly) and not(@disabled)]"
                        + " | .//select[not(@disabled)]"
                        + " | .//*[@contenteditable='true']) >= 3][1]");
        visibleRow = nthVisibleLocatorByPositionOrNull(rows, occurrence);
        if (visibleRow != null) {
            return visibleRow;
        }

        Locator fallbackRows = visibleScope.locator(
                "xpath=.//input[not(@type='checkbox') and not(@readonly) and not(@disabled)]"
                        + "/ancestor::*[count(.//input[not(@type='checkbox') and not(@readonly) and not(@disabled)]"
                        + " | .//textarea[not(@readonly) and not(@disabled)]"
                        + " | .//select[not(@disabled)]"
                        + " | .//*[@contenteditable='true']) >= 3][1]");
        visibleRow = nthVisibleLocatorByPositionOrNull(fallbackRows, occurrence);
        if (visibleRow != null) {
            return visibleRow;
        }

        return null;
    }

    private List<PositionedField> collectVisiblePositionedFields(Locator fields) {
        List<PositionedField> positionedFields = new ArrayList<>();
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }

            BoundingBox box;
            try {
                box = candidate.boundingBox();
            } catch (PlaywrightException ignored) {
                continue;
            }
            if (box == null) {
                continue;
            }
            positionedFields.add(new PositionedField(index, box.x, box.y));
        }
        return positionedFields;
    }

    private List<PositionedElement> collectDistinctVisibleEditableElements(Locator fields) {
        List<PositionedElement> positionedElements = new ArrayList<>();
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }

            BoundingBox box;
            try {
                box = candidate.boundingBox();
            } catch (PlaywrightException ignored) {
                continue;
            }
            if (box == null || box.width < 4 || box.height < 4) {
                continue;
            }

            PositionedElement current = new PositionedElement(
                    index,
                    box.x,
                    box.y,
                    box.width,
                    box.height,
                    editableCandidatePriority(candidate));
            int duplicateIndex = findDuplicatePositionedElementIndex(positionedElements, current);
            if (duplicateIndex >= 0) {
                PositionedElement existing = positionedElements.get(duplicateIndex);
                if (current.priority() > existing.priority()
                        || (current.priority() == existing.priority()
                        && current.width() * current.height() > existing.width() * existing.height())) {
                    positionedElements.set(duplicateIndex, current);
                }
                continue;
            }
            positionedElements.add(current);
        }
        return positionedElements;
    }

    private List<Locator> orderedVisibleEditableFields(Locator scope) {
        List<Locator> orderedFields = new ArrayList<>();
        Locator fields = scope.locator(combinedEditableSelector());
        List<PositionedElement> positionedElements = collectDistinctVisibleEditableElements(fields);
        positionedElements.stream()
                .sorted(Comparator.comparingDouble(PositionedElement::y).thenComparingDouble(PositionedElement::x))
                .forEach(positionedElement -> {
                    Locator resolvedField = resolveConcreteEditableFieldOrNull(fields.nth(positionedElement.index()));
                    if (resolvedField != null) {
                        orderedFields.add(resolvedField);
                    }
                });
        return orderedFields;
    }

    private int findDuplicatePositionedElementIndex(List<PositionedElement> elements, PositionedElement candidate) {
        for (int index = 0; index < elements.size(); index++) {
            PositionedElement existing = elements.get(index);
            double xDistance = Math.abs(existing.x() - candidate.x());
            double yDistance = Math.abs(existing.y() - candidate.y());
            double widthDistance = Math.abs(existing.width() - candidate.width());
            double heightDistance = Math.abs(existing.height() - candidate.height());
            if (xDistance <= 8.0d && yDistance <= 8.0d && widthDistance <= 24.0d && heightDistance <= 24.0d) {
                return index;
            }
        }
        return -1;
    }

    private Locator nthVisibleLocatorOrNull(Locator locator, int occurrence) {
        int visibleIndex = 0;
        int count = locator.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = locator.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            if (visibleIndex++ == occurrence) {
                return candidate;
            }
        }
        return null;
    }

    private Locator nthVisibleLocatorByPositionOrNull(Locator locator, int occurrence) {
        List<PositionedField> positionedLocators = collectVisiblePositionedFields(locator);
        if (occurrence < 0 || occurrence >= positionedLocators.size()) {
            return null;
        }

        List<PositionedField> sortedLocators = positionedLocators.stream()
                .sorted(Comparator.comparingDouble(PositionedField::y).thenComparingDouble(PositionedField::x))
                .toList();
        return locator.nth(sortedLocators.get(occurrence).index());
    }

    private Locator resolveAdditionalCascFieldOrNull(Locator additionalCascRow, String label) {
        Locator visibleScope = firstVisible(additionalCascRow);
        if (visibleScope == null || label == null || label.isBlank()) {
            return null;
        }

        String escapedLabel = toXpathLiteral(label);
        Locator placeholderField = visibleScope.locator(
                "xpath=(.//*[self::input or self::textarea or self::select]"
                        + "[normalize-space(@placeholder)=" + escapedLabel
                        + " or contains(normalize-space(@aria-label), " + escapedLabel + ")])[1]");
        Locator visiblePlaceholderField = firstVisible(placeholderField);
        if (visiblePlaceholderField != null) {
            return visiblePlaceholderField;
        }

        int occurrence = switch (normalize(label).toUpperCase()) {
            case "CODE 1" -> 0;
            case "CODE 2" -> 1;
            case "CODE 3" -> 2;
            default -> -1;
        };
        if (occurrence < 0) {
            return null;
        }
        return resolveVisibleEditableFieldInRowOrNull(additionalCascRow, occurrence);
    }

    private void ensureRenderedFieldValue(Locator field, String expectedValue, String label) {
        if (waitForAnyRenderedFieldValue(field, 1500, expectedValue)) {
            return;
        }

        try {
            field.fill(expectedValue);
        } catch (PlaywrightException ignored) {
        }
        if (!waitForAnyRenderedFieldValue(field, 1000, expectedValue)) {
            ensureTextFieldValue(field, expectedValue);
        }
        if (!waitForAnyRenderedFieldValue(field, 1500, expectedValue)) {
            throw new IllegalStateException(buildFieldVerificationFailure(
                    label + " value was not rendered",
                    field,
                    expectedValue));
        }
    }

    private void clickAdditionalCascAddButton(Locator cascRow) {
        Locator visibleSection = firstVisible(cascRow);
        if (visibleSection == null) {
            captureAdditionalCascFailureArtifacts("add-button-scope-not-visible");
            throw new IllegalStateException("CASC Details section was not visible for Additional CASC add.");
        }

        Locator candidates = visibleSection.locator(
                "button, [role='button'], input[type='button'], input[type='submit'], a, div, span");
        int count = candidates.count();
        for (int index = count - 1; index >= 0; index--) {
            Locator candidate = candidates.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }

            String text = normalize(candidate.innerText()).toUpperCase();
            String ariaLabel = normalize(candidate.getAttribute("aria-label")).toUpperCase();
            String title = normalize(candidate.getAttribute("title")).toUpperCase();
            String value = normalize(candidate.getAttribute("value")).toUpperCase();
            String name = normalize(candidate.getAttribute("name")).toUpperCase();
            String id = normalize(candidate.getAttribute("id")).toUpperCase();
            String dataAction = normalize(candidate.getAttribute("data-action")).toUpperCase();
            boolean exactAdd = "ADD".equals(text)
                    || "ADD".equals(ariaLabel)
                    || "ADD".equals(title)
                    || "ADD".equals(value)
                    || "ADD".equals(name)
                    || "ADD".equals(id)
                    || "ADD".equals(dataAction);
            if (!exactAdd) {
                continue;
            }

            candidate.scrollIntoViewIfNeeded();
            candidate.click(new Locator.ClickOptions().setForce(true));
            return;
        }

        captureAdditionalCascFailureArtifacts("add-button-not-visible");
        throw new IllegalStateException("Additional CASC Add button was not visible.");
    }

    private void clickAddCascProductButton(Locator cascSection) {
        clickButtonInScope(cascSection, "ADD CASC PRODUCT", "ADD PRODUCT");
    }

    private boolean hasVisibleTextInScope(Locator scope, String text) {
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null || text == null || text.isBlank()) {
            return false;
        }

        String expected = normalize(text);
        Locator candidates = visibleScope.locator(
                "xpath=.//*[contains(normalize-space(translate(., '*', '')), " + toXpathLiteral(expected) + ")]");
        int count = candidates.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = candidates.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            String candidateText = normalize(candidate.innerText());
            if (candidateText.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean waitForVisibleSuggestionExact(int timeoutMs, String... values) {
        List<String> candidates = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                appendCandidate(candidates, value);
            }
        }
        if (candidates.isEmpty()) {
            return true;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Boolean visible = (Boolean) page.evaluate("""
                    expectedValues => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const expectedList = expectedValues.map(normalize).filter(Boolean);
                        const isVisible = element => element && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const overlayRoots = [
                            ...document.querySelectorAll(
                                '[role="listbox"], [role="menu"], .ng-dropdown-panel, .cdk-overlay-pane, .cdk-overlay-container, .mat-mdc-autocomplete-panel, .mat-mdc-select-panel, .ui-autocomplete-panel, .dropdown-menu')
                        ].filter(element => isVisible(element));
                        const optionQuery = '[role="option"], .ng-option, .mat-mdc-option, li, [class*="option"], [class*="menu-item"], [class*="dropdown-item"]';
                        const candidates = (overlayRoots.length > 0
                                ? overlayRoots.flatMap(root => Array.from(root.querySelectorAll(optionQuery)))
                                : Array.from(document.querySelectorAll(optionQuery)))
                            .filter(element => isVisible(element))
                            .filter(element => !['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName))
                            .map(element => normalize(element.innerText || element.textContent));
                        return candidates.some(candidate => expectedList.some(expected => candidate === expected));
                    }
                    """, candidates);
            if (Boolean.TRUE.equals(visible)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean waitForAnyVisibleSuggestion(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Boolean visible = (Boolean) page.evaluate("""
                    () => {
                        const isVisible = element => element && (element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const overlayRoots = [
                            ...document.querySelectorAll(
                                '[role="listbox"], [role="menu"], .ng-dropdown-panel, .cdk-overlay-pane, .cdk-overlay-container, .mat-mdc-autocomplete-panel, .mat-mdc-select-panel, .ui-autocomplete-panel, .dropdown-menu')
                        ].filter(element => isVisible(element));
                        const optionQuery = '[role="option"], .ng-option, .mat-mdc-option, li, [class*="option"], [class*="menu-item"], [class*="dropdown-item"]';
                        const candidates = (overlayRoots.length > 0
                                ? overlayRoots.flatMap(root => Array.from(root.querySelectorAll(optionQuery)))
                                : Array.from(document.querySelectorAll(optionQuery)))
                            .filter(element => isVisible(element))
                            .filter(element => !['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName));
                        return candidates.length > 0;
                    }
                    """);
            if (Boolean.TRUE.equals(visible)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    protected Locator resolveFieldByLabelOrNull(String label, int occurrence) {
        waitForFormControls();
        String escapedLabel = toXpathLiteral(label);
        String controlQuery = "self::input or self::textarea or self::select or @role='combobox' or @role='textbox'";
        String labelQuery =
                "//*[self::label or self::span or self::div or self::p]"
                        + "[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])]";

        Locator matchingLabels = page.locator("xpath=" + labelQuery);
        Locator associatedControl = resolveAssociatedControl(page.locator("body"), matchingLabels, occurrence, controlQuery);
        if (associatedControl != null) {
            return preferNativeSelect(associatedControl);
        }

        Locator fromNearestContainer = page.locator(
                "xpath=((" + labelQuery + ")[" + (occurrence + 1) + "]/ancestor::*[.//*[" + controlQuery + "]][1]"
                        + "//*[" + controlQuery + "])[1]");
        Locator visibleFromNearestContainer = resolveConcreteEditableFieldOrNull(fromNearestContainer);
        if (visibleFromNearestContainer != null) {
            return preferNativeSelect(visibleFromNearestContainer);
        }

        Locator fromLabel = page.locator(
                "xpath=(" + labelQuery + ")[" + (occurrence + 1)
                        + "]/following::*[" + controlQuery + "][1]");
        Locator visibleFromLabel = resolveConcreteEditableFieldOrNull(fromLabel);
        if (visibleFromLabel != null) {
            return visibleFromLabel;
        }

        Locator placeholder = page.locator(
                "input[placeholder='" + escapeForSelector(label) + "'], textarea[placeholder='" + escapeForSelector(label) + "']");
        Locator visiblePlaceholder = firstVisible(placeholder);
        if (visiblePlaceholder != null) {
            return visiblePlaceholder;
        }
        return null;
    }

    protected Locator resolveSelectLikeFieldByLabelOrNull(String label, int occurrence) {
        waitForFormControls();
        String escapedLabel = toXpathLiteral(label);
        String controlQuery = "self::select or @role='combobox' or @role='listbox' or @aria-haspopup='listbox'"
                + " or @aria-haspopup='menu' or contains(@class, 'ng-select') or contains(@class, 'mat-mdc-select')"
                + " or contains(@class, 'mat-select') or contains(@class, 'clr-select')"
                + " or contains(@class, 'select') or contains(@class, 'dropdown')";
        String labelQuery =
                "//*[self::label or self::span or self::div or self::p]"
                        + "[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])]";

        Locator matchingLabels = page.locator("xpath=" + labelQuery);
        Locator associatedControl = resolveAssociatedControl(page.locator("body"), matchingLabels, occurrence, controlQuery);
        if (associatedControl != null) {
            return associatedControl;
        }

        Locator fromNearestContainer = page.locator(
                "xpath=((" + labelQuery + ")[" + (occurrence + 1) + "]/ancestor::*[.//*[" + controlQuery + "]][1]"
                        + "//*[" + controlQuery + "])[1]");
        Locator visibleFromNearestContainer = resolveConcreteEditableFieldOrNull(fromNearestContainer);
        if (visibleFromNearestContainer != null) {
            return visibleFromNearestContainer;
        }

        Locator fromLabel = page.locator(
                "xpath=(" + labelQuery + ")[" + (occurrence + 1)
                        + "]/following::*[" + controlQuery + "][1]");
        Locator visibleFromLabel = resolveConcreteEditableFieldOrNull(fromLabel);
        return visibleFromLabel == null ? null : preferNativeSelect(visibleFromLabel);
    }

    private Locator preferNativeSelect(Locator field) {
        if (field == null) {
            return null;
        }
        try {
            String tagName = String.valueOf(field.evaluate("element => element.tagName || ''"));
            if ("SELECT".equalsIgnoreCase(normalize(tagName))) {
                return field;
            }
        } catch (PlaywrightException ignored) {
        }

        try {
            Locator nestedSelect = firstVisible(field.locator("select"));
            if (nestedSelect != null) {
                return nestedSelect;
            }
        } catch (PlaywrightException ignored) {
        }
        return field;
    }

    protected Locator resolveFieldByLabelInSectionOrNull(String sectionTitle, String label, int occurrence) {
        waitForFormControls();
        Locator section = resolveSectionOrNull(sectionTitle);
        if (section == null) {
            return null;
        }
        String escapedLabel = toXpathLiteral(label);
        String controlQuery = "self::input or self::textarea or self::select or @role='combobox' or @role='textbox'";
        String labelQuery =
                ".//*[self::label or self::span or self::div or self::p]"
                        + "[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])]";

        Locator matchingLabels = section.locator("xpath=" + labelQuery);
        Locator associatedControl = resolveAssociatedControl(section, matchingLabels, occurrence, controlQuery);
        if (associatedControl != null) {
            return associatedControl;
        }

        Locator fromNearestContainer = section.locator(
                "xpath=((" + labelQuery + ")[" + (occurrence + 1) + "]/ancestor::*[.//*[" + controlQuery + "]][1]"
                        + "//*[" + controlQuery + "])[1]");
        Locator visibleFromNearestContainer = resolveConcreteEditableFieldOrNull(fromNearestContainer);
        if (visibleFromNearestContainer != null) {
            return visibleFromNearestContainer;
        }

        Locator fromLabel = section.locator(
                "xpath=(" + labelQuery + ")[" + (occurrence + 1)
                        + "]/following::*[" + controlQuery + "][1]");
        Locator visibleFromLabel = resolveConcreteEditableFieldOrNull(fromLabel);
        if (visibleFromLabel != null) {
            return visibleFromLabel;
        }

        Locator placeholders = section.locator(
                "input[placeholder='" + escapeForSelector(label) + "'], textarea[placeholder='" + escapeForSelector(label) + "']");
        return firstVisible(placeholders);
    }

    private Locator resolveBgIndicatorField() {
        Locator checksSection = resolveSection("Checks");
        String escapedLabel = toXpathLiteral("BG Indicator");

        Locator selectField = checksSection.locator(
                "xpath=((.//*[self::label or self::span or self::div or self::p]"
                        + "[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])])[1]"
                        + "/following::*[self::select][1])[1]");
        Locator visibleSelectField = firstVisible(selectField);
        if (visibleSelectField != null) {
            return visibleSelectField;
        }

        Locator field = checksSection.locator(
                "xpath=((.//*[self::label or self::span or self::div or self::p]"
                        + "[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])])[1]"
                        + "/following::*[self::select or @role='combobox' or @role='listbox' or @role='button'"
                        + " or @aria-haspopup='listbox' or @aria-haspopup='menu'"
                        + " or contains(@class, 'ng-select') or contains(@class, 'mat-mdc-select')"
                        + " or contains(@class, 'select') or contains(@class, 'dropdown')][1])[1]");
        Locator visibleField = firstVisible(field);
        if (visibleField != null) {
            return visibleField;
        }

        Locator firstSelectLikeField = checksSection.locator(
                "select, [role='combobox'], [role='listbox'], [aria-haspopup='listbox'], [aria-haspopup='menu'], .ng-select, .mat-mdc-select, .mat-select");
        Locator visibleFirstSelectLikeField = firstVisible(firstSelectLikeField);
        if (visibleFirstSelectLikeField != null) {
            return visibleFirstSelectLikeField;
        }

        Locator fallbackField = resolveFieldByLabelInSectionOrNull("Checks", "BG Indicator", 0);
        if (fallbackField != null) {
            return fallbackField;
        }

        throw new IllegalStateException("BG Indicator field was not visible in Checks section.");
    }

    private Locator resolveAssociatedControl(Locator scope, Locator labels, int occurrence, String controlQuery) {
        int matchedVisibleLabels = 0;
        int count = labels.count();
        for (int index = 0; index < count; index++) {
            Locator label = labels.nth(index);
            if (!label.isVisible()) {
                continue;
            }
            if (matchedVisibleLabels++ != occurrence) {
                continue;
            }

            String targetId = normalize(label.getAttribute("for"));
            if (!targetId.isBlank()) {
                Locator directMatch = scope.locator("#" + escapeCssIdentifier(targetId));
                Locator visibleDirectMatch = resolveConcreteEditableFieldOrNull(directMatch);
                if (visibleDirectMatch != null) {
                    return visibleDirectMatch;
                }
            }

            Locator nestedControl = resolveConcreteEditableFieldOrNull(label.locator("xpath=.//*[" + controlQuery + "]"));
            if (nestedControl != null) {
                return nestedControl;
            }
            return null;
        }
        return null;
    }

    protected Locator resolveSection(String sectionTitle) {
        waitForFormControls();
        String escapedTitle = toXpathLiteral(sectionTitle);
        String controlQuery = ".//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox'] or .//button";

        Locator exactNearestSection = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[" + controlQuery + "][1])");
        Locator visibleExactNearestSection = firstVisible(exactNearestSection);
        if (visibleExactNearestSection != null) {
            return visibleExactNearestSection;
        }

        Locator section = page.locator(
                "xpath=(//*[contains(normalize-space(.), " + escapedTitle + ")]"
                        + "[not(.//*[contains(normalize-space(.), " + escapedTitle + ")])])[1]"
                        + "/ancestor::*[" + controlQuery + "]");
        Locator visibleSection = firstVisible(section);
        if (visibleSection != null) {
            return visibleSection;
        }
        throw new IllegalStateException("Section container was not visible: " + sectionTitle);
    }

    protected Locator resolveSectionOrNull(String sectionTitle) {
        try {
            return resolveSection(sectionTitle);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    protected void ensureAccordionExpanded(String sectionTitle, String... expectedTexts) {
        waitForFormControls();
        Locator title = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + toXpathLiteral(sectionTitle) + "])[last()]");
        Locator visibleTitle = lastVisible(title);
        if (visibleTitle == null || isAccordionContentVisible(visibleTitle, expectedTexts)) {
            return;
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            closeTransientOverlays();
            try {
                Boolean clicked = (Boolean) visibleTitle.evaluate("""
                        element => {
                            const isVisible = candidate => {
                                if (!candidate) {
                                    return false;
                                }
                                const style = window.getComputedStyle(candidate);
                                return !!style
                                    && style.display !== 'none'
                                    && style.visibility !== 'hidden'
                                    && (candidate.offsetWidth || candidate.offsetHeight || candidate.getClientRects().length);
                            };
                            const candidates = [
                                element.closest('button, [role="button"], mat-expansion-panel-header, .mat-expansion-panel-header, .accordion-header, .panel-heading, .card-header'),
                                element.previousElementSibling,
                                element.parentElement,
                                element
                            ].filter((candidate, index, all) =>
                                candidate && isVisible(candidate) && all.indexOf(candidate) === index);
                            for (const candidate of candidates) {
                                candidate.scrollIntoView({ block: 'center' });
                                candidate.click();
                                return true;
                            }
                            return false;
                        }
                        """);
                if (Boolean.FALSE.equals(clicked)) {
                    break;
                }
            } catch (PlaywrightException ignored) {
            }

            page.waitForTimeout(250);
            if (isAccordionContentVisible(visibleTitle, expectedTexts)) {
                return;
            }
        }
    }

    protected Locator firstVisible(Locator locator) {
        int count = locator.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = locator.nth(index);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        return null;
    }

    protected Locator lastVisible(Locator locator) {
        int count = locator.count();
        for (int index = count - 1; index >= 0; index--) {
            Locator candidate = locator.nth(index);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isAccordionContentVisible(Locator title, String... expectedTexts) {
        try {
            return Boolean.TRUE.equals(title.evaluate("""
                    (element, texts) => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const isVisible = candidate => {
                            if (!candidate) {
                                return false;
                            }
                            const style = window.getComputedStyle(candidate);
                            return !!style
                                && style.display !== 'none'
                                && style.visibility !== 'hidden'
                                && (candidate.offsetWidth || candidate.offsetHeight || candidate.getClientRects().length);
                        };
                        const expected = (texts || []).map(normalize).filter(Boolean);
                        let current = element;
                        while (current) {
                            const text = normalize(current.innerText || current.textContent);
                            const hasEditableField = !!current.querySelector(
                                'input, textarea, select, [role="combobox"], [role="textbox"]');
                            if (isVisible(current) && hasEditableField
                                    && expected.every(candidate => text.includes(candidate))) {
                                return true;
                            }
                            current = current.parentElement;
                        }
                        return false;
                    }
                    """, expectedTexts));
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    protected void saveDraft() {
        clickActionButton("SAVE DRAFT", "Save Draft");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        closeTransientOverlays();
        page.waitForTimeout(300);
    }

    protected void goToNextSection() {
        clickActionButton("NEXT", "Next");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        closeTransientOverlays();
    }

    protected void openSection(String sectionName) {
        closeTransientOverlays();
        Locator tabs = page.locator(
                "[role='tab'], button, a, span, div");
        String normalizedSection = normalize(sectionName);
        List<String> visibleTabs = new ArrayList<>();
        List<Locator> fallbackMatches = new ArrayList<>();
        int count = tabs.count();
        for (int index = 0; index < count; index++) {
            Locator tab = tabs.nth(index);
            if (!tab.isVisible()) {
                continue;
            }
            String text = normalize(tab.innerText());
            if (text.isBlank()) {
                continue;
            }
            visibleTabs.add(text);
            if (normalizedSection.equals(text)) {
                tab.scrollIntoViewIfNeeded();
                tab.click(new Locator.ClickOptions().setForce(true));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                return;
            }
            if (sectionTabMatches(normalizedSection, text)) {
                fallbackMatches.add(tab);
            }
        }
        for (Locator fallbackMatch : fallbackMatches) {
            fallbackMatch.scrollIntoViewIfNeeded();
            fallbackMatch.click(new Locator.ClickOptions().setForce(true));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            return;
        }
        logFieldMappingWarning("Section tab lookup failed for '" + sectionName
                + "'. Visible tabs: " + String.join(" | ", visibleTabs));
        throw new IllegalStateException("Section tab was not visible: " + sectionName);
    }

    private boolean sectionTabMatches(String expectedSection, String actualTab) {
        String normalizedExpected = normalize(expectedSection);
        String normalizedActual = normalize(actualTab);
        if (normalizedExpected.equals(normalizedActual)) {
            return true;
        }

        String expectedWithoutShortcut = stripSectionShortcut(normalizedExpected);
        String actualWithoutShortcut = stripSectionShortcut(normalizedActual);
        if (!expectedWithoutShortcut.isBlank() && expectedWithoutShortcut.equals(actualWithoutShortcut)) {
            return true;
        }

        String expectedCompact = collapseInfoSuffix(expectedWithoutShortcut);
        String actualCompact = collapseInfoSuffix(actualWithoutShortcut);
        return !expectedCompact.isBlank()
                && (expectedCompact.equals(actualCompact)
                || normalizedActual.startsWith(expectedWithoutShortcut)
                || actualWithoutShortcut.startsWith(expectedWithoutShortcut)
                || expectedWithoutShortcut.startsWith(actualWithoutShortcut)
                || expectedCompact.equals(normalizedActual)
                || expectedCompact.equals(actualWithoutShortcut));
    }

    private String stripSectionShortcut(String value) {
        String normalized = normalize(value);
        return normalized.replaceFirst("\\s*\\([A-Za-z]\\)$", "").trim();
    }

    private String collapseInfoSuffix(String value) {
        String normalized = stripSectionShortcut(value);
        if (normalized.endsWith(" Info")) {
            return normalized.substring(0, normalized.length() - " Info".length()).trim();
        }
        return normalized;
    }

    private void clickActionButton(String... buttonTexts) {
        for (String buttonText : buttonTexts) {
            if (clickActionButtonIfVisible(buttonText)) {
                return;
            }
        }
        throw new IllegalStateException("Action button was not visible: " + String.join(", ", buttonTexts));
    }

    private void clickActionButtonWithDelay(int delayMs, String... buttonTexts) {
        if (delayMs > 0) {
            page.waitForTimeout(delayMs);
        }
        clickActionButton(buttonTexts);
    }

    private void clickSubmitDeclarationWithDelay(int delayMs) {
        Locator button = resolveSubmitDeclarationButton();
        if (delayMs > 0) {
            page.waitForTimeout(delayMs);
        }

        closeTransientOverlays();
        button = resolveSubmitDeclarationButton();
        button.scrollIntoViewIfNeeded();
        try {
            button.click();
        } catch (PlaywrightException ignored) {
            button.click(new Locator.ClickOptions().setForce(true));
        }
        page.waitForTimeout(UI_POST_SUBMIT_WAIT_MS);
    }

    private void saveDraftAndWaitForCompletion() {
        waitForActionButtonEnabled("SAVE DRAFT", 15000);
        clickActionButtonExactWithRetry("SAVE DRAFT", 3);
        waitForActionButtonsReady(UI_POST_SAVE_READY_TIMEOUT_MS, "SAVE DRAFT", "SUBMIT DECLARATION");
        summaryDraftSaved = true;
    }

    protected void saveDraftAndAdvanceToNextSection() {
        waitForActionButtonEnabled("SAVE DRAFT", 15000);
        clickActionButtonExactWithRetry("SAVE DRAFT", 3);
        waitForActionButtonsReady(UI_POST_SAVE_READY_TIMEOUT_MS, "SAVE DRAFT", "NEXT");
        clickActionButtonExactWithRetry("NEXT", 3);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        closeTransientOverlays();
    }

    private void waitForPostSaveReadyState(int timeoutMs) {
        waitForActionButtonsReady(timeoutMs, "SAVE DRAFT", "SUBMIT DECLARATION");
    }

    private void waitForActionButtonsReady(int timeoutMs, String... buttonTexts) {
        int stableChecks = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Boolean ready = (Boolean) page.evaluate("""
                    expectedButtons => {
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
                        const isEnabled = element => {
                            const ariaDisabled = normalize(element.getAttribute('aria-disabled'));
                            const disabled = element.disabled === true || element.hasAttribute('disabled');
                            return !disabled && ariaDisabled !== 'TRUE';
                        };
                        const findExactButton = expected => Array.from(document.querySelectorAll(
                                "button, [role='button'], input[type='button'], input[type='submit'], a"))
                            .filter(isVisible)
                            .reverse()
                            .find(element => {
                                const text = normalize(element.innerText || element.textContent);
                                const ariaLabel = normalize(element.getAttribute('aria-label'));
                                const value = normalize(element.getAttribute('value'));
                                return text === expected || ariaLabel === expected || value === expected;
                            });

                        const busySelector = [
                            '[aria-busy="true"]',
                            '.spinner',
                            '.loading',
                            '.loader',
                            '.progress-spinner',
                            '.mat-mdc-progress-spinner',
                            '.ngx-spinner-overlay',
                            '.cdk-overlay-backdrop-showing'
                        ].join(', ');
                        const hasBusyOverlay = Array.from(document.querySelectorAll(busySelector)).some(isVisible);
                        if (hasBusyOverlay) {
                            return false;
                        }

                        const requiredButtons = Array.isArray(expectedButtons)
                            ? expectedButtons.map(normalize).filter(Boolean)
                            : [];
                        return requiredButtons.every(buttonText => {
                            const button = findExactButton(buttonText);
                            return !!button && isEnabled(button);
                        });
                    }
                    """, List.of(buttonTexts));
            if (Boolean.TRUE.equals(ready)) {
                stableChecks++;
                if (stableChecks >= 2) {
                    return;
                }
            } else {
                stableChecks = 0;
            }
            page.waitForTimeout(200);
        }
        throw new IllegalStateException("Page did not return to ready state after saving draft. Buttons: "
                + String.join(", ", buttonTexts));
    }

    private void waitForActionButtonEnabled(String buttonText, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Boolean enabled = (Boolean) page.evaluate("""
                    expected => {
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
                        const isEnabled = element => {
                            const ariaDisabled = normalize(element.getAttribute('aria-disabled'));
                            const disabled = element.disabled === true || element.hasAttribute('disabled');
                            return !disabled && ariaDisabled !== 'TRUE';
                        };
                        const target = Array.from(document.querySelectorAll(
                                "button, [role='button'], input[type='button'], input[type='submit'], a"))
                            .filter(isVisible)
                            .reverse()
                            .find(element => {
                                const text = normalize(element.innerText || element.textContent);
                                const ariaLabel = normalize(element.getAttribute('aria-label'));
                                const value = normalize(element.getAttribute('value'));
                                return text === expected || ariaLabel === expected || value === expected;
                            });
                        return !!target && isEnabled(target);
                    }
                    """, buttonText.trim().toUpperCase());
            if (Boolean.TRUE.equals(enabled)) {
                return;
            }
            page.waitForTimeout(250);
        }
        throw new IllegalStateException("Action button was not enabled: " + buttonText);
    }

    private void clickActionButtonExactWithRetry(String buttonText, int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            closeTransientOverlays();
            if (clickExactActionButtonIfVisible(buttonText)) {
                return;
            }
            page.waitForTimeout(500);
        }
        throw new IllegalStateException("Action button was not clickable: " + buttonText);
    }

    private boolean clickExactActionButtonIfVisible(String buttonText) {
        String expected = buttonText.trim().toUpperCase();
        return Boolean.TRUE.equals(page.evaluate("""
                expected => {
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
                    const isEnabled = element => {
                        const ariaDisabled = normalize(element.getAttribute('aria-disabled'));
                        const disabled = element.disabled === true || element.hasAttribute('disabled');
                        return !disabled && ariaDisabled !== 'TRUE';
                    };
                    const target = Array.from(document.querySelectorAll(
                            "button, [role='button'], input[type='button'], input[type='submit'], a"))
                        .filter(isVisible)
                        .reverse()
                        .find(element => {
                            const text = normalize(element.innerText || element.textContent);
                            const ariaLabel = normalize(element.getAttribute('aria-label'));
                            const value = normalize(element.getAttribute('value'));
                            return (text === expected || ariaLabel === expected || value === expected) && isEnabled(element);
                        });
                    if (!target) {
                        return false;
                    }
                    target.scrollIntoView({ block: 'center' });
                    target.click();
                    return true;
                }
                """, expected));
    }

    private void advanceToSubmitDeclarationIfNecessary() {
        Locator submitButton = resolveSubmitDeclarationButtonOrNull();
        if (submitButton != null) {
            return;
        }

        for (int attempts = 0; attempts < 3; attempts++) {
            Locator nextButton = resolveActionButtonOrNull("NEXT");
            if (nextButton == null) {
                break;
            }

            closeTransientOverlays();
            nextButton.scrollIntoViewIfNeeded();
            try {
                nextButton.click();
            } catch (PlaywrightException ignored) {
                nextButton.click(new Locator.ClickOptions().setForce(true));
            }
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(UI_ACTION_PAUSE_MS);

            submitButton = resolveSubmitDeclarationButtonOrNull();
            if (submitButton != null) {
                return;
            }
        }
    }

    private boolean clickActionButtonIfVisible(String buttonText) {
        closeTransientOverlays();
        return clickMatchingPageButton(buttonText);
    }

    private Locator resolveActionButton(String... buttonTexts) {
        for (String buttonText : buttonTexts) {
            Locator button = resolveActionButtonOrNull(buttonText);
            if (button != null) {
                return button;
            }
        }
        throw new IllegalStateException("Action button was not visible: " + String.join(", ", buttonTexts));
    }

    private Locator resolveActionButtonOrNull(String buttonText) {
        closeTransientOverlays();
        Locator buttons = page.locator("button, [role='button'], input[type='button'], input[type='submit'], a");
        String expected = buttonText.trim().toUpperCase();
        Object matchedIndex = page.evaluate("""
                expected => {
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
                    const elements = Array.from(document.querySelectorAll(
                        "button, [role='button'], input[type='button'], input[type='submit'], a"));
                    for (let index = elements.length - 1; index >= 0; index--) {
                        const element = elements[index];
                        if (!isVisible(element)) {
                            continue;
                        }
                        const text = normalize(element.innerText || element.textContent);
                        const ariaLabel = normalize(element.getAttribute('aria-label'));
                        const title = normalize(element.getAttribute('title'));
                        const value = normalize(element.getAttribute('value'));
                        const name = normalize(element.getAttribute('name'));
                        const id = normalize(element.getAttribute('id'));
                        const dataAction = normalize(element.getAttribute('data-action'));
                        if (text.includes(expected)
                                || ariaLabel.includes(expected)
                                || title.includes(expected)
                                || value.includes(expected)
                                || name.includes(expected)
                                || id.includes(expected)
                                || dataAction.includes(expected)) {
                            return index;
                        }
                    }
                    return -1;
                }
                """, expected);
        if (!(matchedIndex instanceof Number number) || number.intValue() < 0) {
            return null;
        }
        return buttons.nth(number.intValue());
    }

    private Locator resolveSubmitDeclarationButton() {
        Locator resolved = resolveSubmitDeclarationButtonOrNull();
        if (resolved != null) {
            return resolved;
        }

        throw new IllegalStateException("Submit Declaration button was not visible.");
    }

    private Locator resolveSubmitDeclarationButtonOrNull() {
        closeTransientOverlays();
        Locator exactText = page.locator(
                "xpath=(//*[self::button or @role='button' or self::a]"
                        + "[normalize-space(translate(., '*', ''))='SUBMIT DECLARATION'])[last()]");
        Locator visibleExactText = lastVisible(exactText);
        if (visibleExactText != null) {
            return visibleExactText;
        }

        Locator exactValue = page.locator(
                "input[type='button'][value='SUBMIT DECLARATION'], input[type='submit'][value='SUBMIT DECLARATION']");
        Locator visibleExactValue = lastVisible(exactValue);
        if (visibleExactValue != null) {
            return visibleExactValue;
        }

        Locator exactAria = page.locator(
                "xpath=(//*[self::button or @role='button' or self::a]"
                        + "[normalize-space(translate(@aria-label, '*', ''))='SUBMIT DECLARATION'])[last()]");
        Locator visibleExactAria = lastVisible(exactAria);
        if (visibleExactAria != null) {
            return visibleExactAria;
        }

        Locator fallback = resolveActionButtonOrNull("SUBMIT DECLARATION");
        if (fallback != null) {
            return fallback;
        }
        return null;
    }

    private void clickButtonInScope(Locator scope, String... buttonTexts) {
        for (String buttonText : buttonTexts) {
            if (clickButtonInScopeIfVisible(scope, buttonText)) {
                return;
            }
        }
        throw new IllegalStateException("Scoped action button was not visible: " + String.join(", ", buttonTexts));
    }

    private boolean clickButtonInScopeIfVisible(Locator scope, String buttonText) {
        closeTransientOverlays();
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return false;
        }

        Locator buttons = visibleScope.locator("button, [role='button'], input[type='button'], input[type='submit'], a");
        return clickMatchingButton(buttons, buttonText);
    }

    private boolean hasButtonInScopeVisible(Locator scope, String buttonText) {
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return false;
        }

        Locator buttons = visibleScope.locator("button, [role='button'], input[type='button'], input[type='submit'], a");
        String expected = buttonText.trim().toUpperCase();
        Object matched = buttons.evaluateAll("""
                (elements, expected) => {
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
                    return elements.some(element => {
                        if (!isVisible(element)) {
                            return false;
                        }
                        const text = normalize(element.innerText || element.textContent);
                        const ariaLabel = normalize(element.getAttribute('aria-label'));
                        const title = normalize(element.getAttribute('title'));
                        const value = normalize(element.getAttribute('value'));
                        const name = normalize(element.getAttribute('name'));
                        const id = normalize(element.getAttribute('id'));
                        const dataAction = normalize(element.getAttribute('data-action'));
                        return text.includes(expected)
                            || ariaLabel.includes(expected)
                            || title.includes(expected)
                            || value.includes(expected)
                            || name.includes(expected)
                            || id.includes(expected)
                            || dataAction.includes(expected);
                    });
                }
                """, expected);
        return Boolean.TRUE.equals(matched);
    }

    private boolean clickMatchingButton(Locator buttons, String buttonText) {
        String expected = buttonText.trim().toUpperCase();
        Object clicked = buttons.evaluateAll("""
                (elements, expected) => {
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
                    for (let index = elements.length - 1; index >= 0; index--) {
                        const element = elements[index];
                        if (!isVisible(element)) {
                            continue;
                        }
                        const text = normalize(element.innerText || element.textContent);
                        const ariaLabel = normalize(element.getAttribute('aria-label'));
                        const title = normalize(element.getAttribute('title'));
                        const value = normalize(element.getAttribute('value'));
                        const name = normalize(element.getAttribute('name'));
                        const id = normalize(element.getAttribute('id'));
                        const dataAction = normalize(element.getAttribute('data-action'));
                        if (!text.includes(expected)
                                && !ariaLabel.includes(expected)
                                && !title.includes(expected)
                                && !value.includes(expected)
                                && !name.includes(expected)
                                && !id.includes(expected)
                                && !dataAction.includes(expected)) {
                            continue;
                        }
                        element.scrollIntoView({ block: 'center' });
                        element.click();
                        return true;
                    }
                    return false;
                }
                """, expected);
        return Boolean.TRUE.equals(clicked);
    }

    private boolean clickMatchingPageButton(String buttonText) {
        String expected = buttonText.trim().toUpperCase();
        Object clicked = page.evaluate("""
                expected => {
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
                    const elements = Array.from(document.querySelectorAll(
                        "button, [role='button'], input[type='button'], input[type='submit'], a"));
                    for (let index = elements.length - 1; index >= 0; index--) {
                        const element = elements[index];
                        if (!isVisible(element)) {
                            continue;
                        }
                        const text = normalize(element.innerText || element.textContent);
                        const ariaLabel = normalize(element.getAttribute('aria-label'));
                        const title = normalize(element.getAttribute('title'));
                        const value = normalize(element.getAttribute('value'));
                        const name = normalize(element.getAttribute('name'));
                        const id = normalize(element.getAttribute('id'));
                        const dataAction = normalize(element.getAttribute('data-action'));
                        if (!text.includes(expected)
                                && !ariaLabel.includes(expected)
                                && !title.includes(expected)
                                && !value.includes(expected)
                                && !name.includes(expected)
                                && !id.includes(expected)
                                && !dataAction.includes(expected)) {
                            continue;
                        }
                        element.scrollIntoView({ block: 'center' });
                        element.click();
                        return true;
                    }
                    return false;
                }
                """, expected);
        return Boolean.TRUE.equals(clicked);
    }

    private void clickCascCloneButtonIfPresent(Locator cascRow) {
        Locator visibleRow = firstVisible(cascRow);
        if (visibleRow == null) {
            return;
        }

        Locator buttons = visibleRow.locator("button, [role='button'], input[type='button'], input[type='submit'], a");
        if (clickMatchingButton(buttons, "CLONE") || clickMatchingButton(buttons, "COPY")) {
            return;
        }

        int count = buttons.count();
        for (int index = 0; index < count; index++) {
            Locator button = buttons.nth(index);
            if (!button.isVisible()) {
                continue;
            }

            String text = normalize(button.innerText()).toUpperCase();
            String ariaLabel = normalize(button.getAttribute("aria-label")).toUpperCase();
            String title = normalize(button.getAttribute("title")).toUpperCase();
            String value = normalize(button.getAttribute("value")).toUpperCase();
            String className = normalize(button.getAttribute("class")).toUpperCase();
            boolean deleteButton = text.contains("DELETE")
                    || ariaLabel.contains("DELETE")
                    || title.contains("DELETE")
                    || value.contains("DELETE")
                    || className.contains("TRASH")
                    || className.contains("DELETE");
            boolean additionalButton = text.contains("ADDITIONAL CASC")
                    || text.contains("CLOSE")
                    || ariaLabel.contains("ADDITIONAL")
                    || title.contains("ADDITIONAL");
            if (deleteButton || additionalButton) {
                continue;
            }

            button.scrollIntoViewIfNeeded();
            button.click(new Locator.ClickOptions().setForce(true));
            return;
        }
    }

    protected boolean isMissingOrEmpty(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
                || (node.isTextual() && normalize(node.asText()).isBlank())
                || (node.isArray() && node.isEmpty())
                || (node.isObject() && node.isEmpty());
    }

    private void captureAdditionalCascFailureArtifacts(String suffix) {
        if (page == null) {
            return;
        }

        try {
            page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(Paths.get("target", "additional-casc-" + suffix + ".png")));
        } catch (PlaywrightException ignored) {
        }
    }

    protected void setCheckboxByLabel(String label, boolean checked) {
        Locator visibleCheckbox = resolveCheckboxByLabel(label);
        if (visibleCheckbox == null) {
            return;
        }

        visibleCheckbox.scrollIntoViewIfNeeded();
        boolean selected = isCheckboxSelected(visibleCheckbox);
        if (selected != checked) {
            clickCheckboxTarget(label, visibleCheckbox);
        }
        if (isCheckboxSelected(visibleCheckbox) != checked) {
            clickContainerByLabelIfPresent(label);
        }
        if (isCheckboxSelected(visibleCheckbox) != checked) {
            forceCheckboxValue(checked, label);
        }
    }

    protected void setCheckboxByLabelIfDifferent(String label, boolean checked) {
        Locator visibleCheckbox = resolveCheckboxByLabel(label);
        if (visibleCheckbox == null) {
            return;
        }

        boolean selected = isCheckboxSelected(visibleCheckbox);
        if (selected == checked) {
            return;
        }

        setCheckboxByLabel(label, checked);
        pauseUi(UI_ACTION_PAUSE_MS);
    }

    private void setDeclarationIndicatorInSummary(boolean checked) {
        Locator declarationSummarySection = resolveSection("Declaration Summary");
        Locator checkbox = firstVisible(declarationSummarySection.locator("input[type='checkbox'], [role='checkbox']"));
        if (checkbox == null) {
            throw new IllegalStateException("Declaration indicator checkbox was not visible in Declaration Summary.");
        }

        checkbox.scrollIntoViewIfNeeded();
        if (isCheckboxSelected(checkbox) != checked) {
            checkbox.click(new Locator.ClickOptions().setForce(true));
        }
        if (isCheckboxSelected(checkbox) != checked) {
            clickContainerByLabelIfPresent("I/We declare that all particulars in this application are true and correct.");
        }
        if (isCheckboxSelected(checkbox) != checked) {
            forceCheckboxValue(checked,
                    "I/We declare that all particulars in this application are true and correct.",
                    "Declaration Indicator");
        }
        if (isCheckboxSelected(checkbox) != checked) {
            throw new IllegalStateException("Declaration indicator checkbox did not reach expected state: " + checked);
        }
    }

    private boolean isCheckboxSelected(Locator checkbox) {
        return "true".equalsIgnoreCase(normalize(checkbox.getAttribute("aria-checked")))
                || Boolean.TRUE.equals(checkbox.evaluate("element => element.checked === true"));
    }

    protected void forceCheckboxValue(boolean checked, String... labelHints) {
        try {
            page.evaluate("""
                    args => {
                        const normalize = input => (input || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const hints = (args.labelHints || []).map(normalize).filter(Boolean);

                        const resolveCheckboxFromElement = element => {
                            if (!element) {
                                return null;
                            }

                            if (element.tagName === 'LABEL') {
                                if (element.control && element.control.type === 'checkbox') {
                                    return element.control;
                                }
                                const htmlFor = element.getAttribute('for');
                                if (htmlFor) {
                                    const associated = document.getElementById(htmlFor);
                                    if (associated && associated.type === 'checkbox') {
                                        return associated;
                                    }
                                }
                            }

                            return element.querySelector?.('input[type="checkbox"], [role="checkbox"]')
                                || element.closest?.('clr-checkbox-wrapper, label, div, section, form')?.querySelector?.('input[type="checkbox"], [role="checkbox"]')
                                || null;
                        };

                        const visibleLabels = Array.from(document.querySelectorAll('label')).filter(isVisible);
                        for (const hint of hints) {
                            const exactLabel = visibleLabels.find(element => normalize(element.innerText || element.textContent) === hint);
                            const exactCheckbox = resolveCheckboxFromElement(exactLabel);
                            if (exactCheckbox) {
                                exactCheckbox.checked = !!args.checked;
                                exactCheckbox.dispatchEvent(new Event('input', { bubbles: true }));
                                exactCheckbox.dispatchEvent(new Event('change', { bubbles: true }));
                                exactCheckbox.dispatchEvent(new Event('blur', { bubbles: true }));
                                return true;
                            }
                        }

                        const label = Array.from(document.querySelectorAll('label, span, div, p'))
                            .filter(isVisible)
                            .find(element => {
                                const text = normalize(element.innerText || element.textContent);
                                return hints.some(hint => text === hint || text.includes(hint));
                            });
                        const checkbox = resolveCheckboxFromElement(label)
                            || document.querySelector('input[type="checkbox"][formcontrolname="declarationIndicator"]');
                        if (!checkbox) {
                            return false;
                        }

                        checkbox.checked = !!args.checked;
                        checkbox.dispatchEvent(new Event('input', { bubbles: true }));
                        checkbox.dispatchEvent(new Event('change', { bubbles: true }));
                        checkbox.dispatchEvent(new Event('blur', { bubbles: true }));
                        return true;
                    }
                    """, java.util.Map.of(
                    "checked", checked,
                    "labelHints", labelHints));
        } catch (Exception ignored) {
        }
    }

    private Locator resolveCheckboxByLabel(String label) {
        waitForFormControls();
        Locator checkboxViaExplicitLabel = resolveCheckboxViaExplicitLabel(label);
        if (checkboxViaExplicitLabel != null) {
            return checkboxViaExplicitLabel;
        }

        String escapedLabel = toXpathLiteral(label);
        Locator nestedCheckbox = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])])[1]"
                        + "//*[self::input[@type='checkbox'] or @role='checkbox']");
        Locator visibleNestedCheckbox = firstVisible(nestedCheckbox);
        if (visibleNestedCheckbox != null) {
            return visibleNestedCheckbox;
        }

        Locator precedingCheckbox = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])])[1]"
                        + "/preceding::*[self::input[@type='checkbox'] or @role='checkbox'][1]");
        Locator visiblePrecedingCheckbox = firstVisible(precedingCheckbox);
        if (visiblePrecedingCheckbox != null) {
            return visiblePrecedingCheckbox;
        }

        Locator checkboxInNearestContainer = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])])[1]"
                        + "/ancestor::*[.//input[@type='checkbox'] or .//*[@role='checkbox']][1]"
                        + "//*[self::input[@type='checkbox'] or @role='checkbox']");
        Locator visibleCheckboxInNearestContainer = firstVisible(checkboxInNearestContainer);
        if (visibleCheckboxInNearestContainer != null) {
            return visibleCheckboxInNearestContainer;
        }

        Locator followingCheckbox = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])])[1]"
                        + "/following::*[self::input[@type='checkbox'] or @role='checkbox'][1]");
        return firstVisible(followingCheckbox);
    }

    private Locator resolveCheckboxViaExplicitLabel(String label) {
        Locator labelElement = resolveExactLabelElement(label);
        if (labelElement == null) {
            return null;
        }

        String associatedControlId = normalize(labelElement.getAttribute("for"));
        if (!associatedControlId.isBlank()) {
            Locator associatedCheckbox = firstVisible(page.locator("#" + escapeCssIdentifier(associatedControlId)));
            if (associatedCheckbox != null) {
                return associatedCheckbox;
            }
        }

        Locator nestedCheckbox = firstVisible(labelElement.locator("input[type='checkbox'], [role='checkbox']"));
        if (nestedCheckbox != null) {
            return nestedCheckbox;
        }

        Locator containerCheckbox = firstVisible(labelElement.locator(
                "xpath=ancestor::*[.//input[@type='checkbox'] or .//*[@role='checkbox']][1]"
                        + "//*[self::input[@type='checkbox'] or @role='checkbox']"));
        if (containerCheckbox != null) {
            return containerCheckbox;
        }

        return null;
    }

    private Locator resolveExactLabelElement(String label) {
        String escapedLabel = toXpathLiteral(label);
        return firstVisible(page.locator(
                "xpath=(//label[normalize-space(translate(., '*', ''))=" + escapedLabel + "])[1]"));
    }

    private void clickCheckboxTarget(String label, Locator checkbox) {
        Locator labelElement = resolveExactLabelElement(label);
        if (labelElement != null) {
            try {
                labelElement.scrollIntoViewIfNeeded();
                labelElement.click(new Locator.ClickOptions().setForce(true));
                return;
            } catch (PlaywrightException ignored) {
            }
        }

        checkbox.click(new Locator.ClickOptions().setForce(true));
    }

    private void clickContainerByLabelIfPresent(String label) {
        Locator labelElement = resolveExactLabelElement(label);
        if (labelElement != null) {
            try {
                labelElement.scrollIntoViewIfNeeded();
                labelElement.click(new Locator.ClickOptions().setForce(true));
                return;
            } catch (PlaywrightException ignored) {
            }
        }

        String escapedLabel = toXpathLiteral(label);
        Locator containers = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + escapedLabel + ")])])[1]"
                        + "/ancestor::*[.//input[@type='checkbox'] or .//*[@role='checkbox']][1]");
        Locator visibleContainer = firstVisible(containers);
        if (visibleContainer == null) {
            return;
        }

        try {
            visibleContainer.scrollIntoViewIfNeeded();
            visibleContainer.click(new Locator.ClickOptions().setForce(true));
        } catch (PlaywrightException ignored) {
        }
    }

    private boolean isFieldInsideText(Locator field, String... texts) {
        try {
            String containerText = String.valueOf(field.evaluate("""
                    (element, expectedTexts) => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const container = element.closest('div, td, tr, section, form, mat-card') || element.parentElement;
                        const text = normalize(container?.innerText || container?.textContent);
                        return JSON.stringify({
                            text,
                            matched: expectedTexts.some(expected => text.includes(normalize(expected)))
                        });
                    }
                    """, texts));
            return containerText.contains("\"matched\":true");
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void waitForFormControls() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForFunction("""
                    () => {
                        const isVisible = element => !!element
                            && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        return Array.from(document.querySelectorAll(
                            "input:not([type='hidden']), textarea, select, [role='combobox'], [role='textbox'], button"))
                            .some(element => isVisible(element) && !element.disabled);
                    }
                    """);
        } catch (PlaywrightException ignored) {
        }
        page.waitForTimeout(300);
    }

    private void closeTransientOverlays() {
        try {
            page.keyboard().press("Escape");
        } catch (PlaywrightException ignored) {
        }

        try {
            page.waitForTimeout(150);
        } catch (PlaywrightException ignored) {
        }
    }

    private void waitForAnyVisibleText(String... texts) {
        try {
            page.waitForFunction("""
                    expectedTexts => {
                        const bodyText = (document.body?.innerText || '').replace(/\\s+/g, ' ').trim();
                        return expectedTexts.some(text => bodyText.includes(text));
                    }
                    """, texts);
        } catch (PlaywrightException ignored) {
        }
    }


    protected String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.path(fieldName);
            if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                String value = valueNode.asText().trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    protected JsonNode firstArrayItem(JsonNode node) {
        if (node != null && node.isArray() && node.size() > 0) {
            return node.get(0);
        }
        return MissingNode.getInstance();
    }

    private Boolean readOptionalJsonBoolean(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            String value = node.asText().trim();
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return null;
    }

    protected String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    protected JsonNode firstNonBlankNode(JsonNode first, JsonNode second) {
        if (first != null && !first.isMissingNode() && !first.isNull() && !first.asText("").isBlank()) {
            return first;
        }
        return second;
    }

    private String[] compactValues(String... values) {
        List<String> compact = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                compact.add(value.trim());
            }
        }
        return compact.toArray(String[]::new);
    }

    private String[] bgIndicatorHints(String bgIndicator) {
        String normalized = normalize(bgIndicator).toUpperCase();
        return switch (normalized) {
            case "I" -> compactValues(
                    bgIndicator,
                    "I - From Importer's BG",
                    "From Importer's BG",
                    "Importer's BG",
                    "Importer");
            case "D" -> compactValues(
                    bgIndicator,
                    "D - From Declaring Agent's BG",
                    "D - From Declarant's BG",
                    "From Declaring Agent's BG",
                    "From Declarant's BG",
                    "Declaring Agent's BG",
                    "Declarant's BG",
                    "Agent's BG",
                    "Declarant",
                    "Declaring Agent");
            default -> compactValues(bgIndicator);
        };
    }

    protected String formatUiDate(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String normalized = value.trim();
        String digitsOnly = normalized.replaceAll("\\D", "");
        if (digitsOnly.length() != 8) {
            return normalized;
        }

        LocalDate parsedDate = tryParseDate(digitsOnly);
        return parsedDate == null ? normalized : parsedDate.format(UI_DATE_FORMAT);
    }

    private String toHtmlDateValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        LocalDate parsedDate = parseFlexibleDateValue(value);
        return parsedDate == null ? "" : parsedDate.toString();
    }

    protected String normalizeNumericForEntry(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private LocalDate tryParseDate(String digitsOnly) {
        if (digitsOnly.matches("(19|20)\\d{6}")) {
            LocalDate yearFirstDate = parseDate(digitsOnly, SOURCE_YYYYMMDD_FORMAT);
            if (yearFirstDate != null) {
                return yearFirstDate;
            }
        }

        LocalDate dayFirstDate = parseDate(digitsOnly, SOURCE_DDMMYYYY_FORMAT);
        if (dayFirstDate != null) {
            return dayFirstDate;
        }

        return parseDate(digitsOnly, SOURCE_YYYYMMDD_FORMAT);
    }

    private LocalDate parseDate(String value, DateTimeFormatter formatter) {
        try {
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private void appendCandidate(List<String> candidates, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isBlank()) {
            candidates.add(normalized);
            LocalDate parsedDate = parseFlexibleDateValue(normalized);
            if (parsedDate != null) {
                candidates.add(parsedDate.format(UI_DATE_FORMAT));
                candidates.add(parsedDate.format(UI_SLASH_DATE_FORMAT));
                candidates.add(parsedDate.toString());
            }
        }
    }

    private LocalDate parseFlexibleDateValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        String separatorNormalized = normalized.replace('/', '-').replace('.', '-');
        LocalDate parsedUiDate = parseDate(separatorNormalized, UI_DATE_FORMAT);
        if (parsedUiDate != null) {
            return parsedUiDate;
        }

        LocalDate parsedIsoDate = parseDate(separatorNormalized, DateTimeFormatter.ISO_LOCAL_DATE);
        if (parsedIsoDate != null) {
            return parsedIsoDate;
        }

        String digitsOnly = normalized.replaceAll("\\D", "");
        if (digitsOnly.length() == 8) {
            return tryParseDate(digitsOnly);
        }

        return null;
    }

    private void pauseUi(int timeoutMs) {
        if (timeoutMs > 0) {
            page.waitForTimeout(timeoutMs);
        }
    }

    protected String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeCommaInsensitive(String value) {
        return normalize(value == null ? "" : value.replace(',', ' '));
    }

    private String escapeForSelector(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String escapeCssIdentifier(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace(".", "\\.")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("#", "\\#");
    }

    private String toXpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        String[] parts = value.split("'");
        StringBuilder builder = new StringBuilder("concat(");
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                builder.append(", \"'\", ");
            }
            builder.append("'").append(parts[index]).append("'");
        }
        builder.append(")");
        return builder.toString();
    }
}
