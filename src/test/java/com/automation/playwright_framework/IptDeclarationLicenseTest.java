package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.IptDeclarationPage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IptDeclarationLicenseTest extends BaseTest {

    @Test
    void fillsLicenseWhenCardMustExpandAndAddRowBeforeInputAppears() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="license-card" class="card" style="width: 420px; min-height: 180px; padding: 16px; border: 1px solid #ccc;"
                       onclick="expandLicensePanel(event)">
                    <div style="margin-bottom: 12px;">
                      <input id="license-toggle" type="checkbox">
                      <label for="license-toggle">License</label>
                    </div>
                    <div id="license-panel" style="display: none; margin-top: 32px;">
                      <div>List (1)</div>
                      <button id="license-add" type="button" onclick="showLicenseInput(event)">ADD</button>
                      <input id="license-input" type="text" style="display: none; margin-top: 12px;">
                    </div>
                  </div>
                  <script>
                    function expandLicensePanel(event) {
                      const toggle = document.getElementById('license-toggle');
                      const panel = document.getElementById('license-panel');
                      const target = event.target;
                      const toggledByCheckbox = target.id === 'license-toggle' || target.getAttribute('for') === 'license-toggle';
                      if (!toggledByCheckbox && toggle.checked) {
                        panel.style.display = 'block';
                      }
                    }

                    function showLicenseInput(event) {
                      event.stopPropagation();
                      document.getElementById('license-input').style.display = 'block';
                    }
                  </script>
                </body>
                </html>
                """);

        invokeFillLicense("IP03L1886");

        assertTrue(page.locator("#license-toggle").isChecked());
        assertEquals("IP03L1886", page.locator("#license-input").inputValue());
    }

    private void invokeFillLicense(String licenseValue) throws Exception {
        IptDeclarationPage iptDeclarationPage = new IptDeclarationPage(page);
        Method method = IptDeclarationPage.class.getDeclaredMethod("fillLicense", String.class);
        method.setAccessible(true);
        method.invoke(iptDeclarationPage, licenseValue);
    }
}
