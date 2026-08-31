package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTestingDashboardServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    @Test
    void prepareJobWorkItemAdvancesJsonRecordAcrossUsers() throws Exception {
        LoadTestingDashboardService service = new LoadTestingDashboardService(null, null);
        setField(service, "runId", "load-dashboard-1781845532873");
        setField(service, "currentRequest", new LoadTestingDashboardService.RunRequest(
                100,
                100,
                1,
                "http://localhost:8081",
                "mohan",
                "12345678",
                "OUT",
                "dataset.json",
                false,
                1,
                1,
                "STANDARD_LOAD",
                null,
                null));

        Method method = LoadTestingDashboardService.class.getDeclaredMethod(
                "prepareJobWorkItem",
                int.class,
                int.class,
                List.class);
        method.setAccessible(true);

        List<JsonNode> payloads = List.of(payload("MR-001"), payload("MR-002"), payload("MR-003"));
        Object userOneJob = method.invoke(service, 1, 1, payloads);
        Object userTwoJob = method.invoke(service, 2, 1, payloads);

        Method recordNumber = userOneJob.getClass().getDeclaredMethod("recordNumber");
        Method payload = userOneJob.getClass().getDeclaredMethod("payload");
        recordNumber.setAccessible(true);
        payload.setAccessible(true);

        assertEquals(1, recordNumber.invoke(userOneJob));
        assertEquals(2, recordNumber.invoke(userTwoJob));
        String firstMessageReference = ((JsonNode) payload.invoke(userOneJob)).path("header").path("messageReference").asText();
        String secondMessageReference = ((JsonNode) payload.invoke(userTwoJob)).path("header").path("messageReference").asText();
        assertTrue(firstMessageReference.startsWith("TDX"));
        assertTrue(secondMessageReference.startsWith("TDX"));
        assertFalse(firstMessageReference.equals(secondMessageReference));
        assertEquals("MR-001", payloads.get(0).path("header").path("messageReference").asText());
        assertEquals("MR-002", payloads.get(1).path("header").path("messageReference").asText());
    }

    @Test
    void normalizeRequestUsesSingleRunPerUserForSharedBrowserExecution() throws Exception {
        LoadTestingDashboardService service = new LoadTestingDashboardService(null, null);
        Method method = LoadTestingDashboardService.class.getDeclaredMethod(
                "normalizeRequest",
                LoadTestingDashboardService.RunRequest.class);
        method.setAccessible(true);

        LoadTestingDashboardService.RunRequest normalized =
                (LoadTestingDashboardService.RunRequest) method.invoke(service, new LoadTestingDashboardService.RunRequest(
                        10,
                        10,
                        10,
                        "http://localhost:8081",
                        "mohan",
                        "12345678",
                        "OUT",
                        "dataset.json",
                        false,
                        1,
                        1,
                        "STANDARD_LOAD",
                        null,
                        null));

        assertEquals(10, normalized.totalUsers());
        assertEquals(10, normalized.browserTabs());
        assertEquals(1, normalized.tabsPerUser());
        assertEquals(10, normalized.totalTabRuns());
    }

    @Test
    void coordinatorCrashRecordsFailedRowsInsteadOfStaleState() throws Exception {
        LoadTestingDashboardService service = new LoadTestingDashboardService(null, null);
        LoadTestingDashboardService.RunRequest request = new LoadTestingDashboardService.RunRequest(
                2,
                2,
                1,
                "http://localhost:8081",
                "mohan",
                "12345678",
                "ipt",
                "dataset.json",
                false,
                1,
                1,
                "LOW_TESTING",
                null,
                null);
        LoadTestingDeclarationCatalog.DeclarationDefinition definition =
                new LoadTestingDeclarationCatalog.DeclarationDefinition(
                        "ipt",
                        "In-Payment (IPT)",
                        "In-Payment (IPT)",
                        "/declarations/ipt",
                        "declaration-json/IPT",
                        LoadTestingDeclarationCatalog.WorkflowKind.IPT,
                        List.of("Edit Declaration", "Job Info"));
        setField(service, "runId", "load-dashboard-1781845532873");
        setField(service, "currentRequest", request);
        setField(service, "currentDefinition", definition);
        setField(service, "startedAt", Instant.now());
        setField(service, "running", true);
        setField(service, "status", "RUNNING");

        Method method = LoadTestingDashboardService.class.getDeclaredMethod(
                "handleCoordinatorCrash",
                LoadTestingDashboardService.RunRequest.class,
                LoadTestingDeclarationCatalog.DeclarationDefinition.class,
                List.class,
                Throwable.class);
        method.setAccessible(true);
        method.invoke(service, request, definition, List.of(payload("MR-001")), new LinkageError("broken coordinator"));

        LoadTestingDashboardService.StatusResponse status = service.status();

        assertEquals("FAILED", status.status());
        assertFalse(status.running());
        assertTrue(status.message().contains("broken coordinator"));
        assertEquals(2, status.completedUsers());
        assertEquals(2, status.executedUsers());
        assertEquals(2, status.failureCount());
        assertEquals(2, status.userJobResults().size());
        assertTrue(status.recentLogs().stream()
                .anyMatch(line -> line.contains("Coordinator thread crashed before normal completion")));
    }

    @Test
    void statusExposesBrowserContextAndCurrentFieldState() throws Exception {
        LoadTestingDashboardService service = new LoadTestingDashboardService(null, null);
        setField(service, "runId", "load-dashboard-1781845532873");
        setField(service, "activeBrowserSessions", 1);
        setField(service, "activeBrowserContexts", 10);
        setField(service, "openTabsCount", 10);
        setField(service, "currentUser", 3);
        setField(service, "currentTab", 3);
        setField(service, "currentField", "Importer Name");
        setField(service, "currentDeclaration", "Party Info");
        setField(service, "validationStatus", "RUNNING");

        LoadTestingDashboardService.StatusResponse status = service.status();

        assertEquals(1, status.activeBrowserSessions());
        assertEquals(10, status.activeBrowserContexts());
        assertEquals(10, status.openTabsCount());
        assertEquals(3, status.currentUser());
        assertEquals(3, status.currentTab());
        assertEquals("Importer Name", status.currentField());
        assertEquals("Party Info", status.currentDeclaration());
        assertEquals("RUNNING", status.validationStatus());
    }

    private JsonNode payload(String messageReference) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("messageReference", messageReference);
        ObjectNode urn = header.putObject("uniqueReferenceNumber");
        urn.put("date", "20260826");
        urn.put("sequenceNumeric", "000001");
        return root;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
