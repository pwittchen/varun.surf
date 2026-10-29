package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("CLI Page E2E Tests")
class CliPageE2eTest extends BaseE2eTest {

    private void navigateToCliPage() {
        page.navigate(BASE_URL + "/cli");
        waitForPageLoad();
    }

    @Test
    @DisplayName("Should load cli page with title")
    void shouldLoadCliPageWithTitle() {
        navigateToCliPage();

        assertThat(page.title()).contains("Command-line Client");
    }

    @Test
    @DisplayName("Should display the install command and examples")
    void shouldDisplayTheInstallCommandAndExamples() {
        navigateToCliPage();

        Locator installCmd = page.locator("#cli-install-cmd");
        installCmd.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(installCmd.textContent()).isEqualTo("curl -fsSL " + BASE_URL + "/cli/install.sh | sh");
        assertThat(page.locator("#cli-examples").textContent()).contains("varun wind jastarnia");
    }

    @Test
    @DisplayName("Should have a copy button for the install command")
    void shouldHaveACopyButtonForTheInstallCommand() {
        navigateToCliPage();

        Locator copyButtons = page.locator(".mcp-copy-btn");
        copyButtons.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(copyButtons.count()).isEqualTo(1);
        assertThat(copyButtons.first().getAttribute("data-copy-target")).isEqualTo("cli-install-cmd");
    }

    @Test
    @DisplayName("Should have the sidebar with the current page highlighted")
    void shouldHaveTheSidebarWithTheCurrentPageHighlighted() {
        navigateToCliPage();

        Locator links = page.locator("#sideMenu .sidebar-link");
        links.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(links.evaluateAll("nodes => nodes.map(n => n.getAttribute('href'))"))
            .isEqualTo(List.of("https://play.google.com/store/apps/details?id=surf.varun.app",
                "/status", "/metrics", "/sources", "/mcp", "/cli", "/logs",
                "https://github.com/pwittchen/varun.surf"));

        Locator active = page.locator("#sideMenu [aria-current='page']");
        assertThat(active.count()).isEqualTo(1);
        assertThat(active.getAttribute("href")).isEqualTo("/cli");
        assertThat(page.locator("#cliLinkSidebarLabel").textContent()).isEqualTo("CLI");
        assertThat(page.locator("#mcpLinkSidebarLabel").textContent()).isEqualTo("MCP");
    }
}
