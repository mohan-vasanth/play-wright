package com.automation.playwright_framework;

import com.automation.IptDeclarationPage;
import com.automation.OutDeclarationPage;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeclarationSubmitFlagTest {

    @Test
    void iptDefaultsToSubmitWhenFlagIsMissing() throws Exception {
        assertTrue(invokeShouldSubmit(new IptDeclarationPage(null), payloadWithoutSubmitFlag()));
    }

    @Test
    void iptHonorsExplicitDraftOnlyFlag() throws Exception {
        assertFalse(invokeShouldSubmit(new IptDeclarationPage(null), payloadWithSubmitFlag(false)));
    }

    @Test
    void outDefaultsToSubmitWhenFlagIsMissing() throws Exception {
        assertTrue(invokeShouldSubmit(new OutDeclarationPage(null), payloadWithoutSubmitFlag()));
    }

    @Test
    void outHonorsExplicitDraftOnlyFlag() throws Exception {
        assertFalse(invokeShouldSubmit(new OutDeclarationPage(null), payloadWithSubmitFlag(false)));
    }

    private boolean invokeShouldSubmit(Object pageObject, ObjectNode payload) throws Exception {
        Method method = pageObject.getClass().getDeclaredMethod("shouldSubmitDeclaration", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(pageObject, payload);
    }

    private ObjectNode payloadWithoutSubmitFlag() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.set("summary", JsonNodeFactory.instance.objectNode());
        root.set("formMetaData", JsonNodeFactory.instance.objectNode());
        return root;
    }

    private ObjectNode payloadWithSubmitFlag(boolean submit) {
        ObjectNode root = payloadWithoutSubmitFlag();
        ((ObjectNode) root.get("summary")).put("submitDeclaration", submit);
        return root;
    }
}
