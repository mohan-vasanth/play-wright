package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TnpDeclarationPage extends IptDeclarationPage {

    public TnpDeclarationPage(Page page) {
        super(page);
    }

    @Override
    protected String declarationFlowLabel() {
        return "TNP";
    }

    @Override
    protected void validateDeclarationPayload(JsonNode data) {
        if (!DeclarationPayloads.matchesDeclarationFamily(data, "TNP")) {
            throw new IllegalArgumentException("Payload does not match a TNP declaration.");
        }
    }

    @Override
    protected void fillDeclarationSpecificShipmentInfo(JsonNode data) {
        fillFieldInSectionIfPresent(
                "Prev Permit Number",
                "Previous Permit Number",
                firstNonBlank(
                        text(data.path("header"), "previousPermitNumber"),
                        text(data, "previousPermitNumber")));

        String cargoType = text(data.path("cargo"), "cargoPackingType");
        String[] cargoTypeSearchHints = cargoTypeSearchHints(cargoType);
        String[] cargoTypeRenderedHints = cargoTypeRenderedHints(cargoType);
        var cargoTypeField = resolveFieldByLabelInSectionOrNull("Declaration Info", "Cargo Type", 0);
        if (cargoTypeField != null) {
            focusAndType(cargoTypeField, cargoType, true, cargoTypeSearchHints);
            if (!waitForStrictRenderedFieldValue(cargoTypeField, 1200, cargoTypeRenderedHints)) {
                openLookupAndChooseOption(cargoTypeField, cargoTypeRenderedHints);
            }
            if (!waitForStrictRenderedFieldValue(cargoTypeField, 1200, cargoTypeRenderedHints)) {
                syncLookupComponentValue(
                        "app-cargo-type-lookup[formcontrolname='cargoPackingType'], [formcontrolname='cargoPackingType']",
                        "Cargo Type",
                        cargoType);
            }
            waitForStrictRenderedFieldValue(cargoTypeField, 1500, cargoTypeRenderedHints);
        } else {
            logFieldMappingWarning("UI lookup field not found for section 'Declaration Info' and label 'Cargo Type' while JSON value was '"
                    + cargoType + "'.");
        }
        logLookupFieldResult(
                "Declaration Info",
                "Cargo Type",
                cargoType,
                cargoTypeRenderedHints);

        JsonNode outwardTransportMode = data.path("transport").path("outwardTransport").path("transportMeans").path("transportMode");
        String modeCode = text(outwardTransportMode, "modeCode");
        String[] suggestionHints = transportModeHints(modeCode);
        if (resolveFieldByLabelInSectionOrNull("Declaration Info", "Outward Transport ID", 0) != null) {
            fillLookupFieldInSectionIfPresent(
                    "Declaration Info",
                    "Outward Transport ID",
                    modeCode,
                    suggestionHints);
            return;
        }
        fillLookupFieldInSectionIfPresent(
                "Declaration Info",
                "Outward Transport Mode",
                modeCode,
                suggestionHints);
    }

    @Override
    protected void fillDeclarationSpecificTransportInfo(JsonNode data) {
        JsonNode outwardTransport = data.path("transport").path("outwardTransport");
        JsonNode additionalVesselInformation = outwardTransport.path("additionalVesselInformation");
        if (isMissingOrEmpty(additionalVesselInformation)) {
            return;
        }

        if (resolveSectionOrNull("Additional Vessel Information") == null) {
            logFieldMappingWarning("TNP additional vessel information UI section was not visible while JSON values were present.");
            return;
        }

        fillTnpLookupFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Vessel Type",
                text(additionalVesselInformation, "vesselType"),
                text(additionalVesselInformation, "vesselType"));
        fillTnpFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Towing Vessel Voyage Number",
                text(additionalVesselInformation.path("towingVessel"), "vesselID"));
        fillTnpFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Towing Vessel Name",
                text(additionalVesselInformation.path("towingVessel"), "vesselName"));
    }

    @Override
    protected void fillTransportInfo(JsonNode data) {
        JsonNode cargo = data.path("cargo");
        JsonNode summary = data.path("summary");
        JsonNode totalOuterPack = summary.path("totalOuterPack");
        JsonNode totalGrossWeight = summary.path("totalGrossWeight");
        JsonNode inwardTransport = data.path("transport").path("inwardTransport");
        JsonNode inwardTransportMeans = inwardTransport.path("transportMeans");
        JsonNode inwardTransportMode = inwardTransportMeans.path("transportMode");
        JsonNode outwardTransport = data.path("transport").path("outwardTransport");
        JsonNode outwardTransportMeans = outwardTransport.path("transportMeans");
        JsonNode outwardTransportMode = outwardTransportMeans.path("transportMode");

        waitForAnyVisibleText(
                "Cargo Details",
                "Inward Transport Means",
                "Outward Transport Means",
                "Arrival Date",
                "Conveyance Reference Number",
                "Loading Port",
                "Departure Date",
                "Discharge Port",
                "Country of Final Destination");

        fillTnpFieldInSectionIfPresent("Cargo Details", "Total Package", text(totalOuterPack, "value"));
        fillTnpNthLookupFieldInSectionIfPresent("Cargo Details", 1, text(totalOuterPack, "unitCode"), text(totalOuterPack, "unitCode"));
        fillTnpFieldInSectionIfPresent("Cargo Details", "Gross Weight", text(totalGrossWeight, "value"));
        fillTnpNthLookupFieldInSectionIfPresent("Cargo Details", 3, text(totalGrossWeight, "unitCode"), text(totalGrossWeight, "unitCode"));

        fillTnpDateFieldInSectionIfPresent(
                "Inward Transport Means",
                "Arrival Date",
                formatUiDate(text(inwardTransport, "arrivalDate")));
        fillTnpFieldInSectionIfPresent(
                "Inward Transport Means",
                inwardConveyanceLabel(text(inwardTransportMode, "modeCode")),
                text(inwardTransportMode, "conveyanceReferenceNumber"));
        fillTnpLookupFieldInSectionIfPresent(
                "Inward Transport Means",
                "Loading Port",
                text(inwardTransport, "loadingPort"),
                text(inwardTransport, "loadingPort"));
        fillTnpFieldInSectionIfPresent(
                "Inward Transport Means",
                inwardIdentifierLabel(text(inwardTransportMode, "modeCode")),
                text(inwardTransportMode, "transportIdentifier"));
        fillTnpFieldInSectionIfPresent(
                "Inward Transport Means",
                inwardReferenceNumberLabel(text(inwardTransportMode, "modeCode")),
                firstNonBlank(
                        text(inwardTransportMeans, "mawboucroblNumber"),
                        text(inwardTransport, "mawboucroblNumber")));

        fillTnpDateFieldInSectionIfPresent(
                "Outward Transport Means",
                "Departure Date",
                formatUiDate(text(outwardTransport, "departureDate")));
        fillTnpFieldInSectionIfPresent(
                "Outward Transport Means",
                outwardConveyanceLabel(text(outwardTransportMode, "modeCode")),
                text(outwardTransportMode, "conveyanceReferenceNumber"));
        fillTnpLookupFieldInSectionIfPresent(
                "Outward Transport Means",
                "Discharge Port",
                text(outwardTransport, "dischargePort"),
                text(outwardTransport, "dischargePort"));
        fillTnpFieldInSectionIfPresent(
                "Outward Transport Means",
                outwardIdentifierLabel(text(outwardTransportMode, "modeCode")),
                text(outwardTransportMode, "transportIdentifier"));
        fillTnpFieldInSectionIfPresent(
                "Outward Transport Means",
                outwardReferenceNumberLabel(text(outwardTransportMode, "modeCode")),
                firstNonBlank(
                        text(outwardTransportMeans, "mawboucroblNumber"),
                        text(outwardTransport, "mawboucroblNumber")));
        fillTnpLookupFieldInSectionIfPresent(
                "Outward Transport Means",
                "Country of Final Destination",
                text(outwardTransport, "finalDestinationCountry"),
                text(outwardTransport, "finalDestinationCountry"));

        fillDeclarationSpecificTransportInfo(data);
        fillTnpTransportEquipmentDetails(cargo);
    }

    @Override
    protected void fillPartyInfo(JsonNode data) {
        JsonNode party = data.path("party");
        waitForAnyVisibleText(
                "Party Name",
                "Declaring Agent",
                "Inward Carrier",
                "Outward Carrier",
                "Handling Agent",
                "Importer",
                "Freight Forwarder",
                "Consignee",
                "End User");
        fillStrictTnpPartyRow("Importer", party.path("importerParty"));
        fillStrictTnpPartyRow("Inward Carrier", party.path("inwardCarrierAgentParty"));
        fillStrictTnpPartyRow("Outward Carrier", party.path("outwardCarrierAgentParty"));
        fillStrictTnpPartyRow("Freight Forwarder", party.path("freightForwarderParty"));
        fillStrictTnpPartyRow("Handling Agent", party.path("handlingAgentParty"));
        fillStrictTnpPartyRow("Declaring Agent", party.path("declaringAgentParty"));
        fillStrictTnpPartyCard("Consignee", party.path("consigneeParty"));
        fillStrictTnpPartyCard("End User", party.path("endUserParty"));
    }

    @Override
    protected void fillDeclarationSpecificPartyInfo(JsonNode data, JsonNode party) {
    }

    @Override
    protected void fillItemInfo(JsonNode data) {
        JsonNode items = data.path("item");
        if (!items.isArray() || items.isEmpty()) {
            logFieldMappingInfo("TNP items JSON -> no item array present; Items tab skipped.");
            return;
        }

        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                clickTnpAddItemButton();
                page.waitForTimeout(500);
            }
            fillTnpSingleItem(items.get(index), data.path("formMetaData"), index);
        }
    }

    @Override
    protected void fillDeclarationSpecificItemInfo(JsonNode data, JsonNode item) {
        fillTnpFieldIfPresentByLabels(
                text(item, "inMAWBOUCROBLNumber"),
                "InMAWB/OUCRO/BL Number",
                "InMAWB/UCR/OBL Number",
                "Inward MAWB/UCR/OBL Number");
        fillTnpFieldIfPresentByLabels(
                text(item, "outMAWBOUCROBLNumber"),
                "OutMAWB/OUCRO/BL Number",
                "OutMAWB/UCR/OBL Number",
                "Outward MAWB/UCR/OBL Number");
    }

    @Override
    protected void fillDeclarationSpecificCpcInfo(JsonNode data) {
    }

    @Override
    protected String invoiceSectionName() {
        return null;
    }

    @Override
    protected String cpcSectionName() {
        return "CPC (C)";
    }

    private String[] transportModeHints(String modeCode) {
        String normalizedModeCode = firstNonBlank(modeCode, "").trim();
        return switch (normalizedModeCode) {
            case "1" -> new String[] { "1 - Sea", "Sea", "1" };
            case "3" -> new String[] { "3 - Road", "Road", "3" };
            case "4" -> new String[] { "4 - Air", "Air", "4" };
            default -> new String[] { normalizedModeCode };
        };
    }

    private String inwardConveyanceLabel(String modeCode) {
        return switch (firstNonBlank(modeCode, "").trim()) {
            case "1" -> "Inward Voyage Number";
            case "4" -> "Inward Flight Number";
            default -> "Conveyance Reference Number";
        };
    }

    private String outwardConveyanceLabel(String modeCode) {
        return switch (firstNonBlank(modeCode, "").trim()) {
            case "1" -> "Outward Voyage Number";
            case "4" -> "Outward Flight Number";
            default -> "Conveyance Reference Number";
        };
    }

    private String inwardIdentifierLabel(String modeCode) {
        return switch (firstNonBlank(modeCode, "").trim()) {
            case "1" -> "Inward Vessel Name";
            case "4" -> "Inward Aircraft Registration Number";
            case "3" -> "Vehicle Licence/Registration Number";
            default -> "Transport Identifier";
        };
    }

    private String outwardIdentifierLabel(String modeCode) {
        return switch (firstNonBlank(modeCode, "").trim()) {
            case "1" -> "Outward Vessel Name";
            case "4" -> "Aircraft Registration Number";
            case "3" -> "Vehicle Licence/Registration Number";
            default -> "Transport Identifier";
        };
    }

    private String inwardReferenceNumberLabel(String modeCode) {
        return switch (firstNonBlank(modeCode, "").trim()) {
            case "1" -> "Inward Ocean Bill of Lading Number";
            case "4" -> "Inward Master Air Waybill";
            default -> "MAWB/UCR/OBL Number";
        };
    }

    private String outwardReferenceNumberLabel(String modeCode) {
        return switch (firstNonBlank(modeCode, "").trim()) {
            case "1" -> "Outward Ocean Bill of Lading Number";
            case "4" -> "Outward Master Air Waybill";
            default -> "MAWB/UCR/OBL Number";
        };
    }

    private void fillTnpTransportEquipmentDetails(JsonNode cargo) {
        JsonNode transportEquipments = cargo.path("transportEquipment");
        if (!transportEquipments.isArray() || transportEquipments.isEmpty()) {
            return;
        }

        Locator containerDetailsSection = resolveTnpContainerDetailsSectionOrNull();
        if (containerDetailsSection == null) {
            logFieldMappingWarning("TNP container details UI section was not visible while JSON values were present.");
            return;
        }

        for (int index = 0; index < transportEquipments.size(); index++) {
            JsonNode transportEquipment = transportEquipments.get(index);
            if (index > 0) {
                clickTnpAddButtonInScope(containerDetailsSection, "ADD");
                page.waitForTimeout(500);
            }

            int baseOccurrence = index * 4;
            String equipmentId = normalize(text(transportEquipment, "equipmentID"));
            String sizeTypeCode = text(transportEquipment, "sizeTypeCode");
            String equipmentWeight = text(transportEquipment, "equipmentWeightMeasureNumeric");
            String sealNumber = text(transportEquipment.path("transportEquipmentSeal"), "sealID");

            logFieldMappingInfo("TNP container row " + (index + 1)
                    + " -> equipmentID='" + firstNonBlank(equipmentId, "N/A")
                    + "', sizeTypeCode='" + firstNonBlank(sizeTypeCode, "N/A")
                    + "', weight='" + firstNonBlank(equipmentWeight, "N/A")
                    + "', seal='" + firstNonBlank(sealNumber, "N/A") + "'");

            fillNthFieldInScopeIfPresent(containerDetailsSection, baseOccurrence, equipmentId);
            fillNthLookupFieldInScopeIfPresent(containerDetailsSection, baseOccurrence + 1, sizeTypeCode, sizeTypeCode);
            fillNthFieldInScopeIfPresent(containerDetailsSection, baseOccurrence + 2, equipmentWeight);
            fillNthFieldInScopeIfPresent(containerDetailsSection, baseOccurrence + 3, sealNumber);
        }
    }

    private String[] cargoTypeSearchHints(String cargoType) {
        String normalizedCargoType = firstNonBlank(cargoType, "").trim();
        return switch (normalizedCargoType) {
            case "5" -> new String[] { "5 - Other non-containerized", "Other non-containerized", "5", "OTHER", "Other" };
            default -> new String[] { normalizedCargoType };
        };
    }

    private String[] cargoTypeRenderedHints(String cargoType) {
        String normalizedCargoType = firstNonBlank(cargoType, "").trim();
        return switch (normalizedCargoType) {
            case "5" -> new String[] { "5 - Other non-containerized", "Other non-containerized" };
            default -> cargoTypeSearchHints(cargoType);
        };
    }

    private boolean waitForStrictRenderedFieldValue(Locator field, int timeoutMs, String... expectedValues) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            if (strictRenderedFieldValueMatches(readRenderedFieldValue(field), expectedValues)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean strictRenderedFieldValueMatches(String actualValue, String... expectedValues) {
        String normalizedActual = normalize(actualValue);
        if (normalizedActual.isBlank() || expectedValues == null || expectedValues.length == 0) {
            return false;
        }

        for (String expectedValue : expectedValues) {
            String normalizedExpected = normalize(expectedValue);
            if (normalizedExpected.isBlank()) {
                continue;
            }
            if (normalizedActual.equalsIgnoreCase(normalizedExpected)
                    || normalizedActual.contains(normalizedExpected)) {
                return true;
            }
        }
        return false;
    }

    private void syncLookupComponentValue(String selector, String labelText, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            page.evaluate("""
                    args => {
                        const normalize = input => (input || '').replace(/\\s+/g, ' ').trim().toUpperCase();
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
                        const matches = Array.from(document.querySelectorAll(args.selector));
                        const target = matches.find(element =>
                                normalize(element.innerText || element.textContent).includes(normalize(args.labelText)))
                            || matches.find(isVisible)
                            || matches[0];
                        if (!target) {
                            return false;
                        }

                        const componentHost = target.closest?.(
                                'app-cargo-type-lookup, app-transport-mode-lookup, app-location-lookup, app-loading-port-lookup, app-country-code-lookup, app-lookup, app-dropdown, ng-select, [formcontrolname]')
                            || target;

                        if (typeof window.ng !== 'undefined' && typeof window.ng.getComponent === 'function') {
                            const component = window.ng.getComponent(componentHost);
                            if (component) {
                                const option = Array.isArray(component.allOptions)
                                    ? component.allOptions.find(candidate =>
                                        normalize(candidate?.code) === normalize(args.value)
                                        || normalize(candidate?.description) === normalize(args.value)
                                        || normalize(`${candidate?.code} ${candidate?.description}`) === normalize(args.value))
                                    : null;
                                if (option) {
                                    component._value = option.code;
                                    const visibleText = [option.code, option.description].filter(Boolean).join(' - ');
                                    component.inputDisplayValue = visibleText || option.description || option.code;
                                    component.displayValue = visibleText || option.description || option.code;
                                    const componentField = componentHost.querySelector?.('input, textarea, select');
                                    if (componentField) {
                                        componentField.value = visibleText || option.description || option.code;
                                        componentField.dispatchEvent(new Event('input', { bubbles: true }));
                                        componentField.dispatchEvent(new Event('change', { bubbles: true }));
                                        componentField.dispatchEvent(new Event('blur', { bubbles: true }));
                                    }
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

                        const field = target.matches?.('input, textarea, select')
                            ? target
                            : target.querySelector('input, textarea, select')
                                || componentHost.querySelector?.('input, textarea, select');
                        if (!field) {
                            return false;
                        }

                        field.value = args.value;
                        field.dispatchEvent(new Event('input', { bubbles: true }));
                        field.dispatchEvent(new Event('change', { bubbles: true }));
                        field.dispatchEvent(new Event('blur', { bubbles: true }));
                        return true;
                    }
                    """, Map.of(
                    "selector", selector,
                    "labelText", labelText,
                    "value", value));
        } catch (Exception ignored) {
        }
    }

    private void fillTnpSingleItem(JsonNode item, JsonNode formMetaData, int index) {
        logFieldMappingInfo("TNP item " + (index + 1)
                + " -> hsCode='" + firstNonBlank(text(item, "itemHarmonizedSystemCode"), "N/A")
                + "', description='" + firstNonBlank(text(item, "goodsDescription"), "N/A")
                + "', inwardRef='" + firstNonBlank(text(item, "inMAWBOUCROBLNumber"), "N/A")
                + "', outwardRef='" + firstNonBlank(text(item, "outMAWBOUCROBLNumber"), "N/A") + "'");

        Locator itemDetailsSection = resolveCurrentTnpItemDetailsSection();
        waitForAnyVisibleText(
                "HS Code",
                "Description",
                "COO",
                "HS Quantity");

        fillTnpFieldIfPresentByLabels(
                text(item, "inHawbHucrHblNumber"),
                "InHAWB/HUCR/HBL Number");
        fillTnpFieldIfPresentByLabels(
                text(item, "outHawbHucrHblNumber"),
                "OutHAWB/HUCR/HBL Number");
        fillTnpFieldIfPresentByLabels(
                text(item, "inMAWBOUCROBLNumber"),
                "InMAWB/OUCRO/BL Number",
                "InMAWB/UCR/OBL Number",
                "Inward MAWB/UCR/OBL Number");
        fillTnpFieldIfPresentByLabels(
                text(item, "outMAWBOUCROBLNumber"),
                "OutMAWB/OUCRO/BL Number",
                "OutMAWB/UCR/OBL Number",
                "Outward MAWB/UCR/OBL Number");
        if (scopeContainsText(itemDetailsSection, "S No.")) {
            fillTnpReferenceFieldByOccurrence(itemDetailsSection, 1, text(item, "inHawbHucrHblNumber"));
            fillTnpReferenceFieldByOccurrence(itemDetailsSection, 2, text(item, "outHawbHucrHblNumber"));
            fillTnpReferenceFieldByOccurrence(itemDetailsSection, 3, text(item, "inMAWBOUCROBLNumber"));
            fillTnpReferenceFieldByOccurrence(itemDetailsSection, 4, text(item, "outMAWBOUCROBLNumber"));
        }
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS Code",
                0,
                text(item, "itemHarmonizedSystemCode"),
                text(item, "itemHarmonizedSystemCode"));
        fillFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "Description",
                0,
                text(item, "goodsDescription"));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "COO",
                0,
                text(item, "originCountry"),
                text(item, "originCountry"));
        fillFieldAfterScopeLabelIfPresent(itemDetailsSection, "Brand", 0, text(item, "brandName"));
        fillFieldAfterScopeLabelIfPresent(itemDetailsSection, "Model", 0, text(item, "modelDescription"));
        fillLookupFieldAfterScopeLabelIfPresent(itemDetailsSection, "HS Type", 0, text(item, "hsType"), text(item, "hsType"));
        fillLookupFieldAfterScopeLabelIfPresent(itemDetailsSection, "Duty Type", 0, text(item, "dutyType"), text(item, "dutyType"));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS Transhipment CA",
                0,
                firstNonBlank(text(item, "hsTranshipmentCa"), text(item, "hsCa")),
                firstNonBlank(text(item, "hsTranshipmentCa"), text(item, "hsCa")));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS CA",
                0,
                firstNonBlank(text(item, "hsTranshipmentCa"), text(item, "hsCa")),
                firstNonBlank(text(item, "hsTranshipmentCa"), text(item, "hsCa")));

        fillTnpItemQuantityDetails(item.path("itemQuantity"));
        fillTnpItemValueDetails(item.path("transactionValue"));

        if (item.path("dangerousGoodsIndicator").asBoolean(false)) {
            setCheckboxByLabel("DG", true);
        }
        if (item.path("unbrandedIndicator").asBoolean(false)) {
            setCheckboxByLabel("Unbranded", true);
        }
        if (formMetaData.path("shippingMarksIsActive").path(index).asBoolean(false)) {
            fillShippingMarks(firstArrayItem(item.path("shippingMarksInformation")));
        }
    }

    private void fillTnpReferenceFieldByOccurrence(Locator itemDetailsSection, int occurrence, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        fillNthFieldInScopeIfPresent(itemDetailsSection, occurrence, value);
    }

    private boolean scopeContainsText(Locator scope, String expectedText) {
        try {
            return Boolean.TRUE.equals(scope.evaluate(
                    "(element, text) => (element.innerText || element.textContent || '').includes(text)",
                    expectedText));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void fillTnpItemQuantityDetails(JsonNode itemQuantity) {
        Locator itemQuantitySection = resolveCurrentTnpItemQuantitySection();
        fillQuantityRowInScope(itemQuantitySection, "Dutiable Quantity", itemQuantity.path("dutiableQuantity"));
        fillQuantityRowInScope(itemQuantitySection, "Total Dutiable Qty", itemQuantity.path("totalDutiableQuantity"));
        fillQuantityRowInScope(itemQuantitySection, "Total Dutiable Quantity", itemQuantity.path("totalDutiableQuantity"));
        fillQuantityRowInScope(
                itemQuantitySection,
                "HS Quantity",
                firstNonBlankNode(itemQuantity.path("hsQuantity"), itemQuantity.path("harmonizedSystemQuantity")));
        fillFieldAfterScopeLabelIfPresent(itemQuantitySection, "Alcohol %", 0, text(itemQuantity, "alcoholPercent"));
    }

    private void fillTnpItemValueDetails(JsonNode transactionValue) {
        Locator itemQuantitySection = resolveCurrentTnpItemQuantitySection();
        String cifFobValue = normalizeNumericForEntry(text(transactionValue, "itemCIFFOBValue"));
        fillFieldAfterScopeLabelIfPresent(itemQuantitySection, "CIF/FOB Value (SGD)", 0, cifFobValue);
        fillFieldAfterScopeLabelIfPresent(itemQuantitySection, "Item CIF/FOB Value (SGD)", 0, cifFobValue);
    }

    private void fillStrictTnpPartyRow(String rowLabel, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        String partyName = normalize(text(identityNode.path("partyName"), "name"));
        String partyId = normalize(text(identityNode.path("partyIdentification"), "id"));

        if (partyName.isBlank() && partyId.isBlank()) {
            clearPartyRow(rowLabel);
            return;
        }

        fillPartyRowIfPresent(rowLabel, partyNode);

        if (partyName.isBlank()) {
            clearPartyNameField(rowLabel);
        }
        if (partyId.isBlank()) {
            clearPartyIdField(rowLabel);
        }
    }

    private void fillStrictTnpPartyCard(String title, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        JsonNode addressNode = partyNode.path("address");

        String name = normalize(text(identityNode.path("partyName"), "name"));
        String partyId = normalize(text(identityNode.path("partyIdentification"), "id"));
        String addressLine1 = arrayText(addressNode.path("addressLine").path("line"), 0);
        String addressLine2 = arrayText(addressNode.path("addressLine").path("line"), 1);
        String city = text(addressNode, "cityName");
        String postalCode = firstNonBlank(text(addressNode, "postalZone"), text(addressNode, "countrySubentityCode"));
        String countrySubentity = text(addressNode, "countrySubentity");
        String compactAddress = joinNonBlank(", ", addressLine1, addressLine2);
        String otherAddressDetails = joinNonBlank(", ", city, postalCode, countrySubentity);
        String countryCode = text(addressNode, "countryCode");

        if (name.isBlank()
                && partyId.isBlank()
                && compactAddress.isBlank()
                && otherAddressDetails.isBlank()
                && (countryCode == null || countryCode.isBlank())) {
            clearTnpPartyCard(title);
            return;
        }

        fillTnpPartyCardIfPresent(title, partyNode);

        Locator card = resolveTnpPartyCard(title);
        if (card == null) {
            return;
        }

        if (name.isBlank()) {
            clearNthFieldInScopeIfPresent(card, 0);
        }
        if (partyId.isBlank()) {
            clearFieldAfterScopeLabelIfPresent(card, "UEN", 0);
        }
        if (compactAddress.isBlank()) {
            clearFieldAfterScopeLabelIfPresent(card, "Address", 0);
        }
        if (otherAddressDetails.isBlank()) {
            clearFieldAfterScopeLabelIfPresent(card, "Other address details", 0);
        }
        if (countryCode == null || countryCode.isBlank()) {
            clearFieldAfterScopeLabelIfPresent(card, "Country Code", 0);
        }
    }

    private void clearTnpPartyCard(String title) {
        Locator card = resolveTnpPartyCard(title);
        if (card == null) {
            return;
        }

        clearNthFieldInScopeIfPresent(card, 0);
        clearFieldAfterScopeLabelIfPresent(card, "UEN", 0);
        clearFieldAfterScopeLabelIfPresent(card, "Address", 0);
        clearFieldAfterScopeLabelIfPresent(card, "Other address details", 0);
        clearFieldAfterScopeLabelIfPresent(card, "Country Code", 0);
    }

    private void fillTnpPartyCardIfPresent(String title, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        JsonNode addressNode = partyNode.path("address");

        String name = normalize(text(identityNode.path("partyName"), "name"));
        String partyId = normalize(text(identityNode.path("partyIdentification"), "id"));
        String addressLine1 = arrayText(addressNode.path("addressLine").path("line"), 0);
        String addressLine2 = arrayText(addressNode.path("addressLine").path("line"), 1);
        String city = text(addressNode, "cityName");
        String postalCode = firstNonBlank(text(addressNode, "postalZone"), text(addressNode, "countrySubentityCode"));
        String countrySubentity = text(addressNode, "countrySubentity");
        String compactAddress = joinNonBlank(", ", addressLine1, addressLine2);
        String otherAddressDetails = joinNonBlank(", ", city, postalCode, countrySubentity);
        String countryCode = text(addressNode, "countryCode");

        if ((name == null || name.isBlank())
                && (partyId == null || partyId.isBlank())
                && compactAddress.isBlank()
                && otherAddressDetails.isBlank()
                && (countryCode == null || countryCode.isBlank())) {
            return;
        }

        Locator card = resolveTnpPartyCard(title);
        if (card == null) {
            logFieldMappingWarning("TNP party card '" + title + "' was not visible while JSON values were present.");
            return;
        }

        logFieldMappingInfo("TNP party card '" + title + "' -> name='" + firstNonBlank(name, "N/A")
                + "', uen='" + firstNonBlank(partyId, "N/A")
                + "', address='" + firstNonBlank(compactAddress, "N/A")
                + "', otherAddressDetails='" + firstNonBlank(otherAddressDetails, "N/A")
                + "', countryCode='" + firstNonBlank(countryCode, "N/A") + "'");

        fillNthLookupFieldInScopeByClickOnlyIfPresent(card, 0, name, name, partyId);
        fillFieldAfterScopeLabelIfPresent(card, "UEN", 0, partyId);
        fillFieldAfterScopeLabelIfPresent(card, "Address", 0, compactAddress);
        fillFieldAfterScopeLabelIfPresent(card, "Other address details", 0, otherAddressDetails);
        fillLookupFieldAfterScopeLabelIfPresent(card, "Country Code", 0, countryCode, countryCode);
    }

    private JsonNode partyIdentityNode(JsonNode partyNode) {
        JsonNode partyDetail = partyNode.path("partyDetail");
        return isMissingOrEmpty(partyDetail) ? partyNode : partyDetail;
    }

    private Locator resolveTnpPartyCard(String title) {
        String escapedTitle = toXpathLiteralLocal(title);
        Locator cards = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1])");
        int count = cards.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = cards.nth(index);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        return null;
    }

    private Locator resolveTnpContainerDetailsSectionOrNull() {
        Locator section = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), 'Container Details')])[last()]"
                        + "/ancestor::*[.//*[normalize-space(translate(., '*', ''))='Container Number']"
                        + " and .//*[normalize-space(translate(., '*', ''))='Size / Type']"
                        + " and .//*[contains(normalize-space(translate(., '*', '')), 'Weight')]"
                        + " and .//*[normalize-space(translate(., '*', ''))='Seal Number']"
                        + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
        int count = section.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = section.nth(index);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        return null;
    }

    private Locator resolveCurrentTnpItemDetailsSection() {
        Locator section = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Item Details'])[last()]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]");
        int count = section.count();
        for (int index = count - 1; index >= 0; index--) {
            Locator candidate = section.nth(index);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        throw new IllegalStateException("TNP Item Details section was not visible.");
    }

    private Locator resolveCurrentTnpItemQuantitySection() {
        String[] titles = new String[] { "Item Quantity & Value", "Item Quantity & value", "Item Quantity" };
        for (String title : titles) {
            Locator section = page.locator(
                    "xpath=(//*[normalize-space(translate(., '*', ''))=" + toXpathLiteralLocal(title) + "])[last()]"
                            + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]");
            int count = section.count();
            for (int index = count - 1; index >= 0; index--) {
                Locator candidate = section.nth(index);
                if (candidate.isVisible()) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("TNP Item Quantity section was not visible.");
    }

    private void clickTnpAddItemButton() {
        clickTnpPageButton("ADD ITEM", "Add Item");
    }

    private void clickTnpPageButton(String... candidates) {
        for (String candidate : candidates) {
            Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(candidate).setExact(true));
            if (button.count() > 0 && button.first().isVisible()) {
                button.first().click(new Locator.ClickOptions().setForce(true));
                return;
            }
        }
        for (String candidate : candidates) {
            Locator button = page.locator("button:has-text('" + candidate + "'), [role='button']:has-text('" + candidate + "')").first();
            if (button.count() > 0 && button.isVisible()) {
                button.click(new Locator.ClickOptions().setForce(true));
                return;
            }
        }
        throw new IllegalStateException("Unable to click TNP page button. Candidates: " + String.join(", ", candidates));
    }

    private void clickTnpAddButtonInScope(Locator scope, String exactText) {
        int count = scope.locator("button, [role='button'], a, input[type='button'], input[type='submit'], div, span").count();
        for (int index = 0; index < count; index++) {
            Locator candidate = scope.locator("button, [role='button'], a, input[type='button'], input[type='submit'], div, span").nth(index);
            if (!candidate.isVisible()) {
                continue;
            }

            String text = normalize(candidate.innerText()).toUpperCase();
            String ariaLabel = normalize(candidate.getAttribute("aria-label")).toUpperCase();
            String title = normalize(candidate.getAttribute("title")).toUpperCase();
            String value = normalize(candidate.getAttribute("value")).toUpperCase();
            if (exactText.equalsIgnoreCase(text)
                    || exactText.equalsIgnoreCase(ariaLabel)
                    || exactText.equalsIgnoreCase(title)
                    || exactText.equalsIgnoreCase(value)) {
                candidate.click(new Locator.ClickOptions().setForce(true));
                return;
            }
        }
        throw new IllegalStateException("TNP scoped button was not visible: " + exactText);
    }

    private void fillTnpFieldInSectionIfPresent(String sectionTitle, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        logFieldMappingInfo("TNP field -> section='" + sectionTitle + "', label='" + label + "', json='" + value + "'");
        fillFieldInSectionIfPresent(sectionTitle, label, value);
    }

    private void fillTnpDateFieldInSectionIfPresent(String sectionTitle, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        logFieldMappingInfo("TNP date field -> section='" + sectionTitle + "', label='" + label + "', json='" + value + "'");
        fillDateFieldInSectionIfPresent(sectionTitle, label, value);
    }

    private void fillTnpLookupFieldInSectionIfPresent(String sectionTitle, String label, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        logFieldMappingInfo("TNP lookup field -> section='" + sectionTitle + "', label='" + label + "', json='" + value + "'");
        fillLookupFieldInSectionIfPresent(sectionTitle, label, value, suggestionHints);
    }

    private void fillTnpNthLookupFieldInSectionIfPresent(String sectionTitle, int occurrence, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }
        logFieldMappingInfo("TNP lookup field -> section='" + sectionTitle + "', occurrence=" + occurrence + ", json='" + value + "'");
        fillNthLookupFieldInSectionIfPresent(sectionTitle, occurrence, value, suggestionHints);
    }

    private void fillTnpFieldIfPresentByLabels(String value, String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String label : labels) {
            Locator field = resolveFieldByLabelOrNull(label, 0);
            if (field != null) {
                logFieldMappingInfo("TNP field -> label='" + label + "', json='" + value + "'");
                focusAndType(field, value, false);
                return;
            }
        }
        logFieldMappingWarning("TNP UI field not found for labels '" + String.join(", ", labels)
                + "' while JSON value was '" + value + "'.");
    }

    private void fillTnpLookupFieldIfPresentByLabels(String value, String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String label : labels) {
            Locator field = resolveFieldByLabelOrNull(label, 0);
            if (field != null) {
                logFieldMappingInfo("TNP lookup field -> label='" + label + "', json='" + value + "'");
                focusAndType(field, value, true, value);
                return;
            }
        }
        logFieldMappingWarning("TNP lookup UI field not found for labels '" + String.join(", ", labels)
                + "' while JSON value was '" + value + "'.");
    }

    @Override
    protected String arrayText(JsonNode arrayNode, int index) {
        if (arrayNode == null || !arrayNode.isArray() || index < 0 || index >= arrayNode.size()) {
            return null;
        }
        JsonNode valueNode = arrayNode.get(index);
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return null;
        }
        String value = normalize(valueNode.asText());
        return value == null || value.isBlank() ? null : value;
    }

    private String joinNonBlank(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            String normalizedValue = normalize(value);
            if (!normalizedValue.isBlank()) {
                parts.add(normalizedValue);
            }
        }
        return String.join(delimiter, parts);
    }

    private String toXpathLiteralLocal(String value) {
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
        String[] parts = value.split("'");
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                builder.append(", \"'\", ");
            }
            builder.append("'").append(parts[index]).append("'");
        }
        builder.append(")");
        return builder.toString();
    }

    private void logLookupFieldResult(
            String sectionTitle,
            String label,
            String expectedJsonValue,
            String... expectedUiValues) {
        try {
            var field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
            if (field == null) {
                logFieldMappingWarning("Transport field '" + label + "' in section '" + sectionTitle
                        + "' was not found while JSON value was '" + expectedJsonValue + "'.");
                return;
            }

            String renderedValue = firstNonBlank(readRenderedFieldValue(field), "N/A");
            boolean matched = waitForStrictRenderedFieldValue(field, 1200, expectedUiValues);
            logFieldMappingInfo("Transport field '" + label + "' -> json='"
                    + firstNonBlank(expectedJsonValue, "N/A")
                    + "', ui='"
                    + renderedValue
                    + "', matched="
                    + matched);
        } catch (Exception exception) {
            logFieldMappingWarning("Unable to verify transport field '" + label + "' in section '" + sectionTitle
                    + "': " + exception.getMessage());
        }
    }
}
