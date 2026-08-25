import * as flags from '../common/flags.js';
import * as translations from '../common/translations.js';
import * as footer from '../common/footer.js';
import * as weather from '../common/weather.js';
import * as constants from '../common/constants.js';
import * as date from '../common/date.js';
import * as api from '../common/api.js';
import * as routing from '../common/routing.js';
import * as map from '../common/map.js';
import * as state from '../common/state.js';
import * as modals from '../common/modals.js';
import * as calculator from '../common/calculator.js';
import * as appShell from '../common/appShell.js';
import * as sideMenu from '../common/sideMenu.js';

// ============================================================================
// GLOBAL STATE MANAGEMENT
// ============================================================================

// Application state variables
let globalWeatherData = [];
let availableCountries = new Set();
let currentSearchQuery = '';
let showingFavorites = false;
let autoRefreshInterval = null;
let currentFilter = 'all';

// Track previous URL for favorite toggle
let previousUrl = state.getPreviousUrl();

// Hero section slogans
const HERO_SLOGANS = {
    en: [
        "Chase the wind. Ride the wave.",
        "Your next session starts here.",
        "Find your wind. Ride your spot.",
        "Wind forecast for every kite spot.",
        "All spots. One dashboard.",
        "Where will the wind take you?",
        "Check the wind. Hit the water.",
        "No progress? Let go the bar.",
        "Respect the locals.",
        "Keep the spot clean.",
        "The worst day on the water is better than the best day in the office.",
        "Plan the epic kite trip.",
        "Lend your pump to a kiter in need.",
        "Be kind when someone on the water offers help.",
        "A good rider rides well on every kite.",
        "No wind? Check if there's a new drama on the kite forum."
    ],
    pl: [
        "Goń wiatr. Jedź na fali.",
        "Twoja sesja zaczyna się tutaj.",
        "Znajdź wiatr i pływaj na swoim spocie.",
        "Prognoza wiatru dla każdego spotu.",
        "Wszystkie spoty. Jeden dashboard.",
        "Dokąd zabierze Cię wiatr?",
        "Sprawdź wiatr i wskakuj na wodę.",
        "Progres stoi w miejscu? Odpuść bar.",
        "Respektuj lokalesów.",
        "Zachowaj czystość na spocie.",
        "Najgorszy dzień na wodzie jest lepszy, niż najlepszy dzień w biurze.",
        "Zaplanuj epicki kite trip.",
        "Pożycz pompkę kitesurferowi w potrzebie.",
        "Bądź uprzejmy, jeśli ktoś na wodzie oferuje pomoc.",
        "Dobry zawodnik pływa na każdym kajcie dobrze.",
        "Nie wieje? Sprawdź, czy jest nowa afera na kiteforum.",
    ]
};

// ============================================================================
// URL ROUTING HELPERS
// ============================================================================

function findCountryByNormalizedName(normalizedName) {
    // Find the actual country name from normalized URL name
    for (const country of availableCountries) {
        if (routing.normalizeCountryForUrl(country) === normalizedName) {
            return country;
        }
    }
    return null;
}

function updateUrlForCountry(country) {
    // Update browser URL without reloading the page
    // Store previous URL before changing (if not already starred)
    if (!routing.isStarredUrl()) {
        previousUrl = routing.getCurrentPath();
        state.setPreviousUrl(previousUrl);
    }
    routing.pushCountryUrl(country);
}

function updateUrlForStarred() {
    // Store the current URL before switching to starred
    if (!routing.isStarredUrl()) {
        previousUrl = routing.getCurrentPath();
        state.setPreviousUrl(previousUrl);
    }
    routing.pushStarredUrl();
    document.title = `${translations.t('favoritesToggleTooltip')} - VARUN.SURF`;
}

function restorePreviousUrl() {
    // Restore previous URL when exiting starred view
    const targetUrl = previousUrl || '/';
    routing.pushUrl(targetUrl);

    // Update page title based on URL
    if (targetUrl === '/') {
        updatePageTitle('all');
    } else {
        const urlCountry = routing.getCountryFromUrl();
        if (urlCountry) {
            const actualCountry = findCountryByNormalizedName(urlCountry);
            if (actualCountry) {
                updatePageTitle(actualCountry);
            }
        }
    }
}

function updatePageTitle(country) {
    // Update page title with country name
    if (country === 'all' || !country) {
        document.title = 'VARUN.SURF - Kitesurfing Weather Forecast';
    } else {
        const translatedCountry = translations.t(country.replace(/\s+/g, ''));
        document.title = `${translatedCountry} - VARUN.SURF`;
    }
}

function showInvalidCountryError(countryName) {
    const spotsGrid = document.getElementById('spotsGrid');
    spotsGrid.innerHTML = `
            <div class="error-message">
                <span class="error-icon">⚠️</span>
                <div class="error-title">${translations.t('invalidCountry')}</div>
                <div class="error-description">
                    ${translations.t('invalidCountryDescription')}<br/>
                    <br/>
                    <strong>Invalid country: "${countryName}"</strong>
                </div>
            </div>
        `;
}

// ============================================================================
// FAVORITES MANAGEMENT
// ============================================================================

function isFavorite(spotName) {
    return state.isFavorite(spotName);
}

function toggleFavorite(spotName) {
    const wasAdded = state.toggleFavorite(spotName);

    // Update all instances of this spot's favorite icon
    const allFavoriteIcons = document.querySelectorAll('.favorite-icon');
    allFavoriteIcons.forEach(icon => {
        // Check if it's in a card (grid view)
        const card = icon.closest('.spot-card');
        if (card) {
            const spotNameElement = card.querySelector('.spot-name');
            if (spotNameElement && spotNameElement.textContent === spotName) {
                if (wasAdded) {
                    icon.classList.add('favorited');
                    icon.title = translations.t('removeFromFavorites');
                } else {
                    icon.classList.remove('favorited');
                    icon.title = translations.t('addToFavorites');
                }

                // Add animation
                icon.classList.add('animate');
                setTimeout(() => icon.classList.remove('animate'), 400);
            }
        }

        // Check if it's in a list row (list view)
        const row = icon.closest('.list-row');
        if (row) {
            const spotNameElement = row.querySelector('.list-spot-name');
            if (spotNameElement && spotNameElement.textContent === spotName) {
                if (wasAdded) {
                    icon.classList.add('favorited');
                    icon.title = translations.t('removeFromFavorites');
                } else {
                    icon.classList.remove('favorited');
                    icon.title = translations.t('addToFavorites');
                }

                // Add animation
                icon.classList.add('animate');
                setTimeout(() => icon.classList.remove('animate'), 400);
            }
        }
    });

    // If showing favorites, re-render to reflect changes
    if (showingFavorites) {
        renderFavorites();
    }
}

async function renderFavorites() {
    showingFavorites = true;
    const favoritesButton = document.getElementById('favoritesToggle');
    favoritesButton.classList.add('active');

    const spotsGrid = document.getElementById('spotsGrid');
    const favorites = state.getFavorites();

    if (favorites.length === 0) {
        spotsGrid.innerHTML = `
                <div class="error-message">
                    <span class="error-icon">⭐</span>
                    <div class="error-title">${translations.t('noFavoritesTitle')}</div>
                    <div class="error-description">
                        ${translations.t('noFavoritesDescription')}
                    </div>
                </div>
            `;
        return;
    }

    try {
        if (globalWeatherData.length === 0) {
            globalWeatherData = await api.fetchAllSpots();
        }

        let favoriteSpots = globalWeatherData.filter(spot => favorites.includes(spot.name));
        if (showOnlyLiveStations) {
            favoriteSpots = favoriteSpots.filter(hasLiveConditions);
        }

        cancelLazyRendering();
        spotsGrid.innerHTML = '';

        if (favoriteSpots.length === 0) {
            spotsGrid.innerHTML = `
                <div class="error-message">
                    <span class="error-icon">🔍</span>
                    <div class="error-title">${translations.t('errorNoSpotsTitle')}</div>
                    <div class="error-description">
                        ${translations.t('errorNoSpotsDescription')}<br/>
                        ${translations.t('errorTryAdjusting')}
                    </div>
                </div>
            `;
            renderHeroSection();
            return;
        }

        // Render based on the current view mode
        if (currentViewMode === 'list') {
            // Explicit column sort wins; otherwise default to "firing now" when enabled
            const sortedSpots = listSortColumn
                ? sortSpots(favoriteSpots, listSortColumn, listSortDirection)
                : (firingSortEnabled ? sortByFiringNow(favoriteSpots) : favoriteSpots);

            // Render list view
            spotsGrid.appendChild(createListHeader());
            renderSpotsIncrementally(spotsGrid, sortedSpots, createListRow);
        } else {
            // Render grid view; "firing now" default ordering unless disabled
            let gridSpots = firingSortEnabled ? sortByFiringNow(favoriteSpots) : favoriteSpots;
            if (!firingSortEnabled) {
                gridSpots = applySavedOrder(
                    gridSpots,
                    state.getSpotOrder(gridColumnMode(spotsGrid), currentFilter, currentSearchQuery),
                    spot => spot.name
                );
            }
            renderSpotsIncrementally(spotsGrid, gridSpots, createSpotCard);
        }

        renderHeroSection();
    } catch (error) {
        console.error('Failed to load favorites:', error.message);
        showErrorMessage(error);
    }
}

function setupFavorites() {
    const favoritesButton = document.getElementById('favoritesToggle');

    favoritesButton.addEventListener('click', () => {
        if (isMapView) {
            hideMapView({ skipRender: true });
        }

        if (showingFavorites) {
            exitFavoritesMode();
        } else {
            // Enter favorites mode
            state.setShowingFavorites(true);

            // Reset country filter to "all"
            currentFilter = 'all';
            state.setSelectedCountry('all');
            updateSelectedCountryLabel('all');
            const dropdownOptions = document.querySelectorAll('.dropdown-option');
            dropdownOptions.forEach(opt => {
                opt.classList.toggle('selected', opt.dataset.country === 'all');
            });

            // Update URL to /starred
            updateUrlForStarred();

            // Clear previous URL since country was reset to "all"
            // Must be after updateUrlForStarred() which saves current path as previousUrl
            previousUrl = '/';
            state.setPreviousUrl('/');

            renderFavorites();
        }

        // Scroll to top after toggling favorites
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    });

}

function exitFavoritesMode(options = {}) {
    if (!showingFavorites) {
        return;
    }

    const { skipRender = false, skipScroll = false } = options;

    showingFavorites = false;
    state.setShowingFavorites(false);
    const favoritesButton = document.getElementById('favoritesToggle');
    if (favoritesButton) {
        favoritesButton.classList.remove('active');
    }

    restorePreviousUrl();

    if (!skipRender) {
        renderSpots(state.getSelectedCountry(), '');
    }

    if (!skipScroll) {
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    }
}

// ============================================================================
// THEME MANAGEMENT
// ============================================================================

function initTheme() {
    const savedTheme = state.getTheme();
    const themeToggle = document.getElementById('themeToggle');
    const themeIcon = document.getElementById('themeIcon');

    function updateThemeUI(theme) {
        state.applyTheme(theme);
        if (theme === 'light') {
            themeIcon.innerHTML = constants.THEME_ICON_SUN;
        } else {
            themeIcon.innerHTML = constants.THEME_ICON_MOON;
        }
        state.setTheme(theme);
        mapTileLayer = map.updateTileLayer(leafletMap, mapTileLayer, currentMapLayer);
    }

    // Set the initial theme
    updateThemeUI(savedTheme);

    // Theme toggle event
    themeToggle.addEventListener('click', () => {
        const currentThemeValue = state.getCurrentTheme();
        const newTheme = currentThemeValue === 'dark' ? 'light' : 'dark';
        updateThemeUI(newTheme);
    });

    // Make the logo clickable to go back home with the current country filter
    const headerLogo = document.getElementById('headerLogo');
    if (headerLogo) {
        headerLogo.addEventListener('click', () => {
            const savedCountry = state.getSelectedCountry();
            if (savedCountry === 'all') {
                routing.navigateToHome();
            } else {
                routing.navigateToCountry(savedCountry);
            }
        });
    }
}

// ============================================================================
// LANGUAGE/INTERNATIONALIZATION MANAGEMENT
// ============================================================================

