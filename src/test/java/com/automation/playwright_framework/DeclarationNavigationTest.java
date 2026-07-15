package com.automation.playwright_framework;

import base.BaseTest;
import base.TradenixLiveTest;
import com.automation.DeclarationsPage;
import com.automation.LoginPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TradenixLiveTest
public class DeclarationNavigationTest extends BaseTest {

    private static final String LOGIN_URL = System.getProperty(
            "tradenix.login.url",
            "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard");
    private static final String USER_USERNAME = System.getProperty("tradenix.user.username", "mohan");
    private static final String USER_PASSWORD = System.getProperty("tradenix.user.password", "12345678");
    private static final String USER_FORWARDER = System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD");
    private static final String USER_DEPARTMENT = System.getProperty("tradenix.user.department", "IMPORT");

    private static final List<DeclarationType> DECLARATION_TYPES = List.of(
            new DeclarationType("In-Payment (IPT)", "/declarations/ipt"),
            new DeclarationType("In-Non-Payment (INP)", "/declarations/inp"),
            new DeclarationType("Transhipment (TNP)", "/declarations/tnp"),
            new DeclarationType("Out Payment (OUT)", "/declarations/out"),
            new DeclarationType("Certificate of Origin (COO)", "/declarations/coo"));

    @Test
    public void userCanOpenNewDeclarationForAllDeclarationTypes() {
        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);

        loginPage.navigate(LOGIN_URL);
        loginPage.loginAsUser(USER_USERNAME, USER_PASSWORD, USER_FORWARDER, USER_DEPARTMENT);
        loginPage.waitForAuthenticatedState();

        declarationsPage.autoAcceptUnsavedChanges();

        for (DeclarationType declarationType : DECLARATION_TYPES) {
            declarationsPage.openDeclarationList(declarationType.menuLabel(), declarationType.route());
            declarationsPage.createNewDeclarationDraft(declarationType.route());
            assertTrue(page.url().contains(declarationType.route() + "/edit/"));
        }
    }

    private record DeclarationType(String menuLabel, String route) {
    }
}
