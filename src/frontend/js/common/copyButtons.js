import { t } from './translations.js';

// ============================================================================
// COPY BUTTONS
// The MCP and CLI pages put a "Copy" button next to every command and URL; the
// button names the element to copy in its data-copy-target attribute.
// ============================================================================

export function setupCopyButtons() {
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
