// ============================================================================
// ANDROID APP
// The one-off invitation to install the app for visitors browsing on an
// Android phone; the sidebar carries a permanent link to the same listing. The
// invitation is remembered as soon as it is shown, not when it is answered:
// someone who only wants the web never sees it twice, however they leave it.
// ============================================================================

import * as modals from './modals.js';
import * as state from './state.js';
import { t } from './translations.js';

export const PLAY_STORE_URL = 'https://play.google.com/store/apps/details?id=surf.varun.app';

const MODAL_ID = 'androidAppModal';

// Long enough for the page to draw its content first, so the invitation does
// not land on a blank screen
const PROMPT_DELAY_MS = 1500;

const TEXT_IDS = [
    'androidAppModalTitle',
    'androidAppModalText',
    'androidAppModalCancel',
    'androidAppModalConfirmLabel'
];

// "; wv)" marks an Android WebView - an app showing a page, not a visitor
// browsing in Chrome, so not someone to send to the Play Store
function isAndroidBrowser() {
    const ua = navigator.userAgent;
    return /Android/i.test(ua) && !/; wv\)/.test(ua);
}

// iPadOS reports itself as a Mac; the touch screen gives it away
function isIOS() {
    const ua = navigator.userAgent;
    return /iPad|iPhone|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
}

export function updateTranslations() {
    TEXT_IDS.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = t(id);
        }
    });
}

function close() {
    modals.closeModal(MODAL_ID);
}

function maybePrompt() {
    if (!isAndroidBrowser() || state.wasAndroidAppPromptShown()) {
        return;
    }
    setTimeout(() => {
        // Never stack on top of a dialog the visitor opened themselves; the
        // invitation waits for the next visit instead
        if (document.querySelector('.modal-overlay.active')) {
            return;
        }
        updateTranslations();
        modals.openModal(MODAL_ID);
        state.setAndroidAppPromptShown();
    }, PROMPT_DELAY_MS);
}

// Call after appShell.renderSidebar() and renderModals()
export function setup() {
    // An iPhone has no use for a Play Store link in its drawer; the styles
    // drop it there (and leave the desktop sidebar of an iPad alone)
    document.documentElement.classList.toggle('ios', isIOS());

    updateTranslations();

    if (!document.getElementById(MODAL_ID)) {
        return;
    }
    modals.setupModals([{ modalId: MODAL_ID, closeButtonId: 'androidAppModalClose', closeCallback: close }]);
    modals.setupCloseButton('androidAppModalCancel', close);
    // The link opens the Play Store in a new tab; the dialog has done its job
    modals.setupCloseButton('androidAppModalConfirm', close);

    maybePrompt();
}
