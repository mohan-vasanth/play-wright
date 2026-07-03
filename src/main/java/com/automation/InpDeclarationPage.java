package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InpDeclarationPage extends IptDeclarationPage {

    public InpDeclarationPage(Page page) {
        super(page);
    }

    @Override
    protected String declarationFlowLabel() {
        return "INP";
    }

    @Override
    protected void validateDeclarationPayload(JsonNode data) {
        if (!DeclarationPayloads.matchesDeclarationFamily(data, "INP")) {
            throw new IllegalArgumentException("Payload does not match an INP declaration.");
        }
    }

    @Override
    protected void fillDeclarationSpecificPartyInfo(JsonNode data, JsonNode party) {
        fillPartyRowIfPresent("Exporter", party.path("exporterParty"));
        fillPartyRowIfPresent("Outward Carrier", party.path("outwardCarrierAgentParty"));
        fillPartyRowIfPresent("Declaring Agent", party.path("declaringAgentParty"));
        fillInpPartyCardIfPresent("Consignee", party.path("consigneeParty"));
        fillClaimantPartyIfPresent(party.path("claimantParty"));
    }

    @Override
    protected void fillDeclarationSpecificShipmentInfo(JsonNode data) {
        JsonNode outwardTransportMode = data.path("transport")
                .path("outwardTransport")
                .path("transportMeans")
                .path("transportMode");
        String outwardModeCode = text(outwardTransportMode, "modeCode");
        if (outwardModeCode == null || outwardModeCode.isBlank()) {
            logFieldMappingInfo("INP shipment transport JSON -> outwardTransportMode='N/A'; field skipped because selected JSON has no outward transport mode.");
            return;
        }
        String[] outwardModeHints = transportModeHints(outwardModeCode);

        logFieldMappingInfo("INP shipment transport JSON -> outwardTransportMode='"
                + firstNonBlank(outwardModeCode, "N/A") + "'");

        fillLookupFieldInSectionIfPresent(
                "Declaration Info",
                "Outward Transport Mode",
                outwardModeCode,
                outwardModeHints);
        syncLookupComponentValue(
                "app-transport-mode-lookup[formcontrolname='modeCode']",
                "Outward Transport Mode",
                outwardModeCode);
        logLookupFieldResult(
                "Declaration Info",
                "Outward Transport Mode",
                outwardModeCode,
                outwardModeHints);
    }

    @Override
    protected void fillDeclarationSpecificTransportInfo(JsonNode data) {
        JsonNode outwardTransport = data.path("transport").path("outwardTransport");
        if (outwardTransport == null || outwardTransport.isMissingNode() || outwardTransport.isNull()) {
            logFieldMappingInfo("INP outward transport JSON -> section absent in selected JSON; outward transport details skipped.");
            return;
        }
        JsonNode outwardTransportMeans = outwardTransport.path("transportMeans");
        JsonNode outwardTransportMode = outwardTransportMeans.path("transportMode");
        JsonNode additionalVesselInformation = outwardTransport.path("additionalVesselInformation");

        logFieldMappingInfo("INP outward transport JSON -> conveyanceReferenceNumber='"
                + firstNonBlank(text(outwardTransportMode, "conveyanceReferenceNumber"), "N/A")
                + "', transportIdentifier='"
                + firstNonBlank(text(outwardTransportMode, "transportIdentifier"), "N/A")
                + "', mawboucroblNumber='"
                + firstNonBlank(
                text(outwardTransportMeans, "mawboucroblNumber"),
                text(outwardTransport, "mawboucroblNumber"),
                "N/A")
                + "', departureDate='"
                + firstNonBlank(formatUiDate(text(outwardTransport, "departureDate")), "N/A")
                + "', dischargePort='"
                + firstNonBlank(text(outwardTransport, "dischargePort"), "N/A")
                + "', finalDestinationCountry='"
                + firstNonBlank(text(outwardTransport, "finalDestinationCountry"), "N/A")
                + "'");

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
                "Vehicle/Vessel Registration Number",
                "Vehicle Licence/Registration Number");
        fillFieldInSectionByAnyLabelIfPresent(
                "Outward Transport Means",
                firstNonBlank(
                        text(outwardTransportMeans, "mawboucroblNumber"),
                        text(outwardTransport, "mawboucroblNumber")),
                "Outward Master Air Waybill",
                "Master Air Waybill",
                "MAWB/UCR/OBL Number",
                "MAWB/OUCR/OBL Number",
                "Outward Ocean Bill of Lading Number",
                "Outward Ocean Bill Of Lading Number");
        fillDateFieldInSectionIfPresent(
                "Outward Transport Means",
                "Departure Date",
                formatUiDate(text(outwardTransport, "departureDate")));
        fillLookupFieldInSectionIfPresent(
                "Outward Transport Means",
                "Discharge Port",
                text(outwardTransport, "dischargePort"),
                text(outwardTransport, "dischargePort"));
        syncLookupComponentValue(
                "app-loading-port-lookup[formcontrolname='dischargePort']",
                "Discharge Port",
                text(outwardTransport, "dischargePort"));
        fillLookupFieldInSectionIfPresent(
                "Outward Transport Means",
                "Country of Final Destination",
                text(outwardTransport, "finalDestinationCountry"),
                text(outwardTransport, "finalDestinationCountry"));
        syncLookupComponentValue(
                "app-country-code-lookup[formcontrolname='finalDestinationCountry']",
                "Country of Final Destination",
                text(outwardTransport, "finalDestinationCountry"));

        if (!sectionVisible("Additional Vessel Information")) {
            logFieldMappingInfo("INP outward transport -> 'Additional Vessel Information' section not visible; optional vessel fields skipped.");
            return;
        }

        fillLookupFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Vessel Type",
                text(additionalVesselInformation, "vesselType"),
                text(additionalVesselInformation, "vesselType"));
        syncLookupComponentValue(
                "[formcontrolname='vesselType']",
                "Vessel Type",
                text(additionalVesselInformation, "vesselType"));
        fillFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Net Register Tonnage",
                text(additionalVesselInformation, "netRegisterTonnage"));
        fillLookupFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Next Port of Call",
                text(additionalVesselInformation, "loadingNextPort"),
                text(additionalVesselInformation, "loadingNextPort"));
        syncLookupComponentValue(
                "[formcontrolname='loadingNextPort']",
                "Next Port of Call",
                text(additionalVesselInformation, "loadingNextPort"));
        fillLookupFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Final Port of Call",
                text(additionalVesselInformation, "loadingFinalPort"),
                text(additionalVesselInformation, "loadingFinalPort"));
        syncLookupComponentValue(
                "[formcontrolname='loadingFinalPort']",
                "Final Port of Call",
                text(additionalVesselInformation, "loadingFinalPort"));
        fillFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Vessel Nationality",
                firstNonBlank(
                        text(additionalVesselInformation, "vesselNationality"),
                        text(additionalVesselInformation, "vesselNationalality")));
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
    protected void fillDeclarationSpecificItemInfo(JsonNode data, JsonNode item) {
        fillFieldIfPresent("Outward HAWB", text(item, "outHawbHucrHblNumber"));
    }

    private void syncLookupComponentValue(String selector, String labelText, String value) {
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
                    """, Map.of(
                    "selector", selector,
                    "labelText", labelText,
                    "value", value));
        } catch (Exception ignored) {
        }
    }

    private boolean sectionVisible(String sectionTitle) {
        try {
            return resolveSection(sectionTitle) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void logLookupFieldResult(
            String sectionTitle,
            String label,
            String expectedJsonValue,
            String... expectedUiValues) {
        try {
            Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
            if (field == null) {
                logFieldMappingWarning("Transport field '" + label + "' in section '" + sectionTitle
                        + "' was not found while JSON value was '" + expectedJsonValue + "'.");
                return;
            }

            String renderedValue = firstNonBlank(readRenderedFieldValue(field), "N/A");
            boolean matched = waitForAnyRenderedFieldValue(field, 1200, expectedUiValues);
            logFieldMappingInfo("Transport field '" + label + "' -> json='"
                    + firstNonBlank(expectedJsonValue, "N/A")
                    + "', uiRendered='"
                    + renderedValue
                    + "', matched="
                    + matched);
            if (!matched) {
                logFieldMappingWarning("Transport field '" + label + "' did not match JSON value. Expected one of: '"
                        + String.join("' | '", expectedUiValues)
                        + "', Actual: '"
                        + renderedValue
                        + "'");
            }
        } catch (Exception exception) {
            logFieldMappingWarning("Transport field '" + label + "' verification failed: " + exception.getMessage());
        }
    }

    private String[] transportModeHints(String modeCode) {
        List<String> hints = new ArrayList<>();
        if (modeCode != null && !modeCode.isBlank()) {
            hints.add(modeCode.trim());
        }

        String normalizedMode = modeCode == null ? "" : modeCode.trim();
        switch (normalizedMode) {
            case "1" -> {
                hints.add("1 - Sea");
                hints.add("SEA");
                hints.add("Sea");
            }
            case "3" -> {
                hints.add("3 - Road");
                hints.add("ROAD");
                hints.add("Road");
            }
            case "4" -> {
                hints.add("4 - Air");
                hints.add("AIR");
                hints.add("Air");
            }
            default -> {
            }
        }
        return hints.toArray(String[]::new);
    }

    private void fillClaimantPartyIfPresent(JsonNode claimantParty) {
        if (isMissingOrEmpty(claimantParty)) {
            return;
        }

        fillPartyRowIfPresent("Claimant Party", claimantParty);

        JsonNode claimantInformation = claimantParty.path("claimantInformation");
        fillFieldIfPresent("Claimant Id", text(claimantInformation, "codeValue"));
        fillFieldIfPresent("Claimant Name", text(claimantInformation, "name"));
    }

    private void fillInpPartyCardIfPresent(String title, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        JsonNode addressNode = partyNode.path("address");

        String name = normalize(text(identityNode.path("partyName"), "name"));
        String partyId = normalize(text(identityNode.path("partyIdentification"), "id"));
        String addressLine1 = arrayText(addressNode.path("addressLine").path("line"), 0);
        String addressLine2 = arrayText(addressNode.path("addressLine").path("line"), 1);
        String city = text(addressNode, "cityName");
        String postalCode = firstNonBlank(text(addressNode, "postalZone"), text(addressNode, "countrySubentityCode"));
        String compactAddress = joinNonBlank(", ", addressLine1, addressLine2, city, postalCode);
        String countryCode = text(addressNode, "countryCode");

        if ((name == null || name.isBlank())
                && (partyId == null || partyId.isBlank())
                && compactAddress.isBlank()
                && (countryCode == null || countryCode.isBlank())) {
            return;
        }

        Locator card = resolveInpPartyCard(title);
        if (card == null) {
            logFieldMappingWarning("INP party card '" + title + "' was not visible while JSON value was '"
                    + firstNonBlank(name, compactAddress, countryCode, "N/A") + "'.");
            return;
        }

        fillNthLookupFieldInScopeByClickOnlyIfPresent(card, 0, name, name, partyId);
        fillFieldAfterScopeLabelIfPresent(card, "UEN", 0, partyId);
        fillFieldAfterScopeLabelIfPresent(card, "Address", 0, compactAddress);
        fillLookupFieldAfterScopeLabelIfPresent(card, "Country Code", 0, countryCode, countryCode);
    }

    private JsonNode partyIdentityNode(JsonNode partyNode) {
        JsonNode partyDetail = partyNode.path("partyDetail");
        return isMissingOrEmpty(partyDetail) ? partyNode : partyDetail;
    }

    private Locator resolveInpPartyCard(String title) {
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

    private String arrayText(JsonNode arrayNode, int index) {
        if (arrayNode != null && arrayNode.isArray() && index >= 0 && index < arrayNode.size()) {
            JsonNode valueNode = arrayNode.get(index);
            if (valueNode != null && !valueNode.isNull()) {
                return normalize(valueNode.asText());
            }
        }
        return null;
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
}