function initLanguage() {
    const savedLanguage = state.getLanguage();
    const languageToggle = document.getElementById('languageToggle');

    function updateLanguageUI(lang) {
        // Update all UI elements with translations
        updateUITranslations();
    }

    function updateUITranslations() {
        // Update page title with current country
        updatePageTitle(state.getSelectedCountry());

        // Update search placeholder
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.placeholder = translations.t('searchPlaceholder');
        }

        // Update tooltips
        sideMenu.updateTranslations(translations.t);

        const liveStationsFilter = document.getElementById('liveStationsFilter');
        if (liveStationsFilter) {
            liveStationsFilter.title = translations.t('liveStationsOnly');
        }

        if (languageToggle) {
            languageToggle.title = translations.t('languageToggleTooltip');
        }

        // Update language code text
        const langCode = document.getElementById('langCode');
        if (langCode) {
            langCode.textContent = translations.t('langCode');
        }

        // Update footer
        footer.updateFooter(translations.t);

        // Update "All" in the dropdown if selected
        if (globalWeatherData.length > 0) {
            populateCountryDropdown(globalWeatherData);
        } else {
            updateSelectedCountryLabel(state.getSelectedCountry());
        }

        // Update dropdown "All" option
        const dropdownMenu = document.getElementById('dropdownMenu');
        if (dropdownMenu) {
            const allOption = dropdownMenu.querySelector('[data-country="all"]');
            if (allOption) {
                allOption.textContent = `🌎 ${translations.t('allCountries')}`;
            }
        }

        // Update loading message if visible
        const loadingText = document.querySelector('.loading-text');
        if (loadingText) {
            loadingText.textContent = translations.t('loadingText');
        }

        // Update app info modal content
        const appInfoModalTitle = document.getElementById('appInfoModalTitle');
        if (appInfoModalTitle) {
            appInfoModalTitle.textContent = translations.t('appInfoModalTitle');
        }

        const appInfoDescription = document.getElementById('appInfoDescription');
        if (appInfoDescription) {
            appInfoDescription.textContent = translations.t('appInfoDescription');
        }

        const appInfoContactTitle = document.getElementById('appInfoContactTitle');
        if (appInfoContactTitle) {
            appInfoContactTitle.textContent = translations.t('appInfoContactTitle');
        }

        const appInfoContactText = document.getElementById('appInfoContactText');
        if (appInfoContactText) {
            appInfoContactText.innerHTML = translations.t('appInfoContactText');
        }

        const appInfoNewSpotTitle = document.getElementById('appInfoNewSpotTitle');
        if (appInfoNewSpotTitle) {
            appInfoNewSpotTitle.textContent = translations.t('appInfoNewSpotTitle');
        }

        const appInfoNewSpotText = document.getElementById('appInfoNewSpotText');
        if (appInfoNewSpotText) {
            appInfoNewSpotText.innerHTML = translations.t('appInfoNewSpotText');
        }

        const appInfoCollaborationTitle = document.getElementById('appInfoCollaborationTitle');
        if (appInfoCollaborationTitle) {
            appInfoCollaborationTitle.textContent = translations.t('appInfoCollaborationTitle');
        }

        const appInfoCollaborationText = document.getElementById('appInfoCollaborationText');
        if (appInfoCollaborationText) {
            appInfoCollaborationText.innerHTML = translations.t('appInfoCollaborationText');
        }

        const appInfoDevTitle = document.getElementById('appInfoDevTitle');
        if (appInfoDevTitle) {
            appInfoDevTitle.textContent = translations.t('appInfoDevTitle');
        }

        const appInfoDevText = document.getElementById('appInfoDevText');
        if (appInfoDevText) {
            appInfoDevText.innerHTML = translations.t('appInfoDevText');
        }

        // Update kite size calculator modal content
        calculator.updateTranslations(translations.t);

        // Update the hero banner close button and its confirmation modal
        const heroClose = document.getElementById('heroClose');
        if (heroClose) {
            heroClose.title = translations.t('heroCloseTooltip');
        }

        [
            'heroCloseModalTitle',
            'heroCloseModalQuestion',
            'heroCloseModalHint',
            'heroCloseModalCancel',
            'heroCloseModalConfirm'
        ].forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                el.textContent = translations.t(id);
            }
        });

        // Update AI modal disclaimer
        const aiDisclaimer = document.querySelector('#aiModal .modal-disclaimer');
        if (aiDisclaimer) {
            aiDisclaimer.textContent = translations.t('aiDisclaimer');
        }

        // Update map layer switcher labels
        map.updateLayerSwitcherLabels();
        map.updateWindOverlayLabels();
        map.updateSpotsToggleLabels();
        map.updateForecastTimelineLabels();
        if (windOverlayDisclaimerEl) {
            windOverlayDisclaimerEl.textContent = translations.t('windOverlayDisclaimer');
        }

        // Re-render spots to update table headers and content
        if (globalWeatherData.length > 0) {
            if (showingFavorites) {
                renderFavorites();
            } else {
                renderSpots(currentFilter, currentSearchQuery, true);
            }
        }

        // Update hero section slogan and label for new language
        const heroSection = document.getElementById('heroSection');
        if (heroSection && heroSection.style.display !== 'none') {
            const heroSlogan = document.getElementById('heroSlogan');
            if (heroSlogan) {
                const lang = state.getLanguage();
                const slogans = HERO_SLOGANS[lang] || HERO_SLOGANS.en;
                heroSlogan.textContent = slogans[Math.floor(Math.random() * slogans.length)];
            }
            const heroSpotLabel = document.getElementById('heroSpotLabel');
            if (heroSpotLabel && heroSpotLabel.dataset.country) {
                const spotName = heroSpotLabel.dataset.spotName;
                heroSpotLabel.textContent = `${spotName}, ${translations.t(heroSpotLabel.dataset.country)}`;
            }
        }
    }

    // Set initial language and update UI
    updateLanguageUI(savedLanguage);

    // Language toggle event
    languageToggle.addEventListener('click', () => {
        const currentLang = state.getLanguage();
        const newLang = currentLang === 'en' ? 'pl' : 'en';
        state.setLanguage(newLang);
        updateLanguageUI(newLang);
    });
}

// ============================================================================
// UI MESSAGE FUNCTIONS
// ============================================================================

function showLoadingMessage() {
    const spotsGrid = document.getElementById('spotsGrid');
    spotsGrid.innerHTML = `
                <div class="loading-message">
                    <div class="loading-spinner"></div>
                    <span class="loading-text">${translations.t('loadingText')}</span>
                </div>
            `;
}

function showErrorMessage(error) {
    const spotsGrid = document.getElementById('spotsGrid');

    let errorTitle = translations.t('errorLoadDataTitle');
    let errorMessage = translations.t('errorLoadDataDescription');

    if (error.message.includes('HTTP Error: 404')) {
        errorTitle = translations.t('errorDataNotFoundTitle');
        errorMessage = translations.t('errorDataNotFoundDescription');
    } else if (error.message.includes('HTTP Error: 500')) {
        errorTitle = translations.t('errorServerTitle');
        errorMessage = translations.t('errorServerDescription');
    } else if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
        errorTitle = translations.t('errorConnectionTitle');
        errorMessage = translations.t('errorConnectionDescription');
    } else if (error.message.includes('JSON') || error.message.includes('Invalid data format')) {
        errorTitle = translations.t('errorDataFormatTitle');
        errorMessage = translations.t('errorDataFormatDescription');
    }

    spotsGrid.innerHTML = `
                <div class="error-message">
                    <span class="error-icon">⚠️</span>
                    <div class="error-title">${errorTitle}</div>
                    <div class="error-description">
                        ${errorMessage}<br/>
                        ${translations.t('errorRefresh')}
                    </div>
                </div>
            `;
}

// ============================================================================
// WEATHER DISPLAY HELPER FUNCTIONS
// ============================================================================

function translateDayName(dayName) {
    const dayMap = {
        'Mon': translations.t('dayMon'),
        'Tue': translations.t('dayTue'),
        'Wed': translations.t('dayWed'),
        'Thu': translations.t('dayThu'),
        'Fri': translations.t('dayFri'),
        'Sat': translations.t('daySat'),
        'Sun': translations.t('daySun'),
        'Today': translations.t('dayToday'),
        'Tomorrow': translations.t('dayTomorrow'),
        'Day 3': translations.t('dayDay3'),
        'Day 4': translations.t('dayDay4'),
        'Day 5': translations.t('dayDay5')
    };
    return dayMap[dayName] || dayName;
}

function formatForecastDateLabel(rawDate) {
    if (!rawDate || typeof rawDate !== 'string') {
        return '';
    }

    const tokens = rawDate.trim().split(/\s+/);
    if (tokens.length < 5) {
        const translated = translateDayName(rawDate);
        return translated || rawDate;
    }

    const [dayToken, dayOfMonthToken, , , timeToken] = tokens;
    const translatedDay = translateDayName(dayToken);
    const formattedDay = dayOfMonthToken.padStart(2, '0');
    const isMobile = window.innerWidth <= 929;

    if (isMobile) {
        const hour = timeToken.split(':')[0];
        const shortDay = translatedDay.substring(0, 2);
        return `${shortDay} ${hour}`.trim();
    }

    return `${formattedDay}. ${translatedDay} ${timeToken}`.trim();
}

const toNumber = weather.toNumber;

// The shared reading plus a label formatted the way this page shows dates.
function getSpotConditions(spot) {
    const conditions = weather.getWindConditions(spot);
    if (!conditions) {
        return null;
    }

    return {
        ...conditions,
        label: conditions.isCurrent
            ? translations.t('nowLabel')
            : formatForecastDateLabel(conditions.forecastDate)
    };
}

// ============================================================================
// COUNTRY DROPDOWN FUNCTIONS
// ============================================================================

function populateCountryDropdown(data) {
    const dropdownMenu = document.getElementById('dropdownMenu');
    const selectedCountry = document.getElementById('selectedCountry');
    availableCountries.clear();

    // Collect all unique countries from the data
    data.forEach(spot => {
        if (spot.country) {
            availableCountries.add(spot.country);
        }
    });

    // Sort countries alphabetically based on translated names
    const sortedCountries = Array.from(availableCountries).sort((a, b) => {
        const nameA = translations.t(a.replace(/\s+/g, ''));
        const nameB = translations.t(b.replace(/\s+/g, ''));
        return nameA.localeCompare(nameB);
    });

    // Get saved country
    const savedCountry = state.getSelectedCountry();

    // Build dropdown HTML
    const allLabel = translations.t('allCountries');
    let dropdownHTML = `<div class="dropdown-option ${savedCountry === 'all' ? 'selected' : ''}" data-country="all">🌎 <span class="dropdown-option-name">${allLabel}</span></div>`;

    sortedCountries.forEach(country => {
        const countryFlag = flags.getCountryFlag(country);
        const isSelected = savedCountry === country ? 'selected' : '';
        const countryName = translations.t(country.replace(/\s+/g, ''));
        dropdownHTML += `<div class="dropdown-option ${isSelected}" data-country="${country}">${countryFlag} <span class="dropdown-option-name">${countryName.toUpperCase()}</span></div>`;
    });

    dropdownMenu.innerHTML = dropdownHTML;

    // Update the selected country text in the button
    updateSelectedCountryLabel(savedCountry);

    // Header badge shares the same counts as the dropdown
    updateHeaderStats(data.length, availableCountries.size, countLiveStations(data));

    // Re-attach event listeners for the new dropdown options
    setupDropdownEvents();
}

// Spots reporting live data from a weather station. Mirrors the backend's
// countLiveStations(): any of wind / gusts / direction present is enough.
function countLiveStations(data) {
    return data.filter(spot => {
        const current = spot.currentConditions;
        if (!current) {
            return false;
        }
        return toNumber(current.wind) > 0
            || toNumber(current.gusts) > 0
            || !!current.direction;
    }).length;
}

// Renders the "N spots · M countries · K stations" badge in the header (desktop)
function updateHeaderStats(spotsCount, countriesCount, stationsCount) {
    const headerStats = document.getElementById('headerStats');
    if (!headerStats) {
        return;
    }

    if (!spotsCount) {
        headerStats.innerHTML = '';
        return;
    }

    const spotsLabel = translations.plural(spotsCount, 'headerStatsSpots');
    const countriesLabel = translations.plural(countriesCount, 'headerStatsCountries');
    const stationsLabel = translations.plural(stationsCount, 'headerStatsStations');
    const stationsTooltip = translations.t('headerStatsStationsTooltip');

    headerStats.innerHTML =
        `<span class="header-stats-value">${spotsCount}</span> ${spotsLabel}` +
        `<span class="header-stats-separator">·</span>` +
        `<span class="header-stats-value">${countriesCount}</span> ${countriesLabel}` +
        `<span class="header-stats-separator">·</span>` +
        `<span class="header-stats-stations" title="${stationsTooltip}">` +
        `<span class="header-stats-value">${stationsCount}</span> ${stationsLabel}</span>`;
}

function updateSelectedCountryLabel(countryKey) {
    const selectedCountry = document.getElementById('selectedCountry');
    if (!selectedCountry) {
        return;
    }

    if (!countryKey || countryKey === 'all') {
        selectedCountry.textContent = `🌎 ${translations.t('allCountries')}`;
        return;
    }

    const countryFlag = flags.getCountryFlag(countryKey);
    const countryName = translations.t(countryKey.replace(/\s+/g, ''));
    selectedCountry.textContent = `${countryFlag} ${countryName.toUpperCase()}`;
}

function setupDropdownEvents() {
    const dropdownOptions = document.querySelectorAll('.dropdown-option');
    const searchInput = document.getElementById('searchInput');

    dropdownOptions.forEach(option => {
        option.addEventListener('click', (e) => {
            e.stopPropagation();

            dropdownOptions.forEach(opt => opt.classList.remove('selected'));
            option.classList.add('selected');

            const country = option.dataset.country;
            updateSelectedCountryLabel(country);

            // Save selected country
            state.setSelectedCountry(country);

            // Update URL
            updateUrlForCountry(country);

            // Update page title
            updatePageTitle(country);

            // Deselect favorites if changing country
            if (showingFavorites) {
                showingFavorites = false;
                state.setShowingFavorites(false);
                document.getElementById('favoritesToggle').classList.remove('active');
            }

            renderSpots(country, searchInput.value, true);

            if (globalWeatherData.length > 0) {
                renderHeroSection();
            }

            // Update map markers if map view is visible
            if (isMapView) {
                updateMapMarkers();
            }
            closeDropdown();

            // Scroll to top smoothly after country selection
            window.scrollTo({
                top: 0,
                behavior: 'smooth'
            });
        });
    });
}

