package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.CooDeclarationPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class CooDeclarationFieldResolverTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void fillsScopedConcreteInputWhenRowContainsWrapperElement() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="coo-scope" style="width: 520px; padding: 12px; border: 1px solid #ccc;">
                    <div style="display: grid; grid-template-columns: 180px 1fr; align-items: center; margin-bottom: 12px;">
                      <div>Item Invoice Number</div>
                      <div role="textbox" style="display: block; width: 260px; height: 32px; border: 1px solid #999; padding: 2px;">
                        <input id="item-invoice-number" type="text" style="width: 240px; height: 24px;">
                      </div>
                    </div>
                    <div style="display: grid; grid-template-columns: 180px 1fr; align-items: center;">
                      <div>Certificate Item Description</div>
                      <div role="textbox" style="display: block; width: 260px; height: 60px; border: 1px solid #999; padding: 2px;">
                        <textarea id="certificate-item-description" style="width: 240px; height: 48px;"></textarea>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """);

        CooDeclarationPage declarationPage = new CooDeclarationPage(page);
        Method method = CooDeclarationPage.class.getDeclaredMethod(
                "fillScopedFieldAfterScopeLabelIfPresent",
                Locator.class,
                String.class,
                int.class,
                String.class);
        method.setAccessible(true);

        Locator scope = page.locator("#coo-scope");
        method.invoke(declarationPage, scope, "Item Invoice Number", 0, "INV-991011");
        method.invoke(declarationPage, scope, "Certificate Item Description", 0, "FLAVOUR");

        assertEquals("INV-991011", page.locator("#item-invoice-number").inputValue());
        assertEquals("FLAVOUR", page.locator("#certificate-item-description").inputValue());
    }

    @Test
    void fillsItemCertificateUsingOrderedLayoutFallback() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="coo-scope" style="width: 720px; padding: 12px; border: 1px solid #ccc;">
                    <div>Certificate of Origin (CO)</div>
                    <div>Certificate Quantity</div>
                    <input id="certificate-quantity" type="text">
                    <select id="certificate-uom">
                      <option value=""></option>
                      <option value="KGM">KGM</option>
                    </select>
                    <div>Certificate Item Value</div>
                    <input id="certificate-item-value" type="text">
                    <div>Manufacturing Cost Date</div>
                    <input id="manufacturing-cost-date" type="text">
                    <div>Item Invoice Number</div>
                    <input id="item-invoice-number" type="text">
                    <div>Item Invoice Date</div>
                    <input id="item-invoice-date" type="text">
                    <div>HS Code</div>
                    <input id="hs-code" type="text">
                    <div>Content Percent</div>
                    <input id="content-percent" type="text">
                    <div>Certificate Item Description</div>
                    <textarea id="certificate-item-description"></textarea>
                    <div>Origin Criterion 1</div>
                    <input id="origin-criterion-1" type="text">
                    <div>Origin Criterion 2</div>
                    <input id="origin-criterion-2" type="text">
                    <div>Origin Criterion 3</div>
                    <input id="origin-criterion-3" type="text">
                  </div>
                </body>
                </html>
                """);

        JsonNode itemCertificate = OBJECT_MAPPER.readTree("""
                {
                  "itemCertificateQuantity": {
                    "value": "960",
                    "unitCode": "KGM"
                  },
                  "itemValue": "1000",
                  "itemCertificateDescription": [
                    {
                      "line": ["FLAVOUR"]
                    }
                  ],
                  "manufacturingCostDate": "20260326",
                  "itemInvoiceNumber": "991011",
                  "itemInvoiceDate": "20260525",
                  "originCriterion": ["CTH"],
                  "harmonizedSystemCode": "330290",
                  "contentPercent": "45"
                }
                """);

        CooDeclarationPage declarationPage = new CooDeclarationPage(page);
        Method method = CooDeclarationPage.class.getDeclaredMethod(
                "fillItemCertificateByLayoutFallback",
                Locator.class,
                JsonNode.class);
        method.setAccessible(true);

        method.invoke(declarationPage, page.locator("#coo-scope"), itemCertificate);

        assertEquals("CTH", page.locator("#origin-criterion-1").inputValue());
        assertEquals("1000", page.locator("#certificate-item-value").inputValue());
        assertEquals("26-03-2026", page.locator("#manufacturing-cost-date").inputValue());
        assertEquals("991011", page.locator("#item-invoice-number").inputValue());
        assertEquals("25-05-2026", page.locator("#item-invoice-date").inputValue());
        assertEquals("330290", page.locator("#hs-code").inputValue());
        assertEquals("45", page.locator("#content-percent").inputValue());
        assertEquals("FLAVOUR", page.locator("#certificate-item-description").inputValue());
    }

    @Test
    void continuesItemCertificateScopedEntryWhenPageLabelFallbackOnlyPartiallyMatches() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="coo-scope" style="width: 720px; padding: 12px; border: 1px solid #ccc;">
                    <div>Certificate of Origin (CO)</div>
                    <div class="row">
                      <div>Certificate Quantity</div>
                      <input id="certificate-quantity" type="text">
                      <select id="certificate-uom">
                        <option value=""></option>
                        <option value="KGM">KGM</option>
                      </select>
                    </div>
                    <div class="row">
                      <div>Certificate Item Value Amount</div>
                      <input id="certificate-item-value" type="text">
                    </div>
                    <div class="row">
                      <div>Manufacturing Cost Date Value</div>
                      <input id="manufacturing-cost-date" type="text">
                    </div>
                    <div class="row">
                      <div>Item Invoice Number Ref</div>
                      <input id="item-invoice-number" type="text">
                    </div>
                    <div class="row">
                      <div>Item Invoice Date Value</div>
                      <input id="item-invoice-date" type="text">
                    </div>
                    <div class="row">
                      <div>Origin Criterion 1 Value</div>
                      <input id="origin-criterion-1" type="text">
                    </div>
                    <div class="row">
                      <div>Origin Criterion 2 Value</div>
                      <input id="origin-criterion-2" type="text">
                    </div>
                    <div class="row">
                      <div>Origin Criterion 3 Value</div>
                      <input id="origin-criterion-3" type="text">
                    </div>
                    <div class="row">
                      <div>Certificate Item Description</div>
                      <textarea id="certificate-item-description"></textarea>
                    </div>
                  </div>
                </body>
                </html>
                """);

        JsonNode itemCertificate = OBJECT_MAPPER.readTree("""
                {
                  "itemCertificateQuantity": {
                    "value": "960",
                    "unitCode": "KGM"
                  },
                  "itemValue": "1000",
                  "itemCertificateDescription": [
                    {
                      "line": ["FLAVOUR"]
                    }
                  ],
                  "manufacturingCostDate": "20260326",
                  "itemInvoiceNumber": "991011",
                  "itemInvoiceDate": "20260525",
                  "originCriterion": ["CTH"]
                }
                """);

        CooDeclarationPage declarationPage = new CooDeclarationPage(page);
        Method method = CooDeclarationPage.class.getDeclaredMethod(
                "fillItemCertificate",
                JsonNode.class,
                JsonNode.class);
        method.setAccessible(true);

        method.invoke(declarationPage, itemCertificate, OBJECT_MAPPER.createObjectNode());

        assertEquals("960", page.locator("#certificate-quantity").inputValue());
        assertEquals("KGM", page.locator("#certificate-uom").inputValue());
        assertEquals("1000", page.locator("#certificate-item-value").inputValue());
        assertEquals("26-03-2026", page.locator("#manufacturing-cost-date").inputValue());
        assertEquals("991011", page.locator("#item-invoice-number").inputValue());
        assertEquals("25-05-2026", page.locator("#item-invoice-date").inputValue());
        assertEquals("CTH", page.locator("#origin-criterion-1").inputValue());
        assertEquals("FLAVOUR", page.locator("#certificate-item-description").inputValue());
    }

    @Test
    void fillsPartyCertificateOfOriginLegendsUsingSharedOutFlow() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="party-scope" style="width: 720px; padding: 12px; border: 1px solid #ccc;">
                    <div>Certificate of Origin (CO)</div>
                    <div>Certificate Quantity</div>
                    <input id="certificate-quantity" type="text">
                    <select id="certificate-uom">
                      <option value=""></option>
                      <option value="KGM">KGM</option>
                    </select>
                    <div>Certificate Item Value</div>
                    <input id="certificate-item-value" type="text">
                    <div>Manufacturing Cost Date</div>
                    <input id="manufacturing-cost-date" type="text">
                    <div>Item Invoice Number</div>
                    <input id="item-invoice-number" type="text">
                    <div>Item Invoice Date</div>
                    <input id="item-invoice-date" type="text">
                    <div>HS Code</div>
                    <input id="hs-code" type="text">
                    <div>Content Percent (%)</div>
                    <input id="content-percent" type="text">
                    <div>Origin Criterion 1</div>
                    <input id="origin-criterion-1" type="text">
                    <div>Origin Criterion 2</div>
                    <input id="origin-criterion-2" type="text">
                    <div>Origin Criterion 3</div>
                    <input id="origin-criterion-3" type="text">
                    <div>Certificate Item Description</div>
                    <textarea id="certificate-item-description"></textarea>
                  </div>
                </body>
                </html>
                """);

        JsonNode itemCertificate = OBJECT_MAPPER.readTree("""
                {
                  "itemCertificateQuantity": {
                    "value": "960",
                    "unitCode": "KGM"
                  },
                  "itemValue": "1000",
                  "itemCertificateDescription": [
                    {
                      "line": ["FLAVOUR"]
                    }
                  ],
                  "manufacturingCostDate": "20260326",
                  "itemInvoiceNumber": "991011",
                  "itemInvoiceDate": "20260525",
                  "originCriterion": ["CTH"],
                  "harmonizedSystemCode": "330290",
                  "contentPercent": "45"
                }
                """);

        CooDeclarationPage declarationPage = new CooDeclarationPage(page);
        Method method = CooDeclarationPage.class.getDeclaredMethod(
                "fillPartyCertificateOfOriginLegends",
                JsonNode.class,
                JsonNode.class);
        method.setAccessible(true);

        method.invoke(declarationPage, itemCertificate, OBJECT_MAPPER.createObjectNode());

        assertEquals("960", page.locator("#certificate-quantity").inputValue());
        assertEquals("KGM", page.locator("#certificate-uom").inputValue());
        assertEquals("1000", page.locator("#certificate-item-value").inputValue());
        assertEquals("26-03-2026", page.locator("#manufacturing-cost-date").inputValue());
        assertEquals("991011", page.locator("#item-invoice-number").inputValue());
        assertEquals("25-05-2026", page.locator("#item-invoice-date").inputValue());
        assertEquals("330290", page.locator("#hs-code").inputValue());
        assertEquals("45", page.locator("#content-percent").inputValue());
        assertEquals("CTH", page.locator("#origin-criterion-1").inputValue());
        assertEquals("FLAVOUR", page.locator("#certificate-item-description").inputValue());
    }

    @Test
    void fallsBackToConcretePartyInputsWhenScopedSectionResolvesWrapperOnlyField() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="party-scope" style="width: 720px; padding: 12px; border: 1px solid #ccc;">
                    <div>Certificate of Origin (CO)</div>
                    <div>Certificate Quantity</div>
                    <input id="certificate-quantity" type="text">
                    <select id="certificate-uom">
                      <option value=""></option>
                      <option value="KGM">KGM</option>
                    </select>
                    <div>Certificate Item Value</div>
                    <div id="certificate-item-value-wrapper" role="textbox" style="display: block; width: 260px; min-height: 24px; border: 1px solid #999;"></div>
                    <input id="certificate-item-value" type="text">
                    <div>Manufacturing Cost Date</div>
                    <input id="manufacturing-cost-date" type="text">
                    <div>Item Invoice Number</div>
                    <input id="item-invoice-number" type="text">
                    <div>Item Invoice Date</div>
                    <input id="item-invoice-date" type="text">
                    <div>HS Code</div>
                    <input id="hs-code" type="text">
                    <div>Content Percent (%)</div>
                    <input id="content-percent" type="text">
                    <div>Origin Criterion 1</div>
                    <input id="origin-criterion-1" type="text">
                    <div>Origin Criterion 2</div>
                    <input id="origin-criterion-2" type="text">
                    <div>Origin Criterion 3</div>
                    <input id="origin-criterion-3" type="text">
                    <div>Certificate Item Description</div>
                    <textarea id="certificate-item-description"></textarea>
                  </div>
                </body>
                </html>
                """);

        JsonNode itemCertificate = OBJECT_MAPPER.readTree("""
                {
                  "itemCertificateQuantity": {
                    "value": "960",
                    "unitCode": "KGM"
                  },
                  "itemValue": "1000",
                  "itemCertificateDescription": [
                    {
                      "line": ["FLAVOUR"]
                    }
                  ],
                  "manufacturingCostDate": "20260326",
                  "itemInvoiceNumber": "991011",
                  "itemInvoiceDate": "20260525",
                  "originCriterion": ["CTH"],
                  "harmonizedSystemCode": "330290",
                  "contentPercent": "45"
                }
                """);

        CooDeclarationPage declarationPage = new CooDeclarationPage(page);
        Method method = CooDeclarationPage.class.getDeclaredMethod(
                "fillPartyCertificateOfOriginLegends",
                JsonNode.class,
                JsonNode.class);
        method.setAccessible(true);

        method.invoke(declarationPage, itemCertificate, OBJECT_MAPPER.createObjectNode());

        assertEquals("1000", page.locator("#certificate-item-value").inputValue());
        assertEquals("26-03-2026", page.locator("#manufacturing-cost-date").inputValue());
        assertEquals("991011", page.locator("#item-invoice-number").inputValue());
        assertEquals("25-05-2026", page.locator("#item-invoice-date").inputValue());
        assertEquals("330290", page.locator("#hs-code").inputValue());
        assertEquals("45", page.locator("#content-percent").inputValue());
        assertEquals("CTH", page.locator("#origin-criterion-1").inputValue());
        assertEquals("FLAVOUR", page.locator("#certificate-item-description").inputValue());
    }

    @Test
    void fillsPartyCertificateOfOriginLegendsWhenCooPartyTabHasOnlyLegendLabels() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="party-scope" style="width: 720px; padding: 12px; border: 1px solid #ccc;">
                    <div>Certificate Quantity</div>
                    <input id="certificate-quantity" type="text">
                    <select id="certificate-uom">
                      <option value=""></option>
                      <option value="KGM">KGM</option>
                    </select>
                    <div>Certificate Item Value</div>
                    <input id="certificate-item-value" type="text">
                    <div>Manufacturing Cost Date</div>
                    <input id="manufacturing-cost-date" type="text">
                    <div>Item Invoice Number</div>
                    <input id="item-invoice-number" type="text">
                    <div>Item Invoice Date</div>
                    <input id="item-invoice-date" type="text">
                    <div>HS Code</div>
                    <input id="hs-code" type="text">
                    <div>Content Percent</div>
                    <input id="content-percent" type="text">
                    <div>Origin Criterion 1</div>
                    <input id="origin-criterion-1" type="text">
                    <div>Origin Criterion 2</div>
                    <input id="origin-criterion-2" type="text">
                    <div>Origin Criterion 3</div>
                    <input id="origin-criterion-3" type="text">
                    <div>Certificate Item Description</div>
                    <textarea id="certificate-item-description"></textarea>
                  </div>
                </body>
                </html>
                """);

        JsonNode itemCertificate = OBJECT_MAPPER.readTree("""
                {
                  "itemCertificateQuantity": {
                    "value": "960",
                    "unitCode": "KGM"
                  },
                  "itemValue": "1000",
                  "itemCertificateDescription": [
                    {
                      "line": ["FLAVOUR"]
                    }
                  ],
                  "manufacturingCostDate": "20260326",
                  "itemInvoiceNumber": "991011",
                  "itemInvoiceDate": "20260525",
                  "originCriterion": ["CTH"],
                  "harmonizedSystemCode": "330290",
                  "contentPercent": "45"
                }
                """);

        CooDeclarationPage declarationPage = new CooDeclarationPage(page);
        Method method = CooDeclarationPage.class.getDeclaredMethod(
                "fillPartyCertificateOfOriginLegends",
                JsonNode.class,
                JsonNode.class);
        method.setAccessible(true);

        method.invoke(declarationPage, itemCertificate, OBJECT_MAPPER.createObjectNode());

        assertEquals("960", page.locator("#certificate-quantity").inputValue());
        assertEquals("KGM", page.locator("#certificate-uom").inputValue());
        assertEquals("1000", page.locator("#certificate-item-value").inputValue());
        assertEquals("26-03-2026", page.locator("#manufacturing-cost-date").inputValue());
        assertEquals("991011", page.locator("#item-invoice-number").inputValue());
        assertEquals("25-05-2026", page.locator("#item-invoice-date").inputValue());
        assertEquals("330290", page.locator("#hs-code").inputValue());
        assertEquals("45", page.locator("#content-percent").inputValue());
        assertEquals("CTH", page.locator("#origin-criterion-1").inputValue());
        assertEquals("FLAVOUR", page.locator("#certificate-item-description").inputValue());
    }

    @Test
    void skipsPartyCertificateOfOriginLegendsWhenPartyTabDoesNotExposeUi() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <div id="party-scope" style="width: 720px; padding: 12px; border: 1px solid #ccc;">
                    <div>Freight Forwarder</div>
                    <input id="freight-forwarder" type="text">
                    <div>Exporter</div>
                    <input id="exporter" type="text">
                  </div>
                </body>
                </html>
                """);

        JsonNode itemCertificate = OBJECT_MAPPER.readTree("""
                {
                  "itemCertificateQuantity": {
                    "value": "960",
                    "unitCode": "KGM"
                  },
                  "itemValue": "1000",
                  "itemCertificateDescription": [
                    {
                      "line": ["FLAVOUR"]
                    }
                  ],
                  "manufacturingCostDate": "20260326",
                  "itemInvoiceNumber": "991011",
                  "itemInvoiceDate": "20260525",
                  "originCriterion": ["CTH"]
                }
                """);

        CooDeclarationPage declarationPage = new CooDeclarationPage(page);
        Method method = CooDeclarationPage.class.getDeclaredMethod(
                "fillPartyCertificateOfOriginLegends",
                JsonNode.class,
                JsonNode.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(declarationPage, itemCertificate, OBJECT_MAPPER.createObjectNode()));
    }
}
