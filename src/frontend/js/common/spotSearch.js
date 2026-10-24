// ============================================================================
// HEADER SPOT SEARCH (desktop)
//
// The single spot page shows one spot and has no list to filter, so the search
// field here is a jump box: type a few letters, pick a spot, land on its page.
// Results are the same match the main page's filter makes (name or country,
// diacritics folded), so the same typing finds the same spots on both pages.
//
// The spots list is not held by this page. Rather than pay for it on load, it
// is fetched once on the first focus - by the time two letters are typed it is
// usually there, and a page that never touches the field never fetches it. The
// field is hidden on the mobile drawer layout, where the header has no room for
// it and the way to another spot is the list itself.
// ============================================================================

import * as api from './api.js';
import * as flags from './flags.js';
import * as routing from './routing.js';
import * as translations from './translations.js';
import { normalizeForSearch, spotMatchesQuery } from './search.js';

// Enough to recognise the spot you meant without turning the header into a page
const MAX_RESULTS = 8;

// Long enough to skip the letters typed on the way to a word, short enough that
// the list feels like it follows the keyboard
export const DEBOUNCE_MS = 150;

let allSpots = null;
let spotsPromise = null;
let resultsMenu = null;
let debounceTimeout = null;

function elements() {
    return {
        container: document.getElementById('spotSearchContainer'),
        input: document.getElementById('spotSearchInput'),
        clear: document.getElementById('spotSearchClear'),
        menu: document.getElementById('spotSearchResults')
    };
}

// One fetch per page: the second focus reuses the first one's promise, whether
// it has resolved yet or not.
function loadSpots() {
    if (allSpots) {
        return Promise.resolve(allSpots);
    }

    if (!spotsPromise) {
        spotsPromise = api.fetchAllSpots()
            .then(spots => {
                allSpots = spots;
                return spots;
            })
            .catch(error => {
                console.error('Error fetching spots for search:', error);
                // Let a later focus try again rather than leaving the field dead
                spotsPromise = null;
                return [];
            });
    }

    return spotsPromise;
}

/**
 * The list of matching spots hanging from a search field, with the keyboard
 * and pointer handling that goes with it. Shared by the single spot page, where
 * a pick opens the spot's page, and the main page's map view, where it flies
 * the map to the spot.
 *
 * @param {object} options - Configuration options
 * @param {HTMLElement} options.container - Element holding the field and the menu;
 *   a click outside it puts the results away
 * @param {HTMLInputElement} options.input - The search field
 * @param {HTMLElement} options.menu - The dropdown the results are written into
 * @param {function} options.getSpots - Returns the spots to match against, or a
 *   promise of them while they are still on their way
 * @param {function} options.onPick - Called with the spot picked
 * @param {function} [options.isEnabled] - The keys are left alone while false
 * @returns {{search:function, close:function, isOpen:function}}
 */
