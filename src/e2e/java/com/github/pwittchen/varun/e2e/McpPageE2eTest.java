package com.github.pwittchen.varun.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("MCP Page E2E Tests")
class McpPageE2eTest extends BaseE2eTest {

    private void navigateToMcpPage() {
        page.navigate(BASE_URL + "/mcp");
        waitForPageLoad();
    }

    @Test
    @DisplayName("Should load mcp page with title")
    void shouldLoadMcpPageWithTitle() {
        navigateToMcpPage();

        assertThat(page.title()).contains("MCP Server");
    }

    @Test
    @DisplayName("Should display resolved sse endpoint and install command")
    void shouldDisplayResolvedSseEndpointAndInstallCommand() {
        navigateToMcpPage();

        Locator endpoint = page.locator("#mcp-endpoint-url");
        endpoint.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        Locator installCmd = page.locator("#mcp-install-cmd");

        assertThat(endpoint.textContent()).isEqualTo(BASE_URL + "/mcp/sse");
        assertThat(installCmd.textContent()).contains("claude mcp add");
        assertThat(installCmd.textContent()).contains(BASE_URL + "/mcp/sse");
    }

    @Test
    @DisplayName("Should display json configuration")
    void shouldDisplayJsonConfiguration() {
        navigateToMcpPage();

        Locator jsonConfig = page.locator("#mcp-json-config");
        jsonConfig.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        String content = jsonConfig.textContent();
        assertThat(content).contains("mcpServers");
        assertThat(content).contains("varun-surf");
        assertThat(content).contains(BASE_URL + "/mcp/sse");
    }

    @Test
    @DisplayName("Should list all available mcp tools")
    void shouldListAllAvailableMcpTools() {
        navigateToMcpPage();

        Locator tools = page.locator("#mcp-tools-card .mcp-tool-name");
        tools.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(tools.count()).isEqualTo(6);
        assertThat(tools.allTextContents()).containsExactly(
            "list_spots",
            "get_spot",
            "find_spot_by_name",
            "list_countries",
            "get_spots_by_country",
            "get_status"
        );
    }

    @Test
    @DisplayName("Should have copy buttons for configuration")
    void shouldHaveCopyButtonsForConfiguration() {
        navigateToMcpPage();

        Locator copyButtons = page.locator(".mcp-copy-btn");
        copyButtons.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(copyButtons.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should display llms.txt section with markdown endpoints")
    void shouldDisplayLlmsTxtSectionWithMarkdownEndpoints() {
        navigateToMcpPage();

        Locator llmsUrl = page.locator("#mcp-llms-url");
        llmsUrl.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(llmsUrl.textContent()).isEqualTo(BASE_URL + "/llms.txt");

        Locator endpoints = page.locator("#mcp-llms-card .mcp-tool-name");
        assertThat(endpoints.allTextContents()).containsExactly(
            "/llms.txt",
            "/llms/spots.md",
            "/llms/spots/{wgId}.md",
            "/llms/countries.md",
            "/llms/countries/{slug}.md"
        );
    }

    @Test
    @DisplayName("Should have the sidebar with the current page highlighted")
    void shouldHaveTheSidebarWithTheCurrentPageHighlighted() {
        navigateToMcpPage();

        Locator links = page.locator("#sideMenu .sidebar-link");
        links.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT));

        assertThat(links.evaluateAll("nodes => nodes.map(n => n.getAttribute('href'))"))
            .isEqualTo(List.of("/status", "/metrics", "/sources", "/mcp",
                "https://github.com/pwittchen/varun.surf", "/logs"));

        Locator active = page.locator("#sideMenu [aria-current='page']");
        assertThat(active.count()).isEqualTo(1);
        assertThat(active.getAttribute("href")).isEqualTo("/mcp");
    }
}
