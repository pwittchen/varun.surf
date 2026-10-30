package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("Privacy Page E2E Tests")
class PrivacyPageE2eTest extends BaseE2eTest {

    private void navigateToPrivacyPage() {
        page.navigate(BASE_URL + "/privacy");
        waitForPageLoad();
    }

    @Test
    @DisplayName("Should load privacy page with title")
    void shouldLoadPrivacyPageWithTitle() {
        navigateToPrivacyPage();

        assertThat(page.title()).contains("Privacy Policy");
    }

    @Test
    @DisplayName("Should display policy sections with contact email")
    void shouldDisplayPolicySectionsWithContactEmail() {
        navigateToPrivacyPage();

        Locator cards = page.locator(".status-card");
        cards.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(cards.count()).isEqualTo(9);
        assertThat(page.locator("a[href='mailto:hello@varun.surf']").count()).isAtLeast(1);
    }

    @Test
    @DisplayName("Should render the shared sidebar")
    void shouldRenderTheSharedSidebar() {
        navigateToPrivacyPage();

        Locator infoToggle = page.locator("#infoToggle");
        infoToggle.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(infoToggle.isVisible()).isTrue();
    }

    @Test
    @DisplayName("Should link privacy page from info modal")
    void shouldLinkPrivacyPageFromInfoModal() {
        page.navigate(BASE_URL + "/mcp");
        waitForPageLoad();

        page.locator("#infoToggle").click();

        Locator link = page.locator("#appInfoModal a[href='/privacy']");
        link.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(link.count()).isEqualTo(1);
    }
}