function setupDropdown() {
    const dropdownButton = document.getElementById('dropdownButton');
    const dropdownMenu = document.getElementById('dropdownMenu');

    function closeDropdown() {
        dropdownButton.classList.remove('open', 'active');
        dropdownMenu.classList.remove('open');
    }

    dropdownButton.addEventListener('click', (e) => {
        e.stopPropagation();
        if (dropdownMenu.classList.contains('open')) {
            closeDropdown();
        } else {
            dropdownButton.classList.add('open', 'active');
            dropdownMenu.classList.add('open');
        }
    });

    document.addEventListener('click', (e) => {
        if (!dropdownButton.contains(e.target) && !dropdownMenu.contains(e.target)) {
            closeDropdown();
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && dropdownMenu.classList.contains('open')) {
            closeDropdown();
            dropdownButton.focus();
        }
    });

    // Make closeDropdown available globally
    window.closeDropdown = closeDropdown;
}

// ============================================================================
// MODAL FUNCTIONS
// ============================================================================

function openAppInfoModal() {
    modals.openModal('appInfoModal');
    appShell.loadAppVersion();
}

function closeAppInfoModal() {
    modals.closeModal('appInfoModal');
}

function openAIModal(spotName) {
    const spot = globalWeatherData.find(spot => spot.name === spotName);
    const aiAnalysis = state.getLanguage() === 'pl' ? spot.aiAnalysisPl : spot.aiAnalysisEn;

    if (!spot || !aiAnalysis) return;

    const modalSpotName = document.getElementById('modalSpotName');
    const aiAnalysisContent = document.getElementById('aiAnalysisContent');
    const aiModalDisclaimer = document.getElementById('aiModalDisclaimer');

    modalSpotName.textContent = `${translations.t('aiAnalysisTitle')} - ${spotName}`;
    aiAnalysisContent.innerHTML = aiAnalysis.trim();
    aiModalDisclaimer.textContent = translations.t('aiDisclaimer');

    modals.openModal('aiModal');
}

function closeAIModal() {
    modals.closeModal('aiModal');
}

function openIcmModal(spotName, icmUrl) {
    const modalSpotName = document.getElementById('icmModalSpotName');
    const icmImage = document.getElementById('icmImage');

    modalSpotName.textContent = `${spotName} - ${translations.t('icmForecastTitle')}`;
    icmImage.src = icmUrl;
    icmImage.alt = `${translations.t('icmForecastTitle')} for ${spotName}`;

    modals.openModal('icmModal');
}

function closeIcmModal() {
    modals.closeModal('icmModal');

    // Clear the image source to stop loading
    const icmImage = document.getElementById('icmImage');
    icmImage.src = '';
}

function openHeroCloseModal() {
    modals.openModal('heroCloseModal');
}

function closeHeroCloseModal() {
    modals.closeModal('heroCloseModal');
}

function setupModals() {
    const modalConfigs = [
        { modalId: 'aiModal', closeButtonId: 'modalClose', closeCallback: closeAIModal },
        { modalId: 'appInfoModal', closeButtonId: 'appInfoModalClose', closeCallback: closeAppInfoModal },
        { modalId: 'icmModal', closeButtonId: 'icmModalClose', closeCallback: closeIcmModal },
        { modalId: 'heroCloseModal', closeButtonId: 'heroCloseModalClose', closeCallback: closeHeroCloseModal }
    ];

    modals.setupModals(modalConfigs);
}

// ============================================================================
// SPOT CARD CREATION AND RENDERING
// ============================================================================

function createSpotCard(spot) {
    const card = document.createElement('div');
    card.className = 'spot-card';
    card.dataset.country = translations.t(spot.country.replace(/\s+/g, ''));

    // Check if a spot has wave data
    const hasWaveData = spot.forecast && spot.forecast.some(day => day.wave != null) ||
        (spot.currentConditions && spot.currentConditions.wave != null);

    const spotConditions = getSpotConditions(spot);

    let forecastRows = '';
    if (spot.forecast && Array.isArray(spot.forecast)) {
        spot.forecast.forEach(day => {
            // Skip the "Today" row - it duplicates the "now" readout above the table
            if (typeof day.date === 'string' && day.date.toLowerCase() === 'today') {
                return;
            }

            const windTextClass = weather.getWindClass(day.wind);
            const gustTextClass = weather.getWindClass(day.gusts);

            // Calculate average of base wind and gusts for row background
            const averageWind = (day.wind + day.gusts) / 2;
            const averageWindClass = weather.getWindClass(averageWind);

            // Use average wind class for row background
            const rowWindClass = averageWindClass === 'wind-weak' ? 'weak-wind' :
                averageWindClass === 'wind-moderate' ? 'moderate-wind' :
                    averageWindClass === 'wind-strong' ? 'strong-wind' : 'extreme-wind';

            const tempClass = day.temp >= 18 ? 'temp-positive' : 'temp-negative';
            const windArrow = weather.getWindArrow(day.direction);
            const precipClass = day.precipitation === 0 ? 'precipitation-none' : 'precipitation';

            // Wave classes
            let waveClass = '';
            let waveText = '-';
            if (day.wave != null) {
                if (day.wave < 1.0) {
                    waveClass = 'wave-small';
                } else if (day.wave >= 1.0 && day.wave < 2.0) {
                    waveClass = 'wave-moderate';
                } else {
                    waveClass = 'wave-large';
                }
                waveText = `${day.wave}`;
            }

            forecastRows += `
                        <tr class="${rowWindClass}">
                            <td><strong>${translateDayName(day.date)}</strong></td>
                            <td class="${windTextClass}">${day.wind} kts</td>
                            <td class="${gustTextClass}">${day.gusts} kts</td>
                            <td class="${windTextClass}">
                                <span class="wind-arrow">${windArrow}</span> ${day.direction}
                            </td>
                            <td class="${tempClass}">${day.temp}°C</td>
                            <td class="${precipClass}">${day.precipitation}%</td>
                            ${hasWaveData ? `<td class="${waveClass} wave-col">${waveText}</td>` : ''}
                        </tr>
                    `;
        });
    }

    // Prominent "now" readout (live conditions if available, otherwise forecast-for-now).
    // Promoted above the forecast table so the current wind is the primary, glanceable read.
    let nowReadout = '';
    let statusClass = '';
    if (spotConditions) {
        const baseWind = spotConditions.wind;
        const gustWind = spotConditions.gusts;
        // Color the "now" readings according to the gust wind
        const windColorClass = weather.getWindClass(gustWind);

        const averageWind = (baseWind + gustWind) / 2;
        statusClass = {
            'wind-weak': 'weak',
            'wind-moderate': 'moderate',
            'wind-strong': 'strong',
            'wind-extreme': 'extreme'
        }[weather.getWindClass(averageWind)];

        const hasTemperature = Number.isFinite(spotConditions.temp);
        const tempValue = hasTemperature ? `${spotConditions.temp}°C` : '';
        const windArrow = weather.getWindArrow(spotConditions.direction);
        const isLive = spotConditions.isCurrent;
        const outdated = isLive && spot.currentConditions && date.isConditionsOutdated(spot.currentConditions.date);

        const badge = isLive
            ? `<span class="live-indicator"><span class="live-dot${outdated ? ' outdated' : ''}"></span><strong class="live-text">${translations.t('nowLabel')}</strong></span>`
            : `<span class="now-forecast-badge">${spotConditions.label || ''}</span>`;

        nowReadout = `
                <div class="spot-now status-${statusClass}">
                    <div class="now-main">
                        <span class="now-wind ${windColorClass}">${baseWind}-${gustWind}</span>
                        <span class="now-unit">kts</span>
                    </div>
                    <div class="now-dir">
                        <span class="wind-arrow now-arrow ${windColorClass}">${windArrow}</span>
                        <span class="now-dir-label">${spotConditions.direction || '-'}</span>
                    </div>
                    ${tempValue ? `<span class="now-temp">${tempValue}</span>` : ''}
                    <div class="now-badge">${badge}</div>
                </div>
            `;
    }

    // Check if a spot is favorited
    const isFavorited = isFavorite(spot.name);
    const favoriteClass = isFavorited ? 'favorited' : '';

    if (statusClass) {
        card.classList.add(`status-${statusClass}`);
    }

    card.innerHTML = `
                <div class="drag-handle" draggable="true"><svg class="drag-icon" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg"><circle cx="4" cy="4" r="1.5"/><circle cx="12" cy="4" r="1.5"/><circle cx="4" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/></svg></div>
                <div class="spot-header">
                    <div class="spot-title">
                        <div class="spot-name" onclick="window.location.href='${routing.buildSpotUrl(spot.wgId)}'">${spot.name || 'Unknown Spot'}</div>
                    </div>
                    <div class="spot-meta">
                        <div class="country-tag-wrapper">
                            <div class="favorite-icon ${favoriteClass}" onclick="toggleFavorite('${spot.name}')" title="${isFavorited ? 'Remove from favorites' : 'Add to favorites'}">
                                <svg class="favorite-icon-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                                    <path d="M1.327,12.4,4.887,15,3.535,19.187A3.178,3.178,0,0,0,4.719,22.8a3.177,3.177,0,0,0,3.8-.019L12,20.219l3.482,2.559a3.227,3.227,0,0,0,4.983-3.591L19.113,15l3.56-2.6a3.227,3.227,0,0,0-1.9-5.832H16.4L15.073,2.432a3.227,3.227,0,0,0-6.146,0L7.6,6.568H3.231a3.227,3.227,0,0,0-1.9,5.832Z"/>
                                </svg>
                            </div>
                            <div class="country-tag">${translations.t(spot.country.replace(/\s+/g, '')) || 'Unknown'}</div>
                        </div>
                        <div class="last-updated">${spot.lastUpdated || 'No data'}</div>
                    </div>
                </div>
                <div class="external-links">
                    ${spot.windguruUrl || spot.windguruFallbackUrl ? `<a href="${!spot.windguruUrl && spot.windguruFallbackUrl ? spot.windguruFallbackUrl : spot.windguruUrl}" target="_blank" class="external-link">WG</a>` : ''}
                    ${spot.windfinderUrl ? `<a href="${spot.windfinderUrl}" target="_blank" class="external-link">WF</a>` : ''}
                    ${spot.icmUrl ? `<span class="external-link" onclick="openIcmModal('${spot.name}', '${spot.icmUrl}')">ICM</span>` : ''}
                    ${spot.webcamUrl ? `<a href="${spot.webcamUrl}" target="_blank" class="external-link webcam-link">${translations.t('camLinkLabel')}</a>` : ''}
                    ${spot.locationUrl ? `<a href="${spot.locationUrl}" target="_blank" class="external-link location-link">${translations.t('mapLinkLabel')}</a>` : ''}
                    ${(spot.aiAnalysisEn || spot.aiAnalysisPl) ? `<span class="external-link ai-link" onclick="openAIModal('${spot.name}')">AI</span>` : ''}
                </div>
                ${nowReadout}
                <table class="weather-table">
                    <thead>
                        <tr>
                            <th>${translations.t('dateHeader')}</th>
                            <th>${translations.t('windHeader')}</th>
                            <th>${translations.t('gustsHeader')}</th>
                            <th>${translations.t('directionHeader')}</th>
                            <th>${translations.t('tempHeader')}</th>
                            <th>${translations.t('rainHeader')}</th>
                            ${hasWaveData ? `<th class="wave-col">${translations.t('waveHeader')}</th>` : ''}
                        </tr>
                    </thead>
                    <tbody>
                        ${forecastRows}
                    </tbody>
                </table>
            `;

    return card;
}

const SEARCH_CHAR_MAP = {
    'ł': 'l', 'Ł': 'l',
    'ø': 'o', 'Ø': 'o',
    'æ': 'ae', 'Æ': 'ae',
    'œ': 'oe', 'Œ': 'oe',
    'ß': 'ss', 'ẞ': 'ss',
    'đ': 'd', 'Đ': 'd',
    'ð': 'd', 'Ð': 'd',
    'þ': 'th', 'Þ': 'th',
    'ı': 'i', 'İ': 'i'
};

function normalizeForSearch(text) {
    if (!text) return '';
    let result = '';
    for (const ch of text.toLowerCase()) {
        result += SEARCH_CHAR_MAP[ch] ?? ch;
    }
    return result.normalize('NFD').replace(/[̀-ͯ]/g, '');
}

// A spot counts as "live" when its readout comes from a weather station rather
// than the forecast fallback.
function hasLiveConditions(spot) {
    const conditions = getSpotConditions(spot);
    return !!(conditions && conditions.isCurrent);
}

function filterSpots(data, countryFilter, searchQuery) {
    let filtered = countryFilter === 'all' ? data : data.filter(spot => spot.country === countryFilter);

    if (searchQuery && searchQuery.trim() !== '') {
        const query = normalizeForSearch(searchQuery.trim());
        filtered = filtered.filter(spot => {
            return normalizeForSearch(spot.name).includes(query) ||
                (spot.country && normalizeForSearch(spot.country).includes(query));
        });
    }

    // Applied here so grid, list and map views share the same filter
    if (showOnlyLiveStations) {
        filtered = filtered.filter(hasLiveConditions);
    }

    return filtered;
}

