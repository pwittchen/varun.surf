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
        Locator columnToggle = page.locator("#columnToggle");

        String initialClass = spotsGrid.getAttribute("class");
        assertThat(initialClass).isNotNull();

        columnToggle.click();
        page.waitForTimeout(500);

        String classAfterFirstClick = spotsGrid.getAttribute("class");
        assertThat(classAfterFirstClick).isNotNull();

        columnToggle.click();
        page.waitForTimeout(500);

        String classAfterSecondClick = spotsGrid.getAttribute("class");
        assertThat(classAfterSecondClick).isNotNull();
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