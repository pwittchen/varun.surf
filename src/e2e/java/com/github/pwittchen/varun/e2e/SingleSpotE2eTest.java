package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("Single Spot View E2E Tests")
class SingleSpotE2eTest extends BaseE2eTest {

    private void navigateToSpotPage() {
        page.navigate(BASE_URL + "/spot/500760");
        waitForPageLoad();
    }

    private void waitForSpotToLoad() {
        // Wait for the loading spinner to disappear
        Locator loadingMessage = page.locator("#loadingMessage");
        loadingMessage.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.HIDDEN)
            .setTimeout(DEFAULT_TIMEOUT));
    }

    @Test
    @DisplayName("Should load single spot page")
    void shouldLoadSingleSpotPage() {
        navigateToSpotPage();

        assertThat(page.title()).contains("VARUN.SURF");
    }

    @Test
    @DisplayName("Should display spot container with content")
    void shouldDisplaySpotContainerWithContent() {
        navigateToSpotPage();
        waitForSpotToLoad();

        Locator spotContainer = page.locator("#spotContainer");
        boolean hasContent = !spotContainer.innerHTML().trim().isEmpty();

        assertThat(hasContent).isTrue();
    }

    @Test
    @DisplayName("Should switch between forecast tabs and chart view")
    void shouldSwitchBetweenForecastTabs() {
        navigateToSpotPage();
        waitForSpotToLoad();

        // Look for tabs that may be dynamically generated
        Locator tabs = page.locator(".tab-button, .forecast-tab, .spot-tab");
        page.waitForTimeout(2000);

        if (tabs.count() > 1) {
            tabs.nth(1).click();
            page.waitForTimeout(1000);

            tabs.first().click();
            page.waitForTimeout(1000);
        }

        // Switch to the chart view
        Locator chartToggle = page.locator("#chartToggle, .chart-toggle, [data-view='chart']");
        if (chartToggle.count() > 0 && chartToggle.first().isVisible()) {
            chartToggle.first().click();
            page.waitForTimeout(1000);

            // Verify chart is displayed
            Locator chartContainer = page.locator(".chart-container, #chartContainer, canvas");
            if (chartContainer.count() > 0) {
                assertThat(chartContainer.first().isVisible()).isTrue();
            }

            // Switch back to the table view
            Locator tableToggle = page.locator("#tableToggle, .table-toggle, [data-view='table']");
            if (tableToggle.count() > 0 && tableToggle.first().isVisible()) {
                tableToggle.first().click();
                page.waitForTimeout(1000);
            }
        }
    }

    @Test
    @DisplayName("Should display model dropdown")
    void shouldDisplayModelDropdown() {
        navigateToSpotPage();
        waitForSpotToLoad();

        Locator modelDropdown = page.locator("#modelDropdown");
        modelDropdown.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        modelDropdown.click();

        Locator dropdownMenu = page.locator("#modelDropdownMenu");
        dropdownMenu.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(dropdownMenu.isVisible()).isTrue();

        Locator ifsOption = dropdownMenu.locator("[data-value='ifs']");
        if (ifsOption.isVisible()) {
            ifsOption.click();
            page.waitForTimeout(2000);
        }
    }

    @Test
    @DisplayName("Should open info modal on single spot page")
    void shouldOpenInfoModalOnSingleSpotPage() {
        navigateToSpotPage();
        waitForPageLoad();

        Locator infoToggle = page.locator("#infoToggle");
        infoToggle.click();

        Locator appInfoModal = page.locator("#appInfoModal");
        appInfoModal.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(appInfoModal.isVisible()).isTrue();

        Locator closeButton = page.locator("#appInfoModalClose");
        closeButton.click();

        appInfoModal.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.HIDDEN)
            .setTimeout(DEFAULT_TIMEOUT));
        assertThat(appInfoModal.isVisible()).isFalse();
    }

    @Test
    @DisplayName("Should toggle theme on single spot page")
    void shouldToggleThemeOnSingleSpotPage() {
        navigateToSpotPage();
        waitForPageLoad();

        Locator themeToggle = page.locator("#themeToggle");

        themeToggle.click();
        page.waitForTimeout(500);

        themeToggle.click();
        page.waitForTimeout(500);
    }

    @Test
    @DisplayName("Should navigate back to main page via logo")
    void shouldNavigateBackToMainPageViaLogo() {
        navigateToSpotPage();
        waitForPageLoad();

        Locator logo = page.locator("#headerLogo");
        logo.click();

        page.waitForURL(url -> url.equals(BASE_URL + "/") || url.equals(BASE_URL),
            new Page.WaitForURLOptions().setTimeout(NAVIGATION_TIMEOUT));

        String currentUrl = page.url();
        assertThat(currentUrl.startsWith(BASE_URL)).isTrue();
    }

    @Test
    @DisplayName("Should change language on single spot page")
    void shouldChangeLanguageOnSingleSpotPage() {
        navigateToSpotPage();
        waitForPageLoad();

        Locator languageToggle = page.locator("#languageToggle");
        Locator langCode = page.locator("#langCode");

        String initialLang = langCode.textContent();

        languageToggle.click();
        page.waitForTimeout(1000);

        String newLang = langCode.textContent();
        assertThat(newLang).isNotEqualTo(initialLang);
    }
}