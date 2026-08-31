package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OutDeclarationPage extends IptDeclarationPage {

    private boolean summaryDraftSaved;
    private boolean additionalRecipientsRequested;

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
        if (!additionalRecipientsRequested) {
            ensureAdditionalRecipientsInactive();
        }
        if (!summaryDraftSaved) {
            saveDraftAndWaitForCompletion();
        } else {
            waitForPostSaveReadyState(15000);
        }
        waitForActionButtonEnabled("SUBMIT DECLARATION", configuredElementWaitTimeoutMs());
        clickActionButtonExactWithRetry("SUBMIT DECLARATION", configuredRetryCount());
        page.waitForTimeout(5000);
    }

    public void populateDraftFrom(JsonNode data) {
        summaryDraftSaved = false;
        additionalRecipientsRequested = false;
        fillSectionAndAdvance("Shipment Info (S)", () -> fillShipmentInfo(data));
        fillSectionAndAdvance("Transport Info (T)", () -> fillTransportInfo(data));
        fillSectionAndAdvance("Party Info (P)", () -> fillPartyInfo(data));
        fillSectionAndAdvance("Invoice (V)", () -> fillInvoiceInfo(data));
        fillSectionAndAdvance("Items (I)", () -> fillItemInfo(data));
        fillSectionAndAdvance("CPC (C)", () -> fillCpcInfo(data));
        fillSummary(data);
    }

    @Override
    protected void completeSection(String sectionName) {
        saveDraftAndAdvanceToNextSection();
    }

    private void fillShipmentInfo(JsonNode data) {
        JsonNode header = data.path("header");
        JsonNode cargo = data.path("cargo");
        JsonNode certificate = data.path("certificate");
        JsonNode license = firstArrayItem(data.path("licence"));
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
        setHiddenComponentValue(
                "app-cargo-type-lookup[formcontrolname='cargoPackingType']",
                "Cargo Type",
                text(cargo, "cargoPackingType"));
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
        setHiddenComponentValue(
                "app-transport-mode-lookup[formcontrolname='modeCode']",
                "Outward Transport Mode",
                text(outwardTransportMode, "modeCode"));
        fillLookupFieldInSection("Declaration Info", "Release Location",
                text(releaseLocation, "locationCode"),
                text(releaseLocation, "locationCode"),
                text(releaseLocation, "locationName"));
        setHiddenComponentValue(
                "app-location-lookup[formcontrolname='locationCode']",
                "Release Location",
                text(releaseLocation, "locationCode"));
        fillLookupFieldInSection("Declaration Info", "Receipt Location",
                text(receiptLocation, "locationCode"),
                text(receiptLocation, "locationCode"),
                text(receiptLocation, "locationName"));
        setHiddenComponentValue(
                "app-location-lookup[formcontrolname='locationCode']",
                "Receipt Location",
                text(receiptLocation, "locationCode"));
        fillLookupFieldInSectionIfPresent("Declaration Info", "Storage Location",
                text(cargo.path("storageLocation"), "locationCode"),
                text(cargo.path("storageLocation"), "locationCode"),
                text(cargo.path("storageLocation"), "locationName"));
        setHiddenComponentValue(
                "app-location-lookup[formcontrolname='locationCode']",
                "Storage Location",
                text(cargo.path("storageLocation"), "locationCode"));

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
                text(data.path("certificateOfOrigin"), "coType"),
                text(certificate, "applicationProductType"));
        if (coType != null && !coType.isBlank()) {
            fillLookupFieldInSectionIfPresent("Certificate of Origin", "CO Type", coType, coType);
            setHiddenComponentValue(
                    "app-application-product-type-lookup[formcontrolname='applicationProductType']",
                    "CO Type",
                    coType);
            fillCertificateOfOrigin(certificate);
        }
        fillLicense(text(license, "referenceID"));
        if (data.path("formMetaData").path("supportingDocumentIsActive").asBoolean(false)) {
            setCheckboxByLabel("Document", true);
        }
    }

    private void fillCertificateOfOrigin(JsonNode certificate) {
        if (certificate == null || certificate.isMissingNode() || certificate.isNull()) {
            return;
        }

        String gspDonorCountry = text(certificate, "gspDonorCountry");
        fillLookupFieldInSectionIfPresent("Certificate of Origin", "GSP Donor Country",
                gspDonorCountry,
                gspDonorCountry);
        setHiddenComponentValue(
                "app-country-code-lookup[formcontrolname='gspDonorCountry'], [formcontrolname='gspDonorCountry']",
                "GSP Donor Country",
                gspDonorCountry);

        fillFieldInSectionIfPresent("Certificate of Origin", "Entry Year", text(certificate, "entryYear"));
        fillFieldInSectionIfPresent("Certificate of Origin", "Preference Content Percent",
                text(certificate, "preferenceContentPercent"));

        String currencyCode = text(certificate, "currencyCode");
        fillLookupFieldInSectionIfPresent("Certificate of Origin", "Currency",
                currencyCode,
                currencyCode);
        setHiddenComponentValue(
                "app-currency-code-lookup[formcontrolname='currencyCode'], [formcontrolname='currencyCode']",
                "Currency",
                currencyCode);

        fillCertificateDetail(certificate.path("certificateDetail"), 0, "Certificate Detail #1");
        fillCertificateDetail(certificate.path("certificateDetail"), 1, "Certificate Detail #2");
        fillRepeatedCertificateText(certificate.path("additionalCertificateDetails"), "Additional Info");
        fillRepeatedCertificateText(certificate.path("transportDetails"), "Transport Detail");
    }

    private void fillCertificateDetail(JsonNode certificateDetails, int index, String labelPrefix) {
        if (!certificateDetails.isArray() || index >= certificateDetails.size()) {
            return;
        }

        JsonNode detail = certificateDetails.get(index);
        if (detail == null || detail.isMissingNode() || detail.isNull()) {
            return;
        }

        String certificateType = text(detail, "certificateType");
        fillLookupFieldInSectionIfPresent("Certificate of Origin", labelPrefix + " - Type",
                certificateType,
                certificateType);
        fillFieldInSectionIfPresent("Certificate of Origin", labelPrefix + " - Copies",
                text(detail, "copiesNumeric"));
    }

    private void fillRepeatedCertificateText(JsonNode values, String labelPrefix) {
        if (!values.isArray()) {
            return;
        }

        for (int index = 0; index < values.size(); index++) {
            String value = normalize(nodeText(values.get(index)));
            if (value == null || value.isBlank()) {
                continue;
            }
            fillFieldInSectionIfPresent("Certificate of Origin", labelPrefix + " #" + (index + 1), value);
        }
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
        JsonNode additionalVesselInformation = outwardTransport.path("additionalVesselInformation");

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
            String inwardBillOfLadingNumber = firstNonBlank(
                    text(inwardTransportMeans, "mawboucroblNumber"),
                    text(inwardTransport, "mawboucroblNumber"));

            fillVerifiedTransportTextField(
                    "Inward Transport Means",
                    "conveyanceReferenceNumber",
                    inwardConveyanceReferenceNumber,
                    "Inward Flight Number",
                    "Flight Number",
                    "Conveyance Reference Number",
                    "Inward Voyage Number");
            fillVerifiedTransportTextField(
                    "Inward Transport Means",
                    "transportIdentifier",
                    inwardTransportIdentifier,
                    "Transport Identifier",
                    "Aircraft Registration Number",
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
            fillVerifiedTransportTextField(
                    "Inward Transport Means",
                    "mawboucroblNumber",
                    inwardBillOfLadingNumber,
                    "Inward Master Air Waybill",
                    "Master Air Waybill",
                    "MAWB/UCR/OBL Number",
                    "Inward Ocean Bill of Lading Number",
                    "Inward Ocean Bill Of Lading Number");
            fillDateFieldInSectionIfPresent("Inward Transport Means", "Arrival Date", formatUiDate(text(inwardTransport, "arrivalDate")));
            fillLookupFieldInSectionIfPresent("Inward Transport Means", "Loading Port",
                    text(inwardTransport, "loadingPort"),
                    text(inwardTransport, "loadingPort"));
            setHiddenComponentValue(
                    "app-loading-port-lookup[formcontrolname='loadingPort']",
                    "Loading Port",
                    text(inwardTransport, "loadingPort"));
        }

        if (resolveSectionOrNull("Outward Transport Means") != null) {
            String outwardConveyanceReferenceNumber = text(outwardTransportMode, "conveyanceReferenceNumber");
            String outwardTransportIdentifier = text(outwardTransportMode, "transportIdentifier");
            String outwardBillOfLadingNumber = firstNonBlank(
                    text(outwardTransportMeans, "mawboucroblNumber"),
                    text(outwardTransport, "mawboucroblNumber"));

            fillVerifiedTransportTextField(
                    "Outward Transport Means",
                    "conveyanceReferenceNumber",
                    outwardConveyanceReferenceNumber,
                    "Outward Flight Number",
                    "Flight Number",
                    "Conveyance Reference Number",
                    "Outward Voyage Number");
            fillVerifiedTransportTextField(
                    "Outward Transport Means",
                    "transportIdentifier",
                    outwardTransportIdentifier,
                    "Transport Identifier",
                    "Aircraft Registration Number",
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
            fillVerifiedTransportTextField(
                    "Outward Transport Means",
                    "mawboucroblNumber",
                    outwardBillOfLadingNumber,
                    "Outward Master Air Waybill",
                    "Master Air Waybill",
                    "MAWB/OUCR/OBL Number",
                    "MAWB/UCR/OBL Number",
                    "Outward Ocean Bill of Lading Number",
                    "Outward Ocean Bill Of Lading Number");
            fillDateFieldInSectionIfPresent("Outward Transport Means", "Departure Date", formatUiDate(text(outwardTransport, "departureDate")));
            fillLookupFieldInSectionIfPresent("Outward Transport Means", "Discharge Port",
                    text(outwardTransport, "dischargePort"),
                    text(outwardTransport, "dischargePort"));
            setHiddenComponentValue(
                    "app-loading-port-lookup[formcontrolname='dischargePort']",
                    "Discharge Port",
                    text(outwardTransport, "dischargePort"));
            fillLookupFieldInSectionIfPresent("Outward Transport Means", "Country of Final Destination",
                    text(outwardTransport, "finalDestinationCountry"),
                    text(outwardTransport, "finalDestinationCountry"));
            setHiddenComponentValue(
                "app-country-code-lookup[formcontrolname='finalDestinationCountry']",
                "Country of Final Destination",
                text(outwardTransport, "finalDestinationCountry"));
        }

        fillAdditionalVesselInformation(additionalVesselInformation);
        fillTransportEquipmentDetails(cargo);
    }

    private void fillAdditionalVesselInformation(JsonNode additionalVesselInformation) {
        if (additionalVesselInformation == null
                || additionalVesselInformation.isMissingNode()
                || additionalVesselInformation.isNull()) {
            return;
        }

        Locator additionalVesselSection = resolveSectionOrNull("Additional Vessel Information");
        if (additionalVesselSection == null) {
            return;
        }

        String vesselType = text(additionalVesselInformation, "vesselType");
        String netRegisterTonnage = text(additionalVesselInformation, "netRegisterTonnage");
        String loadingNextPort = text(additionalVesselInformation, "loadingNextPort");
        String loadingFinalPort = text(additionalVesselInformation, "loadingFinalPort");
        String vesselNationality = firstNonBlank(
                text(additionalVesselInformation, "vesselNationality"),
                text(additionalVesselInformation, "vesselNationalality"));
        JsonNode towingVessel = additionalVesselInformation.path("towingVessel");

        fillLookupFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Vessel Type",
                vesselType,
                vesselType);
        setHiddenComponentValue(
                "[formcontrolname='vesselType']",
                "Vessel Type",
                vesselType);

        fillFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Towing Vessel Voyage Number",
                text(towingVessel, "vesselID"));
        syncVisibleTextComponentValue(
                "vesselID",
                text(towingVessel, "vesselID"),
                "Towing Vessel Voyage Number");

        fillLookupFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Next Port of Call",
                loadingNextPort,
                loadingNextPort);
        setHiddenComponentValue(
                "[formcontrolname='loadingNextPort']",
                "Next Port of Call",
                loadingNextPort);

        fillFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Net Register Tonnage",
                netRegisterTonnage);
        syncVisibleTextComponentValue(
                "netRegisterTonnage",
                netRegisterTonnage,
                "Net Register Tonnage");

        fillFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Towing Vessel Name",
                text(towingVessel, "vesselName"));
        syncVisibleTextComponentValue(
                "vesselName",
                text(towingVessel, "vesselName"),
                "Towing Vessel Name");

        fillLookupFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Final Port of Call",
                loadingFinalPort,
                loadingFinalPort);
        setHiddenComponentValue(
                "[formcontrolname='loadingFinalPort']",
                "Final Port of Call",
                loadingFinalPort);

        fillFieldInSectionIfPresent(
                "Additional Vessel Information",
                "Vessel Nationality",
                vesselNationality);
        syncVisibleTextComponentValue(
                "vesselNationality",
                vesselNationality,
                "Vessel Nationality");
    }

    private void fillTransportEquipmentDetails(JsonNode cargo) {
        JsonNode transportEquipment = firstArrayItem(cargo.path("transportEquipment"));
        if (transportEquipment == null
                || transportEquipment.isMissingNode()
                || transportEquipment.isNull()) {
            return;
        }

        Locator containerDetailsSection = resolveContainerDetailsSectionOrNull();
        if (containerDetailsSection == null) {
            return;
        }

        Locator firstRow = resolveContainerDetailsRowOrNull(containerDetailsSection, "1");
        if (firstRow == null) {
            clickContainerAddButtonIfPresent(containerDetailsSection);
            page.waitForTimeout(300);
            firstRow = resolveContainerDetailsRowOrNull(containerDetailsSection, "1");
        }
        if (firstRow == null) {
            firstRow = containerDetailsSection;
        }

        String containerNumber = normalize(text(transportEquipment, "equipmentID"));
        String sizeTypeCode = text(transportEquipment, "sizeTypeCode");
        String equipmentWeight = text(transportEquipment, "equipmentWeightMeasureNumeric");
        String sealNumber = text(transportEquipment.path("transportEquipmentSeal"), "sealID");

        fillNthFieldInScopeIfPresent(firstRow, 0, containerNumber);
        syncVisibleTextComponentValue("equipmentID", containerNumber, "Container Number");

        fillNthLookupFieldInScopeIfPresent(firstRow, 1, sizeTypeCode, sizeTypeCode);
        setHiddenComponentValue("[formcontrolname='sizeTypeCode']", "Size / Type", sizeTypeCode);

        fillNthFieldInScopeIfPresent(firstRow, 2, equipmentWeight);
        syncVisibleTextComponentValue("equipmentWeightMeasureNumeric", equipmentWeight, "Weight (TNE)");

        fillNthFieldInScopeIfPresent(firstRow, 3, sealNumber);
        syncVisibleTextComponentValue("sealID", sealNumber, "Seal Number");
    }

    private Locator resolveContainerDetailsSectionOrNull() {
        try {
            Locator section = page.locator(
                    "xpath=(//*[contains(normalize-space(translate(., '*', '')), 'Container Details')])[last()]"
                            + "/ancestor::*[.//*[normalize-space(translate(., '*', ''))='Container Number']"
                            + " and .//*[normalize-space(translate(., '*', ''))='Size / Type']"
                            + " and .//*[contains(normalize-space(translate(., '*', '')), 'Weight (TNE)')]"
                            + " and .//*[normalize-space(translate(., '*', ''))='Seal Number']"
                            + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'] or .//button)][1]");
            return firstVisible(section);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Locator resolveContainerDetailsRowOrNull(Locator containerDetailsSection, String sequenceNumber) {
        Locator visibleScope = firstVisible(containerDetailsSection);
        if (visibleScope == null) {
            return null;
        }

        String sequenceLiteral = toXpathLiteral(sequenceNumber);
        Locator numberedRow = visibleScope.locator(
                "xpath=(.//*[normalize-space(.)=" + sequenceLiteral + "]"
                        + "[not(ancestor::*[self::thead or @role='columnheader'])]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1])[last()]");
        Locator visibleNumberedRow = firstVisible(numberedRow);
        if (visibleNumberedRow != null) {
            return visibleNumberedRow;
        }

        Locator bodyRow = visibleScope.locator(
                "xpath=(.//*[normalize-space(translate(., '*', ''))='Container Number']"
                        + "/ancestor::*[self::table or @role='table' or contains(@class, 'table')][1]"
                        + "//*[self::tr or @role='row'][.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']])[1]");
        return firstVisible(bodyRow);
    }

    private void clickContainerAddButtonIfPresent(Locator containerDetailsSection) {
        Locator visibleScope = firstVisible(containerDetailsSection);
        if (visibleScope == null) {
            return;
        }

        Locator buttons = visibleScope.locator("button, [role='button'], input[type='button'], input[type='submit'], a");
        int count = buttons.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = buttons.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            String text = normalize(candidate.innerText()).toUpperCase();
            String ariaLabel = normalize(candidate.getAttribute("aria-label")).toUpperCase();
            String title = normalize(candidate.getAttribute("title")).toUpperCase();
            String value = normalize(candidate.getAttribute("value")).toUpperCase();
            if ("ADD".equals(text) || "ADD".equals(ariaLabel) || "ADD".equals(title) || "ADD".equals(value)) {
                candidate.scrollIntoViewIfNeeded();
                candidate.click(new Locator.ClickOptions().setForce(true));
                return;
            }
        }
    }

    @Override
    protected void fillPartyInfo(JsonNode data) {
        JsonNode party = data.path("party");
        logOutPartyDebug("PARTY", "Start");
        waitForAnyVisibleText(
                "Importer",
                "Inward Carrier",
                "Freight Forwarder",
                "Outward Carrier",
                "Declaring Agent",
                "Exporter",
                "Consignee",
                "End User",
                "Manufacturer");

        fillDeclarantParty(party.path("declarantParty"));

        fillStrictOutPartyRow("Importer", party.path("importerParty"));
        fillStrictOutPartyRow("Inward Carrier", party.path("inwardCarrierAgentParty"));
        fillStrictOutPartyRow("Freight Forwarder", party.path("freightForwarderParty"));
        fillStrictOutPartyRow("Outward Carrier", party.path("outwardCarrierAgentParty"));
        fillStrictOutPartyRow("Declaring Agent", party.path("declaringAgentParty"));

        fillExporterParty(party.path("exporterParty"));

        // Exporter's own address fields (e.g. "Other address details") can toggle open right before this point,
        // reflowing the cards below it; give Angular a moment to settle so Consignee/End User/Manufacturer
        // resolve against the final layout instead of a mid-reflow DOM snapshot.
        waitForPartyCardsSectionSettled();

        fillPartyCard("Consignee", party.path("consigneeParty"));
        fillPartyCard("End User", party.path("endUserParty"));
        fillPartyCard("Manufacturer", party.path("manufacturerParty"));
        logOutPartyDebug("PARTY", "Completed");
    }

    private void waitForPartyCardsSectionSettled() {
        try {
            waitForAnyVisibleText("Consignee", "End User", "Manufacturer");
        } catch (Exception ignored) {
        }
        page.waitForTimeout(400);
    }

    private void fillExporterParty(JsonNode exporterParty) {
        if (resolvePartyCard("Exporter") != null) {
            fillPartyCard("Exporter", exporterParty);
            return;
        }

        fillPartyRowIfPresent("Exporter", exporterParty);
    }

    private void fillDeclarantParty(JsonNode declarantParty) {
        JsonNode personInformation = declarantParty.path("personInformation");
        String declarantCode = text(personInformation, "codeValue");
        String declarantName = text(personInformation, "name");
        String telephone = text(declarantParty, "telephone");
        if ((declarantCode == null || declarantCode.isBlank())
                && (declarantName == null || declarantName.isBlank())
                && (telephone == null || telephone.isBlank())) {
            return;
        }

        Locator partySection = resolvePartyInfoSectionOrNull();
        fillAndVerifyScopedPartyField(
                partySection,
                "Declarant Agent/Party",
                "Code / UEN",
                declarantCode,
                "Declarant Agent / Party Code / UEN",
                "Declarant Agent / Party Code/UEN",
                "Declarant Agent/Party Code / UEN",
                "Declarant Agent/Party Code/UEN",
                "Declarant Party Code / UEN",
                "Declarant Party Code/UEN",
                "Declarant Code / UEN");
        fillAndVerifyScopedPartyField(
                partySection,
                "Declarant Agent/Party",
                "Name",
                declarantName,
                "Declarant Agent / Party Name",
                "Declarant Agent/Party Name",
                "Declarant Party Name",
                "Declarant Name");
        fillAndVerifyScopedPartyField(
                partySection,
                "Declarant Agent/Party",
                "Telephone",
                telephone,
                "Declarant Agent / Party Telephone",
                "Declarant Agent/Party Telephone",
                "Declarant Party Telephone",
                "Declarant Telephone");
    }

    private void fillPartyLookupRow(String rowLabel, JsonNode partyNode) {
        recordPartyOperation("party_row", "fill-out-party-lookup-row", rowLabel, () -> {
            JsonNode identityNode = partyIdentityNode(partyNode);
            String partyName = text(identityNode.path("partyName"), "name");
            String partyId = text(identityNode.path("partyIdentification"), "id");
            fillLookupPartyRow(
                    rowLabel,
                    partyName,
                    partyId);
            logLookupPartyRowValidation(rowLabel, "Party Name", partyName, 0);
            logLookupPartyRowValidation(rowLabel, "UEN / ID", partyId, 1);
        });
    }

    private void fillStrictOutPartyRow(String rowLabel, JsonNode partyNode) {
        recordPartyOperation("party_row", "fill-out-party-row-strict", rowLabel, () -> {
            logOutPartyRowJsonState(rowLabel, partyNode);
            fillStrictPartyRow(rowLabel, partyNode);
            stabilizeStrictOutPartyRow(rowLabel, partyNode);
            JsonNode identityNode = partyIdentityNode(partyNode);
            logLookupPartyRowValidation(rowLabel, "Party Name", partyName(partyNode), 0);
            logLookupPartyRowValidation(
                    rowLabel,
                    "UEN / ID",
                    text(identityNode.path("partyIdentification"), "id"),
                    1);
            logOutPartyRowFinalState(rowLabel);
        });
    }

    private void stabilizeStrictOutPartyRow(String rowLabel, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        String expectedName = partyName(partyNode);
        String expectedId = normalize(text(identityNode.path("partyIdentification"), "id"));
        if ((expectedName == null || expectedName.isBlank()) && (expectedId == null || expectedId.isBlank())) {
            return;
        }

        long stabilizationStartedAt = System.currentTimeMillis();
        int[] retryWaitsMs = {0, 150, 350};
        for (int attempt = 0; attempt < retryWaitsMs.length; attempt++) {
            if (retryWaitsMs[attempt] > 0) {
                page.waitForTimeout(retryWaitsMs[attempt]);
            }

            Locator nameField = resolveOutPartyRowFieldOrNull(rowLabel, 0);
            Locator idField = resolveOutPartyRowFieldOrNull(rowLabel, 1);
            logOutPartyDebug(
                    normalize(rowLabel).toUpperCase(),
                    "Stabilization attempt=" + (attempt + 1)
                            + ", currentName='" + safeRenderedOutPartyValue(nameField)
                            + "', currentUen='" + safeRenderedOutPartyValue(idField)
                            + ", expectedName='" + firstNonBlank(expectedName, "N/A")
                            + "', expectedUen='" + firstNonBlank(expectedId, "N/A") + "'");
            boolean nameExact = expectedName == null
                    || expectedName.isBlank()
                    || waitForExactOutPartyFieldValue(nameField, expectedName, 150);
            boolean idExact = expectedId == null
                    || expectedId.isBlank()
                    || waitForExactOutPartyFieldValue(idField, expectedId, 150);
            if (nameExact && idExact) {
                logOutPartyDebug(
                        normalize(rowLabel).toUpperCase(),
                        "Stabilization skipped; row already exact"
                                + ", attempt=" + (attempt + 1)
                                + ", elapsedMs=" + (System.currentTimeMillis() - stabilizationStartedAt));
                return;
            }

            if (!nameExact) {
                ensureExactOutPartyRowFieldValue(nameField, expectedName);
            }
            if (!idExact) {
                ensureExactOutPartyRowFieldValue(idField, expectedId);
            }
            if (outPartyRowExactlyResolved(nameField, idField, expectedName, expectedId)) {
                logOutPartyDebug(
                        normalize(rowLabel).toUpperCase(),
                        "Stabilization resolved"
                                + ", attempt=" + (attempt + 1)
                                + ", elapsedMs=" + (System.currentTimeMillis() - stabilizationStartedAt));
                return;
            }
        }
        logOutPartyDebug(
                normalize(rowLabel).toUpperCase(),
                "Stabilization exhausted without exact resolution"
                        + ", attempts=" + retryWaitsMs.length
                        + ", elapsedMs=" + (System.currentTimeMillis() - stabilizationStartedAt));
    }

    private boolean outPartyRowExactlyResolved(String rowLabel, String expectedName, String expectedId) {
        Locator nameField = resolveOutPartyRowFieldOrNull(rowLabel, 0);
        Locator idField = resolveOutPartyRowFieldOrNull(rowLabel, 1);
        return outPartyRowExactlyResolved(nameField, idField, expectedName, expectedId);
    }

    private boolean outPartyRowExactlyResolved(
            Locator nameField,
            Locator idField,
            String expectedName,
            String expectedId) {
        boolean nameMatches = expectedName == null
                || expectedName.isBlank()
                || waitForExactOutPartyFieldValue(nameField, expectedName, 150);
        boolean idMatches = expectedId == null
                || expectedId.isBlank()
                || waitForExactOutPartyFieldValue(idField, expectedId, 150);
        return nameMatches && idMatches;
    }

    private void ensureExactOutPartyRowFieldValue(Locator field, String expectedValue) {
        if (field == null || expectedValue == null || expectedValue.isBlank()) {
            return;
        }

        if (waitForExactOutPartyFieldValue(field, expectedValue, 100)) {
            return;
        }

        try {
            field.evaluate("""
                    (element, newValue) => {
                        if ('value' in element) {
                            element.value = newValue;
                            element.setAttribute?.('value', newValue);
                        } else {
                            element.textContent = newValue;
                        }
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        element.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    """, expectedValue);
        } catch (PlaywrightException ignored) {
        }

        if (waitForExactOutPartyFieldValue(field, expectedValue, 300)) {
            return;
        }

        try {
            focusAndType(field, expectedValue, false);
        } catch (Exception ignored) {
        }

        if (waitForExactOutPartyFieldValue(field, expectedValue, 600)) {
            return;
        }

        try {
            field.evaluate("""
                    (element, newValue) => {
                        if ('value' in element) {
                            element.value = newValue;
                            element.setAttribute?.('value', newValue);
                        } else {
                            element.textContent = newValue;
                        }
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        element.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    """, expectedValue);
        } catch (PlaywrightException ignored) {
        }

        waitForExactOutPartyFieldValue(field, expectedValue, 300);
    }

    private boolean waitForExactOutPartyFieldValue(Locator field, String expectedValue, int timeoutMs) {
        if (field == null || expectedValue == null || expectedValue.isBlank()) {
            return false;
        }

        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 100);
        while (System.currentTimeMillis() <= deadline) {
            if (outPartyFieldExactlyMatches(readRenderedFieldValue(field), expectedValue)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private Locator resolveOutPartyRowFieldOrNull(String rowLabel, int occurrence) {
        if (occurrence == 0) {
            Locator componentField = resolveOutPartyNameFieldFromComponentOrNull(rowLabel);
            if (componentField != null) {
                return componentField;
            }
        }

        if (occurrence == 1) {
            Locator componentIdField = resolveOutPartyIdFieldFromComponentOrNull(rowLabel);
            if (componentIdField != null) {
                return componentIdField;
            }
        }

        Locator row = resolvePartyTableRow(rowLabel);
        Locator rowField = row == null ? null : resolveVisibleEditableFieldInRowOrNull(row, occurrence);
        if (rowField != null) {
            return rowField;
        }
        return resolveOutPartyValidationFallbackFieldOrNull(rowLabel, occurrence);
    }

    private Locator resolveOutPartyNameFieldFromComponentOrNull(String rowLabel) {
        String selector = outPartyRowComponentSelector(rowLabel);
        if (selector == null || selector.isBlank()) {
            return null;
        }

        Locator components = page.locator(selector);
        int count = components.count();
        for (int index = 0; index < count; index++) {
            Locator component = components.nth(index);
            Locator nestedField = firstVisible(component.locator(
                    "input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']"));
            if (nestedField != null) {
                return nestedField;
            }
        }
        return null;
    }

    private Locator resolveOutPartyLookupComponentOrNull(String rowLabel) {
        String selector = outPartyRowComponentSelector(rowLabel);
        if (selector == null || selector.isBlank()) {
            return null;
        }

        Locator components = page.locator(selector);
        int count = components.count();
        for (int index = 0; index < count; index++) {
            Locator component = components.nth(index);
            Locator nestedField = firstVisible(component.locator(
                    "input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']"));
            if (nestedField != null) {
                return component;
            }
        }
        return count > 0 ? components.first() : null;
    }

    private Locator resolveOutPartyIdFieldFromComponentOrNull(String rowLabel) {
        Locator nameField = resolveOutPartyNameFieldFromComponentOrNull(rowLabel);
        String selector = outPartyRowComponentSelector(rowLabel);
        if (nameField == null || selector == null || selector.isBlank()) {
            return null;
        }

        Locator directRow = resolvePartyTableRow(rowLabel);
        Locator rowSibling = resolveOutPartyNearestFieldToRightOrNull(directRow, nameField, selector);
        if (rowSibling != null) {
            return rowSibling;
        }

        Locator component = resolveOutPartyLookupComponentOrNull(rowLabel);
        if (component != null) {
            Locator ancestorScopes = component.locator(
                    "xpath=ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > 1]");
            int scopeCount = ancestorScopes.count();
            for (int scopeIndex = 0; scopeIndex < scopeCount; scopeIndex++) {
                Locator ancestorSibling = resolveOutPartyNearestFieldToRightOrNull(
                        ancestorScopes.nth(scopeIndex),
                        nameField,
                        selector);
                if (ancestorSibling != null) {
                    return ancestorSibling;
                }
            }
        }

        return resolveOutPartyNearestFieldToRightOrNull(resolvePartyInfoSectionOrNull(), nameField, selector);
    }

    private Locator resolveOutPartyNearestFieldToRightOrNull(
            Locator scope,
            Locator anchorField,
            String excludedSelector) {
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

        Locator fields = visibleScope.locator("input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']");
        int count = fields.count();
        Locator bestField = null;
        double bestScore = Double.MAX_VALUE;
        double anchorMidY = anchorBox.y + (anchorBox.height / 2.0d);
        double anchorRightX = anchorBox.x + anchorBox.width;

        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()
                    || resolveOutPartyFieldsMatch(candidate, anchorField)
                    || isOutPartyFieldInsideSelector(candidate, excludedSelector)) {
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

    private boolean isOutPartyFieldInsideSelector(Locator field, String selector) {
        if (field == null || selector == null || selector.isBlank()) {
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

    private boolean resolveOutPartyFieldsMatch(Locator left, Locator right) {
        if (left == null || right == null) {
            return false;
        }

        String leftId = safeOutPartyLocatorAttribute(left, "id");
        String rightId = safeOutPartyLocatorAttribute(right, "id");
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

    private String safeOutPartyLocatorAttribute(Locator field, String attributeName) {
        if (field == null || attributeName == null || attributeName.isBlank()) {
            return "";
        }
        try {
            return firstNonBlank(field.getAttribute(attributeName), "");
        } catch (PlaywrightException ignored) {
            return "";
        }
    }

    private boolean syncExactOutPartyLookupComponentState(String rowLabel, String partyName, String partyId) {
        String selector = outPartyRowComponentSelector(rowLabel);
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
                        if (!component) {
                            return false;
                        }

                        const expectedName = args.partyName || '';
                        const expectedId = args.partyId || '';
                        const normalizedExpectedName = normalize(expectedName);
                        const normalizedExpectedId = normalize(expectedId);
                        const allOptions = Array.isArray(component?.allOptions)
                            ? component.allOptions
                            : Array.isArray(component?.options)
                                ? component.options
                                : [];
                        const matched = allOptions.find(option => {
                            const code = normalize(option?.code);
                            const description = normalize(option?.description);
                            return (!!normalizedExpectedId && code === normalizedExpectedId)
                                || (!!normalizedExpectedName
                                    && (description === normalizedExpectedName
                                        || description.includes(normalizedExpectedName)
                                        || normalizedExpectedName.includes(description)));
                        });
                        const selected = {
                            ...(matched || {}),
                            code: matched?.code || expectedId || matched?.description || expectedName,
                            description: matched?.description || expectedName || expectedId
                        };
                        const display = expectedName || selected.description || selected.code || expectedId;
                        const value = selected.code || expectedId || expectedName;

                        if ('value' in component) {
                            component.value = value || component.value;
                        }
                        component._value = value || component._value;
                        if (component.formControl && typeof component.formControl.setValue === 'function') {
                            component.formControl.setValue(value || display);
                        }
                        if (component.control && typeof component.control.setValue === 'function') {
                            component.control.setValue(value || display);
                        }
                        if (typeof component.writeValue === 'function') {
                            component.writeValue(value || display);
                        }
                        if ('selectedItem' in component) {
                            component.selectedItem = selected;
                        }
                        if ('selectedOption' in component) {
                            component.selectedOption = selected;
                        }
                        if ('selectedLabel' in component) {
                            component.selectedLabel = display;
                        }
                        if ('inputDisplayValue' in component) {
                            component.inputDisplayValue = display;
                        }
                        if ('displayValue' in component) {
                            component.displayValue = display;
                        }

                        const field = component.inputElement?.nativeElement
                            || host.querySelector('input, textarea, select');
                        if (typeof component.selectOptionItem === 'function') {
                            component.selectOptionItem(selected);
                        }
                        if (typeof component.selectOptionLabel === 'function') {
                            component.selectOptionLabel(selected);
                        }
                        if (typeof component.onChange === 'function') {
                            component.onChange(value || display);
                        }
                        if (typeof component.onTouched === 'function') {
                            component.onTouched();
                        }
                        if (field && 'value' in field) {
                            field.value = display;
                            field.setAttribute?.('value', display);
                            field.dispatchEvent(new Event('input', { bubbles: true }));
                            field.dispatchEvent(new Event('change', { bubbles: true }));
                            field.dispatchEvent(new Event('blur', { bubbles: true }));
                        }
                        return true;
                    }
                    """, java.util.Map.of(
                    "selector", selector,
                    "partyName", firstNonBlank(partyName, ""),
                    "partyId", firstNonBlank(partyId, "")));
            return Boolean.TRUE.equals(synced);
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private String outPartyRowComponentSelector(String rowLabel) {
        return switch (normalize(rowLabel).toUpperCase()) {
            case "IMPORTER" -> "app-importer-lookup[formcontrolname='name']";
            case "INWARD CARRIER" -> "app-inward-carrier-lookup[formcontrolname='name']";
            case "OUTWARD CARRIER" -> "app-outward-carrier-agent-lookup[formcontrolname='name']";
            case "FREIGHT FORWARDER" -> "app-freight-forwarder-lookup[formcontrolname='name']";
            case "DECLARING AGENT" -> "app-declaring-agent-lookup[formcontrolname='name']";
            default -> null;
        };
    }

    private void reconcileLookupPartyComponentIfNeeded(String rowLabel, JsonNode partyNode) {
        recordPartyOperation("party_reconciliation", "reconcile-out-lookup-party-row", rowLabel, () -> {
            JsonNode identityNode = partyIdentityNode(partyNode);
            String resolvedPartyName = partyName(partyNode);
            String resolvedPartyId = text(identityNode.path("partyIdentification"), "id");
            if ((resolvedPartyName == null || resolvedPartyName.isBlank())
                    && (resolvedPartyId == null || resolvedPartyId.isBlank())) {
                return;
            }

            Locator row = resolvePartyTableRow(rowLabel);
            if (row != null) {
                Locator nameField = resolveVisibleEditableFieldInRowOrNull(row, 0);
                Locator idField = resolveVisibleEditableFieldInRowOrNull(row, 1);
                if (isPartyPerformanceAlignmentEnabled()
                        ? lookupPartyRowResolvedSnapshot(row, nameField, idField, resolvedPartyName, resolvedPartyId)
                        : lookupPartyRowResolved(row, nameField, idField, resolvedPartyName, resolvedPartyId, 800)) {
                    return;
                }
            }

            boolean componentResolved = syncPartyLookupComponentSelection(rowLabel, resolvedPartyName, resolvedPartyId);
            if (componentResolved && row != null) {
                Locator nameField = resolveVisibleEditableFieldInRowOrNull(row, 0);
                Locator idField = resolveVisibleEditableFieldInRowOrNull(row, 1);
                if (lookupPartyRowResolved(row, nameField, idField, resolvedPartyName, resolvedPartyId, 1200)) {
                    return;
                }
            }

            logFieldMappingWarning("OUT lookup row '" + rowLabel
                    + "' did not fully resolve through the lightweight row/component path. "
                    + "Reapplying with the stricter party-row mapper.");
            fillPartyRowIfPresent(rowLabel, partyNode);
        });
    }

    private JsonNode partyIdentityNode(JsonNode partyNode) {
        JsonNode partyDetail = partyNode.path("partyDetail");
        if (!isMissingOrEmpty(partyDetail)) {
            return partyDetail;
        }
        return partyNode;
    }

    private JsonNode partyAddressNode(JsonNode partyNode) {
        JsonNode addressNode = partyNode.path("address");
        if (!isMissingOrEmpty(addressNode)) {
            return addressNode;
        }
        return partyNode;
    }

    private void fillPartyCard(String title, JsonNode partyNode) {
        recordPartyOperation("party_card", "fill-out-party-card", title, () -> {
            JsonNode identityNode = partyIdentityNode(partyNode);
            JsonNode addressNode = partyAddressNode(partyNode);

            String name = normalize(firstNonBlank(
                    partyName(partyNode),
                    text(identityNode, "name"),
                    text(partyNode, "name")));
            String id = normalize(firstNonBlank(
                    text(identityNode.path("partyIdentification"), "id"),
                    text(identityNode, "id"),
                    text(partyNode.path("partyIdentification"), "id"),
                    text(partyNode, "id")));
            if ((name == null || name.isBlank())
                    && (id == null || id.isBlank())
                    && isMissingOrEmpty(addressNode)) {
                return;
            }

            logOutPartyDebug(
                    normalize(title).toUpperCase(),
                    "Card JSON name='" + firstNonBlank(name, "N/A")
                            + "', uen='" + firstNonBlank(id, "N/A") + "'");

            logFieldMappingInfo("OUT party card '" + title + "' JSON value -> name='"
                    + firstNonBlank(name, "N/A")
                    + "', id='" + firstNonBlank(id, "N/A")
                    + "', hasAddress=" + !isMissingOrEmpty(addressNode));

            ensureAccordionExpanded(title, "Country Code", "Address");
            Locator card = resolvePartyCard(title);
            for (int attempt = 0; card == null && attempt < 4; attempt++) {
                page.waitForTimeout(300L * (attempt + 1));
                ensureAccordionExpanded(title, "Country Code", "Address");
                card = resolvePartyCard(title);
            }
            if (card == null) {
                boolean globalSync = syncPartyLookupComponentSelection(title, name, id);
                if (globalSync) {
                    logFieldMappingInfo("OUT party card '" + title
                            + "' lookup host resolved before card container discovery.");
                    page.waitForTimeout(250);
                    ensureAccordionExpanded(title, "Country Code", "Address");
                    card = resolvePartyCard(title);
                }
            }
            if (card == null) {
                logFieldMappingWarning("OUT party card container was not found for '" + title
                        + "' while JSON value was '" + firstNonBlank(name, id, "N/A")
                        + "'. Party Info section text -> " + describePartyInfoSectionTextOrNull() + ".");
                return;
            }
            try {
                card.scrollIntoViewIfNeeded();
            } catch (Exception ignored) {
            }
            page.waitForTimeout(250);
            Locator visibleCardAfterScroll = resolvePartyCard(title);
            if (visibleCardAfterScroll != null) {
                card = visibleCardAfterScroll;
            }
            try {
                logFieldMappingInfo("OUT party card '" + title + "' scope text -> " + normalize(card.innerText()));
            } catch (Exception ignored) {
            }
            logOutPartyCardScopeDiagnostics(title, card);

            boolean synced = syncPartyLookupComponentSelectionInScope(card, title, name, id);
            if (!synced) {
                synced = syncPartyLookupComponentSelection(title, name, id);
            }
            if (!synced) {
                fillOutPartyCardNameLookupIfPresent(card, title, name, id);
            }
            if (!synced) {
                synced = syncPartyLookupComponentSelectionInScope(card, title, name, id);
            }
            logFieldMappingInfo("OUT party card '" + title + "' lookup sync -> resolved=" + synced
                    + ", cardScopeFound=" + (card != null));
            ensurePartyCardNameValue(card, title, name);
            logPartyFieldVerification(title, "Party Name", name, resolvePartyCardNameField(card, title), null);
            fillAndVerifyOutPartyCardField(card, title, "UEN", id, true, "UEN", "Code / UEN", "ID / UEN", "ID");

            JsonNode addressLines = addressNode.path("addressLine").path("line");
            String addressLine1 = arrayText(addressLines, 0);
            String addressLine2 = arrayText(addressLines, 1);
            String city = text(addressNode, "cityName");
            String subdivisionCode = firstNonBlank(
                    text(addressNode, "countrySubentityCode"),
                    text(addressNode, "countrySubdivisionCode"));
            String subdivisionName = firstNonBlank(
                    text(addressNode, "countrySubentity"),
                    text(addressNode, "countrySubdivision"),
                    text(addressNode, "countrySubdivisionName"));
            String postalCode = firstNonBlank(text(addressNode, "postalZone"), subdivisionCode);
            String compactAddress = joinNonBlank(", ", addressLine1, addressLine2, city);

            fillAndVerifyOutPartyCardField(card, title, "Address", compactAddress, false, "Address");
            fillAndVerifyOutPartyCardField(card, title, "Address Line 1", addressLine1, false, "Address Line 1", "Address 1");
            fillAndVerifyOutPartyCardField(card, title, "Address Line 2", addressLine2, false, "Address Line 2", "Address 2");
            fillAndVerifyOutPartyCardField(card, title, "City", city, false, "City");
            fillAndVerifyOutPartyCardField(
                    card,
                    title,
                    "Subdivision Code",
                    subdivisionCode,
                    false,
                    "Subdivision Code",
                    "State Code",
                    "Province Code",
                    "Country Subdivision Code");
            fillAndVerifyOutPartyCardField(
                    card,
                    title,
                    "Subdivision Name",
                    subdivisionName,
                    false,
                    "Subdivision Name",
                    "Subdivision",
                    "State / Province",
                    "State",
                    "Province",
                    "Country Subdivision");
            fillAndVerifyOutPartyCardField(card, title, "Postal Code", postalCode, false, "Postal Code", "Postal");
            fillAndVerifyOutPartyCardField(card, title, "Country Code", text(addressNode, "countryCode"), false, "Country Code", "Country");
        });
    }

    private boolean syncPartyLookupComponentSelectionInScope(Locator scope, String title, String partyName, String partyId) {
        if (scope == null || (partyName == null || partyName.isBlank()) && (partyId == null || partyId.isBlank())) {
            return false;
        }

        Locator preferredHost = resolvePartyCardNameLookupHost(scope, title);
        if (preferredHost != null && syncPartyLookupComponentValue(preferredHost, partyName, partyId)) {
            return true;
        }
        logOutPartyDebug(
                normalize(title).toUpperCase(),
                "Scoped component sync skipped because no title-specific lookup host was found in the resolved card scope");
        return false;
    }

    private boolean syncPartyLookupComponentValue(Locator host, String partyName, String partyId) {
        try {
            Object synced = host.evaluate("""
                    (element, args) => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        if (typeof window.ng === 'undefined' || typeof window.ng.getComponent !== 'function') {
                            return false;
                        }

                        const component = window.ng.getComponent(element);
                        if (!component) {
                            return false;
                        }

                        const expectedName = normalize(args.partyName);
                        const expectedId = normalize(args.partyId);
                        const allOptions = Array.isArray(component?.allOptions)
                            ? component.allOptions
                            : Array.isArray(component?.options)
                                ? component.options
                                : [];
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

                        const ranked = allOptions
                            .map(option => ({ option, score: scoreOption(option) }))
                            .filter(entry => entry.score > 0)
                            .sort((left, right) => right.score - left.score);
                        const selected = ranked[0]?.option || {
                            code: args.partyId || args.partyName || '',
                            description: args.partyName || args.partyId || ''
                        };
                        const display = selected.description || selected.code || args.partyName || args.partyId || '';
                        const value = selected.code || display;
                        const field = component?.inputElement?.nativeElement
                            || element.querySelector?.("input, textarea, select, [role='combobox'], [role='textbox']");

                        if ('value' in component) {
                            component.value = value;
                        }
                        if ('_value' in component) {
                            component._value = value;
                        }
                        if (component.formControl && typeof component.formControl.setValue === 'function') {
                            component.formControl.setValue(value);
                        }
                        if (component.control && typeof component.control.setValue === 'function') {
                            component.control.setValue(value);
                        }
                        if (typeof component.writeValue === 'function') {
                            component.writeValue(value);
                        }
                        if ('selectedItem' in component) {
                            component.selectedItem = selected;
                        }
                        if ('selectedOption' in component) {
                            component.selectedOption = selected;
                        }
                        if ('selectedLabel' in component) {
                            component.selectedLabel = display;
                        }
                        if ('inputDisplayValue' in component) {
                            component.inputDisplayValue = display;
                        }
                        if ('displayValue' in component) {
                            component.displayValue = display;
                        }
                        if (field && 'value' in field) {
                            field.value = display;
                            field.setAttribute?.('value', display);
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
                            component.onChange(value);
                        }
                        if (typeof component.onTouched === 'function') {
                            component.onTouched();
                        }
                        return true;
                    }
                    """, java.util.Map.of(
                    "partyName", firstNonBlank(partyName, ""),
                    "partyId", firstNonBlank(partyId, "")));
            return Boolean.TRUE.equals(synced);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasVisiblePartyLookupHost(Locator scope) {
        if (scope == null) {
            return false;
        }
        Locator host = firstVisible(scope.locator(
                "app-importer-lookup[formcontrolname='name'], "
                        + "app-exporter-lookup[formcontrolname='name'], "
                        + "app-inward-carrier-lookup[formcontrolname='name'], "
                        + "app-outward-carrier-agent-lookup[formcontrolname='name'], "
                        + "app-freight-forwarder-lookup[formcontrolname='name'], "
                        + "app-declaring-agent-lookup[formcontrolname='name'], "
                        + "app-consignee-lookup[formcontrolname='name'], "
                        + "app-end-user-lookup[formcontrolname='name'], "
                        + "app-manufacturer-lookup[formcontrolname='name']"));
        return host != null;
    }

    private void ensurePartyCardNameValue(Locator card, String title, String expectedName) {
        if (card == null || expectedName == null || expectedName.isBlank()) {
            return;
        }

        Locator field = resolvePartyCardNameField(card, title);
        if (field == null) {
            return;
        }

        String currentValue = normalize(readRenderedFieldValue(field));
        String normalizedExpected = normalize(expectedName);
        if (!currentValue.isBlank()
                && (currentValue.equalsIgnoreCase(normalizedExpected)
                || currentValue.contains(normalizedExpected)
                || normalizedExpected.contains(currentValue))) {
            return;
        }

        ensureTextFieldValue(field, expectedName);
    }

    private Locator resolvePartyCardNameField(Locator card, String title) {
        Locator preferredHost = resolvePartyCardNameLookupHost(card, title);
        if (preferredHost != null) {
            Locator preferredField = firstVisible(preferredHost.locator(
                    "input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']"));
            if (preferredField != null) {
                return preferredField;
            }
        }

        Locator titleLinkedField = resolveOutPartyCardNameFieldAfterTitle(card, title);
        if (titleLinkedField != null) {
            return titleLinkedField;
        }

        if (outPartyCardScopeContainsMultipleTitles(card, title)) {
            logOutPartyDebug(
                    normalize(title).toUpperCase(),
                    "Blocked broad card-name fallback because the resolved scope contains multiple party titles");
            return null;
        }

        return firstVisible(card.locator(
                "input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']"));
    }

    private Locator resolveOutPartyCardNameFieldAfterTitle(Locator card, String title) {
        return resolveOutPartyCardScopedFieldOrNull(card, title, title);
    }

    private Locator resolvePartyCardNameLookupHost(Locator scope, String title) {
        if (scope == null || title == null || title.isBlank()) {
            return null;
        }

        String selector = switch (normalize(title).toUpperCase()) {
            case "EXPORTER" -> "app-exporter-lookup[formcontrolname='name']";
            case "CONSIGNEE" -> "app-consignee-lookup[formcontrolname='name']";
            case "END USER" -> "app-end-user-lookup[formcontrolname='name']";
            case "MANUFACTURER" -> "app-manufacturer-lookup[formcontrolname='name']";
            default -> null;
        };
        if (selector == null) {
            return null;
        }

        return firstVisible(scope.locator(selector));
    }

    private void fillPartyCardFieldWithFallback(Locator card, String label, String value) {
        if (card == null || value == null || value.isBlank()) {
            return;
        }

        fillFieldAfterScopeLabelIfPresent(card, label, 0, value);
        Locator field = resolvePartyCardFieldAfterLabelOrNull(card, label, 0);
        if (field == null) {
            return;
        }

        String currentValue = normalize(readRenderedFieldValue(field));
        String normalizedExpected = normalize(value);
        if (!currentValue.isBlank()
                && (currentValue.equalsIgnoreCase(normalizedExpected)
                || currentValue.contains(normalizedExpected)
                || normalizedExpected.contains(currentValue))) {
            return;
        }

        focusAndType(field, value, false);
        ensureTextFieldValue(field, value);
    }

    private void fillAndVerifyScopedPartyField(
            Locator scope,
            String partyName,
            String fieldName,
            String expectedValue,
            String... labelCandidates) {
        fillAndVerifyScopedPartyField(scope, partyName, fieldName, expectedValue, true, false, labelCandidates);
    }

    private void fillAndVerifyScopedPartyField(
            Locator scope,
            String partyName,
            String fieldName,
            String expectedValue,
            boolean allowGlobalFallback,
            boolean exactMatch,
            String... labelCandidates) {
        if (expectedValue == null || expectedValue.isBlank()) {
            logFieldMappingInfo("Party: " + partyName
                    + " | Field: " + fieldName
                    + " | Expected: not provided | Actual: not provided | SKIP - Not provided");
            return;
        }

        Locator field = resolvePartyFieldByLabels(scope, allowGlobalFallback, labelCandidates);
        if (field == null) {
            String failureReason = "FAIL - UI field not found";
            logFieldMappingWarning("Party: " + partyName
                    + " | Field: " + fieldName
                    + " | Expected: " + expectedValue
                    + " | Actual: <field-not-found> | " + failureReason
                    + " | Labels tried: " + String.join(", ", labelCandidates));
            automationDiagnostics.recordFieldVerificationFailure(
                    "Party '" + partyName + "' field '" + fieldName + "' was not found in the OUT Party tab.",
                    expectedValue,
                    "<field-not-found>");
            return;
        }

        String actualValue = normalize(readRenderedFieldValue(field));
        boolean fieldMatches = exactMatch
                ? outPartyFieldExactlyMatches(actualValue, expectedValue)
                : partyFieldMatches(actualValue, expectedValue);
        if (!fieldMatches) {
            focusAndType(field, expectedValue, false);
            boolean rendered = exactMatch
                    ? waitForExactOutPartyFieldValue(field, expectedValue, 1200)
                    : waitForAnyRenderedFieldValue(field, 1200, expectedValue);
            if (!rendered) {
                ensureTextFieldValue(field, expectedValue);
            }
        }

        if (exactMatch) {
            logExactLookupPartyFieldVerification(partyName, fieldName, expectedValue, field, null);
            return;
        }
        logPartyFieldVerification(partyName, fieldName, expectedValue, field, null);
    }

    private Locator resolvePartyFieldByLabels(Locator scope, boolean allowGlobalFallback, String... labelCandidates) {
        if (labelCandidates == null || labelCandidates.length == 0) {
            return null;
        }

        if (scope != null) {
            for (String labelCandidate : labelCandidates) {
                Locator scopedField = resolvePartyCardFieldAfterLabelOrNull(scope, labelCandidate, 0);
                if (scopedField != null) {
                    return scopedField;
                }
            }
        }

        if (!allowGlobalFallback) {
            return null;
        }

        for (String labelCandidate : labelCandidates) {
            Locator field = resolveFieldByLabelOrNull(labelCandidate, 0);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private void fillAndVerifyOutPartyCardField(
            Locator card,
            String title,
            String fieldName,
            String expectedValue,
            boolean exactMatch,
            String... labelCandidates) {
        if (expectedValue == null || expectedValue.isBlank()) {
            logFieldMappingInfo("Party: " + title
                    + " | Field: " + fieldName
                    + " | Expected: not provided | Actual: not provided | SKIP - Not provided");
            return;
        }

        Locator field = resolveOutPartyCardFieldByLabels(card, title, labelCandidates);
        if (field == null) {
            String failureReason = "FAIL - UI field not found";
            logFieldMappingWarning("Party: " + title
                    + " | Field: " + fieldName
                    + " | Expected: " + expectedValue
                    + " | Actual: <field-not-found> | " + failureReason
                    + " | Labels tried: " + String.join(", ", labelCandidates));
            automationDiagnostics.recordFieldVerificationFailure(
                    "Party '" + title + "' field '" + fieldName + "' was not found in the OUT Party card.",
                    expectedValue,
                    "<field-not-found>");
            return;
        }

        String actualValue = normalize(readRenderedFieldValue(field));
        boolean fieldMatches = exactMatch
                ? outPartyFieldExactlyMatches(actualValue, expectedValue)
                : partyFieldMatches(actualValue, expectedValue);
        if (!fieldMatches) {
            focusAndType(field, expectedValue, false);
            boolean rendered = exactMatch
                    ? waitForExactOutPartyFieldValue(field, expectedValue, 1200)
                    : waitForAnyRenderedFieldValue(field, 1200, expectedValue);
            if (!rendered) {
                ensureTextFieldValue(field, expectedValue);
            }
        }

        if (exactMatch) {
            logExactLookupPartyFieldVerification(title, fieldName, expectedValue, field, null);
            return;
        }
        logPartyFieldVerification(title, fieldName, expectedValue, field, null);
    }

    private Locator resolveOutPartyCardFieldByLabels(Locator card, String title, String... labelCandidates) {
        if (labelCandidates == null || labelCandidates.length == 0) {
            return null;
        }

        for (String labelCandidate : labelCandidates) {
            Locator scopedField = resolveOutPartyCardScopedFieldOrNull(card, title, labelCandidate);
            if (scopedField != null) {
                return scopedField;
            }
        }

        if (!outPartyCardScopeContainsMultipleTitles(card, title)) {
            for (String labelCandidate : labelCandidates) {
                Locator fallbackField = resolvePartyCardFieldAfterLabelOrNull(card, labelCandidate, 0);
                if (fallbackField != null) {
                    return fallbackField;
                }
            }
        }

        return null;
    }

    private void logLookupPartyRowValidation(String partyName, String fieldName, String expectedValue, int fieldIndex) {
        if (expectedValue == null || expectedValue.isBlank()) {
            logFieldMappingInfo("Party: " + partyName
                    + " | Field: " + fieldName
                    + " | Expected: not provided | Actual: not provided | SKIP - Not provided");
            return;
        }

        Locator row = resolveValidationPartyRowContainerOrNull(partyName);
        Locator field = resolveValidationPartyFieldOrNull(partyName, fieldIndex);
        String fallbackActual = row == null ? null : normalize(row.innerText());
        logExactLookupPartyFieldVerification(partyName, fieldName, expectedValue, field, fallbackActual);
    }

    private void logExactLookupPartyFieldVerification(
            String partyName,
            String fieldName,
            String expectedValue,
            Locator field,
            String fallbackActualValue) {
        String actualValue = field == null
                ? normalize(fallbackActualValue)
                : normalize(readRenderedFieldValue(field));
        if ((actualValue == null || actualValue.isBlank()) && fallbackActualValue != null && !fallbackActualValue.isBlank()) {
            actualValue = normalize(fallbackActualValue);
        }

        if (outPartyFieldExactlyMatches(actualValue, expectedValue)) {
            logFieldMappingInfo("Party: " + partyName
                    + " | Field: " + fieldName
                    + " | Expected: " + expectedValue
                    + " | Actual: " + firstNonBlank(actualValue, "<empty>")
                    + " | PASS");
            return;
        }

        String resolvedActualValue = firstNonBlank(actualValue, "<empty>");
        logFieldMappingWarning("Party: " + partyName
                + " | Field: " + fieldName
                + " | Expected: " + expectedValue
                + " | Actual: " + resolvedActualValue
                + " | FAIL - Value Missing");
        automationDiagnostics.recordFieldVerificationFailure(
                "Party '" + partyName + "' field '" + fieldName + "' did not match the JSON value.",
                expectedValue,
                resolvedActualValue);
    }

    private Locator resolveValidationPartyFieldOrNull(String rowLabel, int occurrence) {
        return resolveOutPartyRowFieldOrNull(rowLabel, occurrence);
    }

    private Locator resolveOutPartyValidationFallbackFieldOrNull(String rowLabel, int occurrence) {
        Locator row = resolvePartyTableRow(rowLabel);
        Locator rowField = row == null ? null : resolveVisibleEditableFieldInRowOrNull(row, occurrence);
        if (rowField != null) {
            return rowField;
        }

        Locator alignedField = resolveAlignedPartyFieldOrNull(rowLabel, occurrence);
        if (alignedField != null) {
            return alignedField;
        }

        Locator validationRow = resolveValidationPartyRowContainerOrNull(rowLabel);
        return validationRow == null ? null : resolveVisibleEditableFieldInRowOrNull(validationRow, occurrence);
    }

    private Locator resolveValidationPartyRowContainerOrNull(String rowLabel) {
        Locator section = resolvePartyInfoSectionOrNull();
        Locator label = resolveValidationPartyRowLabelOrNull(section, rowLabel);
        if (label == null) {
            return resolvePartyTableRow(rowLabel);
        }

        Locator nearestAncestorRow = label.locator(
                "xpath=ancestor::*[count(.//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox']) > 1][1]");
        Locator visibleNearestAncestorRow = firstVisible(nearestAncestorRow);
        return visibleNearestAncestorRow != null ? visibleNearestAncestorRow : resolvePartyTableRow(rowLabel);
    }

    private Locator resolveAlignedPartyFieldOrNull(String rowLabel, int occurrence) {
        Locator section = resolvePartyInfoSectionOrNull();
        Locator label = resolveValidationPartyRowLabelOrNull(section, rowLabel);
        if (section == null || label == null) {
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

        Locator fields = section.locator("input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']");
        List<ValidationPositionedField> rowFields = new ArrayList<>();
        int count = fields.count();
        double labelMidY = labelBox.y + (labelBox.height / 2.0d);
        for (int index = 0; index < count; index++) {
            Locator candidate = fields.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }

            BoundingBox fieldBox;
            try {
                fieldBox = candidate.boundingBox();
            } catch (PlaywrightException ignored) {
                continue;
            }
            if (fieldBox == null) {
                continue;
            }

            double fieldMidY = fieldBox.y + (fieldBox.height / 2.0d);
            if (Math.abs(fieldMidY - labelMidY) <= 28.0d) {
                rowFields.add(new ValidationPositionedField(index, fieldBox.x, fieldBox.y));
            }
        }

        if (occurrence < 0 || occurrence >= rowFields.size()) {
            return null;
        }

        rowFields.sort(Comparator.comparingDouble(ValidationPositionedField::x).thenComparingDouble(ValidationPositionedField::y));
        return fields.nth(rowFields.get(occurrence).index());
    }

    private Locator resolveValidationPartyRowLabelOrNull(Locator section, String rowLabel) {
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

    private void logPartyFieldVerification(
            String partyName,
            String fieldName,
            String expectedValue,
            Locator field,
            String fallbackActualValue) {
        if (expectedValue == null || expectedValue.isBlank()) {
            logFieldMappingInfo("Party: " + partyName
                    + " | Field: " + fieldName
                    + " | Expected: not provided | Actual: not provided | SKIP - Not provided");
            return;
        }

        String actualValue = field == null
                ? normalize(fallbackActualValue)
                : normalize(readRenderedFieldValue(field));
        if ((actualValue == null || actualValue.isBlank()) && fallbackActualValue != null && !fallbackActualValue.isBlank()) {
            actualValue = normalize(fallbackActualValue);
        }

        if (partyFieldMatches(actualValue, expectedValue)) {
            logFieldMappingInfo("Party: " + partyName
                    + " | Field: " + fieldName
                    + " | Expected: " + expectedValue
                    + " | Actual: " + firstNonBlank(actualValue, "<empty>")
                    + " | PASS");
            return;
        }

        String resolvedActualValue = firstNonBlank(actualValue, "<empty>");
        logFieldMappingWarning("Party: " + partyName
                + " | Field: " + fieldName
                + " | Expected: " + expectedValue
                + " | Actual: " + resolvedActualValue
                + " | FAIL - Value Missing");
        automationDiagnostics.recordFieldVerificationFailure(
                "Party '" + partyName + "' field '" + fieldName + "' did not match the JSON value.",
                expectedValue,
                resolvedActualValue);
    }

    private boolean partyFieldMatches(String actualValue, String expectedValue) {
        String normalizedActual = normalize(actualValue);
        String normalizedExpected = normalize(expectedValue);
        if (normalizedActual == null || normalizedExpected == null
                || normalizedActual.isBlank() || normalizedExpected.isBlank()) {
            return false;
        }
        if (normalizedActual.equalsIgnoreCase(normalizedExpected)
                || normalizedActual.contains(normalizedExpected)
                || normalizedExpected.contains(normalizedActual)) {
            return true;
        }

        String commaInsensitiveActual = normalize(normalizedActual.replace(",", " "));
        String commaInsensitiveExpected = normalize(normalizedExpected.replace(",", " "));
        return commaInsensitiveActual != null
                && commaInsensitiveExpected != null
                && !commaInsensitiveActual.isBlank()
                && !commaInsensitiveExpected.isBlank()
                && (commaInsensitiveActual.equalsIgnoreCase(commaInsensitiveExpected)
                || commaInsensitiveActual.contains(commaInsensitiveExpected)
                || commaInsensitiveExpected.contains(commaInsensitiveActual));
    }

    private boolean outPartyFieldExactlyMatches(String actualValue, String expectedValue) {
        String normalizedActual = normalize(actualValue);
        String normalizedExpected = normalize(expectedValue);
        if (normalizedActual == null || normalizedExpected == null
                || normalizedActual.isBlank() || normalizedExpected.isBlank()) {
            return false;
        }
        if (normalizedActual.equalsIgnoreCase(normalizedExpected)) {
            return true;
        }

        String commaInsensitiveActual = normalize(normalizedActual.replace(",", " "));
        String commaInsensitiveExpected = normalize(normalizedExpected.replace(",", " "));
        return commaInsensitiveActual != null
                && commaInsensitiveExpected != null
                && !commaInsensitiveActual.isBlank()
                && !commaInsensitiveExpected.isBlank()
                && commaInsensitiveActual.equalsIgnoreCase(commaInsensitiveExpected);
    }

    private Locator resolvePartyCardFieldAfterLabelOrNull(Locator card, String label, int occurrence) {
        if (card == null || label == null || label.isBlank()) {
            return null;
        }

        String escapedLabel = toXpathLiteral(label);
        Locator inlineField = card.locator(
                "xpath=((.//*[normalize-space(translate(., '*', ''))=" + escapedLabel + "])[" + (occurrence + 1) + "]"
                        + "//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox'][1])");
        Locator visibleInlineField = firstVisible(inlineField);
        if (visibleInlineField != null) {
            return visibleInlineField;
        }

        Locator scopedField = card.locator(
                "xpath=((.//*[normalize-space(translate(., '*', ''))=" + escapedLabel + "])[" + (occurrence + 1) + "]"
                        + "/parent::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]"
                        + "//*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox'][1])");
        Locator visibleScopedField = firstVisible(scopedField);
        if (visibleScopedField != null) {
            return visibleScopedField;
        }

        Locator directField = card.locator(
                "xpath=((.//*[normalize-space(translate(., '*', ''))=" + escapedLabel + "])[" + (occurrence + 1) + "]"
                        + "/following::*[self::input or self::textarea or self::select or @role='combobox' or @role='textbox'][1])");
        Locator visibleDirectField = firstVisible(directField);
        if (visibleDirectField != null) {
            return visibleDirectField;
        }

        int fallbackOccurrence = switch (normalize(label).toUpperCase()) {
            case "ADDRESS", "ADDRESS LINE 1" -> 2;
            case "COUNTRY CODE" -> 3;
            default -> -1;
        };
        if (fallbackOccurrence < 0) {
            return null;
        }

        Locator fallbackField = card.locator(
                "input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']")
                .nth(fallbackOccurrence);
        return firstVisible(fallbackField);
    }

    private void fillOutPartyCardNameLookupIfPresent(Locator card, String title, String partyName, String partyId) {
        if (card == null || partyName == null || partyName.isBlank()) {
            return;
        }

        Locator field = resolvePartyCardNameField(card, title);
        if (field == null) {
            logOutPartyDebug(normalize(title).toUpperCase(), "Card name field could not be resolved");
            return;
        }
        if (waitForAnyRenderedFieldValue(field, 150, partyName)) {
            return;
        }

        try {
            focusAndTypeByClickOnly(field, partyName, partyName, partyId);
        } catch (IllegalStateException exception) {
            logOutPartyDebug(
                    normalize(title).toUpperCase(),
                    "Lookup click-only selection did not fully resolve card field. Cause: " + exception.getMessage());
        }

        if (!waitForAnyRenderedFieldValue(field, 1000, partyName)) {
            ensureTextFieldValue(field, partyName);
        }
    }

    private Locator resolveOutPartyCardScopedFieldOrNull(Locator card, String title, String label) {
        if (card == null || title == null || title.isBlank() || label == null || label.isBlank()) {
            return null;
        }

        String marker = "out-party-field-" + java.util.UUID.randomUUID();
        try {
            Object resolved = card.evaluate("""
                    (scope, args) => {
                        const normalize = value => (value || '')
                            .replace(/\\*/g, ' ')
                            .replace(/\\s+/g, ' ')
                            .trim()
                            .toUpperCase();
                        const knownPartyTitles = new Set([
                            'IMPORTER',
                            'INWARD CARRIER',
                            'FREIGHT FORWARDER',
                            'OUTWARD CARRIER',
                            'DECLARING AGENT',
                            'EXPORTER',
                            'CONSIGNEE',
                            'END USER',
                            'MANUFACTURER'
                        ]);
                        const isVisible = element => !!element
                            && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const ownText = element => normalize(Array.from(element.childNodes || [])
                            .filter(node => node.nodeType === Node.TEXT_NODE)
                            .map(node => node.textContent || '')
                            .join(' '));
                        const isEditable = element => element
                            && (element.matches?.("input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']")
                                || element.matches?.("[contenteditable='true']"));

                        scope.querySelectorAll(`[data-out-party-field='${args.marker}']`)
                            .forEach(node => node.removeAttribute('data-out-party-field'));

                        const expectedTitle = normalize(args.title);
                        const expectedLabel = normalize(args.label);
                        const visibleElements = [scope, ...Array.from(scope.querySelectorAll('*'))].filter(isVisible);
                        const titleNode = visibleElements.find(element => ownText(element) === expectedTitle);
                        if (!titleNode) {
                            return false;
                        }

                        const candidates = [];
                        let started = false;
                        const walker = document.createTreeWalker(scope, NodeFilter.SHOW_ELEMENT);
                        let current = walker.currentNode;
                        while (current) {
                            const element = current;
                            if (!isVisible(element)) {
                                current = walker.nextNode();
                                continue;
                            }
                            if (element === titleNode) {
                                started = true;
                                current = walker.nextNode();
                                continue;
                            }
                            if (!started) {
                                current = walker.nextNode();
                                continue;
                            }

                            const direct = ownText(element);
                            if (direct && knownPartyTitles.has(direct) && direct !== expectedTitle) {
                                break;
                            }
                            candidates.push(element);
                            current = walker.nextNode();
                        }

                        if (expectedLabel === expectedTitle) {
                            const directField = candidates.find(isEditable);
                            if (!directField) {
                                return false;
                            }
                            directField.setAttribute('data-out-party-field', args.marker);
                            return true;
                        }

                        const labelNode = candidates.find(element => ownText(element) === expectedLabel);
                        if (!labelNode) {
                            return false;
                        }
                        const labelIndex = candidates.indexOf(labelNode);

                        const inlineField = [labelNode, ...Array.from(labelNode.querySelectorAll('*'))].find(isEditable);
                        if (inlineField) {
                            inlineField.setAttribute('data-out-party-field', args.marker);
                            return true;
                        }

                        for (let index = labelIndex + 1; index < candidates.length; index++) {
                            const candidate = candidates[index];
                            if (isEditable(candidate)) {
                                candidate.setAttribute('data-out-party-field', args.marker);
                                return true;
                            }
                            const nestedField = Array.from(candidate.querySelectorAll('*')).find(isEditable);
                            if (nestedField) {
                                nestedField.setAttribute('data-out-party-field', args.marker);
                                return true;
                            }
                        }

                        return false;
                    }
                    """, java.util.Map.of(
                    "marker", marker,
                    "title", title,
                    "label", label));
            if (Boolean.TRUE.equals(resolved)) {
                return firstVisible(page.locator("[data-out-party-field='" + marker + "']"));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean outPartyCardScopeContainsMultipleTitles(Locator scope, String expectedTitle) {
        if (scope == null) {
            return false;
        }

        try {
            Object containsMultiple = scope.evaluate("""
                    (card, args) => {
                        const normalize = value => (value || '')
                            .replace(/\\*/g, ' ')
                            .replace(/\\s+/g, ' ')
                            .trim()
                            .toUpperCase();
                        const knownPartyTitles = new Set([
                            'IMPORTER',
                            'INWARD CARRIER',
                            'FREIGHT FORWARDER',
                            'OUTWARD CARRIER',
                            'DECLARING AGENT',
                            'EXPORTER',
                            'CONSIGNEE',
                            'END USER',
                            'MANUFACTURER'
                        ]);
                        const expected = normalize(args.expectedTitle);
                        const isVisible = element => !!element
                            && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const titles = new Set(
                            [card, ...Array.from(card.querySelectorAll('*'))]
                                .filter(isVisible)
                                .map(element => normalize(Array.from(element.childNodes || [])
                                    .filter(node => node.nodeType === Node.TEXT_NODE)
                                    .map(node => node.textContent || '')
                                    .join(' ')))
                                .filter(text => knownPartyTitles.has(text))
                        );
                        titles.delete(expected);
                        return titles.size > 0;
                    }
                    """, java.util.Map.of("expectedTitle", firstNonBlank(expectedTitle, "")));
            return Boolean.TRUE.equals(containsMultiple);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void logOutPartyCardScopeDiagnostics(String title, Locator card) {
        if (card == null) {
            return;
        }
        try {
            Object scopeSummary = card.evaluate("""
                    element => {
                        const normalize = value => (value || '')
                            .replace(/\\*/g, ' ')
                            .replace(/\\s+/g, ' ')
                            .trim();
                        const knownPartyTitles = new Set([
                            'IMPORTER',
                            'INWARD CARRIER',
                            'FREIGHT FORWARDER',
                            'OUTWARD CARRIER',
                            'DECLARING AGENT',
                            'EXPORTER',
                            'CONSIGNEE',
                            'END USER',
                            'MANUFACTURER'
                        ]);
                        const isVisible = node => !!node
                            && !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length);
                        const titles = [...new Set(
                            [element, ...Array.from(element.querySelectorAll('*'))]
                                .filter(isVisible)
                                .map(node => normalize(Array.from(node.childNodes || [])
                                    .filter(child => child.nodeType === Node.TEXT_NODE)
                                    .map(child => child.textContent || '')
                                    .join(' ')))
                                .filter(text => knownPartyTitles.has(text.toUpperCase()))
                        )];
                        const fields = Array.from(element.querySelectorAll("input, textarea, select, [role='combobox'], [role='textbox']"))
                            .filter(isVisible)
                            .slice(0, 4)
                            .map(field => ({
                                id: field.id || '',
                                value: field.value || field.getAttribute('value') || '',
                                type: field.getAttribute('type') || '',
                                formcontrolname: field.getAttribute('formcontrolname') || ''
                            }));
                        return {
                            titles,
                            fieldCount: Array.from(element.querySelectorAll("input, textarea, select, [role='combobox'], [role='textbox']")).filter(isVisible).length,
                            fields
                        };
                    }
                    """);
            logOutPartyDebug(
                    normalize(title).toUpperCase(),
                    "Card scope -> " + String.valueOf(scopeSummary));
        } catch (Exception ignored) {
        }
    }

    private void logOutPartyRowJsonState(String rowLabel, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        logOutPartyDebug(
                normalize(rowLabel).toUpperCase(),
                "JSON name='" + firstNonBlank(partyName(partyNode), "N/A")
                        + "', uen='" + firstNonBlank(text(identityNode.path("partyIdentification"), "id"), "N/A") + "'");
    }

    private void logOutPartyRowFinalState(String rowLabel) {
        Locator nameField = resolveOutPartyRowFieldOrNull(rowLabel, 0);
        Locator idField = resolveOutPartyRowFieldOrNull(rowLabel, 1);
        logOutPartyDebug(
                normalize(rowLabel).toUpperCase(),
                "Final name='" + safeRenderedOutPartyValue(nameField)
                        + "', finalUen='" + safeRenderedOutPartyValue(idField) + "'");
    }

    private void logOutPartyDebug(String scope, String message) {
        logFieldMappingInfo("[OUT][" + firstNonBlank(scope, "PARTY") + "] " + message);
    }

    private String safeRenderedOutPartyValue(Locator field) {
        if (field == null) {
            return "<empty>";
        }
        return firstNonBlank(normalize(readRenderedFieldValue(field)), "<empty>");
    }

    private void fillInvoiceInfo(JsonNode data) {
        JsonNode invoice = firstArrayItem(data.path("invoice"));
        JsonNode item = firstArrayItem(data.path("item"));
        JsonNode unitPriceValue = item.path("transactionValue").path("unitPriceValue");
        JsonNode supplierManufacturerParty = invoice.path("supplierManufacturerParty");

        fillField("Invoice Number", firstNonBlank(text(invoice, "invoiceNumber"), "1"));
        fillDateField("Invoice Date", formatUiDate(text(invoice, "invoiceDate")));
        fillInvoiceTermType(text(invoice, "unitPriceTermType"));

        String supplierManufacturerName = firstNonBlank(
                text(supplierManufacturerParty, "name"),
                text(supplierManufacturerParty.path("partyName"), "name"),
                text(partyIdentityNode(supplierManufacturerParty).path("partyName"), "name"));
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
        fillFieldInChargeRowIfPresent("A. Total Invoice", "Exchange Rate",
                normalizeNumericForEntry(text(invoice.path("totalInvoiceValue"), "exchangeRate")));
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
            fillLookupFieldInRowByIndexIfPresent("K. GST", 0, gst, gst, gst + "%");
        }
    }

    @Override
    protected void fillItemInfo(JsonNode data) {
        JsonNode invoice = firstArrayItem(data.path("invoice"));
        JsonNode item = firstArrayItem(data.path("item"));
        JsonNode itemQuantity = item.path("itemQuantity");
        JsonNode packingDescription = item.path("packingDescription");
        JsonNode transactionValue = item.path("transactionValue");
        JsonNode unitPriceValue = transactionValue.path("unitPriceValue");
        JsonNode lotIdentification = item.path("lotIdentification");
        JsonNode shippingMarksInformation = firstArrayItem(item.path("shippingMarksInformation"));
        JsonNode cascProduct = item.path("cascProduct");
        JsonNode itemCertificate = item.path("itemCertificate");
        String itemInvoiceNumber = firstNonBlank(text(item, "itemInvoiceNumber"), text(invoice, "invoiceNumber"));

        fillSelectLikeLookupFieldIfPresentByLabels(itemInvoiceNumber, "Item Invoice Number", "Invoice Number");
        fillFieldIfPresent("Inward HAWB", text(item, "inHawbHucrHblNumber"));
        fillFieldIfPresent("Outward HAWB", text(item, "outHawbHucrHblNumber"));
        fillFieldIfPresent("Exchange Rate", normalizeNumericForEntry(text(unitPriceValue, "exchangeRate")));
        fillItemHsCode(text(item, "itemHarmonizedSystemCode"));
        fillFirstPresentField(text(item, "goodsDescription"), "Goods Description", "Description");
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
        boolean hasPackingDescription = !isMissingOrEmpty(packingDescription);
        if (hasPackingDescription || data.path("formMetaData").path("itemPackingIsActive").path(0).asBoolean(false)) {
            setCheckboxByLabel("Item Packing", true);
        }
        if (hasPackingDescription) {
            fillPackingDescription(packingDescription);
        }

        fillItemQuantityDetails(itemQuantity);

        fillItemValues(transactionValue);
        fillLotIdentification(item, lotIdentification);
        fillShippingMarks(shippingMarksInformation);
        fillCascDetails(cascProduct);
        fillItemCertificate(itemCertificate, data.path("formMetaData"));
        fillLookupFieldIfPresentByLabels(text(item, "hsImportCa"), "HS Import CA", "HS CA");
        fillLookupFieldIfPresentByLabels(text(item, "hsExportCa"), "HS Export CA");
        fillLookupFieldIfPresentByLabels(text(item, "hsTranshipmentCa"), "HS Transhipment CA");
    }

    private void fillItemCertificate(JsonNode itemCertificate, JsonNode formMetaData) {
        fillCertificateOfOriginSectionFromItemCertificate(itemCertificate, formMetaData, "Item tab");
    }

    private void fillPackingDescription(JsonNode packingDescription) {
        ensurePackingDescriptionExpanded();
        Locator packingDescriptionSection = resolvePackingDescriptionSectionOrNull();
        if (packingDescriptionSection == null) {
            return;
        }

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
                if (resolveSectionOrNull(title) != null) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private Locator resolvePackingDescriptionSectionOrNull() {
        String[] titles = new String[] { "Packing Description", "Packing Details" };
        for (String title : titles) {
            Locator section = resolvePackingDescriptionSectionOrNull(title);
            if (section != null) {
                return section;
            }
        }
        return null;
    }

    private Locator resolvePackingDescriptionSectionOrNull(String title) {
        String escapedTitle = toXpathLiteral(title);
        return firstVisible(page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[(.//*[contains(normalize-space(translate(., '*', '')), 'Outer Pack Qty')]"
                        + " or .//*[contains(normalize-space(translate(., '*', '')), 'In Pack Qty')]"
                        + " or .//*[contains(normalize-space(translate(., '*', '')), 'Inner Pack Qty')]"
                        + " or .//*[contains(normalize-space(translate(., '*', '')), 'Inmost Pack Qty')])"
                        + " and (.//input or .//select or .//*[@role='combobox'] or .//*[@role='textbox'])][1]"));
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

    private Locator resolvePackingQuantityFieldOrNull(Locator section, String rowLabel, int occurrence) {
        Locator visibleSection = firstVisible(section);
        if (visibleSection == null) {
            return null;
        }

        String escapedRowLabel = toXpathLiteral(rowLabel);
        Locator field = visibleSection.locator(
                "xpath=((.//*[normalize-space(translate(., '*', ''))=" + escapedRowLabel + "]"
                        + "[not(.//*[normalize-space(translate(., '*', ''))=" + escapedRowLabel + "])])[1]"
                        + "/following::*[self::input[not(@type='checkbox')] or self::textarea or self::select or @role='combobox' or @role='textbox']["
                        + (occurrence + 1) + "])[1]");
        return firstVisible(field);
    }

    private void fillAdditionalRecipientsFromJson(JsonNode header, JsonNode formMetaData) {
        additionalRecipientsRequested = false;
        setCheckboxByLabel("Additional Recipients", false);
        syncCheckboxValue(false, "Additional Recipients");
        page.waitForTimeout(200);
        ensureAdditionalRecipientsInactive();

        java.util.List<String> additionalRecipientIds = collectAdditionalRecipientIds(header);
        if (additionalRecipientIds.isEmpty()) {
            return;
        }

        additionalRecipientsRequested = true;
        setCheckboxByLabel("Additional Recipients", true);
        ensureAdditionalRecipientsActive();
        page.waitForTimeout(300);

        Locator additionalRecipientsSection = waitForAdditionalRecipientsSectionOrNull(3000);
        if (additionalRecipientsSection == null) {
            throw new IllegalStateException("Additional Recipients section did not open after enabling the checkbox.");
        }

        for (int index = 0; index < additionalRecipientIds.size(); index++) {
            String additionalRecipientId = additionalRecipientIds.get(index);
            if (index > 0 && !clickAdditionalRecipientsAddButton(additionalRecipientsSection)) {
                throw new IllegalStateException("Additional Recipients ADD button was not clickable.");
            }
            waitForAdditionalRecipientsRow(additionalRecipientsSection, index + 1, 3000);

            Locator input = resolveAdditionalRecipientsInputBoxOrNull(additionalRecipientsSection, index + 1);
            if (input == null) {
                throw new IllegalStateException("Additional Recipient input row was not visible for row " + (index + 1));
            }

            focusAndType(input, additionalRecipientId, false);
            if (!waitForAnyRenderedFieldValue(input, 1500, additionalRecipientId)) {
                throw new IllegalStateException("Additional Recipient value was not rendered. Expected: "
                        + additionalRecipientId + ", Actual: " + readRenderedFieldValue(input));
            }
        }
    }

    private java.util.List<String> collectAdditionalRecipientIds(JsonNode header) {
        java.util.List<String> values = new java.util.ArrayList<>();
        JsonNode additionalRecipientIds = header.path("additionalRecipientId");
        if (additionalRecipientIds == null || !additionalRecipientIds.isArray()) {
            return values;
        }

        for (JsonNode additionalRecipientIdNode : additionalRecipientIds) {
            String additionalRecipientId = normalize(additionalRecipientIdNode.asText());
            if (additionalRecipientId != null && !additionalRecipientId.isBlank()) {
                values.add(additionalRecipientId);
            }
        }
        return values;
    }

    private Locator resolveAdditionalRecipientsSectionOrNull() {
        return firstVisible(page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))='Additional Recipients'])[last()]"
                        + "/ancestor::*[.//*[self::button or @role='button' or self::a]"
                        + "[contains(normalize-space(translate(., 'abcdefghijklmnopqrstuvwxyz*', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ')), 'ADD')]"
                        + " and (.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox'] or .//button)][1]"));
    }

    private Locator waitForAdditionalRecipientsSectionOrNull(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            Locator section = resolveAdditionalRecipientsSectionOrNull();
            if (section != null) {
                return section;
            }
            page.waitForTimeout(100);
        }
        return null;
    }

    private boolean clickAdditionalRecipientsAddButton(Locator additionalRecipientsSection) {
        Locator visibleScope = firstVisible(additionalRecipientsSection);
        if (visibleScope == null) {
            return false;
        }

        Locator addButton = firstVisible(visibleScope.locator(
                "xpath=(.//*[self::button or @role='button' or self::a]"
                        + "[contains(normalize-space(translate(., '*', '')), 'ADD')])[last()]"));
        if (addButton == null) {
            return false;
        }

        dismissTransientOverlays();
        addButton.scrollIntoViewIfNeeded();
        try {
            addButton.click(new Locator.ClickOptions().setForce(true));
            return true;
        } catch (PlaywrightException ignored) {
        }

        try {
            return Boolean.TRUE.equals(addButton.evaluate("""
                    element => {
                        element.click();
                        return true;
                    }
                    """));
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private Locator resolveAdditionalRecipientsInputBoxOrNull(Locator additionalRecipientsSection, int rowNumber) {
        Locator visibleScope = firstVisible(additionalRecipientsSection);
        if (visibleScope == null) {
            return null;
        }

        String rowNumberText = String.valueOf(rowNumber);
        Locator formControlField = visibleScope.locator(
                "xpath=((.//*[normalize-space(.)='" + rowNumberText + "']"
                        + "[not(ancestor::*[self::thead or @role='columnheader'])])[last()]"
                        + "/following::*[(self::input or self::textarea) and @formcontrolname='additionalRecipientId'][1])[1]");
        Locator visibleFormControlField = firstVisible(formControlField);
        if (visibleFormControlField != null) {
            return visibleFormControlField;
        }

        Locator rowField = visibleScope.locator(
                "xpath=((.//*[normalize-space(.)='" + rowNumberText + "']"
                        + "[not(ancestor::*[self::thead or @role='columnheader'])])[last()]"
                        + "/following::*[self::input[not(@type='checkbox')] or self::textarea][1])[1]");
        Locator visibleRowField = firstVisible(rowField);
        if (visibleRowField != null) {
            return visibleRowField;
        }

        Locator newestField = lastVisible(visibleScope.locator(
                "input:not([type='checkbox']):not([readonly]):not([disabled]), textarea:not([readonly]):not([disabled])"));
        if (newestField != null) {
            return newestField;
        }
        return null;
    }

    private void ensureAdditionalRecipientsActive() {
        try {
            Boolean activated = (Boolean) page.evaluate("""
                    () => {
                        const dispatch = element => {
                            element.dispatchEvent(new Event('input', { bubbles: true }));
                            element.dispatchEvent(new Event('change', { bubbles: true }));
                            element.dispatchEvent(new Event('blur', { bubbles: true }));
                        };
                        const syncComponent = host => {
                            if (!host || typeof window.ng === 'undefined' || typeof window.ng.getComponent !== 'function') {
                                return;
                            }
                            const component = window.ng.getComponent(host);
                            if (!component) {
                                return;
                            }
                            if ('checked' in component) {
                                component.checked = true;
                            }
                            if ('value' in component) {
                                component.value = true;
                            }
                            if ('_value' in component) {
                                component._value = true;
                            }
                            if (component.formControl && typeof component.formControl.setValue === 'function') {
                                component.formControl.setValue(true);
                            }
                            if (typeof component.writeValue === 'function') {
                                component.writeValue(true);
                            }
                            if (typeof component.onChange === 'function') {
                                component.onChange(true);
                            }
                            if (typeof component.onTouched === 'function') {
                                component.onTouched();
                            }
                        };

                        const toggles = Array.from(document.querySelectorAll("input[type='checkbox'][formcontrolname='additionalRecipientIdIsActive']"));
                        if (toggles.length === 0) {
                            return false;
                        }

                        toggles.forEach(checkbox => {
                            checkbox.checked = true;
                            checkbox.defaultChecked = true;
                            checkbox.setAttribute('checked', 'checked');
                            checkbox.setAttribute('aria-checked', 'true');
                            dispatch(checkbox);
                            let current = checkbox;
                            for (let depth = 0; current && depth < 5; depth += 1) {
                                syncComponent(current);
                                current = current.parentElement;
                            }
                        });

                        return toggles.every(toggle => toggle.checked === true);
                    }
                    """);
            if (Boolean.TRUE.equals(activated)) {
                return;
            }
        } catch (Exception ignored) {
        }

        syncCheckboxValueByFormControl(true, "additionalRecipientIdIsActive", "Additional Recipients");
    }

    private void waitForAdditionalRecipientsRow(Locator additionalRecipientsSection, int rowNumber, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= deadline) {
            if (resolveAdditionalRecipientsInputBoxOrNull(additionalRecipientsSection, rowNumber) != null) {
                return;
            }
            page.waitForTimeout(100);
        }
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

                        const syncComponent = host => {
                            if (!host || typeof window.ng === 'undefined' || typeof window.ng.getComponent !== 'function') {
                                return;
                            }
                            const component = window.ng.getComponent(host);
                            if (!component) {
                                return;
                            }
                            if ('checked' in component) {
                                component.checked = false;
                            }
                            if ('value' in component) {
                                component.value = false;
                            }
                            if ('_value' in component) {
                                component._value = false;
                            }
                            if (component.formControl && typeof component.formControl.setValue === 'function') {
                                component.formControl.setValue(false);
                            }
                            if (typeof component.writeValue === 'function') {
                                component.writeValue(false);
                            }
                            if (typeof component.onChange === 'function') {
                                component.onChange(false);
                            }
                            if (typeof component.onTouched === 'function') {
                                component.onTouched();
                            }
                        };

                        Array.from(document.querySelectorAll("input[type='checkbox'][formcontrolname='additionalRecipientIdIsActive']"))
                            .forEach(checkbox => {
                                checkbox.checked = false;
                                checkbox.defaultChecked = false;
                                checkbox.removeAttribute('checked');
                                checkbox.setAttribute('aria-checked', 'false');
                                dispatch(checkbox);
                                let current = checkbox;
                                for (let depth = 0; current && depth < 5; depth += 1) {
                                    syncComponent(current);
                                    current = current.parentElement;
                                }
                            });

                        const additionalRecipientFields = document.querySelectorAll(
                            "[formcontrolname='additionalRecipientId'], input[formcontrolname='additionalRecipientId'], textarea[formcontrolname='additionalRecipientId']"
                        );
                        additionalRecipientFields.forEach(field => {
                            if ('value' in field) {
                                field.value = '';
                            }
                            dispatch(field);
                        });

                        const toggles = Array.from(document.querySelectorAll("input[type='checkbox'][formcontrolname='additionalRecipientIdIsActive']"));
                        return toggles.every(toggle => toggle.checked === false);
                    }
                    """);
            page.waitForTimeout(200);
        } catch (Exception ignored) {
        }
    }

    private void fillCpcInfo(JsonNode data) {
        JsonNode header = data.path("header");
        JsonNode formMetaData = data.path("formMetaData");
        setCheckboxIfTrue(formMetaData.path("aeoIsActive").asBoolean(false), "AEO");
        setCheckboxIfTrue(formMetaData.path("cwcIsActive").asBoolean(false), "CWC");
        setCheckboxIfTrue(formMetaData.path("seastoreIsActive").asBoolean(false), "SEASTORE");
        setCheckboxIfTrue(formMetaData.path("stsIsActive").asBoolean(false), "STS");
        setCheckboxIfTrue(formMetaData.path("stsAndCwcIsActive").asBoolean(false), "STS & CWC");
        setCheckboxIfTrue(formMetaData.path("internationalPermitExchangeIsActive").asBoolean(false), "INTERNATIONAL PERMIT EXCHANGE");

        JsonNode customsProcedureCodeInformation = header.path("customsProcedureCodeInformation");
        if (customsProcedureCodeInformation.isArray()) {
            for (JsonNode customsProcedureCodeEntry : customsProcedureCodeInformation) {
                fillCustomsProcedureCodeEntry(customsProcedureCodeEntry);
            }
        }

        // Apply footer checkbox state after CPC row entry because this area can rerender
        // while additional CPC sections are being expanded and populated.
        page.waitForTimeout(250);
        setCpcFooterCheckboxState("CNB", headerBoolean(header, "cnb"));
        setCpcFooterCheckboxState("Deferred Printing of CO",
                headerBoolean(header, "deferredPrintingofCO", "deferredPrintingOfCO"));
    }

    private void fillCustomsProcedureCodeEntry(JsonNode customsProcedureCodeEntry) {
        if (isMissingOrEmpty(customsProcedureCodeEntry)) {
            return;
        }

        String customsProcedureCode = text(customsProcedureCodeEntry, "customsProcedureCode");
        if (customsProcedureCode == null || customsProcedureCode.isBlank()) {
            return;
        }

        ensureCpcSectionExpanded(customsProcedureCode);
        page.waitForTimeout(300);

        JsonNode cpcProcessingCodes = customsProcedureCodeEntry.path("cpcProcessingCode");
        if (!cpcProcessingCodes.isArray() || cpcProcessingCodes.isEmpty()) {
            return;
        }

        for (int index = 0; index < cpcProcessingCodes.size(); index++) {
            JsonNode processingCodeRow = cpcProcessingCodes.get(index);
            if (isMissingOrEmpty(processingCodeRow)) {
                continue;
            }

            if (index > 0) {
                clickCpcAddButton(customsProcedureCode);
                page.waitForTimeout(300);
            }

            fillCpcProcessingRow(customsProcedureCode, index, processingCodeRow);
        }
    }

    private void fillCpcProcessingRow(String customsProcedureCode, int rowIndex, JsonNode processingCodeRow) {
        fillCpcProcessingField(customsProcedureCode, rowIndex, "Code 1", text(processingCodeRow, "processingCodeOne"));
        fillCpcProcessingField(customsProcedureCode, rowIndex, "Code 2", text(processingCodeRow, "processingCodeTwo"));
        fillCpcProcessingField(customsProcedureCode, rowIndex, "Code 3", text(processingCodeRow, "processingCodeThree"));
    }

    private void fillCpcProcessingField(String customsProcedureCode, int rowIndex, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        Locator field = waitForCpcProcessingFieldOrNull(customsProcedureCode, label, rowIndex, 3000);
        if (field == null) {
            throw new IllegalStateException("CPC " + label + " field was not visible for section "
                    + customsProcedureCode + " row " + (rowIndex + 1) + ".");
        }
        fillCpcTextField(field, value, label, rowIndex);
    }

    private void fillCpcTextField(Locator field, String value, String label, int rowIndex) {
        dismissTransientOverlays();
        fillVerifiedTextField(field, value, "CPC " + label + " row " + (rowIndex + 1));
    }

    private void ensureCpcSectionExpanded(String customsProcedureCode) {
        Locator checkbox = resolveCpcCheckboxOrNull(customsProcedureCode);
        if (checkbox == null) {
            setCheckboxByLabel(customsProcedureCode, true);
            return;
        }

        checkbox.scrollIntoViewIfNeeded();
        if (!isCpcCheckboxSelected(checkbox)) {
            try {
                checkbox.click(new Locator.ClickOptions().setForce(true));
            } catch (PlaywrightException ignored) {
            }
        }
        if (!isCpcCheckboxSelected(checkbox)) {
            clickCpcSectionContainer(customsProcedureCode);
        }
        if (!isCpcCheckboxSelected(checkbox)) {
            forceCheckboxValue(true, customsProcedureCode);
        }
        if (!isCpcCheckboxSelected(checkbox)) {
            throw new IllegalStateException("CPC checkbox did not open section: " + customsProcedureCode);
        }
    }

    private Locator resolveCpcCheckboxOrNull(String customsProcedureCode) {
        String escapedTitle = toXpathLiteral(customsProcedureCode);
        Locator checkbox = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input[@type='checkbox'] or .//*[@role='checkbox']][1]"
                        + "//*[self::input[@type='checkbox'] or @role='checkbox']");
        return firstVisible(checkbox);
    }

    private void clickCpcSectionContainer(String customsProcedureCode) {
        String escapedTitle = toXpathLiteral(customsProcedureCode);
        Locator container = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input[@type='checkbox'] or .//*[@role='checkbox']][1]");
        Locator visibleContainer = firstVisible(container);
        if (visibleContainer == null) {
            return;
        }

        try {
            visibleContainer.scrollIntoViewIfNeeded();
            visibleContainer.click(new Locator.ClickOptions().setForce(true));
        } catch (PlaywrightException ignored) {
        }
    }

    private boolean isCpcCheckboxSelected(Locator checkbox) {
        try {
            return "true".equalsIgnoreCase(normalize(checkbox.getAttribute("aria-checked")))
                    || Boolean.TRUE.equals(checkbox.evaluate("element => element.checked === true"));
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private Locator waitForCpcProcessingFieldOrNull(String customsProcedureCode, String label, int rowIndex, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 1000);
        while (System.currentTimeMillis() <= deadline) {
            Locator field = resolveCpcProcessingFieldOrNull(customsProcedureCode, label, rowIndex);
            if (field != null) {
                return field;
            }
            page.waitForTimeout(100);
        }
        return null;
    }

    private Locator resolveCpcProcessingFieldOrNull(String customsProcedureCode, String label, int rowIndex) {
        if (customsProcedureCode == null || customsProcedureCode.isBlank() || label == null || label.isBlank()) {
            return null;
        }

        String escapedTitle = toXpathLiteral(customsProcedureCode);
        String escapedLabel = toXpathLiteral(label);
        Locator placeholderFields = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/following::*[self::input or self::textarea or self::select]"
                        + "[normalize-space(@placeholder)=" + escapedLabel
                        + " or contains(normalize-space(@aria-label), " + escapedLabel + ")])");
        Locator visiblePlaceholderField = nthVisibleLocatorByPositionOrNull(placeholderFields, rowIndex);
        if (visiblePlaceholderField != null) {
            return visiblePlaceholderField;
        }

        Integer occurrence = switch (label.trim().toUpperCase()) {
            case "CODE 1" -> rowIndex * 3;
            case "CODE 2" -> rowIndex * 3 + 1;
            case "CODE 3" -> rowIndex * 3 + 2;
            default -> null;
        };
        if (occurrence == null) {
            return null;
        }

        Locator allFieldsAfterTitle = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/following::*[self::input[not(@type='checkbox')] or self::textarea or self::select or @role='combobox' or @role='textbox'])");
        return nthVisibleLocatorByPositionOrNull(allFieldsAfterTitle, occurrence);
    }

    private void clickCpcAddButton(String customsProcedureCode) {
        String escapedTitle = toXpathLiteral(customsProcedureCode);
        Locator addButtons = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/following::*[self::button or @role='button' or self::a]"
                        + "[contains(normalize-space(translate(., '*', '')), 'ADD')])");
        Locator addButton = nthVisibleLocatorByPositionOrNull(addButtons, 0);
        if (addButton == null) {
            throw new IllegalStateException("CPC ADD button was not visible.");
        }

        dismissTransientOverlays();
        addButton.scrollIntoViewIfNeeded();
        try {
            addButton.click(new Locator.ClickOptions().setForce(true));
            return;
        } catch (PlaywrightException ignored) {
        }

        try {
            addButton.evaluate("""
                    element => {
                        element.click();
                        return true;
                    }
                    """);
        } catch (PlaywrightException exception) {
            throw new IllegalStateException("CPC ADD button click failed.", exception);
        }
    }

    private Locator nthVisibleLocatorByPositionOrNull(Locator locator, int occurrence) {
        List<PositionedCpcField> positionedLocators = collectVisiblePositionedCpcFields(locator);
        if (occurrence < 0 || occurrence >= positionedLocators.size()) {
            return null;
        }

        List<PositionedCpcField> sortedLocators = positionedLocators.stream()
                .sorted(Comparator.comparingDouble(PositionedCpcField::y).thenComparingDouble(PositionedCpcField::x))
                .toList();
        return locator.nth(sortedLocators.get(occurrence).index());
    }

    private List<PositionedCpcField> collectVisiblePositionedCpcFields(Locator locator) {
        List<PositionedCpcField> positionedFields = new ArrayList<>();
        int count = locator.count();
        for (int index = 0; index < count; index++) {
            Locator candidate = locator.nth(index);
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
            positionedFields.add(new PositionedCpcField(index, box.x, box.y));
        }
        return positionedFields;
    }

    private record PositionedCpcField(int index, double x, double y) {
    }

    private record ValidationPositionedField(int index, double x, double y) {
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
        JsonNode formMetaData = data.path("formMetaData");
        Locator declarationSummaryCard = resolveSummaryCard(
                "Declaration Summary",
                "Total Package",
                "Total Gross Weight",
                "Cross Reference ID");
        fillFieldAfterScopeLabelIfPresent(declarationSummaryCard, "Cross Reference ID", 0,
                firstNonBlank(text(data.path("header"), "crossReferenceId"), text(summary, "crossReferenceId")));

        Locator remarksCard = resolveSummaryCard(
                "Remarks",
                "Internal Remarks",
                "Customer Remarks");
        fillFieldAfterScopeLabelIfPresent(remarksCard, "Remarks", 0,
                text(data.path("header").path("remarks"), "freeText"));
        fillFieldAfterScopeLabelIfPresent(remarksCard, "Internal Remarks", 0,
                firstNonBlank(
                        text(formMetaData, "internalRemarks"),
                        text(data.path("header").path("remarks"), "internalText")));
        fillFieldAfterScopeLabelIfPresent(remarksCard, "Customer Remarks", 0,
                firstNonBlank(
                        text(formMetaData, "customerRemarks"),
                        text(data.path("header").path("remarks"), "customerText")));

        if (data.path("header").path("declarationIndicator").asBoolean(false)
                || formMetaData.path("declarationIndicator").asBoolean(false)) {
            setCheckboxByLabel("Declaration Indicator", true);
            setCheckboxByLabel("I/We declare that all the particulars in this Application are true and correct.", true);
            syncCheckboxValue(true,
                    "I/We declare that all the particulars in this Application are true and correct.",
                    "Declaration Indicator");
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
        waitForActionButtonEnabled("SAVE DRAFT", configuredElementWaitTimeoutMs());
        clickActionButtonExactWithRetry("SAVE DRAFT", configuredRetryCount());
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
                automationDiagnostics.recordRetryUsage(buttonText, attempt + 1);
                return;
            }
            page.waitForTimeout(750);
        }
        automationDiagnostics.recordRetryUsage(buttonText, attempts + 1);
        throw new IllegalStateException("Unable to click action button: " + buttonText);
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

    private void setCheckboxIfTrue(boolean enabled, String label) {
        if (enabled) {
            setCheckboxByLabel(label, true);
        }
    }

    private boolean headerBoolean(JsonNode header, String... fieldNames) {
        if (header == null || header.isMissingNode() || header.isNull()) {
            return false;
        }

        for (String fieldName : fieldNames) {
            JsonNode valueNode = header.path(fieldName);
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                continue;
            }
            if (valueNode.isBoolean()) {
                return valueNode.asBoolean(false);
            }

            String value = valueNode.asText("").trim();
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }

        return false;
    }

    private void setCpcFooterCheckboxState(String label, boolean checked) {
        Locator checkbox = resolveCpcFooterCheckboxByExactLabelOrNull(label);
        if (checkbox != null) {
            setCheckboxState(checkbox, checked, label);
            if (isCheckboxWithExactLabelInExpectedState(label, checked)) {
                return;
            }
        }

        setCheckboxByLabelIfDifferent(label, checked);
        if (isCheckboxWithExactLabelInExpectedState(label, checked)) {
            return;
        }

        forceNearbyCheckboxStateByExactLabel(label, checked);
        if (!isCheckboxWithExactLabelInExpectedState(label, checked)) {
            throw new IllegalStateException("CPC checkbox did not reach expected state for "
                    + label + ": " + checked);
        }
    }

    private Locator resolveCpcFooterCheckboxByExactLabelOrNull(String label) {
        try {
            page.waitForTimeout(200);
        } catch (Exception ignored) {
        }
        String escapedLabel = toXpathLiteral(label);

        Locator nestedCheckbox = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedLabel + "])[last()]"
                        + "//*[self::input[@type='checkbox'] or @role='checkbox'])[1]");
        Locator visibleNestedCheckbox = firstVisible(nestedCheckbox);
        if (visibleNestedCheckbox != null) {
            return visibleNestedCheckbox;
        }

        Locator precedingCheckbox = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedLabel + "])[last()]"
                        + "/preceding::*[self::input[@type='checkbox'] or @role='checkbox'][1])[1]");
        Locator visiblePrecedingCheckbox = firstVisible(precedingCheckbox);
        if (visiblePrecedingCheckbox != null) {
            return visiblePrecedingCheckbox;
        }

        Locator checkboxInNearestContainer = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedLabel + "])[last()]"
                        + "/ancestor::*[.//input[@type='checkbox'] or .//*[@role='checkbox']][1]"
                        + "//*[self::input[@type='checkbox'] or @role='checkbox'])[1]");
        Locator visibleCheckboxInNearestContainer = firstVisible(checkboxInNearestContainer);
        if (visibleCheckboxInNearestContainer != null) {
            return visibleCheckboxInNearestContainer;
        }

        Locator followingCheckbox = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedLabel + "])[last()]"
                        + "/following::*[self::input[@type='checkbox'] or @role='checkbox'][1])[1]");
        return firstVisible(followingCheckbox);
    }

    private void setCheckboxState(Locator checkbox, boolean checked, String label) {
        try {
            checkbox.scrollIntoViewIfNeeded();
        } catch (Exception ignored) {
        }

        try {
            if (isCheckboxSelectedInOutPage(checkbox) != checked) {
                checkbox.click(new Locator.ClickOptions().setForce(true));
            }
        } catch (Exception ignored) {
        }

        if (isCheckboxSelectedInOutPage(checkbox) == checked) {
            return;
        }

        try {
            Locator container = checkbox.locator("xpath=ancestor::*[self::label or self::div or self::span][1]");
            Locator visibleContainer = firstVisible(container);
            if (visibleContainer != null) {
                visibleContainer.click(new Locator.ClickOptions().setForce(true));
            }
        } catch (Exception ignored) {
        }

        if (isCheckboxSelectedInOutPage(checkbox) == checked) {
            return;
        }

        forceNearbyCheckboxStateByExactLabel(label, checked);
    }

    private boolean isCheckboxSelectedInOutPage(Locator checkbox) {
        try {
            return "true".equalsIgnoreCase(normalize(checkbox.getAttribute("aria-checked")))
                    || Boolean.TRUE.equals(checkbox.evaluate("element => element.checked === true"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isCheckboxWithExactLabelInExpectedState(String label, boolean expectedState) {
        try {
            Object matched = page.evaluate("""
                    args => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const isVisible = element => {
                            if (!element) {
                                return false;
                            }
                            const style = window.getComputedStyle(element);
                            return style.display !== 'none'
                                && style.visibility !== 'hidden'
                                && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        };
                        const checkboxState = checkbox =>
                            checkbox.getAttribute('aria-checked') === 'true' || checkbox.checked === true;
                        const resolveCheckbox = labelText => {
                            const searchRoots = Array.from(document.querySelectorAll('label, span, div, p'))
                                .filter(isVisible)
                                .filter(element => normalize(element.innerText || element.textContent) === normalize(labelText));
                            const visibleCheckboxes = Array.from(document.querySelectorAll('input[type="checkbox"], [role="checkbox"]'))
                                .filter(isVisible);
                            for (const root of searchRoots) {
                                const containers = [];
                                let current = root;
                                for (let depth = 0; current && depth < 6; depth += 1) {
                                    containers.push(current);
                                    current = current.parentElement;
                                }
                                containers.push(root.previousElementSibling, root.nextElementSibling);
                                for (const container of containers.filter(Boolean)) {
                                    const checkbox = container.matches?.('input[type="checkbox"], [role="checkbox"]')
                                        ? container
                                        : container.querySelector?.('input[type="checkbox"], [role="checkbox"]');
                                    if (checkbox && isVisible(checkbox)) {
                                        return checkbox;
                                    }
                                }

                                const labelRect = root.getBoundingClientRect();
                                let bestCheckbox = null;
                                let bestScore = Number.POSITIVE_INFINITY;
                                for (const checkbox of visibleCheckboxes) {
                                    const rect = checkbox.getBoundingClientRect();
                                    const labelCenterY = labelRect.top + (labelRect.height / 2);
                                    const checkboxCenterY = rect.top + (rect.height / 2);
                                    const verticalGap = Math.abs(labelCenterY - checkboxCenterY);
                                    const horizontalGap = rect.right <= labelRect.left + 8
                                        ? Math.abs(labelRect.left - rect.right)
                                        : Math.abs(rect.left - labelRect.right) + 200;
                                    const sameRowPenalty = verticalGap <= Math.max(rect.height, labelRect.height, 24) ? 0 : 1000;
                                    const score = sameRowPenalty + (verticalGap * 10) + horizontalGap;
                                    if (score < bestScore) {
                                        bestScore = score;
                                        bestCheckbox = checkbox;
                                    }
                                }
                                if (bestCheckbox) {
                                    return bestCheckbox;
                                }
                            }
                            return null;
                        };

                        const checkbox = resolveCheckbox(args.label);
                        if (!checkbox) {
                            return false;
                        }
                        return checkboxState(checkbox) === !!args.expectedState;
                    }
                    """, java.util.Map.of(
                    "label", label,
                    "expectedState", expectedState));
            return Boolean.TRUE.equals(matched);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void forceNearbyCheckboxStateByExactLabel(String label, boolean checked) {
        try {
            page.evaluate("""
                    args => {
                        const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const isVisible = element => {
                            if (!element) {
                                return false;
                            }
                            const style = window.getComputedStyle(element);
                            return style.display !== 'none'
                                && style.visibility !== 'hidden'
                                && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        };
                        const dispatch = checkbox => {
                            checkbox.dispatchEvent(new Event('input', { bubbles: true }));
                            checkbox.dispatchEvent(new Event('change', { bubbles: true }));
                            checkbox.dispatchEvent(new Event('blur', { bubbles: true }));
                        };
                        const checkboxState = checkbox =>
                            checkbox.getAttribute('aria-checked') === 'true' || checkbox.checked === true;
                        const applyState = checkbox => {
                            const expectedState = !!args.checked;
                            if (checkboxState(checkbox) !== expectedState) {
                                checkbox.click?.();
                            }
                            if ('checked' in checkbox) {
                                checkbox.checked = expectedState;
                            }
                            checkbox.setAttribute?.('aria-checked', String(expectedState));
                            dispatch(checkbox);
                            if (checkboxState(checkbox) === expectedState) {
                                return true;
                            }
                            const container = checkbox.closest?.('label, div, span, p') || checkbox.parentElement;
                            container?.click?.();
                            if ('checked' in checkbox) {
                                checkbox.checked = expectedState;
                            }
                            checkbox.setAttribute?.('aria-checked', String(expectedState));
                            dispatch(checkbox);
                            return checkboxState(checkbox) === expectedState;
                        };
                        const resolveCheckbox = labelText => {
                            const exactLabels = Array.from(document.querySelectorAll('label, span, div, p'))
                                .filter(isVisible)
                                .filter(element => normalize(element.innerText || element.textContent) === normalize(labelText));
                            const visibleCheckboxes = Array.from(document.querySelectorAll('input[type="checkbox"], [role="checkbox"]'))
                                .filter(isVisible);
                            for (const textNode of exactLabels) {
                                const containers = [];
                                let current = textNode;
                                for (let depth = 0; current && depth < 6; depth += 1) {
                                    containers.push(current);
                                    current = current.parentElement;
                                }
                                containers.push(textNode.previousElementSibling, textNode.nextElementSibling);
                                for (const container of containers.filter(Boolean)) {
                                    const checkbox = container.matches?.('input[type="checkbox"], [role="checkbox"]')
                                        ? container
                                        : container.querySelector?.('input[type="checkbox"], [role="checkbox"]');
                                    if (checkbox && isVisible(checkbox)) {
                                        return checkbox;
                                    }
                                }

                                const labelRect = textNode.getBoundingClientRect();
                                let bestCheckbox = null;
                                let bestScore = Number.POSITIVE_INFINITY;
                                for (const checkbox of visibleCheckboxes) {
                                    const rect = checkbox.getBoundingClientRect();
                                    const labelCenterY = labelRect.top + (labelRect.height / 2);
                                    const checkboxCenterY = rect.top + (rect.height / 2);
                                    const verticalGap = Math.abs(labelCenterY - checkboxCenterY);
                                    const horizontalGap = rect.right <= labelRect.left + 8
                                        ? Math.abs(labelRect.left - rect.right)
                                        : Math.abs(rect.left - labelRect.right) + 200;
                                    const sameRowPenalty = verticalGap <= Math.max(rect.height, labelRect.height, 24) ? 0 : 1000;
                                    const score = sameRowPenalty + (verticalGap * 10) + horizontalGap;
                                    if (score < bestScore) {
                                        bestScore = score;
                                        bestCheckbox = checkbox;
                                    }
                                }
                                if (bestCheckbox) {
                                    return bestCheckbox;
                                }
                            }
                            return null;
                        };

                        const checkbox = resolveCheckbox(args.label);
                        if (!checkbox) {
                            return false;
                        }
                        return applyState(checkbox);
                    }
                    """, java.util.Map.of(
                    "label", label,
                    "checked", checked));
        } catch (Exception ignored) {
        }
    }

    private Locator resolvePartyCard(String title) {
        Locator scoredCard = resolvePartyCardByScoredContainerMatch(title);
        if (scoredCard != null) {
            return scoredCard;
        }
        // Fall back to the simple ancestor-match strategy CooDeclarationPage's (working) party cards
        // use, for cases the scored container match can't resolve.
        return resolvePartyCardBySimpleAncestorMatch(title);
    }

    private Locator resolvePartyCardBySimpleAncestorMatch(String title) {
        String escapedTitle = toXpathLiteral(title);
        Locator locator = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox']][1]");
        return firstVisible(locator);
    }

    private Locator resolvePartyCardByScoredContainerMatch(String title) {
        Locator partySection = resolvePartyInfoSectionOrNull();
        if (partySection == null) {
            return null;
        }

        String marker = "out-party-card-" + normalize(title).toLowerCase().replaceAll("[^a-z0-9]+", "-");
        try {
            Object found = partySection.evaluate("""
                    (section, args) => {
                        const normalize = value => (value || '')
                            .replace(/\\*/g, ' ')
                            .replace(/\\s+/g, ' ')
                            .trim()
                            .toUpperCase();
                        const knownPartyTitles = new Set([
                            'IMPORTER',
                            'INWARD CARRIER',
                            'FREIGHT FORWARDER',
                            'OUTWARD CARRIER',
                            'DECLARING AGENT',
                            'EXPORTER',
                            'CONSIGNEE',
                            'END USER',
                            'MANUFACTURER'
                        ]);
                        const isVisible = element => !!element
                            && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                        const ownText = element => normalize(Array.from(element.childNodes || [])
                            .filter(node => node.nodeType === Node.TEXT_NODE)
                            .map(node => node.textContent || '')
                            .join(' '));
                        const hasLabel = (element, expected, contains = false) =>
                            Array.from(element.querySelectorAll('*'))
                                .filter(isVisible)
                                .some(node => {
                                    const text = normalize(node.innerText || node.textContent);
                                    return contains ? text.includes(expected) : text === expected;
                                });

                        section.querySelectorAll(`[data-out-party-card="${args.marker}"]`)
                            .forEach(node => node.removeAttribute('data-out-party-card'));

                        const expectedTitle = normalize(args.title);
                        const titleNodes = [section, ...Array.from(section.querySelectorAll('*'))]
                            .filter(isVisible)
                            .filter(node => {
                                const text = normalize(node.innerText || node.textContent);
                                return text === expectedTitle || ownText(node) === expectedTitle;
                            });
                        if (titleNodes.length === 0) {
                            return false;
                        }

                        let best = null;
                        let bestScore = Number.NEGATIVE_INFINITY;
                        let bestDepth = Number.POSITIVE_INFINITY;
                        for (const titleNode of titleNodes) {
                            let depth = 0;
                            for (let candidate = titleNode; candidate && candidate !== section.parentElement; candidate = candidate.parentElement) {
                                if (!isVisible(candidate)) {
                                    depth += 1;
                                    continue;
                                }
                                const inputs = candidate.querySelectorAll(
                                    "input, textarea, select, [role='combobox'], [role='textbox']").length;
                                if (inputs === 0) {
                                    depth += 1;
                                    if (candidate === section) {
                                        break;
                                    }
                                    continue;
                                }

                                const partyTitleCount = [candidate, ...Array.from(candidate.querySelectorAll('*'))]
                                    .filter(isVisible)
                                    .reduce((count, node) => {
                                        const text = normalize(node.innerText || node.textContent);
                                        const directText = ownText(node);
                                        return count + ((knownPartyTitles.has(text) || knownPartyTitles.has(directText)) ? 1 : 0);
                                    }, 0);
                                const score = (hasLabel(candidate, 'ADDRESS') ? 1000 : 0)
                                        + (hasLabel(candidate, 'COUNTRY CODE', true) ? 500 : 0)
                                        + (hasLabel(candidate, 'UEN') ? 250 : 0)
                                        + (partyTitleCount <= 1 ? 250 : 0)
                                        - Math.max(0, partyTitleCount - 1) * 400
                                        + Math.min(inputs, 12)
                                        - Math.max(0, inputs - 12) * 10;
                                if (score > bestScore || (score === bestScore && depth < bestDepth)) {
                                    best = candidate;
                                    bestScore = score;
                                    bestDepth = depth;
                                }
                                if (candidate === section) {
                                    break;
                                }
                                depth += 1;
                            }
                        }

                        if (!best) {
                            return false;
                        }
                        best.setAttribute('data-out-party-card', args.marker);
                        return true;
                    }
                    """, java.util.Map.of(
                    "title", title,
                    "marker", marker));
            if (Boolean.TRUE.equals(found)) {
                Locator marked = page.locator("[data-out-party-card='" + marker + "']");
                Locator visibleMarked = firstVisible(marked);
                if (visibleMarked != null) {
                    return visibleMarked;
                }
            }
        } catch (Exception ignored) {
        }

        String escapedTitle = toXpathLiteral(title);
        Locator fallback = partySection.locator(
                "xpath=(.//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox']][1]");
        return firstVisible(fallback);
    }

    private Locator resolvePartyInfoSectionOrNull() {
        return resolveSectionOrNull("Party Info (P)");
    }

    private String describePartyInfoSectionTextOrNull() {
        try {
            Locator partySection = resolvePartyInfoSectionOrNull();
            if (partySection == null) {
                return "N/A (Party Info section not resolved)";
            }
            return normalize(partySection.innerText());
        } catch (Exception ignored) {
            return "N/A (failed to read section text)";
        }
    }

    @Override
    protected Locator resolveSection(String sectionTitle) {
        String escapedTitle = toXpathLiteral(sectionTitle);
        String controlQuery = ".//input or .//textarea or .//select or .//*[@role='combobox'] or .//*[@role='textbox'] or .//button";

        Locator exactNearestSection = page.locator(
                "xpath=((//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[" + controlQuery + "][1])");
        Locator visibleExactNearestSection = firstVisible(exactNearestSection);
        if (visibleExactNearestSection != null) {
            return visibleExactNearestSection;
        }

        return super.resolveSection(sectionTitle);
    }

    @Override
    protected Locator resolveSectionOrNull(String title) {
        try {
            return resolveSection(title);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void fillLookupPartyComponent(String selector, String partyName, String partyId) {
        if (partyName == null || partyName.isBlank()) {
            clearLookupPartyComponent(selector);
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

        focusAndTypeByClickOnly(field, partyName, partyName, partyId);
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
        if (partyName == null || partyName.isBlank()) {
            clearLookupPartyRow(rowLabel);
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

        Locator idField = resolveVisibleEditableFieldInRowOrNull(row, 1);
        if (syncPartyLookupComponentSelection(rowLabel, partyName, partyId)
                && lookupPartyRowResolved(row, nameField, idField, partyName, partyId, 1500)) {
            return;
        }

        tryLookupPartySelection(nameField, partyName, partyName, partyId);
        if (lookupPartyRowResolved(row, nameField, idField, partyName, partyId, 2000)) {
            return;
        }

        boolean nameResolved = waitForAnyRenderedFieldValue(nameField, 800, partyName)
                || rowTextContains(row, partyName);
        if (idField != null && partyId != null && !partyId.isBlank() && nameResolved) {
            tryFillLookupPartyIdField(rowLabel, idField, partyId);
            if (lookupPartyRowResolved(row, nameField, idField, partyName, partyId, 2000)) {
                return;
            }
        }

        if (partyId != null && !partyId.isBlank() && !nameResolved) {
            tryLookupPartySelection(nameField, partyId, partyName, partyId);
            if (lookupPartyRowResolved(row, nameField, idField, partyName, partyId, 2000)) {
                return;
            }
        }

        if (idField != null && partyId != null && !partyId.isBlank() && !waitForAnyRenderedFieldValue(idField, 1000, partyId)) {
            tryFillLookupPartyIdField(rowLabel, idField, partyId);
        }
    }

    private boolean tryFillLookupPartyIdField(String rowLabel, Locator idField, String partyId) {
        if (idField == null || partyId == null || partyId.isBlank()) {
            return false;
        }
        try {
            focusAndType(idField, partyId, false);
            return true;
        } catch (IllegalStateException exception) {
            logFieldMappingWarning("Lookup party row '" + rowLabel
                    + "' rejected direct UEN entry '" + partyId
                    + "'. Continuing with component-based lookup resolution. Cause: " + exception.getMessage());
            return false;
        }
    }

    private boolean tryLookupPartySelection(Locator field, String searchValue, String... suggestionHints) {
        if (field == null || searchValue == null || searchValue.isBlank()) {
            return false;
        }
        try {
            focusAndTypeByClickOnly(field, searchValue, suggestionHints);
            return true;
        } catch (IllegalStateException exception) {
            String message = exception.getMessage();
            if (message != null && message.contains("Lookup suggestion click did not resolve field")) {
                return false;
            }
            throw exception;
        }
    }

    private boolean rowTextContains(Locator row, String expectedText) {
        String normalizedExpected = normalize(expectedText);
        if (normalizedExpected.isBlank()) {
            return false;
        }

        String normalizedRowText = normalize(readRowText(row));
        return !normalizedRowText.isBlank()
                && (normalizedRowText.contains(normalizedExpected)
                || normalizedExpected.contains(normalizedRowText));
    }

    private boolean lookupPartyRowResolved(
            Locator row,
            Locator nameField,
            Locator idField,
            String partyName,
            String partyId,
            int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 500);
        String normalizedPartyName = normalize(partyName);
        String normalizedPartyId = normalize(partyId);
        while (System.currentTimeMillis() <= deadline) {
            boolean nameMatches = waitForAnyRenderedFieldValue(nameField, 200, partyName, partyId);
            boolean idMatches = normalizedPartyId.isBlank()
                    || (idField != null && waitForAnyRenderedFieldValue(idField, 200, partyId));
            String rowText = normalize(readRowText(row));
            boolean rowNameMatches = normalizedPartyName.isBlank()
                    || rowText.equalsIgnoreCase(normalizedPartyName)
                    || rowText.contains(normalizedPartyName)
                    || normalizedPartyName.contains(rowText);
            boolean rowIdMatches = normalizedPartyId.isBlank()
                    || rowText.equalsIgnoreCase(normalizedPartyId)
                    || rowText.contains(normalizedPartyId)
                    || normalizedPartyId.contains(rowText);
            if ((nameMatches || rowNameMatches) && (idMatches || rowIdMatches)) {
                return true;
            }
            page.waitForTimeout(100);
        }
        return false;
    }

    private boolean lookupPartyRowResolvedSnapshot(
            Locator row,
            Locator nameField,
            Locator idField,
            String partyName,
            String partyId) {
        String normalizedPartyName = normalize(partyName);
        String normalizedPartyId = normalize(partyId);
        String nameValue = normalize(readRenderedFieldValue(nameField));
        String idValue = normalize(readRenderedFieldValue(idField));
        String rowText = normalize(readRowText(row));

        boolean nameMatches = normalizedPartyName.isBlank()
                || partyFieldMatches(nameValue, normalizedPartyName)
                || rowText.equalsIgnoreCase(normalizedPartyName)
                || rowText.contains(normalizedPartyName)
                || normalizedPartyName.contains(rowText);
        boolean idMatches = normalizedPartyId.isBlank()
                || partyFieldMatches(idValue, normalizedPartyId)
                || rowText.equalsIgnoreCase(normalizedPartyId)
                || rowText.contains(normalizedPartyId)
                || normalizedPartyId.contains(rowText);
        return nameMatches && idMatches;
    }

    private String readRowText(Locator row) {
        if (row == null) {
            return "";
        }
        try {
            return row.innerText();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void clearLookupPartyComponent(String selector) {
        Locator component = firstVisible(page.locator(selector));
        if (component == null) {
            return;
        }

        Locator field = firstVisible(component.locator(
                "input:not([type='checkbox']), textarea, select, [role='combobox'], [role='textbox']"));
        if (field == null) {
            return;
        }

        clearLookupLikeField(field);
    }

    private void clearLookupPartyRow(String rowLabel) {
        Locator row = resolvePartyTableRow(rowLabel);
        if (row == null) {
            return;
        }

        Locator nameField = resolveVisibleEditableFieldInRowOrNull(row, 0);
        if (nameField != null) {
            clearLookupLikeField(nameField);
        }

        Locator idField = resolveVisibleEditableFieldInRowOrNull(row, 1);
        if (idField != null) {
            clearLookupLikeField(idField);
        }
    }

    private void clearLookupLikeField(Locator field) {
        try {
            page.keyboard().press("Escape");
        } catch (Exception ignored) {
        }
        page.waitForTimeout(150);
        field.scrollIntoViewIfNeeded();

        try {
            field.evaluate("""
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
                        const container = element.closest('.ng-select, [role="combobox"], [class*="select"], [class*="combobox"]')
                            || element.parentElement;
                        const clearAction = container?.querySelector(
                            '.ng-clear-wrapper, .ng-clear, .ng-value-icon, [aria-label*="clear" i], [title*="clear" i]');
                        if (isVisible(clearAction)) {
                            clearAction.click();
                        }
                        if ('value' in element) {
                            element.value = '';
                        }
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        element.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    """);
        } catch (Exception ignored) {
        }

        try {
            field.click(new Locator.ClickOptions().setForce(true));
            field.fill("");
        } catch (Exception ignored) {
            try {
                page.keyboard().press("Control+A");
                page.keyboard().press("Backspace");
            } catch (Exception ignoredAgain) {
            }
        }

        try {
            page.keyboard().press("Tab");
        } catch (Exception ignored) {
        }
        page.waitForTimeout(150);
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

    private void syncVisibleTextComponentValueInSection(
            String sectionTitle,
            String formControlName,
            String value,
            String... labelHints) {
        if (value == null || value.isBlank()) {
            return;
        }

        Locator section = resolveSectionOrNull(sectionTitle);
        if (section == null) {
            return;
        }

        Locator component = firstVisible(section.locator("[formcontrolname='" + formControlName + "']"));
        if (component == null) {
            component = firstVisible(section.locator(
                    "input[formcontrolname='" + formControlName + "'], textarea[formcontrolname='" + formControlName + "']"));
        }
        if (component == null) {
            return;
        }

        try {
            component.evaluate("""
                    (element, args) => {
                        const normalize = input => (input || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                        const isVisible = candidate => !!candidate && !!(candidate.offsetWidth || candidate.offsetHeight || candidate.getClientRects().length);
                        const hints = (args.labelHints || []).map(normalize).filter(Boolean);
                        const scopeText = normalize(element.innerText || element.textContent || '');
                        if (hints.length > 0 && scopeText && !hints.some(hint => scopeText.includes(hint) || hint.includes(scopeText))) {
                            const labeledContainer = element.closest('app-textbox, app-input, app-text-input, .form-group, .mat-form-field, .ng-star-inserted, div');
                            const containerText = normalize(labeledContainer?.innerText || labeledContainer?.textContent || '');
                            if (!containerText || !hints.some(hint => containerText.includes(hint))) {
                                return false;
                            }
                        }

                        const candidateElements = [
                            element,
                            element.querySelector?.('input, textarea'),
                            element.closest?.('[formcontrolname]'),
                            element.parentElement,
                            element.parentElement?.querySelector?.('input, textarea')
                        ].filter(Boolean);

                        for (const candidate of candidateElements) {
                            const component = typeof window.ng !== 'undefined' && typeof window.ng.getComponent === 'function'
                                ? window.ng.getComponent(candidate)
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
                                const nativeInput = component.inputElement?.nativeElement
                                    || candidate.querySelector?.('input, textarea');
                                if (nativeInput && isVisible(nativeInput)) {
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
                                return true;
                            }
                        }

                        const field = element.matches?.('input, textarea')
                            ? element
                            : element.querySelector?.('input, textarea');
                        if (!field || !isVisible(field)) {
                            return false;
                        }

                        field.value = args.value;
                        field.dispatchEvent(new Event('input', { bubbles: true }));
                        field.dispatchEvent(new Event('change', { bubbles: true }));
                        field.dispatchEvent(new Event('blur', { bubbles: true }));
                        return true;
                    }
                    """, java.util.Map.of(
                    "value", value,
                    "labelHints", labelHints));
        } catch (Exception ignored) {
        }
    }

    private void fillVerifiedTransportTextField(
            String sectionTitle,
            String formControlName,
            String value,
            String... labels) {
        if (value == null || value.isBlank()) {
            return;
        }

        fillFieldInSectionByAnyLabelIfPresent(sectionTitle, value, labels);

        Locator field = resolveTransportTextFieldOrNull(sectionTitle, formControlName, labels);
        if (field != null && waitForExpectedRenderedValue(field, value, 1000)) {
            return;
        }
        if (field != null) {
            ensureTransportTextFieldValue(field, value);
            if (waitForExpectedRenderedValue(field, value, 1000)) {
                return;
            }
        }

        syncVisibleTextComponentValueInSection(sectionTitle, formControlName, value, labels);

        field = resolveTransportTextFieldOrNull(sectionTitle, formControlName, labels);
        if (field != null) {
            ensureTransportTextFieldValue(field, value);
        }
        if (field == null || !waitForExpectedRenderedValue(field, value, 1500)) {
            throw new IllegalStateException(sectionTitle + " field did not render expected value for "
                    + formControlName + ". Expected: " + value
                    + ", Actual: " + (field == null ? "<field-not-found>" : readRenderedFieldValue(field)));
        }
    }

    private Locator resolveTransportTextFieldOrNull(String sectionTitle, String formControlName, String... labels) {
        for (String label : labels) {
            Locator field = resolveFieldByLabelInSectionOrNull(sectionTitle, label, 0);
            if (field != null) {
                Locator concreteField = resolveConcreteTransportEditableFieldOrNull(field);
                if (concreteField != null) {
                    return concreteField;
                }
            }
        }

        Locator section = resolveSectionOrNull(sectionTitle);
        if (section == null) {
            return null;
        }

        Locator field = firstVisible(section.locator(
                "input[formcontrolname='" + formControlName + "'], textarea[formcontrolname='" + formControlName + "']"));
        if (field != null) {
            return field;
        }

        return resolveConcreteTransportEditableFieldOrNull(
                firstVisible(section.locator("[formcontrolname='" + formControlName + "']")));
    }

    private boolean waitForExpectedRenderedValue(Locator field, String expectedValue, int timeoutMs) {
        if (field == null || expectedValue == null || expectedValue.isBlank()) {
            return false;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        String normalizedExpected = normalize(expectedValue);
        while (System.currentTimeMillis() <= deadline) {
            String currentValue = normalize(readRenderedFieldValue(field));
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

    private void ensureTransportTextFieldValue(Locator field, String expectedValue) {
        if (field == null || expectedValue == null || expectedValue.isBlank()) {
            return;
        }

        try {
            String currentValue = normalize(readRenderedFieldValue(field));
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
                        element.setAttribute('value', newValue);
                        element.dispatchEvent(new Event('input', { bubbles: true }));
                        element.dispatchEvent(new Event('change', { bubbles: true }));
                        element.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    """, expectedValue);
        } catch (Exception ignored) {
        }
    }

    private Locator resolveConcreteTransportEditableFieldOrNull(Locator candidate) {
        Locator visibleCandidate = firstVisible(candidate);
        if (visibleCandidate == null) {
            return null;
        }

        if (isConcreteTransportEditableField(visibleCandidate)) {
            return visibleCandidate;
        }

        Locator nestedConcreteField = firstVisible(visibleCandidate.locator(
                "input:not([type='checkbox']):not([readonly]):not([disabled]), "
                        + "textarea:not([readonly]):not([disabled]), "
                        + "select:not([disabled]), "
                        + "[contenteditable='true']"));
        if (nestedConcreteField != null) {
            return nestedConcreteField;
        }

        Locator nestedTextbox = firstVisible(visibleCandidate.locator("[role='combobox'], [role='textbox']"));
        if (nestedTextbox != null) {
            return nestedTextbox;
        }

        Locator parentConcreteField = firstVisible(visibleCandidate.locator(
                "xpath=(ancestor::*[.//input or .//textarea or .//select or .//*[@contenteditable='true']][1]"
                        + "//input[not(@type='checkbox') and not(@readonly) and not(@disabled)]"
                        + " | ancestor::*[.//input or .//textarea or .//select or .//*[@contenteditable='true']][1]"
                        + "//textarea[not(@readonly) and not(@disabled)]"
                        + " | ancestor::*[.//input or .//textarea or .//select or .//*[@contenteditable='true']][1]"
                        + "//select[not(@disabled)]"
                        + " | ancestor::*[.//input or .//textarea or .//select or .//*[@contenteditable='true']][1]"
                        + "//*[@contenteditable='true'])[1]"));
        if (parentConcreteField != null) {
            return parentConcreteField;
        }

        return visibleCandidate;
    }

    private boolean isConcreteTransportEditableField(Locator field) {
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
        } catch (Exception ignored) {
            return false;
        }
    }

    private void syncCheckboxValue(boolean checked, String... labelHints) {
        syncCheckboxValueByFormControl(checked, null, labelHints);
    }

    private void syncCheckboxValueByFormControl(boolean checked, String formControlName, String... labelHints) {
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
                            || document.querySelector(`input[type="checkbox"][formcontrolname="${args.formControlName || 'declarationIndicator'}"]`);
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
                    "labelHints", labelHints,
                    "formControlName", formControlName));
        } catch (Exception ignored) {
        }
    }

    private Locator resolvePartyTableRow(String rowLabel) {
        Locator partySection = resolvePartyInfoSectionOrNull();
        if (partySection == null) {
            return null;
        }

        String escaped = toXpathLiteral(rowLabel);
        Locator tableRow = partySection.locator(
                "xpath=(.//*[normalize-space(translate(., '*', ''))=" + escaped + "])[1]/ancestor::tr[1]");
        Locator visibleTableRow = firstVisible(tableRow);
        if (visibleTableRow != null) {
            return visibleTableRow;
        }

        Locator genericRow = partySection.locator(
                "xpath=(.//*[normalize-space(translate(., '*', ''))=" + escaped + "])[1]"
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

    private void dismissTransientOverlays() {
        try {
            page.keyboard().press("Escape");
        } catch (PlaywrightException ignored) {
        }

        try {
            page.waitForTimeout(150);
        } catch (PlaywrightException ignored) {
        }
    }

    private void fillFirstPresentField(String value, String... labels) {
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

    private void fillLookupFieldIfPresentByLabels(String value, String... labels) {
        if (isPlaceholderLookupValue(value)) {
            return;
        }
        for (String label : labels) {
            Locator field = resolveFieldByLabelOrNull(label, 0);
            if (field != null) {
                focusAndType(field, value, true, value);
                return;
            }
        }
    }

    private void fillSelectLikeLookupFieldIfPresentByLabels(String value, String... labels) {
        if (isPlaceholderLookupValue(value)) {
            return;
        }
        for (String label : labels) {
            Locator field = resolveSelectLikeFieldByLabelOrNull(label, 0);
            if (field != null) {
                focusAndType(field, value, true, value);
                return;
            }
        }
        fillLookupFieldIfPresentByLabels(value, labels);
    }

    private boolean isPlaceholderLookupValue(String value) {
        String normalizedValue = normalize(value);
        return normalizedValue == null
                || normalizedValue.isBlank()
                || "-".equals(normalizedValue)
                || "-NA-".equalsIgnoreCase(normalizedValue)
                || "N/A".equalsIgnoreCase(normalizedValue);
    }

    private String joinNonBlank(String delimiter, String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(value.trim());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = normalize(node.asText());
        return value.isBlank() ? null : value;
    }

    @Override
    protected String arrayText(JsonNode arrayNode, int index) {
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
