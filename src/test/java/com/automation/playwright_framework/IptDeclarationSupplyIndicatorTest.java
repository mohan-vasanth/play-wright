package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IptDeclarationSupplyIndicatorTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void clearsSupplyIndicatorWhenJsonValueIsMissing() throws Exception {
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

        invokeFillChecksSection(OBJECT_MAPPER.createObjectNode(), OBJECT_MAPPER.createObjectNode());

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
