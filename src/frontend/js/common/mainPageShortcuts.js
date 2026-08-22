// ============================================================================
// MAIN PAGE SHORTCUTS
// Away from the main page, the menu entries that act on the spots list have no
// list to act on. Each one stores its mode (the main page reads the same keys on
// load) and navigates back, so the effect is visible right away. No entry is
// ever highlighted here — an active state describes the spots list, so it
// belongs to the main page only.
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
    // The hero banner belongs to the main page only
    markDisabled('heroToggle');

    onClick('favoritesToggle', () => {
        state.setShowingFavorites(true);
        window.location.href = '/starred';
    });

    onClick('mapToggle', () => {
        window.location.href = '/map';
    });

    onClick('firingSortToggle', () => {
        state.setFiringSort(!state.getFiringSort());
        goToSpotsList();
    });

    onClick('liveStationsToggle', () => {
        state.setLiveStationsOnly(!state.getLiveStationsOnly());
        goToSpotsList();
    });

    // Mirrors the main page: in the mobile drawer the list entry is the way back
    // to the plain spots list, so it drops the two filters the drawer can set.
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
