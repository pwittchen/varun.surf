package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("Sources Page E2E Tests")
class SourcesPageE2eTest extends BaseE2eTest {

    private void navigateToSourcesPage() {
        page.navigate(BASE_URL + "/sources");
        waitForPageLoad();
    }

    @Test
    @DisplayName("Should load sources page with title")
    void shouldLoadSourcesPageWithTitle() {
        navigateToSourcesPage();

        assertThat(page.title()).contains("Sources");
    }

    @Test
    @DisplayName("Should display all source sections")
    void shouldDisplayAllSourceSections() {
        navigateToSourcesPage();

        Locator spotsDataSources = page.locator("#spots-data-sources");
        spotsDataSources.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        Locator forecastSources = page.locator("#forecast-sources");
        Locator liveStationSources = page.locator("#live-station-sources");

        assertThat(spotsDataSources.isVisible()).isTrue();
        assertThat(forecastSources.isVisible()).isTrue();
        assertThat(liveStationSources.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should render source links after loading")
    void shouldRenderSourceLinksAfterLoading() {
        navigateToSourcesPage();

        Locator sourceLinks = page.locator(".source-link");
        sourceLinks.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(sourceLinks.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should have the sidebar with the current page highlighted")
    void shouldHaveTheSidebarWithTheCurrentPageHighlighted() {
        navigateToSourcesPage();

        Locator links = page.locator("#sideMenu .sidebar-link");
        links.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(links.evaluateAll("nodes => nodes.map(n => n.getAttribute('href'))"))
            .isEqualTo(List.of("/status", "/sources", "/mcp",
                "https://github.com/pwittchen/varun.surf", "/logs", "/metrics"));

        Locator active = page.locator("#sideMenu [aria-current='page']");
        assertThat(active.count()).isEqualTo(1);
        assertThat(active.getAttribute("href")).isEqualTo("/sources");
    }
}
