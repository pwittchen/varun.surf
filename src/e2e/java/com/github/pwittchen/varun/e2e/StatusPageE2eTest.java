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
    @DisplayName("Should have the shared navigation with the current page highlighted")
    void shouldHaveTheSharedNavigationWithTheCurrentPageHighlighted() {
        navigateToStatusPage();

        Locator actions = page.locator(".status-actions a");
        actions.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(actions.allTextContents().stream().map(String::trim).toList())
            .containsExactly("Status", "Sources", "MCP", "Metrics", "Logs", "Dashboard")
            .inOrder();

        Locator active = page.locator(".status-actions a.active");
        assertThat(active.count()).isEqualTo(1);
        assertThat(active.getAttribute("href")).isEqualTo("/status");
        assertThat(active.getAttribute("aria-current")).isEqualTo("page");
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
    @DisplayName("Should navigate to sources page from status actions")
    void shouldNavigateToSourcesPageFromStatusActions() {
        navigateToStatusPage();

        Locator sourcesLink = page.locator(".status-actions a[href='/sources']");
        sourcesLink.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        sourcesLink.click();

        page.waitForURL(BASE_URL + "/sources",
            new Page.WaitForURLOptions().setTimeout(NAVIGATION_TIMEOUT));

        assertThat(page.title()).contains("Sources");
    }

    @Test
    @DisplayName("Should navigate to mcp page from status actions")
    void shouldNavigateToMcpPageFromStatusActions() {
        navigateToStatusPage();

        Locator mcpLink = page.locator(".status-actions a[href='/mcp']");
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