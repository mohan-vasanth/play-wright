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
        waitForActionButtonEnabled("SUBMIT DECLARATION", 15000);
        clickActionButtonExactWithRetry("SUBMIT DECLARATION", 3);
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

    private void fillTransportInfo(JsonNode data) {
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

    private void fillPartyInfo(JsonNode data) {
        JsonNode party = data.path("party");

        fillPartyLookupRow("Importer", party.path("importerParty"));
        fillPartyLookupRow("Inward Carrier", party.path("inwardCarrierAgentParty"));
        fillPartyLookupRow("Freight Forwarder", party.path("freightForwarderParty"));
        fillPartyLookupRow("Outward Carrier", party.path("outwardCarrierAgentParty"));
        fillPartyLookupRow("Declaring Agent", party.path("declaringAgentParty"));

        fillLookupPartyComponent("app-importer-lookup[formcontrolname='name']",
                text(partyIdentityNode(party.path("importerParty")).path("partyName"), "name"),
                text(partyIdentityNode(party.path("importerParty")).path("partyIdentification"), "id"));
        fillLookupPartyComponent("app-inward-carrier-lookup[formcontrolname='name']",
                text(partyIdentityNode(party.path("inwardCarrierAgentParty")).path("partyName"), "name"),
                text(partyIdentityNode(party.path("inwardCarrierAgentParty")).path("partyIdentification"), "id"));
        fillLookupPartyComponent("app-freight-forwarder-lookup[formcontrolname='name']",
                text(partyIdentityNode(party.path("freightForwarderParty")).path("partyName"), "name"),
                text(partyIdentityNode(party.path("freightForwarderParty")).path("partyIdentification"), "id"));
        fillLookupPartyComponent("app-outward-carrier-agent-lookup[formcontrolname='name']",
                text(partyIdentityNode(party.path("outwardCarrierAgentParty")).path("partyName"), "name"),
                text(partyIdentityNode(party.path("outwardCarrierAgentParty")).path("partyIdentification"), "id"));

        fillPartyCard(
                "Exporter",
                partyIdentityNode(party.path("exporterParty")).path("partyName").path("name"),
                partyIdentityNode(party.path("exporterParty")).path("partyIdentification").path("id"),
                party.path("exporterParty").path("address"));
        fillPartyCard(
                "Consignee",
                partyIdentityNode(party.path("consigneeParty")).path("partyName").path("name"),
                partyIdentityNode(party.path("consigneeParty")).path("partyIdentification").path("id"),
                party.path("consigneeParty").path("address"));
        fillPartyCard(
                "End User",
                partyIdentityNode(party.path("endUserParty")).path("partyName").path("name"),
                partyIdentityNode(party.path("endUserParty")).path("partyIdentification").path("id"),
                party.path("endUserParty").path("address"));
        fillPartyCard(
                "Manufacturer",
                partyIdentityNode(party.path("manufacturerParty")).path("partyName").path("name"),
                partyIdentityNode(party.path("manufacturerParty")).path("partyIdentification").path("id"),
                party.path("manufacturerParty").path("address"));
    }

    private void fillPartyLookupRow(String rowLabel, JsonNode partyNode) {
        JsonNode identityNode = partyIdentityNode(partyNode);
        fillLookupPartyRow(
                rowLabel,
                text(identityNode.path("partyName"), "name"),
                text(identityNode.path("partyIdentification"), "id"));
    }

    private JsonNode partyIdentityNode(JsonNode partyNode) {
        JsonNode partyDetail = partyNode.path("partyDetail");
        if (!isMissingOrEmpty(partyDetail)) {
            return partyDetail;
        }
        return partyNode;
    }

    private void fillPartyCard(String title, JsonNode nameNode, JsonNode idNode, JsonNode addressNode) {
        String name = normalize(nodeText(nameNode));
        String id = normalize(nodeText(idNode));
        if ((name == null || name.isBlank()) && (addressNode == null || addressNode.isMissingNode())) {
            return;
        }

        ensureAccordionExpanded(title, "Country Code");
        Locator card = resolvePartyCard(title);
        if (card == null) {
            return;
        }

        fillNthLookupFieldInScopeIfPresent(card, 0, name, name, id);
        fillFieldAfterScopeLabelIfPresent(card, "UEN", 0, id);

        JsonNode addressLines = addressNode.path("addressLine").path("line");
        String addressLine1 = arrayText(addressLines, 0);
        String addressLine2 = arrayText(addressLines, 1);
        String city = text(addressNode, "cityName");
        String postalCode = firstNonBlank(text(addressNode, "postalZone"), text(addressNode, "countrySubentityCode"));
        String compactAddress = joinNonBlank(", ", addressLine1, addressLine2, city);

        fillFieldAfterScopeLabelIfPresent(card, "Address", 0, compactAddress);
        fillFieldAfterScopeLabelIfPresent(card, "Address Line 1", 0, addressLine1);
        fillFieldAfterScopeLabelIfPresent(card, "Address Line 2", 0, addressLine2);
        fillFieldAfterScopeLabelIfPresent(card, "City", 0, city);
        fillFieldAfterScopeLabelIfPresent(card, "Postal Code", 0, postalCode);
        fillLookupFieldAfterScopeLabelIfPresent(card, "Country Code", 0,
                text(addressNode, "countryCode"),
                text(addressNode, "countryCode"));
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

    private void fillItemInfo(JsonNode data) {
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

        fillLookupFieldIfPresent("Invoice Number",
                firstNonBlank(text(item, "itemInvoiceNumber"), text(invoice, "invoiceNumber")),
                firstNonBlank(text(item, "itemInvoiceNumber"), text(invoice, "invoiceNumber")));
        fillFieldIfPresent("Inward HAWB", text(item, "inHawbHucrHblNumber"));
        fillFieldIfPresent("Outward HAWB", text(item, "outHawbHucrHblNumber"));
        fillLookupFieldIfPresent("Currency",
                text(unitPriceValue.path("amount"), "currencyID"),
                text(unitPriceValue.path("amount"), "currencyID"));
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

        fillFieldIfPresent("Item Unit Value",
                normalizeNumericForEntry(text(unitPriceValue.path("amount"), "value")));
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
        boolean hasItemCertificateData = !isMissingOrEmpty(itemCertificate);
        boolean itemCoEnabled = hasItemCertificateData
                || formMetaData.path("itemCoIsActive").path(0).asBoolean(false);
        if (!itemCoEnabled) {
            return;
        }

        setCheckboxByLabel("Certificate of Origin (CO)", true);
        page.waitForTimeout(300);

        Locator itemCertificateSection = waitForItemCertificateSectionOrNull(3000);
        if (itemCertificateSection == null) {
            throw new IllegalStateException("Certificate of Origin (CO) section did not open in the Item tab.");
        }
        if (!hasItemCertificateData) {
            return;
        }

        fillQuantityRowInScope(itemCertificateSection, "Certificate Quantity", itemCertificate.path("itemCertificateQuantity"));
        fillQuantityRowInScope(itemCertificateSection, "Textile Quota Quantity", itemCertificate.path("textileQuotaQuantity"));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Manufacturing Cost Date", 0,
                formatUiDate(text(itemCertificate, "manufacturingCostDate")));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Certificate Item Value", 0,
                normalizeNumericForEntry(text(itemCertificate, "itemValue")));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Textile Category Code", 0,
                text(itemCertificate, "textileCategoryCode"));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Item Invoice Number", 0, text(itemCertificate, "itemInvoiceNumber"));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Item Invoice Date", 0,
                formatUiDate(text(itemCertificate, "itemInvoiceDate")));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "HS Code", 0, text(itemCertificate, "harmonizedSystemCode"));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Content Percent (%)", 0,
                normalizeNumericForEntry(text(itemCertificate, "contentPercent")));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Origin Criterion 1", 0, arrayText(itemCertificate.path("originCriterion"), 0));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Origin Criterion 2", 0, arrayText(itemCertificate.path("originCriterion"), 1));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Origin Criterion 3", 0, arrayText(itemCertificate.path("originCriterion"), 2));
        fillFieldAfterScopeLabelIfPresent(itemCertificateSection, "Certificate Item Description", 0,
                itemCertificateDescriptionText(itemCertificate.path("itemCertificateDescription")));
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
        String sectionTitle = toXpathLiteral("Certificate of Origin (CO)");
        return firstVisible(page.locator(
                "xpath=(//*[contains(normalize-space(translate(., '*', '')), " + sectionTitle + ")]"
                        + "[not(.//*[contains(normalize-space(translate(., '*', '')), " + sectionTitle + ")])])[last()]"
                        + "/ancestor::*[.//*[contains(normalize-space(translate(., '*', '')), 'Certificate Quantity')"
                        + " or contains(normalize-space(translate(., '*', '')), 'Certificate Item Description')"
                        + " or contains(normalize-space(translate(., '*', '')), 'Origin Criterion 1')]"
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
        field.scrollIntoViewIfNeeded();
        field.click(new Locator.ClickOptions().setForce(true));

        try {
            field.fill("");
        } catch (PlaywrightException ignored) {
            page.keyboard().press("Control+A");
            page.keyboard().press("Backspace");
        }

        try {
            field.fill(value);
        } catch (PlaywrightException ignored) {
            field.type(value, new Locator.TypeOptions().setDelay(60));
        }

        page.waitForTimeout(250);
        if (!waitForAnyRenderedFieldValue(field, 1500, value)) {
            try {
                field.evaluate("""
                        (element, newValue) => {
                            element.value = newValue;
                            element.dispatchEvent(new Event('input', { bubbles: true }));
                            element.dispatchEvent(new Event('change', { bubbles: true }));
                            element.dispatchEvent(new Event('blur', { bubbles: true }));
                        }
                        """, value);
            } catch (PlaywrightException ignored) {
            }
        }
        if (!waitForAnyRenderedFieldValue(field, 1500, value)) {
            throw new IllegalStateException("CPC " + label + " value was not rendered for row " + (rowIndex + 1)
                    + ". Expected: " + value + ", Actual: " + readRenderedFieldValue(field));
        }

        page.keyboard().press("Tab");
        page.waitForTimeout(150);
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
        String escapedTitle = toXpathLiteral(title);
        Locator locator = page.locator(
                "xpath=(//*[normalize-space(translate(., '*', ''))=" + escapedTitle + "])[last()]"
                        + "/ancestor::*[.//input or .//textarea or .//select or .//*[@role='combobox']][1]");
        return firstVisible(locator);
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

    private Locator resolveSectionOrNull(String title) {
        try {
            return resolveSection(title);
        } catch (Exception ignored) {
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

        focusAndType(field, partyName, true, partyName, partyId);
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

        focusAndType(nameField, partyName, true, partyName, partyId);
        Locator idField = resolveVisibleEditableFieldInRowOrNull(row, 1);
        if (idField != null && partyId != null && !partyId.isBlank() && !waitForAnyRenderedFieldValue(idField, 500, partyId)) {
            focusAndType(idField, partyId, false);
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
        if (value == null || value.isBlank()) {
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