export function createResultsMenu(options) {
    const { container, input, menu, getSpots, onPick, isEnabled = () => true } = options;

    let results = [];
    let activeIndex = -1;

    const setExpanded = (open) => {
        input.setAttribute('aria-expanded', String(open));
    };

    const close = () => {
        menu.classList.remove('open');
        menu.innerHTML = '';
        results = [];
        activeIndex = -1;
        setExpanded(false);
    };

    const isOpen = () => menu.classList.contains('open');

    const renderMessage = (text) => {
        results = [];
        activeIndex = -1;
        menu.innerHTML = `<div class="dropdown-option spot-search-message">${text}</div>`;
        menu.classList.add('open');
        setExpanded(true);
    };

    const renderResults = () => {
        if (results.length === 0) {
            renderMessage(translations.t('searchNoResults'));
            return;
        }

        menu.innerHTML = results.map((spot, index) => `
            <div class="dropdown-option spot-search-option${index === activeIndex ? ' active' : ''}"
                 role="option"
                 data-index="${index}"
                 data-wg-id="${spot.wgId}">
                <span class="spot-search-flag">${flags.getCountryFlag(spot.country)}</span>
                <span class="dropdown-option-name">${spot.name}</span>
                <span class="spot-search-country">${spot.country || ''}</span>
            </div>
        `).join('');
        menu.classList.add('open');
        setExpanded(true);
    };

    const setActiveIndex = (index) => {
        if (results.length === 0) return;

        activeIndex = index;
        menu.querySelectorAll('.spot-search-option').forEach(option => {
            const isActive = Number(option.dataset.index) === activeIndex;
            option.classList.toggle('active', isActive);
            if (isActive) {
                option.scrollIntoView({block: 'nearest'});
            }
        });
    };

    const moveActive = (step) => {
        if (results.length === 0) return;

        const next = activeIndex + step;
        if (next < 0) {
            setActiveIndex(results.length - 1);
        } else if (next >= results.length) {
            setActiveIndex(0);
        } else {
            setActiveIndex(next);
        }
    };

    const pick = (spot) => {
        if (!spot) return;
        close();
        onPick(spot);
    };

    const search = async (rawQuery) => {
        const query = normalizeForSearch(rawQuery.trim());

        if (query === '') {
            close();
            return;
        }

        let spots = getSpots();
        if (spots && typeof spots.then === 'function') {
            renderMessage(translations.t('searchLoadingSpots'));
            spots = await spots;
            // The field may have been cleared or retyped while the list was on
            // its way; only the query still in the field gets to paint results.
            if (normalizeForSearch(input.value.trim()) !== query) {
                return;
            }
        }

        results = (spots || [])
            .filter(spot => spotMatchesQuery(spot, query))
            .slice(0, MAX_RESULTS);
        activeIndex = results.length > 0 ? 0 : -1;
        renderResults();
    };

    input.addEventListener('keydown', (e) => {
        if (!isEnabled()) return;

        switch (e.key) {
            case 'ArrowDown':
                e.preventDefault();
                moveActive(1);
                break;
            case 'ArrowUp':
                e.preventDefault();
                moveActive(-1);
                break;
            case 'Enter':
                if (activeIndex >= 0 && results[activeIndex]) {
                    e.preventDefault();
                    pick(results[activeIndex]);
                }
                break;
            default:
                break;
        }
    });

    menu.addEventListener('mousedown', (e) => {
        // Before the blur that a click would otherwise fire first
        const option = e.target instanceof Element
            ? e.target.closest('.spot-search-option')
            : null;
        if (!option) return;

        e.preventDefault();
        pick(results[Number(option.dataset.index)]);
    });

    // A click anywhere else puts the results away; the field keeps what was
    // typed, so coming back to it is a keystroke rather than a retype.
    document.addEventListener('click', (e) => {
        const target = e.target instanceof Element ? e.target : null;
        if (!target || !container.contains(target)) {
            close();
        }
    });

    return { search, close, isOpen };
}

function updateClearVisibility(input, clear) {
    if (!clear) return;
    clear.classList.toggle('visible', input.value.trim() !== '');
}

/**
 * Refresh the copy the field renders itself (placeholder, tooltip and the
 * message rows), so the language toggle reaches it like the rest of the page.
 */
export function updateTranslations() {
    const {container, input} = elements();

    if (input) {
        input.placeholder = translations.t('searchPlaceholder');
    }

    if (container) {
        container.title = translations.t('searchShortcutTooltip');
    }

    // A list on screen was written in the old language
    if (resultsMenu && input && input.value.trim() !== '') {
        resultsMenu.search(input.value);
    }
}

export function setup() {
    const {container, input, clear, menu} = elements();
    if (!container || !input || !menu) {
        return;
    }

    resultsMenu = createResultsMenu({
        container,
        input,
        menu,
        getSpots: () => allSpots || loadSpots(),
        onPick: spot => routing.navigateToSpot(spot.wgId)
    });

    updateTranslations();

    input.addEventListener('focus', () => {
        // Warm the list up while the first letters are being typed
        loadSpots();
    });

    input.addEventListener('input', () => {
        updateClearVisibility(input, clear);

        clearTimeout(debounceTimeout);
        debounceTimeout = setTimeout(() => resultsMenu.search(input.value), DEBOUNCE_MS);
    });

    input.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;

        if (resultsMenu.isOpen()) {
            resultsMenu.close();
        } else if (input.value !== '') {
            input.value = '';
            updateClearVisibility(input, clear);
        } else {
            input.blur();
        }
    });

    if (clear) {
        clear.addEventListener('click', () => {
            input.value = '';
            updateClearVisibility(input, clear);
            resultsMenu.close();
            input.focus();
        });
    }

    // "/" anywhere on the page jumps to the field, as it does on the main page.
    // Ignored while another field has the focus (the slash is a character
    // there), while a modifier is held (browser shortcuts win), while a modal is
    // open (the key belongs to the modal) and on the drawer layout, where the
    // field is hidden.
    document.addEventListener('keydown', (e) => {
        if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey) {
            return;
        }

        const target = e.target;
        if (target instanceof HTMLInputElement
            || target instanceof HTMLTextAreaElement
            || target instanceof HTMLSelectElement
            || (target instanceof HTMLElement && target.isContentEditable)) {
            return;
        }

        if (document.querySelector('.modal-overlay.active')) {
            return;
        }

        if (container.offsetParent === null) {
            return;
        }

        e.preventDefault();
        input.focus();
        input.select();
    });
}
