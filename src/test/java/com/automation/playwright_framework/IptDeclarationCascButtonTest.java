package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IptDeclarationCascButtonTest extends BaseTest {

    @Test
    void addProductButtonIsAcceptedForCascSection() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div>
                    <div>CASC Details</div>
                    <div id="casc-section">
                      <button id="add-product-button" onclick="window.cascAddClicked = true;">ADD PRODUCT</button>
                    </div>
                  </div>
                </body>
                </html>
                """);

        IptDeclarationPage iptDeclarationPage = new IptDeclarationPage(page);
        Locator cascSection = page.locator("#casc-section");
        Method method = IptDeclarationPage.class.getDeclaredMethod("clickAddCascProductButton", Locator.class);
        method.setAccessible(true);

        method.invoke(iptDeclarationPage, cascSection);

        Object clicked = page.evaluate("() => window.cascAddClicked === true");
        assertTrue(Boolean.TRUE.equals(clicked), "ADD PRODUCT should be accepted as the CASC add button.");
    }
}
