package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.DeclarationsPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeclarationsPageListEntryTest extends BaseTest {

    @Test
    void readsPermitNumberFromDeclarationListRow() {
        page.setContent("""
                <html>
                <body>
                  <table>
                    <thead>
                      <tr>
                        <th>Job ID</th>
                        <th>Message Ref</th>
                        <th>Declaration Type</th>
                        <th>Created By</th>
                        <th>Status</th>
                        <th>Permit No.</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>5341</td>
                        <td>TDX2606150030</td>
                        <td>DRT</td>
                        <td>mohan</td>
                        <td>PMT</td>
                        <td>OD6F274299A</td>
                      </tr>
                    </tbody>
                  </table>
                </body>
                </html>
                """);

        DeclarationsPage.DeclarationListEntry entry = new DeclarationsPage(page)
                .readDeclarationListEntry("TDX2606150030");

        assertEquals("5341", entry.jobId());
        assertEquals("PMT", entry.jobStatus());
        assertEquals("TDX2606150030", entry.declarationNumber());
        assertEquals("mohan", entry.jobCreatedBy());
        assertEquals("OD6F274299A", entry.permitNumber());
    }

    @Test
    void readsPermitNumberWhenListUsesPermitNumberHeader() {
        page.setContent("""
                <html>
                <body>
                  <table>
                    <thead>
                      <tr>
                        <th>Job ID</th>
                        <th>Message Ref</th>
                        <th>Created By</th>
                        <th>Status</th>
                        <th>Permit Number</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>5357</td>
                        <td>TDX2606150046</td>
                        <td>mohan</td>
                        <td>PMT</td>
                        <td>OD6F274299A</td>
                      </tr>
                    </tbody>
                  </table>
                </body>
                </html>
                """);

        DeclarationsPage.DeclarationListEntry entry = new DeclarationsPage(page)
                .readDeclarationListEntry("TDX2606150046");

        assertEquals("5357", entry.jobId());
        assertEquals("PMT", entry.jobStatus());
        assertEquals("TDX2606150046", entry.declarationNumber());
        assertEquals("mohan", entry.jobCreatedBy());
        assertEquals("OD6F274299A", entry.permitNumber());
    }
}
