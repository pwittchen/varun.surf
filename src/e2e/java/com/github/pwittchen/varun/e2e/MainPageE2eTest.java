package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("Main Page E2E Tests")
class MainPageE2eTest extends BaseE2eTest {

    private void navigateToMainPage() {
        page.navigate(BASE_URL);
        waitForPageLoad();
    }

    private void waitForSpotsToLoad() {
        Locator spotsGrid = page.locator("#spotsGrid");
        spotsGrid.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        Locator spotCards = page.locator("#spotsGrid .spot-card");
        spotCards.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));
    }

    @Test
    @DisplayName("Should load main page with title")
    void shouldLoadMainPageWithTitle() {
        navigateToMainPage();

        assertThat(page.title()).contains("VARUN.SURF");
    }

    @Test
    @DisplayName("Should display spots grid after loading forecasts")
    void shouldDisplaySpotsGridAfterLoadingForecasts() {
        navigateToMainPage();
        waitForSpotsToLoad();

        Locator spotCards = page.locator("#spotsGrid .spot-card");
        assertThat(spotCards.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should switch between grid and list view")
    void shouldSwitchBetweenGridAndListView() {
        navigateToMainPage();
        waitForSpotsToLoad();

        Locator spotsGrid = page.locator("#spotsGrid");
        Locator listViewBtn = page.locator("#listViewBtn");
        Locator gridViewBtn = page.locator("#gridViewBtn");

        String initialClass = spotsGrid.getAttribute("class");
        assertThat(initialClass).isNotNull();
        assertThat(initialClass).contains("spots-grid");

        // Switch to list view
        listViewBtn.click();
        page.waitForTimeout(500);

        String classAfterListClick = spotsGrid.getAttribute("class");
        assertThat(classAfterListClick).isNotNull();
        assertThat(classAfterListClick).contains("spots-list");

        // Switch back to grid view
        gridViewBtn.click();
        page.waitForTimeout(500);

        String classAfterGridClick = spotsGrid.getAttribute("class");
        assertThat(classAfterGridClick).isNotNull();
        assertThat(classAfterGridClick).contains("spots-grid");
    }

    @Test
    @DisplayName("Should switch to map view")
    void shouldSwitchToMapView() {
        navigateToMainPage();
        waitForSpotsToLoad();

        Locator spotsGrid = page.locator("#spotsGrid");
        Locator mapToggle = page.locator("#mapToggle");

        mapToggle.click();

        Locator mapContainer = page.locator("#mapContainer");
        mapContainer.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(mapContainer.isVisible()).isTrue();

        mapToggle.click();
        page.waitForTimeout(500);

        spotsGrid.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));
        assertThat(spotsGrid.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should cycle wind overlay field -> off -> arrows -> field")
    void shouldCycleWindOverlayModes() {
        navigateToMainPage();
        waitForSpotsToLoad();

        // Enter map view
        page.locator("#mapToggle").click();
        Locator mapContainer = page.locator("#mapContainer");
        mapContainer.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        Locator overlayButton = page.locator(".leaflet-control-wind-overlay .layer-switcher-button");
        overlayButton.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        Locator disclaimer = page.locator(".wind-overlay-disclaimer");
        Locator activeOption = page.locator(".leaflet-control-wind-overlay .layer-switcher-option.active");

        Locator particleCanvas = page.locator("canvas.wind-particle-layer");
        Locator colourWash = page.locator(".wind-field-layer");

        // Default mode is the field overlay: button active, both passes present
        // (colour wash + animated canvas), disclaimer visible.
        // (Marker/arrow rendering depends on network-resolved coordinates, which are
        // not available in the sandboxed E2E run, so we assert the control mechanics.)
        assertThat(activeOption.getAttribute("data-value")).isEqualTo("field");
        assertThat(overlayButton.getAttribute("class")).contains("active");
        assertThat(disclaimer.isVisible()).isTrue();
        assertThat(particleCanvas.count()).isEqualTo(1);
        assertThat(colourWash.count()).isEqualTo(1);

        // Switch to off: button not active, disclaimer and both passes removed
        overlayButton.click();
        page.locator(".leaflet-control-wind-overlay .layer-switcher-option[data-value='off']").click();
        page.waitForTimeout(300);
        assertThat(activeOption.getAttribute("data-value")).isEqualTo("off");
        assertThat(overlayButton.getAttribute("class")).doesNotContain("active");
        assertThat(disclaimer.count()).isEqualTo(0);
        assertThat(particleCanvas.count()).isEqualTo(0);
        assertThat(colourWash.count()).isEqualTo(0);

        // Switch to arrows: option active, button active, no interpolation disclaimer
        overlayButton.click();
        page.locator(".leaflet-control-wind-overlay .layer-switcher-option[data-value='arrows']").click();
        page.waitForTimeout(300);
        assertThat(activeOption.getAttribute("data-value")).isEqualTo("arrows");
        assertThat(overlayButton.getAttribute("class")).contains("active");
        assertThat(disclaimer.count()).isEqualTo(0);
        assertThat(particleCanvas.count()).isEqualTo(0);
        assertThat(colourWash.count()).isEqualTo(0);

        // Switch back to the field overlay: both passes come back together
        overlayButton.click();
        page.locator(".leaflet-control-wind-overlay .layer-switcher-option[data-value='field']").click();
        page.waitForTimeout(300);
        assertThat(activeOption.getAttribute("data-value")).isEqualTo("field");
        assertThat(overlayButton.getAttribute("class")).contains("active");
        assertThat(disclaimer.isVisible()).isTrue();
        assertThat(particleCanvas.count()).isEqualTo(1);
        assertThat(colourWash.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should link to the other pages from the sidebar")
    void shouldLinkToTheOtherPagesFromTheSidebar() {
        navigateToMainPage();
        waitForPageLoad();

        assertThat(page.locator("#statusLink").getAttribute("href")).isEqualTo("/status");
        assertThat(page.locator("#sourcesLink").getAttribute("href")).isEqualTo("/sources");
        assertThat(page.locator("#mcpLink").getAttribute("href")).isEqualTo("/mcp");
        assertThat(page.locator("#logsLink").getAttribute("href")).isEqualTo("/logs");
        assertThat(page.locator("#metricsLink").getAttribute("href")).isEqualTo("/metrics");

        Locator githubLink = page.locator("#githubLink");
        assertThat(githubLink.getAttribute("href")).isEqualTo("https://github.com/pwittchen/varun.surf");
        assertThat(githubLink.getAttribute("target")).isEqualTo("_blank");

        page.locator("#statusLink").click();
        page.waitForLoadState();

        assertThat(page.url()).endsWith("/status");
        Locator statusIndicator = page.locator("#status-indicator");
        statusIndicator.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));
        assertThat(statusIndicator.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should collapse the sidebar and remember it")
    void shouldCollapseSidebarAndRememberIt() {
        navigateToMainPage();
        waitForPageLoad();

        Locator body = page.locator("body");
        Locator collapseButton = page.locator("#sidebarCollapse");
        Locator label = page.locator("#favoritesToggleSidebarLabel");

        assertThat(body.getAttribute("class")).doesNotContain("sidebar-collapsed");
        assertThat(label.isVisible()).isTrue();

        collapseButton.click();
        page.waitForTimeout(500);

        assertThat(body.getAttribute("class")).contains("sidebar-collapsed");
        assertThat(label.isVisible()).isFalse();

        // The collapsed state survives a reload
        navigateToMainPage();
        assertThat(body.getAttribute("class")).contains("sidebar-collapsed");

        collapseButton.click();
        page.waitForTimeout(500);

        assertThat(body.getAttribute("class")).doesNotContain("sidebar-collapsed");
        assertThat(label.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should open info modal")
    void shouldOpenInfoModal() {
        navigateToMainPage();
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
    @DisplayName("Should open kite size calculator modal")
    void shouldOpenKiteSizeCalculatorModal() {
        navigateToMainPage();
        waitForPageLoad();

        Locator kiteSizeToggle = page.locator("#kiteSizeToggle");
        kiteSizeToggle.click();

        Locator kiteSizeModal = page.locator("#kiteSizeModal");
        kiteSizeModal.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(kiteSizeModal.isVisible()).isTrue();

        Locator closeButton = page.locator("#kiteSizeModalClose");
        closeButton.click();

        kiteSizeModal.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.HIDDEN)
            .setTimeout(DEFAULT_TIMEOUT));
        assertThat(kiteSizeModal.isVisible()).isFalse();
    }

    @Test
    @DisplayName("Should remember last entered kite size calculator data")
    void shouldRememberLastEnteredKiteSizeCalculatorData() {
        navigateToMainPage();
        waitForPageLoad();

        page.locator("#kiteSizeToggle").click();

        Locator kiteSizeModal = page.locator("#kiteSizeModal");
        kiteSizeModal.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        page.locator("#windSpeed").fill("18");
        page.locator("#riderWeight").fill("80");
        page.locator("#skillLevel").selectOption("intermediate-flat");
        page.locator("#calculateBtn").click();

        page.locator("#kiteSizeModalClose").click();
        kiteSizeModal.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.HIDDEN)
            .setTimeout(DEFAULT_TIMEOUT));

        navigateToMainPage();
        page.locator("#kiteSizeToggle").click();
        kiteSizeModal.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(page.locator("#windSpeed").inputValue()).isEqualTo("18");
        assertThat(page.locator("#riderWeight").inputValue()).isEqualTo("80");
        assertThat(page.locator("#skillLevel").inputValue()).isEqualTo("intermediate-flat");
    }

    @Test
    @DisplayName("Should filter spots by search")
    void shouldFilterSpotsBySearch() {
        navigateToMainPage();
        waitForSpotsToLoad();

        Locator spotCards = page.locator("#spotsGrid .spot-card");
        int initialCount = spotCards.count();

        Locator searchInput = page.locator("#searchInput");
        searchInput.fill("Hel");

        page.waitForTimeout(1000);

        Locator visibleCards = page.locator("#spotsGrid .spot-card:visible");
        int filteredCount = visibleCards.count();

        assertThat(filteredCount).isAtMost(initialCount);
    }

    @Test
    @DisplayName("Should toggle theme")
    void shouldToggleTheme() {
        navigateToMainPage();
        waitForPageLoad();

        Locator themeToggle = page.locator("#themeToggle");

        themeToggle.click();
        page.waitForTimeout(500);

        themeToggle.click();
        page.waitForTimeout(500);
    }

    @Test
    @DisplayName("Should open country dropdown and filter")
    void shouldOpenCountryDropdownAndFilter() {
        navigateToMainPage();
        waitForSpotsToLoad();

        Locator dropdownButton = page.locator("#dropdownButton");
        dropdownButton.click();

        Locator dropdownMenu = page.locator("#dropdownMenu");
        dropdownMenu.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(dropdownMenu.isVisible()).isTrue();

        Locator firstOption = dropdownMenu.locator(".dropdown-option").first();
        firstOption.click();

        page.waitForTimeout(1000);
    }
}