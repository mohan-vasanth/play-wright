package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "user.dir", mode = ResourceAccessMode.READ_WRITE)
class TestLauncherControllerTest {

    private final TestLauncherController controller = new TestLauncherController(new TestReportController());

    @Test
    void jsonOptionsListsRepositoryFilesFromProjectRoot() {
        Map<?, ?> body = jsonOptionsBody("ipt");

        assertEquals("ipt", body.get("type"));
        assertEquals(Boolean.TRUE, body.get("folderFound"));
        assertTrue(String.valueOf(body.get("configuredFolder")).endsWith("src/test/resources/IPT"));

        List<?> files = (List<?>) body.get("files");
        assertFalse(files.isEmpty());
        assertTrue(files.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(file -> String.valueOf(file.get("resourcePath")))
                .anyMatch("ipt-declaration-test-case-1.json"::equals));
    }

    @Test
    void jsonOptionsStillListsFilesWhenApplicationStartsFromTargetDirectory() {
        String originalUserDir = System.getProperty("user.dir");
        Path targetDirectory = Paths.get(originalUserDir).resolve("target").toAbsolutePath().normalize();

        try {
            System.setProperty("user.dir", targetDirectory.toString());

            Map<?, ?> body = jsonOptionsBody("ipt");
            List<?> files = (List<?>) body.get("files");

            assertEquals(Boolean.TRUE, body.get("folderFound"));
            assertFalse(files.isEmpty());
            assertTrue(String.valueOf(body.get("resolvedFolderPath")).endsWith("IPT"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void pmtWithoutNumberRemainsInProgressUntilPermitNumberExists() throws Exception {
        Method mapJobState = TestLauncherController.class.getDeclaredMethod("mapJobState", String.class, String.class);
        mapJobState.setAccessible(true);

        assertEquals("IN_PROGRESS", mapJobState.invoke(controller, "PMT", null));
        assertEquals("IN_PROGRESS", mapJobState.invoke(controller, "PMT", "N/A"));
        assertEquals("SUCCESS", mapJobState.invoke(controller, "PMT", "OD6F274299A"));
    }

    private Map<?, ?> jsonOptionsBody(String type) {
        ResponseEntity<?> response = controller.jsonOptions(type);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        return (Map<?, ?>) response.getBody();
    }
}
