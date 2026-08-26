// ============================================================================
// MAIN PAGE SHORTCUTS
// Away from the main page, the menu entries that act on the spots list have no
// list to act on. The view entries still mean something — they store which of
// the three spots-list views to return to (the main page reads the same keys on
// load) and navigate back. The filters do not: they are greyed out here, the way
// the banner is, and only come back in the views that carry a list to filter.
// Every mode entry still shows its switch, set to what the spots list has stored:
// greyed out says "not from here", not "off".
// Shared by the single spot page and the status / sources / MCP / logs /
// metrics pages.
// ============================================================================

import * as api from './api.js';
import * as routing from './routing.js';
import * as state from './state.js';

function onClick(id, handler) {
    const btn = document.getElementById(id);
    if (btn) {
        btn.addEventListener('click', handler);
    }
}

// Grey out and switch off an entry that has nothing to act on here
export function markDisabled(id) {
    const btn = document.getElementById(id);
    if (btn) {
        btn.classList.add('side-menu-disabled');
        btn.disabled = true;
        btn.setAttribute('aria-disabled', 'true');
    }
}

// The mode entries carry a switch, and here it shows the mode as the spots list
// has it stored — the same value the main page reads on load. Nothing on these
// pages writes it back: the entries are inert, so the switch is a readout of the
// list the user will return to rather than a control.
const STORED_MODES = [
    ['heroToggle', state.getHeroVisible],
    ['favoritesToggle', state.getShowingFavorites],
    ['firingSortToggle', state.getFiringSort],
    ['liveStationsToggle', state.getLiveStationsOnly]
];

// Runs before sideMenu.setup(), which mirrors the class onto aria-checked on the
// way in, so the switch is right from the first paint.
function reflectStoredModes() {
    STORED_MODES.forEach(([id, isOn]) => {
        const btn = document.getElementById(id);
        if (btn) {
            btn.classList.toggle('active', isOn());
        }
    });
}

// Back to the spots list the user came from: favorites if that mode is on,
// otherwise the country filter (the same target as the logo and the wordmark).
export function goToSpotsList() {
    if (state.getShowingFavorites()) {
        window.location.href = '/starred';
        return;
    }

    const savedCountry = state.getSelectedCountry();
    if (savedCountry === 'all') {
        routing.navigateToHome();
    } else {
        routing.navigateToCountry(savedCountry);
    }
}

export function setup() {
    reflectStoredModes();

    // The hero banner belongs to the main page only
    markDisabled('heroToggle');

    // A filter needs a spots list to act on, and these pages hold none. The three
    // of them used to store their mode and navigate back, which made a filter a
    // way of leaving the page being read; they are greyed out here instead, the
    // same way the banner is, and stay live in the three views that do carry the
    // list - grid, list and map.
    markDisabled('favoritesToggle');
    markDisabled('firingSortToggle');
    markDisabled('liveStationsToggle');

    // The view entries stay live: they say which of those three views to come
    // back to, which is something these pages can offer.
    onClick('mapToggle', () => {
        window.location.href = '/map';
    });

    // Mirrors the main page, where the list entry is the way back to a plain
    // spots list on a phone and clears the two filters a narrow layout can set.
    onClick('listViewBtn', () => {
        if (window.innerWidth <= 929) {
            state.setFiringSort(false);
            state.setLiveStationsOnly(false);
        }
        state.setDesktopViewMode('list');
        goToSpotsList();
    });

    onClick('gridViewBtn', () => {
        state.setDesktopViewMode('grid');
        goToSpotsList();
    });
}

// Jumps to a randomly picked spot, skipping the one currently open (if any).
// The spots list is not held by these pages, so it is fetched on click
// (~170 kB gzipped) and the entry is disabled meanwhile.
export function setupRandomSpotToggle(getCurrentSpotId = () => null) {
    const btn = document.getElementById('randomSpotToggle');
    if (!btn) {
        return;
    }

    btn.addEventListener('click', async () => {
        if (btn.disabled) {
            return;
        }

        btn.disabled = true;

        try {
            const spots = await api.fetchAllSpots();
            const currentSpotId = getCurrentSpotId();
            const candidates = spots.filter(spot => String(spot.wgId) !== String(currentSpotId));
            const pool = candidates.length > 0 ? candidates : spots;

            if (pool.length === 0) {
                return;
            }

            routing.navigateToSpot(pool[Math.floor(Math.random() * pool.length)].wgId);
        } catch (error) {
            console.error('Error fetching spots for random pick:', error);
        } finally {
            btn.disabled = false;
        }
    });
}
