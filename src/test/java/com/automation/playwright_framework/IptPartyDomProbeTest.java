package com.automation.playwright_framework;

import base.BaseTest;
import com.automation.DeclarationsPage;
import com.automation.LoginPage;
import org.junit.jupiter.api.Test;

public class IptPartyDomProbeTest extends BaseTest {

    @Test
    void probePartySelectors() {
        LoginPage loginPage = new LoginPage(page);
        DeclarationsPage declarationsPage = new DeclarationsPage(page);
        String route = System.getProperty("tradenix.declaration.route", "/declarations/ipt");
        String menuLabel = System.getProperty("tradenix.declaration.menu.label", "In-Payment (IPT)");

        loginPage.navigate(System.getProperty(
                "tradenix.login.url",
                "http://ec2-18-141-176-151.ap-southeast-1.compute.amazonaws.com/auth/login?returnUrl=%2Fdashboard"));
        loginPage.loginAsUser(
                System.getProperty("tradenix.user.username", "mohan"),
                System.getProperty("tradenix.user.password", "12345678"),
                System.getProperty("tradenix.user.forwarder", "ADATACOMPANY PTE.LTD"),
                System.getProperty("tradenix.user.department", "IMPORT"));
        loginPage.waitForAuthenticatedState();

        declarationsPage.autoAcceptUnsavedChanges();
        declarationsPage.openDeclarationList(menuLabel, route);
        DeclarationsPage.DeclarationListEntry latestEntry = declarationsPage.readLatestDeclarationListEntry();
        if (latestEntry == null || latestEntry.jobId() == null || latestEntry.jobId().isBlank()) {
            throw new IllegalStateException("Unable to resolve latest IPT declaration job id for DOM probe.");
        }

        String currentUrl = page.url();
        int routeIndex = currentUrl.indexOf(route);
        String baseUrl = routeIndex >= 0 ? currentUrl.substring(0, routeIndex) : currentUrl;
        page.navigate(baseUrl + route + "/edit/" + latestEntry.jobId());
        page.waitForURL("**" + route + "/edit/*");
        page.locator("text=Party Info (P)").click();
        page.waitForTimeout(2000);

        Object dump = page.evaluate("""
                () => {
                    const selectors = [
                        'app-importer-lookup',
                        'app-inward-carrier-lookup',
                        'app-freight-forwarder-lookup',
                        'app-declaring-agent-lookup',
                        'app-importer-lookup[formcontrolname="name"]',
                        'app-inward-carrier-lookup[formcontrolname="name"]',
                        'app-freight-forwarder-lookup[formcontrolname="name"]',
                        'app-declaring-agent-lookup[formcontrolname="name"]',
                        '[formcontrolname]'
                    ];
                    const selectorDump = selectors.map(selector => ({
                        selector,
                        count: document.querySelectorAll(selector).length,
                        matches: Array.from(document.querySelectorAll(selector)).slice(0, 10).map(element => ({
                            tag: element.tagName,
                            formcontrolname: element.getAttribute('formcontrolname'),
                            id: element.id,
                            classes: element.className,
                            text: (element.innerText || element.textContent || '').replace(/\\s+/g, ' ').trim().slice(0, 160),
                            nestedInputs: Array.from(element.querySelectorAll('input, textarea, select, [role="combobox"], [role="textbox"]'))
                                .map(input => ({
                                    tag: input.tagName,
                                    id: input.id,
                                    formcontrolname: input.getAttribute('formcontrolname'),
                                    type: input.getAttribute('type'),
                                    classes: input.className,
                                    value: input.value || '',
                                    visible: !!(input.offsetWidth || input.offsetHeight || input.getClientRects().length)
                                }))
                        }))
                    }));

                    const normalize = value => (value || '').replace(/\\s+/g, ' ').trim();
                    const isVisible = element => !!element && !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
                    const partySection = Array.from(document.querySelectorAll('section, div, article, form'))
                        .filter(isVisible)
                        .find(element => normalize(element.innerText || element.textContent || '').includes('Party Name Name UEN Importer Inward Carrier'));

                    const fieldRows = Array.from((partySection || document).querySelectorAll('input, textarea, select, [role="combobox"], [role="textbox"]'))
                        .filter(isVisible)
                        .map(element => {
                            const parentText = normalize(
                                element.closest('tr, [role="row"], .row, .form-group, .clr-row, div')
                                    ?.innerText || element.parentElement?.innerText || '');
                            return {
                                tag: element.tagName,
                                id: element.id,
                                formcontrolname: element.getAttribute('formcontrolname'),
                                type: element.getAttribute('type'),
                                classes: element.className,
                                value: element.value || '',
                                parentText: parentText.slice(0, 200)
                            };
                        });

                    return {
                        selectors: selectorDump,
                        partySectionHtml: partySection ? partySection.outerHTML.slice(0, 12000) : '',
                        fieldRows
                    };
                }
                """);

        System.out.println(String.valueOf(dump));
    }
}
