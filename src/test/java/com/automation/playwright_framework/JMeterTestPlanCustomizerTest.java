package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JMeterTestPlanCustomizerTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesRuntimeConfigurationToTestPlan() throws Exception {
        Path source = Path.of("jmeter", "performance-test.jmx").toAbsolutePath().normalize();
        Path output = tempDir.resolve("custom-plan.jmx");

        new JMeterTestPlanCustomizer().customize(
                source,
                output,
                new JMeterExecutionService.RunRequest(
                        "https://example.org:8443",
                        125,
                        25,
                        9,
                        120,
                        "coo",
                        "coo-batch-submit",
                        "COO/COO-PERMIT.json"));

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Files.newInputStream(output));
        document.getDocumentElement().normalize();

        assertEquals("125", firstValue(document, "intProp", "ThreadGroup.num_threads"));
        assertEquals("25", firstValue(document, "intProp", "ThreadGroup.ramp_time"));
        assertEquals("9", firstValue(document, "intProp", "LoopController.loops"));
        assertEquals("true", firstValue(document, "boolProp", "ThreadGroup.scheduler"));
        assertEquals("120", firstValue(document, "stringProp", "ThreadGroup.duration"));
        assertEquals("https", firstValue(document, "stringProp", "HTTPSampler.protocol"));
        assertEquals("example.org", firstValue(document, "stringProp", "HTTPSampler.domain"));
        assertEquals("8443", firstValue(document, "stringProp", "HTTPSampler.port"));
        assertEquals("coo", firstArgumentValue(document, "DECLARATION_TYPE"));
        assertEquals("coo-batch-submit", firstArgumentValue(document, "REPORT_PREFIX"));
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

    private String firstArgumentValue(Document document, String argumentName) {
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
        NodeList children = element.getElementsByTagName("stringProp");
        for (int index = 0; index < children.getLength(); index++) {
            var child = children.item(index);
            if (child instanceof org.w3c.dom.Element childElement
                    && propName.equals(childElement.getAttribute("name"))) {
                return childElement.getTextContent();
            }
        }
        return null;
    }
}
