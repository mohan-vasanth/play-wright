package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

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
            syncVisibleTextComponentValue("transportIdentifier",
                    inwardTransportIdentifier,
                    "Transport Identifier",
                    "Aircraft Registration Number",
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
            setHiddenComponentValue(
                    "app-loading-port-lookup[formcontrolname='loadingPort']",
                    "Loading Port",
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
            fillFieldInSectionIfPresent(
                    "Outward Transport Means",
                    "MAWB/OUCR/OBL Number",
                    text(outwardTransportMeans, "mawboucroblNumber"));
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
        if (data.path("formMetaData").path("itemPackingIsActive").path(0).asBoolean(false) && hasPackingDescription) {
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
        ensureAccordionExpanded(
                "Packing Description",
                "Outer Pack Qty",
                "In Pack Qty",
                "Inner Pack Qty",
                "Inmost Pack Qty");
        Locator packingDescriptionSection = resolveSectionOrNull("Packing Description");
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

        fillQuantityRowInScope(packingDescriptionSection, "Outer Pack Qty", outerPackQuantity, defaultPackingUnitCode);
        fillQuantityRowInScope(packingDescriptionSection, "In Pack Qty", inPackQuantity, defaultPackingUnitCode);
        fillQuantityRowInScope(packingDescriptionSection, "Inner Pack Qty", innerPackQuantity, defaultPackingUnitCode);
        fillQuantityRowInScope(packingDescriptionSection, "Inmost Pack Qty", inmostPackQuantity, defaultPackingUnitCode);
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
                text(data.path("header").path("remarks"), "internalText"));
        fillFieldAfterScopeLabelIfPresent(remarksCard, "Customer Remarks", 0,
                text(data.path("header").path("remarks"), "customerText"));

        if (data.path("header").path("declarationIndicator").asBoolean(false)) {
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
