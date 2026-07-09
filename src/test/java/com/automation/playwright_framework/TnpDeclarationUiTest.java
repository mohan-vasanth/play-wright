package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.TnpDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TnpDeclarationUiTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void fillsTnpTransportFieldsIncludingContainerDetails() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section style="padding: 12px; border: 1px solid #ccc; width: 1400px;">
                    <h2>Transport Info (T)</h2>
                    <div id="cargo-details">
                      <div>Cargo Details</div>
                      <div><label>Total Package</label><input id="total-package" type="text"></div>
                      <div><label>Total Package Unit</label><input id="total-package-unit" type="text"></div>
                      <div><label>Gross Weight</label><input id="gross-weight" type="text"></div>
                      <div><label>Gross Weight Unit</label><input id="gross-weight-unit" type="text"></div>
                    </div>
                    <div id="inward-transport">
                      <div>Inward Transport Means</div>
                      <div><label>Arrival Date</label><input id="arrival-date" type="text"></div>
                      <div><label>Inward Voyage Number</label><input id="inward-conveyance" type="text"></div>
                      <div><label>Loading Port</label><input id="loading-port" type="text"></div>
                      <div><label>Inward Vessel Name</label><input id="inward-identifier" type="text"></div>
                      <div><label>Inward Ocean Bill of Lading Number</label><input id="inward-mawb" type="text"></div>
                    </div>
                    <div id="outward-transport">
                      <div>Outward Transport Means</div>
                      <div><label>Departure Date</label><input id="departure-date" type="text"></div>
                      <div><label>Outward Voyage Number</label><input id="outward-conveyance" type="text"></div>
                      <div><label>Discharge Port</label><input id="discharge-port" type="text"></div>
                      <div><label>Outward Vessel Name</label><input id="outward-identifier" type="text"></div>
                      <div><label>Outward Ocean Bill of Lading Number</label><input id="outward-mawb" type="text"></div>
                      <div><label>Country of Final Destination</label><input id="final-country" type="text"></div>
                    </div>
                    <div id="additional-vessel">
                      <div>Additional Vessel Information</div>
                      <div><label>Vessel Type</label><input id="vessel-type" type="text"></div>
                      <div><label>Towing Vessel Voyage Number</label><input id="towing-vessel-id" type="text"></div>
                      <div><label>Towing Vessel Name</label><input id="towing-vessel-name" type="text"></div>
                    </div>
                    <div id="container-details">
                      <div>Container Details (0)</div>
                      <div>Container Number</div>
                      <div>Size / Type</div>
                      <div>Weight (TNE)</div>
                      <div>Seal Number</div>
                      <input id="container-number" type="text">
                      <input id="container-size" type="text">
                      <input id="container-weight" type="text">
                      <input id="container-seal" type="text">
                      <button type="button">ADD</button>
                    </div>
                  </section>
                </body>
                </html>
                """);

        TnpDeclarationPage declarationPage = new TnpDeclarationPage(page);
        Method method = TnpDeclarationPage.class.getDeclaredMethod("fillTransportInfo", JsonNode.class);
        method.setAccessible(true);
        method.invoke(declarationPage, readResource("TNP/TW PERMIT.json"));

        assertEquals("100", page.locator("#total-package").inputValue());
        assertEquals("CTN", page.locator("#total-package-unit").inputValue());
        assertEquals("20.000", page.locator("#gross-weight").inputValue());
        assertEquals("TNE", page.locator("#gross-weight-unit").inputValue());
        assertEquals("25-06-2026", page.locator("#arrival-date").inputValue());
        assertEquals("QWERT123", page.locator("#inward-conveyance").inputValue());
        assertEquals("TWTPE", page.locator("#loading-port").inputValue());
        assertEquals("QAWSED321", page.locator("#inward-identifier").inputValue());
        assertEquals("WERTY123", page.locator("#inward-mawb").inputValue());
        assertEquals("25-06-2026", page.locator("#departure-date").inputValue());
        assertEquals("TREWQ123", page.locator("#outward-conveyance").inputValue());
        assertEquals("IDJKT", page.locator("#discharge-port").inputValue());
        assertEquals("EDWSQA213", page.locator("#outward-identifier").inputValue());
        assertEquals("YTRWE321", page.locator("#outward-mawb").inputValue());
        assertEquals("ID", page.locator("#final-country").inputValue());
        assertEquals("CV", page.locator("#vessel-type").inputValue());
        assertEquals("TEST123", page.locator("#towing-vessel-id").inputValue());
        assertEquals("SUN PHARMA", page.locator("#towing-vessel-name").inputValue());
        assertEquals("REWSAE12", page.locator("#container-number").inputValue());
        assertEquals("FCL20", page.locator("#container-size").inputValue());
        assertEquals("12", page.locator("#container-weight").inputValue());
        assertEquals("WSEDRF", page.locator("#container-seal").inputValue());
    }

    @Test
    void syncsTnpCargoTypeIntoUnderlyingLookupComponent() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section style="padding: 12px; border: 1px solid #ccc; width: 900px;">
                    <h2>Shipment Info (S)</h2>
                    <div>Declaration Info</div>
                    <label>Cargo Type</label>
                    <app-cargo-type-lookup id="cargo-type-component" formcontrolname="cargoPackingType" style="display:block;">
                      <input id="cargo-type-input" type="text">
                    </app-cargo-type-lookup>
                  </section>
                  <script>
                    window.__cargoTypeComponent = {
                      allOptions: [
                        { code: '5', description: 'Other non-containerized' }
                      ],
                      _value: '',
                      inputDisplayValue: '',
                      displayValue: '',
                      selectOptionItem(option) { this.lastSelectedCode = option.code; },
                      onChange(value) { this.lastChangedValue = value; },
                      onTouched() { this.touched = true; }
                    };
                    window.ng = {
                      getComponent(element) {
                        if (element && element.id === 'cargo-type-component') {
                          return window.__cargoTypeComponent;
                        }
                        return null;
                      }
                    };
                  </script>
                </body>
                </html>
                """);

        TnpDeclarationPage declarationPage = new TnpDeclarationPage(page);
        Method method = TnpDeclarationPage.class.getDeclaredMethod("fillDeclarationSpecificShipmentInfo", JsonNode.class);
        method.setAccessible(true);
        method.invoke(declarationPage, readResource("TNP/TT PERMIT.json"));

        assertEquals("5", page.evaluate("window.__cargoTypeComponent._value"));
        assertEquals("5", page.evaluate("window.__cargoTypeComponent.lastChangedValue"));
        assertEquals("5 - Other non-containerized", page.locator("#cargo-type-input").inputValue());
    }

    @Test
    void selectsVisibleCargoTypeOptionWhenRawCodeAloneWouldNotPersist() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section style="padding: 12px; border: 1px solid #ccc; width: 900px;">
                    <h2>Shipment Info (S)</h2>
                    <div>Declaration Info</div>
                    <label>Cargo Type</label>
                    <div id="cargo-type-shell" style="display:block; width:260px;">
                      <input id="cargo-type-input" type="text">
                    </div>
                    <div id="cargo-type-options" class="dropdown-menu" style="display:none; width:260px; border:1px solid #999; background:#fff;">
                      <div id="cargo-type-option" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                        5 - Other non-containerized
                      </div>
                    </div>
                  </section>
                  <script>
                    (() => {
                      const field = document.getElementById('cargo-type-input');
                      const menu = document.getElementById('cargo-type-options');
                      const option = document.getElementById('cargo-type-option');
                      const normalize = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                      field.addEventListener('input', () => {
                        if (normalize(field.value) === '5') {
                          field.value = '5';
                        }
                      });
                      field.addEventListener('click', () => {
                        menu.style.display = 'block';
                      });
                      option.addEventListener('click', () => {
                        field.value = option.textContent.trim();
                        field.dispatchEvent(new Event('input', { bubbles: true }));
                        field.dispatchEvent(new Event('change', { bubbles: true }));
                        menu.style.display = 'none';
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        TnpDeclarationPage declarationPage = new TnpDeclarationPage(page);
        Method method = TnpDeclarationPage.class.getDeclaredMethod("fillDeclarationSpecificShipmentInfo", JsonNode.class);
        method.setAccessible(true);
        method.invoke(declarationPage, readResource("TNP/TT PERMIT.json"));

        assertEquals("5 - Other non-containerized", page.locator("#cargo-type-input").inputValue());
    }

    @Test
    void fillsTnpPartyRowsAndConsigneeCard() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section style="padding: 12px; border: 1px solid #ccc; width: 1400px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Importer</div>
                      <app-importer-lookup formcontrolname="name" style="display:block;">
                        <input id="importer-name" type="text">
                      </app-importer-lookup>
                      <div>🔍</div>
                      <input id="importer-uen" type="text">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Inward Carrier</div>
                      <app-inward-carrier-lookup formcontrolname="name" style="display:block;">
                        <input id="inward-carrier-name" type="text">
                      </app-inward-carrier-lookup>
                      <div>🔍</div>
                      <input id="inward-carrier-uen" type="text">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display:block;">
                        <input id="outward-carrier-name" type="text">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-carrier-uen" type="text">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Freight Forwarder</div>
                      <app-freight-forwarder-lookup formcontrolname="name" style="display:block;">
                        <input id="freight-forwarder-name" type="text">
                      </app-freight-forwarder-lookup>
                      <div>🔍</div>
                      <input id="freight-forwarder-uen" type="text">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Declaring Agent</div>
                      <app-declaring-agent-lookup formcontrolname="name" style="display:block;">
                        <input id="declaring-agent-name" type="text">
                      </app-declaring-agent-lookup>
                      <div>🔍</div>
                      <input id="declaring-agent-uen" type="text">
                    </div>
                    <div id="consignee-card" style="display: grid; grid-template-columns: 160px 360px 220px; gap: 12px; align-items: center;">
                      <div>Consignee</div>
                      <input id="consignee-name" type="text">
                      <input id="consignee-uen" type="text">
                      <div></div>
                      <div>
                        <label>Address</label>
                        <input id="consignee-address" type="text">
                      </div>
                      <div>
                        <label>Other address details</label>
                        <input id="consignee-other-address" type="text">
                      </div>
                      <div>
                        <label>Country Code</label>
                        <input id="consignee-country" type="text">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        TnpDeclarationPage declarationPage = new TnpDeclarationPage(page);
        Method method = TnpDeclarationPage.class.getDeclaredMethod("fillPartyInfo", JsonNode.class);
        method.setAccessible(true);
        method.invoke(declarationPage, readResource("TNP/TW PERMIT.json"));

        assertEquals("BAYSWATER SHIPPING FORWARDING PTE LTD", page.locator("#importer-name").inputValue());
        assertEquals("198402847H", page.locator("#importer-uen").inputValue());
        assertEquals("AB SHIPPING PTE LTD", page.locator("#inward-carrier-name").inputValue());
        assertEquals("199201306R", page.locator("#inward-carrier-uen").inputValue());
        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-carrier-name").inputValue());
        assertEquals("199201306R", page.locator("#outward-carrier-uen").inputValue());
        assertEquals("", page.locator("#freight-forwarder-name").inputValue());
        assertEquals("", page.locator("#freight-forwarder-uen").inputValue());
        assertEquals("ADATA PVT LMT", page.locator("#declaring-agent-name").inputValue());
        assertEquals("202535359H", page.locator("#declaring-agent-uen").inputValue());
        assertEquals("NAME1", page.locator("#consignee-name").inputValue());
        assertEquals("ADD2", page.locator("#consignee-address").inputValue());
        assertEquals("SAMPLECITY, 21312, SAA", page.locator("#consignee-other-address").inputValue());
        assertEquals("SG", page.locator("#consignee-country").inputValue());
    }

    @Test
    void clearsPrefilledFreightForwarderWhenJsonDoesNotContainIt() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section style="padding: 12px; border: 1px solid #ccc; width: 1400px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Importer</div>
                      <app-importer-lookup formcontrolname="name" style="display:block;">
                        <input id="importer-name" type="text">
                      </app-importer-lookup>
                      <div>🔍</div>
                      <input id="importer-uen" type="text">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Inward Carrier</div>
                      <app-inward-carrier-lookup formcontrolname="name" style="display:block;">
                        <input id="inward-carrier-name" type="text">
                      </app-inward-carrier-lookup>
                      <div>🔍</div>
                      <input id="inward-carrier-uen" type="text">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display:block;">
                        <input id="outward-carrier-name" type="text">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-carrier-uen" type="text">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Freight Forwarder</div>
                      <app-freight-forwarder-lookup formcontrolname="name" style="display:block;">
                        <input id="freight-forwarder-name" type="text" value="STALE FREIGHT FORWARDER">
                      </app-freight-forwarder-lookup>
                      <div>🔍</div>
                      <input id="freight-forwarder-uen" type="text" value="ST1234567X">
                    </div>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Declaring Agent</div>
                      <app-declaring-agent-lookup formcontrolname="name" style="display:block;">
                        <input id="declaring-agent-name" type="text">
                      </app-declaring-agent-lookup>
                      <div>🔍</div>
                      <input id="declaring-agent-uen" type="text">
                    </div>
                  </section>
                </body>
                </html>
                """);

        JsonNode payload = OBJECT_MAPPER.readTree("""
                {
                  "header": {
                    "declarationType": "72"
                  },
                  "transport": {
                    "outwardTransport": {
                      "transportMeans": {
                        "transportMode": {
                          "modeCode": "1"
                        }
                      }
                    }
                  },
                  "party": {
                    "importerParty": {
                      "partyIdentification": {
                        "id": "198402847H"
                      },
                      "partyName": {
                        "name": "BAYSWATER SHIPPING FORWARDING PTE LTD"
                      }
                    },
                    "inwardCarrierAgentParty": {
                      "partyIdentification": {
                        "id": "199201306R"
                      },
                      "partyName": {
                        "name": "AB SHIPPING PTE LTD"
                      }
                    },
                    "outwardCarrierAgentParty": {
                      "partyIdentification": {
                        "id": "199201306R"
                      },
                      "partyName": {
                        "name": "AB SHIPPING PTE LTD"
                      }
                    },
                    "declaringAgentParty": {
                      "partyIdentification": {
                        "id": "202535359H"
                      },
                      "partyName": {
                        "name": "ADATA PVT LMT"
                      }
                    }
                  }
                }
                """);

        TnpDeclarationPage declarationPage = new TnpDeclarationPage(page);
        Method method = TnpDeclarationPage.class.getDeclaredMethod("fillPartyInfo", JsonNode.class);
        method.setAccessible(true);
        method.invoke(declarationPage, payload);

        assertEquals("", page.locator("#freight-forwarder-name").inputValue());
        assertEquals("", page.locator("#freight-forwarder-uen").inputValue());
        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-carrier-name").inputValue());
        assertEquals("199201306R", page.locator("#outward-carrier-uen").inputValue());
    }

    @Test
    void fillsAllTnpItemsAcrossAddItemFlow() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section style="padding: 12px; border: 1px solid #ccc; width: 1400px;">
                    <h2>Items (I)</h2>
                    <button id="add-item" type="button"
                        onclick="document.getElementById('item-1').style.display='none'; document.getElementById('item-2').style.display='block'; document.getElementById('item-2-item-title').textContent='Item Details'; document.getElementById('item-2-qty-title').textContent='Item Quantity & Value';">
                      ADD ITEM
                    </button>
                    <div id="item-1">
                      <div>Item Details</div>
                      <div><label>S No.</label><input id="item1-seq" type="text"></div>
                      <div><label>InHAWB/HUCR/HBL Number</label><input id="item1-in" type="text"></div>
                      <div><label>OutHAWB/HUCR/HBL Number</label><input id="item1-out" type="text"></div>
                      <div><label>InMAWB/OUCRO/BL Number</label><input id="item1-in-mawb" type="text"></div>
                      <div><label>OutMAWB/OUCRO/BL Number</label><input id="item1-out-mawb" type="text"></div>
                      <div><label>HS Code</label><input id="item1-hs" type="text"></div>
                      <div><label>Description</label><input id="item1-description" type="text"></div>
                      <div><label>COO</label><input id="item1-coo" type="text"></div>
                      <div>Item Quantity & Value</div>
                      <div><label>HS Quantity</label><input id="item1-qty" type="text"><input id="item1-uom" type="text"></div>
                    </div>
                    <div id="item-2" style="display:none;">
                      <div id="item-2-item-title"></div>
                      <div><label>S No.</label><input id="item2-seq" type="text"></div>
                      <div><label>InHAWB/HUCR/HBL Number</label><input id="item2-in" type="text"></div>
                      <div><label>OutHAWB/HUCR/HBL Number</label><input id="item2-out" type="text"></div>
                      <div><label>InMAWB/OUCRO/BL Number</label><input id="item2-in-mawb" type="text"></div>
                      <div><label>OutMAWB/OUCRO/BL Number</label><input id="item2-out-mawb" type="text"></div>
                      <div><label>HS Code</label><input id="item2-hs" type="text"></div>
                      <div><label>Description</label><input id="item2-description" type="text"></div>
                      <div><label>COO</label><input id="item2-coo" type="text"></div>
                      <div id="item-2-qty-title"></div>
                      <div><label>HS Quantity</label><input id="item2-qty" type="text"><input id="item2-uom" type="text"></div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        TnpDeclarationPage declarationPage = new TnpDeclarationPage(page);
        Method method = TnpDeclarationPage.class.getDeclaredMethod("fillItemInfo", JsonNode.class);
        method.setAccessible(true);
        method.invoke(declarationPage, readResource("TNP/TT PERMIT.json"));

        assertEquals("", page.locator("#item1-in").inputValue());
        assertEquals("", page.locator("#item1-out").inputValue());
        assertEquals("ASDGFDG", page.locator("#item1-in-mawb").inputValue());
        assertEquals("GDFGERFER", page.locator("#item1-out-mawb").inputValue());
        assertEquals("85041000", page.locator("#item1-hs").inputValue());
        assertEquals("BALLASTS FOR DISCHARGE LAMPS OR TUBES (NMB)", page.locator("#item1-description").inputValue());
        assertEquals("CN", page.locator("#item1-coo").inputValue());
        assertEquals("85.000", page.locator("#item1-qty").inputValue());
        assertEquals("NMB", page.locator("#item1-uom").inputValue());
        assertEquals("", page.locator("#item2-in").inputValue());
        assertEquals("", page.locator("#item2-out").inputValue());
        assertEquals("EDEEEEE", page.locator("#item2-in-mawb").inputValue());
        assertEquals("REDFERERF", page.locator("#item2-out-mawb").inputValue());
        assertEquals("85042111", page.locator("#item2-hs").inputValue());
        assertEquals(
                "INSTRUMENT TRANSFORMERS WITH POWER HANDLING CAPACITY NOT OVER 1KVA & OF HIGH SIDE VOLTAGE OF 110KV OR MORE (NMB)",
                page.locator("#item2-description").inputValue());
        assertEquals("CN", page.locator("#item2-coo").inputValue());
        assertEquals("58.000", page.locator("#item2-qty").inputValue());
        assertEquals("NMB", page.locator("#item2-uom").inputValue());
    }

    private JsonNode readResource(String resourcePath) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Missing resource: " + resourcePath);
            return OBJECT_MAPPER.readTree(inputStream);
        }
    }
}
