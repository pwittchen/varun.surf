package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("Status Page E2E Tests")
class StatusPageE2eTest extends BaseE2eTest {

    private void navigateToStatusPage() {
        page.navigate(BASE_URL + "/status");
        waitForPageLoad();
    }

    @Test
    @DisplayName("Should load status page with title")
    void shouldLoadStatusPageWithTitle() {
        navigateToStatusPage();

        assertThat(page.title()).contains("Status");
    }

    @Test
    @DisplayName("Should display system status")
    void shouldDisplaySystemStatus() {
        navigateToStatusPage();

        Locator statusIndicator = page.locator("#status-indicator");
        statusIndicator.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(statusIndicator.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should display service information")
    void shouldDisplayServiceInformation() {
        navigateToStatusPage();

        Locator version = page.locator("#version");
        version.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        Locator uptime = page.locator("#uptime");
        Locator startTime = page.locator("#start-time");
        Locator spotsCount = page.locator("#spots-count");
        Locator countriesCount = page.locator("#countries-count");
        Locator liveStations = page.locator("#live-stations");

        assertThat(version.isVisible()).isTrue();
        assertThat(uptime.isVisible()).isTrue();
        assertThat(startTime.isVisible()).isTrue();
        assertThat(spotsCount.isVisible()).isTrue();
        assertThat(countriesCount.isVisible()).isTrue();
        assertThat(liveStations.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should display API endpoints status")
    void shouldDisplayApiEndpointsStatus() {
        navigateToStatusPage();

        Locator endpoints = page.locator(".status-endpoint");
        endpoints.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(endpoints.count()).isGreaterThan(0);

        Locator healthEndpoint = page.locator("[data-endpoint='/api/v1/health']");
        Locator statusEndpoint = page.locator("[data-endpoint='/api/v1/status']");
        Locator spotsEndpoint = page.locator("[data-endpoint='/api/v1/spots']");

        assertThat(healthEndpoint.isVisible()).isTrue();
        assertThat(statusEndpoint.isVisible()).isTrue();
        assertThat(spotsEndpoint.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should have refresh status button")
    void shouldHaveRefreshStatusButton() {
        navigateToStatusPage();

        Locator refreshButton = page.locator("#refresh-status");
        refreshButton.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(refreshButton.isVisible()).isTrue();

        refreshButton.click();
        page.waitForTimeout(2000);
    }

    @Test
    @DisplayName("Should have back to dashboard link")
    void shouldHaveBackToDashboardLink() {
        navigateToStatusPage();

        Locator backLink = page.locator("a[href='/']");
        backLink.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(backLink.isVisible()).isTrue();

        backLink.click();

        page.waitForURL(url -> url.equals(BASE_URL + "/") || url.equals(BASE_URL),
            new Page.WaitForURLOptions().setTimeout(NAVIGATION_TIMEOUT));

        String currentUrl = page.url();
        assertThat(currentUrl.startsWith(BASE_URL)).isTrue();
    }

    @Test
    @DisplayName("Should show operational status after loading")
    void shouldShowOperationalStatusAfterLoading() {
        navigateToStatusPage();

        Locator statusText = page.locator("#status-indicator .status-text");
        statusText.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        page.waitForTimeout(3000);

        String statusContent = statusText.textContent();
        assertThat(statusContent).isNotEmpty();
    }

    @Test
    @DisplayName("Should update last updated timestamp")
    void shouldUpdateLastUpdatedTimestamp() {
        navigateToStatusPage();

        Locator lastUpdated = page.locator("#last-updated");
        lastUpdated.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        page.waitForTimeout(3000);

        String timestampContent = lastUpdated.textContent();
        assertThat(timestampContent).isNotEmpty();
    }
}