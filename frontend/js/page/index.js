import { getCountryFlag } from '../common/flags.js';
import { t } from '../common/translations.js';
import { updateFooter } from '../common/footer.js';
import { getWindArrow, getWindClass } from '../common/weather.js';
import { AUTO_REFRESH_INTERVAL } from '../common/constants.js';
import { findClosestForecast } from '../common/date.js';
import { fetchAllSpots, fetchSponsors } from '../common/api.js';
import {
    normalizeCountryForUrl,
    getCountryFromUrl,
    getCurrentPath,
    isStarredUrl,
    isMapUrl,
    navigateToHome,
    navigateToCountry,
    navigateToSpot,
    pushCountryUrl,
    pushStarredUrl,
    pushMapUrl,
    pushUrl,
    buildCountryUrl,
    buildSpotUrl,
    reloadPage
} from '../common/routing.js';
import {
    updateTileLayer,
    createLayerSwitcher,
    updateLayerSwitcherLabels
} from '../common/map.js';
import {
    getTheme,
    setTheme,
    applyTheme,
    getCurrentTheme,
    getLanguage,
    setLanguage,
    getFavorites,
    saveFavorites,
    isFavorite as checkIsFavorite,
    toggleFavorite as toggleFavoriteState,
    getShowingFavorites,
    setShowingFavorites,
    getSelectedCountry,
    setSelectedCountry,
    getDesktopViewMode,
    setDesktopViewMode,
    getPreviousUrl,
    setPreviousUrl,
    getSpotOrder,
    saveSpotOrder,
    removeSpotOrder,
    getListOrder,
    saveListOrder
} from '../common/state.js';

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
let previousUrl = getPreviousUrl();

// ============================================================================
// URL ROUTING HELPERS
// ============================================================================

function findCountryByNormalizedName(normalizedName) {
    // Find the actual country name from normalized URL name
    for (const country of availableCountries) {
        if (normalizeCountryForUrl(country) === normalizedName) {
            return country;
        }
    }
    return null;
}

function updateUrlForCountry(country) {
    // Update browser URL without reloading the page
    // Store previous URL before changing (if not already starred)
    if (!isStarredUrl()) {
        previousUrl = getCurrentPath();
        setPreviousUrl(previousUrl);
    }
    pushCountryUrl(country);
}

function updateUrlForStarred() {
    // Store the current URL before switching to starred
    if (!isStarredUrl()) {
        previousUrl = getCurrentPath();
        setPreviousUrl(previousUrl);
    }
    pushStarredUrl();
    document.title = `${t('favoritesToggleTooltip')} - VARUN.SURF`;
}

function restorePreviousUrl() {
    // Restore previous URL when exiting starred view
    const targetUrl = previousUrl || '/';
    pushUrl(targetUrl);

    // Update page title based on URL
    if (targetUrl === '/') {
        updatePageTitle('all');
    } else {
        const urlCountry = getCountryFromUrl();
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
        const translatedCountry = t(country.replace(/\s+/g, ''));
        document.title = `${translatedCountry} - VARUN.SURF`;
    }
}

