package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IptDeclarationInvoiceJsonMappingTest {

    private static final String TEST_DATA_RESOURCE = "data/ipt-declaration-test-case-1.json";

    @Test
    void readsSupplierManufacturerNameFromInvoiceNode() {
        JsonNode testData = IptDeclarationTestDataLoader.load(TEST_DATA_RESOURCE);

        String supplierManufacturerName = testData
                .path("invoice")
                .get(0)
                .path("supplierManufacturerParty")
                .path("name")
                .asText();

        assertEquals("NAME", supplierManufacturerName);
    }
}
