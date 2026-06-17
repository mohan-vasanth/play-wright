package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.BoundingBox;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        waitForActionButtonEnabled("SUBMIT DECLARATION", 15000);
        captureProgressScreenshot("summary-before-submit");
        clickActionButtonExactWithRetry("SUBMIT DECLARATION", 3);
        page.waitForTimeout(5000);
        captureProgressScreenshot("after-submit");
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
        fillFieldInSectionIfPresent(
                "Certificate of Origin",
                "Preference Content Percent",
                normalizeNumericForEntry(text(certificate, "preferenceContentPercent")));
        fillLookupFieldInSectionIfPresent(
                "Certificate of Origin",
                "Currency",
                text(certificate, "currencyCode"),
                text(certificate, "currencyCode"));
        fillCertificateColumnValues("Certificate Additional Info", certificate.path("additionalCertificateDetails"));
        fillCertificateColumnValues("Transport Details", certificate.path("transportDetails"));

        if (hasDocumentData(data)) {
            setCheckboxByLabel("Document", true);
            fillSupportingDocumentReferences(data);
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

        Locator certificateDetailRow = resolveCertificateDetailRowOrNull(index);
        if (certificateDetailRow != null) {
            fillLastOrderedFieldInScope(certificateDetailRow, copies);
            return;
        }

        Locator certificateSection = resolveSection("Certificate of Origin");
        fillFieldAfterScopeLabelIfPresent(certificateSection, "No. of copies", index, copies);
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
        String documentId = firstNonBlank(text(documentNode, "documentID"), text(documentNode, "documentId"));
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

        boolean filenameRendered = false;
        if (filename == null || filename.isBlank()) {
            clickDocumentUploadButtonIfVisible(documentSection);
            return;
        }

        if (filenameField != null) {
            focusAndType(filenameField, displayFileName, false);
            filenameRendered = waitForAnyRenderedFieldValue(filenameField, 1500, displayFileName);
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
        String fileNameOnly = fileNameOnly(filename);
        Path directory = Path.of("target", "upload-fixtures", String.valueOf(System.nanoTime()));
        Files.createDirectories(directory);
        Path output = directory.resolve(fileNameOnly);

        String lowerName = fileNameOnly.toLowerCase(java.util.Locale.ROOT);
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
        String documentLabel = xpathLiteral("Document");
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

    private void fillCertificateColumnValues(String heading, JsonNode values) {
        if (values == null || !values.isArray() || values.isEmpty()) {
            return;
        }

        Locator column = resolveCertificateColumnByHeadingOrNull(heading);
        if (column == null) {
            return;
        }

        List<Locator> orderedFields = orderedVisibleEditableFields(column);
        int fieldIndex = 0;
        for (int index = 0; index < values.size() && fieldIndex < orderedFields.size(); index++) {
            String value = normalize(values.get(index).asText());
            if (value == null || value.isBlank()) {
                continue;
            }
            focusAndType(orderedFields.get(fieldIndex++), value, false);
        }
    }

    private void fillTransportInfo(JsonNode data) {
        JsonNode outwardTransport = data.path("transport").path("outwardTransport");
        JsonNode transportMeans = outwardTransport.path("transportMeans");
        JsonNode transportMode = transportMeans.path("transportMode");

        fillVerifiedFieldInSectionByAnyLabelIfPresent(
                "Outward Transport Means",
                text(transportMode, "conveyanceReferenceNumber"),
                "Flight Number",
                "Conveyance Ref. No.",
                "Conveyance Reference Number",
                "Outward Flight Number",
                "Outward Voyage Number");
        fillVerifiedFieldInSectionByAnyLabelIfPresent(
                "Outward Transport Means",
                text(transportMode, "transportIdentifier"),
                "Aircraft Registration Number",
                "Transport Identifier",
                "Vessel Name",
                "Outward Aircraft Registration Number",
                "Outward Vessel Name",
                "Vehicle Licence/Registration Number");
        fillDateFieldInSectionIfPresent(
                "Outward Transport Means",
                "Departure Date",
                formatUiDate(text(outwardTransport, "departureDate")));
        fillVerifiedLookupFieldInSectionIfPresent(
                "Outward Transport Means",
                "Discharge Port",
                text(outwardTransport, "dischargePort"),
                text(outwardTransport, "dischargePort"));
        fillVerifiedLookupFieldInSectionIfPresent(
                "Outward Transport Means",
                "Country of Final Destination",
                text(outwardTransport, "finalDestinationCountry"),
                text(outwardTransport, "finalDestinationCountry"));
    }

    private void fillPartyInfo(JsonNode data) {
        JsonNode party = data.path("party");

        fillPartyCard(
                "Freight Forwarder",
                party.path("freightForwarderParty"),
                MissingNode.getInstance());
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
        fillVerifiedLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS Code",
                0,
                text(item, "itemHarmonizedSystemCode"),
                text(item, "itemHarmonizedSystemCode"));
        fillValidatedFieldAfterScopeLabelIfPresent(itemDetailsSection, "Description", 0, text(item, "goodsDescription"));
        fillVerifiedLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "COO",
                0,
                text(item, "originCountry"),
                text(item, "originCountry"));
        fillVerifiedLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS Type",
                0,
                text(item, "hsType"),
                text(item, "hsType"));
        fillVerifiedLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "Duty Type",
                0,
                text(item, "dutyType"),
                text(item, "dutyType"));
        fillVerifiedLookupFieldAfterScopeLabelIfPresent(
                itemDetailsSection,
                "HS CA",
                0,
                firstNonBlank(text(item, "hsCa"), text(item, "hsExportCa")),
                firstNonBlank(text(item, "hsCa"), text(item, "hsExportCa")));

        fillQuantityRowInScope(resolveItemQuantitySection(), "HS Quantity", item.path("harmonizedSystemQuantity"));

        String itemCurrency = firstNonBlank(
                arrayText(formMetaData.path("unitPriceCurrencies"), index),
                text(certificate, "currencyCode"));
        fillVerifiedLookupFieldInSectionIfPresent("Item Details", "Currency", itemCurrency, itemCurrency);

        Locator itemValuesSection = resolveItemValuesSection();
        String itemValue = firstNonBlank(
                arrayText(formMetaData.path("itemValues"), index),
                text(item.path("itemCertificate"), "itemValue"));
        fillVerifiedLookupFieldAfterScopeLabelIfPresent(itemValuesSection, "Item Value", 1, itemCurrency, itemCurrency);
        fillFieldAfterScopeLabelIfPresent(itemValuesSection, "Item Value", 0, normalizeNumericForEntry(itemValue));
        fillFieldAfterScopeLabelIfPresent(
                itemValuesSection,
                "Item CIF/FOB Value (SGD)",
                0,
                normalizeNumericForEntry(text(item, "itemCIFFOBValue")));

        fillShippingMarks(firstArrayItem(item.path("shippingMarksInformation")));
        fillItemCertificate(item.path("itemCertificate"), formMetaData);
    }

    private void fillVerifiedFieldInSectionByAnyLabelIfPresent(String sectionTitle, String value, String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }

        for (String label : labels) {
            Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
            if (field == null) {
                continue;
            }

            focusAndType(field, value, false);
            if (waitForAnyRenderedFieldValue(field, 1500, value)) {
                return;
            }

            try {
                field.fill(value);
            } catch (PlaywrightException ignored) {
            }
            if (waitForAnyRenderedFieldValue(field, 1500, value)) {
                return;
            }

            throw new IllegalStateException(label + " value was not rendered. Expected: "
                    + value + ", Actual: " + readRenderedFieldValue(field));
        }
    }

    private void fillVerifiedLookupFieldInSectionIfPresent(
            String sectionTitle,
            String label,
            String value,
            String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }

        Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
        if (field == null) {
            return;
        }

        fillVerifiedLookupField(field, value, label, suggestionHints);
    }

    private void fillVerifiedLookupFieldAfterScopeLabelIfPresent(
            Locator scope,
            String rowLabel,
            int occurrence,
            String value,
            String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }

        Locator field = resolveEditableFieldAfterScopeLabelInScopeOrNull(scope, rowLabel, occurrence);
        if (field == null) {
            return;
        }

        fillVerifiedLookupField(field, value, rowLabel, suggestionHints);
    }

    private void fillVerifiedLookupField(
            Locator field,
            String value,
            String label,
            String... suggestionHints) {
        focusAndType(field, value, true, suggestionHints);
        if (waitForAnyRenderedFieldValue(field, 1500, appendLookupExpectedValues(value, suggestionHints))) {
            return;
        }

        syncLookupComponentValue(field, value, suggestionHints);
        if (waitForAnyRenderedFieldValue(field, 1500, appendLookupExpectedValues(value, suggestionHints))) {
            return;
        }

        throw new IllegalStateException(label + " lookup value was not rendered. Expected: "
                + value + ", Actual: " + readRenderedFieldValue(field));
    }

    private String[] appendLookupExpectedValues(String value, String... suggestionHints) {
        List<String> expectedValues = new ArrayList<>();
        if (value != null && !value.isBlank()) {
            expectedValues.add(value);
        }
        if (suggestionHints != null) {
            for (String suggestionHint : suggestionHints) {
                if (suggestionHint != null && !suggestionHint.isBlank()) {
                    expectedValues.add(suggestionHint);
                }
            }
        }
        return expectedValues.toArray(String[]::new);
    }

    private void syncLookupComponentValue(Locator field, String value, String... suggestionHints) {
        if (value == null || value.isBlank()) {
            return;
        }

        try {
            field.evaluate("""
                    (element, args) => {
                        const normalize = input => (input || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const hints = [args.value, ...(args.suggestionHints || [])].map(normalize).filter(Boolean);
                        const unique = candidates => candidates.filter((candidate, index) =>
                            !!candidate && candidates.indexOf(candidate) === index);

                        const candidateElements = unique([
                            element,
                            element.closest?.('[formcontrolname]'),
                            element.closest?.('app-currency-code-lookup, app-lookup, app-dropdown, ng-select, mat-select, .ng-select, [role="combobox"]'),
                            element.parentElement,
                            element.parentElement?.parentElement,
                            element.parentElement?.parentElement?.parentElement
                        ]);

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
                            const option = allOptions.find(optionCandidate => {
                                const code = normalize(optionCandidate?.code);
                                const description = normalize(optionCandidate?.description);
                                const combined = normalize(`${optionCandidate?.code || ''} ${optionCandidate?.description || ''}`);
                                return hints.some(hint =>
                                    hint === code
                                    || hint === description
                                    || hint === combined
                                    || combined.includes(hint)
                                    || hint.includes(combined));
                            });

                            const selectedValue = option?.code || args.value;
                            const selectedDisplay = option?.description || option?.code || args.value;

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
                            if (typeof component.selectOptionItem === 'function' && option) {
                                component.selectOptionItem(option);
                            }
                            if (typeof component.selectOptionLabel === 'function' && option) {
                                component.selectOptionLabel(option);
                            }
                            if (typeof component.onChange === 'function') {
                                component.onChange(selectedValue);
                            }
                            if (typeof component.onTouched === 'function') {
                                component.onTouched();
                            }

                            const nativeInput = component.inputElement?.nativeElement
                                || candidate.querySelector?.('input, textarea, select')
                                || element.querySelector?.('input, textarea, select');
                            if (nativeInput) {
                                nativeInput.value = selectedDisplay;
                                nativeInput.dispatchEvent(new Event('input', { bubbles: true }));
                                nativeInput.dispatchEvent(new Event('change', { bubbles: true }));
                                nativeInput.dispatchEvent(new Event('blur', { bubbles: true }));
                            }
                            return true;
                        }

                        const fallbackField = element.matches?.('input, textarea, select')
                            ? element
                            : element.querySelector?.('input, textarea, select')
                                || element.closest?.('[formcontrolname]')?.querySelector?.('input, textarea, select');
                        if (!fallbackField) {
                            return false;
                        }

                        fallbackField.value = args.value;
                        fallbackField.dispatchEvent(new Event('input', { bubbles: true }));
                        fallbackField.dispatchEvent(new Event('change', { bubbles: true }));
                        fallbackField.dispatchEvent(new Event('blur', { bubbles: true }));
                        return true;
                    }
                    """, java.util.Map.of(
                    "value", value,
                    "suggestionHints", suggestionHints == null ? new String[0] : suggestionHints));
        } catch (Exception ignored) {
        }
    }

    private Locator resolveEditableFieldAfterScopeLabelInScopeOrNull(Locator scope, String rowLabel, int occurrence) {
        Locator visibleScope = firstVisible(scope);
        if (visibleScope == null) {
            return null;
        }

        Locator field = resolveEditableFieldAfterScopeLabelByMatchOrNull(visibleScope, rowLabel, occurrence, false);
        if (field != null) {
            return field;
        }

        field = resolveEditableFieldAfterScopeLabelByMatchOrNull(visibleScope, rowLabel, occurrence, true);
        if (field != null) {
            return field;
        }

        return resolveEditableFieldInScopeRowOrNull(visibleScope, rowLabel, occurrence);
    }

    private Locator resolveEditableFieldInScopeRowOrNull(Locator visibleScope, String rowLabel, int occurrence) {
        Locator exactRow = resolveEditableFieldInScopeRowByLabelOrNull(visibleScope, rowLabel, occurrence, false);
        if (exactRow != null) {
            return exactRow;
        }
        return resolveEditableFieldInScopeRowByLabelOrNull(visibleScope, rowLabel, occurrence, true);
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
        return firstVisible(field);
    }

    private Locator resolveScopeLabelOrNull(Locator visibleScope, String rowLabel, boolean containsMatch) {
        String escapedRowLabel = xpathLiteral(rowLabel);
        String matchExpression = containsMatch
                ? "contains(normalize-space(translate(., '*', '')), " + escapedRowLabel + ")"
                : "normalize-space(translate(., '*', ''))=" + escapedRowLabel;
        Locator label = visibleScope.locator(
                "xpath=(.//*[" + matchExpression + "]"
                        + "[not(.//*[" + matchExpression + "])])[1]");
        return firstVisible(label);
    }

    protected Locator resolveVisibleEditableFieldInRowOrNull(Locator row, int occurrence) {
        List<Locator> orderedFields = orderedVisibleEditableFields(row);
        if (occurrence < 0 || occurrence >= orderedFields.size()) {
            return null;
        }
        return orderedFields.get(occurrence);
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
        fillValidatedFieldAfterScopeLabelIfPresent(
                section,
                "Item Invoice Number",
                0,
                text(itemCertificate, "itemInvoiceNumber"));
        fillValidatedFieldAfterScopeLabelIfPresent(
                section,
                "Item Invoice Date",
                0,
                formatUiDate(text(itemCertificate, "itemInvoiceDate")));
        fillValidatedFieldAfterScopeLabelIfPresent(
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
        fillValidatedFieldAfterScopeLabelIfPresent(
                section,
                "Origin Criterion 1",
                0,
                arrayText(itemCertificate.path("originCriterion"), 0));
        fillValidatedFieldAfterScopeLabelIfPresent(
                section,
                "Origin Criterion 2",
                0,
                arrayText(itemCertificate.path("originCriterion"), 1));
        fillValidatedFieldAfterScopeLabelIfPresent(
                section,
                "Origin Criterion 3",
                0,
                arrayText(itemCertificate.path("originCriterion"), 2));
        fillValidatedFieldAfterScopeLabelIfPresent(
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
        captureProgressScreenshot("summary-before-save-draft");
        saveDraftAndWaitForCompletion();
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

    private Locator resolveCertificateDetailRowOrNull(int index) {
        String detailLabel = "Certificate Detail #" + (index + 1) + " - Type";
        String escapedDetailLabel = xpathLiteral(detailLabel);
        String escapedCopiesLabel = xpathLiteral("No. of copies");
        Locator row = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedDetailLabel + ")])[last()]"
                        + "/ancestor::*[.//*[contains(normalize-space(translate(., '*', '')), " + escapedCopiesLabel + ")]"
                        + " and (.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]");
        return firstVisible(row);
    }

    private Locator resolveCertificateColumnByHeadingOrNull(String heading) {
        String escapedHeading = xpathLiteral(heading);
        Locator column = page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + escapedHeading + ")])[last()]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]");
        return firstVisible(column);
    }

    private void fillLastOrderedFieldInScope(Locator scope, String value) {
        if (scope == null || value == null || value.isBlank()) {
            return;
        }

        List<Locator> fields = orderedVisibleEditableFields(scope);
        if (fields.isEmpty()) {
            return;
        }

        focusAndType(fields.get(fields.size() - 1), value, false);
    }

    private List<Locator> orderedVisibleEditableFields(Locator scope) {
        List<Locator> orderedFields = new ArrayList<>();
        Locator fields = scope.locator(combinedEditableSelector());
        List<PositionedElement> positionedElements = collectDistinctVisibleEditableElements(fields);
        positionedElements.stream()
                .sorted(Comparator.comparingDouble(PositionedElement::y).thenComparingDouble(PositionedElement::x))
                .forEach(positionedElement -> orderedFields.add(fields.nth(positionedElement.index())));
        return orderedFields;
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

            PositionedElement current = new PositionedElement(index, box.x, box.y, box.width, box.height);
            int duplicateIndex = findDuplicatePositionedElementIndex(positionedElements, current);
            if (duplicateIndex >= 0) {
                PositionedElement existing = positionedElements.get(duplicateIndex);
                if (current.width() * current.height() > existing.width() * existing.height()) {
                    positionedElements.set(duplicateIndex, current);
                }
                continue;
            }

            positionedElements.add(current);
        }
        return positionedElements;
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

    private String combinedEditableSelector() {
        return "input:not([type='checkbox']):not([readonly]):not([disabled]), "
                + "textarea:not([readonly]):not([disabled]), "
                + "select:not([disabled]), "
                + "[contenteditable='true'], "
                + "[role='combobox'], "
                + "[role='textbox']";
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

    private void saveDraftAndWaitForCompletion() {
        waitForActionButtonEnabled("SAVE DRAFT", 15000);
        clickActionButtonExactWithRetry("SAVE DRAFT", 3);
        waitForPostSaveReadyState(15000);
        captureProgressScreenshot("after-save-draft");
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
        throw new IllegalStateException("Draft save did not complete successfully before submit.");
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
        throw new IllegalStateException("Action button was not clickable: " + buttonText);
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

    private record PositionedElement(
            int index,
            double x,
            double y,
            double width,
            double height) {
    }
}
