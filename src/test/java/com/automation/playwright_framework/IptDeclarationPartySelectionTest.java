package com.automation.playwright_framework;

import com.automation.IptDeclarationPage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IptDeclarationPartySelectionTest {

    @Test
    void acceptsResolvedDisplayNameForShortLookupCode() throws Exception {
        assertTrue(invokeResolvedPartySelectionValue(
                "CHANGI INTERNATIONAL AIRPORT SERVICES PTE LTD",
                "CHGI",
                "197702772D",
                "CHGI"));
    }

    @Test
    void rejectsUnchangedLookupCodeWhenSelectionDidNotResolve() throws Exception {
        assertFalse(invokeResolvedPartySelectionValue(
                "CHGI",
                "CHGI",
                "197702772D",
                "CHGI"));
    }

    @Test
    void rejectsBlankRenderedValue() throws Exception {
        assertFalse(invokeResolvedPartySelectionValue(
                "",
                "CHGI",
                "197702772D",
                "CHGI"));
    }

    @Test
    void acceptsDirectFullNameMatch() throws Exception {
        assertTrue(invokeResolvedPartySelectionValue(
                "FORESPAND FOOD ENTER PRISE PTE LTD",
                "FORESPAND FOOD ENTER PRISE PTE LTD",
                "198700002E",
                "FORESPAND FOOD ENTER PRISE PTE LTD"));
    }

    private boolean invokeResolvedPartySelectionValue(
            String renderedValue,
            String partyName,
            String partyId,
            String searchCandidate) throws Exception {
        IptDeclarationPage page = new IptDeclarationPage(null);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "isResolvedPartySelectionValue",
                String.class,
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(page, renderedValue, partyName, partyId, searchCandidate);
    }
}
