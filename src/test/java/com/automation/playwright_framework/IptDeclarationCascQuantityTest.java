package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IptDeclarationCascQuantityTest extends BaseTest {

    @Test
    void waitsForTheQuantitySlotInsteadOfFallingThroughToTheUomSlot() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="casc-row">
                    <input id="product-code" type="text">
                    <input id="quantity" type="text" disabled>
                    <input id="uom" type="text">
                  </section>
                  <script>
                    setTimeout(() => document.getElementById('quantity').disabled = false, 250);
                  </script>
                </body>
                </html>
                """);

        IptDeclarationPage declarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "waitForCascPrimaryEditableField", Locator.class, int.class);
        method.setAccessible(true);

        Locator resolved = (Locator) method.invoke(declarationPage, page.locator("#casc-row"), 1);

        assertEquals("quantity", resolved.getAttribute("id"));
    }
}
