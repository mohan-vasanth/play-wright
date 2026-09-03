package com.automation.playwright_framework;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProvisioningWorkbookTest {

    @Test
    void createsAndParsesTheExcelUserTemplate() throws Exception {
        byte[] template = UserProvisioningWorkbook.createTemplate();
        Map<String, byte[]> entries = unzip(template);
        String sheet = new String(entries.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
        assertTrue(sheet.contains("organizationRole"));

        String row = "<row r=\"2\">"
                + inlineCell("A2", "loadtest51")
                + inlineCell("B2", "pwd123456")
                + inlineCell("C2", "loadtest51@email.com")
                + inlineCell("D2", "DECLARANT")
                + inlineCell("E2", "ADATACOMPANY PTE. LTD.")
                + inlineCell("F2", "IMPORT")
                + "</row>";
        entries.put("xl/worksheets/sheet1.xml", sheet.replace("</sheetData>", row + "</sheetData>").getBytes(StandardCharsets.UTF_8));

        List<UserProvisioningService.UserInput> users = UserProvisioningWorkbook.parse(zip(entries));

        assertEquals(1, users.size());
        assertEquals("loadtest51", users.get(0).username());
        assertEquals("IMPORT", users.get(0).department());
    }

    @Test
    void exportsCreatedUsersWithCredentialsAndLoginAttributes() {
        byte[] workbook = UserProvisioningWorkbook.exportUsers(List.of(
                new ProvisionedLoadTestUserPool.LoadTestUser(
                        "loadtest51", "pwd123456", "loadtest51@email.com", "DECLARANT",
                        "ADATACOMPANY PTE. LTD.", "IMPORT", Instant.now())));

        List<UserProvisioningService.UserInput> users = UserProvisioningWorkbook.parse(workbook);

        assertEquals(1, users.size());
        assertEquals("pwd123456", users.get(0).password());
        assertEquals("ADATACOMPANY PTE. LTD.", users.get(0).forwarder());
        assertEquals("IMPORT", users.get(0).department());
    }

    private Map<String, byte[]> unzip(byte[] content) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private String inlineCell(String reference, String value) {
        return "<c r=\"" + reference + "\" t=\"inlineStr\"><is><t>" + value + "</t></is></c>";
    }
}
