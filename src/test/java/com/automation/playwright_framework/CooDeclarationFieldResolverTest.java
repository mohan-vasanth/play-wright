package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.CooDeclarationPage;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CooDeclarationFieldResolverTest extends BaseTest {

    @Test
    void fillsScopedConcreteInputWhenRowContainsWrapperElement() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="coo-scope" style="width: 520px; padding: 12px; border: 1px solid #ccc;">
                    <div style="display: grid; grid-template-columns: 180px 1fr; align-items: center; margin-bottom: 12px;">
                      <div>Item Invoice Number</div>
                      <div role="textbox" style="display: block; width: 260px; height: 32px; border: 1px solid #999; padding: 2px;">
                        <input id="item-invoice-number" type="text" style="width: 240px; height: 24px;">
                      </div>
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 1fr; align-items: center;">
                      <div>Certificate Item Description</div>
                      <div role="textbox" style="display: block; width: 260px; height: 60px; border: 1px solid #999; padding: 2px;">
                        <textarea id="certificate-item-description" style="width: 240px; height: 48px;"></textarea>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """);

        CooDeclarationPage declarationPage = new CooDeclarationPage(page);
        Method method = CooDeclarationPage.class.getDeclaredMethod(
                "fillScopedFieldAfterScopeLabelIfPresent",
                Locator.class,
                String.class,
                int.class,
                String.class);
        method.setAccessible(true);

        Locator scope = page.locator("#coo-scope");
        method.invoke(declarationPage, scope, "Item Invoice Number", 0, "INV-991011");
        method.invoke(declarationPage, scope, "Certificate Item Description", 0, "FLAVOUR");

        assertEquals("INV-991011", page.locator("#item-invoice-number").inputValue());
        assertEquals("FLAVOUR", page.locator("#certificate-item-description").inputValue());
    }
}
