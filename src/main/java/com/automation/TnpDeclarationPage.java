package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;

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
        String cargoType = text(data.path("cargo"), "cargoPackingType");
        String[] cargoTypeHints = cargoTypeHints(cargoType);
        var cargoTypeField = resolveFieldByLabelInSectionOrNull("Declaration Info", "Cargo Type", 0);
        if (cargoTypeField != null) {
            focusAndType(cargoTypeField, cargoTypeHints[0], true, cargoTypeHints);
            if (!waitForAnyRenderedFieldValue(cargoTypeField, 1500, cargoTypeHints)) {
                syncLookupComponentValue(
                        "app-cargo-type-lookup[formcontrolname='cargoPackingType'], [formcontrolname='cargoPackingType']",
                        "Cargo Type",
                        cargoType);
            }
        } else {
            logFieldMappingWarning("UI lookup field not found for section 'Declaration Info' and label 'Cargo Type' while JSON value was '"
                    + cargoType + "'.");
        }
        logLookupFieldResult(
                "Declaration Info",
                "Cargo Type",
                cargoType,
                cargoTypeHints);

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
        JsonNode outwardTransportMeans = outwardTransport.path("transportMeans");
        JsonNode outwardTransportMode = outwardTransportMeans.path("transportMode");
        JsonNode additionalVesselInformation = outwardTransport.path("additionalVesselInformation");

        fillFieldInSectionByAnyLabelIfPresent(
                "Outward Transport Means",
                text(outwardTransportMode, "conveyanceReferenceNumber"),
                "Outward Flight Number",
                "Flight Number",
                "Conveyance Reference Number",
                "Outward Voyage Number");
        fillFieldInSectionByAnyLabelIfPresent(
                "Outward Transport Means",
                text(outwardTransportMode, "transportIdentifier"),
                "Transport Identifier",
                "Outward Aircraft Registration Number",
                "Outward Vessel Name",
                "Outward Vehicle/Vessel Registration Number",
                "Vehicle Licence/Registration Number");
        fillFieldInSectionByAnyLabelIfPresent(
                "Outward Transport Means",
                firstNonBlank(
                        text(outwardTransportMeans, "mawboucroblNumber"),
                        text(outwardTransport, "mawboucroblNumber")),
                "Outward Master Air Waybill",
                "Outward Ocean Bill of Lading Number",
                "Outward Ocean Bill Of Lading Number",
                "MAWB/OUCR/OBL Number");
        fillDateFieldInSectionIfPresent(
                "Outward Transport Means",
                "Departure Date",
                formatUiDate(text(outwardTransport, "departureDate")));
        fillLookupFieldInSectionIfPresent(
                "Outward Transport Means",
                "Discharge Port",
                text(outwardTransport, "dischargePort"),
                text(outwardTransport, "dischargePort"));
        fillLookupFieldInSectionIfPresent(
                "Outward Transport Means",
                "Country of Final Destination",
                text(outwardTransport, "finalDestinationCountry"),
                text(outwardTransport, "finalDestinationCountry"));
        fillLookupFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Vessel Type",
                text(additionalVesselInformation, "vesselType"),
                text(additionalVesselInformation, "vesselType"));
        fillFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Towing Vessel Voyage Number",
                text(additionalVesselInformation.path("towingVessel"), "vesselID"));
        fillFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Towing Vessel Name",
                text(additionalVesselInformation.path("towingVessel"), "vesselName"));
    }

    @Override
    protected void fillDeclarationSpecificPartyInfo(JsonNode data, JsonNode party) {
        fillPartyRowIfPresent("Declaring Agent", party.path("declaringAgentParty"));
        fillPartyRowIfPresent("Handling Agent", party.path("handlingAgentParty"));
        fillPartyRowIfPresent("Outward Carrier", party.path("outwardCarrierAgentParty"));
        fillPartyRowIfPresent("Consignee", party.path("consigneeParty"));
    }

    @Override
    protected void fillDeclarationSpecificItemInfo(JsonNode data, JsonNode item) {
        fillFieldIfPresent("Inward MAWB/UCR/OBL Number", text(item, "inMAWBOUCROBLNumber"));
        fillFieldIfPresent("Outward MAWB/UCR/OBL Number", text(item, "outMAWBOUCROBLNumber"));
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

    private String[] cargoTypeHints(String cargoType) {
        String normalizedCargoType = firstNonBlank(cargoType, "").trim();
        return switch (normalizedCargoType) {
            case "5" -> new String[] { "5 - Other non-containerized", "Other non-containerized", "5", "OTHER", "Other" };
            default -> new String[] { normalizedCargoType };
        };
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
            boolean matched = waitForAnyRenderedFieldValue(field, 1200, expectedUiValues);
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
