package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;

import java.math.BigDecimal;
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
import java.util.Set;

public class IptDeclarationPage {

    private static final DateTimeFormatter UI_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);
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
    private static final int UI_SUBMIT_CLICK_WAIT_MS = 2000;
    private static final int UI_POST_SUBMIT_WAIT_MS = 3000;

    private final Page page;

    public IptDeclarationPage(Page page) {
        this.page = page;
    }

    public void populateFrom(JsonNode data) {
        fillSectionAndAdvance("Shipment Info (S)", () -> fillShipmentInfo(data));
        fillSectionAndAdvance("Transport Info (T)", () -> fillTransportInfo(data));
        fillSectionAndAdvance("Party Info (P)", () -> fillPartyInfo(data));
        fillSectionAndAdvance("Invoice Info (V)", () -> fillInvoiceInfo(data));
        fillSectionAndAdvance("Items (I)", () -> fillItemInfo(data));
        fillSummary(data);
    }

    public void openInvoiceInfoSection() {
        openSection("Invoice Info (V)");
    }

    public String readSupplierManufacturerNameValue() {
        return normalize(readRenderedFieldValue(resolveSupplierManufacturerNameField()));
    }

    public String captureSubmitValidationDiagnostics() {
        try {
            return String.valueOf(page.evaluate("""
                    () => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const describeElement = element => {
                            if (!element) {
                                return '';
                            }

                            const ownText = normalize(element.innerText || element.textContent);
                            const label = normalize(
                                element.closest('div, td, tr, section, form')?.querySelector('label, .form-label, span, p, div')
                                    ?.textContent);
                            const placeholder = normalize(element.getAttribute('placeholder'));
                            const name = normalize(element.getAttribute('name'));
                            const formControlName = normalize(element.getAttribute('formcontrolname'));
                            const id = normalize(element.getAttribute('id'));
                            const type = normalize(element.getAttribute('type'));
                            return JSON.stringify({
                                label,
                                text: ownText,
                                placeholder,
                                name,
                                formControlName,
                                id,
                                type,
                                ariaInvalid: normalize(element.getAttribute('aria-invalid')),
                                classes: normalize(element.getAttribute('class'))
                            });
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

                        const toastText = Array.from(document.querySelectorAll('*'))
                            .map(element => normalize(element.innerText || element.textContent))
                            .find(text => text.includes('Validation Failed')) || '';

                        return JSON.stringify({
                            toastText,
                            invalidElements: invalidElements.map(describeElement)
                        }, null, 2);
                    }
                    """));
        } catch (PlaywrightException exception) {
            return "Unable to capture submit validation diagnostics: " + exception.getMessage();
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
        fillDeclarationType(declarationType);

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
        fillLicense(text(license, "referenceID"));
    }

    private void fillTransportInfo(JsonNode data) {
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
        waitForAnyVisibleText("Inward Flight Number", "Flight Number", "Inward Master Air Waybill", "Master Air Waybill");
        fillOneOfLabelsIfPresent(text(transportMode, "conveyanceReferenceNumber"), "Inward Flight Number", "Flight Number");
        fillOneOfLabelsIfPresent(text(transportMeans, "mawboucroblNumber"), "Inward Master Air Waybill", "Master Air Waybill");
        fillDateFieldInSection("Inward Transport Means", "Arrival Date", formatUiDate(text(inwardTransport, "arrivalDate")));
        fillLookupFieldIfPresent(
                "Loading Port",
                text(inwardTransport, "loadingPort"),
                text(inwardTransport, "loadingPort"));
    }

    private void fillPartyInfo(JsonNode data) {
        JsonNode party = data.path("party");
        fillPartyRow("Importer", party.path("importerParty"));
        fillPartyRow("Inward Carrier", party.path("inwardCarrierAgentParty"));
        fillPartyRow("Freight Forwarder", party.path("freightForwarderParty"));
    }

    private void fillPartyRow(String rowLabel, JsonNode partyNode) {
        String partyName = normalize(text(partyNode.path("partyName"), "name"));
        if (partyName == null || partyName.isBlank()) {
            return;
        }

        Locator field = resolvePartyNameField(rowLabel);
        field.waitFor(new Locator.WaitForOptions().setTimeout(5000));

        field.scrollIntoViewIfNeeded();
        field.click(new Locator.ClickOptions().setForce(true));
        String[] selectionHints = partySelectionHints(partyName);
        String[] searchCandidates = partySearchCandidates(partyName);

        boolean matched = false;
        for (String searchCandidate : searchCandidates) {
            clearAndTypePartyField(field, searchCandidate);
            attemptPartySuggestionSelection(selectionHints);
            try {
                page.keyboard().press("Tab");
            } catch (PlaywrightException ignored) {
            }
            pauseUi(UI_NEXT_FIELD_PAUSE_MS);
            if (waitForPartyFieldValue(field, partyName, 2500)
                    || waitForCommittedPartySelection(field, 2500)
                    || waitForPartyRowValues(rowLabel, partyName, 1500)) {
                matched = true;
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

    private boolean waitForPartyFieldValue(Locator field, String expectedName, int timeoutMs) {
        List<String> expectedValues = new ArrayList<>();
        appendCandidate(expectedValues, expectedName);
        appendCandidate(expectedValues, deduplicateRepeatedPartyName(expectedName));
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
        long deadline = System.currentTimeMillis() + timeoutMs;
        String normalizedExpectedName = normalize(expectedName);
        while (System.currentTimeMillis() <= deadline) {
            String rowText = readPartyRowText(rowLabel);
            boolean nameMatches = normalizedExpectedName.isBlank()
                    || rowText.equalsIgnoreCase(normalizedExpectedName)
                    || rowText.contains(normalizedExpectedName)
                    || normalizedExpectedName.contains(rowText);
            if (nameMatches) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private void fillInvoiceInfo(JsonNode data) {
        JsonNode invoice = firstArrayItem(data.path("invoice"));
        String supplierManufacturerName = text(invoice.path("supplierManufacturerParty"), "name");
        fillField("Invoice Number", text(invoice, "invoiceNumber"));
        fillDateField("Invoice Date", formatUiDate(text(invoice, "invoiceDate")));
        fillLookupField("Term Type", text(invoice, "unitPriceTermType"), "FOB");
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

    private void fillItemInfo(JsonNode data) {
        JsonNode invoice = firstArrayItem(data.path("invoice"));
        JsonNode item = firstArrayItem(data.path("item"));
        JsonNode cascProduct = firstArrayItem(item.path("cascProduct"));

        fillLookupFieldIfPresent(
                "Invoice Number",
                firstNonBlank(text(item, "itemInvoiceNumber"), text(invoice, "invoiceNumber")),
                firstNonBlank(text(item, "itemInvoiceNumber"), text(invoice, "invoiceNumber")));
        fillField("Inward HAWB", text(item, "inHawbHucrHblNumber"));
        fillLookupField("HS Code", text(item, "itemHarmonizedSystemCode"), text(item, "itemHarmonizedSystemCode"));
        fillFieldIfPresent("Goods Description", text(item, "goodsDescription"));
        fillField("Brand", text(item, "brandName"));
        fillLookupFieldIfPresent("COO", text(item, "originCountry"), text(item, "originCountry"));
        fillField("HS Quantity", text(item.path("itemQuantity").path("hsQuantity"), "value"));
        fillField("Item Unit Value", normalizeNumericForEntry(
                text(item.path("transactionValue").path("unitPriceValue").path("amount"), "value")));

        fillCascDetails(cascProduct);
    }

    private void fillCascDetails(JsonNode cascProduct) {
        if (isMissingOrEmpty(cascProduct)) {
            return;
        }

        Locator cascSection = resolveSection("CASC Details");
        Locator cascRow = resolvePrimaryCascRow(cascSection);

        String productCode = text(cascProduct, "cascProductCode");
        if (productCode != null && !productCode.isBlank()) {
            Locator productCodeField = resolveVisibleEditableFieldInRow(cascRow, "CASC Product", 0);
            focusAndType(productCodeField, productCode, true, productCode);
        }

        JsonNode cascQuantity = cascProduct.path("cascProductQuantity");
        String quantityValue = text(cascQuantity, "value");
        if (quantityValue != null && !quantityValue.isBlank()) {
            Locator quantityField = resolveVisibleEditableFieldInRow(cascRow, "CASC Product", 1);
            focusAndType(quantityField, quantityValue, false);
        }

        String quantityUom = text(cascQuantity, "unitCode");
        if (quantityUom != null && !quantityUom.isBlank()) {
            Locator uomField = resolveVisibleEditableFieldInRow(cascRow, "CASC Product", 2);
            focusAndType(uomField, quantityUom, true, quantityUom);
        }

        clickCascCloneButtonIfPresent(cascRow);

        JsonNode additionalCascIdentification = firstArrayItem(cascProduct.path("additionalCascIdentification"));
        if (isMissingOrEmpty(additionalCascIdentification)) {
            return;
        }

        clickButtonInScope(cascRow, "ADDITIONAL CASC", "ADDITIONAL", "ADDITIONAL CA");
        waitForAdditionalCascSection(cascSection, cascRow, 5000);
        clickAdditionalCascAddButton(cascSection);
        pauseUi(UI_ACTION_PAUSE_MS);

        String cascCodeOne = text(additionalCascIdentification, "cascCodeOne");
        if (cascCodeOne != null && !cascCodeOne.isBlank()) {
            Locator additionalCascRow = waitForAdditionalCascEntryRow(cascSection, 5000);
            fillAdditionalCascCodeOne(additionalCascRow, cascCodeOne);
        }
    }

    private void fillAdditionalCascCodeOne(Locator additionalCascRow, String value) {
        Locator editableField = resolveFirstVisibleEditableFieldInScopeOrNull(additionalCascRow);
        if (editableField != null) {
            focusAndType(editableField, value, false);
            return;
        }

        Locator activator = resolveAdditionalCascCodeOneActivatorOrNull(additionalCascRow);
        if (activator != null) {
            closeTransientOverlays();
            activator.scrollIntoViewIfNeeded();
            activator.click(new Locator.ClickOptions().setForce(true));
            pauseUi(UI_ACTION_PAUSE_MS);

            Locator activatedField = resolveFirstVisibleEditableFieldInScopeOrNull(additionalCascRow);
            if (activatedField != null) {
                focusAndType(activatedField, value, false);
                return;
            }

            page.keyboard().press("Control+A");
            page.keyboard().press("Backspace");
            page.keyboard().type(value);
            page.keyboard().press("Tab");
            pauseUi(UI_NEXT_FIELD_PAUSE_MS);
            return;
        }

        captureAdditionalCascFailureArtifacts("code-one-field-not-visible");
        throw new IllegalStateException("Additional CASC Code 1 field was not visible.");
    }

    private void fillSummary(JsonNode data) {
        openSection("Summary (Y)");
        if (data.path("header").path("declarationIndicator").asBoolean(false)) {
            setCheckboxByLabel("Declaration Indicator", true);
        }
        saveDraft();
        if (shouldSubmitDeclaration(data)) {
            clickSubmitDeclarationWithDelay(UI_SUBMIT_CLICK_WAIT_MS);
        }
    }

    private boolean shouldSubmitDeclaration(JsonNode data) {
        return data.path("summary").path("submitDeclaration").asBoolean(false)
                || data.path("formMetaData").path("submitDeclaration").asBoolean(false);
    }

    private void fillDeclarationType(String declarationType) {
        if (declarationType == null || declarationType.isBlank()) {
            return;
        }

        Locator field = resolveFieldByLabelInSection("Declaration Info", "Declaration Type", 0);
        if ("10".equals(normalize(declarationType))) {
            openLookupAndChooseOption(
                    field,
                    "10 GST",
                    "GST (including Duty Exemption)",
                    "10",
                    "GST");
            return;
        }

        focusAndType(field, declarationType, true, declarationType);
    }

    private void fillSectionAndAdvance(String sectionName, Runnable filler) {
        openSection(sectionName);
        filler.run();
        saveDraft();
        goToNextSection();
    }

    private void fillLicense(String licenseValue) {
        if (licenseValue == null || licenseValue.isBlank()) {
            return;
        }
        Set<Integer> visibleFieldIndexesBeforeOpening = captureVisibleTextEntryIndexes();
        setCheckboxByLabel("License", true);
        clickContainerByLabelIfPresent("License");
        page.waitForTimeout(300);

        Locator licenseField = resolveLicenseFieldOrNull(visibleFieldIndexesBeforeOpening);
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

    private Locator resolveLicenseFieldOrNull(Set<Integer> visibleFieldIndexesBeforeOpening) {
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

    private void fillField(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabel(label, 0);
        focusAndType(field, value, false);
    }

    private void fillFieldIfPresent(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelOrNull(label, 0);
        if (field == null) {
            return;
        }
        focusAndType(field, value, false);
    }

    private void fillDateField(String label, String value) {
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

    private void fillSupplierManufacturerName(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveSupplierManufacturerNameField();
        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        field.click(new Locator.ClickOptions().setForce(true));
        field.fill(value);
        ensureTextFieldValue(field, value);
        field.press("Tab");
        pauseUi(UI_NEXT_FIELD_PAUSE_MS);
        if (!waitForAnyRenderedFieldValue(field, 1500, value)) {
            throw new IllegalStateException("Supplier / Manufacturer Name was not rendered. Expected: "
                    + value + ", Actual: " + readRenderedFieldValue(field));
        }
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

    private void fillLookupField(String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabel(label, 0);
        focusAndType(field, value, true, suggestionHints);
    }

    private void fillFieldInSection(String sectionTitle, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelInSection(sectionTitle, label, 0);
        focusAndType(field, value, false);
    }

    private void fillDateFieldInSection(String sectionTitle, String label, String value) {
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

    private void fillLookupFieldInSection(String sectionTitle, String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelInSection(sectionTitle, label, 0);
        focusAndType(field, value, true, suggestionHints);
    }

    private void fillNthLookupFieldInSection(String sectionTitle, int occurrence, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthFieldInSection(sectionTitle, occurrence);
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

    private void fillFirstFieldInSection(String sectionTitle, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveNthFieldInSection(sectionTitle, 0);
        focusAndType(field, value, false);
    }

    private void fillLookupFieldInSectionIfPresent(String sectionTitle, String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
        if (field == null) {
            return;
        }
        focusAndType(field, value, true, suggestionHints);
    }

    private void fillLookupFieldIfPresent(String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabelOrNull(label, 0);
        if (field == null) {
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

    private void fillNthLookupField(String label, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveFieldByLabel(label, occurrence);
        focusAndType(field, value, true, value);
    }

    private void fillLookupFieldInRow(String rowLabel, String value, String... suggestionHints) {
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

    private void fillFieldInRowByIndexIfPresent(String rowLabel, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldInRowByContainsOrNull(rowLabel, occurrence);
        if (field == null) {
            return;
        }
        focusAndType(field, value, false);
    }

    private void fillLookupFieldInRowByIndex(String rowLabel, int occurrence, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveEditableFieldInRowByContains(rowLabel, occurrence);
        focusAndType(field, value, true, suggestionHints.length == 0 ? new String[] { value } : suggestionHints);
    }

    private void fillFieldInChargeRow(String rowLabel, String columnLabel, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveInvoiceChargeField(rowLabel, columnLabel);
        focusAndType(field, value, false);
    }

    private void fillLookupFieldInChargeRow(String rowLabel, String columnLabel, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        Locator field = resolveInvoiceChargeField(rowLabel, columnLabel);
        focusAndType(field, value, true, suggestionHints.length == 0 ? new String[] { value } : suggestionHints);
    }

    private void focusAndType(Locator field, String value, boolean selectSuggestion) {
        focusAndType(field, value, selectSuggestion, value);
    }

    private void focusAndType(Locator field, String value, boolean selectSuggestion, String... suggestionHints) {
        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();

        if (trySelectNativeDropdown(field, value, suggestionHints)) {
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
        }
        page.keyboard().press("Tab");
        pauseUi(UI_NEXT_FIELD_PAUSE_MS);
    }

    private void openLookupAndChooseOption(Locator field, String... optionHints) {
        closeTransientOverlays();
        field.scrollIntoViewIfNeeded();
        field.click(new Locator.ClickOptions().setForce(true));
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
            Boolean matched = (Boolean) field.evaluate("""
                    (element, expectedValues) => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const expected = expectedValues.map(normalize).filter(Boolean);
                        const options = Array.from(element.options || []);
                        const option = options.find(candidate => {
                            const optionText = normalize(candidate.textContent);
                            const optionValue = normalize(candidate.value);
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
            return Boolean.TRUE.equals(matched);
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void ensureLookupValue(Locator field, String value) {
        try {
            String currentValue = normalize(inputValueOrEmpty(field));
            if (!currentValue.isBlank()) {
                return;
            }
            field.evaluate("""
                    (element, newValue) => {
                        element.value = newValue;
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
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

    private String readRenderedFieldValue(Locator field) {
        try {
            return String.valueOf(field.evaluate("""
                    element => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                        const directValue = normalize(element.value);
                        if (directValue) {
                            return directValue;
                        }

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

                        return normalize(element.innerText || element.textContent);
                    }
                    """));
        } catch (PlaywrightException ignored) {
            return inputValueOrEmpty(field);
        }
    }

    private void ensureTextFieldValue(Locator field, String expectedValue) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return;
        }

        try {
            String currentValue = normalize(inputValueOrEmpty(field));
            String normalizedExpected = normalize(expectedValue);
            if (!currentValue.isBlank()
                    && (currentValue.equalsIgnoreCase(normalizedExpected)
                    || currentValue.contains(normalizedExpected)
                    || normalizedExpected.contains(currentValue))) {
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
            throw new IllegalStateException("Date value was not rendered. Expected: " + expectedValue
                    + ", Actual: " + readRenderedFieldValue(field));
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
            throw new IllegalStateException("Native date value was not rendered. Expected: " + expectedUiValue
                    + " / " + htmlDateValue + ", Actual: " + readRenderedFieldValue(field));
        }
    }

    private boolean waitForAnyRenderedFieldValue(Locator field, int timeoutMs, String... expectedValues) {
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
                    String normalizedExpected = normalize(candidate);
                    if (currentValue.equalsIgnoreCase(normalizedExpected)
                            || currentValue.contains(normalizedExpected)
                            || normalizedExpected.contains(currentValue)) {
                        return true;
                    }
                }
            }
            page.waitForTimeout(100);
        }
        return false;
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
        page.waitForTimeout(300);
        field.click(new Locator.ClickOptions().setForce(true));
        try {
            field.fill("");
        } catch (Exception ignored) {
            page.keyboard().press("Control+A");
            page.keyboard().press("Backspace");
        }
        field.type(value, new Locator.TypeOptions().setDelay(100));
        page.waitForTimeout(500);
    }

    private void attemptPartySuggestionSelection(String... selectionHints) {
        if (waitForVisibleSuggestion(2000, selectionHints) && clickVisibleSuggestion(selectionHints)) {
            page.waitForTimeout(1000);
            return;
        }

        if (waitForAnyVisibleSuggestion(2500)
                && (clickVisibleSuggestion(selectionHints) || clickFirstVisibleSuggestion())) {
            page.waitForTimeout(1000);
            return;
        }

        try {
            page.keyboard().press("ArrowDown");
            page.waitForTimeout(300);
            if (waitForAnyVisibleSuggestion(1500)
                    && (clickVisibleSuggestion(selectionHints) || clickFirstVisibleSuggestion())) {
                page.waitForTimeout(1000);
                return;
            }
            page.keyboard().press("Enter");
        } catch (PlaywrightException ignored) {
        }

        page.waitForTimeout(1000);
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
        appendCandidate(hints, deduplicateRepeatedPartyName(partyName));

        String normalized = normalize(partyName);
        if (!normalized.isBlank()) {
            String[] words = normalized.split(" ");
            if (words.length >= 2) {
                appendCandidate(hints, String.join(" ", words[0], words[1]));
            }
            if (words.length >= 3) {
                appendCandidate(hints, String.join(" ", words[0], words[1], words[2]));
            }
            if (words.length >= 4) {
                appendCandidate(hints, String.join(" ", words[0], words[1], words[2], words[3]));
            }
        }

        return hints.toArray(String[]::new);
    }

    private String[] partySearchCandidates(String partyName) {
        List<String> candidates = new ArrayList<>();
        appendCandidate(candidates, partyName);
        appendCandidate(candidates, deduplicateRepeatedPartyName(partyName));

        String normalized = normalize(partyName);
        if (!normalized.isBlank()) {
            String[] words = normalized.split(" ");
            String condensed = normalized.replaceAll("[^A-Za-z0-9]", "");
            appendCandidate(candidates, condensed);
            if (words.length >= 1) {
                appendCandidate(candidates, words[0]);
            }
            if (words.length >= 4) {
                appendCandidate(candidates, String.join(" ", words[0], words[1], words[2], words[3]));
            }
            if (words.length >= 3) {
                appendCandidate(candidates, String.join(" ", words[0], words[1], words[2]));
            }
            if (words.length >= 2) {
                appendCandidate(candidates, String.join(" ", words[0], words[1]));
                appendCandidate(candidates, words[0] + words[1]);
            }
            String businessStem = normalized
                    .replaceAll("\\bPTE\\b", "")
                    .replaceAll("\\bLTD\\b", "")
                    .replaceAll("\\bLIMITED\\b", "")
                    .replaceAll("\\bPRIVATE\\b", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            appendCandidate(candidates, businessStem);
            appendCandidate(candidates, businessStem.replaceAll("[^A-Za-z0-9]", ""));
        }

        return candidates.stream().distinct().toArray(String[]::new);
    }

    private String deduplicateRepeatedPartyName(String partyName) {
        String normalized = normalize(partyName);
        if (normalized.isBlank()) {
            return normalized;
        }

        String[] words = normalized.split(" ");
        if (words.length >= 2 && words.length % 2 == 0) {
            int midpoint = words.length / 2;
            String left = String.join(" ", java.util.Arrays.copyOfRange(words, 0, midpoint));
            String right = String.join(" ", java.util.Arrays.copyOfRange(words, midpoint, words.length));
            if (left.equalsIgnoreCase(right)) {
                return left;
            }
        }

        return normalized;
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
        waitForFormControls();
        Locator section = resolveSection(sectionTitle);
        Locator fields = section.locator("input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']");
        int visibleIndex = 0;
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            if (visibleIndex++ == occurrence) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to resolve field at occurrence " + occurrence + " in section " + sectionTitle);
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

    private Locator resolveFirstFieldInRow(String rowLabel) {
        return resolveEditableFieldInRowByExactText(rowLabel, 0);
    }

    private Locator resolvePartyNameField(String rowLabel) {
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

    private int partyRowOrder(String rowLabel) {
        return switch (normalize(rowLabel).toUpperCase()) {
            case "IMPORTER" -> 0;
            case "INWARD CARRIER" -> 1;
            case "FREIGHT FORWARDER" -> 2;
            default -> -1;
        };
    }

    private record PositionedField(int index, double x, double y) {
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

    private Locator resolveVisibleEditableFieldInRowOrNull(Locator row, int occurrence) {
        Locator visibleRow = firstVisible(row);
        if (visibleRow == null) {
            return null;
        }

        Locator fields = visibleRow.locator(
                "input:not([type='checkbox']):not([readonly]):not([disabled]), "
                        + "textarea:not([readonly]):not([disabled]), "
                        + "select:not([disabled]), "
                        + "[role='combobox'], "
                        + "[role='textbox']");
        int visibleIndex = 0;
        int count = fields.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            if (visibleIndex++ == occurrence) {
                return candidate;
            }
        }
        return null;
    }

    private Locator resolvePrimaryCascRow(Locator cascSection) {
        Locator row = cascSection.locator(
                "xpath=(.//*[self::button or @role='button' or self::a]"
                        + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'ADDITIONAL CASC')"
                        + " or contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'CLOSE')]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1])[1]");
        Locator visibleRow = firstVisible(row);
        if (visibleRow != null) {
            return visibleRow;
        }
        throw new IllegalStateException("CASC product row was not visible.");
    }

    private void waitForAdditionalCascSection(Locator cascSection, Locator cascRow, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            boolean closeVisible = hasButtonInScopeVisible(cascRow, "CLOSE");
            if (closeVisible || hasVisibleTextInScope(cascSection, "Code 1")) {
                return;
            }
            page.waitForTimeout(100);
        }
        captureAdditionalCascFailureArtifacts("section-not-visible");
        throw new IllegalStateException("Additional CASC section was not visible.");
    }

    private Locator waitForAdditionalCascEntryRow(Locator cascSection, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            Locator entryRow = resolveAdditionalCascEntryRowOrNull(cascSection);
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

        Locator fields = visibleScope.locator(
                "input:not([type='checkbox']):not([readonly]):not([disabled]), "
                        + "textarea:not([readonly]):not([disabled]), "
                        + "select:not([disabled]), "
                        + "[role='combobox'], "
                        + "[role='textbox'], "
                        + "[contenteditable='true']");
        return firstVisible(fields);
    }

    private Locator resolveAdditionalCascEntryRowOrNull(Locator cascSection) {
        Locator visibleScope = firstVisible(cascSection);
        if (visibleScope == null) {
            return null;
        }

        Locator copyRow = visibleScope.locator(
                "xpath=(.//*[self::button or @role='button' or self::a]"
                        + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'COPY')]"
                        + "/ancestor::*[.//*[contains(normalize-space(translate(., '*', '')), 'Code 1')]][1])[1]");
        Locator visibleCopyRow = firstVisible(copyRow);
        if (visibleCopyRow != null) {
            return visibleCopyRow;
        }

        Locator placeholderRow = visibleScope.locator(
                "xpath=(.//*[contains(normalize-space(translate(., '*', '')), 'Code 1')]"
                        + "[not(self::th)]"
                        + "[not(ancestor::*[self::thead or @role='columnheader'])]"
                        + "/ancestor::*[.//*[contains(normalize-space(translate(., '*', '')), 'Code 2')]"
                        + " and .//*[contains(normalize-space(translate(., '*', '')), 'Code 3')]][1])[last()]");
        return firstVisible(placeholderRow);
    }

    private Locator resolveAdditionalCascCodeOneActivatorOrNull(Locator additionalCascRow) {
        Locator visibleScope = firstVisible(additionalCascRow);
        if (visibleScope == null) {
            return null;
        }

        Locator rowCodeOne = visibleScope.locator(
                "xpath=(.//*[contains(normalize-space(translate(., '*', '')), 'Code 1')])[last()]");
        return firstVisible(rowCodeOne);
    }

    private void clickAdditionalCascAddButton(Locator cascSection) {
        Locator visibleSection = firstVisible(cascSection);
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

    private Locator resolveFieldByLabelOrNull(String label, int occurrence) {
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
            return associatedControl;
        }

        Locator fromNearestContainer = page.locator(
                "xpath=((" + labelQuery + ")[" + (occurrence + 1) + "]/ancestor::*[.//*[" + controlQuery + "]][1]"
                        + "//*[" + controlQuery + "])[1]");
        Locator visibleFromNearestContainer = firstVisible(fromNearestContainer);
        if (visibleFromNearestContainer != null) {
            return visibleFromNearestContainer;
        }

        Locator fromLabel = page.locator(
                "xpath=(" + labelQuery + ")[" + (occurrence + 1)
                        + "]/following::*[" + controlQuery + "][1]");
        Locator visibleFromLabel = firstVisible(fromLabel);
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

    private Locator resolveFieldByLabelInSectionOrNull(String sectionTitle, String label, int occurrence) {
        waitForFormControls();
        Locator section = resolveSection(sectionTitle);
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
        Locator visibleFromNearestContainer = firstVisible(fromNearestContainer);
        if (visibleFromNearestContainer != null) {
            return visibleFromNearestContainer;
        }

        Locator fromLabel = section.locator(
                "xpath=(" + labelQuery + ")[" + (occurrence + 1)
                        + "]/following::*[" + controlQuery + "][1]");
        Locator visibleFromLabel = firstVisible(fromLabel);
        if (visibleFromLabel != null) {
            return visibleFromLabel;
        }

        Locator placeholders = section.locator(
                "input[placeholder='" + escapeForSelector(label) + "'], textarea[placeholder='" + escapeForSelector(label) + "']");
        return firstVisible(placeholders);
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
            if (targetId.isBlank()) {
                return null;
            }

            Locator directMatch = scope.locator("#" + escapeCssIdentifier(targetId));
            Locator visibleDirectMatch = firstVisible(directMatch);
            if (visibleDirectMatch != null) {
                return visibleDirectMatch;
            }

            Locator nestedControl = firstVisible(label.locator("xpath=.//*[" + controlQuery + "]"));
            if (nestedControl != null) {
                return nestedControl;
            }
            return null;
        }
        return null;
    }

    private Locator resolveSection(String sectionTitle) {
        waitForFormControls();
        String escapedTitle = toXpathLiteral(sectionTitle);
        Locator section = page.locator(
                "xpath=(//*[contains(normalize-space(.), " + escapedTitle + ")]"
                        + "[not(.//*[contains(normalize-space(.), " + escapedTitle + ")])])[1]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox'] or .//button]");
        Locator visibleSection = firstVisible(section);
        if (visibleSection != null) {
            return visibleSection;
        }
        throw new IllegalStateException("Section container was not visible: " + sectionTitle);
    }

    private Locator firstVisible(Locator locator) {
        int count = locator.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = locator.nth(index);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        return null;
    }

    private Locator lastVisible(Locator locator) {
        int count = locator.count();
        for (int index = count - 1; index >= 0; index--) {
            Locator candidate = locator.nth(index);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        return null;
    }

    private void saveDraft() {
        clickActionButton("SAVE DRAFT", "Save Draft");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        closeTransientOverlays();
        page.waitForTimeout(300);
    }

    private void goToNextSection() {
        clickActionButton("NEXT", "Next");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        closeTransientOverlays();
    }

    private void openSection(String sectionName) {
        closeTransientOverlays();
        Locator tabs = page.locator(
                "[role='tab'], button, a, span, div");
        String normalizedSection = sectionName.trim();
        int count = tabs.count();
        for (int index = 0; index < count; index++) {
            Locator tab = tabs.nth(index);
            if (!tab.isVisible()) {
                continue;
            }
            String text = normalize(tab.innerText());
            if (normalizedSection.equals(text)) {
                tab.scrollIntoViewIfNeeded();
                tab.click(new Locator.ClickOptions().setForce(true));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                return;
            }
        }
        throw new IllegalStateException("Section tab was not visible: " + sectionName);
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

    private boolean clickActionButtonIfVisible(String buttonText) {
        closeTransientOverlays();
        Locator button = resolveActionButtonOrNull(buttonText);
        if (button == null) {
            return false;
        }

        button.scrollIntoViewIfNeeded();
        button.click(new Locator.ClickOptions().setForce(true));
        return true;
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
        int count = buttons.count();

        for (int index = count - 1; index >= 0; index--) {
            Locator button = buttons.nth(index);
            if (!button.isVisible()) {
                continue;
            }

            String text = normalize(button.innerText()).toUpperCase();
            String ariaLabel = normalize(button.getAttribute("aria-label")).toUpperCase();
            String title = normalize(button.getAttribute("title")).toUpperCase();
            String value = normalize(button.getAttribute("value")).toUpperCase();
            String name = normalize(button.getAttribute("name")).toUpperCase();
            String id = normalize(button.getAttribute("id")).toUpperCase();
            String dataAction = normalize(button.getAttribute("data-action")).toUpperCase();
            if (!text.contains(expected)
                    && !ariaLabel.contains(expected)
                    && !title.contains(expected)
                    && !value.contains(expected)
                    && !name.contains(expected)
                    && !id.contains(expected)
                    && !dataAction.contains(expected)) {
                continue;
            }
            return button;
        }

        return null;
    }

    private Locator resolveSubmitDeclarationButton() {
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

        throw new IllegalStateException("Submit Declaration button was not visible.");
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
        int count = buttons.count();
        for (int index = count - 1; index >= 0; index--) {
            Locator button = buttons.nth(index);
            if (!button.isVisible()) {
                continue;
            }

            String text = normalize(button.innerText()).toUpperCase();
            String ariaLabel = normalize(button.getAttribute("aria-label")).toUpperCase();
            String title = normalize(button.getAttribute("title")).toUpperCase();
            String value = normalize(button.getAttribute("value")).toUpperCase();
            String name = normalize(button.getAttribute("name")).toUpperCase();
            String id = normalize(button.getAttribute("id")).toUpperCase();
            String dataAction = normalize(button.getAttribute("data-action")).toUpperCase();
            if (text.contains(expected)
                    || ariaLabel.contains(expected)
                    || title.contains(expected)
                    || value.contains(expected)
                    || name.contains(expected)
                    || id.contains(expected)
                    || dataAction.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickMatchingButton(Locator buttons, String buttonText) {
        String expected = buttonText.trim().toUpperCase();
        int count = buttons.count();
        for (int index = count - 1; index >= 0; index--) {
            Locator button = buttons.nth(index);
            if (!button.isVisible()) {
                continue;
            }

            String text = normalize(button.innerText()).toUpperCase();
            String ariaLabel = normalize(button.getAttribute("aria-label")).toUpperCase();
            String title = normalize(button.getAttribute("title")).toUpperCase();
            String value = normalize(button.getAttribute("value")).toUpperCase();
            String name = normalize(button.getAttribute("name")).toUpperCase();
            String id = normalize(button.getAttribute("id")).toUpperCase();
            String dataAction = normalize(button.getAttribute("data-action")).toUpperCase();
            if (!text.contains(expected)
                    && !ariaLabel.contains(expected)
                    && !title.contains(expected)
                    && !value.contains(expected)
                    && !name.contains(expected)
                    && !id.contains(expected)
                    && !dataAction.contains(expected)) {
                continue;
            }

            button.scrollIntoViewIfNeeded();
            button.click(new Locator.ClickOptions().setForce(true));
            return true;
        }
        return false;
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

    private boolean isMissingOrEmpty(JsonNode node) {
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

    private void setCheckboxByLabel(String label, boolean checked) {
        Locator visibleCheckbox = resolveCheckboxByLabel(label);
        if (visibleCheckbox == null) {
            return;
        }

        visibleCheckbox.scrollIntoViewIfNeeded();
        boolean selected = "true".equalsIgnoreCase(normalize(visibleCheckbox.getAttribute("aria-checked")))
                || Boolean.TRUE.equals(visibleCheckbox.evaluate("element => element.checked === true"));
        if (selected != checked) {
            visibleCheckbox.click(new Locator.ClickOptions().setForce(true));
        }
    }

    private Locator resolveCheckboxByLabel(String label) {
        waitForFormControls();
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

    private void clickContainerByLabelIfPresent(String label) {
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


    private String text(JsonNode node, String... fieldNames) {
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

    private JsonNode firstArrayItem(JsonNode node) {
        if (node != null && node.isArray() && node.size() > 0) {
            return node.get(0);
        }
        return MissingNode.getInstance();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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

    private String formatUiDate(String value) {
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
        LocalDate parsedDate = parseDate(value.trim(), UI_DATE_FORMAT);
        return parsedDate == null ? "" : parsedDate.toString();
    }

    private String normalizeNumericForEntry(String value) {
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
        }
    }

    private void pauseUi(int timeoutMs) {
        if (timeoutMs > 0) {
            page.waitForTimeout(timeoutMs);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
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
