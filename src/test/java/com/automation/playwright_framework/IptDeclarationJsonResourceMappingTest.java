package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IptDeclarationJsonResourceMappingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void idPermitJsonIncludesSupplyIndicatorAsBooleanTrue() throws Exception {
        JsonNode root = readResource("IPT/ID-PERMIT.json");
        JsonNode supplyIndicator = root.path("cargo").path("supplyIndicator");

        assertTrue(supplyIndicator.isBoolean(), "cargo.supplyIndicator must be a JSON boolean");
        assertTrue(supplyIndicator.booleanValue(), "cargo.supplyIndicator must be true when the checkbox is selected");
    }

    @Test
    void iptBatchFixtureUsesBooleanSupplyIndicator() throws Exception {
        JsonNode root = readResource("IPT/ipt-declaration-test-case-1.json");
        JsonNode firstDeclaration = root.isArray() && !root.isEmpty() ? root.get(0) : root;
        JsonNode supplyIndicator = firstDeclaration.path("cargo").path("supplyIndicator");

        assertTrue(supplyIndicator.isBoolean(), "cargo.supplyIndicator must be a JSON boolean");
        assertTrue(supplyIndicator.booleanValue(), "cargo.supplyIndicator must be true for the checked fixture");
    }

    private JsonNode readResource(String resourcePath) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Missing resource: " + resourcePath);
            return OBJECT_MAPPER.readTree(inputStream);
        }
    }
}
