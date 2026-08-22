import * as toolsPage from '../common/toolsPage.js';
import { t } from '../common/translations.js';

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

    document.querySelectorAll('.mcp-copy-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const targetId = btn.getAttribute('data-copy-target');
            const target = document.getElementById(targetId);
            if (!target) return;
            try {
                await navigator.clipboard.writeText(target.textContent);
                btn.textContent = t('mcpCopiedButton');
                btn.classList.add('copied');
                setTimeout(() => {
                    btn.textContent = t('mcpCopyButton');
                    btn.classList.remove('copied');
                }, 1500);
            } catch (err) {
                console.error('Failed to copy', err);
            }
        });
    });
}

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    toolsPage.setup();

    initMcpConfig();
});
