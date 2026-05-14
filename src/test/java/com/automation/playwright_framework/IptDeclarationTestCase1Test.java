package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.DeclarationsPage;
import com.automation.IptDeclarationPage;
import com.automation.LoginPage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

public class IptDeclarationTestCase1Test extends BaseTest {

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-52-74-80-143.ap-southeast-1.compute.amazonaws.com:9000/auth/login");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "mohan");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "12345678");
    private static final String USER_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD");
    private static final String USER_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");
    private static final String IPT_ROUTE = "/declarations/ipt";
    private static final String IPT_MENU_LABEL = "In-Payment (IPT)";
    private static final String TEST_DATA_RESOURCE = "data/ipt-declaration-test-case-1.json";

    @Test
    void submitIptDeclarationTestCase1UsingJsonData() {
        JsonNode testData = IptDeclarationTestDataLoader.load(TEST_DATA_RESOURCE);

        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        IptDeclarationPage iptDeclarationPage = new IptDeclarationPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);
        page.waitForURL("**/dashboard");

        declarationsPage.autoAcceptUnsavedChanges();
        declarationsPage.openDeclarationList(IPT_MENU_LABEL, IPT_ROUTE);
        declarationsPage.createNewDeclarationDraft(IPT_ROUTE);

        iptDeclarationPage.populateFrom(testData);
        iptDeclarationPage.openInvoiceInfoSection();
        page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                .setFullPage(true)
                .setPath(Paths.get("target", "invoice-supplier-manufacturer-verification.png")));
        Assertions.assertEquals("NAME", iptDeclarationPage.readSupplierManufacturerNameValue());
    }
}
