package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IptDeclarationSupplyIndicatorTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest
    @MethodSource("uncheckedSupplyIndicatorValues")
    void leavesSupplyIndicatorUncheckedUnlessJsonValueIsBooleanTrue(JsonNode supplyIndicatorValue) throws Exception {
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
        if (supplyIndicatorValue != null) {
            cargo.set("supplyIndicator", supplyIndicatorValue);
        }

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

    private static Stream<Arguments> uncheckedSupplyIndicatorValues() {
        return Stream.of(
                Arguments.of((JsonNode) null),
                Arguments.of(JsonNodeFactory.instance.nullNode()),
                Arguments.of(JsonNodeFactory.instance.booleanNode(false)),
                Arguments.of(JsonNodeFactory.instance.textNode("")),
                Arguments.of(JsonNodeFactory.instance.textNode("true")),
                Arguments.of(JsonNodeFactory.instance.numberNode(1)));
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
