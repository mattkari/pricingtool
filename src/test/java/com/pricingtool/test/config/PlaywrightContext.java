package com.pricingtool.test.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope
public class PlaywrightContext {

    private Playwright playwright;
    private Browser browser;
    private Page page;

    public void init() {
        playwright = Playwright.create();
        boolean headless = Boolean.parseBoolean(
                System.getProperty("playwright.headless", "false"));
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless));
        page = browser.newPage();
    }

    public Page getPage() {
        return page;
    }

    public void close() {
        if (page != null) {
            page.close();
            page = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
