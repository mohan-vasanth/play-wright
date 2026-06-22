package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
}
