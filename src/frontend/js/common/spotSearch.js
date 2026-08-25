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
const DEBOUNCE_MS = 150;

let allSpots = null;
let spotsPromise = null;
let results = [];
let activeIndex = -1;
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

function setExpanded(open) {
    const {input} = elements();
    if (input) {
        input.setAttribute('aria-expanded', String(open));
    }
}

function closeResults() {
    const {menu} = elements();
    if (!menu) return;

    menu.classList.remove('open');
    menu.innerHTML = '';
    results = [];
    activeIndex = -1;
    setExpanded(false);
}

function renderMessage(text) {
    const {menu} = elements();
    if (!menu) return;

    results = [];
    activeIndex = -1;
    menu.innerHTML = `<div class="dropdown-option spot-search-message">${text}</div>`;
    menu.classList.add('open');
    setExpanded(true);
}

function renderResults() {
    const {menu} = elements();
    if (!menu) return;

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
}

function setActiveIndex(index) {
    const {menu} = elements();
    if (!menu || results.length === 0) return;

    activeIndex = index;
    menu.querySelectorAll('.spot-search-option').forEach(option => {
        const isActive = Number(option.dataset.index) === activeIndex;
        option.classList.toggle('active', isActive);
        if (isActive) {
            option.scrollIntoView({block: 'nearest'});
        }
    });
}

function moveActive(step) {
    if (results.length === 0) return;

    const next = activeIndex + step;
    if (next < 0) {
        setActiveIndex(results.length - 1);
    } else if (next >= results.length) {
        setActiveIndex(0);
    } else {
        setActiveIndex(next);
    }
}

function openSpot(spot) {
    if (!spot) return;
    routing.navigateToSpot(spot.wgId);
}

async function search(rawQuery) {
    const query = normalizeForSearch(rawQuery.trim());

    if (query === '') {
        closeResults();
        return;
    }

    if (!allSpots) {
        renderMessage(translations.t('searchLoadingSpots'));
        await loadSpots();
        // The field may have been cleared or retyped while the list was on its
        // way; only the query still in the field gets to paint results.
        const {input} = elements();
        if (!input || normalizeForSearch(input.value.trim()) !== query) {
            return;
        }
    }

    results = (allSpots || [])
        .filter(spot => spotMatchesQuery(spot, query))
        .slice(0, MAX_RESULTS);
    activeIndex = results.length > 0 ? 0 : -1;
    renderResults();
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
    if (input && input.value.trim() !== '') {
        search(input.value);
    }
}

export function setup() {
    const {container, input, clear, menu} = elements();
    if (!container || !input || !menu) {
        return;
    }

    updateTranslations();

    input.addEventListener('focus', () => {
        // Warm the list up while the first letters are being typed
        loadSpots();
    });

    input.addEventListener('input', () => {
        updateClearVisibility(input, clear);

        clearTimeout(debounceTimeout);
        debounceTimeout = setTimeout(() => search(input.value), DEBOUNCE_MS);
    });

    input.addEventListener('keydown', (e) => {
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
                    openSpot(results[activeIndex]);
                }
                break;
            case 'Escape':
                if (menu.classList.contains('open')) {
                    closeResults();
                } else if (input.value !== '') {
                    input.value = '';
                    updateClearVisibility(input, clear);
                } else {
                    input.blur();
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
        openSpot(results[Number(option.dataset.index)]);
    });

    if (clear) {
        clear.addEventListener('click', () => {
            input.value = '';
            updateClearVisibility(input, clear);
            closeResults();
            input.focus();
        });
    }

    // A click anywhere else puts the results away; the field keeps what was
    // typed, so coming back to it is a keystroke rather than a retype.
    document.addEventListener('click', (e) => {
        const target = e.target instanceof Element ? e.target : null;
        if (!target || !target.closest('#spotSearchContainer')) {
            closeResults();
        }
    });

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
