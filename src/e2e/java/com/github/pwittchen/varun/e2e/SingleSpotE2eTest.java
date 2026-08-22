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
        // navigate via the first available spot card instead of a hardcoded spot id,
        // so the test stays independent of changes to spots.json
        page.navigate(BASE_URL + "/");
        waitForPageLoad();

        Locator firstSpotName = page.locator("#spotsGrid .spot-card .spot-name").first();
        firstSpotName.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));
        firstSpotName.click();

        page.waitForURL(url -> url.contains("/spot/"),
            new Page.WaitForURLOptions().setTimeout(NAVIGATION_TIMEOUT));
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

        // Select any non-GFS option if available (models are populated dynamically)
        Locator options = dropdownMenu.locator(".dropdown-option");
        if (options.count() > 1) {
            options.nth(1).click();
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
    @DisplayName("Should prefill kite size calculator with the spot wind speed")
    void shouldPrefillKiteSizeCalculatorWithSpotWindSpeed() {
        navigateToSpotPage();
        waitForSpotToLoad();

        // remember a wind speed that must be overwritten by the spot wind speed
        page.evaluate("() => localStorage.setItem('calculatorInputs', "
            + "JSON.stringify({windSpeed: 33, riderWeight: 82, skillLevel: 'advanced-flat'}))");
        page.reload();
        waitForSpotToLoad();

        Locator spotWindSpeed = page.locator(".current-conditions-card .wind-speed");
        spotWindSpeed.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));
        // the card renders e.g. "13 kts", the calculator prefills the rounded knots
        String renderedWindSpeed = spotWindSpeed.textContent().replaceAll("[^0-9.].*$", "").trim();
        String expectedWindSpeed = String.valueOf(Math.round(Double.parseDouble(renderedWindSpeed)));

        page.locator("#kiteSizeToggle").click();
        page.locator("#kiteSizeModal").waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        // wind speed comes from the spot, the remaining inputs from the remembered data
        assertThat(page.locator("#windSpeed").inputValue()).isEqualTo(expectedWindSpeed);
        assertThat(page.locator("#riderWeight").inputValue()).isEqualTo("82");
        assertThat(page.locator("#skillLevel").inputValue()).isEqualTo("advanced-flat");

        // the spot wind speed also overwrites the remembered one
        Object storedWindSpeed = page.evaluate(
            "() => JSON.parse(localStorage.getItem('calculatorInputs')).windSpeed");
        assertThat(String.valueOf(storedWindSpeed)).isEqualTo(expectedWindSpeed);
    }

    @Test
    @DisplayName("Should read the wind map popup off the forecast slider")
    void shouldUpdateWindMapPopupWithForecastSlider() {
        // pick a spot the shared wind grid actually covers, so stepping the slider
        // has something to say about it; the main page visit is what mints the session
        page.navigate(BASE_URL + "/");
        waitForPageLoad();

        Object wgId = page.evaluate(
            "async () => {"
                + " const response = await fetch('/api/v1/wind?hours=24', { credentials: 'same-origin' });"
                + " const timeline = await response.json();"
                + " return timeline.spots && timeline.spots.length > 0 ? timeline.spots[0].wgId : null;"
                + "}");
        assertThat(wgId).isNotNull();

        page.navigate(BASE_URL + "/spot/" + wgId);
        waitForPageLoad();
        waitForSpotToLoad();

        // the wind map - and its all-spots fetch - is built only once its tab is opened
        page.locator(".spot-media-tab[data-media='wind']").click();

        // the spot's own marker opens with wind, gusts and direction, not just its name
        Locator popup = page.locator(".spot-wind-map-wrapper .map-popup");
        Locator speed = popup.locator(".map-popup-speed");
        speed.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));
        assertThat(speed.textContent()).contains("kts");
        assertThat(popup.locator(".map-popup-direction").count()).isEqualTo(1);
        String nowReading = popup.innerHTML();

        // the slider under the map arrives with /api/v1/wind
        Locator timeline = page.locator(".spot-wind-map-wrapper .map-timeline");
        timeline.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        // stepping into the forecast rewrites the popup in place instead of closing it
        page.locator(".spot-wind-map-wrapper .map-timeline-tick").last().click();
        page.waitForTimeout(500);

        assertThat(popup.count()).isEqualTo(1);
        assertThat(popup.locator(".map-popup-speed").textContent()).contains("kts");
        // a forecast hour is not a measurement, so the popup names the hour it reads
        assertThat(popup.locator(".map-popup-meta").count()).isEqualTo(1);
        String forecastReading = popup.innerHTML();
        assertThat(forecastReading).isNotEqualTo(nowReading);

        // and back to now, still without reopening anything
        page.locator(".spot-wind-map-wrapper .map-timeline-tick[data-step='0']").click();
        page.waitForTimeout(500);

        assertThat(popup.count()).isEqualTo(1);
        assertThat(popup.innerHTML()).isNotEqualTo(forecastReading);
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