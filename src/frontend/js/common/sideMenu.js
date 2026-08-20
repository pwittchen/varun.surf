// ============================================================================
// SIDEBAR
// Behaviour of the sidebar every page shares (its markup lives in appShell.js):
// a brand, labelled menu entries grouped into sections, and a collapse switch
// that shrinks it to an icon rail (remembered across visits).
// At mobile widths (<=929px) the sidebar steps aside. Where the page has a
// drawer (main page, single spot page) its entries move into #headerIcons
// (inside .header-controls) and the brand into the header; on the pages with no
// drawer (status / sources / MCP / logs / metrics) only the brand moves, and the
// header — wordmark plus language switch — is the whole chrome.
// ============================================================================

import * as state from './state.js';

export const BREAKPOINT = 929;

// Between the drawer and this width the sidebar keeps to its icon rail: the
// content column (the list view in particular) has no room for both.
export const NARROW_BREAKPOINT = 1100;

// Every button that can live in the sidebar, with the translation key for its
// tooltip. Missing buttons are skipped, so a page carrying only part of the
// menu can use this as-is.
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
    gridViewBtn: 'gridViewTooltip',
    statusLink: 'statusLinkTooltip',
    sourcesLink: 'sourcesLinkTooltip',
    mcpLink: 'mcpLinkTooltip',
    githubLink: 'githubLinkTooltip',
    logsLink: 'logsLinkTooltip',
    metricsLink: 'metricsLinkTooltip'
};

// Row labels next to the icons, plus the section headings above them. Short by
// design: the full sentence stays in the tooltip.
const SIDEBAR_LABEL_KEYS = {
    infoToggleSidebarLabel: 'sidebarInfo',
    themeToggleSidebarLabel: 'sidebarTheme',
    listViewBtnSidebarLabel: 'sidebarListView',
    gridViewBtnSidebarLabel: 'sidebarGridView',
    mapToggleSidebarLabel: 'sidebarMap',
    heroToggleSidebarLabel: 'sidebarHero',
    favoritesToggleSidebarLabel: 'sidebarFavorites',
    firingSortToggleSidebarLabel: 'sidebarFiring',
    liveStationsToggleSidebarLabel: 'sidebarLiveStations',
    kiteSizeToggleSidebarLabel: 'sidebarCalculator',
    randomSpotToggleSidebarLabel: 'sidebarRandomSpot',
    statusLinkSidebarLabel: 'sidebarStatus',
    sourcesLinkSidebarLabel: 'sidebarSources',
    mcpLinkSidebarLabel: 'sidebarMcp',
    githubLinkSidebarLabel: 'sidebarGithub',
    logsLinkSidebarLabel: 'sidebarLogs',
    metricsLinkSidebarLabel: 'sidebarMetrics',
    sidebarSectionView: 'sidebarSectionView',
    sidebarSectionFilters: 'sidebarSectionFilters',
    sidebarSectionTools: 'sidebarSectionTools',
    sidebarSectionMore: 'sidebarSectionMore',
    sidebarSectionAdmin: 'sidebarSectionAdmin'
};

// Text labels next to the icon; visible only in the mobile drawer, where the
// sidebar's own labels are dropped.
const LABEL_KEYS = {
    infoToggleLabel: 'infoButtonLabel',
    mapToggleLabel: 'mapButtonLabel'
};

// Kept from the last updateTranslations() call so the collapse switch can
// relabel itself ("Collapse" <-> "Expand") without the caller passing t again.
let translate = null;

// Apply the current language to every sidebar button present on the page.
export function updateTranslations(t) {
    translate = t;

    Object.entries(TOOLTIP_KEYS).forEach(([id, key]) => {
        const btn = document.getElementById(id);
        if (btn) {
            btn.title = t(key);
        }
    });

    Object.entries({...LABEL_KEYS, ...SIDEBAR_LABEL_KEYS}).forEach(([id, key]) => {
        const label = document.getElementById(id);
        if (label) {
            label.textContent = t(key);
        }
    });

    updateCollapseLabel();

    convertTitlesToHints(Object.keys(TOOLTIP_KEYS));
}

function updateCollapseLabel() {
    const btn = document.getElementById('sidebarCollapse');
    if (!btn || !translate) {
        return;
    }
    const collapsed = document.body.classList.contains('sidebar-collapsed');
    const text = translate(collapsed ? 'sidebarExpand' : 'sidebarCollapse');
    const label = document.getElementById('sidebarCollapseLabel');
    if (label) {
        label.textContent = text;
    }
    btn.setAttribute('aria-label', text);
    btn.dataset.hint = text;
}

