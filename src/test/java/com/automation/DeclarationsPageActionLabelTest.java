package com.automation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeclarationsPageActionLabelTest {

    @Test
    void declarationActionLabelsIncludeRouteSpecificCreateButtons() throws Exception {
        DeclarationsPage declarationsPage = new DeclarationsPage(null);
        Method method = DeclarationsPage.class.getDeclaredMethod("declarationActionLabels", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> iptLabels = (List<String>) method.invoke(
                declarationsPage,
                "http://localhost:8081/declarations/ipt");
        @SuppressWarnings("unchecked")
        List<String> outLabels = (List<String>) method.invoke(declarationsPage, "/declarations/out");
        @SuppressWarnings("unchecked")
        List<String> cooLabels = (List<String>) method.invoke(declarationsPage, "/declarations/coo");

        assertTrue(iptLabels.contains("NEW DECLARATION"));
        assertTrue(iptLabels.contains("NEW IPT DECLARATION"));
        assertTrue(outLabels.contains("NEW OUT DECLARATION"));
        assertTrue(cooLabels.contains("NEW COO DECLARATION"));
        assertFalse(iptLabels.contains("IPTUPD"));
        assertFalse(outLabels.contains("OUTUPD"));
    }
}
