package com.automation.playwright_framework;

import com.automation.IptDeclarationPage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IptDeclarationPageDateFormatTest {

    @Test
    void formatsArrivalDateFromYyyyMmDdToDdMmYyyy() throws Exception {
        assertEquals("13-05-2026", formatUiDate("20260513"));
    }

    @Test
    void keepsAlreadyFormattedUiDateUntouched() throws Exception {
        assertEquals("13-05-2026", formatUiDate("13-05-2026"));
    }

    private String formatUiDate(String value)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        IptDeclarationPage page = new IptDeclarationPage(null);
        Method method = IptDeclarationPage.class.getDeclaredMethod("formatUiDate", String.class);
        method.setAccessible(true);
        return (String) method.invoke(page, value);
    }
}
