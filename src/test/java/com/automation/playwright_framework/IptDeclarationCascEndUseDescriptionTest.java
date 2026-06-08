package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IptDeclarationCascEndUseDescriptionTest extends BaseTest {

    @Test
    void endUseDescriptionIsFilledPerCascBlock() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="block-1">
                    <div>End User Description (Strategic Goods)</div>
                    <textarea id="end-use-1"></textarea>
                  </div>
                  <div id="block-2">
                    <div>End User Description (Strategic Goods)</div>
                    <textarea id="end-use-2"></textarea>
                  </div>
                </body>
                </html>
                """);

        IptDeclarationPage iptDeclarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod("fillCascEndUseDescription", Locator.class, Locator.class, String.class);
        method.setAccessible(true);

        method.invoke(iptDeclarationPage, page.locator("#block-1"), page.locator("#block-1"), "ADATA CASE VALUE");
        method.invoke(iptDeclarationPage, page.locator("#block-2"), page.locator("#block-2"), "ADATA CASE VALUE 111");

        assertEquals("ADATA CASE VALUE", page.locator("#end-use-1").inputValue());
        assertEquals("ADATA CASE VALUE 111", page.locator("#end-use-2").inputValue());
    }
}