function showInvalidCountryError(countryName) {
    const spotsGrid = document.getElementById('spotsGrid');
    spotsGrid.innerHTML = `
            <div class="error-message">
                <span class="error-icon">⚠️</span>
                <div class="error-title">${t('invalidCountry')}</div>
                <div class="error-description">
                    ${t('invalidCountryDescription')}<br/>
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
    return checkIsFavorite(spotName);
}

function toggleFavorite(spotName) {
    const wasAdded = toggleFavoriteState(spotName);

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
                    icon.title = t('removeFromFavorites');
                } else {
                    icon.classList.remove('favorited');
                    icon.title = t('addToFavorites');
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
                    icon.title = t('removeFromFavorites');
                } else {
                    icon.classList.remove('favorited');
                    icon.title = t('addToFavorites');
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
    const favorites = getFavorites();

    if (favorites.length === 0) {
        spotsGrid.innerHTML = `
                <div class="error-message">
                    <span class="error-icon">⭐</span>
                    <div class="error-title">${t('noFavoritesTitle')}</div>
                    <div class="error-description">
                        ${t('noFavoritesDescription')}
                    </div>
                </div>
            `;
        return;
    }

    try {
        if (globalWeatherData.length === 0) {
            globalWeatherData = await fetchAllSpots();
        }

        const favoriteSpots = globalWeatherData.filter(spot => favorites.includes(spot.name));

        spotsGrid.innerHTML = '';

        // Render based on the current view mode
        if (currentViewMode === 'list') {
            // Apply sorting if a column is selected
            const sortedSpots = listSortColumn ? sortSpots(favoriteSpots, listSortColumn, listSortDirection) : favoriteSpots;

            // Render list view
            spotsGrid.appendChild(createListHeader());
            sortedSpots.forEach(spot => {
                spotsGrid.appendChild(createListRow(spot));
            });
        } else {
            // Render grid view
            favoriteSpots.forEach(spot => {
                spotsGrid.appendChild(createSpotCard(spot));
            });
            loadCardOrder();
        }
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
            setShowingFavorites(true);

            // Update URL to /starred
            updateUrlForStarred();

            renderFavorites();
        }

        // Scroll to top after toggling favorites
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    });

    // Restore favorites state on page load
    if (getShowingFavorites()) {
        renderFavorites();
    }
}

function exitFavoritesMode(options = {}) {
    if (!showingFavorites) {
        return;
    }

    const { skipRender = false, skipScroll = false } = options;

    showingFavorites = false;
    setShowingFavorites(false);
    const favoritesButton = document.getElementById('favoritesToggle');
    if (favoritesButton) {
        favoritesButton.classList.remove('active');
    }

    restorePreviousUrl();

    if (!skipRender) {
        renderSpots(getSelectedCountry(), '');
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
    const savedTheme = getTheme();
    const themeToggle = document.getElementById('themeToggle');
    const themeIcon = document.getElementById('themeIcon');

    function updateThemeUI(theme) {
        applyTheme(theme);
        if (theme === 'light') {
            themeIcon.innerHTML = '<path d="M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5,5-2.24,5-5-2.24-5-5-5Zm0,7c-1.1,0-2-.9-2-2s.9-2,2-2,2,.9,2,2-.9,2-2,2Zm4.95-6.95c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0,.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44ZM7.05,16.95c.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0ZM3.51,5.64c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41Zm16.97,12.73c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41Zm3.51-6.36c0,.83-.67,1.5-1.5,1.5h-2c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5h2c.83,0,1.5,.67,1.5,1.5ZM3.5,13.5H1.5c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5H3.5c.83,0,1.5,.67,1.5,1.5s-.67,1.5-1.5,1.5ZM10.5,3.5V1.5c0-.83,.67-1.5,1.5-1.5s1.5,.67,1.5,1.5V3.5c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5Zm3,17v2c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5v-2c0-.83,.67-1.5,1.5-1.5s1.5,.67,1.5,1.5Z"/>';
        } else {
            themeIcon.innerHTML = '<path d="M15,24a12.021,12.021,0,0,1-8.914-3.966,11.9,11.9,0,0,1-3.02-9.309A12.122,12.122,0,0,1,13.085.152a13.061,13.061,0,0,1,5.031.205,2.5,2.5,0,0,1,1.108,4.226c-4.56,4.166-4.164,10.644.807,14.41a2.5,2.5,0,0,1-.7,4.32A13.894,13.894,0,0,1,15,24Z"/>';
        }
        setTheme(theme);
        mapTileLayer = updateTileLayer(map, mapTileLayer, currentMapLayer);
    }

    // Set the initial theme
    updateThemeUI(savedTheme);

    // Theme toggle event
    themeToggle.addEventListener('click', () => {
        const currentThemeValue = getCurrentTheme();
        const newTheme = currentThemeValue === 'dark' ? 'light' : 'dark';
        updateThemeUI(newTheme);
    });

    // Make the logo clickable to go back home with the current country filter
    const headerLogo = document.getElementById('headerLogo');
    if (headerLogo) {
        headerLogo.addEventListener('click', () => {
            const savedCountry = getSelectedCountry();
            if (savedCountry === 'all') {
                navigateToHome();
            } else {
                navigateToCountry(savedCountry);
            }
        });
    }
}

// ============================================================================
// LANGUAGE/INTERNATIONALIZATION MANAGEMENT
// ============================================================================

function initLanguage() {
    const savedLanguage = getLanguage();
    const languageToggle = document.getElementById('languageToggle');

    function updateLanguageUI(lang) {
        setLanguage(lang);
        // Update all UI elements with translations
        updateUITranslations();
    }

    function updateUITranslations() {
        // Update page title with current country
        updatePageTitle(getSelectedCountry());

        // Update search placeholder
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.placeholder = t('searchPlaceholder');
        }

        // Update tooltips
        const themeToggle = document.getElementById('themeToggle');
        if (themeToggle) {
            themeToggle.title = t('themeToggleTooltip');
        }

        const favoritesToggle = document.getElementById('favoritesToggle');
        if (favoritesToggle) {
            favoritesToggle.title = t('favoritesToggleTooltip');
        }

        const infoToggle = document.getElementById('infoToggle');
        if (infoToggle) {
            infoToggle.title = t('infoToggleTooltip');
        }

        const infoToggleLabel = document.getElementById('infoToggleLabel');
        if (infoToggleLabel) {
            infoToggleLabel.textContent = t('infoButtonLabel');
        }

        const mapToggleLabel = document.getElementById('mapToggleLabel');
        if (mapToggleLabel) {
            mapToggleLabel.textContent = t('mapButtonLabel');
        }

        const kiteSizeToggle = document.getElementById('kiteSizeToggle');
        if (kiteSizeToggle) {
            kiteSizeToggle.title = t('kiteSizeToggleTooltip');
        }

        const columnToggle = document.getElementById('columnToggle');
        if (columnToggle) {
            columnToggle.title = t('columnToggleTooltip');
        }

        if (languageToggle) {
            languageToggle.title = t('languageToggleTooltip');
        }

        // Update language code text
        const langCode = document.getElementById('langCode');
        if (langCode) {
            langCode.textContent = t('langCode');
        }

        // Update footer
        updateFooter(t);

        // Update "All" in the dropdown if selected
        if (globalWeatherData.length > 0) {
            populateCountryDropdown(globalWeatherData);
        } else {
            updateSelectedCountryLabel(getSelectedCountry());
        }

        // Update dropdown "All" option
        const dropdownMenu = document.getElementById('dropdownMenu');
        if (dropdownMenu) {
            const allOption = dropdownMenu.querySelector('[data-country="all"]');
            if (allOption) {
                allOption.textContent = `🌎 ${t('allCountries')}`;
            }
        }

        // Update loading message if visible
        const loadingText = document.querySelector('.loading-text');
        if (loadingText) {
            loadingText.textContent = t('loadingText');
        }

        // Update app info modal content
        const appInfoModalTitle = document.getElementById('appInfoModalTitle');
        if (appInfoModalTitle) {
            appInfoModalTitle.textContent = t('appInfoModalTitle');
        }

        const appInfoDescription = document.getElementById('appInfoDescription');
        if (appInfoDescription) {
            appInfoDescription.textContent = t('appInfoDescription');
        }

        const appInfoContactTitle = document.getElementById('appInfoContactTitle');
        if (appInfoContactTitle) {
            appInfoContactTitle.textContent = t('appInfoContactTitle');
        }

        const appInfoContactText = document.getElementById('appInfoContactText');
        if (appInfoContactText) {
            appInfoContactText.innerHTML = t('appInfoContactText');
        }

        const appInfoNewSpotTitle = document.getElementById('appInfoNewSpotTitle');
        if (appInfoNewSpotTitle) {
            appInfoNewSpotTitle.textContent = t('appInfoNewSpotTitle');
        }

        const appInfoNewSpotText = document.getElementById('appInfoNewSpotText');
        if (appInfoNewSpotText) {
            appInfoNewSpotText.innerHTML = t('appInfoNewSpotText');
        }

        const appInfoCollaborationTitle = document.getElementById('appInfoCollaborationTitle');
        if (appInfoCollaborationTitle) {
            appInfoCollaborationTitle.textContent = t('appInfoCollaborationTitle');
        }

        const appInfoCollaborationText = document.getElementById('appInfoCollaborationText');
        if (appInfoCollaborationText) {
            appInfoCollaborationText.innerHTML = t('appInfoCollaborationText');
        }

        // Update kite size calculator modal content
        const kiteSizeModalTitle = document.querySelector('#kiteSizeModal .modal-title span');
        if (kiteSizeModalTitle) {
            kiteSizeModalTitle.textContent = t('kiteSizeCalculatorTitle');
        }

        const windSpeedLabel = document.querySelector('label[for="windSpeed"]');
        if (windSpeedLabel) {
            windSpeedLabel.textContent = t('windSpeedLabel');
        }

        const windSpeedInput = document.getElementById('windSpeed');
        if (windSpeedInput) {
            windSpeedInput.placeholder = t('windSpeedPlaceholder');
        }

        const riderWeightLabel = document.querySelector('label[for="riderWeight"]');
        if (riderWeightLabel) {
            riderWeightLabel.textContent = t('riderWeightLabel');
        }

        const riderWeightInput = document.getElementById('riderWeight');
        if (riderWeightInput) {
            riderWeightInput.placeholder = t('riderWeightPlaceholder');
        }

        const skillLevelLabel = document.querySelector('label[for="skillLevel"]');
        if (skillLevelLabel) {
            skillLevelLabel.textContent = t('skillLevelLabel');
        }

        const skillLevelSelect = document.getElementById('skillLevel');
        if (skillLevelSelect) {
            const options = skillLevelSelect.querySelectorAll('option');
            options[0].textContent = t('skillLevelPlaceholder');
            options[1].textContent = t('skillBeginnerFlat');
            options[2].textContent = t('skillBeginnerSmall');
            options[3].textContent = t('skillIntermediateFlat');
            options[4].textContent = t('skillIntermediateMedium');
            options[5].textContent = t('skillAdvancedFlat');
            options[6].textContent = t('skillAdvancedMedium');
            options[7].textContent = t('skillAdvancedLarge');
        }

        const calculateBtn = document.getElementById('calculateBtn');
        if (calculateBtn) {
            calculateBtn.textContent = t('calculateButton');
        }

        const calcResultTitle = document.querySelector('#calcResult .calc-result-title');
        if (calcResultTitle) {
            calcResultTitle.textContent = t('recommendedEquipment');
        }

        const kiteSizeLabel = document.querySelector('#calcResult .calc-result-item:nth-child(2) .calc-result-label');
        if (kiteSizeLabel) {
            kiteSizeLabel.textContent = t('kiteSizeLabel');
        }

        const boardSizeLabel = document.querySelector('#calcResult .calc-result-item:nth-child(3) .calc-result-label');
        if (boardSizeLabel) {
            boardSizeLabel.textContent = t('boardSizeLabel');
        }

        const calcDisclaimer = document.querySelector('#calcResult .modal-disclaimer');
        if (calcDisclaimer) {
            calcDisclaimer.textContent = t('calcDisclaimer');
        }

        // Update AI modal disclaimer
        const aiDisclaimer = document.querySelector('#aiModal .modal-disclaimer');
        if (aiDisclaimer) {
            aiDisclaimer.textContent = t('aiDisclaimer');
        }

        // Update map layer switcher labels
        updateLayerSwitcherLabels();

        // Re-render spots to update table headers and content
        if (globalWeatherData.length > 0) {
            if (showingFavorites) {
                renderFavorites();
            } else {
                renderSpots(currentFilter, currentSearchQuery, true);
            }
        }
    }

    // Set initial language and update UI
    updateLanguageUI(savedLanguage);

    // Language toggle event
    languageToggle.addEventListener('click', () => {
        const currentLang = getLanguage();
        const newLang = currentLang === 'en' ? 'pl' : 'en';
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
                    <span class="loading-text">${t('loadingText')}</span>
                </div>
            `;
}