async function renderSpots(filter = 'all', searchQuery = '', skipDelay = false, forceRefresh = false) {
    currentFilter = filter;
    currentSearchQuery = searchQuery;
    const spotsGrid = document.getElementById('spotsGrid');

    // If we already have data and not forcing refresh, use cached data
    if (globalWeatherData.length > 0 && !forceRefresh) {
        const filteredSpots = filterSpots(globalWeatherData, filter, searchQuery);
        displaySpots(filteredSpots, spotsGrid, filter, searchQuery);
        if (!heroInitialized) {
            renderHeroSection();
        }
        return;
    }

    // Show loading message immediately
    showLoadingMessage();

    try {
        const data = await api.fetchAllSpots();

        globalWeatherData = data;

        // Populate country dropdown with available countries
        populateCountryDropdown(data);

        const filteredSpots = filterSpots(data, filter, searchQuery);
        displaySpots(filteredSpots, spotsGrid, filter, searchQuery);
        if (!heroInitialized) {
            renderHeroSection();
        }
    } catch (error) {
        console.error('Failed to load weather:', error.message);
        showErrorMessage(error);
    }
}

// ============================================================================
// LIST VIEW FUNCTIONS
// ============================================================================

function createListHeader() {
    const header = document.createElement('div');
    header.className = 'spots-list-header';

    const columns = [
        { key: '', label: '', sortable: false, isDragHandle: true },
        { key: '', label: '', sortable: false, isCheckbox: true },
        { key: 'spot', label: translations.t('spotHeader'), sortable: true },
        { key: 'wind', label: translations.t('windHeader'), sortable: true },
        { key: 'gust', label: translations.t('gustsHeader'), sortable: true },
        { key: 'direction', label: translations.t('directionHeader'), sortable: true },
        { key: 'temp', label: translations.t('tempHeader'), sortable: true },
        { key: 'rain', label: translations.t('rainHeader'), sortable: true },
        { key: 'country', label: translations.t('countryHeader'), sortable: true },
        { key: '', label: '', sortable: false }
    ];

    columns.forEach(col => {
        const cell = document.createElement('div');
        cell.className = 'header-cell';

        if (col.isDragHandle) {
            // Empty header cell for drag handle column
            cell.className = 'header-cell list-drag-header';
        } else if (col.isCheckbox) {
            // Create checkbox in first column
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.id = 'liveStationsFilter';
            checkbox.className = 'live-stations-checkbox';
            checkbox.checked = showOnlyLiveStations;
            checkbox.title = translations.t('liveStationsOnly');
            checkbox.addEventListener('change', (e) => {
                setLiveStationsFilter(e.target.checked);
            });
            cell.appendChild(checkbox);
        } else if (col.sortable) {
            cell.classList.add('sortable');
            cell.dataset.column = col.key;

            if (listSortColumn === col.key) {
                cell.classList.add(listSortDirection === 'asc' ? 'sorted-asc' : 'sorted-desc');
            }

            cell.addEventListener('click', () => {
                handleListSort(col.key);
            });
            cell.textContent = col.label;
        } else {
            cell.textContent = col.label;
        }

        header.appendChild(cell);
    });

    return header;
}

function createListRow(spot) {
    const row = document.createElement('div');
    row.className = 'list-row';
    row.dataset.country = translations.t(spot.country.replace(/\s+/g, ''));
    row.dataset.spotId = spot.wgId;

    const spotConditions = getSpotConditions(spot);
    const isCurrent = spotConditions && spotConditions.isCurrent;

    if (isCurrent) {
        row.classList.add('current-conditions');
    }

    // Drag handle column
    const dragHandleCell = document.createElement('div');
    dragHandleCell.className = 'list-drag-handle';
    dragHandleCell.innerHTML = `
        <svg class="drag-icon" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">
            <circle cx="4" cy="4" r="1.5"/>
            <circle cx="12" cy="4" r="1.5"/>
            <circle cx="4" cy="12" r="1.5"/>
            <circle cx="12" cy="12" r="1.5"/>
        </svg>
    `;
    row.appendChild(dragHandleCell);

    // Indicator column
    const indicatorCell = document.createElement('div');
    indicatorCell.className = 'list-indicator-cell';
    if (isCurrent) {
        const indicator = document.createElement('div');
        const isOutdated = spot.currentConditions && date.isConditionsOutdated(spot.currentConditions.date);
        indicator.className = `list-indicator${isOutdated ? ' outdated' : ''}`;
        indicatorCell.appendChild(indicator);
    }
    row.appendChild(indicatorCell);

    // Spot name column
    const nameCell = document.createElement('div');
    nameCell.className = 'list-spot-name';
    nameCell.textContent = spot.name || 'Unknown Spot';
    nameCell.onclick = () => routing.navigateToSpot(spot.wgId);
    row.appendChild(nameCell);

    if (spotConditions) {
        // Wind column
        const windCell = document.createElement('div');
        windCell.className = `list-wind ${weather.getWindClass(spotConditions.wind)}`;
        windCell.textContent = `${spotConditions.wind} kts`;
        row.appendChild(windCell);

        // Gust column
        const gustCell = document.createElement('div');
        gustCell.className = `list-gust ${weather.getWindClass(spotConditions.gusts)}`;
        gustCell.textContent = `${spotConditions.gusts} kts`;
        row.appendChild(gustCell);

        // Direction column
        const directionCell = document.createElement('div');
        directionCell.className = 'list-direction';
        const arrow = weather.getWindArrow(spotConditions.direction);
        directionCell.innerHTML = `<span class="wind-arrow">${arrow}</span> ${spotConditions.direction || '-'}`;
        row.appendChild(directionCell);

        // Temperature column
        const tempCell = document.createElement('div');
        const tempClass = Number.isFinite(spotConditions.temp)
            ? (spotConditions.temp >= 18 ? 'temp-positive' : 'temp-negative')
            : '';
        tempCell.className = `list-temp ${tempClass}`;
        tempCell.textContent = Number.isFinite(spotConditions.temp) ? `${spotConditions.temp}°C` : '-';
        row.appendChild(tempCell);

        // Rain column
        const rainCell = document.createElement('div');
        rainCell.className = 'list-rain';
        rainCell.textContent = spotConditions.precipitation !== null && !isCurrent
            ? `${spotConditions.precipitation}%`
            : '-';
        row.appendChild(rainCell);
    } else {
        // No conditions available
        for (let i = 0; i < 5; i++) {
            const emptyCell = document.createElement('div');
            emptyCell.textContent = '-';
            row.appendChild(emptyCell);
        }
    }

    // Country column
    const countryCell = document.createElement('div');
    countryCell.className = 'list-country';
    countryCell.textContent = translations.t(spot.country.replace(/\s+/g, '')) || 'Unknown';
    row.appendChild(countryCell);

    // Favorite column
    const favoriteCell = document.createElement('div');
    favoriteCell.className = 'list-favorite';
    const isFavorited = isFavorite(spot.name);
    const favoriteClass = isFavorited ? 'favorited' : '';

    const favoriteIcon = document.createElement('div');
    favoriteIcon.className = `favorite-icon ${favoriteClass}`;
    favoriteIcon.title = isFavorited ? translations.t('removeFromFavorites') : translations.t('addToFavorites');
    favoriteIcon.innerHTML = `
        <svg class="favorite-icon-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path d="M1.327,12.4,4.887,15,3.535,19.187A3.178,3.178,0,0,0,4.719,22.8a3.177,3.177,0,0,0,3.8-.019L12,20.219l3.482,2.559a3.227,3.227,0,0,0,4.983-3.591L19.113,15l3.56-2.6a3.227,3.227,0,0,0-1.9-5.832H16.4L15.073,2.432a3.227,3.227,0,0,0-6.146,0L7.6,6.568H3.231a3.227,3.227,0,0,0-1.9,5.832Z"/>
        </svg>
    `;

    favoriteIcon.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleFavorite(spot.name);
    });

    favoriteCell.appendChild(favoriteIcon);
    row.appendChild(favoriteCell);

    return row;
}

function handleListSort(column) {
    if (listSortColumn === column) {
        // Toggle direction if same column
        listSortDirection = listSortDirection === 'asc' ? 'desc' : 'asc';
    } else {
        // New column, default to ascending
        listSortColumn = column;
        listSortDirection = 'asc';
    }

    // Clear any saved custom list order when sorting is applied
    clearListOrder();

    // Re-render with sorted data
    renderSpots(currentFilter, currentSearchQuery, true);
}

function clearListOrder() {
    state.removeListOrder(currentFilter, currentSearchQuery);
}

function sortSpots(spots, sortColumn, sortDirection) {
    if (!sortColumn) return spots;

    const sorted = [...spots].sort((a, b) => {
        let aValue, bValue;

        switch (sortColumn) {
            case 'spot':
                aValue = a.name || '';
                bValue = b.name || '';
                return sortDirection === 'asc'
                    ? aValue.localeCompare(bValue)
                    : bValue.localeCompare(aValue);

            case 'wind':
            case 'gust':
            case 'temp':
            case 'rain': {
                const aConditions = getSpotConditions(a);
                const bConditions = getSpotConditions(b);

                if (sortColumn === 'wind') {
                    aValue = aConditions && Number.isFinite(aConditions.wind) ? aConditions.wind : -Infinity;
                    bValue = bConditions && Number.isFinite(bConditions.wind) ? bConditions.wind : -Infinity;
                } else if (sortColumn === 'gust') {
                    aValue = aConditions && Number.isFinite(aConditions.gusts) ? aConditions.gusts : -Infinity;
                    bValue = bConditions && Number.isFinite(bConditions.gusts) ? bConditions.gusts : -Infinity;
                } else if (sortColumn === 'temp') {
                    aValue = aConditions && Number.isFinite(aConditions.temp) ? aConditions.temp : -Infinity;
                    bValue = bConditions && Number.isFinite(bConditions.temp) ? bConditions.temp : -Infinity;
                } else if (sortColumn === 'rain') {
                    aValue = aConditions && Number.isFinite(aConditions.precipitation) ? aConditions.precipitation : -Infinity;
                    bValue = bConditions && Number.isFinite(bConditions.precipitation) ? bConditions.precipitation : -Infinity;
                }

                return sortDirection === 'asc' ? aValue - bValue : bValue - aValue;
            }

            case 'direction': {
                const aConditions = getSpotConditions(a);
                const bConditions = getSpotConditions(b);
                aValue = aConditions && aConditions.direction ? aConditions.direction : '';
                bValue = bConditions && bConditions.direction ? bConditions.direction : '';
                return sortDirection === 'asc'
                    ? aValue.localeCompare(bValue)
                    : bValue.localeCompare(aValue);
            }

            case 'country': {
                const aCountry = a.country || '';
                const bCountry = b.country || '';
                aValue = translations.t(aCountry.replace(/\s+/g, '')) || aCountry;
                bValue = translations.t(bCountry.replace(/\s+/g, '')) || bCountry;
                return sortDirection === 'asc'
                    ? aValue.localeCompare(bValue)
                    : bValue.localeCompare(aValue);
            }

            default:
                return 0;
        }
    });

    return sorted;
}

// "Firing now" score: average of current/live wind and gusts. Spots without usable
// conditions sink to the bottom. Used for the default main-page ordering.
function firingScore(spot) {
    const conditions = getSpotConditions(spot);
    if (!conditions || !Number.isFinite(conditions.wind) || !Number.isFinite(conditions.gusts)) {
        return -Infinity;
    }
    return (conditions.wind + conditions.gusts) / 2;
}

function sortByFiringNow(spots) {
    return [...spots].sort((a, b) => firingScore(b) - firingScore(a));
}

// ============================================================================
// INCREMENTAL (LAZY) RENDERING
// ============================================================================

// Rendering every spot up front costs a full layout pass over hundreds of cards,
// each carrying a multi-day forecast table. Instead the first batch is rendered
// eagerly (enough to fill any viewport) and the rest follow as the user scrolls
// towards them.
const LAZY_INITIAL_BATCH_SIZE = 24;
const LAZY_BATCH_SIZE = 12;
// Render the next batch well before it scrolls into view so cards are already
// in place by the time they would become visible.
const LAZY_ROOT_MARGIN = '1200px';

let lazyObserver = null;
let lazyObservedElement = null;
// Spots that have been ordered and filtered but not yet turned into DOM nodes.
// Order-saving and background refresh both need to know about these.
let lazyPendingSpots = [];

function cancelLazyRendering() {
    if (lazyObserver) {
        lazyObserver.disconnect();
        lazyObserver = null;
    }
    lazyObservedElement = null;
    lazyPendingSpots = [];
}

// Renders `spots` into `container` in batches. `createElementFn` turns a single
// spot into its DOM node. The last rendered node is observed; when it approaches
// the viewport the next batch is appended and the new last node is observed.
function renderSpotsIncrementally(container, spots, createElementFn) {
    cancelLazyRendering();

    const appendBatch = (batch) => {
        const fragment = document.createDocumentFragment();
        batch.forEach(spot => fragment.appendChild(createElementFn(spot)));
        container.appendChild(fragment);
    };

    // Without IntersectionObserver support there is nothing to drive the batches,
    // so fall back to rendering everything at once.
    if (typeof IntersectionObserver === 'undefined') {
        appendBatch(spots);
        return;
    }

    lazyPendingSpots = [...spots];
    appendBatch(lazyPendingSpots.splice(0, LAZY_INITIAL_BATCH_SIZE));

    if (lazyPendingSpots.length === 0) return;

    lazyObserver = new IntersectionObserver((entries) => {
        if (!entries.some(entry => entry.isIntersecting)) return;
        appendBatch(lazyPendingSpots.splice(0, LAZY_BATCH_SIZE));
        observeLastChild(container);
    }, { rootMargin: LAZY_ROOT_MARGIN });

    observeLastChild(container);
}

