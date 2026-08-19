// ============================================================================
// STICKY LEFT MENU
// The action buttons live in the vertical #sideMenu on desktop / tablet. At
// mobile widths (<=929px) the hamburger drawer takes over, so the buttons are
// moved into #headerIcons (inside .header-controls) where the drawer styles
// lay them out. Shared by the main page and the single spot page.
// ============================================================================

export const BREAKPOINT = 929;

// Every button that can live in the sticky left menu, with the translation key
// for its tooltip. Missing buttons are skipped, so a page carrying only part of
// the menu can use this as-is.
const TOOLTIP_KEYS = {
    infoToggle: 'infoToggleTooltip',
    themeToggle: 'themeToggleTooltip',
    favoritesToggle: 'favoritesToggleTooltip',
    firingSortToggle: 'firingSortTooltip',
    liveStationsToggle: 'liveStationsTooltip',
    mapToggle: 'mapToggleTooltip',
    heroToggle: 'heroToggleTooltip',
    kiteSizeToggle: 'kiteSizeToggleTooltip',
    randomSpotToggle: 'randomSpotTooltip',
    listViewBtn: 'listViewTooltip',
    gridViewBtn: 'gridViewTooltip'
};

// Text labels next to the icon; visible only in the mobile drawer, where the
// rail's icon-only styling no longer applies.
const LABEL_KEYS = {
    infoToggleLabel: 'infoButtonLabel',
    mapToggleLabel: 'mapButtonLabel'
};

// Apply the current language to every side menu button present on the page.
export function updateTranslations(t) {
    Object.entries(TOOLTIP_KEYS).forEach(([id, key]) => {
        const btn = document.getElementById(id);
        if (btn) {
            btn.title = t(key);
        }
    });

    Object.entries(LABEL_KEYS).forEach(([id, key]) => {
        const label = document.getElementById(id);
        if (label) {
            label.textContent = t(key);
        }
    });

    convertTitlesToHints(Object.keys(TOOLTIP_KEYS));
}

// Convert the sticky left menu's native tooltips into custom hint bubbles
// (styled like the map popups). The text is moved off `title` (so the browser's
// default tooltip no longer duplicates the bubble) into `data-hint` for
// setupHints() and kept as aria-label for accessibility / the mobile drawer.
export function convertTitlesToHints(ids) {
    ids.forEach(id => {
        const btn = document.getElementById(id);
        if (btn && btn.title) {
            btn.dataset.hint = btn.title;
            btn.setAttribute('aria-label', btn.title);
            btn.removeAttribute('title');
        }
    });
}

// Custom tooltips for the sticky left menu, styled like the map popups. A single
// bubble element is reused and positioned to the right of the hovered icon.
// It is appended to <body> and positioned with fixed coordinates so it escapes
// the side menu's overflow clipping (overflow-y: auto also clips horizontally).
export function setupHints() {
    const sideMenu = document.getElementById('sideMenu');
    if (!sideMenu) {
        return;
    }

    let hint = document.getElementById('sideMenuHint');
    if (!hint) {
        hint = document.createElement('div');
        hint.id = 'sideMenuHint';
        hint.className = 'side-menu-hint';
        hint.setAttribute('role', 'tooltip');
        document.body.appendChild(hint);
    }

    function hide() {
        hint.classList.remove('visible');
    }

    function show(btn) {
        // Only pop out from the vertical rail; in the mobile drawer the buttons
        // move into #headerIcons and the bubble would be misplaced.
        if (btn.parentElement !== sideMenu) {
            return;
        }
        const text = btn.dataset.hint || btn.getAttribute('aria-label');
        if (!text) {
            return;
        }
        hint.textContent = text;
        // Measure before revealing so vertical centering is exact.
        const btnRect = btn.getBoundingClientRect();
        const hintRect = hint.getBoundingClientRect();
        hint.style.left = `${btnRect.right + 12}px`;
        hint.style.top = `${btnRect.top + (btnRect.height - hintRect.height) / 2}px`;
        hint.classList.add('visible');
    }

    // The buttons may already sit in the mobile drawer when this runs, so look
    // in both containers; show() keeps the bubble to the vertical rail anyway.
    const headerIcons = document.getElementById('headerIcons');
    const containers = headerIcons ? [sideMenu, headerIcons] : [sideMenu];

    containers.forEach(container => {
        container.querySelectorAll('.theme-toggle').forEach(btn => {
            btn.addEventListener('mouseenter', () => show(btn));
            btn.addEventListener('mouseleave', hide);
        });
    });

    // Keep the bubble anchored if the rail scrolls, and dismiss on scroll/resize
    // to avoid a stale position.
    window.addEventListener('scroll', hide, true);
    window.addEventListener('resize', hide);
}

export function setup() {
    const sideMenu = document.getElementById('sideMenu');
    const headerIcons = document.getElementById('headerIcons');

    if (!sideMenu || !headerIcons) {
        return;
    }

    // Capture the buttons in their authored order so they always go back the
    // same way regardless of which container currently holds them.
    const iconButtons = Array.from(sideMenu.children);

    function moveTo(container) {
        iconButtons.forEach(btn => container.appendChild(btn));
    }

    function update() {
        if (window.innerWidth <= BREAKPOINT) {
            if (iconButtons[0] && iconButtons[0].parentElement !== headerIcons) {
                moveTo(headerIcons);
            }
        } else if (iconButtons[0] && iconButtons[0].parentElement !== sideMenu) {
            moveTo(sideMenu);
        }
    }

    let resizeTimer;
    window.addEventListener('resize', () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(update, 150);
    });

    update();
}
