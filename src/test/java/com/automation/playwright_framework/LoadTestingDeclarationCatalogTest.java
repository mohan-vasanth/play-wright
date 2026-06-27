package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTestingDeclarationCatalogTest {

    @Test
    void declarationTypesPayloadExposesSupportedTypesForUi() {
        LoadTestingDeclarationCatalog catalog = new LoadTestingDeclarationCatalog();

        List<Map<String, String>> declarationTypes = catalog.declarationTypesPayload();

        assertFalse(declarationTypes.isEmpty());
        assertTrue(declarationTypes.stream().anyMatch(type -> "ipt".equals(type.get("value"))));
        assertTrue(declarationTypes.stream().anyMatch(type -> "out".equals(type.get("value"))));
        assertTrue(declarationTypes.stream().allMatch(type -> type.containsKey("value") && type.containsKey("label")));
    }

    @Test
    void inpAndTnpCatalogOptionsUseModuleSpecificFixtures() {
        LoadTestingDeclarationCatalog catalog = new LoadTestingDeclarationCatalog();

        Map<String, Object> inpPayload = catalog.jsonOptionsPayload("inp");
        Map<String, Object> tnpPayload = catalog.jsonOptionsPayload("tnp");

        assertTrue(String.valueOf(inpPayload.get("resolvedFolderPath")).endsWith("src\\test\\resources\\INP"));
        assertTrue(String.valueOf(tnpPayload.get("resolvedFolderPath")).endsWith("src\\test\\resources\\TNP"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> inpFiles = (List<Map<String, String>>) inpPayload.get("files");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tnpFiles = (List<Map<String, String>>) tnpPayload.get("files");

        assertTrue(inpFiles.stream().map(file -> file.get("resourcePath")).collect(Collectors.toSet()).contains("IE PERMIT.JSON"));
        assertTrue(tnpFiles.stream().map(file -> file.get("resourcePath")).collect(Collectors.toSet()).contains("TT PERMIT.json"));
        assertEquals("inp", inpPayload.get("type"));
        assertEquals("tnp", tnpPayload.get("type"));
    }
}
