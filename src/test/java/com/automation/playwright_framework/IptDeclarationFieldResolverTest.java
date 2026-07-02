package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class IptDeclarationFieldResolverTest extends BaseTest {

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
}
