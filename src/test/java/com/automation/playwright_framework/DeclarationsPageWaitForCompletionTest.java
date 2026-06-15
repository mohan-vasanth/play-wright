package com.automation.playwright_framework;

import com.automation.DeclarationsPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeclarationsPageWaitForCompletionTest {

    @Test
    void returnsImmediatelyWhenTerminalStatusIsReachedBeforePermitNumberIsResolved() {
        DeclarationsPage declarationsPage = new DeclarationsPage(null) {
            @Override
            public DeclarationListEntry readDeclarationListEntry(String messageReference) {
                return new DeclarationListEntry("5371", "PMT", "TDX2606150060", "mohan", null);
            }

            @Override
            public void refreshDeclarationList() {
                throw new AssertionError("refreshDeclarationList should not be called after PMT is observed");
            }
        };

        DeclarationsPage.DeclarationListEntry entry = declarationsPage.waitForDeclarationCompletion(
                "TDX2606150060",
                "5371",
                0);

        assertEquals("5371", entry.jobId());
        assertEquals("PMT", entry.jobStatus());
        assertEquals("TDX2606150060", entry.declarationNumber());
        assertEquals("mohan", entry.jobCreatedBy());
        assertNull(entry.permitNumber());
    }
}
