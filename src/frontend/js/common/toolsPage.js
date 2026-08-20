// ============================================================================
// TOOLS PAGES
// The status / sources / MCP / logs / metrics pages share one controller: they
// all show the same sidebar as the rest of the app (appShell), a header holding
// nothing but the language switch, and the same modals. The menu entries that
// need the spots list hand their mode over and navigate back to it
// (mainPageShortcuts), the rest work here.
// At mobile widths the sidebar steps aside completely and the header — wordmark
// plus language switch — is the whole chrome, with the wordmark leading home.
// ============================================================================

import * as appShell from './appShell.js';
import * as calculator from './calculator.js';
import * as constants from './constants.js';
import * as footer from './footer.js';
import * as mainPageShortcuts from './mainPageShortcuts.js';
import * as modals from './modals.js';
import * as routing from './routing.js';
import * as sideMenu from './sideMenu.js';
import * as state from './state.js';
import * as translations from './translations.js';

// The about modal is filled from the same keys everywhere; innerHTML for the
// entries carrying links, textContent for the rest
const MODAL_TEXT_IDS = [
    'appInfoModalTitle',
    'appInfoDescription',
    'appInfoContactTitle',
    'appInfoNewSpotTitle',
    'appInfoCollaborationTitle',
    'appInfoDevTitle'
];

const MODAL_HTML_IDS = [
    'appInfoContactText',
    'appInfoNewSpotText',
    'appInfoCollaborationText',
    'appInfoDevText'
];

function markCurrentPage() {
    const path = window.location.pathname.replace(/\/+$/, '') || '/';
    const current = document.querySelector(`#sideMenu .sidebar-link[href="${path}"]`);
    if (current) {
        current.setAttribute('aria-current', 'page');
    }
}

function updateTranslations() {
    const t = translations.t;

    MODAL_TEXT_IDS.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = t(id);
        }
    });

    MODAL_HTML_IDS.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.innerHTML = t(id);
        }
    });

    sideMenu.updateTranslations(t);
    calculator.updateTranslations(t);
    footer.updateFooter(t);

    const langCode = document.getElementById('langCode');
    if (langCode) {
        langCode.textContent = t('langCode');
    }

    const languageToggle = document.getElementById('languageToggle');
    if (languageToggle) {
        languageToggle.title = t('languageToggleTooltip');
    }
}

function setupTheme() {
    const themeToggle = document.getElementById('themeToggle');
    const themeIcon = document.getElementById('themeIcon');

    function applyIcon(theme) {
        themeIcon.innerHTML = theme === 'light' ? constants.THEME_ICON_SUN : constants.THEME_ICON_MOON;
    }

    applyIcon(state.getCurrentTheme());

    themeToggle.addEventListener('click', () => applyIcon(state.toggleTheme()));
}

function setupLanguage() {
    updateTranslations();

    const languageToggle = document.getElementById('languageToggle');
    if (languageToggle) {
        languageToggle.addEventListener('click', () => {
            state.toggleLanguage();
            updateTranslations();
        });
    }
}

// The calculator modal brings its own close/escape handling (calculator.js)
function setupInfoModal() {
    modals.setupModal({
        modalId: 'appInfoModal',
        closeButtonId: 'appInfoModalClose',
        closeCallback: () => modals.closeModal('appInfoModal')
    });

    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && modals.isModalActive('appInfoModal')) {
            modals.closeModal('appInfoModal');
        }
    });

    const infoToggle = document.getElementById('infoToggle');
    if (infoToggle) {
        infoToggle.addEventListener('click', () => modals.openModal('appInfoModal'));
    }
}

// Build the chrome and wire it up. Call first thing on load, before the page
// fills in its own content.
export function setup() {
    state.applyTheme(state.getTheme());

    appShell.renderSidebar();
    appShell.renderMinimalHeader();
    appShell.renderModals();

    markCurrentPage();

    document.getElementById('sidebarBrand').addEventListener('click', routing.navigateToHome);

    setupTheme();
    setupInfoModal();
    calculator.setupKiteSizeCalculator();
    mainPageShortcuts.setup();
    mainPageShortcuts.setupRandomSpotToggle();

    setupLanguage();

    sideMenu.setup();
    sideMenu.setupHints();
}
