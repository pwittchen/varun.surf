package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        Locator windEndpoint = page.locator("[data-endpoint='/api/v1/wind']");

        assertThat(healthEndpoint.isVisible()).isTrue();
        assertThat(statusEndpoint.isVisible()).isTrue();
        assertThat(spotsEndpoint.isVisible()).isTrue();
        assertThat(windEndpoint.isVisible()).isTrue();

        // Every listed endpoint is probed, so none may be left on "checking..."
        Locator windStatus = windEndpoint.locator(".status-endpoint-text");
        windStatus.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));
        assertThat(windStatus.textContent()).isEqualTo("operational");
    }

    @Test
    @DisplayName("Should have the sidebar with the current page highlighted")
    void shouldHaveTheSidebarWithTheCurrentPageHighlighted() {
        navigateToStatusPage();

        Locator links = page.locator("#sideMenu .sidebar-link");
        links.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(links.evaluateAll("nodes => nodes.map(n => n.getAttribute('href'))"))
            .isEqualTo(List.of("/status", "/metrics", "/sources", "/mcp",
                "https://github.com/pwittchen/varun.surf", "/logs"));

        Locator active = page.locator("#sideMenu [aria-current='page']");
        assertThat(active.count()).isEqualTo(1);
        assertThat(active.getAttribute("href")).isEqualTo("/status");
    }

    @Test
    @DisplayName("Should have refresh status button in the header card")
    void shouldHaveRefreshStatusButton() {
        navigateToStatusPage();

        Locator refreshButton = page.locator(".status-header-actions #refresh-status");
        refreshButton.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(refreshButton.isVisible()).isTrue();

        refreshButton.click();
        page.waitForTimeout(2000);
    }

    @Test
    @DisplayName("Should go back to the dashboard from the wordmark")
    void shouldGoBackToTheDashboardFromTheWordmark() {
        navigateToStatusPage();

        Locator wordmark = page.locator("#headerTitle");
        wordmark.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        wordmark.click();

        page.waitForURL(url -> url.equals(BASE_URL + "/") || url.equals(BASE_URL),
            new Page.WaitForURLOptions().setTimeout(NAVIGATION_TIMEOUT));

        assertThat(page.title()).contains("VARUN.SURF");
    }

    @Test
    @DisplayName("Should navigate to sources page from the sidebar")
    void shouldNavigateToSourcesPageFromTheSidebar() {
        navigateToStatusPage();

        Locator sourcesLink = page.locator("#sourcesLink");
        sourcesLink.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        sourcesLink.click();

        page.waitForURL(BASE_URL + "/sources",
            new Page.WaitForURLOptions().setTimeout(NAVIGATION_TIMEOUT));

        assertThat(page.title()).contains("Sources");
    }

    @Test
    @DisplayName("Should navigate to mcp page from the sidebar")
    void shouldNavigateToMcpPageFromTheSidebar() {
        navigateToStatusPage();

        Locator mcpLink = page.locator("#mcpLink");
        mcpLink.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        mcpLink.click();

        page.waitForURL(BASE_URL + "/mcp",
            new Page.WaitForURLOptions().setTimeout(NAVIGATION_TIMEOUT));

        assertThat(page.title()).contains("MCP Server");
    }

    @Test
    @DisplayName("Should have sources and mcp links in the footer")
    void shouldHaveSourcesAndMcpLinksInTheFooter() {
        navigateToStatusPage();

        Locator sourcesFooterLink = page.locator(".footer-content a[href='/sources']");
        Locator mcpFooterLink = page.locator(".footer-content a[href='/mcp']");
        sourcesFooterLink.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(sourcesFooterLink.isVisible()).isTrue();
        assertThat(mcpFooterLink.isVisible()).isTrue();
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