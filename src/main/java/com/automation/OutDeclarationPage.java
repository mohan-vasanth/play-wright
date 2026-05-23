package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class OutDeclarationPage extends IptDeclarationPage {

    private boolean summaryDraftSaved;

    public OutDeclarationPage(Page page) {
        super(page);
    }

    public void populateFrom(JsonNode data) {
        populateDraftFrom(data);
        if (shouldSubmitDeclaration(data)) {
            submitDeclaration();
        }
    }

    @Override
    public void submitDeclaration() {
        openSection("Summary (Y)");
        if (!summaryDraftSaved) {
            saveDraftAndWaitForCompletion();
        } else {
            waitForPostSaveReadyState(15000);
        }
        waitForActionButtonEnabled("SUBMIT DECLARATION", 15000);
        clickActionButtonExactWithRetry("SUBMIT DECLARATION", 3);
        page.waitForTimeout(5000);
    }

    public void populateDraftFrom(JsonNode data) {
        fillSectionAndAdvance("Shipment Info (S)", () -> fillShipmentInfo(data));
        fillSectionAndAdvance("Transport Info (T)", () -> fillTransportInfo(data));
        fillSectionAndAdvance("Party Info (P)", () -> fillPartyInfo(data));
        fillSectionAndAdvance("Invoice (V)", () -> fillInvoiceInfo(data));
        fillSectionAndAdvance("Items (I)", () -> fillItemInfo(data));
        fillSectionAndAdvance("CPC (C)", () -> fillCpcInfo(data));
        fillSummary(data);
    }

    private void fillShipmentInfo(JsonNode data) {
        JsonNode header = data.path("header");
        JsonNode cargo = data.path("cargo");
        JsonNode releaseLocation = cargo.path("releaseLocation");
        JsonNode receiptLocation = cargo.path("receiptLocation");
        JsonNode inwardTransportMode = data.path("transport")
                .path("inwardTransport")
                .path("transportMeans")
                .path("transportMode");
        JsonNode outwardTransportMode = data.path("transport")
                .path("outwardTransport")
                .path("transportMeans")
                .path("transportMode");

        fillDeclarationType(text(header, "declarationType"));
        fillLookupFieldInSection("Declaration Info", "Cargo Type",
                text(cargo, "cargoPackingType"),
                text(cargo, "cargoPackingType"),
                "5",
                "OTHER",
                "Other");
        fillLookupFieldInSection("Declaration Info", "Inward Transport Mode",
                text(inwardTransportMode, "modeCode"),
                text(inwardTransportMode, "modeCode"),
                "1 - Sea",
                "SEA",
                "Sea");
        setHiddenComponentValue(
                "app-transport-mode-lookup[formcontrolname='modeCode']",
                "Inward Transport Mode",
                text(inwardTransportMode, "modeCode"));
        fillLookupFieldInSection("Declaration Info", "Outward Transport Mode",
                text(outwardTransportMode, "modeCode"),
                text(outwardTransportMode, "modeCode"),
                "4 - Air",
                "AIR",
                "Air");
        fillLookupFieldInSection("Declaration Info", "Release Location",
                text(releaseLocation, "locationCode"),
                text(releaseLocation, "locationCode"),
                text(releaseLocation, "locationName"));
        fillLookupFieldInSection("Declaration Info", "Receipt Location",
                text(receiptLocation, "locationCode"),
                text(receiptLocation, "locationCode"),
                text(receiptLocation, "locationName"));
        fillLookupFieldInSectionIfPresent("Declaration Info", "Storage Location",
                text(cargo.path("storageLocation"), "locationCode"),
                text(cargo.path("storageLocation"), "locationCode"),
                text(cargo.path("storageLocation"), "locationName"));

        fillFieldInSectionIfPresent("Prev Permit Number", "Previous Permit Number", text(header, "previousPermitNumber"));
        fillFieldInSectionIfPresent("Checks", "Blanket Start Date", formatUiDate(text(cargo, "blanketStartDate")));

        String bgIndicator = text(header, "bankerGuaranteeCode");
        if (bgIndicator != null && !bgIndicator.isBlank()) {
            fillLookupFieldInSectionIfPresent("Checks", "BG Indicator",
                    bgIndicator,
                    bgIndicator,
                    "I - From Importer's BG",
                    "D - From Declaring agent's BG");
        }

        fillAdditionalRecipientsFromJson(header, data.path("formMetaData"));

        String coType = firstNonBlank(
                text(header.path("certificateOfOrigin"), "coType"),
                text(data.path("certificateOfOrigin"), "coType"));
        if (coType != null && !coType.isBlank()) {
            fillLookupFieldInSectionIfPresent("Certificate of Origin", "CO Type", coType, coType);
        }
        if (data.path("formMetaData").path("licenceIsActive").asBoolean(false)) {
            setCheckboxByLabel("License", true);
        }
        if (data.path("formMetaData").path("supportingDocumentIsActive").asBoolean(false)) {
            setCheckboxByLabel("Document", true);
        }
    }

    private void fillTransportInfo(JsonNode data) {
        JsonNode summary = data.path("summary");
        JsonNode totalOuterPack = summary.path("totalOuterPack");
        JsonNode totalGrossWeight = summary.path("totalGrossWeight");
        JsonNode inwardTransport = data.path("transport").path("inwardTransport");
        JsonNode inwardTransportMeans = inwardTransport.path("transportMeans");
        JsonNode inwardTransportMode = inwardTransportMeans.path("transportMode");
        JsonNode outwardTransport = data.path("transport").path("outwardTransport");
        JsonNode outwardTransportMeans = outwardTransport.path("transportMeans");
        JsonNode outwardTransportMode = outwardTransportMeans.path("transportMode");

        fillFieldInSection("Cargo Details", "Total Package", text(totalOuterPack, "value"));
        fillNthLookupFieldInSection("Cargo Details", 1,
                text(totalOuterPack, "unitCode"),
                text(totalOuterPack, "unitCode"));
        fillFieldInSection("Cargo Details", "Gross Weight", text(totalGrossWeight, "value"));
        fillNthLookupFieldInSection("Cargo Details", 3,
                text(totalGrossWeight, "unitCode"),
                text(totalGrossWeight, "unitCode"));

        if (resolveSectionOrNull("Inward Transport Means") != null) {
            String inwardConveyanceReferenceNumber = text(inwardTransportMode, "conveyanceReferenceNumber");
            String inwardTransportIdentifier = text(inwardTransportMode, "transportIdentifier");
            String inwardBillOfLadingNumber = text(inwardTransport, "mawboucroblNumber");

            fillFieldInSectionIfPresent(
                    "Inward Transport Means",
                    "Inward Voyage Number",
                    inwardConveyanceReferenceNumber);
            syncVisibleTextComponentValue("conveyanceReferenceNumber",
                    inwardConveyanceReferenceNumber,
                    "Inward Voyage Number");
            fillFieldInSectionByAnyLabelIfPresent(
                    "Inward Transport Means",
                    inwardTransportIdentifier,
                    "Transport Identifier",
                    "Inward Vehicle/Vessel Registration Number",
                    "Vehicle/Vessel Registration Number",
                    "Inward Aircraft Registration Number",
                    "Vehicle Licence/Registration Number",
                    "Transport Identifier",
                    "Inward Vehicle/Vessel Registration Number",
                    "Vehicle/Vessel Registration Number",
                    "Inward Aircraft Registration Number",
                    "Inward Vessel Name",
                    "Vehicle Licence/Registration Number");
            syncVisibleTextComponentValue("transportIdentifier",
                    inwardTransportIdentifier,
                    "Transport Identifier",
                    "Inward Vehicle/Vessel Registration Number",
                    "Vehicle/Vessel Registration Number",
                    "Inward Aircraft Registration Number",
                    "Inward Vessel Name",
                    "Vehicle Licence/Registration Number");
            fillFieldInSectionIfPresent(
                    "Inward Transport Means",
                    "Inward Ocean Bill of Lading Number",
                    inwardBillOfLadingNumber);
            syncVisibleTextComponentValue("mawboucroblNumber",
                    inwardBillOfLadingNumber,
                    "Inward Ocean Bill of Lading Number",
                    "Inward Ocean Bill Of Lading Number");
            fillDateFieldInSectionIfPresent("Inward Transport Means", "Arrival Date", formatUiDate(text(inwardTransport, "arrivalDate")));
            fillLookupFieldIfPresent("Loading Port",
                    text(inwardTransport, "loadingPort"),
                    text(inwardTransport, "loadingPort"));
        }

        if (resolveSectionOrNull("Outward Transport Means") != null) {
            fillFieldInSectionByAnyLabelIfPresent(
                    "Outward Transport Means",
                    text(outwardTransportMode, "conveyanceReferenceNumber"),
                    "Outward Flight Number",
                    "Flight Number",
                    "Conveyance Reference Number",
                    "Outward Flight Number",
                    "Flight Number",
                    "Conveyance Reference Number",
                    "Outward Voyage Number");
            fillFieldInSectionByAnyLabelIfPresent(
                    "Outward Transport Means",
                    text(outwardTransportMode, "transportIdentifier"),
                    "Transport Identifier",
                    "Outward Vehicle/Vessel Registration Number",
                    "Vehicle/Vessel Registration Number",
                    "Outward Aircraft Registration Number",
                    "Vehicle Licence/Registration Number",
                    "Transport Identifier",
                    "Outward Vehicle/Vessel Registration Number",
                    "Vehicle/Vessel Registration Number",
                    "Outward Aircraft Registration Number",
                    "Outward Vessel Name",
                    "Vehicle Licence/Registration Number");
            fillFieldInSectionIfPresent(
                    "Outward Transport Means",
                    "MAWB/OUCR/OBL Number",
                    text(outwardTransportMeans, "mawboucroblNumber"));
            fillDateFieldInSectionIfPresent("Outward Transport Means", "Departure Date", formatUiDate(text(outwardTransport, "departureDate")));
            fillLookupFieldInSectionIfPresent("Outward Transport Means", "Discharge Port",
                    text(outwardTransport, "dischargePort"),
                    text(outwardTransport, "dischargePort"));
            fillLookupFieldInSectionIfPresent("Outward Transport Means", "Country of Final Destination",
                    text(outwardTransport, "finalDestinationCountry"),
                    text(outwardTransport, "finalDestinationCountry"));
        }
    }

    private void fillPartyInfo(JsonNode data) {
        JsonNode party = data.path("party");

        fillLookupPartyComponent("app-inward-carrier-lookup[formcontrolname='name']",
                text(party.path("inwardCarrierAgentParty").path("partyName"), "name"),
                text(party.path("inwardCarrierAgentParty").path("partyIdentification"), "id"));
        fillLookupPartyComponent("app-freight-forwarder-lookup[formcontrolname='name']",
                text(party.path("freightForwarderParty").path("partyName"), "name"),
                text(party.path("freightForwarderParty").path("partyIdentification"), "id"));
        fillLookupPartyComponent("app-outward-carrier-agent-lookup[formcontrolname='name']",
                text(party.path("outwardCarrierAgentParty").path("partyName"), "name"),
                text(party.path("outwardCarrierAgentParty").path("partyIdentification"), "id"));

        fillPartyCard(
                "Exporter",
                party.path("exporterParty").path("partyDetail").path("partyName").path("name"),
                party.path("exporterParty").path("partyDetail").path("partyIdentification").path("id"),
                party.path("exporterParty").path("address"));
        fillPartyCard(
                "Consignee",
                party.path("consigneeParty").path("partyName").path("name"),
                party.path("consigneeParty").path("partyIdentification").path("id"),
                party.path("consigneeParty").path("address"));
        fillPartyCard(
                "End User",
                party.path("endUserParty").path("partyName").path("name"),
                party.path("endUserParty").path("partyIdentification").path("id"),
                party.path("endUserParty").path("address"));
        fillPartyCard(
                "Manufacturer",
                party.path("manufacturerParty").path("partyName").path("name"),
                party.path("manufacturerParty").path("partyIdentification").path("id"),
                party.path("manufacturerParty").path("address"));
    }

    private void fillPartyCard(String title, JsonNode nameNode, JsonNode idNode, JsonNode addressNode) {
        String name = normalize(nodeText(nameNode));
        String id = normalize(nodeText(idNode));
        if ((name == null || name.isBlank()) && (addressNode == null || addressNode.isMissingNode())) {
            return;
        }

        Locator card = resolvePartyCard(title);
        if (card == null) {
            return;
        }

        fillNthLookupFieldInScopeIfPresent(card, 0, firstNonBlank(id, name), firstNonBlank(id, name), name);
        fillNthFieldInScopeIfPresent(card, 1, id);

        JsonNode addressLines = addressNode.path("addressLine").path("line");
        fillFieldAfterScopeLabelIfPresent(card, "Address Line 1", 0, arrayText(addressLines, 0));
        fillFieldAfterScopeLabelIfPresent(card, "Address Line 2", 0, arrayText(addressLines, 1));
        fillFieldAfterScopeLabelIfPresent(card, "City", 0, text(addressNode, "cityName"));
        fillFieldAfterScopeLabelIfPresent(card, "Postal Code", 0,
                firstNonBlank(text(addressNode, "postalZone"), text(addressNode, "countrySubentityCode")));
        fillLookupFieldAfterScopeLabelIfPresent(card, "Country Code", 0,
                text(addressNode, "countryCode"),
                text(addressNode, "countryCode"));
    }

    private void fillInvoiceInfo(JsonNode data) {
        JsonNode invoice = firstArrayItem(data.path("invoice"));
        JsonNode item = firstArrayItem(data.path("item"));
        JsonNode unitPriceValue = item.path("transactionValue").path("unitPriceValue");

        fillField("Invoice Number", firstNonBlank(text(invoice, "invoiceNumber"), "1"));
        fillDateField("Invoice Date", formatUiDate(text(invoice, "invoiceDate")));
        fillInvoiceTermType(text(invoice, "unitPriceTermType"));

        String supplierManufacturerName = text(invoice.path("supplierManufacturerParty"), "name");
        if (supplierManufacturerName != null && !supplierManufacturerName.isBlank()) {
            fillSupplierManufacturerName(supplierManufacturerName);
        }

        String invoiceCurrency = firstNonBlank(
                text(invoice.path("totalInvoiceValue").path("amount"), "currencyID"),
                text(unitPriceValue.path("amount"), "currencyID"));
        String invoiceAmount = firstNonBlank(
                text(invoice.path("totalInvoiceValue").path("amount"), "value"),
                text(data.path("summary"), "totalCifFobValue"),
                text(item.path("transactionValue"), "itemCIFFOBValue"));
        fillLookupFieldInChargeRowIfPresent("A. Total Invoice", "Currency", invoiceCurrency, invoiceCurrency);
        fillFieldInChargeRowIfPresent("A. Total Invoice", "Amount", normalizeNumericForEntry(invoiceAmount));

        JsonNode freightCharge = invoice.path("freightCharge");
        if (freightCharge.path("includeOtherTaxableCharge").asBoolean(false)) {
            setCheckboxByLabel("Include Other", true);
        }
        fillFieldInChargeRowIfPresent("C. Freight Charge", "Charge %",
                normalizeNumericForEntry(text(freightCharge, "chargePercent")));
        fillLookupFieldInChargeRowIfPresent("C. Freight Charge", "Currency",
                text(freightCharge.path("amount"), "currencyID"),
                text(freightCharge.path("amount"), "currencyID"));
        fillFieldInChargeRowIfPresent("C. Freight Charge", "Exchange Rate",
                normalizeNumericForEntry(text(freightCharge, "exchangeRate")));
        fillFieldInChargeRowIfPresent("C. Freight Charge", "Amount",
                normalizeNumericForEntry(text(freightCharge.path("amount"), "value")));

        JsonNode insuranceCharge = invoice.path("insuranceCharge");
        fillFieldInChargeRowIfPresent("E. Insurance Charge", "Charge %",
                normalizeNumericForEntry(text(insuranceCharge, "chargePercent")));
        fillLookupFieldInChargeRowIfPresent("E. Insurance Charge", "Currency",
                text(insuranceCharge.path("amount"), "currencyID"),
                text(insuranceCharge.path("amount"), "currencyID"));
        fillFieldInChargeRowIfPresent("E. Insurance Charge", "Exchange Rate",
                normalizeNumericForEntry(text(insuranceCharge, "exchangeRate")));
        fillFieldInChargeRowIfPresent("E. Insurance Charge", "Amount",
                normalizeNumericForEntry(text(insuranceCharge.path("amount"), "value")));

        String gst = nodeText(invoice.path("gst"));
        if (gst != null && !gst.isBlank()) {
            fillLookupFieldInRowByIndex("K. GST", 0, gst, gst, gst + "%");
        }
    }

    private void fillItemInfo(JsonNode data) {
        JsonNode item = firstArrayItem(data.path("item"));
        JsonNode itemQuantity = item.path("itemQuantity");
        JsonNode transactionValue = item.path("transactionValue");
        JsonNode unitPriceValue = transactionValue.path("unitPriceValue");

        fillFieldIfPresent("Inward HAWB", text(item, "inHawbHucrHblNumber"));
        fillFieldIfPresent("Outward HAWB", text(item, "outHawbHucrHblNumber"));
        fillLookupFieldIfPresent("Currency",
                text(unitPriceValue.path("amount"), "currencyID"),
                text(unitPriceValue.path("amount"), "currencyID"));
        fillFieldIfPresent("Exchange Rate", normalizeNumericForEntry(text(unitPriceValue, "exchangeRate")));
        fillItemHsCode(text(item, "itemHarmonizedSystemCode"));
        fillFieldIfPresent("Description", text(item, "goodsDescription"));
        fillLookupFieldIfPresent("COO",
                text(item, "originCountry"),
                text(item, "originCountry"));
        fillFieldIfPresent("Brand", text(item, "brandName"));
        fillFieldIfPresent("Model", text(item, "modelDescription"));

        if (item.path("dangerousGoodsIndicator").asBoolean(false)) {
            setCheckboxByLabel("DG", true);
        }
        if (item.path("unbrandedIndicator").asBoolean(false)) {
            setCheckboxByLabel("Unbranded", true);
        }
        if (data.path("formMetaData").path("itemPackingIsActive").path(0).asBoolean(false)) {
            setCheckboxByLabel("Item Packing", true);
        }

        Locator itemQuantitySection = resolveSection("Item Quantity");
        fillQuantityRowInScope(itemQuantitySection, "Dutiable Quantity", itemQuantity.path("dutiableQuantity"));
        fillQuantityRowInScope(itemQuantitySection, "Total Dutiable Quantity", itemQuantity.path("totalDutiableQuantity"));
        fillQuantityRowInScope(itemQuantitySection, "HS Quantity",
                firstNonBlankNode(itemQuantity.path("hsQuantity"), itemQuantity.path("harmonizedSystemQuantity")));

        fillFieldIfPresent("Item Unit Value",
                normalizeNumericForEntry(text(unitPriceValue.path("amount"), "value")));
        fillItemValues(transactionValue);
    }

    private void fillAdditionalRecipientsFromJson(JsonNode header, JsonNode formMetaData) {
        if (formMetaData == null || !formMetaData.path("additionalRecipientIdIsActive").asBoolean(false)) {
            ensureAdditionalRecipientsInactive();
            return;
        }

        JsonNode additionalRecipientIds = header.path("additionalRecipientId");
        if (additionalRecipientIds == null || !additionalRecipientIds.isArray() || additionalRecipientIds.isEmpty()) {
            return;
        }

        String additionalRecipientId = normalize(additionalRecipientIds.get(0).asText());
        if (additionalRecipientId == null || additionalRecipientId.isBlank()) {
            return;
        }

        setCheckboxByLabel("Additional Recipients", true);
        page.waitForTimeout(300);

        Locator additionalRecipientsSection = firstVisible(page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Additional Recipients'])[last()]"
                        + "/ancestor::*[.//button or .//input or .//textarea][1]"));
        if (additionalRecipientsSection == null) {
            return;
        }

        Locator addButton = firstVisible(additionalRecipientsSection.locator(
                "button, [role='button'], input[type='button'], input[type='submit'], a"));
        if (addButton != null) {
            String buttonText = normalize(addButton.innerText());
            if (buttonText.contains("ADD")) {
                addButton.click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(300);
            }
        }

        Locator input = firstVisible(additionalRecipientsSection.locator(
                "input:not([type='checkbox']):not([readonly]):not([disabled]), textarea:not([readonly]):not([disabled])"));
        if (input == null) {
            return;
        }

        focusAndType(input, additionalRecipientId, false);
    }

    private void ensureAdditionalRecipientsInactive() {
        try {
            page.evaluate("""
                    () => {
                        const isVisible = element => !!element
                            && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const dispatch = element => {
                            element.dispatchEvent(new Event('input', { bubbles: true }));
                            element.dispatchEvent(new Event('change', { bubbles: true }));
                            element.dispatchEvent(new Event('blur', { bubbles: true }));
                        };

                        const checkbox = Array.from(document.querySelectorAll("input[type='checkbox'][formcontrolname='additionalRecipientIdIsActive']"))
                            .find(isVisible);
                        if (checkbox) {
                            checkbox.checked = false;
                            checkbox.removeAttribute('checked');
                            checkbox.setAttribute('aria-checked', 'false');
                            dispatch(checkbox);
                        }

                        const componentHost = checkbox?.closest("[formcontrolname='additionalRecipientIdIsActive'], clr-checkbox-wrapper, label, div");
                        const component = componentHost && typeof window.ng !== 'undefined' && typeof window.ng.getComponent === 'function'
                            ? window.ng.getComponent(componentHost)
                            : null;
                        if (component) {
                            if ('checked' in component) {
                                component.checked = false;
                            }
                            if ('value' in component) {
                                component.value = false;
                            }
                            if ('_value' in component) {
                                component._value = false;
                            }
                            if (typeof component.onChange === 'function') {
                                component.onChange(false);
                            }
                            if (typeof component.onTouched === 'function') {
                                component.onTouched();
                            }
                        }

                        const additionalRecipientFields = document.querySelectorAll(
                            "[formcontrolname='additionalRecipientId'], input[formcontrolname='additionalRecipientId'], textarea[formcontrolname='additionalRecipientId']"
                        );
                        additionalRecipientFields.forEach(field => {
                            if ('value' in field) {
                                field.value = '';
                            }
                            dispatch(field);
                        });
                    }
                    """);
            page.waitForTimeout(200);
        } catch (Exception ignored) {
        }
    }

    private void fillCpcInfo(JsonNode data) {
        JsonNode formMetaData = data.path("formMetaData");
        setCheckboxIfTrue(formMetaData.path("aeoIsActive").asBoolean(false), "AEO");
        setCheckboxIfTrue(formMetaData.path("cwcIsActive").asBoolean(false), "CWC");
        setCheckboxIfTrue(formMetaData.path("seastoreIsActive").asBoolean(false), "SEASTORE");
        setCheckboxIfTrue(formMetaData.path("stsIsActive").asBoolean(false), "STS");
        setCheckboxIfTrue(formMetaData.path("stsAndCwcIsActive").asBoolean(false), "STS & CWC");
        setCheckboxIfTrue(formMetaData.path("internationalPermitExchangeIsActive").asBoolean(false), "INTERNATIONAL PERMIT EXCHANGE");
        setCheckboxIfTrue(formMetaData.path("cnbIsActive").asBoolean(false), "CNB");
        setCheckboxIfTrue(formMetaData.path("deferredPrintingOfCoIsActive").asBoolean(false), "Deferred Printing of CO");
    }

    private void fillItemHsCode(String hsCode) {
        if (hsCode == null || hsCode.isBlank()) {
            return;
        }

        Locator itemDetailsSection = resolveSection("Item Details");
        Locator editableInput = firstVisible(itemDetailsSection.locator(
                "xpath=((.//*[self::label or self::span or self::div or self::p]"
                        + "[contains(normalize-space(translate(., '*', '')), 'HS Code')]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), 'HS Code')])])[1]"
                        + "/following::*[self::input[not(@type='checkbox')] or self::textarea][1])"));
        if (editableInput == null) {
            Locator hsCodeField = resolveFieldByLabelInSectionOrNull("Item Details", "HS Code", 0);
            if (hsCodeField != null) {
                editableInput = firstVisible(hsCodeField.locator("input:not([type='checkbox']), textarea"));
                if (editableInput == null) {
                    editableInput = hsCodeField;
                }
            }
        }
        if (editableInput == null) {
            return;
        }

        focusAndType(editableInput, hsCode, true, hsCode);
        if (!waitForAnyRenderedFieldValue(editableInput, 1500, hsCode)) {
            try {
                editableInput.evaluate("""
                        (element, value) => {
                            element.value = value;
                            element.dispatchEvent(new Event('input', { bubbles: true }));
                            element.dispatchEvent(new Event('change', { bubbles: true }));
                            element.dispatchEvent(new Event('blur', { bubbles: true }));
                        }
                        """, hsCode);
            } catch (Exception ignored) {
            }
        }
    }

    private void fillSummary(JsonNode data) {
        openSection("Summary (Y)");

        JsonNode summary = data.path("summary");
        fillFieldIfPresent("Number of Invoices", text(summary, "numberOfInvoices"));
        fillFieldIfPresent("Number of Items", text(summary, "numberOfItems"));
        fillFieldIfPresent("Total Invoice CIF Value", normalizeNumericForEntry(text(summary, "totalInvoiceCifValue")));
        fillFieldIfPresent("Total CIF/FOB Value", normalizeNumericForEntry(text(summary, "totalCifFobValue")));
        fillFieldIfPresent("Cross Reference ID",
                firstNonBlank(text(data.path("header"), "crossReferenceId"), text(summary, "crossReferenceId")));

        fillFieldIfPresent("Remarks", text(data.path("header").path("remarks"), "freeText"));
        fillFieldIfPresent("Internal Remarks", text(data.path("header").path("remarks"), "internalText"));
        fillFieldIfPresent("Customer Remarks", text(data.path("header").path("remarks"), "customerText"));

        if (data.path("header").path("declarationIndicator").asBoolean(false)) {
            setCheckboxByLabel("Declaration Indicator", true);
            setCheckboxByLabel("I/We declare that all the particulars in this Application are true and correct.", true);
            syncCheckboxValue(true,
                    "I/We declare that all the particulars in this Application are true and correct.",
                    "Declaration Indicator");
        }

        saveDraftAndWaitForCompletion();
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

    private void saveDraftAndWaitForCompletion() {
        waitForActionButtonEnabled("SAVE DRAFT", 15000);
        clickActionButtonExactWithRetry("SAVE DRAFT", 3);
        waitForPostSaveReadyState(15000);
        summaryDraftSaved = true;
    }

    private void waitForPostSaveReadyState(int timeoutMs) {
        int stableChecks = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Boolean ready = (Boolean) page.evaluate("""
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

                        const saveDraftButton = findExactButton('SAVE DRAFT');
                        const submitButton = findExactButton('SUBMIT DECLARATION');
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
                        return !!saveDraftButton
                            && !!submitButton
                            && isEnabled(saveDraftButton)
                            && isEnabled(submitButton)
                            && !hasBusyOverlay;
                    }
                    """);
            if (Boolean.TRUE.equals(ready)) {
                stableChecks++;
                if (stableChecks >= 4) {
                    return;
                }
            } else {
                stableChecks = 0;
            }
            page.waitForTimeout(300);
        }
        throw new IllegalStateException("Draft save did not reach a ready state before submit.");
    }

    private void clickActionButtonExactWithRetry(String buttonText, int attempts) {
        String expected = buttonText.trim().toUpperCase();
        for (int attempt = 0; attempt < attempts; attempt++) {
            Boolean clicked = (Boolean) page.evaluate("""
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
                    """, expected);
            if (Boolean.TRUE.equals(clicked)) {
                return;
            }
            page.waitForTimeout(750);
        }
        throw new IllegalStateException("Unable to click action button: " + buttonText);
    }

    private boolean shouldSubmitDeclaration(JsonNode data) {
        return data.path("summary").path("submitDeclaration").asBoolean(false)
                || data.path("formMetaData").path("submitDeclaration").asBoolean(false);
    }

    private void setCheckboxIfTrue(boolean enabled, String label) {
        if (enabled) {
            setCheckboxByLabel(label, true);
        }
    }

    private JsonNode firstNonBlankNode(JsonNode first, JsonNode second) {
        if (first != null && !first.isMissingNode() && !first.isNull() && !first.asText("").isBlank()) {
            return first;
        }
        return second;
    }

    private Locator resolvePartyCard(String title) {
        String escapedTitle = toXpathLiteral(title);
        Locator locator = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox']][1]");
        return firstVisible(locator);
    }

    private Locator resolveSectionOrNull(String title) {
        try {
            return resolveSection(title);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void fillLookupPartyComponent(String selector, String partyName, String partyId) {
        String searchValue = firstNonBlank(partyName, partyId);
        if (searchValue == null || searchValue.isBlank()) {
            return;
        }

        Locator component = firstVisible(page.locator(selector));
        if (component == null) {
            return;
        }

        Locator field = firstVisible(component.locator("input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']"));
        if (field == null) {
            return;
        }

        focusAndType(field, searchValue, true, firstNonBlank(partyId, partyName), partyName, partyId);
    }

    private void setHiddenComponentValue(String selector, String labelText, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            page.evaluate("""
                    args => {
                        const normalize = input => (input || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const target = Array.from(document.querySelectorAll(args.selector))
                            .find(element => normalize(element.innerText || element.textContent).includes(normalize(args.labelText)));
                        if (!target) {
                            return false;
                        }

                        if (typeof window.ng !== 'undefined' && typeof window.ng.getComponent === 'function') {
                            const component = window.ng.getComponent(target);
                            if (component) {
                                const option = Array.isArray(component.allOptions)
                                    ? component.allOptions.find(candidate =>
                                        normalize(candidate?.code) === normalize(args.value)
                                        || normalize(candidate?.description) === normalize(args.value)
                                        || normalize(`${candidate?.code} ${candidate?.description}`) === normalize(args.value))
                                    : null;
                                if (option) {
                                    component._value = option.code;
                                    component.inputDisplayValue = option.description;
                                    component.displayValue = option.description;
                                    if (typeof component.selectOptionItem === 'function') {
                                        component.selectOptionItem(option);
                                    }
                                    if (typeof component.selectOptionLabel === 'function') {
                                        component.selectOptionLabel(option);
                                    }
                                    if (typeof component.onChange === 'function') {
                                        component.onChange(option.code);
                                    }
                                    if (typeof component.onTouched === 'function') {
                                        component.onTouched();
                                    }
                                    return true;
                                }
                            }
                        }

                        const field = target.querySelector('input, textarea, select');
                        if (!field) {
                            return false;
                        }

                        field.value = args.value;
                        field.dispatchEvent(new Event('input', { bubbles: true }));
                        field.dispatchEvent(new Event('change', { bubbles: true }));
                        field.dispatchEvent(new Event('blur', { bubbles: true }));
                        return true;
                    }
                    """, java.util.Map.of(
                    "selector", selector,
                    "labelText", labelText,
                    "value", value));
        } catch (Exception ignored) {
        }
    }

    private void fillLookupPartyRow(String rowLabel, String partyName, String partyId) {
        String searchValue = firstNonBlank(partyName, partyId);
        if (searchValue == null || searchValue.isBlank()) {
            return;
        }

        Locator row = resolvePartyTableRow(rowLabel);
        if (row == null) {
            return;
        }

        Locator nameField = resolveVisibleEditableFieldInRowOrNull(row, 0);
        if (nameField == null) {
            return;
        }

        focusAndType(nameField, searchValue, true, firstNonBlank(partyId, partyName), partyName, partyId);
        Locator idField = resolveVisibleEditableFieldInRowOrNull(row, 1);
        if (idField != null && partyId != null && !partyId.isBlank() && !waitForAnyRenderedFieldValue(idField, 500, partyId)) {
            focusAndType(idField, partyId, false);
        }
    }

    private void syncVisibleTextComponentValue(String formControlName, String value, String... labelHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            page.evaluate("""
                    args => {
                        const normalize = input => (input || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const hints = (args.labelHints || []).map(normalize).filter(Boolean);
                        const target = Array.from(document.querySelectorAll(`[formcontrolname="${args.formControlName}"]`))
                            .filter(isVisible)
                            .find(element => {
                                if (hints.length === 0) {
                                    return true;
                                }
                                const text = normalize(element.innerText || element.textContent);
                                return hints.some(hint => text.includes(hint) || hint.includes(text));
                            });
                        if (!target) {
                            return false;
                        }

                        const component = typeof window.ng !== 'undefined' && typeof window.ng.getComponent === 'function'
                            ? window.ng.getComponent(target)
                            : null;
                        if (component) {
                            if ('value' in component) {
                                component.value = args.value;
                            }
                            if ('_value' in component) {
                                component._value = args.value;
                            }
                            if ('inputDisplayValue' in component) {
                                component.inputDisplayValue = args.value;
                            }
                            if ('displayValue' in component) {
                                component.displayValue = args.value;
                            }
                            const nativeInput = component.inputElement?.nativeElement;
                            if (nativeInput) {
                                nativeInput.value = args.value;
                                nativeInput.dispatchEvent(new Event('input', { bubbles: true }));
                                nativeInput.dispatchEvent(new Event('change', { bubbles: true }));
                                nativeInput.dispatchEvent(new Event('blur', { bubbles: true }));
                            }
                            if (typeof component.onChange === 'function') {
                                component.onChange(args.value);
                            }
                            if (typeof component.onTouched === 'function') {
                                component.onTouched();
                            }
                        }

                        const field = target.querySelector('input, textarea');
                        if (field) {
                            field.value = args.value;
                            field.dispatchEvent(new Event('input', { bubbles: true }));
                            field.dispatchEvent(new Event('change', { bubbles: true }));
                            field.dispatchEvent(new Event('blur', { bubbles: true }));
                        }
                        return true;
                    }
                    """, java.util.Map.of(
                    "formControlName", formControlName,
                    "value", value,
                    "labelHints", labelHints));
        } catch (Exception ignored) {
        }
    }

    private void syncCheckboxValue(boolean checked, String... labelHints) {
        try {
            page.evaluate("""
                    args => {
                        const normalize = input => (input || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const hints = (args.labelHints || []).map(normalize).filter(Boolean);
                        const label = Array.from(document.querySelectorAll('label, span, div, p'))
                            .filter(isVisible)
                            .find(element => {
                                const text = normalize(element.innerText || element.textContent);
                                return hints.some(hint => text.includes(hint));
                            });
                        if (!label) {
                            return false;
                        }

                        const checkbox = label.closest('div, label, section, form')?.querySelector('input[type="checkbox"]')
                            || label.parentElement?.querySelector('input[type="checkbox"]')
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

    private Locator resolvePartyTableRow(String rowLabel) {
        String escaped = toXpathLiteral(rowLabel);
        Locator tableRow = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escaped + "])[1]/ancestor::tr[1]");
        Locator visibleTableRow = firstVisible(tableRow);
        if (visibleTableRow != null) {
            return visibleTableRow;
        }

        Locator genericRow = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escaped + "])[1]"
                        + "/ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > 1][1]");
        return firstVisible(genericRow);
    }

    private void fillFieldInChargeRowIfPresent(String rowLabel, String columnLabel, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            fillFieldInChargeRow(rowLabel, columnLabel, value);
        } catch (Exception ignored) {
        }
    }

    private void fillLookupFieldInChargeRowIfPresent(String rowLabel, String columnLabel, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            fillLookupFieldInChargeRow(rowLabel, columnLabel, value, suggestionHints);
        } catch (Exception ignored) {
        }
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = normalize(node.asText());
        return value.isBlank() ? null : value;
    }

    private String arrayText(JsonNode arrayNode, int index) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.size() <= index) {
            return null;
        }
        return nodeText(arrayNode.get(index));
    }

    private String toXpathLiteral(String value) {
        if (value == null) {
            return "''";
        }
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }

        StringBuilder builder = new StringBuilder("concat(");
        for (int index = 0; index < value.length(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            char current = value.charAt(index);
            if (current == '\'') {
                builder.append("\"'\"");
            } else {
                builder.append('\'').append(current).append('\'');
            }
        }
        builder.append(')');
        return builder.toString();
    }
}
