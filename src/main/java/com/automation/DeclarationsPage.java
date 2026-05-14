package com.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

public class DeclarationsPage {

    private final Page page;
    private static final String DECLARATIONS_MENU = "text=Declarations";
    private static final String NEW_DECLARATION_BUTTON = "button:has-text('NEW DECLARATION')";
    private static final String IPT_MENU_ITEM = "a:has-text('In-Payment (IPT)')";

    public DeclarationsPage(Page page) {
        this.page = page;
    }

    public void autoAcceptUnsavedChanges() {
        page.evaluate("window.confirm = () => true");
    }

    public void openDeclarationList(String menuLabel, String route) {
        expandDeclarationsMenuIfNeeded();
        Locator menuItem = page.locator("a:has-text('" + menuLabel + "')").first();
        menuItem.waitFor(new Locator.WaitForOptions().setTimeout(30000));
        menuItem.click();
        page.waitForURL("**" + route);
        waitForNewDeclarationButton();
    }

    public void createNewDeclarationDraft(String route) {
        clickNewDeclarationButton();
        page.waitForURL("**" + route + "/edit/*");
    }

    private void expandDeclarationsMenuIfNeeded() {
        if (!page.locator(IPT_MENU_ITEM).isVisible()) {
            page.locator(DECLARATIONS_MENU).first().click();
            page.locator(IPT_MENU_ITEM).first().waitFor(new Locator.WaitForOptions().setTimeout(30000));
        }
    }

    private void waitForNewDeclarationButton() {
        Locator button = resolveNewDeclarationButton();
        if (button != null) {
            button.waitFor(new Locator.WaitForOptions().setTimeout(30000));
            return;
        }

        try {
            page.waitForFunction("""
                    () => Array.from(document.querySelectorAll("button, [role='button'], a"))
                            .some(element => ((element.innerText || element.textContent || "").replace(/\\s+/g, " ").trim().toUpperCase()).includes("NEW DECLARATION"))
                    """);
        } catch (PlaywrightException exception) {
            System.out.println("DECLARATION_LIST_URL=" + page.url());
            System.out.println("DECLARATION_LIST_BODY=" + page.locator("body").innerText());
            throw new IllegalStateException("NEW DECLARATION button was not visible on declarations page.", exception);
        }
    }

    private void clickNewDeclarationButton() {
        Locator button = resolveNewDeclarationButton();
        if (button != null && button.isVisible()) {
            button.click();
            return;
        }
        

        Boolean clicked = (Boolean) page.evaluate("""
                () => {
                    const normalize = value => (value || "").replace(/\\s+/g, " ").trim().toUpperCase();
                    const target = Array.from(document.querySelectorAll("button, [role='button'], a"))
                        .find(element => normalize(element.innerText || element.textContent).includes("NEW DECLARATION"));
                    if (!target) {
                        return false;
                    }
                    target.scrollIntoView({ block: "center" });
                    target.click();
                    return true;
                }
                """);

        if (!Boolean.TRUE.equals(clicked)) {
            throw new IllegalStateException("Unable to click NEW DECLARATION button.");
        }
    }

    private Locator resolveNewDeclarationButton() {
        String[] selectors = new String[] {
                "button:has-text('NEW DECLARATION')",
                "button:has-text('New Declaration')",
                "[role='button']:has-text('NEW DECLARATION')",
                "[role='button']:has-text('New Declaration')",
                "text=NEW DECLARATION"
        };

        for (String selector : selectors) {
            Locator locator = page.locator(selector).first();
            if (locator.count() > 0) {
                return locator;
            }
        }

        return null;
    }
}