// Re-points the observer whenever something moved or replaced rendered nodes: the
// background refresh swaps every card, drag-and-drop reorders them. An element that
// was detached, or that is no longer last, never triggers the next batch again,
// which silently stops lazy rendering until a full page reload.
function ensureLazySentinel(container) {
    if (!lazyObserver || !lazyObservedElement) return;
    if (lazyObservedElement === container.lastElementChild) return;
    observeLastChild(container);
}

function observeLastChild(container) {
    if (!lazyObserver) return;

    if (lazyObservedElement) {
        lazyObserver.unobserve(lazyObservedElement);
        lazyObservedElement = null;
    }

    if (lazyPendingSpots.length === 0) {
        cancelLazyRendering();
        return;
    }

    const lastChild = container.lastElementChild;
    if (!lastChild) return;

    lazyObservedElement = lastChild;
    lazyObserver.observe(lastChild);
}

// Reproduces the ordering the DOM-based reordering used to produce: spots present
// in the saved order are placed last in that order, and anything unknown to it
// (a newly added spot, say) keeps its position ahead of them. Applying the order
// to the data rather than the rendered nodes is what makes lazy rendering safe.
function applySavedOrder(spots, savedOrder, keyOf) {
    if (!savedOrder || savedOrder.length === 0) return spots;

    const remaining = new Map();
    spots.forEach(spot => {
        const key = keyOf(spot);
        if (!remaining.has(key)) remaining.set(key, []);
        remaining.get(key).push(spot);
    });

    const ordered = [];
    savedOrder.forEach(key => {
        const bucket = remaining.get(key);
        if (bucket && bucket.length > 0) {
            ordered.push(bucket.shift());
        }
    });

    const orderedSet = new Set(ordered);
    return [...spots.filter(spot => !orderedSet.has(spot)), ...ordered];
}

function displaySpots(filteredSpots, spotsGrid, filter, searchQuery) {
    cancelLazyRendering();
    spotsGrid.innerHTML = '';
    if (filteredSpots.length === 0) {
        const message = searchQuery ?
            `${translations.t('errorNoSpotsSearchDescription')} "${searchQuery}"` :
            `${translations.t('errorNoSpotsDescription')}`;
        spotsGrid.innerHTML = `
                    <div class="error-message">
                        <span class="error-icon">🔍</span>
                        <div class="error-title">${translations.t('errorNoSpotsTitle')}</div>
                        <div class="error-description">
                            ${message}<br/>
                            ${translations.t('errorTryAdjusting')}
                        </div>
                    </div>
                `;
    } else {
        // Check if all spots have empty forecasts
        const allForecastsEmpty = filteredSpots.every(spot =>
            !spot.forecast || spot.forecast.length === 0
        );

        if (allForecastsEmpty) {
            spotsGrid.innerHTML = `
                    <div class="loading-message">
                        <div class="loading-spinner"></div>
                        <span class="loading-text">${translations.t('loadingText')}</span>
                    </div>
                `;
            // Retry loading after 5 seconds
            setTimeout(() => {
                renderSpots(filter, searchQuery, false, true);
            }, 5000);
        } else {
            // Check current view mode and render accordingly
            if (currentViewMode === 'list') {
                // Explicit column sort wins; otherwise default to "firing now" when enabled
                let sortedSpots = listSortColumn
                    ? sortSpots(filteredSpots, listSortColumn, listSortDirection)
                    : (firingSortEnabled ? sortByFiringNow(filteredSpots) : filteredSpots);

                // Saved manual order only applies when not sorting by firing/column
                if (!listSortColumn && !firingSortEnabled) {
                    sortedSpots = applySavedOrder(
                        sortedSpots,
                        state.getListOrder(currentFilter, currentSearchQuery),
                        spot => String(spot.wgId)
                    );
                }

                // Render list view
                spotsGrid.appendChild(createListHeader());
                renderSpotsIncrementally(spotsGrid, sortedSpots, createListRow);
            } else {
                // Render grid view; "firing now" default ordering unless disabled
                let gridSpots = firingSortEnabled ? sortByFiringNow(filteredSpots) : filteredSpots;

                // Saved manual card order only applies when firing sort is off
                if (!firingSortEnabled) {
                    gridSpots = applySavedOrder(
                        gridSpots,
                        state.getSpotOrder(gridColumnMode(spotsGrid), currentFilter, currentSearchQuery),
                        spot => spot.name
                    );
                }

                renderSpotsIncrementally(spotsGrid, gridSpots, createSpotCard);
            }
        }
    }
}

// ============================================================================
// SEARCH FUNCTIONALITY
// ============================================================================

function setupSearch() {
    const searchInput = document.getElementById('searchInput');
    const searchClear = document.getElementById('searchClear');
    let searchTimeout;

    searchInput.addEventListener('focus', () => {
        if (isMapView) {
            hideMapView({ skipRender: true });
        }
    });

    searchInput.addEventListener('input', (e) => {
        const value = e.target.value;

        if (isMapView) {
            hideMapView({ skipRender: true });
        }

        // Show/hide clear button
        if (value.trim() !== '') {
            searchClear.classList.add('visible');
        } else {
            searchClear.classList.remove('visible');
        }

        // Deselect favorites if searching
        if (showingFavorites && value.trim() !== '') {
            showingFavorites = false;
            document.getElementById('favoritesToggle').classList.remove('active');
        }

        // Clear existing timeout
        if (searchTimeout) {
            clearTimeout(searchTimeout);
        }

        // Add delay before triggering search
        searchTimeout = setTimeout(() => {
            renderSpots(currentFilter, value, true);
            window.scrollTo(0, 0);
        }, 300);
    });

    searchClear.addEventListener('click', () => {
        if (isMapView) {
            hideMapView({ skipRender: true });
        }

        searchInput.value = '';
        searchClear.classList.remove('visible');
        renderSpots(currentFilter, '');
        window.scrollTo(0, 0);
        searchInput.focus();
    });

    // Clear search on an Escape key
    searchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && searchInput.value !== '') {
            if (isMapView) {
                hideMapView({ skipRender: true });
            }
            searchInput.value = '';
            searchClear.classList.remove('visible');
            renderSpots(currentFilter, '');
        }
    });
}

// ============================================================================
// DRAG AND DROP FUNCTIONALITY
// ============================================================================

function setupDragAndDrop() {
    const spotsGrid = document.getElementById('spotsGrid');
    let draggedCard = null;
    let dragGhost = null;

    spotsGrid.addEventListener('dragstart', (e) => {
        const handle = e.target.closest('.drag-handle');
        if (!handle) {
            e.preventDefault();
            return;
        }

        draggedCard = e.target.closest('.spot-card');
        if (draggedCard) {
            draggedCard.classList.add('dragging');
            e.dataTransfer.effectAllowed = 'move';

            // Create a visual clone of the dragged card
            dragGhost = draggedCard.cloneNode(true);
            dragGhost.classList.add('drag-ghost');
            dragGhost.classList.remove('dragging');
            dragGhost.style.width = draggedCard.offsetWidth + 'px';
            dragGhost.style.position = 'fixed';
            dragGhost.style.left = '-9999px';
            dragGhost.style.top = '-9999px';
            document.body.appendChild(dragGhost);

            // Set the drag image to the clone
            e.dataTransfer.setDragImage(dragGhost, e.offsetX, e.offsetY);
        }
    });

    spotsGrid.addEventListener('dragend', () => {
        if (draggedCard) {
            draggedCard.classList.remove('dragging');
            draggedCard = null;
            saveCardOrder();
            // Dragging can move the observed card away from the end of the grid.
            ensureLazySentinel(spotsGrid);
        }

        // Remove the ghost element
        if (dragGhost) {
            dragGhost.remove();
            dragGhost = null;
        }
    });

    spotsGrid.addEventListener('dragover', (e) => {
        e.preventDefault();
        const afterElement = getDragAfterElement(spotsGrid, e.clientX, e.clientY);
        if (draggedCard && afterElement == null) {
            spotsGrid.appendChild(draggedCard);
        } else if (draggedCard && afterElement) {
            spotsGrid.insertBefore(draggedCard, afterElement);
        }
    });

    function getDragAfterElement(container, x, y) {
        const draggableElements = [...container.querySelectorAll('.spot-card:not(.dragging)')];

        let closestElement = null;
        let closestOffset = Number.NEGATIVE_INFINITY;

        draggableElements.forEach(child => {
            const box = child.getBoundingClientRect();
            const centerY = box.top + box.height / 2;

            // Calculate vertical and horizontal offsets
            const offsetY = y - centerY;
            const offsetX = x - (box.left + box.width / 2);

            // We want elements that are AFTER the cursor position
            // This means elements that are either:
            // 1. Below the cursor (offsetY < 0)
            // 2. To the right of the cursor in the same row (offsetX < 0 and on the same row)

            if (offsetY < 0) {
                // Element is below cursor - prioritize by vertical distance
                const offset = offsetY;
                if (offset > closestOffset) {
                    closestOffset = offset;
                    closestElement = child;
                }
            } else if (offsetX < 0 && offsetY < box.height / 2) {
                // Element is to the right and roughly in the same row
                // Use a combined offset that prioritizes horizontal over vertical
                const offset = offsetX / 2 + offsetY;
                if (offset > closestOffset) {
                    closestOffset = offset;
                    closestElement = child;
                }
            }
        });

        return closestElement;
    }
}

function gridColumnMode(spotsGrid) {
    return spotsGrid.classList.contains('three-columns') ? '3col' : '2col';
}

function saveCardOrder() {
    const spotsGrid = document.getElementById('spotsGrid');
    const cards = spotsGrid.querySelectorAll('.spot-card');
    const order = Array.from(cards).map(card => {
        return card.querySelector('.spot-name').textContent;
    });

    // Spots still queued for lazy rendering sit after everything on screen, so
    // append them to keep the saved order complete rather than truncating it.
    lazyPendingSpots.forEach(spot => order.push(spot.name));

    state.saveSpotOrder(gridColumnMode(spotsGrid), currentFilter, currentSearchQuery, order);
}

// ============================================================================
// LIST VIEW DRAG AND DROP FUNCTIONALITY
// ============================================================================

function setupListDragAndDrop() {
    const spotsGrid = document.getElementById('spotsGrid');
    let draggedRow = null;
    let dragGhost = null;
    let ghostOffsetY = 0;
    let isDragging = false;

    spotsGrid.addEventListener('mousedown', handleDragStart);
    spotsGrid.addEventListener('touchstart', handleDragStart, { passive: false });

    function handleDragStart(e) {
        // Only handle list view
        if (currentViewMode !== 'list') return;

        const handle = e.target.closest('.list-drag-handle');
        if (!handle) return;

        const row = handle.closest('.list-row');
        if (!row) return;

        e.preventDefault();
        isDragging = true;
        draggedRow = row;

        // Clear sorting state when drag starts
        if (listSortColumn) {
            listSortColumn = null;
            listSortDirection = 'asc';
            // Remove sorting indicators from header
            const headerCells = spotsGrid.querySelectorAll('.header-cell.sortable');
            headerCells.forEach(cell => {
                cell.classList.remove('sorted-asc', 'sorted-desc');
            });
        }

        const clientY = e.type === 'touchstart' ? e.touches[0].clientY : e.clientY;

        // Create ghost element
        const rect = row.getBoundingClientRect();
        ghostOffsetY = clientY - rect.top;
        dragGhost = row.cloneNode(true);
        dragGhost.classList.add('list-row-ghost');
        dragGhost.style.width = rect.width + 'px';
        dragGhost.style.left = rect.left + 'px';
        dragGhost.style.top = (clientY - ghostOffsetY) + 'px';
        document.body.appendChild(dragGhost);

        row.classList.add('dragging');

        document.addEventListener('mousemove', handleDragMove);
        document.addEventListener('mouseup', handleDragEnd);
        document.addEventListener('touchmove', handleDragMove, { passive: false });
        document.addEventListener('touchend', handleDragEnd);
    }

    function handleDragMove(e) {
        if (!isDragging || !draggedRow || !dragGhost) return;

        e.preventDefault();
        const clientY = e.type === 'touchmove' ? e.touches[0].clientY : e.clientY;

        // Move ghost to follow cursor
        dragGhost.style.top = (clientY - ghostOffsetY) + 'px';

        // Find the row we're hovering over
        const rows = Array.from(spotsGrid.querySelectorAll('.list-row:not(.dragging)'));
        const afterRow = getListAfterElement(rows, clientY);

        if (afterRow === null) {
            // Append to end (after header)
            spotsGrid.appendChild(draggedRow);
        } else if (afterRow !== draggedRow) {
            spotsGrid.insertBefore(draggedRow, afterRow);
        }
    }

    function handleDragEnd() {
        if (!isDragging) return;

        isDragging = false;

        if (draggedRow) {
            draggedRow.classList.remove('dragging');
            saveListOrderFn();
            draggedRow = null;
            // Dragging can move the observed row away from the end of the list.
            ensureLazySentinel(spotsGrid);
        }

        if (dragGhost) {
            dragGhost.remove();
            dragGhost = null;
        }

        document.removeEventListener('mousemove', handleDragMove);
        document.removeEventListener('mouseup', handleDragEnd);
        document.removeEventListener('touchmove', handleDragMove);
        document.removeEventListener('touchend', handleDragEnd);
    }

    function getListAfterElement(rows, y) {
        let closestElement = null;
        let closestOffset = Number.NEGATIVE_INFINITY;

        rows.forEach(row => {
            const box = row.getBoundingClientRect();
            const centerY = box.top + box.height / 2;
            const offset = y - centerY;

            // Find the element that is just after the cursor
            if (offset < 0 && offset > closestOffset) {
                closestOffset = offset;
                closestElement = row;
            }
        });

        return closestElement;
    }
}

