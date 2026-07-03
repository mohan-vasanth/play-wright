package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.InpDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InpDeclarationPartyUiTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void fillsInpSpecificPartyRowsAndConsigneeCard() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Exporter</div>
                      <app-exporter-lookup formcontrolname="name" style="display: block;">
                        <input id="exporter-name" type="text" style="width: 320px; height: 28px;">
                      </app-exporter-lookup>
                      <div>🔍</div>
                      <input id="exporter-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display: block;">
                        <input id="outward-name" type="text" style="width: 320px; height: 28px;">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                    <div id="consignee-card" style="display: grid; grid-template-columns: 160px 360px 220px; gap: 12px; align-items: center;">
                      <div>Consignee</div>
                      <input id="consignee-name" type="text" style="width: 320px; height: 28px;">
                      <input id="consignee-code" type="text" style="width: 220px; height: 28px;">
                      <div></div>
                      <div>
                        <label>Address</label>
                        <input id="consignee-address" type="text" style="width: 320px; height: 28px;">
                      </div>
                      <div>
                        <label>Country Code</label>
                        <input id="consignee-country" type="text" style="width: 220px; height: 28px;">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        InpDeclarationPage declarationPage = new InpDeclarationPage(page);
        Method method = InpDeclarationPage.class.getDeclaredMethod(
                "fillDeclarationSpecificPartyInfo",
                JsonNode.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode party = OBJECT_MAPPER.readTree("""
                {
                  "exporterParty": {
                    "partyDetail": {
                      "partyIdentification": { "id": "198402847H" },
                      "partyName": { "name": "BAYSWATER SHIPPING & FORWARDING PTE LTD" }
                    }
                  },
                  "outwardCarrierAgentParty": {
                    "partyIdentification": { "id": "199201306R" },
                    "partyName": { "name": "AB SHIPPING PTE LTD" }
                  },
                  "consigneeParty": {
                    "partyName": { "name": "CRIMSONLOGIC PTE LTD" },
                    "address": {
                      "addressLine": {
                        "line": [ "1 SCIENCE PARK DRIVE", "LEVEL 2" ]
                      },
                      "cityName": "SINGAPORE",
                      "postalZone": "118221",
                      "countryCode": "SG"
                    }
                  }
                }
                """);

        method.invoke(declarationPage, OBJECT_MAPPER.createObjectNode(), party);

        assertEquals("BAYSWATER SHIPPING & FORWARDING PTE LTD", page.locator("#exporter-name").inputValue());
        assertEquals("198402847H", page.locator("#exporter-uen").inputValue());
        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-name").inputValue());
        assertEquals("199201306R", page.locator("#outward-uen").inputValue());
        assertEquals("CRIMSONLOGIC PTE LTD", page.locator("#consignee-name").inputValue());
        assertEquals("1 SCIENCE PARK DRIVE, LEVEL 2, SINGAPORE, 118221", page.locator("#consignee-address").inputValue());
        assertEquals("SG", page.locator("#consignee-country").inputValue());
    }
}
