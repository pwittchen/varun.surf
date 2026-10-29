import * as copyButtons from '../common/copyButtons.js';
import * as toolsPage from '../common/toolsPage.js';

// ============================================================================
// COMMAND-LINE CLIENT
// ============================================================================

function initCliInstall() {
    const installEl = document.getElementById('cli-install-cmd');
    if (installEl) installEl.textContent = `curl -fsSL ${window.location.origin}/cli/install.sh | sh`;

    copyButtons.setupCopyButtons();
}

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    toolsPage.setup();

    initCliInstall();
});
