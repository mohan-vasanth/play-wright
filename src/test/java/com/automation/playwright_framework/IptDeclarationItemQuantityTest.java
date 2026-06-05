package com.automation.playwright_framework;

import com.automation.IptDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IptDeclarationItemQuantityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void detectsOtherQtyFieldsWhenDutiableQuantitiesExist() throws Exception {
        JsonNode itemQuantity = loadFirstItem("data/out-declaration-batch-test-case.json").path("itemQuantity");

        assertTrue(hasOtherQuantityFields(itemQuantity));
    }

    @Test
    void keepsOtherQtyFieldsDisabledWhenOnlyHsQuantityExists() throws Exception {
        JsonNode itemQuantity = OBJECT_MAPPER.createObjectNode()
                .set("hsQuantity", OBJECT_MAPPER.createObjectNode()
                        .put("value", "10")
                        .put("unitCode", "KGM"));

        assertFalse(hasOtherQuantityFields(itemQuantity));
    }

    private boolean hasOtherQuantityFields(JsonNode itemQuantity)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        IptDeclarationPage page = new IptDeclarationPage(null);
        Method method = IptDeclarationPage.class.getDeclaredMethod("hasOtherQuantityFields", JsonNode.class);
        method.setAccessible(true);
        return (boolean) method.invoke(page, itemQuantity);
    }

    private static JsonNode loadFirstItem(String resourcePath) {
        try (InputStream inputStream = IptDeclarationItemQuantityTest.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            JsonNode root = OBJECT_MAPPER.readTree(inputStream);
            return root.get(0).path("item").get(0);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read test data from: " + resourcePath, exception);
        }
    }
}