// Convert the sidebar's native tooltips into custom hint bubbles (styled like
// the map popups). The text is moved off `title` (so the browser's default
// tooltip no longer duplicates the bubble) into `data-hint` for setupHints()
// and kept as aria-label for accessibility / the mobile drawer.
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

// Custom tooltips for the collapsed sidebar, styled like the map popups. A
// single bubble element is reused and positioned to the right of the hovered
// icon. It is appended to <body> and positioned with fixed coordinates so it
// escapes the sidebar's overflow clipping. Expanded, the row already carries
// its label, so no bubble is shown.
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
        // Only pop out from the collapsed rail: expanded rows show their label,
        // and in the mobile drawer the buttons move into #headerIcons, where the
        // bubble would be misplaced.
        if (!sideMenu.contains(btn) || !document.body.classList.contains('sidebar-collapsed')) {
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
    // in both containers; show() keeps the bubble to the collapsed rail anyway.
    const headerIcons = document.getElementById('headerIcons');
    const containers = headerIcons ? [sideMenu, headerIcons] : [sideMenu];

    containers.forEach(container => {
        container.querySelectorAll('.theme-toggle').forEach(btn => {
            btn.addEventListener('mouseenter', () => show(btn));
            btn.addEventListener('mouseleave', hide);
        });
    });

    // Keep the bubble anchored if the sidebar scrolls, and dismiss on
    // scroll/resize to avoid a stale position.
    window.addEventListener('scroll', hide, true);
    window.addEventListener('resize', hide);
}

// Collapse switch: shrinks the sidebar to an icon rail and back. The state is
// stored, so the sidebar opens the way it was left.
function setupCollapse() {
    syncCollapsed();

    // Enable the width transitions only after the stored state is on the body,
    // so a collapsed sidebar does not slide shut on every page load.
    requestAnimationFrame(() => document.body.classList.add('sidebar-ready'));

    const btn = document.getElementById('sidebarCollapse');
    if (!btn) {
        return;
    }

    btn.addEventListener('click', () => {
        const collapsed = !document.body.classList.contains('sidebar-collapsed');
        state.setSidebarCollapsed(collapsed);
        syncCollapsed();
    });
}

// Debounced, and registered even on the pages whose menu has nothing to hand
// over to a drawer, so the narrow-width rail always follows the window
function onResize(handler) {
    let timer;
    window.addEventListener('resize', () => {
        clearTimeout(timer);
        timer = setTimeout(handler, 150);
    });
}

// Apply the stored preference, except on narrow desktop widths, where the
// labelled sidebar would squeeze the content column: there it stays an icon
// rail and the switch is hidden, since it would have nothing to do.
function syncCollapsed() {
    const forced = window.innerWidth <= NARROW_BREAKPOINT;
    document.body.classList.toggle('sidebar-forced-collapsed', forced);
    document.body.classList.toggle('sidebar-collapsed', forced || state.getSidebarCollapsed());
    updateCollapseLabel();
}

export function setup() {
    const sideMenu = document.getElementById('sideMenu');
    const headerContent = document.querySelector('.header-content');

    setupCollapse();
    onResize(syncCollapsed);

    if (!sideMenu || !headerContent) {
        return;
    }

    // Pages without a drawer (status / sources / MCP / logs / metrics) hand over
    // the wordmark only: at mobile widths their header is the whole chrome.
    const headerIcons = document.getElementById('headerIcons');
    const brand = document.getElementById('sidebarBrand');

    // Capture where each moveable element belongs in the sidebar, so it always
    // goes back to its own section rather than to the end of the menu.
    const homes = [
        ...(brand ? [brand] : []),
        ...(headerIcons ? sideMenu.querySelectorAll('.sidebar-item') : [])
    ].map(el => ({el, parent: el.parentElement, next: el.nextElementSibling}));

    if (homes.length === 0) {
        return;
    }

    function moveToDrawer() {
        homes.forEach(({el}) => {
            // The wordmark closes the mobile header, below the drawer it opens
            (el === brand ? headerContent : headerIcons).appendChild(el);
        });
    }

    function moveToSidebar() {
        // Back to front: an element's recorded successor is already in place by
        // the time it is re-inserted before it.
        for (let i = homes.length - 1; i >= 0; i--) {
            const {el, parent, next} = homes[i];
            parent.insertBefore(el, next);
        }
    }

    function update() {
        const inDrawer = homes[0] && homes[0].el.parentElement !== homes[0].parent;
        if (window.innerWidth <= BREAKPOINT) {
            if (!inDrawer) {
                moveToDrawer();
            }
        } else if (inDrawer) {
            moveToSidebar();
        }
    }

    onResize(update);

    update();
}
