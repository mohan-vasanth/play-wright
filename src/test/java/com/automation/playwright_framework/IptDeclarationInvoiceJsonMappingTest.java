package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IptDeclarationInvoiceJsonMappingTest {

    private static final String TEST_DATA_RESOURCE = "data/ipt-declaration-test-case-1.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void readsSupplierManufacturerNameFromInvoiceNode() {
        JsonNode testData = loadTestData(TEST_DATA_RESOURCE);

        String supplierManufacturerName = testData
                .path("invoice")
                .get(0)
                .path("supplierManufacturerParty")
                .path("name")
                .asText();

        assertEquals("NAME", supplierManufacturerName);
    }

    @Test
    void readsCascCodeOneFromBatchItem() {
        JsonNode testData = loadTestData("data/ipt-declaration-batch-test-case.json");

        String cascCodeOne = testData
                .get(0)
                .path("item")
                .get(0)
                .path("cascProduct")
                .get(0)
                .path("additionalCascIdentification")
                .get(0)
                .path("cascCodeOne")
                .asText();

        assertEquals("US99999", cascCodeOne);
    }

    private static JsonNode loadTestData(String resourcePath) {
        InputStream resourceStream = IptDeclarationInvoiceJsonMappingTest.class.getClassLoader()
                .getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            resourceStream = IptDeclarationInvoiceJsonMappingTest.class.getResourceAsStream("/" + resourcePath);
        }
        try (InputStream inputStream = resourceStream) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readTree(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read test data from: " + resourcePath, exception);
        }
    }
}
