import * as copyButtons from '../common/copyButtons.js';
import * as toolsPage from '../common/toolsPage.js';

// ============================================================================
// MCP SERVER CONFIGURATION
// ============================================================================

function initMcpConfig() {
    const origin = window.location.origin;
    const sseUrl = `${origin}/mcp/sse`;
    const installCmd = `claude mcp add --transport sse varun-surf ${sseUrl}`;
    const jsonConfig = JSON.stringify({
        mcpServers: {
            'varun-surf': {
                type: 'sse',
                url: sseUrl
            }
        }
    }, null, 2);

    const endpointEl = document.getElementById('mcp-endpoint-url');
    const installEl = document.getElementById('mcp-install-cmd');
    const jsonEl = document.getElementById('mcp-json-config');
    const llmsEl = document.getElementById('mcp-llms-url');
    if (endpointEl) endpointEl.textContent = sseUrl;
    if (installEl) installEl.textContent = installCmd;
    if (jsonEl) jsonEl.textContent = jsonConfig;
    if (llmsEl) llmsEl.textContent = `${origin}/llms.txt`;

    copyButtons.setupCopyButtons();
}

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    toolsPage.setup();

    initMcpConfig();
});