function saveListOrderFn() {
    const spotsGrid = document.getElementById('spotsGrid');
    const rows = spotsGrid.querySelectorAll('.list-row');
    const order = Array.from(rows).map(row => row.dataset.spotId);

    // Rows still queued for lazy rendering follow the ones already on screen.
    lazyPendingSpots.forEach(spot => order.push(String(spot.wgId)));

    state.saveListOrder(currentFilter, currentSearchQuery, order);
}

// ============================================================================
// BACKGROUND AUTO-REFRESH FUNCTIONALITY
// ============================================================================

// Swaps already-rendered cards for ones built from fresh data, and re-points the
// lazy-render queue at the fresh spot objects so batches rendered later do not
// show data from before the refresh.
function replaceRenderedCards(spotsGrid, freshSpots) {
    const freshByName = new Map(freshSpots.map(spot => [spot.name, spot]));

    spotsGrid.querySelectorAll('.spot-card').forEach(card => {
        const spotName = card.querySelector('.spot-name');
        const updatedSpot = spotName && freshByName.get(spotName.textContent);
        if (updatedSpot) {
            card.replaceWith(createSpotCard(updatedSpot));
        }
    });

    lazyPendingSpots = lazyPendingSpots.map(spot => freshByName.get(spot.name) || spot);

    // The card the observer was watching was just replaced by a fresh node, so the
    // observer has to be moved onto the new last child or no further batch loads.
    ensureLazySentinel(spotsGrid);
}

async function refreshDataInBackground() {
    try {
        // Fetch new data silently
        const freshData = await api.fetchAllSpots();

        // Update global data
        globalWeatherData = freshData;

        // Update country dropdown without changing selection
        populateCountryDropdown(freshData);

        // Silently update the current view
        const spotsGrid = document.getElementById('spotsGrid');
        if (showingFavorites) {
            // Update favorites without re-rendering (to avoid disruption)
            const favorites = state.getFavorites();
            replaceRenderedCards(spotsGrid, globalWeatherData.filter(spot => favorites.includes(spot.name)));
        } else {
            // Update regular filtered view
            replaceRenderedCards(spotsGrid, filterSpots(globalWeatherData, currentFilter, currentSearchQuery));
        }

        console.log('Data refreshed in background at', new Date().toLocaleTimeString());
    } catch (error) {
        console.error('Background refresh failed:', error);
        // Silently fail - don't disturb the user
    }
}

function startAutoRefresh() {
    // Clear any existing interval
    if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
    }

    // Start a new interval
    autoRefreshInterval = setInterval(() => {
        refreshDataInBackground();
    }, constants.AUTO_REFRESH_INTERVAL);

    console.log('Auto-refresh started: updating every', constants.AUTO_REFRESH_INTERVAL / 1000, 'seconds');
}

function stopAutoRefresh() {
    if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
        autoRefreshInterval = null;
        console.log('Auto-refresh stopped');
    }
}

// ============================================================================
// BROWSER NAVIGATION (BACK/FORWARD) HANDLING
// ============================================================================

function handlePopState() {
    window.addEventListener('popstate', () => {
        // Check if we're navigating to /starred
        if (routing.isStarredUrl()) {
            if (!showingFavorites) {
                showingFavorites = true;
                state.setShowingFavorites(true);
                document.getElementById('favoritesToggle').classList.add('active');
                renderFavorites();
            }
        } else {
            // Navigating away from starred
            if (showingFavorites) {
                showingFavorites = false;
                state.setShowingFavorites(false);
                document.getElementById('favoritesToggle').classList.remove('active');
            }

            // Check if there's a country in URL
            const urlCountry = routing.getCountryFromUrl();
            if (urlCountry) {
                const actualCountry = findCountryByNormalizedName(urlCountry);
                if (actualCountry) {
                    state.setSelectedCountry(actualCountry);
                    updatePageTitle(actualCountry);
                    renderSpots(actualCountry, '', true);
                }
            } else {
                // Default to saved country or 'all'
                const savedCountry = state.getSelectedCountry();
                updatePageTitle(savedCountry);
                renderSpots(savedCountry, '', true);
            }
        }
    });
}

// ============================================================================
// MOBILE HAMBURGER MENU
// ============================================================================

function setupHamburgerMenu() {
    const hamburgerMenu = document.getElementById('hamburgerMenu');
    const headerControls = document.getElementById('headerControls');
    const mainContent = document.querySelector('.main-content');

    if (!hamburgerMenu) return;

    hamburgerMenu.addEventListener('click', () => {
        const isOpen = headerControls.classList.contains('show');

        if (isOpen) {
            headerControls.classList.remove('show');
            hamburgerMenu.classList.remove('active');
            hamburgerMenu.textContent = '☰';
            if (mainContent) {
                mainContent.classList.remove('menu-open');
            }
            // Closing the drawer pulls the map back up by the height of the
            // drawer - park it under the header again, slider and all. The
            // header is still mid-collapse right now, so where the map belongs
            // is only known once the drawer has finished folding away.
            if (isMapView) {
                afterDrawerCollapse(headerControls, scrollMapIntoView);
            }
        } else {
            headerControls.classList.add('show');
            hamburgerMenu.classList.add('active');
            hamburgerMenu.textContent = '✕';
            if (mainContent) {
                mainContent.classList.add('menu-open');
            }
        }
    });
}

// Runs once the drawer has finished its max-height transition. The timer is
// not a safety net but the second half of the rule: a drawer that was already
// closed, or a browser that skips the animation, fires no transitionend at all.
const DRAWER_COLLAPSE_MS = 350;

function afterDrawerCollapse(headerControls, callback) {
    let handled = false;

    const run = () => {
        if (handled) return;
        handled = true;
        headerControls.removeEventListener('transitionend', onEnd);
        callback();
    };

    const onEnd = (event) => {
        // Children fade out on their own transition - only the drawer counts
        if (event.target === headerControls) run();
    };

    headerControls.addEventListener('transitionend', onEnd);
    setTimeout(run, DRAWER_COLLAPSE_MS);
}

// ============================================================================
// STICKY LEFT MENU
// Implementation lives in ../common/sideMenu.js (shared with the spot page).
// ============================================================================

// ============================================================================
// COLUMN LAYOUT TOGGLE (2 vs 3 columns)
// ============================================================================

// Global variables for list view
let currentViewMode = 'grid'; // 'grid' or 'list'
let desktopViewMode = 'grid'; // Store desktop preference separately
let listSortColumn = null;
let listSortDirection = 'asc';
let showOnlyLiveStations = state.getLiveStationsOnly(); // Keep only spots backed by a live weather station
let firingSortEnabled = state.getFiringSort(); // Default main-page ordering by live wind strength

function isMobileView() {
    return window.innerWidth <= 929;
}

// Below this width two grid tiles can no longer fit their content on a single
// line (each cell drops under the ~340px the card is designed for), so the
// spots switch to the list view. Above it they render as a grid, which fits as
// many columns as the window allows.
function isListBreakpoint() {
    return window.innerWidth <= 1024;
}

function setupFiringSortToggle() {
    const btn = document.getElementById('firingSortToggle');
    if (!btn) {
        return;
    }

    btn.classList.toggle('active', firingSortEnabled);

    btn.addEventListener('click', () => {
        firingSortEnabled = !firingSortEnabled;
        state.setFiringSort(firingSortEnabled);
        btn.classList.toggle('active', firingSortEnabled);

        if (isMapView) {
            hideMapView({ skipRender: true });
        }

        if (showingFavorites) {
            renderFavorites();
        } else {
            renderSpots(currentFilter, currentSearchQuery, true);
        }
    });
}

// Single entry point for the live-stations filter so the sticky-menu button and
// the list-header checkbox stay in sync whichever one flipped it.
function setLiveStationsFilter(enabled) {
    showOnlyLiveStations = enabled;
    state.setLiveStationsOnly(enabled);

    const btn = document.getElementById('liveStationsToggle');
    if (btn) {
        btn.classList.toggle('active', enabled);
    }

    const checkbox = document.getElementById('liveStationsFilter');
    if (checkbox) {
        checkbox.checked = enabled;
    }

    if (isMapView) {
        updateMapMarkers();
    } else if (showingFavorites) {
        renderFavorites();
    } else {
        renderSpots(currentFilter, currentSearchQuery, true);
    }
}

function setupLiveStationsToggle() {
    const btn = document.getElementById('liveStationsToggle');
    if (!btn) {
        return;
    }

    btn.classList.toggle('active', showOnlyLiveStations);

    btn.addEventListener('click', () => {
        setLiveStationsFilter(!showOnlyLiveStations);
    });
}

// Switch both drawer filters off without rendering: the caller that clears them
// (the list entry) re-renders once on its own.
function clearListFilters() {
    firingSortEnabled = false;
    state.setFiringSort(false);
    const firingBtn = document.getElementById('firingSortToggle');
    if (firingBtn) {
        firingBtn.classList.remove('active');
    }

    showOnlyLiveStations = false;
    state.setLiveStationsOnly(false);
    const liveBtn = document.getElementById('liveStationsToggle');
    if (liveBtn) {
        liveBtn.classList.remove('active');
    }
    const liveCheckbox = document.getElementById('liveStationsFilter');
    if (liveCheckbox) {
        liveCheckbox.checked = false;
    }
}

function setupColumnToggle() {
    const listViewBtn = document.getElementById('listViewBtn');
    const gridViewBtn = document.getElementById('gridViewBtn');
    if (!listViewBtn || !gridViewBtn) {
        return;
    }

    const spotsGrid = document.getElementById('spotsGrid');

    // Load saved desktop preference or default to grid
    desktopViewMode = state.getDesktopViewMode();

    // Set initial view based on viewport width
    if (isListBreakpoint()) {
        currentViewMode = 'list'; // Always list below the grid breakpoint
    } else {
        currentViewMode = desktopViewMode; // Use saved preference on full desktop
    }

    function updateView() {
        if (currentViewMode === 'list') {
            spotsGrid.classList.remove('spots-grid', 'three-columns');
            spotsGrid.classList.add('spots-list');
            listViewBtn.classList.add('active');
            gridViewBtn.classList.remove('active');
        } else {
            spotsGrid.classList.remove('spots-list');
            spotsGrid.classList.add('spots-grid', 'three-columns');
            listViewBtn.classList.remove('active');
            gridViewBtn.classList.add('active');
        }
    }

    updateView();

    function switchToView(mode) {
        if (isMapView) {
            hideMapView({ skipRender: true });
        }

        currentViewMode = mode;

        // Only save to desktop preference when above the grid breakpoint
        if (!isListBreakpoint()) {
            desktopViewMode = currentViewMode;
            state.setDesktopViewMode(desktopViewMode);
        }

        updateView();

        // Re-render preserving favorites filter if active
        if (showingFavorites) {
            renderFavorites();
        } else {
            renderSpots(currentFilter, currentSearchQuery, true);
        }
    }

    // In the mobile drawer the list entry is the only way back to the plain
    // spots list, so it also drops the filters the drawer can set. On desktop
    // it stays what it has always been: the grid/list switch.
    listViewBtn.addEventListener('click', () => {
        if (isMobileView()) {
            clearListFilters();
        }
        switchToView('list');
    });
    gridViewBtn.addEventListener('click', () => switchToView('grid'));

    // Handle viewport resize
    let resizeTimer;
    let wasListView = isListBreakpoint();

    window.addEventListener('resize', () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(() => {
            const isNowListView = isListBreakpoint();
            const wasMobile = currentViewMode === 'list' && isMobileView();

            // List view is forced at <= 1024px, where two grid tiles no longer fit
            if (isNowListView && !wasListView) {
                // Just dropped below the grid breakpoint - switch to list view
                if (currentViewMode === 'grid') {
                    currentViewMode = 'list';
                    updateView();
                    renderSpots(currentFilter, currentSearchQuery, true);
                }
                wasListView = true;
            } else if (!isNowListView && wasListView) {
                // Just rose above the grid breakpoint - restore desktop view
                if (currentViewMode !== desktopViewMode) {
                    currentViewMode = desktopViewMode;
                    updateView();
                    renderSpots(currentFilter, currentSearchQuery, true);
                }
                wasListView = false;
            } else if (isMobileView()) {
                // Mobile view (<=929px) - always use list view
                if (currentViewMode !== 'list') {
                    currentViewMode = 'list';
                    updateView();
                    renderSpots(currentFilter, currentSearchQuery, true);
                }
            } else if (!isMobileView() && wasMobile) {
                // Was in mobile mode, now expanding - use desktop preference
                currentViewMode = desktopViewMode;
                updateView();
                renderSpots(currentFilter, currentSearchQuery, true);
            }
        }, 250); // Debounce resize events
    });
}