function showErrorMessage(error) {
    const spotsGrid = document.getElementById('spotsGrid');

    let errorTitle = t('errorLoadDataTitle');
    let errorMessage = t('errorLoadDataDescription');

    if (error.message.includes('HTTP Error: 404')) {
        errorTitle = t('errorDataNotFoundTitle');
        errorMessage = t('errorDataNotFoundDescription');
    } else if (error.message.includes('HTTP Error: 500')) {
        errorTitle = t('errorServerTitle');
        errorMessage = t('errorServerDescription');
    } else if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
        errorTitle = t('errorConnectionTitle');
        errorMessage = t('errorConnectionDescription');
    } else if (error.message.includes('JSON') || error.message.includes('Invalid data format')) {
        errorTitle = t('errorDataFormatTitle');
        errorMessage = t('errorDataFormatDescription');
    }

    spotsGrid.innerHTML = `
                <div class="error-message">
                    <span class="error-icon">⚠️</span>
                    <div class="error-title">${errorTitle}</div>
                    <div class="error-description">
                        ${errorMessage}<br/>
                        ${t('errorRefresh')}
                    </div>
                </div>
            `;
}

// ============================================================================
// WEATHER DISPLAY HELPER FUNCTIONS
// ============================================================================

function getSpotInfo(spot) {
    if (!spot) return null;
    // Direct access - no fallback, all translations are complete
    return getLanguage() === 'pl' ? spot.spotInfoPL : spot.spotInfo;
}

