package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "user.dir", mode = ResourceAccessMode.READ_WRITE)
class TestLauncherControllerTest {

    private final TestLauncherController controller = new TestLauncherController(new TestReportController());

    @Test
    void jsonOptionsListsRepositoryFilesFromProjectRoot() {
        Map<?, ?> body = jsonOptionsBody("ipt");

        assertEquals("ipt", body.get("type"));
        assertEquals(Boolean.TRUE, body.get("folderFound"));
        assertTrue(String.valueOf(body.get("configuredFolder")).endsWith("src/main/resources/declaration-json/IPT"));

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
    void inpAndTnpLaunchersAreExecutable() {
        Map<?, ?> inpBody = jsonOptionsBody("inp");
        Map<?, ?> tnpBody = jsonOptionsBody("tnp");

        assertEquals(Boolean.TRUE, inpBody.get("executable"));
        assertEquals(Boolean.TRUE, tnpBody.get("executable"));
    }

    @Test
    void inpAndTnpJsonOptionsPreferModuleSpecificTestResourceFolders() {
        Map<?, ?> inpBody = jsonOptionsBody("inp");
        Map<?, ?> tnpBody = jsonOptionsBody("tnp");

        assertTrue(String.valueOf(inpBody.get("resolvedFolderPath")).endsWith("src\\test\\resources\\INP"));
        assertTrue(String.valueOf(tnpBody.get("resolvedFolderPath")).endsWith("src\\test\\resources\\TNP"));

        List<?> inpFiles = (List<?>) inpBody.get("files");
        List<?> tnpFiles = (List<?>) tnpBody.get("files");

        assertTrue(inpFiles.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(file -> String.valueOf(file.get("resourcePath")))
                .anyMatch("IE PERMIT.JSON"::equals));
        assertTrue(tnpFiles.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(file -> String.valueOf(file.get("resourcePath")))
                .anyMatch("TT PERMIT.json"::equals));
    }

    @Test
    void pmtStatusStopsPollingEvenBeforePermitNumberIsCaptured() throws Exception {
        Method mapJobState = TestLauncherController.class.getDeclaredMethod("mapJobState", String.class, String.class);
        mapJobState.setAccessible(true);

        assertEquals("SUCCESS", mapJobState.invoke(controller, "PMT", null));
        assertEquals("SUCCESS", mapJobState.invoke(controller, "PMT", "N/A"));
        assertEquals("SUCCESS", mapJobState.invoke(controller, "PMT", "OD6F274299A"));
    }

    @Test
    void submittedStatusIsTreatedAsSuccessfulTerminalState() throws Exception {
        Method mapJobState = TestLauncherController.class.getDeclaredMethod("mapJobState", String.class, String.class);
        mapJobState.setAccessible(true);

        assertEquals("SUCCESS", mapJobState.invoke(controller, "SUB", null));
        assertEquals("SUCCESS", mapJobState.invoke(controller, "SUB", "N/A"));
        assertEquals("SUCCESS", mapJobState.invoke(controller, "SUB", "OD6F274299A"));
    }

    @Test
    void classpathFallbackListsJsonFiles() throws Exception {
        Object config = resolveTypeConfig("out");
        Method listClasspathJsonFiles = TestLauncherController.class.getDeclaredMethod("listClasspathJsonFiles", config.getClass());
        listClasspathJsonFiles.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> files = (List<Map<String, String>>) listClasspathJsonFiles.invoke(controller, config);

        assertFalse(files.isEmpty());
        assertTrue(files.stream()
                .map(file -> file.get("resourcePath"))
                .anyMatch("OD-WITH COO.json"::equals));
    }

    @Test
    void classpathFallbackCanMaterializeSelectedJson() throws Exception {
        Object config = resolveTypeConfig("ipt");
        Method resolveClasspathJsonResource = TestLauncherController.class.getDeclaredMethod(
                "resolveClasspathJsonResource",
                config.getClass(),
                String.class);
        resolveClasspathJsonResource.setAccessible(true);

        Object resource = resolveClasspathJsonResource.invoke(controller, config, "ipt-declaration-test-case-1.json");
        assertNotNull(resource);

        Method materializeClasspathJsonResource = TestLauncherController.class.getDeclaredMethod(
                "materializeClasspathJsonResource",
                config.getClass(),
                String.class,
                Resource.class);
        materializeClasspathJsonResource.setAccessible(true);

        Path materializedPath = (Path) materializeClasspathJsonResource.invoke(
                controller,
                config,
                "ipt-declaration-test-case-1.json",
                resource);

        assertTrue(Files.isRegularFile(materializedPath));
        assertTrue(Files.size(materializedPath) > 0);
    }

    @Test
    void startRequiresARepositorySelectionOrUploadedJson() {
        ResponseEntity<?> response = controller.start("out", null, null);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        assertEquals(
                "Select a JSON from the folder or upload a JSON file manually.",
                ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void startRejectsInvalidUploadedJson() {
        MockMultipartFile invalidJson = new MockMultipartFile(
                "jsonFile",
                "bad.json",
                "application/json",
                "{ invalid json".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> response = controller.start("out", null, invalidJson);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        assertTrue(String.valueOf(((Map<?, ?>) response.getBody()).get("error"))
                .startsWith("Uploaded JSON file is invalid:"));
    }

    @Test
    void launcherCommandEnablesTradenixLiveTests() throws Exception {
        Object config = resolveTypeConfig("ipt");
        Method buildCommand = TestLauncherController.class.getDeclaredMethod(
                "buildCommand",
                config.getClass(),
                String.class,
                String.class);
        buildCommand.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> command = (List<String>) buildCommand.invoke(
                controller,
                config,
                "D:\\Mohan\\play-wright\\mvnw.cmd",
                "D:\\Mohan\\play-wright\\src\\test\\resources\\declaration-json\\IPT\\ipt-declaration-test-case-1.json");

        assertTrue(command.contains("-Dtradenix.live.tests=true"));
        assertTrue(command.contains("-Dtest=IptDeclarationTestCase1Test"));
    }

    private Map<?, ?> jsonOptionsBody(String type) {
        ResponseEntity<?> response = controller.jsonOptions(type);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        return (Map<?, ?>) response.getBody();
    }

    private Object resolveTypeConfig(String type) throws Exception {
        Method resolveTypeConfig = TestLauncherController.class.getDeclaredMethod("resolveTypeConfig", String.class);
        resolveTypeConfig.setAccessible(true);
        return resolveTypeConfig.invoke(controller, type);
    }
}