// ============================================================================
// SPONSORS FUNCTIONALITY
// ============================================================================

async function renderMainSponsors() {
    const sponsorsContainer = document.getElementById('sponsorsContainer');
    if (!sponsorsContainer) {
        return;
    }

    const sponsors = await api.fetchSponsors();

    if (!sponsors || sponsors.length === 0) {
        sponsorsContainer.innerHTML = '';
        return;
    }

    let sponsorsHTML = '<div class="sponsors-container"><div class="sponsors-list">';

    for (const sponsor of sponsors) {
        sponsorsHTML += `
                <div class="sponsor-item">
                    <a href="${sponsor.link}" target="_blank" rel="noopener noreferrer" class="sponsor-link">
                        <span class="sponsor-name">${sponsor.name}</span>
                    </a>
                </div>
            `;
    }

    sponsorsHTML += '</div></div>';
    sponsorsContainer.innerHTML = sponsorsHTML;
}

// ============================================================================
// URL HANDLING AND ROUTING
// ============================================================================

function handleCountryURL() {
    // Check for country in URL
    const urlCountry = routing.getCountryFromUrl();

    if (urlCountry) {
        // Wait for data to be loaded to validate country
        api.fetchAllSpots().then(data => {
            globalWeatherData = data;
            populateCountryDropdown(data);

            // Find the actual country name from URL
            const actualCountry = findCountryByNormalizedName(urlCountry);

            if (actualCountry) {
                // Valid country in URL
                state.setSelectedCountry(actualCountry);
                updatePageTitle(actualCountry);
                renderSpots(actualCountry, '', true);
            } else {
                // Invalid country in URL
                showInvalidCountryError(urlCountry);
            }

            // Start auto-refresh after an initial load
            startAutoRefresh();
        }).catch(error => {
            console.error('Failed to load weather:', error.message);
            showErrorMessage(error);
        });
    } else {
        // No country or starred in URL - use saved/default country
        const savedCountry = state.getSelectedCountry();
        updateUrlForCountry(savedCountry);
        updatePageTitle(savedCountry);
        renderSpots(savedCountry);

        // Start auto-refresh after an initial load
        startAutoRefresh();
    }
}

function handleStarredURL() {
    if (routing.isMapUrl()) {
        handleMapRoute();
        return;
    }

    // Check for /starred URL or persisted favorites state
    if (routing.isStarredUrl() || state.getShowingFavorites()) {
        // Load favorites directly
        showingFavorites = true;
        state.setShowingFavorites(true);
        const favoritesToggle = document.getElementById('favoritesToggle');
        if (favoritesToggle) {
            favoritesToggle.classList.add('active');
        }
        // Ensure URL reflects starred state
        if (!routing.isStarredUrl()) {
            routing.pushStarredUrl();
        }
        document.title = `${translations.t('favoritesToggleTooltip')} - VARUN.SURF`;
        renderFavorites();
        // Start auto-refresh after an initial load
        startAutoRefresh();
    } else {
        handleCountryURL();
    }
}

function handleMapRoute() {
    const savedCountry = state.getSelectedCountry();

    renderSpots(savedCountry, '', true)
        .then(() => {
            updatePageTitle(savedCountry);
            showMapView();
            startAutoRefresh();
        })
        .catch(error => {
            console.error('Failed to initialize map route:', error);
        });
}

function setupInfoToggle() {
    const infoToggle = document.getElementById('infoToggle');
    if (infoToggle) {
        infoToggle.addEventListener('click', () => {
            openAppInfoModal();
        });
    }
}

// ============================================================================
// PAGE RELOAD BEHAVIOR
// ============================================================================

// Reload the page after clicking on the logo
function scrollAndReloadPage() {
    window.scrollTo(0, 0);
    routing.reloadPage();
}

// Setup header title click handler
function setupHeaderTitle() {
    const headerTitle = document.getElementById('headerTitle');
    if (headerTitle) {
        headerTitle.addEventListener('click', scrollAndReloadPage);
    }
}

// Scroll to top on page reload
window.onbeforeunload = function () {
    window.scrollTo(0, 0);
};

// ============================================================================
// MAP VIEW FUNCTIONALITY
// ============================================================================

let leafletMap = null;
// Persistent containers: markers and overlays are cleared and refilled in place,
// so a rebuild can never leave an orphaned layer behind on the map.
let mapMarkerLayer = null;
let windOverlayLayer = null;
let mapBoundsInitialized = false;
let mapTileLayer = null;
let isMapView = false;
let currentMapLayer = 'satellite'; // 'satellite', 'osm' or 'osmDark'
let windOverlayVisible = state.getWindOverlayVisible();
let mapSpotsVisible = state.getMapSpotsVisible();
let windOverlayDisclaimerEl = null;
// Hourly slider under the map: 0 is now, later steps walk the forecast hour by
// hour. The hours come from /api/v1/wind, which the spots response can't carry.
let mapTimeline = null;
let mapTimelineIndex = null;
let mapTimelineRequest = null;
let mapForecastStep = 0;

// Everything the map draws - dots, field, popups - reads its conditions
// through the timeline, so the whole map describes the selected hour at once.
function getMapSpotConditions(spot) {
    const conditions = weather.getWindConditionsAtStep(spot, mapForecastStep, mapTimelineIndex);
    if (!conditions) {
        return null;
    }

    return {
        ...conditions,
        label: conditions.isCurrent
            ? translations.t('nowLabel')
            : formatForecastDateLabel(conditions.forecastDate)
    };
}

// Fetched the first time the map is opened, so visitors who never leave the
// spots grid don't pay for it. Until it arrives the map shows what it always
// has: the conditions right now.
function ensureMapTimeline() {
    if (mapTimeline || mapTimelineRequest) {
        return;
    }

    const mapContainer = document.getElementById('mapContainer');
    if (!mapContainer) {
        return;
    }

    // The grid is fetched once per session, so the width decided here is the one
    // the slider keeps - a phone that later becomes a desktop window (rotated
    // tablet) steps through five days until the next page load.
    const timelineHours = isMobileView() ? map.TIMELINE_HOURS_COMPACT : map.TIMELINE_HOURS_FULL;

    mapTimelineRequest = api.fetchWindTimeline(timelineHours).then(timeline => {
        const index = weather.indexWindTimeline(timeline);
        if (index.hours.length === 0 || index.bySpotId.size === 0) {
            // Nothing to step through - leave the map on "now" and let a later
            // visit to the map view try again.
            mapTimelineRequest = null;
            return;
        }

        mapTimelineIndex = index;
        mapTimeline = map.createForecastTimeline({
            container: mapContainer,
            hours: index.hours,
            initialStep: mapForecastStep,
            onChange: (step) => {
                mapForecastStep = step;
                updateMapMarkers();
            }
        });

        // The slider takes height off the map container, so Leaflet has to
        // re-measure before its next repaint
        if (leafletMap) {
            leafletMap.invalidateSize();
        }
    });
}

function initMap() {
    if (leafletMap) return; // Already initialized

    const mapContainer = document.getElementById('map');
    if (!mapContainer) return;

    // Initialize Leaflet map
    leafletMap = L.map('map').setView([51.505, -0.09], 2); // Default world view

    // Containers for spot markers and the wind overlay, kept for the map's lifetime
    mapMarkerLayer = L.layerGroup().addTo(leafletMap);
    windOverlayLayer = L.layerGroup().addTo(leafletMap);

    // Add base tile layer
    mapTileLayer = map.updateTileLayer(leafletMap, mapTileLayer, currentMapLayer);

    // Add layer switcher control using common module
    const layerSwitcher = map.createLayerSwitcher({
        getCurrentLayer: () => currentMapLayer,
        onLayerChange: (newLayer) => {
            currentMapLayer = newLayer;
            mapTileLayer = map.updateTileLayer(leafletMap, mapTileLayer, currentMapLayer);
        }
    });
    leafletMap.addControl(layerSwitcher);

    // Show/hide the interpolated wind field
    const windOverlayControl = map.createWindOverlayControl({
        isVisible: () => windOverlayVisible,
        onToggle: (visible) => {
            windOverlayVisible = visible;
            state.setWindOverlayVisible(visible);
            updateMapMarkers();
        }
    });
    leafletMap.addControl(windOverlayControl);

    // Show/hide the spot markers and clusters. The wind overlay is a layer of
    // its own, so hiding the spots leaves the field on the map by itself.
    const spotsToggleControl = map.createSpotsToggleControl({
        isVisible: () => mapSpotsVisible,
        onToggle: (visible) => {
            mapSpotsVisible = visible;
            state.setMapSpotsVisible(visible);
            updateMapMarkers();
        }
    });
    leafletMap.addControl(spotsToggleControl);

    // Clustering is computed in screen pixels, so the markers have to be
    // rebuilt whenever the zoom level changes.
    leafletMap.on('zoomend', () => {
        if (isMapView) {
            updateMapMarkers();
        }
    });
}

// Shown by the field overlay, which interpolates between spots, so nobody reads
// the painted field as measured data.
function ensureWindOverlayDisclaimer(visible) {
    const mapContainer = document.getElementById('mapContainer');
    if (!mapContainer) return;

    if (visible) {
        if (!windOverlayDisclaimerEl) {
            windOverlayDisclaimerEl = document.createElement('div');
            windOverlayDisclaimerEl.className = 'wind-overlay-disclaimer';
            mapContainer.appendChild(windOverlayDisclaimerEl);
        }
        windOverlayDisclaimerEl.textContent = translations.t('windOverlayDisclaimer');
    } else if (windOverlayDisclaimerEl) {
        windOverlayDisclaimerEl.remove();
        windOverlayDisclaimerEl = null;
    }
}

function renderWindOverlay(spots) {
    if (!leafletMap || !windOverlayLayer) return;

    // Drop the previous overlay contents (the container itself stays on the map)
    windOverlayLayer.clearLayers();

    if (windOverlayVisible) {
        // One overlay, two passes: the colour wash says how hard it blows, the
        // particles on top say where. Added in this order so the streaks stay
        // above the wash in the overlay pane.
        windOverlayLayer.addLayer(map.createWindHeatLayer(spots, getMapSpotConditions));
        windOverlayLayer.addLayer(map.createWindParticleLayer(spots, getMapSpotConditions));
        ensureWindOverlayDisclaimer(true);
    } else {
        ensureWindOverlayDisclaimer(false);
    }
}

function buildMapPopupWindDetails(spotConditions) {
    if (!spotConditions) {
        return '';
    }

    const arrow = weather.getWindArrow(spotConditions.direction);
    const gustLabel = Number.isFinite(spotConditions.gusts) ? `${spotConditions.gusts} kts` : '-';
    const windClass = weather.getWindClass(spotConditions.wind);
    const directionLabel = spotConditions.direction || '-';
    const forecastMeta = !spotConditions.isCurrent
        ? `<div class="map-popup-meta">${translations.t('forecastEstimateLabel')}${spotConditions.label ? ` · ${spotConditions.label}` : ''}</div>`
        : '';

    return `
        <div class="map-popup-wind ${windClass}">
            <span class="wind-arrow">${arrow}</span>
            <span class="map-popup-direction">${directionLabel}</span>
            <span class="map-popup-speed">${spotConditions.wind} kts - ${gustLabel}</span>
        </div>
        ${forecastMeta}
    `;
}

// Shared popup markup for a spot (clickable name + wind summary). Used by both
// the dot markers and the wind-arrow markers.
function buildSpotMarkerPopup(spot) {
    const popupWindDetails = buildMapPopupWindDetails(getMapSpotConditions(spot));
    return `
        <div class="map-popup">
            <a href="${routing.buildSpotUrl(spot.wgId)}" style="color: var(--accent-primary); text-decoration: none; font-weight: 600;">${spot.name}</a>
            ${popupWindDetails}
        </div>
    `;
}

function addMarkersToMap(spots) {
    if (!leafletMap || !mapMarkerLayer) return;

    mapMarkerLayer.clearLayers();

    if (!spots || spots.length === 0) return;

    // Fit map to show all markers (only on first view to avoid repeated zooming).
    // Done before the markers are built so clustering already sees the final zoom.
    fitMapToSpots(spots);

    // Hidden spots leave the wind overlay - which lives on its own layer - as the
    // only thing painted on the map.
    if (mapSpotsVisible) {
        // Nearby spots collapse into a numbered bubble while the map is zoomed out.
        mapMarkerLayer.addLayer(
            map.createSpotMarkerLayer(leafletMap, spots, getMapSpotConditions, buildSpotMarkerPopup)
        );
    }
}