function translateDayName(dayName) {
    const dayMap = {
        'Mon': t('dayMon'),
        'Tue': t('dayTue'),
        'Wed': t('dayWed'),
        'Thu': t('dayThu'),
        'Fri': t('dayFri'),
        'Sat': t('daySat'),
        'Sun': t('daySun'),
        'Today': t('dayToday'),
        'Tomorrow': t('dayTomorrow'),
        'Day 3': t('dayDay3'),
        'Day 4': t('dayDay4'),
        'Day 5': t('dayDay5')
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
    const isMobile = window.innerWidth <= 768;

    if (isMobile) {
        const hour = timeToken.split(':')[0];
        const shortDay = translatedDay.substring(0, 2);
        return `${shortDay} ${hour}`.trim();
    }

    return `${formattedDay}. ${translatedDay} ${timeToken}`.trim();
}

function toNumber(value) {
    if (typeof value === 'number' && Number.isFinite(value)) {
        return value;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
}

function selectForecastForConditions(spot) {
    if (!spot) {
        return null;
    }

    if (Array.isArray(spot.forecastHourly) && spot.forecastHourly.length > 0) {
        const closest = findClosestForecast(spot.forecastHourly);
        if (closest) {
            return closest;
        }
    }

    if (Array.isArray(spot.forecast) && spot.forecast.length > 0) {
        const todayForecast = spot.forecast.find(forecast => typeof forecast.date === 'string' && forecast.date.toLowerCase() === 'today');
        return todayForecast || spot.forecast[0];
    }

    return null;
}

function getSpotConditions(spot) {
    if (!spot) {
        return null;
    }

    const current = spot.currentConditions;
    if (current) {
        const wind = toNumber(current.wind);
        const gusts = toNumber(current.gusts);
        if (wind !== null && gusts !== null) {
            return {
                wind,
                gusts,
                direction: current.direction || '',
                temp: toNumber(current.temp),
                precipitation: null,
                label: t('nowLabel'),
                isCurrent: true
            };
        }
    }

    const forecast = selectForecastForConditions(spot);
    if (!forecast) {
        return null;
    }

    const wind = toNumber(forecast.wind);
    const gusts = toNumber(forecast.gusts);
    if (wind === null || gusts === null) {
        return null;
    }

    return {
        wind,
        gusts,
        direction: forecast.direction || '',
        temp: toNumber(forecast.temp),
        precipitation: toNumber(forecast.precipitation),
        label: formatForecastDateLabel(forecast.date),
        isCurrent: false
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
        const nameA = t(a.replace(/\s+/g, ''));
        const nameB = t(b.replace(/\s+/g, ''));
        return nameA.localeCompare(nameB);
    });

    // Get saved country
    const savedCountry = getSelectedCountry();

    // Build dropdown HTML
    const allLabel = t('allCountries');
    let dropdownHTML = `<div class="dropdown-option ${savedCountry === 'all' ? 'selected' : ''}" data-country="all">🌎 ${allLabel}</div>`;

    sortedCountries.forEach(country => {
        const countryFlag = getCountryFlag(country);
        const isSelected = savedCountry === country ? 'selected' : '';
        const countryName = t(country.replace(/\s+/g, ''));
        dropdownHTML += `<div class="dropdown-option ${isSelected}" data-country="${country}">${countryFlag} ${countryName.toUpperCase()}</div>`;
    });

    dropdownMenu.innerHTML = dropdownHTML;

    // Update the selected country text in the button
    updateSelectedCountryLabel(savedCountry);

    // Re-attach event listeners for the new dropdown options
    setupDropdownEvents();
}

function updateSelectedCountryLabel(countryKey) {
    const selectedCountry = document.getElementById('selectedCountry');
    if (!selectedCountry) {
        return;
    }

    if (!countryKey || countryKey === 'all') {
        selectedCountry.textContent = `🌎 ${t('allCountries')}`;
        return;
    }

    const countryFlag = getCountryFlag(countryKey);
    const countryName = t(countryKey.replace(/\s+/g, ''));
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
            setSelectedCountry(country);

            // Update URL
            updateUrlForCountry(country);

            // Update page title
            updatePageTitle(country);

            // Deselect favorites if changing country
            if (showingFavorites) {
                showingFavorites = false;
                setShowingFavorites(false);
                document.getElementById('favoritesToggle').classList.remove('active');
            }

            renderSpots(country, searchInput.value, true);

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
    const modal = document.getElementById('appInfoModal');
    if (!modal) {
        return;
    }
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeAppInfoModal() {
    const modal = document.getElementById('appInfoModal');
    if (!modal) {
        return;
    }
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
}

function openAIModal(spotName) {
    const spot = globalWeatherData.find(spot => spot.name === spotName);
    const aiAnalysis = getLanguage() === 'pl' ? spot.aiAnalysisPl : spot.aiAnalysisEn;

    if (!spot || !aiAnalysis) return;

    const modal = document.getElementById('aiModal');
    const modalSpotName = document.getElementById('modalSpotName');
    const aiAnalysisContent = document.getElementById('aiAnalysisContent');
    const aiModalDisclaimer = document.getElementById('aiModalDisclaimer');

    modalSpotName.textContent = `${t('aiAnalysisTitle')} - ${spotName}`;
    aiAnalysisContent.innerHTML = aiAnalysis.trim();
    aiModalDisclaimer.textContent = t('aiDisclaimer');

    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function openInfoModal(spotName) {
    const spot = globalWeatherData.find(spot => spot.name === spotName);
    if (!spot || !spot.spotInfo) return;

    const modal = document.getElementById('infoModal');
    const modalSpotName = document.getElementById('infoModalSpotName');
    const spotInfoContent = document.getElementById('spotInfoContent');

    modalSpotName.textContent = `${spotName}`;

    const info = getSpotInfo(spot);
    spotInfoContent.innerHTML = `
                <div class="info-grid">
                    <div class="info-item" style="grid-column: 1 / -1;">
                        <div class="info-label">${t('overviewLabel')}</div>
                        <div class="info-value">${info.description}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('spotTypeLabel')}</div>
                        <div class="info-value">${info.type}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('bestWindLabel')}</div>
                        <div class="info-value">${info.bestWind}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('waterTempLabel')}</div>
                        <div class="info-value">${info.waterTemp}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('experienceLabel')}</div>
                        <div class="info-value">${info.experience}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('launchTypeLabel')}</div>
                        <div class="info-value">${info.launch}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('hazardsLabel')}</div>
                        <div class="info-value">${info.hazards}</div>
                    </div>
                    <div class="info-item" style="grid-column: 1 / -1;">
                        <div class="info-label">${t('seasonLabel')}</div>
                        <div class="info-value">${info.season}</div>
                    </div>
                </div>
            `;

    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeAIModal() {
    const modal = document.getElementById('aiModal');
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
}

function closeInfoModal() {
    const modal = document.getElementById('infoModal');
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
}

function openIcmModal(spotName, icmUrl) {
    const modal = document.getElementById('icmModal');
    const modalSpotName = document.getElementById('icmModalSpotName');
    const icmImage = document.getElementById('icmImage');

    modalSpotName.textContent = `${spotName} - ${t('icmForecastTitle')}`;
    icmImage.src = icmUrl;
    icmImage.alt = `${t('icmForecastTitle')} for ${spotName}`;

    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeIcmModal() {
    const modal = document.getElementById('icmModal');
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';

    // Clear the image source to stop loading
    const icmImage = document.getElementById('icmImage');
    icmImage.src = '';
}

function setupModals() {
    const aiModal = document.getElementById('aiModal');
    const aiCloseButton = document.getElementById('modalClose');
    const appInfoModal = document.getElementById('appInfoModal');
    const appInfoCloseButton = document.getElementById('appInfoModalClose');
    const infoModal = document.getElementById('infoModal');
    const infoCloseButton = document.getElementById('infoModalClose');
    const icmModal = document.getElementById('icmModal');
    const icmCloseButton = document.getElementById('icmModalClose');

    // AI Modal events
    aiCloseButton.addEventListener('click', closeAIModal);
    aiModal.addEventListener('click', (e) => {
        if (e.target === aiModal) {
            closeAIModal();
        }
    });

    // App Info Modal events
    if (appInfoCloseButton) {
        appInfoCloseButton.addEventListener('click', closeAppInfoModal);
    }
    if (appInfoModal) {
        appInfoModal.addEventListener('click', (e) => {
            if (e.target === appInfoModal) {
                closeAppInfoModal();
            }
        });
    }

    // Info Modal events
    infoCloseButton.addEventListener('click', closeInfoModal);
    infoModal.addEventListener('click', (e) => {
        if (e.target === infoModal) {
            closeInfoModal();
        }
    });

    // ICM Modal events
    icmCloseButton.addEventListener('click', closeIcmModal);
    icmModal.addEventListener('click', (e) => {
        if (e.target === icmModal) {
            closeIcmModal();
        }
    });

    // Escape key for all modals
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (aiModal.classList.contains('active')) {
                closeAIModal();
            }
            if (appInfoModal && appInfoModal.classList.contains('active')) {
                closeAppInfoModal();
            }
            if (infoModal.classList.contains('active')) {
                closeInfoModal();
            }
            if (icmModal.classList.contains('active')) {
                closeIcmModal();
            }
        }
    });
}

// ============================================================================
// SPOT CARD CREATION AND RENDERING
// ============================================================================

function createSpotCard(spot) {
    const card = document.createElement('div');
    card.className = 'spot-card';
    card.dataset.country = t(spot.country.replace(/\s+/g, ''));

    // Check if a spot has wave data
    const hasWaveData = spot.forecast && spot.forecast.some(day => day.wave !== undefined) ||
        (spot.currentConditions && spot.currentConditions.wave !== undefined);

    const spotConditions = getSpotConditions(spot);

    let forecastRows = '';
    if (spot.forecast && Array.isArray(spot.forecast)) {
        spot.forecast.forEach(day => {
            const windTextClass = getWindClass(day.wind);
            const gustTextClass = getWindClass(day.gusts);

            // Calculate average of base wind and gusts for row background
            const averageWind = (day.wind + day.gusts) / 2;
            const averageWindClass = getWindClass(averageWind);

            // Use average wind class for row background
            const rowWindClass = averageWindClass === 'wind-weak' ? 'weak-wind' :
                averageWindClass === 'wind-moderate' ? 'moderate-wind' :
                    averageWindClass === 'wind-strong' ? 'strong-wind' : 'extreme-wind';

            const tempClass = day.temp >= 18 ? 'temp-positive' : 'temp-negative';
            const windArrow = getWindArrow(day.direction);
            const precipClass = day.precipitation === 0 ? 'precipitation-none' : 'precipitation';

            // Wave classes
            let waveClass = '';
            let waveText = '-';
            if (day.wave !== undefined) {
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
                            ${hasWaveData ? `<td class="${waveClass}">${waveText}</td>` : ''}
                        </tr>
                    `;
        });
    }

    let currentConditionsRow = '';
    if (spotConditions && spotConditions.isCurrent) {
        const baseWind = spotConditions.wind;
        const gustWind = spotConditions.gusts;
        const windTextClass = getWindClass(baseWind);
        const gustTextClass = getWindClass(gustWind);

        const averageWind = (baseWind + gustWind) / 2;
        const averageWindClass = getWindClass(averageWind);
        const rowWindClass = averageWindClass === 'wind-weak' ? 'weak-wind' :
            averageWindClass === 'wind-moderate' ? 'moderate-wind' :
                averageWindClass === 'wind-strong' ? 'strong-wind' : 'extreme-wind';

        const hasTemperature = Number.isFinite(spotConditions.temp);
        const tempClass = hasTemperature
            ? (spotConditions.temp >= 20 ? 'temp-positive' : 'temp-negative')
            : '';
        const tempValue = hasTemperature ? `${spotConditions.temp}°C` : '-';
        const windArrow = getWindArrow(spotConditions.direction);

        let currentWaveClass = '';
        let currentWaveText = '-';
        if (spot.currentConditions && spot.currentConditions.wave !== undefined) {
            if (spot.currentConditions.wave < 1.0) {
                currentWaveClass = 'wave-small';
            } else if (spot.currentConditions.wave >= 1.0 && spot.currentConditions.wave < 2.0) {
                currentWaveClass = 'wave-moderate';
            } else {
                currentWaveClass = 'wave-large';
            }
            currentWaveText = `${spot.currentConditions.wave}`;
        }

        currentConditionsRow = `
                    <tr class="${rowWindClass}" style="border-bottom: 2px solid #404040;">
                        <td>
                            <div class="live-indicator">
                                <strong class="live-text">${t('nowLabel')}</strong>
                                <div class="live-dot"></div>
                            </div>
                        </td>
                        <td class="${windTextClass}">${baseWind} kts</td>
                        <td class="${gustTextClass}">${gustWind} kts</td>
                        <td class="${windTextClass}">
                            <span class="wind-arrow">${windArrow}</span> ${spotConditions.direction || '-'}
                        </td>
                        <td class="${tempClass}">${tempValue}</td>
                        <td>-</td>
                        ${hasWaveData ? `<td class="${currentWaveClass}">${currentWaveText}</td>` : ''}
                    </tr>
                `;
    }

    // Check if a spot is favorited
    const isFavorited = isFavorite(spot.name);
    const favoriteClass = isFavorited ? 'favorited' : '';

    card.innerHTML = `
                <div class="drag-handle" draggable="true"><svg class="drag-icon" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg"><circle cx="4" cy="4" r="1.5"/><circle cx="12" cy="4" r="1.5"/><circle cx="4" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/></svg></div>
                <div class="spot-header">
                    <div class="spot-title">
                        <div class="spot-name" onclick="window.location.href='${buildSpotUrl(spot.wgId)}'">${spot.name || 'Unknown Spot'}</div>
                    </div>
                    <div class="spot-meta">
                        <div class="country-tag-wrapper">
                            <div class="favorite-icon ${favoriteClass}" onclick="toggleFavorite('${spot.name}')" title="${isFavorited ? 'Remove from favorites' : 'Add to favorites'}">
                                <svg class="favorite-icon-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                                    <path d="M1.327,12.4,4.887,15,3.535,19.187A3.178,3.178,0,0,0,4.719,22.8a3.177,3.177,0,0,0,3.8-.019L12,20.219l3.482,2.559a3.227,3.227,0,0,0,4.983-3.591L19.113,15l3.56-2.6a3.227,3.227,0,0,0-1.9-5.832H16.4L15.073,2.432a3.227,3.227,0,0,0-6.146,0L7.6,6.568H3.231a3.227,3.227,0,0,0-1.9,5.832Z"/>
                                </svg>
                            </div>
                            <div class="country-tag">${t(spot.country.replace(/\s+/g, '')) || 'Unknown'}</div>
                        </div>
                        <div class="last-updated">${spot.lastUpdated || 'No data'}</div>
                    </div>
                </div>
                <div class="external-links">
                    ${spot.spotInfo ? `<span class="external-link info-link" onclick="openInfoModal('${spot.name}')"></span>` : ''}
                    ${spot.windguruUrl || spot.windguruFallbackUrl ? `<a href="${!spot.windguruUrl && spot.windguruFallbackUrl ? spot.windguruFallbackUrl : spot.windguruUrl}" target="_blank" class="external-link">WG</a>` : ''}
                    ${spot.windfinderUrl ? `<a href="${spot.windfinderUrl}" target="_blank" class="external-link">WF</a>` : ''}
                    ${spot.icmUrl ? `<span class="external-link" onclick="openIcmModal('${spot.name}', '${spot.icmUrl}')">ICM</span>` : ''}
                    ${spot.webcamUrl ? `<a href="${spot.webcamUrl}" target="_blank" class="external-link webcam-link">${t('camLinkLabel')}</a>` : ''}
                    ${spot.locationUrl ? `<a href="${spot.locationUrl}" target="_blank" class="external-link location-link">${t('mapLinkLabel')}</a>` : ''}
                    ${(spot.aiAnalysisEn || spot.aiAnalysisPl) ? `<span class="external-link ai-link" onclick="openAIModal('${spot.name}')">AI</span>` : ''}
                </div>
                <table class="weather-table">
                    <thead>
                        <tr>
                            <th>${t('dateHeader')}</th>
                            <th>${t('windHeader')}</th>
                            <th>${t('gustsHeader')}</th>
                            <th>${t('directionHeader')}</th>
                            <th>${t('tempHeader')}</th>
                            <th>${t('rainHeader')}</th>
                            ${hasWaveData ? `<th>${t('waveHeader')}</th>` : ''}
                        </tr>
                    </thead>
                    <tbody>
                        ${currentConditionsRow}
                        ${forecastRows}
                    </tbody>
                </table>
            `;

    return card;
}

function filterSpots(data, countryFilter, searchQuery) {
    let filtered = countryFilter === 'all' ? data : data.filter(spot => spot.country === countryFilter);

    if (searchQuery && searchQuery.trim() !== '') {
        const query = searchQuery.toLowerCase().trim();
        filtered = filtered.filter(spot => {
            return spot.name.toLowerCase().includes(query) ||
                (spot.country && spot.country.toLowerCase().includes(query));
        });
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
        return;
    }

    // Show loading message immediately
    showLoadingMessage();

    try {
        const data = await fetchAllSpots();

        globalWeatherData = data;

        // Populate country dropdown with available countries
        populateCountryDropdown(data);

        const filteredSpots = filterSpots(data, filter, searchQuery);
        displaySpots(filteredSpots, spotsGrid, filter, searchQuery);
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
        { key: 'spot', label: t('spotHeader'), sortable: true },
        { key: 'wind', label: t('windHeader'), sortable: true },
        { key: 'gust', label: t('gustsHeader'), sortable: true },
        { key: 'direction', label: t('directionHeader'), sortable: true },
        { key: 'temp', label: t('tempHeader'), sortable: true },
        { key: 'rain', label: t('rainHeader'), sortable: true },
        { key: 'country', label: t('countryHeader'), sortable: true },
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
            checkbox.addEventListener('change', (e) => {
                showOnlyLiveStations = e.target.checked;
                renderSpots(currentFilter, currentSearchQuery, true);
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
    row.dataset.country = t(spot.country.replace(/\s+/g, ''));
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
        indicator.className = 'list-indicator';
        indicatorCell.appendChild(indicator);
    }
    row.appendChild(indicatorCell);

    // Spot name column
    const nameCell = document.createElement('div');
    nameCell.className = 'list-spot-name';
    nameCell.textContent = spot.name || 'Unknown Spot';
    nameCell.onclick = () => navigateToSpot(spot.wgId);
    row.appendChild(nameCell);

    if (spotConditions) {
        // Wind column
        const windCell = document.createElement('div');
        windCell.className = `list-wind ${getWindClass(spotConditions.wind)}`;
        windCell.textContent = `${spotConditions.wind} kts`;
        row.appendChild(windCell);

        // Gust column
        const gustCell = document.createElement('div');
        gustCell.className = `list-gust ${getWindClass(spotConditions.gusts)}`;
        gustCell.textContent = `${spotConditions.gusts} kts`;
        row.appendChild(gustCell);

        // Direction column
        const directionCell = document.createElement('div');
        directionCell.className = 'list-direction';
        const arrow = getWindArrow(spotConditions.direction);
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
    countryCell.textContent = t(spot.country.replace(/\s+/g, '')) || 'Unknown';
    row.appendChild(countryCell);

    // Favorite column
    const favoriteCell = document.createElement('div');
    favoriteCell.className = 'list-favorite';
    const isFavorited = isFavorite(spot.name);
    const favoriteClass = isFavorited ? 'favorited' : '';

    const favoriteIcon = document.createElement('div');
    favoriteIcon.className = `favorite-icon ${favoriteClass}`;
    favoriteIcon.title = isFavorited ? t('removeFromFavorites') : t('addToFavorites');
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
    removeListOrder(currentFilter, currentSearchQuery);
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
                    aValue = aConditions ? aConditions.wind : -Infinity;
                    bValue = bConditions ? bConditions.wind : -Infinity;
                } else if (sortColumn === 'gust') {
                    aValue = aConditions ? aConditions.gusts : -Infinity;
                    bValue = bConditions ? bConditions.gusts : -Infinity;
                } else if (sortColumn === 'temp') {
                    aValue = aConditions && Number.isFinite(aConditions.temp) ? aConditions.temp : -Infinity;
                    bValue = bConditions && Number.isFinite(bConditions.temp) ? bConditions.temp : -Infinity;
                } else if (sortColumn === 'rain') {
                    aValue = aConditions && aConditions.precipitation !== null ? aConditions.precipitation : -Infinity;
                    bValue = bConditions && bConditions.precipitation !== null ? bConditions.precipitation : -Infinity;
                }

                return sortDirection === 'asc' ? aValue - bValue : bValue - aValue;
            }

            case 'direction': {
                const aConditions = getSpotConditions(a);
                const bConditions = getSpotConditions(b);
                aValue = aConditions ? aConditions.direction || '' : '';
                bValue = bConditions ? bConditions.direction || '' : '';
                return sortDirection === 'asc'
                    ? aValue.localeCompare(bValue)
                    : bValue.localeCompare(aValue);
            }

            case 'country':
                aValue = t(a.country.replace(/\s+/g, '')) || '';
                bValue = t(b.country.replace(/\s+/g, '')) || '';
                return sortDirection === 'asc'
                    ? aValue.localeCompare(bValue)
                    : bValue.localeCompare(aValue);

            default:
                return 0;
        }
    });

    return sorted;
}

function displaySpots(filteredSpots, spotsGrid, filter, searchQuery) {
    spotsGrid.innerHTML = '';
    if (filteredSpots.length === 0) {
        const message = searchQuery ?
            `${t('errorNoSpotsSearchDescription')} "${searchQuery}"` :
            `${t('errorNoSpotsDescription')}`;
        spotsGrid.innerHTML = `
                    <div class="error-message">
                        <span class="error-icon">🔍</span>
                        <div class="error-title">${t('errorNoSpotsTitle')}</div>
                        <div class="error-description">
                            ${message}<br/>
                            ${t('errorTryAdjusting')}
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
                        <span class="loading-text">${t('loadingText')}</span>
                    </div>
                `;
            // Retry loading after 5 seconds
            setTimeout(() => {
                renderSpots(filter, searchQuery, false, true);
            }, 5000);
        } else {
            // Check current view mode and render accordingly
            if (currentViewMode === 'list') {
                // Filter live stations if checkbox is enabled
                let spotsToShow = filteredSpots;
                if (showOnlyLiveStations) {
                    spotsToShow = filteredSpots.filter(spot => {
                        const conditions = getSpotConditions(spot);
                        return conditions && conditions.isCurrent;
                    });
                }

                // Apply sorting if a column is selected
                const sortedSpots = listSortColumn ? sortSpots(spotsToShow, listSortColumn, listSortDirection) : spotsToShow;

                // Render list view
                spotsGrid.appendChild(createListHeader());
                sortedSpots.forEach(spot => {
                    spotsGrid.appendChild(createListRow(spot));
                });
                loadListOrderFn();
            } else {
                // Render grid view
                filteredSpots.forEach(spot => {
                    spotsGrid.appendChild(createSpotCard(spot));
                });
                loadCardOrder();
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

function saveCardOrder() {
    const spotsGrid = document.getElementById('spotsGrid');
    const cards = spotsGrid.querySelectorAll('.spot-card');
    const order = Array.from(cards).map(card => {
        return card.querySelector('.spot-name').textContent;
    });

    const isThreeColumns = spotsGrid.classList.contains('three-columns');
    const columnMode = isThreeColumns ? '3col' : '2col';
    saveSpotOrder(columnMode, currentFilter, currentSearchQuery, order);
}

function loadCardOrder() {
    const spotsGrid = document.getElementById('spotsGrid');
    const isThreeColumns = spotsGrid.classList.contains('three-columns');
    const columnMode = isThreeColumns ? '3col' : '2col';
    const order = getSpotOrder(columnMode, currentFilter, currentSearchQuery);

    if (!order) return;

    try {
        const cards = Array.from(spotsGrid.querySelectorAll('.spot-card'));

        order.forEach(spotName => {
            const card = cards.find(c => c.querySelector('.spot-name').textContent === spotName);
            if (card) {
                spotsGrid.appendChild(card);
            }
        });
    } catch (e) {
        console.error('Failed to load card order:', e);
    }
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
    saveListOrder(currentFilter, currentSearchQuery, order);
}

function loadListOrderFn() {
    // Don't apply custom order when sorting is active
    if (listSortColumn) return;

    const spotsGrid = document.getElementById('spotsGrid');
    const order = getListOrder(currentFilter, currentSearchQuery);

    if (!order) return;

    try {
        const rows = Array.from(spotsGrid.querySelectorAll('.list-row'));
        const header = spotsGrid.querySelector('.spots-list-header');

        order.forEach(spotId => {
            const row = rows.find(r => r.dataset.spotId === spotId);
            if (row) {
                spotsGrid.appendChild(row);
            }
        });

        // Ensure header stays at top
        if (header) {
            spotsGrid.insertBefore(header, spotsGrid.firstChild);
        }
    } catch (e) {
        console.error('Failed to load list order:', e);
    }
}

// ============================================================================
// BACKGROUND AUTO-REFRESH FUNCTIONALITY
// ============================================================================

async function refreshDataInBackground() {
    try {
        // Fetch new data silently
        const freshData = await fetchAllSpots();

        // Update global data
        globalWeatherData = freshData;

        // Update country dropdown without changing selection
        populateCountryDropdown(freshData);

        // Silently update the current view
        if (showingFavorites) {
            // Update favorites without re-rendering (to avoid disruption)
            const spotsGrid = document.getElementById('spotsGrid');
            const favorites = getFavorites();
            const favoriteSpots = globalWeatherData.filter(spot => favorites.includes(spot.name));

            // Update existing cards instead of recreating them
            favoriteSpots.forEach(updatedSpot => {
                const existingCard = Array.from(spotsGrid.querySelectorAll('.spot-card')).find(card => {
                    const spotName = card.querySelector('.spot-name');
                    return spotName && spotName.textContent === updatedSpot.name;
                });

                if (existingCard) {
                    const newCard = createSpotCard(updatedSpot);
                    existingCard.replaceWith(newCard);
                }
            });
        } else {
            // Update regular filtered view
            const filteredSpots = filterSpots(globalWeatherData, currentFilter, currentSearchQuery);
            const spotsGrid = document.getElementById('spotsGrid');

            // Update existing cards instead of recreating them
            filteredSpots.forEach(updatedSpot => {
                const existingCard = Array.from(spotsGrid.querySelectorAll('.spot-card')).find(card => {
                    const spotName = card.querySelector('.spot-name');
                    return spotName && spotName.textContent === updatedSpot.name;
                });

                if (existingCard) {
                    const newCard = createSpotCard(updatedSpot);
                    existingCard.replaceWith(newCard);
                }
            });
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
    }, AUTO_REFRESH_INTERVAL);

    console.log('Auto-refresh started: updating every', AUTO_REFRESH_INTERVAL / 1000, 'seconds');
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
        if (isStarredUrl()) {
            if (!showingFavorites) {
                showingFavorites = true;
                setShowingFavorites(true);
                document.getElementById('favoritesToggle').classList.add('active');
                renderFavorites();
            }
        } else {
            // Navigating away from starred
            if (showingFavorites) {
                showingFavorites = false;
                setShowingFavorites(false);
                document.getElementById('favoritesToggle').classList.remove('active');
            }

            // Check if there's a country in URL
            const urlCountry = getCountryFromUrl();
            if (urlCountry) {
                const actualCountry = findCountryByNormalizedName(urlCountry);
                if (actualCountry) {
                    setSelectedCountry(actualCountry);
                    updatePageTitle(actualCountry);
                    renderSpots(actualCountry, '', true);
                }
            } else {
                // Default to saved country or 'all'
                const savedCountry = getSelectedCountry();
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

// ============================================================================
// KITE SIZE CALCULATOR
// ============================================================================

function calculateKiteSize(windSpeed, riderWeight, skillLevel) {
    // Base calculation: kite size (m²) = rider weight (kg) * factor / wind speed (kts)
    let factor = 2.5; // Default factor for intermediate flat water

    // Adjust factor based on skill level and conditions
    const factorAdjustments = {
        'beginner-flat': 3.0,         // Beginners need larger kites
        'beginner-small': 2.8,        // Slightly smaller for small waves
        'intermediate-flat': 2.5,     // Standard factor
        'intermediate-medium': 2.3,   // Medium waves need smaller kites
        'advanced-flat': 2.2,         // Advanced riders can use smaller kites
        'advanced-medium': 2.0,       // Advanced with medium waves
        'advanced-large': 1.8         // Advanced with large waves need smallest kites
    };

    if (skillLevel && factorAdjustments[skillLevel]) {
        factor = factorAdjustments[skillLevel];
    }

    // Calculate base kite size
    let kiteSize = (riderWeight * factor) / windSpeed;

    // Round to the nearest common kite size (7, 9, 10, 12, 14, 15, 17 m²)
    const commonSizes = [7, 9, 10, 12, 14, 15, 17];
    kiteSize = commonSizes.reduce((prev, curr) =>
        Math.abs(curr - kiteSize) < Math.abs(prev - kiteSize) ? curr : prev
    );

    return kiteSize;
}

function calculateBoardSize(riderWeight, skillLevel) {
    // Determine a board type first
    let boardType = 'Twin Tip';
    if (skillLevel.includes('large') || (skillLevel.includes('medium') && skillLevel.includes('advanced'))) {
        boardType = 'Surfboard/Directional 🏄';
    }

    if (boardType === 'Twin Tip') {
        // Twin Tip calculation with realistic sizes (125-160 cm)
        let baseSize = 125; // Minimum realistic size

        // Add size based on weight (up to 115 kg)
        const effectiveWeight = Math.min(riderWeight, 115);
        baseSize += (effectiveWeight - 50) * 0.5; // ~32.5 cm range for 50-115 kg

        // Adjust based on skill level
        const sizeAdjustments = {
            'beginner-flat': 1.15,          // Larger for stability
            'beginner-small': 1.10,
            'intermediate-flat': 1.0,       // Standard size
            'intermediate-medium': 0.98,
            'advanced-flat': 0.92,          // Smaller for maneuverability
            'advanced-medium': 0.90
        };

        const adjustment = sizeAdjustments[skillLevel] || 1.0;
        let boardSize = Math.round(baseSize * adjustment);

        // Round to nearest 2 cm
        boardSize = Math.round(boardSize / 2) * 2;

        // Apply minimum and maximum size constraints
        const minSize = 136; // Minimum board size
        let maxSize = 160; // Default for beginners
        if (skillLevel.includes('intermediate')) {
            maxSize = 142;
        } else if (skillLevel.includes('advanced')) {
            maxSize = 142;
        }
        boardSize = Math.max(minSize, Math.min(boardSize, maxSize));

        // Calculate width (typically 38-46 cm for twin tips)
        let width = 38 + Math.floor((boardSize - 136) / 4);
        width = Math.min(width, 46);

        return `${boardSize} x ${width} cm (${boardType})`;
    } else {
        // Surfboard/Directional sizes (typically 5'4" to 6'2")
        const sizeInFeet = riderWeight < 70 ? "5'6\"" : riderWeight < 85 ? "5'10\"" : "6'0\"";
        return `${sizeInFeet} (${boardType})`;
    }
}

function setupKiteSizeCalculator() {
    const kiteSizeButton = document.getElementById('kiteSizeToggle');
    const kiteSizeModal = document.getElementById('kiteSizeModal');
    const kiteSizeCloseButton = document.getElementById('kiteSizeModalClose');
    const calculateBtn = document.getElementById('calculateBtn');

    // Open modal
    kiteSizeButton.addEventListener('click', () => {
        kiteSizeModal.classList.add('active');
        document.body.style.overflow = 'hidden';
        // Reset result visibility
        document.getElementById('calcResult').classList.remove('show');
    });

    // Close modal
    function closeKiteSizeModal() {
        kiteSizeModal.classList.remove('active');
        document.body.style.overflow = 'auto';
    }

    kiteSizeCloseButton.addEventListener('click', closeKiteSizeModal);

    kiteSizeModal.addEventListener('click', (e) => {
        if (e.target === kiteSizeModal) {
            closeKiteSizeModal();
        }
    });

    // Calculate button
    calculateBtn.addEventListener('click', () => {
        const windSpeed = parseFloat(document.getElementById('windSpeed').value);
        const riderWeight = parseFloat(document.getElementById('riderWeight').value);
        const skillLevel = document.getElementById('skillLevel').value;
        const calcWarning = document.getElementById('calcWarning');
        const calcResult = document.getElementById('calcResult');

        // Hide previous warnings and results
        calcWarning.style.display = 'none';
        calcResult.classList.remove('show');

        // Validation
        if (!windSpeed || windSpeed < 5 || windSpeed > 50) {
            calcWarning.innerHTML = '⚠️ <strong>INVALID WIND SPEED</strong><br><br>Please enter a valid wind speed between 5 and 50 knots.';
            calcWarning.style.display = 'block';
            return;
        }

        if (!riderWeight || riderWeight > 150) {
            calcWarning.innerHTML = '⚠️ <strong>INVALID RIDER WEIGHT</strong><br><br>Please enter a valid rider weight between 40 and 150 kg.';
            calcWarning.style.display = 'block';
            return;
        }

        if (riderWeight < 40) {
            calcWarning.innerHTML = '⚠️ <strong>WEIGHT TOO LOW</strong><br><br>Your weight is too low and riding may be dangerous. Kitesurfing requires sufficient body weight for control and safety.';
            calcWarning.style.display = 'block';
            return;
        }

        if (!skillLevel) {
            calcWarning.innerHTML = '⚠️ <strong>SKILL LEVEL REQUIRED</strong><br><br>Please select your skill level and conditions.';
            calcWarning.style.display = 'block';
            return;
        }

        // Check for warning conditions
        if (riderWeight > 120) {
            calcWarning.innerHTML = '😉 Maybe you should lose some weight before going onto the water!';
            calcWarning.style.display = 'block';
            return;
        }

        if (windSpeed > 40) {
            calcWarning.innerHTML = '⚠️ <strong>EXTREME CONDITIONS!</strong><br><br>Wind speeds above 40 knots are extremely dangerous. We strongly recommend NOT going onto the water in these conditions. Stay safe!';
            calcWarning.style.display = 'block';
            return;
        }

        if (windSpeed < 12) {
            calcWarning.innerHTML = '💨 <strong>LOW WIND CONDITIONS</strong><br><br>There is not enough wind for riding with a regular setup. However, you could try going on a <strong>kite foil board</strong>, which works great in light wind conditions!';
            calcWarning.style.display = 'block';
            return;
        }

        // Check for low wind with medium or large waves
        if (windSpeed < 15 && (skillLevel.includes('medium') || skillLevel.includes('large'))) {
            calcWarning.innerHTML = '🌊 <strong>INSUFFICIENT WIND FOR WAVE CONDITIONS</strong><br><br>Wind speed below 15 knots is not enough for riding in medium or large waves. Waves create additional resistance and you need more wind power to maintain speed and control. We recommend NOT going onto the water in these conditions.';
            calcWarning.style.display = 'block';
            return;
        }

        // Calculate
        const kiteSize = calculateKiteSize(windSpeed, riderWeight, skillLevel);
        const boardSize = calculateBoardSize(riderWeight, skillLevel);

        // Display results
        document.getElementById('kiteSize').textContent = `${kiteSize} m²`;
        document.getElementById('boardSize').textContent = boardSize;
        calcResult.classList.add('show');
    });

    // Escape key support
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && kiteSizeModal.classList.contains('active')) {
            closeKiteSizeModal();
        }
    });
}

// ============================================================================
// COLUMN LAYOUT TOGGLE (2 vs 3 columns)
// ============================================================================

// Global variables for list view
let currentViewMode = 'grid'; // 'grid' or 'list'
let desktopViewMode = 'grid'; // Store desktop preference separately
let listSortColumn = null;
let listSortDirection = 'asc';
let showOnlyLiveStations = false; // Filter for live stations in list view

function isMobileView() {
    return window.innerWidth <= 768;
}

function isDrawerView() {
    return window.innerWidth <= 1430;
}

function setupColumnToggle() {
    const columnToggle = document.getElementById('columnToggle');
    if (!columnToggle) {
        return;
    }

    const spotsGrid = document.getElementById('spotsGrid');
    const iconGrid = document.getElementById('iconGrid');
    const iconList = document.getElementById('iconList');

    // Load saved desktop preference or default to grid
    desktopViewMode = getDesktopViewMode();

    // Set initial view based on viewport width
    if (isDrawerView()) {
        currentViewMode = 'list'; // Always list when drawer menu is active
    } else {
        currentViewMode = desktopViewMode; // Use saved preference on full desktop
    }

    function updateView() {
        if (currentViewMode === 'list') {
            spotsGrid.classList.remove('spots-grid', 'three-columns');
            spotsGrid.classList.add('spots-list');
            iconGrid.style.display = 'none';
            iconList.style.display = 'block';
        } else {
            spotsGrid.classList.remove('spots-list');
            spotsGrid.classList.add('spots-grid', 'three-columns');
            iconGrid.style.display = 'block';
            iconList.style.display = 'none';
        }
    }

    updateView();

    columnToggle.addEventListener('click', () => {
        if (isMapView) {
            hideMapView({ skipRender: true });
        }

        // Toggle view mode
        currentViewMode = currentViewMode === 'grid' ? 'list' : 'grid';

        // Only save to desktop preference if not in drawer/mobile view
        if (!isDrawerView()) {
            desktopViewMode = currentViewMode;
            setDesktopViewMode(desktopViewMode);
        }

        updateView();

        // Re-render with current filter and search
        renderSpots(currentFilter, currentSearchQuery, true);
    });

    // Handle viewport resize
    let resizeTimer;
    let wasDrawerView = isDrawerView();

    window.addEventListener('resize', () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(() => {
            const isNowDrawerView = isDrawerView();
            const wasMobile = currentViewMode === 'list' && isMobileView();

            // Drawer menu appears at <= 1430px, same as in CSS
            if (isNowDrawerView && !wasDrawerView) {
                // Just entered drawer view - switch to list view
                if (currentViewMode === 'grid') {
                    currentViewMode = 'list';
                    updateView();
                    renderSpots(currentFilter, currentSearchQuery, true);
                }
                wasDrawerView = true;
            } else if (!isNowDrawerView && wasDrawerView) {
                // Just exited drawer view - restore original desktop view
                if (currentViewMode !== desktopViewMode) {
                    currentViewMode = desktopViewMode;
                    updateView();
                    renderSpots(currentFilter, currentSearchQuery, true);
                }
                wasDrawerView = false;
            } else if (isMobileView()) {
                // Mobile view (<=768px) - always use list view
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

    const sponsors = await fetchSponsors();

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
    const urlCountry = getCountryFromUrl();

    if (urlCountry) {
        // Wait for data to be loaded to validate country
        fetchAllSpots().then(data => {
            globalWeatherData = data;
            populateCountryDropdown(data);

            // Find the actual country name from URL
            const actualCountry = findCountryByNormalizedName(urlCountry);

            if (actualCountry) {
                // Valid country in URL
                setSelectedCountry(actualCountry);
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
        const savedCountry = getSelectedCountry();
        updateUrlForCountry(savedCountry);
        updatePageTitle(savedCountry);
        renderSpots(savedCountry);

        // Start auto-refresh after an initial load
        startAutoRefresh();
    }
}

function handleStarredURL() {
    if (isMapUrl()) {
        handleMapRoute();
        return;
    }

    // Check for /starred URL first
    if (isStarredUrl()) {
        // Load favorites directly
        showingFavorites = true;
        setShowingFavorites(true);
        const favoritesToggle = document.getElementById('favoritesToggle');
        if (favoritesToggle) {
            favoritesToggle.classList.add('active');
        }
        updatePageTitle('all'); // Will be overridden by renderFavorites if needed
        renderFavorites();
        // Start auto-refresh after an initial load
        startAutoRefresh();
    } else {
        handleCountryURL();
    }
}

function handleMapRoute() {
    const savedCountry = getSelectedCountry();

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
    reloadPage();
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

let map = null;
let mapMarkers = [];
let mapTileLayer = null;
let isMapView = false;
let currentMapLayer = 'satellite'; // 'satellite' or 'osm'

function initMap() {
    if (map) return; // Already initialized

    const mapContainer = document.getElementById('map');
    if (!mapContainer) return;

    // Initialize Leaflet map
    map = L.map('map').setView([51.505, -0.09], 2); // Default world view

    // Add base tile layer
    mapTileLayer = updateTileLayer(map, mapTileLayer, currentMapLayer);

    // Add layer switcher control using common module
    const layerSwitcher = createLayerSwitcher({
        getCurrentLayer: () => currentMapLayer,
        onLayerChange: (newLayer) => {
            currentMapLayer = newLayer;
            mapTileLayer = updateTileLayer(map, mapTileLayer, currentMapLayer);
        }
    });
    map.addControl(layerSwitcher);
}

function buildMapPopupWindDetails(spotConditions) {
    if (!spotConditions) {
        return '';
    }

    const arrow = getWindArrow(spotConditions.direction);
    const gustLabel = Number.isFinite(spotConditions.gusts) ? `${spotConditions.gusts} kts` : '-';
    const windClass = getWindClass(spotConditions.wind);
    const directionLabel = spotConditions.direction || '-';
    const forecastMeta = !spotConditions.isCurrent
        ? `<div class="map-popup-meta">${t('forecastEstimateLabel')}${spotConditions.label ? ` · ${spotConditions.label}` : ''}</div>`
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

function addMarkersToMap(spots) {
    // Clear existing markers
    mapMarkers.forEach(marker => marker.remove());
    mapMarkers = [];

    if (!map || !spots || spots.length === 0) return;

    const bounds = [];

    spots.forEach(spot => {
        if (!spot.coordinates || !spot.coordinates.lat || !spot.coordinates.lon) return;

        const lat = spot.coordinates.lat;
        const lng = spot.coordinates.lon;

        const spotConditions = getSpotConditions(spot);
        const markerWindClass = spotConditions
            ? getWindClass(spotConditions.wind)
            : 'wind-no-data';

        const markerIcon = L.divIcon({
            className: 'custom-marker-icon',
            html: `<div class="custom-marker ${markerWindClass}"><div class="marker-dot"></div></div>`,
            iconSize: [18, 18],
            iconAnchor: [9, 9]
        });

        // Create marker
        const marker = L.marker([lat, lng], { icon: markerIcon }).addTo(map);

        const popupWindDetails = buildMapPopupWindDetails(spotConditions);

        // Add popup with clickable spot name and wind summary
        marker.bindPopup(`
            <div class="map-popup">
                <a href="${buildSpotUrl(spot.wgId)}" style="color: var(--accent-primary); text-decoration: none; font-weight: 600;">${spot.name}</a>
                ${popupWindDetails}
            </div>
        `);

        mapMarkers.push(marker);
        bounds.push([lat, lng]);
    });

    // Fit map to show all markers
    if (bounds.length > 0) {
        map.fitBounds(bounds, { padding: [50, 50] });
    }
}

function showMapView() {
    if (showingFavorites) {
        exitFavoritesMode({ skipRender: true, skipScroll: true });
    }

    isMapView = true;
    const spotsGrid = document.getElementById('spotsGrid');
    const mapContainer = document.getElementById('mapContainer');
    const mapToggle = document.getElementById('mapToggle');

    // Hide spots grid
    spotsGrid.style.display = 'none';

    // Show map container
    mapContainer.style.display = 'block';

    // Mark button as active
    mapToggle.classList.add('active');


    // Initialize map if not already done
    initMap();

    // Add markers for filtered spots
    const filteredSpots = filterSpots(globalWeatherData, currentFilter, currentSearchQuery);
    addMarkersToMap(filteredSpots);

    // Invalidate map size (needed for proper rendering)
    if (map) map.invalidateSize();

    // Update URL to /map
    pushMapUrl();

    // Scroll to top when opening map view
    window.scrollTo({
        top: 0,
        behavior: 'smooth'
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

    // Show spots grid
    spotsGrid.style.display = '';

    // Hide map container
    mapContainer.style.display = 'none';

    // Remove active state from button
    mapToggle.classList.remove('active');


    // Restore previous URL
    pushUrl(buildCountryUrl(currentFilter));

    // Re-render spots to clear any filter changes
    if (!skipRender) {
        renderSpots(currentFilter, currentSearchQuery, true);
    }
}

function setupMapToggle() {
    const mapToggle = document.getElementById('mapToggle');
    if (!mapToggle) return;

    mapToggle.addEventListener('click', () => {
        if (isMapView) {
            hideMapView();
        } else {
            showMapView();
        }
    });
}

function updateMapMarkers() {
    if (!isMapView) return;

    const filteredSpots = filterSpots(globalWeatherData, currentFilter, currentSearchQuery);
    addMarkersToMap(filteredSpots);
}

// ============================================================================
// GLOBAL WINDOW FUNCTIONS (for onclick handlers)
// ============================================================================

// Make functions global for onclick handlers
window.openAppInfoModal = openAppInfoModal;
window.closeAppInfoModal = closeAppInfoModal;
window.openAIModal = openAIModal;
window.closeAIModal = closeAIModal;
window.openInfoModal = openInfoModal;
window.closeInfoModal = closeInfoModal;
window.openIcmModal = openIcmModal;
window.closeIcmModal = closeIcmModal;
window.toggleFavorite = toggleFavorite;

// ============================================================================
// MAIN INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
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
    setupKiteSizeCalculator();
    setupColumnToggle();
    setupMapToggle();
    handlePopState();
    setupInfoToggle();
    renderMainSponsors();
    handleStarredURL();
});
