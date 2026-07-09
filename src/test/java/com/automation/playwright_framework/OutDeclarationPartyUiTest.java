package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.OutDeclarationPage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OutDeclarationPartyUiTest extends BaseTest {

    @Test
    void ignoresRejectedDirectUenTypingForOutwardCarrierRow() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="party-section" style="padding: 12px; border: 1px solid #ccc; width: 1200px;">
                    <h2>Party Info (P)</h2>
                    <div style="display: grid; grid-template-columns: 160px 360px 40px 220px; gap: 12px; align-items: center; margin-bottom: 12px;">
                      <div>Outward Carrier</div>
                      <app-outward-carrier-agent-lookup formcontrolname="name" style="display: block;">
                        <input id="outward-name" type="text" value="AB SHIPPING PTE LTD" style="width: 320px; height: 28px;">
                      </app-outward-carrier-agent-lookup>
                      <div>🔍</div>
                      <input id="outward-uen" type="text" style="width: 220px; height: 28px;">
                    </div>
                  </section>
                  <script>
                    (() => {
                      const outwardUen = document.getElementById('outward-uen');
                      let cleared = false;
                      outwardUen.addEventListener('input', () => {
                        if (cleared) {
                          return;
                        }
                        cleared = true;
                        outwardUen.value = '';
                        outwardUen.dispatchEvent(new Event('input', { bubbles: true }));
                        outwardUen.dispatchEvent(new Event('change', { bubbles: true }));
                      });
                    })();
                  </script>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod(
                "fillLookupPartyRow",
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(
                declarationPage,
                "Outward Carrier",
                "AB SHIPPING PTE LTD",
                "199201306R"));

        assertEquals("AB SHIPPING PTE LTD", page.locator("#outward-name").inputValue());
        assertNotEquals("199201306R", page.locator("#outward-uen").inputValue());
    }
}
