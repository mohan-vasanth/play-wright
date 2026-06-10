package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

public class CooDeclarationPage extends IptDeclarationPage {

    private int progressScreenshotIndex;

    public CooDeclarationPage(Page page) {
        super(page);
    }

    @Override
    public void populateFrom(JsonNode data) {
        validateCooPayload(data);
        populateDraftFrom(data);
        if (shouldSubmitDeclaration(data)) {
            submitDeclaration();
        }
    }

    @Override
    public void populateDraftFrom(JsonNode data) {
        validateCooPayload(data);
        progressScreenshotIndex = 0;
        captureProgressScreenshot("form-visible-before-data-entry");
        fillSectionAndAdvance("Header & Certificate (H)", () -> fillHeaderAndCertificate(data));
        captureProgressScreenshot("header-certificate-complete");
        fillSectionAndAdvance("Transport Info (T)", () -> fillTransportInfo(data));
        captureProgressScreenshot("transport-complete");
        fillSectionAndAdvance("Party Info (P)", () -> fillPartyInfo(data));
        captureProgressScreenshot("party-complete");
        fillSectionAndAdvance("Items (I)", () -> fillItems(data));
        captureProgressScreenshot("items-complete");
        fillSummary(data);
        captureProgressScreenshot("summary-complete");
    }

    @Override
    public void submitDeclaration() {
        openSection("Summary (S)");
        saveDraft();
        page.waitForTimeout(1500);
        clickButtonByText("SUBMIT DECLARATION", "Submit Declaration");
        page.waitForTimeout(5000);
    }

    private void validateCooPayload(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            throw new IllegalArgumentException("COO declaration payload is required.");
        }

