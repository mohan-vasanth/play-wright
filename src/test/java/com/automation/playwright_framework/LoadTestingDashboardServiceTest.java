package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTestingDashboardServiceTest {

    @Test
    void messageReferencesRemainUniqueAcrossLargeRuns() throws Exception {
        LoadTestingDashboardService service = new LoadTestingDashboardService(null, null);
        setField(service, "runId", "load-dashboard-1781845532873");

        Method method = LoadTestingDashboardService.class.getDeclaredMethod("generateMessageReference", int.class);
        method.setAccessible(true);

        Set<String> references = new HashSet<>();
        for (int userIndex = 1; userIndex <= 1000; userIndex++) {
            references.add((String) method.invoke(service, userIndex));
        }

        assertEquals(1000, references.size());
    }

    @Test
    void personalizedSuffixIsRetainedWhenValueIsTrimmed() throws Exception {
        LoadTestingDashboardService service = new LoadTestingDashboardService(null, null);
        Method method = LoadTestingDashboardService.class.getDeclaredMethod(
                "appendUserSuffix",
                String.class,
                int.class,
                int.class,
                int.class);
        method.setAccessible(true);

        String personalized = (String) method.invoke(service, "ABCDEFGHIJKLMNOPQRST", 1000, 1, 20);

        assertEquals(20, personalized.length());
        assertTrue(personalized.endsWith("U1000I1"));
    }

    @Test
    void reportStatusUsesSuccessFailureSemantics() throws Exception {
        LoadTestingDashboardService service = new LoadTestingDashboardService(null, null);
        Method method = LoadTestingDashboardService.class.getDeclaredMethod(
                "resolveReportStatus",
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class);
        method.setAccessible(true);

        assertEquals("SUCCESS", method.invoke(service, true, false, true, true));
        assertEquals("FAILED", method.invoke(service, false, true, true, true));
        assertEquals("PENDING", method.invoke(service, false, false, true, true));
    }

    @Test
    void jsonAuditIsNonBlockingByDefaultButCanBeEnabled() throws Exception {
        LoadTestingDashboardService service = new LoadTestingDashboardService(null, null);
        Method method = LoadTestingDashboardService.class.getDeclaredMethod("isStrictJsonAuditEnabled");
        method.setAccessible(true);

        String originalValue = System.getProperty("tradenix.strict.json.audit");
        try {
            System.clearProperty("tradenix.strict.json.audit");
            assertFalse((Boolean) method.invoke(service));

            System.setProperty("tradenix.strict.json.audit", "true");
            assertTrue((Boolean) method.invoke(service));
        } finally {
            if (originalValue == null) {
                System.clearProperty("tradenix.strict.json.audit");
            } else {
                System.setProperty("tradenix.strict.json.audit", originalValue);
            }
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
