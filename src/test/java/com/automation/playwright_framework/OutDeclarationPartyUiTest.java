package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.automation.OutDeclarationPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OutDeclarationPartyUiTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void ignoresRejectedDirectUenTypingForOutwardCarrierRow() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display: block;">
                        <input id="outward-name" type="text" value="AB SHIPPING PTE LTD" style="width: 320px; height: 28px;">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                  <script>
                    (() => {
                      const outwardUen = document.getElementById('outward-uen');
                      let cleared = false;
                      outwardUen.addEventListener('input', () => {
                        if (cleared) {
                          return;
                        }
                        cleared = true;
                        outwardUen.value = '';
                        outwardUen.dispatchEvent(new Event('input', { bubbles: true }));
                        outwardUen.dispatchEvent(new Event('change', { bubbles: true }));
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod(
                "fillLookupPartyRow",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(
                declarationPage,
                "Outward Carrier",
                "AB SHIPPING PTE LTD",
                "199201306R"));

        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-name").inputValue());
        assertNotEquals("199201306R", page.locator("#outward-uen").inputValue());
    }

    @Test
    void fillPartyInfoUsesScopedOutwardCarrierLookupWithoutTouchingProfileMenu() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="user-profile-menu" class="dropdown-menu" style="display:block; width:220px; border:1px solid #999; background:#fff; margin-bottom:24px;">
                    <div id="profile-item" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                      Profile
                    </div>
                  </div>

                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display: block;">
                        <input id="outward-name" type="text" style="width: 320px; height: 28px;">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                    <div id="outward-options" class="dropdown-menu" style="display:block; width:320px; border:1px solid #999; background:#fff; margin-top:8px;">
                      <div id="outward-option" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                        AB SHIPPING PTE LTD
                      </div>
                    </div>
                  </section>

                  <script>
                    (() => {
                      const outwardName = document.getElementById('outward-name');
                      const outwardUen = document.getElementById('outward-uen');
                      document.getElementById('profile-item').addEventListener('click', () => {
                        window.profileClicks = (window.profileClicks || 0) + 1;
                      });
                      document.getElementById('outward-option').addEventListener('click', () => {
                        window.lookupClicks = (window.lookupClicks || 0) + 1;
                        outwardName.value = 'AB SHIPPING PTE LTD';
                        outwardUen.value = '199201306R';
                        outwardName.dispatchEvent(new Event('input', { bubbles: true }));
                        outwardName.dispatchEvent(new Event('change', { bubbles: true }));
                        outwardUen.dispatchEvent(new Event('input', { bubbles: true }));
                        outwardUen.dispatchEvent(new Event('change', { bubbles: true }));
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillPartyInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "party": {
                            "outwardCarrierAgentParty": {
                              "partyDetail": {
                                "partyName": { "name": "AB SHIPPING PTE LTD" },
                                "partyIdentification": { "id": "199201306R" }
                              }
                            }
                          }
                        }
                        """));

        assertEquals(0, ((Number) page.evaluate("window.profileClicks || 0")).intValue());
        assertDoesNotThrow(() -> {
            int lookupClicks = ((Number) page.evaluate("window.lookupClicks || 0")).intValue();
            if (lookupClicks < 1) {
                throw new AssertionError("Expected at least one lookup selection click for the Outward Carrier row.");
            }
        });
        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-name").inputValue());
        assertEquals("199201306R", page.locator("#outward-uen").inputValue());
    }

    @Test
    void fillPartyInfoDoesNotRepeatResolvedOutwardCarrierLookupSelection() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display: block;">
                        <input id="outward-name" type="text" style="width: 320px; height: 28px;">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                    <div id="outward-options" class="dropdown-menu" style="display:block; width:320px; border:1px solid #999; background:#fff; margin-top:8px;">
                      <div id="outward-option" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                        AB SHIPPING PTE LTD
                      </div>
                    </div>
                  </section>

                  <script>
                    (() => {
                      const outwardName = document.getElementById('outward-name');
                      const outwardUen = document.getElementById('outward-uen');
                      const outwardOptions = document.getElementById('outward-options');
                      document.getElementById('outward-option').addEventListener('click', () => {
                        window.lookupClicks = (window.lookupClicks || 0) + 1;
                        outwardName.value = 'AB SHIPPING PTE LTD';
                        outwardUen.value = '199201306R';
                        outwardName.dispatchEvent(new Event('input', { bubbles: true }));
                        outwardName.dispatchEvent(new Event('change', { bubbles: true }));
                        outwardUen.dispatchEvent(new Event('input', { bubbles: true }));
                        outwardUen.dispatchEvent(new Event('change', { bubbles: true }));
                        outwardOptions.style.display = 'none';
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillPartyInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "party": {
                            "outwardCarrierAgentParty": {
                              "partyDetail": {
                                "partyName": { "name": "AB SHIPPING PTE LTD" },
                                "partyIdentification": { "id": "199201306R" }
                              }
                            }
                          }
                        }
                        """));

        assertEquals(1, ((Number) page.evaluate("window.lookupClicks || 0")).intValue());
        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-name").inputValue());
        assertEquals("199201306R", page.locator("#outward-uen").inputValue());
    }

    @Test
    void clickFirstVisibleSuggestionUsesFieldAnchorWhenInputFocusIsGone() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="user-profile-menu" class="dropdown-menu" style="display:block; width:220px; border:1px solid #999; background:#fff; margin-bottom:24px;">
                    <div id="profile-item" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                      Profile
                    </div>
                  </div>

                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display: block;">
                        <input id="outward-name" type="text" style="width: 320px; height: 28px;">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                    <div id="outward-options" class="dropdown-menu" style="display:block; width:320px; border:1px solid #999; background:#fff; margin-top:8px;">
                      <div id="outward-option" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                        AB SHIPPING PTE LTD
                      </div>
                    </div>
                  </section>

                  <script>
                    (() => {
                      const outwardName = document.getElementById('outward-name');
                      const outwardUen = document.getElementById('outward-uen');
                      document.getElementById('profile-item').addEventListener('click', () => {
                        window.profileClicks = (window.profileClicks || 0) + 1;
                      });
                      document.getElementById('outward-option').addEventListener('click', () => {
                        window.lookupClicks = (window.lookupClicks || 0) + 1;
                        outwardName.value = 'AB SHIPPING PTE LTD';
                        outwardUen.value = '199201306R';
                        outwardName.dispatchEvent(new Event('input', { bubbles: true }));
                        outwardName.dispatchEvent(new Event('change', { bubbles: true }));
                        outwardUen.dispatchEvent(new Event('input', { bubbles: true }));
                        outwardUen.dispatchEvent(new Event('change', { bubbles: true }));
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod("clickFirstVisibleSuggestion", com.microsoft.playwright.Locator.class);
        method.setAccessible(true);

        page.locator("body").click();
        Object clicked = method.invoke(declarationPage, page.locator("#outward-name"));

        assertEquals(Boolean.TRUE, clicked);
        assertEquals(0, ((Number) page.evaluate("window.profileClicks || 0")).intValue());
        assertDoesNotThrow(() -> {
            int lookupClicks = ((Number) page.evaluate("window.lookupClicks || 0")).intValue();
            if (lookupClicks < 1) {
                throw new AssertionError("Expected at least one Exporter lookup selection click.");
            }
        });
        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-name").inputValue());
        assertEquals("199201306R", page.locator("#outward-uen").inputValue());
    }

    void fillPartyInfoUsesExporterRowWhenExporterCardIsAbsent() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Exporter</div>
                      <app-exporter-lookup id="exporter-host" formcontrolname="name" style="display:block;">
                        <input id="exporter-name" type="text" style="width:320px; height:28px;">
                      </app-exporter-lookup>
                      <div>🔍</div>
                      <div>
                        <input id="exporter-uen" type="text" style="width:220px; height:28px;">
                      </div>
                    </div>
                    <div id="exporter-options" class="dropdown-menu" style="display:block; width:320px; border:1px solid #999; background:#fff; margin-top:8px;">
                      <div id="exporter-option" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                        FORESPAND FOOD ENTER PRISE PTE LTD
                      </div>
                    </div>
                    <input id="exporter-model" type="hidden">
                  </section>

                  <script>
                    (() => {
                      const host = document.getElementById('exporter-host');
                      const nameField = document.getElementById('exporter-name');
                      const uenField = document.getElementById('exporter-uen');
                      const modelField = document.getElementById('exporter-model');
                      document.getElementById('exporter-option').addEventListener('click', () => {
                        window.lookupClicks = (window.lookupClicks || 0) + 1;
                        nameField.value = 'FORESPAND FOOD ENTER PRISE PTE LTD';
                        nameField.dispatchEvent(new Event('input', { bubbles: true }));
                        nameField.dispatchEvent(new Event('change', { bubbles: true }));
                        modelField.value = '198700002E';
                        uenField.value = '198700002E';
                        uenField.dispatchEvent(new Event('input', { bubbles: true }));
                        uenField.dispatchEvent(new Event('change', { bubbles: true }));
                      });
                      const component = {
                        allOptions: [
                          { code: '198700002E', description: 'FORESPAND FOOD ENTER PRISE PTE LTD' }
                        ],
                        inputElement: { nativeElement: nameField },
                        onChange(code) {
                          modelField.value = code;
                          uenField.value = code;
                          uenField.dispatchEvent(new Event('input', { bubbles: true }));
                          uenField.dispatchEvent(new Event('change', { bubbles: true }));
                        },
                        onTouched() {}
                      };
                      window.ng = {
                        getComponent(target) {
                          return target === host ? component : null;
                        }
                      };
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillPartyInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "party": {
                            "exporterParty": {
                              "partyDetail": {
                                "partyIdentification": { "id": "198700002E" },
                                "partyName": { "name": "FORESPAND FOOD ENTER PRISE PTE LTD" }
                              },
                              "address": {
                                "addressLine": {
                                  "line": [ "FD", "HGE" ]
                                },
                                "cityName": "SGD",
                                "countryCode": "SG"
                              }
                            }
                          }
                        }
                        """));

        assertEquals("FORESPAND FOOD ENTER PRISE PTE LTD", page.locator("#exporter-name").inputValue());
        assertEquals("198700002E", page.locator("#exporter-uen").inputValue());
        assertEquals("198700002E", page.locator("#exporter-model").inputValue());
    }

    @Test
    void fillPartyInfoFallsBackToDirectConsigneeCardTextEntryWhenLookupDoesNotResolve() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div id="consignee-card" style="border:1px solid #ddd; padding:16px; margin-top:16px;">
                      <div style="display:grid; grid-template-columns:160px 360px 220px; gap:12px; align-items:center;">
                        <div>Consignee</div>
                        <div>
                          <input id="consignee-name" type="text" style="width:320px; height:28px;">
                        </div>
                        <div>
                          <div>UEN</div>
                          <input id="consignee-uen" type="text" style="width:220px; height:28px;">
                        </div>
                      </div>
                      <div style="margin-top:12px;">
                        <div>Address</div>
                        <input id="consignee-address" type="text" style="width:520px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Country Code</div>
                        <input id="consignee-country" type="text" style="width:140px; height:28px;">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillPartyInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "party": {
                            "consigneeParty": {
                              "partyName": {
                                "name": "NAME1"
                              },
                              "address": {
                                "addressLine": {
                                  "line": [ "ADD2 ADD3 SAMPLECITY" ]
                                },
                                "countryCode": "SG"
                              }
                            }
                          }
                        }
                        """));

        assertEquals("NAME1", page.locator("#consignee-name").inputValue());
        assertEquals("ADD2 ADD3 SAMPLECITY", page.locator("#consignee-address").inputValue());
        assertEquals("SG", page.locator("#consignee-country").inputValue());
    }

    @Test
    void fillPartyInfoCommitsConsigneeLookupComponentModelInsideCard() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div id="consignee-card" style="border:1px solid #ddd; padding:16px; margin-top:16px;">
                      <div style="display:grid; grid-template-columns:160px 360px 220px; gap:12px; align-items:center;">
                        <div>Consignee</div>
                        <app-consignee-lookup id="consignee-host" formcontrolname="name" style="display:block;">
                          <input id="consignee-name" type="text" style="width:320px; height:28px;">
                        </app-consignee-lookup>
                        <div>
                          <div>UEN</div>
                          <input id="consignee-uen" type="text" style="width:220px; height:28px;">
                        </div>
                      </div>
                      <div style="margin-top:12px;">
                        <div>Address</div>
                        <input id="consignee-address" type="text" style="width:520px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Country Code</div>
                        <input id="consignee-country" type="text" style="width:140px; height:28px;">
                      </div>
                      <input id="consignee-model" type="hidden">
                    </div>
                  </section>
                  <script>
                    (() => {
                      const host = document.getElementById('consignee-host');
                      const nameField = document.getElementById('consignee-name');
                      const modelField = document.getElementById('consignee-model');
                      const component = {
                        allOptions: [
                          { code: '', description: 'NAME1' }
                        ],
                        inputElement: { nativeElement: nameField },
                        onChange(value) {
                          modelField.value = value;
                          nameField.value = 'NAME1';
                          nameField.dispatchEvent(new Event('input', { bubbles: true }));
                          nameField.dispatchEvent(new Event('change', { bubbles: true }));
                        },
                        onTouched() {}
                      };
                      window.ng = {
                        getComponent(target) {
                          return target === host ? component : null;
                        }
                      };
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillPartyInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "party": {
                            "consigneeParty": {
                              "partyName": {
                                "name": "NAME1"
                              },
                              "address": {
                                "addressLine": {
                                  "line": [ "ADD2 ADD3 SAMPLECITY" ]
                                },
                                "countryCode": "SG"
                              }
                            }
                          }
                        }
                        """));

        assertEquals("NAME1", page.locator("#consignee-name").inputValue());
        assertEquals("NAME1", page.locator("#consignee-model").inputValue());
        assertEquals("ADD2 ADD3 SAMPLECITY", page.locator("#consignee-address").inputValue());
        assertEquals("SG", page.locator("#consignee-country").inputValue());
    }

    @Test
    void fillPartyInfoClicksConsigneeSuggestionWhenLookupHostExistsWithoutAngularComponent() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div id="consignee-card" style="border:1px solid #ddd; padding:16px; margin-top:16px;">
                      <div style="display:grid; grid-template-columns:160px 360px 220px; gap:12px; align-items:center;">
                        <div>Consignee</div>
                        <app-consignee-lookup id="consignee-host" formcontrolname="name" style="display:block;">
                          <input id="consignee-name" type="text" style="width:320px; height:28px;">
                        </app-consignee-lookup>
                        <div>
                          <div>UEN</div>
                          <input id="consignee-uen" type="text" style="width:220px; height:28px;">
                        </div>
                      </div>
                      <div style="margin-top:12px;">
                        <div>Address</div>
                        <input id="consignee-address" type="text" style="width:520px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Country Code</div>
                        <input id="consignee-country" type="text" style="width:140px; height:28px;">
                      </div>
                      <input id="consignee-model" type="hidden">
                    </div>

                    <div id="consignee-options" class="dropdown-menu" style="display:block; width:320px; border:1px solid #999; background:#fff; margin-top:8px;">
                      <div id="consignee-option" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                        NAME1
                      </div>
                    </div>
                  </section>
                  <script>
                    (() => {
                      const nameField = document.getElementById('consignee-name');
                      const uenField = document.getElementById('consignee-uen');
                      const addressField = document.getElementById('consignee-address');
                      const countryField = document.getElementById('consignee-country');
                      const modelField = document.getElementById('consignee-model');
                      document.getElementById('consignee-option').addEventListener('click', () => {
                        window.lookupClicks = (window.lookupClicks || 0) + 1;
                        nameField.value = 'NAME1';
                        uenField.value = 'NAME1';
                        modelField.value = 'NAME1';
                        addressField.value = 'ADD2 ADD3 SAMPLECITY';
                        countryField.value = 'SG';
                        nameField.dispatchEvent(new Event('input', { bubbles: true }));
                        nameField.dispatchEvent(new Event('change', { bubbles: true }));
                        uenField.dispatchEvent(new Event('input', { bubbles: true }));
                        uenField.dispatchEvent(new Event('change', { bubbles: true }));
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillPartyInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "party": {
                            "consigneeParty": {
                              "partyName": {
                                "name": "NAME1"
                              },
                              "address": {
                                "addressLine": {
                                  "line": [ "ADD2 ADD3 SAMPLECITY" ]
                                },
                                "countryCode": "SG"
                              }
                            }
                          }
                        }
                        """));

        assertEquals(1, ((Number) page.evaluate("window.lookupClicks || 0")).intValue());
        assertEquals("NAME1", page.locator("#consignee-name").inputValue());
        assertEquals("NAME1", page.locator("#consignee-model").inputValue());
        assertEquals("SG", page.locator("#consignee-country").inputValue());
    }

    @Test
    void fillPartyInfoUsesCardLookupHostsWithoutTouchingProfileMenu() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="user-profile-menu" class="dropdown-menu" style="display:block; width:220px; border:1px solid #999; background:#fff; margin-bottom:24px;">
                    <div id="profile-item" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                      Profile
                    </div>
                  </div>

                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>

                    <div id="consignee-card" style="border:1px solid #ddd; padding:16px; margin-top:16px;">
                      <div style="display:grid; grid-template-columns:160px 360px 220px; gap:12px; align-items:center;">
                        <div>Consignee</div>
                        <app-consignee-lookup id="consignee-host" formcontrolname="name" style="display:block;">
                          <input id="consignee-name" type="text" style="width:320px; height:28px;">
                        </app-consignee-lookup>
                        <input id="consignee-uen" type="text" style="width:220px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Address</div>
                        <input id="consignee-address" type="text" style="width:520px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Country Code</div>
                        <input id="consignee-country" type="text" style="width:140px; height:28px;">
                      </div>
                      <input id="consignee-model" type="hidden">
                    </div>

                    <div id="end-user-card" style="border:1px solid #ddd; padding:16px; margin-top:16px;">
                      <div style="display:grid; grid-template-columns:160px 360px 220px; gap:12px; align-items:center;">
                        <div>End User</div>
                        <app-end-user-lookup id="end-user-host" formcontrolname="name" style="display:block;">
                          <input id="end-user-name" type="text" style="width:320px; height:28px;">
                        </app-end-user-lookup>
                        <input id="end-user-uen" type="text" style="width:220px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Address</div>
                        <input id="end-user-address" type="text" style="width:520px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Country Code</div>
                        <input id="end-user-country" type="text" style="width:140px; height:28px;">
                      </div>
                      <input id="end-user-model" type="hidden">
                    </div>

                    <div id="manufacturer-card" style="border:1px solid #ddd; padding:16px; margin-top:16px;">
                      <div style="display:grid; grid-template-columns:160px 360px 220px; gap:12px; align-items:center;">
                        <div>Manufacturer</div>
                        <app-manufacturer-lookup id="manufacturer-host" formcontrolname="name" style="display:block;">
                          <input id="manufacturer-name" type="text" style="width:320px; height:28px;">
                        </app-manufacturer-lookup>
                        <input id="manufacturer-uen" type="text" style="width:220px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Address</div>
                        <input id="manufacturer-address" type="text" style="width:520px; height:28px;">
                      </div>
                      <div style="margin-top:12px;">
                        <div>Country Code</div>
                        <input id="manufacturer-country" type="text" style="width:140px; height:28px;">
                      </div>
                      <input id="manufacturer-model" type="hidden">
                    </div>
                  </section>

                  <script>
                    (() => {
                      document.getElementById('profile-item').addEventListener('click', () => {
                        window.profileClicks = (window.profileClicks || 0) + 1;
                      });

                      const bindComponent = (hostId, inputId, modelId, description) => {
                        const host = document.getElementById(hostId);
                        const input = document.getElementById(inputId);
                        const model = document.getElementById(modelId);
                        const component = {
                          allOptions: [
                            { code: '', description }
                          ],
                          inputElement: { nativeElement: input },
                          onChange(value) {
                            model.value = value;
                            input.value = description;
                            input.dispatchEvent(new Event('input', { bubbles: true }));
                            input.dispatchEvent(new Event('change', { bubbles: true }));
                          },
                          onTouched() {}
                        };
                        return { host, component };
                      };

                      const consignee = bindComponent('consignee-host', 'consignee-name', 'consignee-model', 'NAME1');
                      const endUser = bindComponent('end-user-host', 'end-user-name', 'end-user-model', 'ENDNAME1');
                      const manufacturer = bindComponent('manufacturer-host', 'manufacturer-name', 'manufacturer-model', 'ARMSTRONG INDUSTRIAL CORP');

                      window.ng = {
                        getComponent(target) {
                          if (target === consignee.host) {
                            return consignee.component;
                          }
                          if (target === endUser.host) {
                            return endUser.component;
                          }
                          if (target === manufacturer.host) {
                            return manufacturer.component;
                          }
                          return null;
                        }
                      };
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillPartyInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "party": {
                            "consigneeParty": {
                              "partyName": {
                                "name": "NAME1"
                              },
                              "address": {
                                "addressLine": {
                                  "line": [ "ADD2 ADD3 SAMPLECITY" ]
                                },
                                "countryCode": "SG"
                              }
                            },
                            "endUserParty": {
                              "partyName": {
                                "name": "ENDNAME1"
                              },
                              "address": {
                                "addressLine": {
                                  "line": [ "END USER ADDRESS" ]
                                },
                                "countryCode": "IN"
                              }
                            },
                            "manufacturerParty": {
                              "partyDetail": {
                                "partyName": {
                                  "name": "ARMSTRONG INDUSTRIAL CORP"
                                }
                              },
                              "address": {
                                "addressLine": {
                                  "line": [ "TOLLGATE LALGUDI" ]
                                },
                                "countryCode": "IN"
                              }
                            }
                          }
                        }
                        """));

        assertEquals(0, ((Number) page.evaluate("window.profileClicks || 0")).intValue());
        assertEquals("NAME1", page.locator("#consignee-name").inputValue());
        assertEquals("NAME1", page.locator("#consignee-model").inputValue());
        assertEquals("ENDNAME1", page.locator("#end-user-name").inputValue());
        assertEquals("ENDNAME1", page.locator("#end-user-model").inputValue());
        assertEquals("ARMSTRONG INDUSTRIAL CORP", page.locator("#manufacturer-name").inputValue());
        assertEquals("ARMSTRONG INDUSTRIAL CORP", page.locator("#manufacturer-model").inputValue());
    }
}
