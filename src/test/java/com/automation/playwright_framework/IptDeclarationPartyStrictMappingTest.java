package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IptDeclarationPartyStrictMappingTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void clearsInwardCarrierRowWhenJsonOmitsTheParty() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center;">
                      <div>Inward Carrier</div>
                      <input id="carrier-name" type="text" value="STALE CARRIER NAME" style="width: 320px; height: 28px;">
                      <div>🔍</div>
                      <input id="carrier-uen" type="text" value="STALEUEN123" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        invokeFillPartyInfo("""
                {
                  "party": {}
                }
                """);

        assertEquals("", page.locator("#carrier-name").inputValue());
        assertEquals("", page.locator("#carrier-uen").inputValue());
    }

    @Test
    void clearsInwardCarrierNameWhenJsonContainsOnlyUen() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center;">
                      <div>Inward Carrier</div>
                      <input id="carrier-name" type="text" value="STALE CARRIER NAME" style="width: 320px; height: 28px;">
                      <div>🔍</div>
                      <input id="carrier-uen" type="text" value="" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        invokeFillPartyInfo("""
                {
                  "party": {
                    "inwardCarrierAgentParty": {
                      "partyIdentification": {
                        "id": "202535359H"
                      }
                    }
                  }
                }
                """);

        assertEquals("", page.locator("#carrier-name").inputValue());
        assertEquals("202535359H", page.locator("#carrier-uen").inputValue());
    }

    @Test
    void doesNotPopulateMissingInwardCarrierFromOtherPartyNames() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1280px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center;">
                      <div>Importer</div>
                      <input id="importer-name" type="text" value="" style="width: 320px; height: 28px;">
                      <div>🔍</div>
                      <input id="importer-uen" type="text" value="" style="width: 220px; height: 28px;">
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center;">
                      <div>Inward Carrier</div>
                      <input id="carrier-name" type="text" value="STALE CARRIER NAME" style="width: 320px; height: 28px;">
                      <div>🔍</div>
                      <input id="carrier-uen" type="text" value="STALEUEN123" style="width: 220px; height: 28px;">
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center;">
                      <div>Freight Forwarder</div>
                      <input id="forwarder-name" type="text" value="" style="width: 320px; height: 28px;">
                      <div>🔍</div>
                      <input id="forwarder-uen" type="text" value="" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        invokeFillPartyInfo("""
                {
                  "party": {
                    "importerParty": {
                      "partyIdentification": { "id": "198700002E" },
                      "partyName": { "name": "FORESPAND FOOD ENTER PRISE PTE LTD" }
                    },
                    "freightForwarderParty": {
                      "partyIdentification": { "id": "202535359H" },
                      "partyName": { "name": "ADATACOMPANY PTE LTD" }
                    }
                  }
                }
                """);

        assertEquals("", page.locator("#carrier-name").inputValue());
        assertEquals("", page.locator("#carrier-uen").inputValue());
    }

    @Test
    void resolvePartyIdFieldDoesNotCrossIntoNeighborRowWhenTargetRowFieldIsUnavailable() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1280px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div style="padding-top: 20px; font-weight: 600;">Importer</div>
                      <div style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-importer-lookup formcontrolname="name" style="display: block;">
                          <input id="importer-name" type="text" value="FORESPAND FOOD ENTER PRISE PTE LTD" style="width: 320px; height: 28px;">
                        </app-importer-lookup>
                        <div>🔍</div>
                        <input id="importer-uen-hidden" type="text" value="198700002E" style="display: none; width: 220px; height: 28px;">
                      </div>
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div style="padding-top: 20px; font-weight: 600;">Inward Carrier</div>
                      <div style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" value="" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" value="" style="width: 220px; height: 28px;">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod("resolvePartyIdFieldOrNull", String.class);
        method.setAccessible(true);

        Object resolved = method.invoke(declarationPage, "Importer");

        assertNull(resolved);
    }

    private void invokeFillPartyInfo(String json) throws Exception {
        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod("fillPartyInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        method.invoke(declarationPage, OBJECT_MAPPER.readTree(json));
    }
}
