package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InpDeclarationPartyMappingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void mapsClaimantPartyUsingInpSpecificLabels() throws Exception {
        RecordingInpDeclarationPage declarationPage = new RecordingInpDeclarationPage();
        Method method = InpDeclarationPage.class.getDeclaredMethod(
                "fillDeclarationSpecificPartyInfo",
                JsonNode.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode party = OBJECT_MAPPER.readTree("""
                {
                  "claimantParty": {
                    "partyDetail": {
                      "partyIdentification": { "id": "198800784N" },
                      "partyName": { "name": "CRIMSONLOGIC PTE LTD" }
                    },
                    "claimantInformation": {
                      "codeValue": "S1234567T",
                      "name": "DA LAO BAN"
                    }
                  }
                }
                """);

        method.invoke(declarationPage, OBJECT_MAPPER.createObjectNode(), party);

        assertEquals("CRIMSONLOGIC PTE LTD", declarationPage.partyRows.get("Claimant Party"));
        assertEquals("S1234567T", declarationPage.filledFields.get("Claimant Id"));
        assertEquals("DA LAO BAN", declarationPage.filledFields.get("Claimant Name"));
    }

    @Test
    void mapsInpPartyRowsWhenPartyNameUsesFlatNameField() throws Exception {
        RecordingInpDeclarationPage declarationPage = new RecordingInpDeclarationPage();
        Method method = InpDeclarationPage.class.getDeclaredMethod(
                "fillDeclarationSpecificPartyInfo",
                JsonNode.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode party = OBJECT_MAPPER.readTree("""
                {
                  "outwardCarrierAgentParty": {
                    "partyIdentification": { "id": "197702772D" },
                    "name": "CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD"
                  }
                }
                """);

        method.invoke(declarationPage, OBJECT_MAPPER.createObjectNode(), party);

        assertEquals(
                "CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD",
                declarationPage.partyRows.get("Outward Carrier"));
    }

    private static final class RecordingInpDeclarationPage extends InpDeclarationPage {

        private final Map<String, String> partyRows = new LinkedHashMap<>();
        private final Map<String, String> filledFields = new LinkedHashMap<>();

        private RecordingInpDeclarationPage() {
            super(null);
        }

        @Override
        protected void fillPartyRowIfPresent(String rowLabel, JsonNode partyNode) {
            String partyName = partyName(partyNode);
            if (partyName != null) {
                partyRows.put(rowLabel, partyName);
            }
        }

        @Override
        protected void fillFieldIfPresent(String label, String value) {
            if (value != null && !value.isBlank()) {
                filledFields.put(label, value);
            }
        }
    }
}
