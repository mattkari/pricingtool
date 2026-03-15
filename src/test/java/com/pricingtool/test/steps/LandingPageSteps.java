package com.pricingtool.test.steps;

import com.pricingtool.test.config.PlaywrightContext;
import com.pricingtool.test.config.TestEnvironmentConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LandingPageSteps {

    @Autowired
    private PlaywrightContext playwrightContext;

    @Autowired
    private TestEnvironmentConfig testEnvConfig;

    @Given("the user navigates to the EcoHaven Boots website")
    public void navigateToWebsite() {
        playwrightContext.getPage().navigate(testEnvConfig.getBaseUrl());
    }

    @Then("the user should be on the landing page")
    public void verifyLandingPage() {
        Page page = playwrightContext.getPage();
        assertFalse(page.url().isEmpty(), "URL should not be empty");
        assertFalse(page.title().isEmpty(), "Page title should not be empty");
    }

    @Then("the navigation should contain {string}")
    public void verifyNavigationContains(String linkText) {
        Page page = playwrightContext.getPage();
        Locator link = page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName(linkText));
        assertThat(link.first()).isVisible();
    }
}
