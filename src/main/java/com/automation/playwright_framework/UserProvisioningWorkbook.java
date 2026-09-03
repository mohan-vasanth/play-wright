package com.automation.playwright_framework;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Minimal .xlsx template writer and importer; avoids storing uploaded data on disk. */
final class UserProvisioningWorkbook {

    private static final List<String> HEADERS = List.of(
            "username", "password", "email", "organizationRole", "forwarder", "department");

    private UserProvisioningWorkbook() {
    }

    static byte[] createTemplate() {
        return createWorkbook(HEADERS, List.of());
    }

    static byte[] exportUsers(List<ProvisionedLoadTestUserPool.LoadTestUser> users) {
        List<List<String>> rows = users.stream()
                .map(user -> List.of(
                        user.username(), user.password(), user.email(), user.organizationRole(), user.forwarder(), user.department()))
                .toList();
        return createWorkbook(HEADERS, rows);
    }

    private static byte[] createWorkbook(List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>
                    """);
            writeEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            writeEntry(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="Users" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            writeEntry(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                    """);
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols>");
            for (int column = 1; column <= headers.size(); column++) {
                sheet.append("<col min=\"").append(column).append("\" max=\"").append(column)
                        .append("\" width=\"25\" customWidth=\"1\"/>");
            }
            sheet.append("</cols><sheetData><row r=\"1\">");
            for (int index = 0; index < headers.size(); index++) {
                sheet.append(inlineCell(columnName(index) + "1", headers.get(index)));
            }
            sheet.append("</row>");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                sheet.append("<row r=\"").append(rowIndex + 2).append("\">");
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    String value = columnIndex < row.size() && row.get(columnIndex) != null ? row.get(columnIndex) : "";
                    sheet.append(inlineCell(columnName(columnIndex) + (rowIndex + 2), value));
                }
                sheet.append("</row>");
            }
            sheet.append("</sheetData></worksheet>");
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create the user import template.", exception);
        }
    }

    static List<UserProvisioningService.UserInput> parse(byte[] workbookBytes) {
        if (workbookBytes == null || workbookBytes.length == 0) {
            throw new IllegalArgumentException("The uploaded Excel file is empty.");
        }
        if (workbookBytes.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("The uploaded Excel file must be 5 MB or smaller.");
        }
        Map<String, byte[]> entries = readEntries(workbookBytes);
        byte[] sheetBytes = entries.get("xl/worksheets/sheet1.xml");
        if (sheetBytes == null) {
            throw new IllegalArgumentException("The Excel workbook must contain a first worksheet.");
        }
        List<String> sharedStrings = readSharedStrings(entries.get("xl/sharedStrings.xml"));
        Document sheet = parseXml(sheetBytes);
        NodeList rows = sheet.getElementsByTagNameNS("*", "row");
        if (rows.getLength() < 2) {
            throw new IllegalArgumentException("The Excel workbook has no user rows. Download the template and add at least one row.");
        }
        Map<String, Integer> headerColumns = headerColumns((Element) rows.item(0), sharedStrings);
        for (String header : HEADERS) {
            if (!headerColumns.containsKey(normalizeHeader(header))) {
                throw new IllegalArgumentException("The Excel workbook is missing the required column: " + header);
            }
        }

        List<UserProvisioningService.UserInput> users = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.getLength(); rowIndex++) {
            Element row = (Element) rows.item(rowIndex);
            Map<Integer, String> values = rowValues(row, sharedStrings);
            List<String> ordered = HEADERS.stream()
                    .map(header -> values.getOrDefault(headerColumns.get(normalizeHeader(header)), "").trim())
                    .toList();
            if (ordered.stream().allMatch(String::isBlank)) {
                continue;
            }
            users.add(new UserProvisioningService.UserInput(
                    ordered.get(0), ordered.get(1), ordered.get(2), ordered.get(3), ordered.get(4), ordered.get(5)));
        }
        if (users.isEmpty()) {
            throw new IllegalArgumentException("The Excel workbook has no populated user rows.");
        }
        return List.copyOf(users);
    }

    private static Map<String, byte[]> readEntries(byte[] workbookBytes) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbookBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The uploaded file is not a valid .xlsx workbook.", exception);
        }
        return entries;
    }

    private static List<String> readSharedStrings(byte[] xml) {
        if (xml == null) {
            return List.of();
        }
        Document document = parseXml(xml);
        NodeList values = document.getElementsByTagNameNS("*", "si");
        List<String> strings = new ArrayList<>();
        for (int index = 0; index < values.getLength(); index++) {
            strings.add(values.item(index).getTextContent());
        }
        return strings;
    }

    private static Map<String, Integer> headerColumns(Element row, List<String> sharedStrings) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> cell : rowValues(row, sharedStrings).entrySet()) {
            headers.put(normalizeHeader(cell.getValue()), cell.getKey());
        }
        return headers;
    }

    private static Map<Integer, String> rowValues(Element row, List<String> sharedStrings) {
        Map<Integer, String> values = new LinkedHashMap<>();
        NodeList cells = row.getElementsByTagNameNS("*", "c");
        for (int index = 0; index < cells.getLength(); index++) {
            Element cell = (Element) cells.item(index);
            values.put(columnIndex(cell.getAttribute("r")), cellValue(cell, sharedStrings));
        }
        return values;
    }

    private static String cellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList text = cell.getElementsByTagNameNS("*", "t");
            return text.getLength() == 0 ? "" : text.item(0).getTextContent();
        }
        NodeList values = cell.getElementsByTagNameNS("*", "v");
        String value = values.getLength() == 0 ? "" : values.item(0).getTextContent();
        if ("s".equals(type) && !value.isBlank()) {
            int index = Integer.parseInt(value);
            return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
        }
        return value;
    }

    private static Document parseXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(new String(bytes, StandardCharsets.UTF_8))));
        } catch (Exception exception) {
            throw new IllegalArgumentException("The Excel workbook contains invalid worksheet data.", exception);
        }
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static int columnIndex(String reference) {
        int value = 0;
        for (int index = 0; index < reference.length(); index++) {
            char character = reference.charAt(index);
            if (!Character.isLetter(character)) {
                break;
            }
            value = value * 26 + (Character.toUpperCase(character) - 'A' + 1);
        }
        return Math.max(0, value - 1);
    }

    private static String columnName(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private static String inlineCell(String reference, String value) {
        return "<c r=\"" + reference + "\" t=\"inlineStr\"><is><t>"
                + escapeXml(value) + "</t></is></c>";
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.strip().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
