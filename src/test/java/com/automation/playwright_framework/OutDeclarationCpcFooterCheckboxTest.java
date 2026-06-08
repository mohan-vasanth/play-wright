package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.OutDeclarationPage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OutDeclarationCpcFooterCheckboxTest extends BaseTest {

    @Test
    void deferredPrintingOfCoCheckboxFollowsExplicitTrueValue() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div style="margin-top: 24px;">
                    <div style="display: flex; gap: 16px; align-items: center;">
                      <div style="display: flex; gap: 6px; align-items: center;">
                        <input id="cnb" type="checkbox">
                        <div>CNB</div>
                      </div>
                      <div style="display: flex; gap: 6px; align-items: center;">
                        <input id="deferred-printing" type="checkbox">
                        <div><span>Deferred</span> <span>Printing</span> <span>of CO</span></div>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """);

        OutDeclarationPage outDeclarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("setCpcFooterCheckboxState", String.class, boolean.class);
        method.setAccessible(true);

        method.invoke(outDeclarationPage, "Deferred Printing of CO", true);

        assertFalse(page.locator("#cnb").isChecked(), "CNB checkbox should remain unchecked.");
        assertTrue(page.locator("#deferred-printing").isChecked(),
                "Deferred Printing of CO checkbox should be checked when the JSON value is explicitly true.");
    }

    @Test
    void deferredPrintingOfCoCheckboxCanBeForcedUnchecked() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div style="margin-top: 24px;">
                    <div style="display: flex; gap: 16px; align-items: center;">
                      <div style="display: flex; gap: 6px; align-items: center;">
                        <input id="cnb" type="checkbox">
                        <div>CNB</div>
                      </div>
                      <div style="display: flex; gap: 6px; align-items: center;">
                        <input id="deferred-printing" type="checkbox" checked>
                        <div><span>Deferred</span> <span>Printing</span> <span>of CO</span></div>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """);

        OutDeclarationPage outDeclarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("setCpcFooterCheckboxState", String.class, boolean.class);
        method.setAccessible(true);

        method.invoke(outDeclarationPage, "Deferred Printing of CO", false);

        assertFalse(page.locator("#deferred-printing").isChecked(),
                "Deferred Printing of CO checkbox should remain unchecked when the JSON value is false.");
    }
}