        String payloadType = firstNonBlank(
                text(data, "type"),
                text(data.path("header"), "commonAccessReference"),
                text(data.path("header"), "applicationType"));
        String normalizedType = normalize(payloadType);
        if (normalizedType == null
                || (!normalizedType.contains("COO")
                && !normalizedType.contains("COODEC")
                && !normalizedType.contains("CERTIFICATE OF ORIGIN"))) {
            throw new IllegalArgumentException("Payload does not match a COO declaration: " + payloadType);
        }
    }

    private void fillHeaderAndCertificate(JsonNode data) {
        JsonNode header = data.path("header");
        JsonNode certificate = data.path("certificate");
        JsonNode transportMode = data.path("transport")
                .path("outwardTransport")
                .path("transportMeans")
                .path("transportMode");

        fillLookupFieldInSection(
                "Declaration Info",
                "Outward Transport Mode",
                text(transportMode, "modeCode"),
                text(transportMode, "modeCode"),
                "4 - Air",
                "AIR",
                "Air",
                "1 - Sea",
                "SEA",
                "Sea");
        fillFieldInSectionIfPresent("Declaration Info", "Previous Permit Number", text(header, "previousPermitNumber"));

        fillLookupFieldInSection(
                "Certificate of Origin",
                "CO Type",
                text(certificate, "applicationProductType"),
                text(certificate, "applicationProductType"));
        fillCertificateDetail(certificate.path("certificateDetail"), 0, "Certificate Detail #1 - Type");
        fillCertificateDetail(certificate.path("certificateDetail"), 1, "Certificate Detail #2 - Type");
        fillLookupFieldInSectionIfPresent(
                "Certificate of Origin",
                "Currency",
                text(certificate, "currencyCode"),
                text(certificate, "currencyCode"));

        if (hasDocumentData(data)) {
            setCheckboxByLabel("Document", true);
        }
    }

    private void fillCertificateDetail(JsonNode details, int index, String label) {
        if (!details.isArray() || index >= details.size()) {
            return;
        }

        JsonNode detail = details.get(index);
        if (detail == null || detail.isMissingNode() || detail.isNull()) {
            return;
        }

        fillLookupFieldInSectionIfPresent(
                "Certificate of Origin",
                label,
                text(detail, "certificateType"),
                text(detail, "certificateType"));

        String copies = text(detail, "copiesNumeric");
        if (copies == null || copies.isBlank()) {
            return;
        }

        Locator certificateSection = resolveSection("Certificate of Origin");
        fillFieldAfterScopeLabelIfPresent(certificateSection, "No. of copies", index, copies);
    }

    private boolean hasDocumentData(JsonNode data) {
        JsonNode supportingDocuments = data.path("supportingDocument");
        JsonNode formMetaData = data.path("formMetaData");
        return (supportingDocuments.isArray() && !supportingDocuments.isEmpty())
                || formMetaData.path("supportingDocumentIsActive").asBoolean(false)
                || formMetaData.path("documentIsActive").asBoolean(false);
    }

    private void fillTransportInfo(JsonNode data) {
        JsonNode outwardTransport = data.path("transport").path("outwardTransport");
        JsonNode transportMeans = outwardTransport.path("transportMeans");
        JsonNode transportMode = transportMeans.path("transportMode");

        fillFieldInSectionByAnyLabelIfPresent(
                "Outward Transport Means",
                text(transportMode, "conveyanceReferenceNumber"),
                "Conveyance Ref. No.",
                "Conveyance Reference Number",
                "Outward Flight Number",
                "Outward Voyage Number");
        fillFieldInSectionByAnyLabelIfPresent(
                "Outward Transport Means",
                text(transportMode, "transportIdentifier"),
                "Transport Identifier",
                "Outward Aircraft Registration Number",
                "Outward Vessel Name",
                "Vehicle Licence/Registration Number");
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
    }

    private void fillPartyInfo(JsonNode data) {
        JsonNode party = data.path("party");

        fillPartyCard(
                "Declaring Agent",
                party.path("declaringAgentParty"),
                MissingNode.getInstance());
        fillPartyCard(
                "Outward Carrier",
                party.path("outwardCarrierAgentParty"),
                MissingNode.getInstance());
        fillPartyCard(
                "Exporter",
                party.path("exporterParty"),
                partyAddressNode(party.path("exporterParty")));
        fillPartyCard(
                "Consignee",
                party.path("consigneeParty"),
                partyAddressNode(party.path("consigneeParty")));
        fillPartyCard(
                "Manufacturer",
                party.path("manufacturerParty"),
                partyAddressNode(party.path("manufacturerParty")));
    }

    private void fillPartyCard(String title, JsonNode partyNode, JsonNode addressNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        String name = text(identityNode.path("partyName"), "name");
        String id = text(identityNode.path("partyIdentification"), "id");
        if ((name == null || name.isBlank()) && (id == null || id.isBlank()) && isMissingOrEmpty(addressNode)) {
            return;
        }

        ensureAccordionExpanded(title, "Name");
        Locator card = resolvePartyCard(title);
        if (card == null) {
            return;
        }

        fillNthLookupFieldInScopeIfPresent(card, 0, name, name, id);
        fillFieldAfterScopeLabelIfPresent(card, "UEN", 0, id);
        fillFieldAfterScopeLabelIfPresent(card, "Party Name", 0, name);
        fillFieldAfterScopeLabelIfPresent(card, "Name", 0, name);

        String addressLine1 = arrayText(addressNode.path("addressLine").path("line"), 0);
        String addressLine2 = arrayText(addressNode.path("addressLine").path("line"), 1);
        String city = text(addressNode, "cityName");
        String postalCode = text(addressNode, "postalZone");
        String countryCode = text(addressNode, "countryCode");

        fillFieldAfterScopeLabelIfPresent(card, "Address Line 1", 0, addressLine1);
        fillFieldAfterScopeLabelIfPresent(card, "Address Line 2", 0, addressLine2);
        fillFieldAfterScopeLabelIfPresent(card, "City", 0, city);
        fillFieldAfterScopeLabelIfPresent(card, "Postal Code", 0, postalCode);
        fillLookupFieldAfterScopeLabelIfPresent(card, "Country Code", 0, countryCode, countryCode);
    }

    private JsonNode partyIdentityNode(JsonNode partyNode) {
        JsonNode partyDetail = partyNode.path("partyDetail");
        if (!isMissingOrEmpty(partyDetail)) {
            return partyDetail;
        }
        return partyNode;
    }

    private JsonNode partyAddressNode(JsonNode partyNode) {
        JsonNode address = partyNode.path("address");
        if (!isMissingOrEmpty(address)) {
            return address;
        }
        return partyNode;
    }

    private void fillItems(JsonNode data) {
        JsonNode items = data.path("item");
        if (!items.isArray() || items.isEmpty()) {
            return;
        }

        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                clickButtonByText("ADD ITEM", "Add Item");
                page.waitForTimeout(500);
            }
            fillSingleItem(items.get(index), data.path("formMetaData"), data.path("certificate"), index);
        }
    }

    private void fillSingleItem(JsonNode item, JsonNode formMetaData, JsonNode certificate, int index) {
        Locator itemDetailsSection = resolveSection("Item Details");
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS Code",
                0,
                text(item, "itemHarmonizedSystemCode"),
                text(item, "itemHarmonizedSystemCode"));
        fillFieldAfterScopeLabelIfPresent(itemDetailsSection, "Description", 0, text(item, "goodsDescription"));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "COO",
                0,
                text(item, "originCountry"),
                text(item, "originCountry"));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS Type",
                0,
                text(item, "hsType"),
                text(item, "hsType"));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "Duty Type",
                0,
                text(item, "dutyType"),
                text(item, "dutyType"));
        fillLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS CA",
                0,
                firstNonBlank(text(item, "hsCa"), text(item, "hsExportCa")),
                firstNonBlank(text(item, "hsCa"), text(item, "hsExportCa")));

        fillQuantityRowInScope(resolveItemQuantitySection(), "HS Quantity", item.path("harmonizedSystemQuantity"));

        Locator itemValuesSection = resolveItemValuesSection();
        String itemCurrency = firstNonBlank(
                arrayText(formMetaData.path("unitPriceCurrencies"), index),
                text(certificate, "currencyCode"));
        String itemValue = firstNonBlank(
                arrayText(formMetaData.path("itemValues"), index),
                text(item.path("itemCertificate"), "itemValue"));
        fillLookupFieldAfterScopeLabelIfPresent(itemValuesSection, "Item Value", 1, itemCurrency, itemCurrency);
        fillFieldAfterScopeLabelIfPresent(itemValuesSection, "Item Value", 0, normalizeNumericForEntry(itemValue));
        fillFieldAfterScopeLabelIfPresent(
                itemValuesSection,
                "Item CIF/FOB Value (SGD)",
                0,
                normalizeNumericForEntry(text(item, "itemCIFFOBValue")));

        fillShippingMarks(firstArrayItem(item.path("shippingMarksInformation")));
        fillItemCertificate(item.path("itemCertificate"), formMetaData);
    }

    private void fillItemCertificate(JsonNode itemCertificate, JsonNode formMetaData) {
        boolean hasItemCertificateData = !isMissingOrEmpty(itemCertificate);
        boolean itemCoEnabled = hasItemCertificateData
                || formMetaData.path("itemCoIsActive").path(0).asBoolean(false);
        if (!itemCoEnabled) {
            return;
        }

        setCheckboxByLabel("Certificate of Origin (CO)", true);
        page.waitForTimeout(300);

        Locator section = waitForItemCertificateSectionOrNull(3000);
        if (section == null) {
            throw new IllegalStateException("Certificate of Origin (CO) section did not open in the COO item tab.");
        }
        if (!hasItemCertificateData) {
            return;
        }

        fillQuantityRowInScope(section, "Certificate Quantity", itemCertificate.path("itemCertificateQuantity"));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Manufacturing Cost Date",
                0,
                formatUiDate(text(itemCertificate, "manufacturingCostDate")));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Item Invoice Number",
                0,
                text(itemCertificate, "itemInvoiceNumber"));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Item Invoice Date",
                0,
                formatUiDate(text(itemCertificate, "itemInvoiceDate")));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "HS Code",
                0,
                text(itemCertificate, "harmonizedSystemCode"));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Content Percent",
                0,
                normalizeNumericForEntry(text(itemCertificate, "contentPercent")));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Content Percent (%)",
                0,
                normalizeNumericForEntry(text(itemCertificate, "contentPercent")));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Origin Criterion 1",
                0,
                arrayText(itemCertificate.path("originCriterion"), 0));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Origin Criterion 2",
                0,
                arrayText(itemCertificate.path("originCriterion"), 1));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Origin Criterion 3",
                0,
                arrayText(itemCertificate.path("originCriterion"), 2));
        fillFieldAfterScopeLabelIfPresent(
                section,
                "Certificate Item Description",
                0,
                itemCertificateDescriptionText(itemCertificate.path("itemCertificateDescription")));
    }

    private void fillSummary(JsonNode data) {
        openSection("Summary (S)");
        fillFieldIfPresent("Shipment ID", firstNonBlank(
                text(data.path("summary"), "shipmentId"),
                text(data.path("header"), "shipmentId"),
                text(data, "shipmentId")));
        if (data.path("header").path("declarationIndicator").asBoolean(false)
                || data.path("formMetaData").path("declarationIndicator").asBoolean(false)) {
            setCheckboxByLabel("I/We declare that all particulars in this application are true and correct.", true);
        }
        saveDraft();
    }

    private boolean shouldSubmitDeclaration(JsonNode data) {
        return data.path("summary").path("submitDeclaration").asBoolean(false)
                || data.path("formMetaData").path("submitDeclaration").asBoolean(false);
    }

    private Locator resolvePartyCard(String title) {
        String escapedTitle = xpathLiteral(title);
        Locator locator = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox']][1]");
        return firstVisible(locator);
    }

    private Locator resolveItemQuantitySection() {
        String[] titles = new String[] { "Item Quantity", "Item Quantity & value", "Item Quantity & Value" };
        for (String title : titles) {
            String escapedTitle = xpathLiteral(title);
            Locator section = page.locator(
                    "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                            + "/ancestor::*[(.//*[contains(normalize-space(translate(., '*', '')), 'HS Quantity')])"
                            + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
            Locator visibleSection = firstVisible(section);
            if (visibleSection != null) {
                return visibleSection;
            }
        }

        return resolveSection("Item Details");
    }

    private Locator resolveItemValuesSection() {
        String[] titles = new String[] { "Item Values", "Item Quantity & value", "Item Quantity & Value" };
        for (String title : titles) {
            String escapedTitle = xpathLiteral(title);
            Locator section = page.locator(
                    "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                            + "/ancestor::*[(.//*[contains(normalize-space(translate(., '*', '')), 'Item Value')]"
                            + " or .//*[contains(normalize-space(translate(., '*', '')), 'Item CIF/FOB Value (SGD)')])"
                            + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
            Locator visibleSection = firstVisible(section);
            if (visibleSection != null) {
                return visibleSection;
            }
        }

        return resolveSection("Item Details");
    }

    private Locator waitForItemCertificateSectionOrNull(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Locator section = resolveItemCertificateSectionOrNull();
            if (section != null) {
                return section;
            }
            page.waitForTimeout(100);
        }
        return null;
    }

    private Locator resolveItemCertificateSectionOrNull() {
        String sectionTitle = xpathLiteral("Certificate of Origin (CO)");
        return firstVisible(page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + sectionTitle + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + sectionTitle + ")])])[last()]"
                        + "/ancestor::*[.//*[contains(normalize-space(translate(., '*', '')), 'Certificate Quantity')"
                        + " or contains(normalize-space(translate(., '*', '')), 'Certificate Item Description')"
                        + " or contains(normalize-space(translate(., '*', '')), 'Manufacturing Cost Date')]"
                        + " and (.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]"));
    }

    private String itemCertificateDescriptionText(JsonNode itemCertificateDescription) {
        if (itemCertificateDescription == null || !itemCertificateDescription.isArray()) {
            return null;
        }

        java.util.List<String> lines = new java.util.ArrayList<>();
        for (JsonNode descriptionNode : itemCertificateDescription) {
            JsonNode lineNodes = descriptionNode.path("line");
            if (lineNodes.isArray()) {
                for (JsonNode lineNode : lineNodes) {
                    String line = normalize(lineNode.asText());
                    if (line != null && !line.isBlank()) {
                        lines.add(line);
                    }
                }
            } else {
                String line = normalize(descriptionNode.asText());
                if (line != null && !line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        return lines.isEmpty() ? null : String.join(System.lineSeparator(), lines);
    }

    private void clickButtonByText(String... candidates) {
        for (String candidate : candidates) {
            Locator button = page.locator("button:has-text('" + candidate + "')").first();
            if (button.count() > 0 && button.isVisible()) {
                button.click();
                return;
            }
        }

        for (String candidate : candidates) {
            try {
                Boolean clicked = (Boolean) page.evaluate("""
                        text => {
                            const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                            const target = Array.from(document.querySelectorAll('button, [role=\"button\"], a'))
                                .find(element => normalize(element.innerText || element.textContent) === normalize(text));
                            if (!target) {
                                return false;
                            }
                            target.scrollIntoView({ block: 'center' });
                            target.click();
                            return true;
                        }
                        """, candidate);
                if (Boolean.TRUE.equals(clicked)) {
                    return;
                }
            } catch (PlaywrightException ignored) {
            }
        }

        throw new IllegalStateException("Unable to click button. Candidates: " + String.join(", ", candidates));
    }

    private String arrayText(JsonNode arrayNode, int index) {
        if (arrayNode == null || !arrayNode.isArray() || index < 0 || index >= arrayNode.size()) {
            return null;
        }
        JsonNode node = arrayNode.get(index);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = normalize(node.asText());
        return value == null || value.isBlank() ? null : value;
    }

    private String xpathLiteral(String value) {
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
            char character = value.charAt(index);
            if (character == '\'') {
                builder.append("\"'\"");
            } else if (character == '"') {
                builder.append("'\"'");
            } else {
                builder.append('\'').append(character).append('\'');
            }
        }
        builder.append(')');
        return builder.toString();
    }

    private void captureProgressScreenshot(String stepName) {
        String reportPrefix = System.getProperty("tradenix.report.artifact.prefix", "coo-progress");
        String sanitizedStepName = (stepName == null || stepName.isBlank())
                ? "step"
                : stepName.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        progressScreenshotIndex++;

        try {
            java.nio.file.Path screenshotPath = java.nio.file.Paths.get(
                    "target",
                    reportPrefix + "-progress-"
                            + String.format("%02d", progressScreenshotIndex)
                            + "-" + sanitizedStepName + ".png");
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(screenshotPath));
        } catch (Exception ignored) {
        }
    }
}
