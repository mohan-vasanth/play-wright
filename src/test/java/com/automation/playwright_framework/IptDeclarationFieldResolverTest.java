package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IptDeclarationFieldResolverTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void fillsFieldFromActualSectionContentInsteadOfTabHeaderContainer() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="tab-strip" style="display: flex; gap: 12px; margin-bottom: 16px;">
                    <button type="button">Shipment Info (S)</button>
                    <button type="button">Party Info (P)</button>
                  </div>

                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 420px;">
                    <h2>Party Info (P)</h2>
                    <label for="exporter-name">Exporter Name</label>
                    <input id="exporter-name" type="text" style="display: block; width: 240px; height: 28px;">
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillFieldInSectionIfPresent",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        method.invoke(declarationPage, "Party Info (P)", "Exporter Name", "ADATA EXPORT");

        assertEquals("ADATA EXPORT", page.locator("#exporter-name").inputValue());
    }

    @Test
    void openSectionIgnoresHeaderTextAndClicksActualSectionTab() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="header-shortcut" onclick="window.headerClicks = (window.headerClicks || 0) + 1;">
                    Party Info (P)
                  </div>
                  <div id="tab-strip" style="display: flex; gap: 12px; margin-bottom: 16px;">
                    <button id="shipment-tab" type="button"
                            onclick="window.shipmentTabClicks = (window.shipmentTabClicks || 0) + 1;">
                      Shipment Info (S)
                    </button>
                    <button id="party-tab" type="button"
                            onclick="window.partyTabClicks = (window.partyTabClicks || 0) + 1;">
                      Party Info (P)
                    </button>
                    <button id="summary-tab" type="button"
                            onclick="window.summaryTabClicks = (window.summaryTabClicks || 0) + 1;">
                      Summary (Y)
                    </button>
                  </div>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 420px;">
                    <h2>Party Info (P)</h2>
                    <label for="exporter-name">Exporter Name</label>
                    <input id="exporter-name" type="text" style="display: block; width: 240px; height: 28px;">
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod("openSection", String.class);
        method.setAccessible(true);

        method.invoke(declarationPage, "Party Info (P)");

        assertEquals(0, ((Number) page.evaluate("window.headerClicks || 0")).intValue());
        assertEquals(1, ((Number) page.evaluate("window.partyTabClicks || 0")).intValue());
        assertEquals(0, ((Number) page.evaluate("window.shipmentTabClicks || 0")).intValue());
    }

    @Test
    void fillsConcreteInputWhenScopedRowResolvesWrapperFirst() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="party-card" style="width: 520px; padding: 12px; border: 1px solid #ccc;">
                    <div style="display: grid; grid-template-columns: 140px 1fr; align-items: center; margin-bottom: 12px;">
                      <div>UEN</div>
                      <div role="textbox" style="display: block; width: 260px; height: 32px; border: 1px solid #999; padding: 2px;">
                        <input id="uen-input" type="text" style="width: 240px; height: 24px;">
                      </div>
                    </div>
                    <div style="display: grid; grid-template-columns: 140px 1fr; align-items: center;">
                      <div>Party Name</div>
                      <div role="textbox" style="display: block; width: 260px; height: 32px; border: 1px solid #999; padding: 2px;">
                        <input id="party-name-input" type="text" style="width: 240px; height: 24px;">
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillFieldAfterScopeLabelIfPresent",
                Locator.class,
                String.class,
                int.class,
                String.class);
        method.setAccessible(true);

        Locator scope = page.locator("#party-card");
        method.invoke(declarationPage, scope, "UEN", 0, "202535359H");
        method.invoke(declarationPage, scope, "Party Name", 0, "ADATACOMPANY PTE LTD");

        assertEquals("202535359H", page.locator("#uen-input").inputValue());
        assertEquals("ADATACOMPANY PTE LTD", page.locator("#party-name-input").inputValue());
    }

    @Test
    void fillsPartyIdFieldWhenPartyNameIsAlreadyResolved() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 960px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 1fr 60px 1fr; gap: 12px; align-items: center;">
                      <div>Importer</div>
                      <input id="importer-name" type="text" value="FORESPAND FOOD ENTER PRISE PTE LTD" style="width: 320px; height: 28px;">
                      <div>UEN</div>
                      <input id="importer-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyIdFieldIfPresent",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(
                declarationPage,
                "Importer",
                "FORESPAND FOOD ENTER PRISE PTE LTD",
                "198700002E");

        assertTrue(result);
        assertEquals("198700002E", page.locator("#importer-uen").inputValue());
    }

    @Test
    void resolvesImporterIdFieldFromNearestReadonlySiblingInsteadOfNextPartyRow() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="position: relative; padding: 12px; border: 1px solid #ccc; width: 960px; height: 180px;">
                    <h2>Party Info (P)</h2>
                    <div style="position: absolute; left: 16px; top: 56px; font-weight: 600;">Importer</div>
                    <input id="importer-name" type="text" value="GST DUMMY COMPANY GSTDUMMYCOMPANY"
                           style="position: absolute; left: 220px; top: 50px; width: 280px; height: 28px;">
                    <div style="position: absolute; left: 16px; top: 92px; font-weight: 600;">Inward Carrier</div>
                    <input id="carrier-name" type="text" value="SINGAPORE AIRPORT TERMINAL SERVICES"
                           style="position: absolute; left: 220px; top: 86px; width: 280px; height: 28px;">
                    <input id="carrier-uen" type="text" value="197201770G"
                           style="position: absolute; left: 540px; top: 86px; width: 220px; height: 28px;">
                    <input id="importer-uen" type="text" value="20110715GST" readonly
                           style="position: absolute; left: 540px; top: 50px; width: 220px; height: 28px;">
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "resolvePartyIdFieldOrNull",
                String.class);
        method.setAccessible(true);

        Locator field = (Locator) method.invoke(declarationPage, "Importer");

        assertNotNull(field);
        assertEquals("importer-uen", field.getAttribute("id"));
        assertEquals("20110715GST", field.inputValue());
    }

    @Test
    void clickFirstVisibleSuggestionIgnoresUserProfileMenuAndUsesNearbyLookupMenu() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="user-profile-menu" class="dropdown-menu" style="display:block; width:220px; border:1px solid #999; background:#fff; margin-bottom:24px;">
                    <div id="profile-item" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                      Profile
                    </div>
                  </div>

                  <section style="padding: 12px; border: 1px solid #ccc; width: 900px;">
                    <h2>Shipment Info (S)</h2>
                    <div>Declaration Info</div>
                    <label>Cargo Type</label>
                    <div id="cargo-type-shell" style="display:block; width:260px;">
                      <input id="cargo-type-input" type="text">
                    </div>
                    <div id="cargo-type-options" class="dropdown-menu" style="display:block; width:260px; border:1px solid #999; background:#fff; margin-top:8px;">
                      <div id="cargo-type-option" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                        5 - Other non-containerized
                      </div>
                    </div>
                  </section>

                  <script>
                    (() => {
                      const profileItem = document.getElementById('profile-item');
                      const field = document.getElementById('cargo-type-input');
                      const option = document.getElementById('cargo-type-option');
                      profileItem.addEventListener('click', () => {
                        window.profileClicks = (window.profileClicks || 0) + 1;
                      });
                      option.addEventListener('click', () => {
                        window.lookupClicks = (window.lookupClicks || 0) + 1;
                        field.value = option.textContent.trim();
                        field.dispatchEvent(new Event('input', { bubbles: true }));
                        field.dispatchEvent(new Event('change', { bubbles: true }));
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        page.locator("#cargo-type-input").click();

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod("clickFirstVisibleSuggestion");
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(declarationPage);

        assertTrue(result);
        assertEquals(0, ((Number) page.evaluate("window.profileClicks || 0")).intValue());
        assertEquals(1, ((Number) page.evaluate("window.lookupClicks || 0")).intValue());
        assertEquals("5 - Other non-containerized", page.locator("#cargo-type-input").inputValue());
    }

    @Test
    void clickFirstVisibleSuggestionDoesNothingWithoutActiveEditableField() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="user-profile-menu" class="dropdown-menu" style="display:block; width:220px; border:1px solid #999; background:#fff; margin-bottom:24px;">
                    <div id="profile-item" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                      Profile
                    </div>
                  </div>

                  <section style="padding: 12px; border: 1px solid #ccc; width: 900px;">
                    <h2>Shipment Info (S)</h2>
                    <label>Cargo Type</label>
                    <input id="cargo-type-input" type="text">
                    <div id="cargo-type-options" class="dropdown-menu" style="display:block; width:260px; border:1px solid #999; background:#fff; margin-top:8px;">
                      <div id="cargo-type-option" class="dropdown-item" style="padding:6px 8px; cursor:pointer;">
                        5 - Other non-containerized
                      </div>
                    </div>
                  </section>

                  <script>
                    (() => {
                      document.getElementById('profile-item').addEventListener('click', () => {
                        window.profileClicks = (window.profileClicks || 0) + 1;
                      });
                      document.getElementById('cargo-type-option').addEventListener('click', () => {
                        window.lookupClicks = (window.lookupClicks || 0) + 1;
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        page.locator("body").click();

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod("clickFirstVisibleSuggestion");
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(declarationPage);

        assertFalse(result);
        assertEquals(0, ((Number) page.evaluate("window.profileClicks || 0")).intValue());
        assertEquals(0, ((Number) page.evaluate("window.lookupClicks || 0")).intValue());
    }

    @Test
    void fillLookupFieldAfterScopeLabelSkipsNextAutoAmountFieldWhenLookupValueAlreadyMatches() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="item-values" style="padding: 12px; border: 1px solid #ccc; width: 420px;">
                    <h2>Item Values</h2>
                    <div style="display:grid; grid-template-columns: 120px 120px 80px; gap: 10px; align-items:center; margin-bottom: 18px;">
                      <div>Item Value</div>
                      <input id="item-value-amount" type="text" value="10000.00" style="width:120px;">
                      <div id="item-value-currency" aria-haspopup="listbox" style="width:60px; height:28px; border:1px solid #999; display:flex; align-items:center; padding:0 8px;">
                        SGD
                      </div>
                    </div>
                    <div style="display:grid; grid-template-columns: 120px 120px; gap: 10px; align-items:center;">
                      <div>Unit Price (auto)</div>
                      <input id="unit-price-auto" type="text" value="0.00" placeholder="0.00" style="width:120px;">
                    </div>
                  </section>
                  <script>
                    (() => {
                      document.getElementById('item-value-currency').addEventListener('click', () => {
                        window.lookupClicks = (window.lookupClicks || 0) + 1;
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillLookupFieldAfterScopeLabelIfPresent",
                Locator.class,
                String.class,
                int.class,
                String.class,
                String[].class);
        method.setAccessible(true);

        Locator scope = page.locator("#item-values");
        method.invoke(declarationPage, scope, "Item Value", 1, "SGD", new String[] { "SGD" });

        assertEquals("SGD", page.locator("#item-value-currency").innerText().trim());
        assertEquals("0.00", page.locator("#unit-price-auto").inputValue());
        assertEquals(0, ((Number) page.evaluate("window.lookupClicks || 0")).intValue());
    }

    @Test
    void fillLookupFieldAfterScopeLabelUsesRenderedReadonlySameRowValueBeforeFallingToNextInput() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="item-values" style="padding: 12px; border: 1px solid #ccc; width: 420px;">
                    <h2>Item Values</h2>
                    <div style="display:grid; grid-template-columns: 120px 120px 80px; gap: 10px; align-items:center; margin-bottom: 18px;">
                      <div>Item Value</div>
                      <input id="item-value-amount" type="text" value="10000.00" style="width:120px;">
                      <input id="item-value-currency" type="text" value="SGD" readonly style="width:60px;">
                    </div>
                    <div style="display:grid; grid-template-columns: 120px 120px; gap: 10px; align-items:center;">
                      <div>Unit Price (auto)</div>
                      <input id="unit-price-auto" type="text" value="0.00" placeholder="0.00" style="width:120px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillLookupFieldAfterScopeLabelIfPresent",
                Locator.class,
                String.class,
                int.class,
                String.class,
                String[].class);
        method.setAccessible(true);

        Locator scope = page.locator("#item-values");
        method.invoke(declarationPage, scope, "Item Value", 1, "SGD", new String[] { "SGD" });

        assertEquals("SGD", page.locator("#item-value-currency").inputValue());
        assertEquals("0.00", page.locator("#unit-price-auto").inputValue());
    }

    @Test
    void resolvesImporterPartyNameFromComponentSelectorWithoutTouchingUenField() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="tab-strip" style="display: flex; gap: 12px; margin-bottom: 16px;">
                    <button type="button">Shipment Info (S)</button>
                    <button type="button">Party Info (P)</button>
                  </div>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 960px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 1fr 60px 1fr; gap: 12px; align-items: center;">
                      <div>Importer</div>
                      <app-importer-lookup formcontrolname="name" style="display: block;">
                        <input id="importer-name" type="text" style="width: 320px; height: 28px;">
                      </app-importer-lookup>
                      <div>UEN</div>
                      <input id="importer-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "resolvePartyNameField",
                String.class);
        method.setAccessible(true);

        Locator field = (Locator) method.invoke(declarationPage, "Importer");
        field.fill("FORESPAND FOOD ENTER PRISE PTE LTD");

        assertEquals("FORESPAND FOOD ENTER PRISE PTE LTD", page.locator("#importer-name").inputValue());
        assertEquals("", page.locator("#importer-uen").inputValue());
    }

    @Test
    void resolvesImporterPartyUenFromComponentRowInsteadOfNameField() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="tab-strip" style="display: flex; gap: 12px; margin-bottom: 16px;">
                    <button type="button">Shipment Info (S)</button>
                    <button type="button">Party Info (P)</button>
                  </div>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 960px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center;">
                      <div>Importer</div>
                      <app-importer-lookup formcontrolname="name" style="display: block;">
                        <input id="importer-name" type="text" style="width: 320px; height: 28px;">
                      </app-importer-lookup>
                      <div>🔍</div>
                      <input id="importer-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "resolvePartyIdFieldOrNull",
                String.class);
        method.setAccessible(true);

        Locator field = (Locator) method.invoke(declarationPage, "Importer");
        field.fill("198700002E");

        assertEquals("", page.locator("#importer-name").inputValue());
        assertEquals("198700002E", page.locator("#importer-uen").inputValue());
    }

    @Test
    void resolvesFreightForwarderFieldFromItsOwnRow() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 960px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 1fr 60px 1fr; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Importer</div>
                      <input id="importer-name" type="text" style="width: 320px; height: 28px;">
                      <div>UEN</div>
                      <input id="importer-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 1fr 60px 1fr; gap: 12px; align-items: center;">
                      <div>Freight Forwarder</div>
                      <input id="freight-name" type="text" style="width: 320px; height: 28px;">
                      <div>UEN</div>
                      <input id="freight-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "resolvePartyNameField",
                String.class);
        method.setAccessible(true);

        Locator field = (Locator) method.invoke(declarationPage, "Freight Forwarder");
        field.fill("ADATACOMPANY PTE LTD");

        assertEquals("", page.locator("#importer-name").inputValue());
        assertEquals("ADATACOMPANY PTE LTD", page.locator("#freight-name").inputValue());
    }

    @Test
    void resolvesInwardCarrierFromNearestPartyRowContainer() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 260px 80px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Importer</div>
                      <input id="importer-name" type="text" style="width: 240px; height: 28px;">
                      <div>UEN</div>
                      <input id="importer-uen" type="text" style="width: 200px; height: 28px;">
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 80px 220px; gap: 12px; align-items: center;">
                        <input id="carrier-name" type="text" style="width: 300px; height: 28px;">
                        <div>UEN</div>
                        <input id="carrier-uen" type="text" style="width: 200px; height: 28px;">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method resolvePartyNameField = IptDeclarationPage.class.getDeclaredMethod(
                "resolvePartyNameField",
                String.class);
        resolvePartyNameField.setAccessible(true);
        Method readPartyRowText = IptDeclarationPage.class.getDeclaredMethod(
                "readPartyRowText",
                String.class);
        readPartyRowText.setAccessible(true);

        Locator field = (Locator) resolvePartyNameField.invoke(declarationPage, "Inward Carrier");
        field.fill("CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD");

        String rowText = (String) readPartyRowText.invoke(declarationPage, "Inward Carrier");

        assertEquals("", page.locator("#importer-name").inputValue());
        assertEquals("CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD", page.locator("#carrier-name").inputValue());
        assertTrue(rowText.contains("Inward Carrier"));
        assertFalse(rowText.contains("Importer UEN"));
    }

    @Test
    void resolvesInwardCarrierUenFromDistinctFieldWithoutOverwritingPartyName() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Importer</div>
                      <app-importer-lookup formcontrolname="name" style="display: block;">
                        <input id="importer-name" type="text" style="width: 320px; height: 28px;">
                      </app-importer-lookup>
                      <div>🔍</div>
                      <input id="importer-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" style="width: 220px; height: 28px;">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "resolvePartyIdFieldOrNull",
                String.class);
        method.setAccessible(true);

        Locator field = (Locator) method.invoke(declarationPage, "Inward Carrier");
        field.fill("197702772D");

        assertEquals("", page.locator("#carrier-name").inputValue());
        assertEquals("197702772D", page.locator("#carrier-uen").inputValue());
    }

    @Test
    void acceptsExpandedPartyDisplayNameWhenFillingUenForLookupCode() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" value="CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" style="width: 220px; height: 28px;">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyIdFieldIfPresent",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(
                declarationPage,
                "Inward Carrier",
                "CHGI",
                "197702772D");

        assertTrue(result);
        assertEquals("CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD", page.locator("#carrier-name").inputValue());
        assertEquals("197702772D", page.locator("#carrier-uen").inputValue());
    }

    @Test
    void refillsInwardCarrierUenWhenUiClearsTheFirstTypedValue() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" value="CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" style="width: 220px; height: 28px;">
                      </div>
                    </div>
                  </section>
                  <script>
                    (() => {
                      const carrierUen = document.getElementById('carrier-uen');
                      let resetTriggered = false;
                      carrierUen.addEventListener('input', () => {
                        if (resetTriggered) {
                          return;
                        }
                        resetTriggered = true;
                        setTimeout(() => {
                          carrierUen.value = '';
                          carrierUen.dispatchEvent(new Event('input', { bubbles: true }));
                          carrierUen.dispatchEvent(new Event('change', { bubbles: true }));
                        }, 75);
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyIdFieldIfPresent",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(
                declarationPage,
                "Inward Carrier",
                "CHGI",
                "197702772D");

        assertTrue(result);
        assertEquals("CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD", page.locator("#carrier-name").inputValue());
        assertEquals("197702772D", page.locator("#carrier-uen").inputValue());
    }

    @Test
    void refillsInwardCarrierUenAfterResolvedFieldRerendersDuringTyping() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" value="CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen-old" type="text" style="width: 220px; height: 28px;">
                        <input id="carrier-uen-new" type="text" style="display: none; width: 220px; height: 28px;">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page) {
            private boolean firstTypingAttempt = true;

            @Override
            protected void focusAndType(Locator field, String value, boolean selectSuggestion) {
                if (firstTypingAttempt) {
                    firstTypingAttempt = false;
                    page.evaluate("""
                            () => {
                                document.getElementById('carrier-uen-old').style.display = 'none';
                                document.getElementById('carrier-uen-new').style.display = 'block';
                            }
                            """);
                    throw new PlaywrightException("Timeout 15000ms exceeded.");
                }
                super.focusAndType(field, value, selectSuggestion);
            }
        };
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyIdFieldIfPresent",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(
                declarationPage,
                "Inward Carrier",
                "CHGI",
                "197702772D");

        assertTrue(result);
        assertEquals("197702772D", page.locator("#carrier-uen-new").inputValue());
        assertEquals("", page.locator("#carrier-uen-old").inputValue());
    }

    @Test
    void fillsInwardCarrierRowBySelectingVisibleSuggestionForLookupCode() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" style="width: 220px; height: 28px;">
                      </div>
                    </div>
                    <div id="carrier-dropdown" class="dropdown-menu" style="display: none; border: 1px solid #999; width: 320px; background: #fff;">
                      <div id="carrier-option" class="dropdown-item" style="padding: 6px 8px; cursor: pointer;">
                        CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD
                      </div>
                    </div>
                  </section>
                  <script>
                    (() => {
                      const nameField = document.getElementById('carrier-name');
                      const dropdown = document.getElementById('carrier-dropdown');
                      const option = document.getElementById('carrier-option');
                      const normalized = value => (value || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                      const render = () => {
                        const query = normalized(nameField.value);
                        dropdown.style.display = (query === 'CHGI' || query === '197702772D') ? 'block' : 'none';
                      };
                      nameField.addEventListener('input', render);
                      option.addEventListener('click', () => {
                        nameField.value = option.textContent.trim();
                        nameField.dispatchEvent(new Event('input', { bubbles: true }));
                        nameField.dispatchEvent(new Event('change', { bubbles: true }));
                        dropdown.style.display = 'none';
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyRow",
                String.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode partyNode = OBJECT_MAPPER.readTree("""
                {
                  "partyIdentification": { "id": "197702772D" },
                  "partyName": { "name": "CHGI" }
                }
                """);

        method.invoke(declarationPage, "Inward Carrier", partyNode);

        assertEquals("CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD", page.locator("#carrier-name").inputValue());
        assertEquals("197702772D", page.locator("#carrier-uen").inputValue());
    }

    void syncsInwardCarrierLookupComponentWhenUiSelectionDidNotPersistUen() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup id="carrier-host" formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" value="AB SHIPPING PTE LTD AB SHIPPING PTE LTD" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" style="width: 220px; height: 28px;">
                      </div>
                    </div>
                  </section>
                  <script>
                    (() => {
                      const host = document.getElementById('carrier-host');
                      const nameField = document.getElementById('carrier-name');
                      const uenField = document.getElementById('carrier-uen');
                      const component = {
                        allOptions: [
                          { code: '199201306R', description: 'AB SHIPPING PTE LTD AB SHIPPING PTE LTD' }
                        ],
                        selectOptionItem(option) {
                          nameField.value = option.description;
                        },
                        selectOptionLabel(option) {
                          nameField.value = option.description;
                        },
                        onChange(code) {
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

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "syncPartyLookupComponentSelection",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        boolean result = (Boolean) method.invoke(
                declarationPage,
                "Inward Carrier",
                "AB SHIPPING PTE LTD",
                "199201306R");

        assertTrue(result);
        assertEquals("AB SHIPPING PTE LTD AB SHIPPING PTE LTD", page.locator("#carrier-name").inputValue());
        assertEquals("199201306R", page.locator("#carrier-uen").inputValue());
    }

    @Test
    void fillsInwardCarrierBySyncingLookupComponentWhenVisualTextMatchesButSelectionDidNotCommit() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup id="carrier-host" formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" style="width: 220px; height: 28px;">
                        <input id="carrier-model" type="hidden">
                      </div>
                    </div>
                  </section>
                  <script>
                    (() => {
                      const host = document.getElementById('carrier-host');
                      const nameField = document.getElementById('carrier-name');
                      const uenField = document.getElementById('carrier-uen');
                      const modelField = document.getElementById('carrier-model');
                      const component = {
                        allOptions: [
                          { code: '197201770G', description: 'SINGAPORE AIRPORT' }
                        ],
                        selectOptionItem(option) {
                          nameField.value = option.description;
                        },
                        selectOptionLabel(option) {
                          nameField.value = option.description;
                        },
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

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyRow",
                String.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode partyNode = OBJECT_MAPPER.readTree("""
                {
                  "partyIdentification": { "id": "197201770G" },
                  "partyName": { "name": "SINGAPORE AIRPORT" }
                }
                """);

        method.invoke(declarationPage, "Inward Carrier", partyNode);

        assertEquals("SINGAPORE AIRPORT", page.locator("#carrier-name").inputValue());
        assertEquals("197201770G", page.locator("#carrier-uen").inputValue());
        assertEquals("197201770G", page.locator("#carrier-model").inputValue());
    }

    @Test
    void syncsInwardCarrierLookupComponentWhenComponentUsesOptionsCollection() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup id="carrier-host" formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" style="width: 220px; height: 28px;">
                        <input id="carrier-model" type="hidden">
                      </div>
                    </div>
                  </section>
                  <script>
                    (() => {
                      const host = document.getElementById('carrier-host');
                      const nameField = document.getElementById('carrier-name');
                      const uenField = document.getElementById('carrier-uen');
                      const modelField = document.getElementById('carrier-model');
                      const component = {
                        options: [
                          { code: '197201770G', description: 'SINGAPORE AIRPORT TERMINAL SERVICES' }
                        ],
                        inputElement: { nativeElement: nameField },
                        selectOptionItem(option) {
                          nameField.value = option.description;
                        },
                        selectOptionLabel(option) {
                          nameField.value = option.description;
                        },
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

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyRow",
                String.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode partyNode = OBJECT_MAPPER.readTree("""
                {
                  "partyIdentification": { "id": "197201770G" },
                  "partyName": { "name": "SINGAPORE AIRPORT TERMINAL SERVICES" }
                }
                """);

        method.invoke(declarationPage, "Inward Carrier", partyNode);

        assertEquals("SINGAPORE AIRPORT TERMINAL SERVICES", page.locator("#carrier-name").inputValue());
        assertEquals("197201770G", page.locator("#carrier-uen").inputValue());
        assertEquals("197201770G", page.locator("#carrier-model").inputValue());
    }

    @Test
    void syncsInwardCarrierLookupComponentWithoutOptionsByPushingModelValue() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 420px; gap: 16px; align-items: start;">
                      <div id="carrier-label-shell" style="padding-top: 52px; padding-bottom: 52px; font-weight: 600;">
                        <span>Inward</span>
                        <span>Carrier</span>
                      </div>
                      <div id="carrier-row" style="display: grid; grid-template-columns: 320px 40px 220px; gap: 12px; align-items: center;">
                        <app-inward-carrier-lookup id="carrier-host" formcontrolname="name" style="display: block;">
                          <input id="carrier-name" type="text" style="width: 320px; height: 28px;">
                        </app-inward-carrier-lookup>
                        <div>🔍</div>
                        <input id="carrier-uen" type="text" style="width: 220px; height: 28px;">
                        <input id="carrier-model" type="hidden">
                      </div>
                    </div>
                  </section>
                  <script>
                    (() => {
                      const host = document.getElementById('carrier-host');
                      const nameField = document.getElementById('carrier-name');
                      const uenField = document.getElementById('carrier-uen');
                      const modelField = document.getElementById('carrier-model');
                      const component = {
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

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyRow",
                String.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode partyNode = OBJECT_MAPPER.readTree("""
                {
                  "partyIdentification": { "id": "197201770G" },
                  "partyName": { "name": "SINGAPORE AIRPORT TERMINAL SERVICES" }
                }
                """);

        method.invoke(declarationPage, "Inward Carrier", partyNode);

        assertEquals("SINGAPORE AIRPORT TERMINAL SERVICES", page.locator("#carrier-name").inputValue());
        assertEquals("197201770G", page.locator("#carrier-uen").inputValue());
        assertEquals("197201770G", page.locator("#carrier-model").inputValue());
    }

    @Test
    void fillsImporterRowWhenComponentFieldMatchesWithoutSuggestionPopup() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 960px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center;">
                      <div>Importer</div>
                      <app-importer-lookup formcontrolname="name" style="display: block;">
                        <input id="importer-name" type="text" style="width: 320px; height: 28px;">
                      </app-importer-lookup>
                      <div>🔍</div>
                      <input id="importer-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyRow",
                String.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode partyNode = OBJECT_MAPPER.readTree("""
                {
                  "partyIdentification": { "id": "198402847H" },
                  "partyName": { "name": "BAYSWATER SHIPPING FORWARDING PTE LTD" }
                }
                """);

        method.invoke(declarationPage, "Importer", partyNode);

        assertEquals("BAYSWATER SHIPPING FORWARDING PTE LTD", page.locator("#importer-name").inputValue());
        assertEquals("198402847H", page.locator("#importer-uen").inputValue());
    }

    @Test
    void resolvesOutwardCarrierFromDedicatedComponentSelector() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Importer</div>
                      <app-importer-lookup formcontrolname="name" style="display: block;">
                        <input id="importer-name" type="text" style="width: 320px; height: 28px;">
                      </app-importer-lookup>
                      <div>🔍</div>
                      <input id="importer-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 320px 40px 220px; gap: 12px; align-items: center;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display: block;">
                        <input id="outward-name" type="text" style="width: 320px; height: 28px;">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "resolvePartyNameField",
                String.class);
        method.setAccessible(true);

        Locator field = (Locator) method.invoke(declarationPage, "Outward Carrier");
        field.fill("AB SHIPPING PTE LTD");

        assertEquals("", page.locator("#importer-name").inputValue());
        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-name").inputValue());
    }

    @Test
    void resolvesHandlingAgentFromAlignedRowInsteadOfFirstPartyRow() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1400px;">
                    <h2>Party Info (P)</h2>
                    <div id="party-grid">
                      <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                        <div>Inward Carrier</div>
                        <input id="inward-name" type="text">
                        <div>🔍</div>
                        <input id="inward-uen" type="text">
                      </div>
                      <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                        <div>Outward Carrier</div>
                        <input id="outward-name" type="text">
                        <div>🔍</div>
                        <input id="outward-uen" type="text">
                      </div>
                      <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                        <div>Handling Agent</div>
                        <input id="handling-name" type="text">
                        <div>🔍</div>
                        <input id="handling-uen" type="text">
                      </div>
                      <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center;">
                        <div>Declaring Agent</div>
                        <input id="declaring-name" type="text">
                        <div>🔍</div>
                        <input id="declaring-uen" type="text">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillPartyRow",
                String.class,
                JsonNode.class);
        method.setAccessible(true);

        JsonNode partyNode = OBJECT_MAPPER.readTree("""
                {
                  "partyIdentification": { "id": "198800784N" },
                  "partyName": { "name": "CRIMSONLOGIC PTE LTD CUSTOMER SERVICE CENTRE" }
                }
                """);

        method.invoke(declarationPage, "Handling Agent", partyNode);

        assertEquals("", page.locator("#inward-name").inputValue());
        assertEquals("", page.locator("#outward-name").inputValue());
        assertEquals("CRIMSONLOGIC PTE LTD CUSTOMER SERVICE CENTRE", page.locator("#handling-name").inputValue());
        assertEquals("198800784N", page.locator("#handling-uen").inputValue());
    }
}
