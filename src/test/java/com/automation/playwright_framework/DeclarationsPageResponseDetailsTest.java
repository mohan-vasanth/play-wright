package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.DeclarationsPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeclarationsPageResponseDetailsTest extends BaseTest {

    @Test
    void readsResponseDetailsFromVisibleResponseTab() {
        page.setContent("""
                <html>
                <body>
                  <div>View Mode — all fields are read-only.</div>
                  <div>Job Info</div>
                  <button id="response-tab" type="button">Response (R)</button>
                  <section>
                    <h2>Rejection Details</h2>
                    <div id="details-card">
                      <div>PERMIT/AMENDMENT/CANCELLATION/REFUND APPLICATION NOT APPROVED</div>
                      <div>1. PLS PROVIDE CORRECT DATE OF DEPARTURE</div>
                    </div>
                  </section>
                  <button id="raw-toggle" type="button"
                          onclick="document.getElementById('raw-json').style.display='block'">SHOW RAW JSON</button>
                  <pre id="raw-json" style="display:none">{ "responseMessage": "PERMIT/AMENDMENT/CANCELLATION/REFUND APPLICATION NOT APPROVED\\n1. PLS PROVIDE CORRECT DATE OF DEPARTURE", "permitNo": "OD6F274299A" }</pre>
                </body>
                </html>
                """);

        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        DeclarationsPage.DeclarationResponseDetails details = declarationsPage.readCurrentResponseDetails();

        assertTrue(details.responseMessage().contains("PERMIT/AMENDMENT/CANCELLATION/REFUND APPLICATION NOT APPROVED"));
        assertTrue(details.responseMessage().contains("1. PLS PROVIDE CORRECT DATE OF DEPARTURE"));
        assertEquals(details.responseMessage(), details.errorMessage());
        assertTrue(details.rawResponseText().contains("responseMessage"));
        assertEquals("OD6F274299A", details.permitNumber());
    }

    @Test
    void readsPermitNumberFromVisiblePmtNoLabelWithoutRawJson() {
        page.setContent("""
                <html>
                <body>
                  <div>View Mode - all fields are read-only.</div>
                  <button id="response-tab" type="button">Response</button>
                  <section>
                    <h2>Response Details</h2>
                    <div>Declaration completed successfully.</div>
                  </section>
                  <div>
                    <span>PMT No.</span>
                    <span>OD6F274299A</span>
                  </div>
                </body>
                </html>
                """);

        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        DeclarationsPage.DeclarationResponseDetails details = declarationsPage.readCurrentResponseDetails();

        assertEquals("OD6F274299A", details.permitNumber());
    }
}