// Zoom the map onto the spots once per session. The flag is raised before the
// zoom changes, because zooming fires 'zoomend' which re-enters the rendering
// path - without it the fit would recurse.
function fitMapToSpots(spots) {
    if (mapBoundsInitialized) return;

    const bounds = spots
        .filter(spot => spot.coordinates && spot.coordinates.lat && spot.coordinates.lon)
        .map(spot => [spot.coordinates.lat, spot.coordinates.lon]);

    if (bounds.length === 0) return;

    mapBoundsInitialized = true;

    const isMobile = window.innerWidth <= 929;
    if (isMobile) {
        // On mobile, fit bounds then zoom in by 1 level to fill vertical space better
        leafletMap.fitBounds(bounds, { padding: [0, 0] });
        const currentZoom = leafletMap.getZoom();
        leafletMap.setZoom(currentZoom + 1);
    } else {
        leafletMap.fitBounds(bounds, { padding: [50, 50] });
    }
    // On tall viewports the fitted world is shorter than the map container,
    // which shows an empty stripe above the top tile row - nudge the zoom
    // up until the view sits inside the world again.
    map.zoomToFillWorld(leafletMap);
}

function showMapView() {
    if (showingFavorites) {
        exitFavoritesMode({ skipRender: true, skipScroll: true });
    }

    isMapView = true;
    const spotsGrid = document.getElementById('spotsGrid');
    const mapContainer = document.getElementById('mapContainer');
    const mapToggle = document.getElementById('mapToggle');
    const listViewBtn = document.getElementById('listViewBtn');
    const gridViewBtn = document.getElementById('gridViewBtn');
    const heroSection = document.getElementById('heroSection');

    // Hide hero section and spots grid, disable hero toggle button
    if (heroSection) heroSection.style.display = 'none';
    const heroToggle = document.getElementById('heroToggle');
    if (heroToggle) {
        heroToggle.classList.add('disabled-in-map');
        heroToggle.classList.remove('active');
    }
    // Disable the firing-sort toggle while the map is shown (deselect + disable)
    const firingSortToggle = document.getElementById('firingSortToggle');
    if (firingSortToggle) {
        firingSortToggle.classList.add('disabled-in-map');
        firingSortToggle.classList.remove('active');
    }
    spotsGrid.style.display = 'none';

    // Show map container
    // Flex, not block: the container stacks the map above the day slider
    mapContainer.style.display = 'flex';

    // Mark map button as active and deselect view buttons
    mapToggle.classList.add('active');
    if (listViewBtn) listViewBtn.classList.remove('active');
    if (gridViewBtn) gridViewBtn.classList.remove('active');


    // Fetch the hourly forecast grid behind the slider (once per session)
    ensureMapTimeline();

    // Initialize map if not already done
    initMap();

    // Add markers for filtered spots
    const filteredSpots = filterSpots(globalWeatherData, currentFilter, currentSearchQuery);
    addMarkersToMap(filteredSpots);

    // Render the wind field overlay on top of the markers
    renderWindOverlay(filteredSpots);

    // Invalidate map size (needed for proper rendering)
    if (leafletMap) leafletMap.invalidateSize();

    // Update URL to /map
    routing.pushMapUrl();

    scrollMapIntoView();
}

// Desktop has room to spare, so the map view simply starts at the top of the
// page. On a phone the map is the only thing worth looking at, and at scroll 0
// its bottom - the forecast slider - sits under the fold: mobile browsers size
// 100vh with the URL bar hidden, while the visible viewport still has it. So
// the page is nudged down until the map sits right under the fixed header,
// which brings the slider into view without the user reaching for it.
const MAP_VIEWPORT_GAP = 8;

function scrollMapIntoView() {
    if (!isMobileView()) {
        window.scrollTo({ top: 0, behavior: 'smooth' });
        return;
    }

    const mapContainer = document.getElementById('mapContainer');
    if (!mapContainer) {
        return;
    }

    // After a layout pass: the drawer closing and the container switching to
    // flex both move the map before its position can be measured.
    requestAnimationFrame(() => {
        const header = document.querySelector('.fixed-header');
        const headerHeight = header ? header.getBoundingClientRect().height : 0;
        const rect = mapContainer.getBoundingClientRect();
        const top = rect.top + window.scrollY;

        // Where the map starts just below the header, and the least scrolling
        // that still reveals its bottom edge. The larger of the two wins: when
        // the map fits, that is the tidy position under the header; when it
        // does not, showing the slider matters more than the top tile row.
        const underHeader = top - headerHeight - MAP_VIEWPORT_GAP;
        const bottomVisible = top + rect.height - window.innerHeight + MAP_VIEWPORT_GAP;

        window.scrollTo({
            top: Math.max(0, underHeader, bottomVisible),
            behavior: 'smooth'
        });
    });
}

function hideMapView(options = {}) {
    if (!isMapView) {
        return;
    }

    const { skipRender = false } = options;

    isMapView = false;
    const spotsGrid = document.getElementById('spotsGrid');
    const mapContainer = document.getElementById('mapContainer');
    const mapToggle = document.getElementById('mapToggle');
    const listViewBtn = document.getElementById('listViewBtn');
    const gridViewBtn = document.getElementById('gridViewBtn');

    // Show spots grid, restore hero section, and re-enable hero toggle button
    spotsGrid.style.display = '';
    const heroToggle = document.getElementById('heroToggle');
    if (heroToggle) {
        heroToggle.classList.remove('disabled-in-map');
        if (state.getHeroVisible()) heroToggle.classList.add('active');
    }
    // Re-enable the firing-sort toggle and restore its previous selected state
    const firingSortToggle = document.getElementById('firingSortToggle');
    if (firingSortToggle) {
        firingSortToggle.classList.remove('disabled-in-map');
        firingSortToggle.classList.toggle('active', firingSortEnabled);
    }
    updateHeroVisibility();

    // Tear down the wind overlay contents (rebuilt on next showMapView)
    if (windOverlayLayer) {
        windOverlayLayer.clearLayers();
    }
    ensureWindOverlayDisclaimer(false);

    // Hide map container
    mapContainer.style.display = 'none';

    // Remove active state from map button and restore view button state
    mapToggle.classList.remove('active');
    if (currentViewMode === 'list') {
        if (listViewBtn) listViewBtn.classList.add('active');
        if (gridViewBtn) gridViewBtn.classList.remove('active');
    } else {
        if (listViewBtn) listViewBtn.classList.remove('active');
        if (gridViewBtn) gridViewBtn.classList.add('active');
    }


    // Restore previous URL
    routing.pushUrl(routing.buildCountryUrl(currentFilter));

    // Re-render spots to clear any filter changes
    if (!skipRender) {
        renderSpots(currentFilter, currentSearchQuery, true);
    }
}

// ============================================================================
// HERO SECTION
// ============================================================================

function initHeroSection() {
    const heroSection = document.getElementById('heroSection');
    const heroToggle = document.getElementById('heroToggle');
    if (!heroSection || !heroToggle) return;

    const isVisible = state.getHeroVisible();

    function updateHeroToggleUI(visible) {
        if (visible) {
            heroToggle.classList.add('active');
        } else {
            heroToggle.classList.remove('active');
        }
    }

    updateHeroToggleUI(isVisible);

    function applyHeroVisible(visible) {
        state.setHeroVisible(visible);
        updateHeroToggleUI(visible);

        if (isMapView) return;

        if (visible && globalWeatherData.length > 0) {
            renderHeroSection();
            heroSection.style.display = '';
        } else {
            heroSection.style.display = 'none';
        }
    }

    heroToggle.addEventListener('click', () => {
        applyHeroVisible(!state.getHeroVisible());
    });

    const heroRefresh = document.getElementById('heroRefresh');
    if (heroRefresh) {
        heroRefresh.addEventListener('click', () => {
            if (globalWeatherData.length > 0) {
                renderHeroSection();
            }
        });
    }

    // The X on the photo hides the banner for good, so it asks first - and the
    // dialog is where the sidebar button that brings it back is named.
    const heroClose = document.getElementById('heroClose');
    if (heroClose) {
        heroClose.addEventListener('click', openHeroCloseModal);
    }

    const heroCloseConfirm = document.getElementById('heroCloseModalConfirm');
    if (heroCloseConfirm) {
        heroCloseConfirm.addEventListener('click', () => {
            closeHeroCloseModal();
            applyHeroVisible(false);
        });
    }

    const heroCloseCancel = document.getElementById('heroCloseModalCancel');
    if (heroCloseCancel) {
        heroCloseCancel.addEventListener('click', closeHeroCloseModal);
    }
}

let heroInitialized = false;

function updateHeroVisibility() {
    const heroSection = document.getElementById('heroSection');
    if (!heroSection) return;

    if (!state.getHeroVisible() || window.innerWidth <= 929 || isMapView) {
        heroSection.style.display = 'none';
    } else if (heroInitialized) {
        heroSection.style.display = '';
    }
}

function renderHeroSection() {
    const heroSection = document.getElementById('heroSection');
    if (!heroSection || !state.getHeroVisible() || isMapView) return;

    if (window.innerWidth <= 929) {
        heroSection.style.display = 'none';
        return;
    }

    const hasPhoto = spot => {
        const url = typeof spot.spotPhotoUrl === 'string' ? spot.spotPhotoUrl.trim() : '';
        return url.length > 0;
    };

    const allSpotsWithPhotos = globalWeatherData.filter(hasPhoto);

    if (allSpotsWithPhotos.length === 0) {
        heroSection.style.display = 'none';
        return;
    }

    const countrySpotsWithPhotos = currentFilter && currentFilter !== 'all'
        ? allSpotsWithPhotos.filter(spot => spot.country === currentFilter)
        : [];
    const spotsWithPhotos = countrySpotsWithPhotos.length > 0 ? countrySpotsWithPhotos : allSpotsWithPhotos;

    const randomSpot = spotsWithPhotos[Math.floor(Math.random() * spotsWithPhotos.length)];

    const heroImage = document.getElementById('heroImage');
    heroImage.src = randomSpot.spotPhotoUrl;
    heroImage.alt = randomSpot.name;

    const heroSpotLabel = document.getElementById('heroSpotLabel');
    const countryKey = randomSpot.country.replace(/\s+/g, '');
    heroSpotLabel.textContent = `${randomSpot.name}, ${translations.t(countryKey)}`;
    heroSpotLabel.href = routing.buildSpotUrl(randomSpot.wgId);
    heroSpotLabel.dataset.country = countryKey;
    heroSpotLabel.dataset.spotName = randomSpot.name;

    const lang = state.getLanguage();
    const slogans = HERO_SLOGANS[lang] || HERO_SLOGANS.en;
    const slogan = slogans[Math.floor(Math.random() * slogans.length)];
    document.getElementById('heroSlogan').textContent = slogan;

    heroSection.style.display = '';
    heroInitialized = true;
}

window.addEventListener('resize', updateHeroVisibility);

function setupMapToggle() {
    const mapToggle = document.getElementById('mapToggle');
    if (!mapToggle) return;

    mapToggle.addEventListener('click', () => {
        if (!isMapView) {
            showMapView();
            return;
        }
        // In the mobile drawer the map entry reads as a plain link, not a
        // switch: a second tap keeps the map, and the list entry is the way
        // back. On desktop the entry still toggles.
        if (!isMobileView()) {
            hideMapView();
        }
    });
}

// ============================================================================
// RANDOM SPOT
// ============================================================================

// Opens a random spot page. Picks from the spots currently in view (country
// filter, search and the live-stations filter all apply) so the result matches
// what the user is looking at, falling back to every spot when that set is
// empty.
function setupRandomSpotToggle() {
    const btn = document.getElementById('randomSpotToggle');
    if (!btn) {
        return;
    }

    btn.addEventListener('click', async () => {
        let spots = globalWeatherData;

        if (spots.length === 0) {
            try {
                spots = await api.fetchAllSpots();
                globalWeatherData = spots;
            } catch (error) {
                console.error('Error fetching spots for random pick:', error);
                return;
            }
        }

        const filtered = filterSpots(spots, currentFilter, currentSearchQuery);
        const candidates = filtered.length > 0 ? filtered : spots;

        if (candidates.length === 0) {
            return;
        }

        const randomSpot = candidates[Math.floor(Math.random() * candidates.length)];
        routing.navigateToSpot(randomSpot.wgId);
    });
}

function updateMapMarkers() {
    if (!isMapView) return;

    const filteredSpots = filterSpots(globalWeatherData, currentFilter, currentSearchQuery);
    addMarkersToMap(filteredSpots);
    renderWindOverlay(filteredSpots);
}

// ============================================================================
// GLOBAL WINDOW FUNCTIONS (for onclick handlers)
// ============================================================================

// Make functions global for onclick handlers
window.openAppInfoModal = openAppInfoModal;
window.closeAppInfoModal = closeAppInfoModal;
window.openAIModal = openAIModal;
window.closeAIModal = closeAIModal;
window.openIcmModal = openIcmModal;
window.closeIcmModal = closeIcmModal;
window.toggleFavorite = toggleFavorite;

// ============================================================================
// MAIN INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    // The sidebar and its modals are shared markup; everything below wires them up
    appShell.renderSidebar();
    appShell.renderModals();

    initTheme();
    initLanguage();
    setupHeaderTitle();
    setupDropdown();
    setupModals();
    setupSearch();
    setupDragAndDrop();
    setupListDragAndDrop();
    setupFavorites();
    setupHamburgerMenu();
    sideMenu.setup();
    sideMenu.setupHints();
    calculator.setupKiteSizeCalculator();
    setupColumnToggle();
    setupFiringSortToggle();
    setupLiveStationsToggle();
    setupMapToggle();
    setupRandomSpotToggle();
    handlePopState();
    setupInfoToggle();
    renderMainSponsors();
    initHeroSection();
    handleStarredURL();
});
