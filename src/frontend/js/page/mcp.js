import * as translations from '../common/translations.js';
import * as footer from '../common/footer.js';
import * as routing from '../common/routing.js';
import * as state from '../common/state.js';

// ============================================================================
// THEME INITIALIZATION
// ============================================================================

// Set the initial theme
state.applyTheme(state.getTheme());

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
    if (endpointEl) endpointEl.textContent = sseUrl;
    if (installEl) installEl.textContent = installCmd;
    if (jsonEl) jsonEl.textContent = jsonConfig;

    document.querySelectorAll('.mcp-copy-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const targetId = btn.getAttribute('data-copy-target');
            const target = document.getElementById(targetId);
            if (!target) return;
            try {
                await navigator.clipboard.writeText(target.textContent);
                const original = btn.textContent;
                btn.textContent = 'Copied!';
                btn.classList.add('copied');
                setTimeout(() => {
                    btn.textContent = original;
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
    // Setup header title click handler
    const headerTitle = document.getElementById('headerTitle');
    if (headerTitle) {
        headerTitle.addEventListener('click', routing.navigateToHome);
    }

    initMcpConfig();

    footer.updateFooter(translations.t);
});
