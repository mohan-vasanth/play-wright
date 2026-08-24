package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.InpDeclarationPage;
import com.automation.IptDeclarationPage;
import com.automation.TnpDeclarationPage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IptPartyNavigationReadyStateTest extends BaseTest {

    @Test
    void iptPartySectionAdvancesAsSoonAsNextIsReady() throws Exception {
        page.setContent(partySaveHarnessHtml());

        invokeCompleteSection(new IptDeclarationPage(page), "Party Info (P)");

        assertNextClickedBeforeSaveReenabled();
    }

    @Test
    void inpPartySectionAdvancesAsSoonAsNextIsReady() throws Exception {
        page.setContent(partySaveHarnessHtml());

        invokeCompleteSection(new InpDeclarationPage(page), "Party Info (P)");

        assertNextClickedBeforeSaveReenabled();
    }

    @Test
    void nonInpIptPartySectionStillWaitsForFullSaveReadyState() throws Exception {
        page.setContent(partySaveHarnessHtml());

        invokeCompleteSection(new TnpDeclarationPage(page), "Party Info (P)");

        long nextClickedAt = readBodyTimestamp("nextClickedAt");
        long saveReenabledAt = readBodyTimestamp("saveReenabledAt");
        assertTrue(
                nextClickedAt >= saveReenabledAt,
                "Expected NEXT to wait for SAVE DRAFT readiness outside IPT/INP, but NEXT clicked at "
                        + nextClickedAt + " before SAVE DRAFT re-enabled at " + saveReenabledAt);
    }

    private void invokeCompleteSection(Object declarationPage, String sectionName) throws Exception {
        Method method = IptDeclarationPage.class.getDeclaredMethod("completeSection", String.class);
        method.setAccessible(true);
        method.invoke(declarationPage, sectionName);
    }

    private void assertNextClickedBeforeSaveReenabled() {
        waitForTimestamps("nextClickedAt", "saveReenabledAt");
        long nextClickedAt = readBodyTimestamp("nextClickedAt");
        long saveReenabledAt = readBodyTimestamp("saveReenabledAt");
        assertTrue(
                nextClickedAt > 0 && nextClickedAt < saveReenabledAt,
                "Expected NEXT to be clicked before SAVE DRAFT re-enabled, but nextClickedAt="
                        + nextClickedAt + " and saveReenabledAt=" + saveReenabledAt);
    }

    private long readBodyTimestamp(String key) {
        Object value = page.evaluate("key => document.body.dataset[key] || ''", key);
        return Long.parseLong(String.valueOf(value));
    }

    private void waitForTimestamps(String... keys) {
        page.waitForFunction("""
                expectedKeys => expectedKeys.every(key => {
                    const value = document.body.dataset[key];
                    return typeof value === 'string' && value.trim().length > 0;
                })
                """, keys);
    }

    private String partySaveHarnessHtml() {
        return """
                <html>
                <body>
                  <button id="save-draft" type="button">SAVE DRAFT</button>
                  <button id="next" type="button" disabled>NEXT</button>
                  <script>
                    const saveDraft = document.getElementById('save-draft');
                    const next = document.getElementById('next');
                    saveDraft.addEventListener('click', () => {
                      document.body.dataset.saveClickedAt = String(Date.now());
                      saveDraft.disabled = true;
                      next.disabled = true;

                      const spinner = document.createElement('div');
                      spinner.className = 'spinner';
                      spinner.textContent = 'Saving';
                      document.body.appendChild(spinner);

                      setTimeout(() => {
                        spinner.remove();
                        next.disabled = false;
                        document.body.dataset.nextEnabledAt = String(Date.now());
                      }, 120);

                      setTimeout(() => {
                        saveDraft.disabled = false;
                        document.body.dataset.saveReenabledAt = String(Date.now());
                      }, 900);
                    });

                    next.addEventListener('click', () => {
                      document.body.dataset.nextClickedAt = String(Date.now());
                    });
                  </script>
                </body>
                </html>
                """;
    }
}
