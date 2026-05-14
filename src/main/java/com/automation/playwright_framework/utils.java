package com.automation.playwright_framework;

import com.automation.LoginPage;
import com.microsoft.playwright.Page;

public class utils {

    private final LoginPage login;

    public utils(Page page) {
        this.login = new LoginPage(page);
    }

    public void loginAsAdmin() {
        login.loginAsAdmin("prasanna", "123456");
    }
}
