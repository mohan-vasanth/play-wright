package com.automation.playwright_framework;

import com.automation.DeclarationPayloads;
import com.automation.IptDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IptDeclarationJsonResourceMappingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void idPermitJsonIncludesSupplyIndicatorAsBooleanTrue() throws Exception {
        JsonNode root = readResource("IPT/ALL PERMIT IPT.json");
        JsonNode declarationWithSupplyIndicator = root;
        if (root.isArray()) {
            for (JsonNode declaration : root) {
                JsonNode candidate = declaration.path("cargo").path("supplyIndicator");
                if (candidate.isBoolean()) {
                    declarationWithSupplyIndicator = declaration;
                    break;
                }
            }
        }
        JsonNode supplyIndicator = declarationWithSupplyIndicator.path("cargo").path("supplyIndicator");

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

    @Test
    void inpFixtureCanBeUnwrappedFromInboundMessageEnvelope() throws Exception {
        JsonNode root = readResource("INP/IE PERMIT.JSON");
        JsonNode payload = DeclarationPayloads.unwrap(root);

        assertEquals("21", payload.path("header").path("declarationType").asText());
        assertEquals("TDX2606250015", payload.path("header").path("messageReference").asText());
    }

    @Test
    void inpImFixtureInfersPermitTypeFromSelectedJsonResource() throws Exception {
        JsonNode root = DeclarationPayloads.annotatePermitType(
                readResource("INP/IM PERMIT.JSON"),
                "INP/IM PERMIT.JSON");

        assertEquals("IM Permit", DeclarationPayloads.resolvePermitType(root, null));
        assertEquals("IM Permit", root.path("permitType").asText());
    }

    @Test
    void inpItFixtureInfersPermitTypeFromSelectedJsonResource() throws Exception {
        JsonNode root = DeclarationPayloads.annotatePermitType(
                readResource("INP/IT PERMIT.JSON"),
                "INP/IT PERMIT.JSON");

        assertEquals("IT Permit", DeclarationPayloads.resolvePermitType(root, null));
        assertEquals("IT Permit", root.path("permitType").asText());
    }

    @Test
    void itPermitTokenMatchesItDeclarationTypeBranch() throws Exception {
        IptDeclarationPage page = new IptDeclarationPage(null);
        Method method = IptDeclarationPage.class.getDeclaredMethod(
                "permitTypeMatches",
                String.class,
                String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(page, "IT Permit", "IT"));
    }

    @Test
    void tnpFixtureCanBeUnwrappedFromInboundMessageEnvelope() throws Exception {
        JsonNode root = readResource("TNP/TT PERMIT.json");
        JsonNode payload = DeclarationPayloads.unwrap(root);

        assertEquals("70", payload.path("header").path("declarationType").asText());
        assertEquals("TDX2606250022", payload.path("header").path("messageReference").asText());
    }

    private JsonNode readResource(String resourcePath) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Missing resource: " + resourcePath);
            return OBJECT_MAPPER.readTree(inputStream);
        }
    }
}
