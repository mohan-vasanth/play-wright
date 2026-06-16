package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DynamicJmxBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void writesThreadAndTargetConfigurationIntoPlan() throws Exception {
        Path output = tempDir.resolve("dynamic-plan.jmx");
        new DynamicJmxBuilder().writePlan(output, "https://example.org:8443/app/home?x=1", 250, 25, 3);

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Files.newInputStream(output));
        document.getDocumentElement().normalize();

        assertEquals("250", firstValue(document, "intProp", "ThreadGroup.num_threads"));
        assertEquals("25", firstValue(document, "intProp", "ThreadGroup.ramp_time"));
        assertEquals("3", firstValue(document, "intProp", "LoopController.loops"));
        assertEquals("/app/home?x=1", firstValue(document, "stringProp", "HTTPSampler.path"));
        assertEquals("example.org", argumentValue(document, "HOST"));
        assertEquals("8443", argumentValue(document, "PORT"));
        assertEquals("https", argumentValue(document, "PROTOCOL"));
        assertEquals("200", firstValue(document, "stringProp", "response-code-200"));
        assertEquals("8", firstValue(document, "intProp", "Assertion.test_type"));
        assertNull(firstValue(document, "stringProp", "filename"));
    }

    private String firstValue(Document document, String tagName, String propName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        for (int index = 0; index < nodes.getLength(); index++) {
            var node = nodes.item(index);
            if (node instanceof org.w3c.dom.Element element && propName.equals(element.getAttribute("name"))) {
                return element.getTextContent();
            }
        }
        return null;
    }

    private String argumentValue(Document document, String argumentName) {
        NodeList nodes = document.getElementsByTagName("elementProp");
        for (int index = 0; index < nodes.getLength(); index++) {
            var node = nodes.item(index);
            if (!(node instanceof org.w3c.dom.Element element)
                    || !"Argument".equals(element.getAttribute("elementType"))) {
                continue;
            }
            String currentName = childValue(element, "Argument.name");
            if (argumentName.equals(currentName)) {
                return childValue(element, "Argument.value");
            }
        }
        return null;
    }

    private String childValue(org.w3c.dom.Element element, String propName) {
        NodeList nodes = element.getElementsByTagName("stringProp");
        for (int index = 0; index < nodes.getLength(); index++) {
            var node = nodes.item(index);
            if (node instanceof org.w3c.dom.Element child && propName.equals(child.getAttribute("name"))) {
                return child.getTextContent();
            }
        }
        return null;
    }
}
