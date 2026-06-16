package com.automation.playwright_framework;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class JMeterTestPlanCustomizer {

    Path customize(Path sourcePlan, Path outputPlan, JMeterExecutionService.RunRequest request) throws Exception {
        try (InputStream inputStream = Files.newInputStream(sourcePlan)) {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(inputStream);
            document.getDocumentElement().normalize();

            applyTargetUrl(document, request.targetUrl());
            applyLoadProfile(document, request.threads(), request.rampUpSeconds(), request.loopCount(), request.durationSeconds());
            applyWorkflowContext(document, request);

            Files.createDirectories(outputPlan.getParent());
            try (OutputStream outputStream = Files.newOutputStream(outputPlan)) {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.transform(new DOMSource(document), new StreamResult(outputStream));
            }
        }

        return outputPlan;
    }

    private void applyTargetUrl(Document document, String targetUrl) {
        URI targetUri = URI.create(targetUrl);
        String scheme = valueOrDefault(targetUri.getScheme(), "http");
        String host = valueOrDefault(targetUri.getHost(), "localhost");
        String port = targetUri.getPort() > 0 ? Integer.toString(targetUri.getPort()) : "";

        updateStringProp(document, "HTTPSampler.protocol", scheme);
        updateStringProp(document, "HTTPSampler.domain", host);
        updateStringProp(document, "HTTPSampler.port", port);

        NodeList arguments = document.getElementsByTagName("elementProp");
        for (int index = 0; index < arguments.getLength(); index++) {
            Node node = arguments.item(index);
            if (!(node instanceof Element element)) {
                continue;
            }
            if (!"Argument".equals(element.getAttribute("elementType"))) {
                continue;
            }
            Element nameProp = findChildProp(element, "stringProp", "Argument.name");
            Element valueProp = findChildProp(element, "stringProp", "Argument.value");
            if (nameProp == null || valueProp == null) {
                continue;
            }

            String argumentName = nameProp.getTextContent().trim().toUpperCase(Locale.ROOT);
            switch (argumentName) {
                case "HOST", "SERVER", "DOMAIN" -> valueProp.setTextContent(host);
                case "PORT" -> valueProp.setTextContent(port);
                case "PROTOCOL", "SCHEME" -> valueProp.setTextContent(scheme);
                case "TARGET_URL", "BASE_URL" -> valueProp.setTextContent(targetUrl);
                default -> {
                }
            }
        }
    }

    private void applyLoadProfile(Document document, int threads, int rampUpSeconds, int loopCount, int durationSeconds) {
        NodeList threadGroups = document.getElementsByTagName("ThreadGroup");
        for (int index = 0; index < threadGroups.getLength(); index++) {
            Node node = threadGroups.item(index);
            if (!(node instanceof Element threadGroup)) {
                continue;
            }

            setChildProp(threadGroup, "intProp", "ThreadGroup.num_threads", Integer.toString(threads));
            setChildProp(threadGroup, "intProp", "ThreadGroup.ramp_time", Integer.toString(rampUpSeconds));
            setChildProp(threadGroup, "boolProp", "ThreadGroup.scheduler", Boolean.toString(durationSeconds > 0));
            setChildProp(threadGroup, "stringProp", "ThreadGroup.duration", durationSeconds > 0 ? Integer.toString(durationSeconds) : "");

            NodeList loopControllers = threadGroup.getElementsByTagName("elementProp");
            for (int loopIndex = 0; loopIndex < loopControllers.getLength(); loopIndex++) {
                Node loopNode = loopControllers.item(loopIndex);
                if (!(loopNode instanceof Element loopController)) {
                    continue;
                }
                if (!"LoopController".equals(loopController.getAttribute("elementType"))) {
                    continue;
                }
                setChildProp(loopController, "boolProp", "LoopController.continue_forever", "false");
                setChildProp(loopController, "intProp", "LoopController.loops", Integer.toString(loopCount));
            }
        }
    }

    private void applyWorkflowContext(Document document, JMeterExecutionService.RunRequest request) {
        NodeList arguments = document.getElementsByTagName("elementProp");
        for (int index = 0; index < arguments.getLength(); index++) {
            Node node = arguments.item(index);
            if (!(node instanceof Element element)) {
                continue;
            }
            if (!"Argument".equals(element.getAttribute("elementType"))) {
                continue;
            }

            Element nameProp = findChildProp(element, "stringProp", "Argument.name");
            Element valueProp = findChildProp(element, "stringProp", "Argument.value");
            if (nameProp == null || valueProp == null) {
                continue;
            }

            String argumentName = nameProp.getTextContent().trim().toUpperCase(Locale.ROOT);
            switch (argumentName) {
                case "DECLARATION_TYPE" -> valueProp.setTextContent(valueOrDefault(request.declarationType(), "ipt"));
                case "REPORT_PREFIX" -> valueProp.setTextContent(valueOrDefault(request.reportPrefix(), "ipt-batch-submit"));
                case "SELECTED_JSON" -> valueProp.setTextContent(valueOrDefault(request.selectedJson(), ""));
                default -> {
                }
            }
        }
    }

    private void updateStringProp(Document document, String propName, String value) {
        NodeList props = document.getElementsByTagName("stringProp");
        for (int index = 0; index < props.getLength(); index++) {
            Node node = props.item(index);
            if (!(node instanceof Element prop)) {
                continue;
            }
            if (propName.equals(prop.getAttribute("name"))) {
                prop.setTextContent(value);
            }
        }
    }

    private Element findChildProp(Element parent, String tagName, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (!(node instanceof Element child)) {
                continue;
            }
            if (tagName.equals(child.getTagName()) && name.equals(child.getAttribute("name"))) {
                return child;
            }
        }
        return null;
    }

    private void setChildProp(Element parent, String tagName, String name, String value) {
        Element prop = findChildProp(parent, tagName, name);
        if (prop == null) {
            prop = parent.getOwnerDocument().createElement(tagName);
            prop.setAttribute("name", name);
            parent.appendChild(prop);
        }
        prop.setTextContent(value);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
