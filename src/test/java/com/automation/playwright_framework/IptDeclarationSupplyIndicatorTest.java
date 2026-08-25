package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IptDeclarationSupplyIndicatorTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void doesNotTouchSupplyIndicatorWhenJsonFieldIsMissing() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <div>Checks</div>
                    <label for="supply-indicator">Supply Indicator</label>
                    <input id="supply-indicator" type="checkbox">
                    <script>
                      window.supplyIndicatorChanges = 0;
                      document.getElementById('supply-indicator').addEventListener('change', () => {
                        window.supplyIndicatorChanges += 1;
                      });
                    </script>
                  </div>
                </body>
                </html>
                """);

        ObjectNode cargo = OBJECT_MAPPER.createObjectNode();
        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), cargo);

        assertFalse(page.locator("#supply-indicator").isChecked());
        assertEquals(0, ((Number) page.evaluate("() => window.supplyIndicatorChanges")).intValue());
    }

    @Test
    void clearsSupplyIndicatorWhenJsonFieldIsMissingButUiStartsChecked() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <div>Checks</div>
                    <label for="supply-indicator">Supply Indicator</label>
                    <input id="supply-indicator" type="checkbox" checked>
                  </div>
                </body>
                </html>
                """);

        ObjectNode cargo = OBJECT_MAPPER.createObjectNode();
        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), cargo);

        assertFalse(page.locator("#supply-indicator").isChecked());
    }

    @Test
    void clearsSupplyIndicatorOnlyWhenJsonValueIsExplicitlyFalse() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <div>Checks</div>
                    <label for="supply-indicator">Supply Indicator</label>
                    <input id="supply-indicator" type="checkbox" checked>
                  </div>
                </body>
                </html>
                """);

        ObjectNode cargo = OBJECT_MAPPER.createObjectNode();
        cargo.put("supplyIndicator", false);

        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), cargo);

        assertFalse(page.locator("#supply-indicator").isChecked());
    }

    @Test
    void selectsSupplyIndicatorOnlyWhenJsonValueIsExplicitlyTrue() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <div>Checks</div>
                    <label for="supply-indicator">Supply Indicator</label>
                    <input id="supply-indicator" type="checkbox">
                  </div>
                </body>
                </html>
                """);

        ObjectNode cargo = OBJECT_MAPPER.createObjectNode();
        cargo.put("supplyIndicator", true);

        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), cargo);

        assertTrue(page.locator("#supply-indicator").isChecked());
    }

    @Test
    void selectsSupplyIndicatorWhenJsonValueIsTextTrue() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <div>Checks</div>
                    <label for="supply-indicator">Supply Indicator</label>
                    <input id="supply-indicator" type="checkbox">
                  </div>
                </body>
                </html>
                """);

        ObjectNode cargo = OBJECT_MAPPER.createObjectNode();
        cargo.put("supplyIndicator", "true");

        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), cargo);

        assertTrue(page.locator("#supply-indicator").isChecked());
    }

    @Test
    void updatesSupplyIndicatorWithoutChangingNearbyCheckboxes() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <div>Checks</div>
                    <div>
                      <input id="license" type="checkbox" checked>
                      <label for="license">License</label>
                    </div>
                    <div>
                      <input id="supply-indicator" type="checkbox" checked>
                      <label for="supply-indicator">Supply Indicator</label>
                    </div>
                    <div>
                      <input id="additional-recipients" type="checkbox">
                      <label for="additional-recipients">Additional Recipients</label>
                    </div>
                  </div>
                </body>
                </html>
                """);

        ObjectNode cargo = OBJECT_MAPPER.createObjectNode();
        cargo.put("supplyIndicator", false);

        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), cargo);

        assertTrue(page.locator("#license").isChecked());
        assertFalse(page.locator("#supply-indicator").isChecked());
        assertFalse(page.locator("#additional-recipients").isChecked());
    }

    @Test
    void clearsSupplyIndicatorWhenPreviousRunCheckedItAndCurrentJsonOmitsTheField() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <div>Checks</div>
                    <label for="supply-indicator">Supply Indicator</label>
                    <input id="supply-indicator" type="checkbox">
                  </div>
                </body>
                </html>
                """);

        ObjectNode priorCargo = OBJECT_MAPPER.createObjectNode();
        priorCargo.put("supplyIndicator", true);
        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), priorCargo);
        assertTrue(page.locator("#supply-indicator").isChecked());

        ObjectNode currentCargo = OBJECT_MAPPER.createObjectNode();
        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), currentCargo);

        assertFalse(page.locator("#supply-indicator").isChecked());
    }

    @Test
    void captureSubmitValidationDiagnosticsIncludesSupplyIndicatorState() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <label for="supply-indicator">Supply Indicator</label>
                    <input id="supply-indicator" type="checkbox" checked>
                  </div>
                </body>
                </html>
                """);

        IptDeclarationPage iptDeclarationPage = new IptDeclarationPage(page);
        JsonNode diagnostics = OBJECT_MAPPER.readTree(iptDeclarationPage.captureSubmitValidationDiagnostics());

        assertTrue(diagnostics.path("supplyIndicator").asBoolean(false));
    }
    private void invokeFillChecksSection(ObjectNode header, ObjectNode cargo) throws Exception {
        IptDeclarationPage iptDeclarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "fillChecksSection",
                com.fasterxml.jackson.databind.JsonNode.class,
                com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        method.invoke(iptDeclarationPage, header, cargo);
    }
}
