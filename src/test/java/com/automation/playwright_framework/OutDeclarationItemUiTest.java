package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.OutDeclarationPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutDeclarationItemUiTest extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void fillItemInfoUsesItemInvoiceNumberLabelAndScopedItemValueFields() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="item-section" style="padding:12px; border:1px solid #ccc; width:1200px;">
                    <h2>Items (I)</h2>
                    <div id="item-details-section" style="margin-top:16px;">
                      <div>Item Details</div>
                      <div>
                        <label>Item Invoice Number</label>
                        <input id="item-invoice-number" type="text">
                      </div>
                      <div>
                        <label>Exchange Rate</label>
                        <input id="item-exchange-rate" type="text">
                      </div>
                      <div>
                        <label>HS Code</label>
                        <input id="item-hs-code" type="text">
                      </div>
                      <div>
                        <label>Description</label>
                        <input id="item-description" type="text">
                      </div>
                      <div>
                        <label>COO</label>
                        <input id="item-coo" type="text">
                      </div>
                    </div>
                    <div>
                    <div id="item-values-section" style="margin-top:16px;">
                      <div>Item Values</div>
                      <div style="display:grid; grid-template-columns:180px 220px 180px 220px; gap:12px; align-items:center;">
                        <div>Item Value</div>
                        <input id="item-value-amount" type="text">
                        <div>Currency</div>
                        <input id="item-value-currency" type="text">
                      </div>
                    </div>
                    <div id="item-quantity-section" style="margin-top:16px;">
                      <div>Item Quantity</div>
                      <div style="display:grid; grid-template-columns:180px 220px 180px 220px; gap:12px; align-items:center;">
                        <div>HS Quantity</div>
                        <input id="item-hs-quantity" type="text">
                        <div>Unit</div>
                        <input id="item-hs-quantity-unit" type="text">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillItemInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "invoice": [
                            {
                              "invoiceNumber": "INV-1001"
                            }
                          ],
                          "item": [
                            {
                              "itemInvoiceNumber": "INV-1001",
                              "itemHarmonizedSystemCode": "39269099",
                              "goodsDescription": "PLASTIC ARTICLES",
                              "originCountry": "SG",
                              "transactionValue": {
                                "unitPriceValue": {
                                  "amount": {
                                    "value": "1000",
                                    "currencyID": "TWD"
                                  },
                                  "exchangeRate": "4.3643"
                                }
                              }
                            }
                          ],
                          "formMetaData": {}
                        }
                        """));

        assertEquals("INV-1001", page.locator("#item-invoice-number").inputValue());
        assertEquals("4.3643", page.locator("#item-exchange-rate").inputValue());
        assertEquals("39269099", page.locator("#item-hs-code").inputValue());
        assertEquals("PLASTIC ARTICLES", page.locator("#item-description").inputValue());
        assertEquals("SG", page.locator("#item-coo").inputValue());
        assertEquals("1000", page.locator("#item-value-amount").inputValue());
        assertEquals("TWD", page.locator("#item-value-currency").inputValue());
    }

    @Test
    void fillItemInfoSkipsPlaceholderHsCaLookupValues() throws Exception {
        page.setContent("""
                <html>
                <body>
                  <section id="item-section" style="padding:12px; border:1px solid #ccc; width:1200px;">
                    <h2>Items (I)</h2>
                    <div id="item-details-section" style="margin-top:16px;">
                      <div>Item Details</div>
                      <div>
                        <label>Item Invoice Number</label>
                        <input id="item-invoice-number" type="text">
                      </div>
                      <div>
                        <label>HS Code</label>
                        <input id="item-hs-code" type="text">
                      </div>
                    </div>
                    <div id="item-quantity-section" style="margin-top:16px;">
                      <div>Item Quantity</div>
                      <div style="display:grid; grid-template-columns:180px 220px 180px 220px; gap:12px; align-items:center;">
                        <div>HS Quantity</div>
                        <input id="item-hs-quantity" type="text">
                        <div>Unit</div>
                        <input id="item-hs-quantity-unit" type="text">
                      </div>
                    </div>
                    <div id="item-hs-ca-section" style="margin-top:16px;">
                      <div>
                        <label>HS Import CA</label>
                        <input id="hs-import-ca" type="text">
                      </div>
                      <div>
                        <label>HS Export CA</label>
                        <input id="hs-export-ca" type="text">
                      </div>
                      <div>
                        <label>HS Transhipment CA</label>
                        <input id="hs-transhipment-ca" type="text">
                      </div>
                    </div>
                  </section>
                </body>
                </html>
                """);

        OutDeclarationPage declarationPage = new OutDeclarationPage(page);
        Method method = OutDeclarationPage.class.getDeclaredMethod("fillItemInfo", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        method.invoke(
                declarationPage,
                OBJECT_MAPPER.readTree("""
                        {
                          "invoice": [
                            {
                              "invoiceNumber": "INV-1001"
                            }
                          ],
                          "item": [
                            {
                              "itemInvoiceNumber": "INV-1001",
                              "itemHarmonizedSystemCode": "39269099",
                              "itemQuantity": {
                                "hsQuantity": {
                                  "value": "80",
                                  "unitCode": "KGM"
                                }
                              },
                              "hsImportCa": "-",
                              "hsExportCa": "-",
                              "hsTranshipmentCa": "-"
                            }
                          ],
                          "formMetaData": {}
                        }
                        """));

        assertEquals("", page.locator("#hs-import-ca").inputValue());
        assertEquals("", page.locator("#hs-export-ca").inputValue());
        assertEquals("", page.locator("#hs-transhipment-ca").inputValue());
    }
}
