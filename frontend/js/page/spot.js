import {getCountryFlag} from '../common/country-flags.js';
import {t, translations} from '../common/translations.js';

// ============================================================================
// GLOBAL STATE MANAGEMENT
// ============================================================================

// Current spot being displayed
let currentSpot = null;

// Current language setting
let currentLanguage = 'en';

// Error state tracking
let currentErrorKey = null;
let currentErrorText = '';

// Loading state tracking
let currentLoadingKey = 'loadingSpotData';

// Polling and refresh interval IDs
let forecastPollIntervalId = null;
let forecastTimeoutId = null;
let backgroundRefreshIntervalId = null;

// Current spot ID from URL
let currentSpotId = null;

// Selected forecast model (gfs or ifs)
let selectedModel = 'gfs';

// Embed widget configuration
let embedViewSelection = 'conditions';
let embedThemeSelection = 'dark';
let embedLanguageSelection = 'en';

// ============================================================================
// CONFIGURATION CONSTANTS
// ============================================================================

const API_ENDPOINT = '/api/v1/spots';
const FORECAST_POLL_INTERVAL = 5000;       // 5 seconds
const FORECAST_TIMEOUT_MS = 30000;         // 30 seconds
const BACKGROUND_REFRESH_INTERVAL = 60000; // 1 minute

const MAP_TAB_ICON = `<svg class="tab-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12,12A4,4,0,1,0,8,8,4,4,0,0,0,12,12Zm0-6a2,2,0,1,1-2,2A2,2,0,0,1,12,6Zm8.66,3.157-.719-.239A8,8,0,0,0,12,0,7.993,7.993,0,0,0,4.086,9.092a5.045,5.045,0,0,0-2.548,1.3A4.946,4.946,0,0,0,0,14v4.075a5.013,5.013,0,0,0,3.6,4.8l2.87.9A4.981,4.981,0,0,0,7.959,24a5.076,5.076,0,0,0,1.355-.186l5.78-1.71a2.987,2.987,0,0,1,1.573,0l2.387.8A4,4,0,0,0,24,19.021V13.872A5.015,5.015,0,0,0,20.66,9.156ZM7.758,3.762a5.987,5.987,0,0,1,8.484,0,6.037,6.037,0,0,1,.011,8.5L12.7,15.717a.992.992,0,0,1-1.389,0L7.758,12.277A6.04,6.04,0,0,1,7.758,3.762ZM22,19.021a1.991,1.991,0,0,1-.764,1.572,1.969,1.969,0,0,1-1.626.395L17.265,20.2a5.023,5.023,0,0,0-2.717-.016L8.764,21.892a3,3,0,0,1-1.694-.029l-2.894-.9A3.013,3.013,0,0,1,2,18.075V14a2.964,2.964,0,0,1,.92-2.163,3.024,3.024,0,0,1,1.669-.806A8.021,8.021,0,0,0,6.354,13.7l3.567,3.453a2.983,2.983,0,0,0,4.174,0l3.563-3.463a7.962,7.962,0,0,0,1.813-2.821l.537.178A3.006,3.006,0,0,1,22,13.872Z"/></svg>`;
const PHOTO_TAB_ICON = `<svg class="tab-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M19,0H5A5.006,5.006,0,0,0,0,5V19a5.006,5.006,0,0,0,5,5H19a5.006,5.006,0,0,0,5-5V5A5.006,5.006,0,0,0,19,0ZM5,2H19a3,3,0,0,1,3,3V19a2.951,2.951,0,0,1-.3,1.285l-9.163-9.163a5,5,0,0,0-7.072,0L2,14.586V5A3,3,0,0,1,5,2ZM5,22a3,3,0,0,1-3-3V17.414l4.878-4.878a3,3,0,0,1,4.244,0L20.285,21.7A2.951,2.951,0,0,1,19,22Z"/><path fill="currentColor" d="M16,10.5A3.5,3.5,0,1,0,12.5,7,3.5,3.5,0,0,0,16,10.5Zm0-5A1.5,1.5,0,1,1,14.5,7,1.5,1.5,0,0,1,16,5.5Z"/></svg>`;

// ============================================================================
// URL AND MODEL SELECTION HELPERS
// ============================================================================

// Extract spot ID from the URL pattern: /spot/{spotId}
function getSpotIdFromUrl() {
    const pathParts = window.location.pathname.split('/');
    const spotIndex = pathParts.indexOf('spot');
    if (spotIndex !== -1 && pathParts.length > spotIndex + 1) {
        return pathParts[spotIndex + 1];
    }
    return null;
}

// Get a selected forecast model from sessionStorage
function getSelectedModel() {
    const model = sessionStorage.getItem('forecastModel');
    if (model && (model === 'gfs' || model === 'ifs')) {
        return model;
    }
    return 'gfs';
}

// Set the selected forecast model in sessionStorage
function setSelectedModel(model) {
    selectedModel = model;
    sessionStorage.setItem('forecastModel', model);
}

// ============================================================================
// API FUNCTIONS
// ============================================================================

// Fetch single spot data from the API
async function fetchSpotData(spotId) {
    try {
        const model = getSelectedModel();
        const url = `${API_ENDPOINT}/${spotId}${model ? '/' + model : ''}`;
        const response = await fetch(url);
        if (!response.ok) {
            const error = new Error(`HTTP error! status: ${response.status}`);
            error.status = response.status;
            throw error;
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching spot data:', error);
        throw error;
    }
}

// ============================================================================
// DATA VALIDATION HELPERS
// ============================================================================

// Check if a spot has valid forecast data
function hasForecastData(spot) {
    return spot && Array.isArray(spot.forecast) && spot.forecast.length > 0;
}

// ============================================================================
// POLLING AND REFRESH MANAGEMENT
// ============================================================================

// Clear forecast polling timers
function clearForecastPolling() {
    if (forecastPollIntervalId) {
        clearInterval(forecastPollIntervalId);
        forecastPollIntervalId = null;
    }
    if (forecastTimeoutId) {
        clearTimeout(forecastTimeoutId);
        forecastTimeoutId = null;
    }
}

// Clear background refresh timer
function clearBackgroundRefresh() {
    if (backgroundRefreshIntervalId) {
        clearInterval(backgroundRefreshIntervalId);
        backgroundRefreshIntervalId = null;
    }
}

// Set loading message and hide the error
function setLoadingMessage(key) {
    const loadingMessage = document.getElementById('loadingMessage');
    const loadingText = document.getElementById('loadingText');
    const errorMessage = document.getElementById('errorMessage');

    currentLoadingKey = key;

    if (errorMessage) {
        errorMessage.style.display = 'none';
    }

    if (loadingMessage) {
        loadingMessage.style.display = 'block';
    }

    if (loadingText) {
        loadingText.textContent = t(key);
    }
}

// Start polling for forecast data (when initially empty)
function startForecastPolling(spotId) {
    clearForecastPolling();

    let pollingInProgress = false;

    forecastPollIntervalId = setInterval(async () => {
        if (pollingInProgress) {
            return;
        }
        pollingInProgress = true;
        try {
            const latestSpot = await fetchSpotData(spotId);
            if (hasForecastData(latestSpot)) {
                clearForecastPolling();
                displaySpot(latestSpot);
            }
        } catch (error) {
            if (error && error.status === 404) {
                clearForecastPolling();
                displayError('spotNotFound');
            }
        } finally {
            pollingInProgress = false;
        }
    }, FORECAST_POLL_INTERVAL);

    forecastTimeoutId = setTimeout(() => {
        clearForecastPolling();
        displayError('forecastTimeout');
    }, FORECAST_TIMEOUT_MS);
}

// Start background refresh for live data updates
function startBackgroundRefresh(spotId) {
    if (backgroundRefreshIntervalId || !spotId) {
        return;
    }

    backgroundRefreshIntervalId = setInterval(async () => {
        try {
            const latestSpot = await fetchSpotData(spotId);
            if (!hasForecastData(latestSpot)) {
                return;
            }

            // Only update if data has changed
            if (!currentSpot || JSON.stringify(latestSpot) !== JSON.stringify(currentSpot)) {
                displaySpot(latestSpot);
            }
        } catch (error) {
            if (error && error.status === 404) {
                clearBackgroundRefresh();
                displayError('spotNotFound');
            } else {
                console.warn('Background refresh failed:', error);
            }
        }
    }, BACKGROUND_REFRESH_INTERVAL);
}

// ============================================================================
// WEATHER DISPLAY HELPER FUNCTIONS
// ============================================================================

// Get wind arrow symbol based on a direction
function getWindArrow(direction) {
    const arrows = {
        'N': '↓',
        'NE': '↙',
        'E': '←',
        'SE': '↖',
        'S': '↑',
        'SW': '↗',
        'W': '→',
        'NW': '↘'
    };
    return arrows[direction] || '•';
}

// Get wind rotation angle for arrow (in degrees)
function getWindRotation(direction) {
    const rotations = {
        'N': 0,
        'NE': 45,
        'E': 90,
        'SE': 135,
        'S': 180,
        'SW': 225,
        'W': 270,
        'NW': 315
    };
    return rotations[direction] || 0;
}

// ============================================================================
// DATE AND TIME FORMATTING FUNCTIONS
// ============================================================================

// Translate day names (Mon, Tue, Wed, etc.)
function translateDayName(dayName) {
    if (!dayName || typeof dayName !== 'string') {
        return '';
    }

    const token = dayName.substring(0, 3);
    const keyMap = {
        Mon: 'dayMon',
        Tue: 'dayTue',
        Wed: 'dayWed',
        Thu: 'dayThu',
        Fri: 'dayFri',
        Sat: 'daySat',
        Sun: 'daySun'
    };

    const translationKey = keyMap[token];
    return translationKey ? t(translationKey) : dayName;
}

// Translate month names (Jan, Feb, Mar, etc.)
function translateMonthName(monthName) {
    if (!monthName || typeof monthName !== 'string') {
        return '';
    }

    const keyMap = {
        Jan: 'monthJan',
        Feb: 'monthFeb',
        Mar: 'monthMar',
        Apr: 'monthApr',
        May: 'monthMay',
        Jun: 'monthJun',
        Jul: 'monthJul',
        Aug: 'monthAug',
        Sep: 'monthSep',
        Oct: 'monthOct',
        Nov: 'monthNov',
        Dec: 'monthDec'
    };

    const translationKey = keyMap[monthName];
    return translationKey ? t(translationKey) : monthName;
}

// Format forecast date label for table view (responsive)
function formatForecastDateLabel(rawDate) {
    if (!rawDate || typeof rawDate !== 'string') {
        return rawDate || '';
    }

    const tokens = rawDate.split(' ').filter(Boolean);
    if (tokens.length < 5) {
        return rawDate;
    }

    const [dayToken, dayOfMonthToken, monthToken, , timeToken] = tokens;
    const translatedDay = translateDayName(dayToken);
    const formattedDayOfMonth = dayOfMonthToken.padStart(2, '0');

    // Check if mobile view (screen width <= 768px)
    const isMobile = window.innerWidth <= 768;

    if (isMobile) {
        // Mobile: Show only first two letters of day and hour (without minutes)
        const hour = timeToken.split(':')[0];
        const shortDay = translatedDay.substring(0, 2);
        return `${shortDay} ${hour}`.trim();
    } else {
        // Desktop: Show full date with day of month, day of week, and time
        return `${formattedDayOfMonth}. ${translatedDay} ${timeToken}`.trim();
    }
}

// Format day label for windguru horizontal view
function formatDayLabel(dateStr) {
    if (!dateStr) return '';
    const tokens = dateStr.split(' ').filter(Boolean);
    if (tokens.length < 4) return '';

    const dayToken = tokens[0];
    const dayOfMonthToken = tokens[1];
    const monthToken = tokens[2];

    const translatedDay = translateDayName(dayToken);
    const shortDay = translatedDay.substring(0, 3);
    const translatedMonth = translateMonthName(monthToken);

    return `${shortDay} ${dayOfMonthToken}/${translatedMonth}`;
}

// Parse forecast date string (e.g., "Tue 28 Oct 2025 14:00")
function parseForecastDate(dateStr) {
    if (!dateStr) return new Date();

    try {
        const parts = dateStr.trim().split(/\s+/);
        if (parts.length >= 5) {
            const dayOfMonth = parseInt(parts[1]);
            const monthName = parts[2];
            const year = parseInt(parts[3]);
            const timeParts = parts[4].split(':');
            const hours = parseInt(timeParts[0]);
            const minutes = parseInt(timeParts[1]);

            // Map month names to month numbers (0-11)
            const monthMap = {
                'Jan': 0, 'Feb': 1, 'Mar': 2, 'Apr': 3,
                'May': 4, 'Jun': 5, 'Jul': 6, 'Aug': 7,
                'Sep': 8, 'Oct': 9, 'Nov': 10, 'Dec': 11
            };

            const monthNumber = monthMap[monthName];
            if (monthNumber !== undefined) {
                const parsed = new Date(year, monthNumber, dayOfMonth, hours, minutes);
                if (!isNaN(parsed.getTime())) {
                    return parsed;
                }
            }
        }
    } catch (e) {
        console.warn('Error parsing forecast date:', dateStr, e);
    }

    return new Date();
}

// Find forecast closest to current time
function findClosestForecast(forecastData) {
    if (!forecastData || forecastData.length === 0) {
        return null;
    }

    const now = new Date();
    let closestForecast = forecastData[0];
    let minDiff = Math.abs(parseForecastDate(forecastData[0].date) - now);

    for (let i = 1; i < forecastData.length; i++) {
        const forecastTime = parseForecastDate(forecastData[i].date);
        const diff = Math.abs(forecastTime - now);
        if (diff < minDiff) {
            minDiff = diff;
            closestForecast = forecastData[i];
        }
    }

    return closestForecast;
}

// ============================================================================
// LANGUAGE AND SPOT INFO HELPERS
// ============================================================================

// Get spot info based on the current language
function getSpotInfo(spot) {
    if (!spot) return null;
    const lang = localStorage.getItem('language') || 'en';
    // Direct access - no fallback, all translations are complete
    return lang === 'pl' ? spot.spotInfoPL : spot.spotInfo;
}

// Get current language code
function getCurrentLanguageCode() {
    return currentLanguage || localStorage.getItem('language') || 'en';
}

// Get AI analysis for the current language with fallbacks
function getAiAnalysisForCurrentLanguage(spot) {
    if (!spot) return '';

    const sanitize = (value) => (typeof value === 'string' && value.trim().length > 0 ? value : '');

    const aiEn = sanitize(spot.aiAnalysisEn);
    const aiPl = sanitize(spot.aiAnalysisPl);
    const aiLegacy = sanitize(spot.aiAnalysis);
    const lang = getCurrentLanguageCode();

    if (lang === 'pl') {
        return aiPl || aiEn || aiLegacy;
    }

    return aiEn || aiPl || aiLegacy;
}

// ============================================================================
// MODAL FUNCTIONS
// ============================================================================

// Open spot information modal
function openInfoModal(spotName) {
    if (!currentSpot || !currentSpot.spotInfo) return;

    const modal = document.getElementById('infoModal');
    const modalSpotName = document.getElementById('infoModalSpotName');
    const spotInfoContent = document.getElementById('spotInfoContent');

    modalSpotName.textContent = `${spotName}`;

    const info = getSpotInfo(currentSpot);
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

// Closing spot information modal
function closeInfoModal() {
    const modal = document.getElementById('infoModal');
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
}

// Open AI analysis modal
function openAIModal(spotName) {
    if (!currentSpot) return;

    const aiAnalysis = getAiAnalysisForCurrentLanguage(currentSpot);
    if (!aiAnalysis) return;

    const modal = document.getElementById('aiModal');
    const modalTitle = document.getElementById('aiModalTitle');
    const aiAnalysisContent = document.getElementById('aiAnalysisContent');

    modalTitle.textContent = `${spotName} - ${t('aiAnalysisTitle')}`;
    aiAnalysisContent.innerHTML = `<p>${aiAnalysis}</p>`;

    const aiModalDisclaimer = document.getElementById('aiModalDisclaimer');
    if (aiModalDisclaimer) {
        aiModalDisclaimer.textContent = t('aiDisclaimer');
    }

    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

// Close AI analysis modal
function closeAIModal() {
    const modal = document.getElementById('aiModal');
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
}

// Open ICM forecast modal
function openIcmModal(spotName, icmUrl) {
    const modal = document.getElementById('icmModal');
    const modalTitle = document.getElementById('icmModalTitle');
    const icmImage = document.getElementById('icmImage');

    modalTitle.textContent = `${spotName} - ICM Forecast`;
    icmImage.src = icmUrl;

    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

// Close ICM forecast modal
function closeIcmModal() {
    const modal = document.getElementById('icmModal');
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
}

const embedDropdownRegistry = [];
let embedDropdownsInitialized = false;

// Open embed widget modal
function openEmbedModal() {
    if (!currentSpotId) return;

    const modal = document.getElementById('embedModal');
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';

    const copyButton = document.getElementById('copyEmbedCode');
    const copyButtonText = document.getElementById('copyButtonText');
    if (copyButton) {
        copyButton.classList.remove('copied');
    }
    if (copyButtonText) {
        copyButtonText.textContent = t('embedCopyButtonDefault');
    }

    refreshEmbedDropdownTranslations();
    closeEmbedDropdowns();
    updateEmbedCode();
}

// Close embed widget modal
function closeEmbedModal() {
    const modal = document.getElementById('embedModal');
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
    closeEmbedDropdowns();
}

function getEmbedBaseUrl() {
    try {
        return window.location.origin.replace(/\/$/, '');
    } catch (e) {
        return 'https://varun.surf';
    }
}

function buildEmbedUrl(theme, view) {
    const baseUrl = getEmbedBaseUrl();
    return `${baseUrl}/embed?spotId=${currentSpotId}&theme=${theme}&view=${view}&lang=${embedLanguageSelection}`;
}

// Generate embed code based on configuration
function generateEmbedCode() {
    const embedUrl = buildEmbedUrl(embedThemeSelection, embedViewSelection);
    return `<iframe src="${embedUrl}" width="100%" height="500" frameborder="0" style="border-radius: 12px; max-width: 600px;"></iframe>`;
}

// Update embed code and preview
function updateEmbedCode() {
    if (!currentSpotId) {
        return;
    }

    const embedCode = generateEmbedCode();
    const embedCodeTextarea = document.getElementById('embedCode');
    const embedPreview = document.getElementById('embedPreview');

    if (embedCodeTextarea) {
        embedCodeTextarea.value = embedCode;
    }

    if (embedPreview) {
        embedPreview.src = buildEmbedUrl(embedThemeSelection, embedViewSelection);
    }
}

// Copy embed code to clipboard
function copyEmbedCode() {
    const embedCodeTextarea = document.getElementById('embedCode');
    const copyButton = document.getElementById('copyEmbedCode');
    const copyButtonText = document.getElementById('copyButtonText');

    if (!embedCodeTextarea || !copyButton) return;

    embedCodeTextarea.select();
    embedCodeTextarea.setSelectionRange(0, 99999); // For mobile devices

    navigator.clipboard.writeText(embedCodeTextarea.value)
        .then(() => {
            copyButton.classList.add('copied');
            if (copyButtonText) {
                copyButtonText.textContent = t('embedCopyButtonSuccess');
            }

            setTimeout(() => {
                copyButton.classList.remove('copied');
                if (copyButtonText) {
                    copyButtonText.textContent = t('embedCopyButtonDefault');
                }
            }, 2000);
        })
        .catch(err => {
            console.error('Failed to copy:', err);
            try {
                document.execCommand('copy');
                copyButton.classList.add('copied');
                if (copyButtonText) {
                    copyButtonText.textContent = t('embedCopyButtonSuccess');
                }

                setTimeout(() => {
                    copyButton.classList.remove('copied');
                    if (copyButtonText) {
                        copyButtonText.textContent = t('embedCopyButtonDefault');
                    }
                }, 2000);
            } catch (e) {
                alert(t('embedCopyButtonError'));
            }
        });
}

function updateEmbedDropdownSelectedState(menu, value) {
    menu.querySelectorAll('.dropdown-option').forEach(option => {
        option.classList.toggle('selected', option.dataset.value === value);
    });
}

function updateEmbedDropdownText(menuId, textId, value) {
    const menu = document.getElementById(menuId);
    const textElement = document.getElementById(textId);
    if (!menu || !textElement) {
        return;
    }
    const option = menu.querySelector(`.dropdown-option[data-value="${value}"]`);
    if (option) {
        const key = option.dataset.labelKey;
        textElement.textContent = key ? t(key) : option.textContent;
    }
}

function updateEmbedDropdownOptionsText(menuId) {
    const menu = document.getElementById(menuId);
    if (!menu) return;
    menu.querySelectorAll('.dropdown-option').forEach(option => {
        const key = option.dataset.labelKey;
        if (key) {
            option.textContent = t(key);
        }
    });
}

function refreshEmbedDropdownTranslations() {
    updateEmbedDropdownOptionsText('embedViewDropdownMenu');
    updateEmbedDropdownOptionsText('embedThemeDropdownMenu');
    updateEmbedDropdownText('embedViewDropdownMenu', 'embedViewDropdownText', embedViewSelection);
    updateEmbedDropdownText('embedThemeDropdownMenu', 'embedThemeDropdownText', embedThemeSelection);
}

function closeEmbedDropdowns() {
    embedDropdownRegistry.forEach(({button, menu}) => {
        button.classList.remove('open');
        button.setAttribute('aria-expanded', 'false');
        menu.classList.remove('open');
    });
}

function setupEmbedDropdowns() {
    if (embedDropdownsInitialized) {
        return;
    }

    embedDropdownsInitialized = true;

    const configs = [
        {
            buttonId: 'embedViewDropdown',
            menuId: 'embedViewDropdownMenu',
            textId: 'embedViewDropdownText',
            getSelection: () => embedViewSelection,
            setSelection: (value) => {
                embedViewSelection = value;
                updateEmbedCode();
            }
        },
        {
            buttonId: 'embedThemeDropdown',
            menuId: 'embedThemeDropdownMenu',
            textId: 'embedThemeDropdownText',
            getSelection: () => embedThemeSelection,
            setSelection: (value) => {
                embedThemeSelection = value;
                updateEmbedCode();
            }
        }
    ];

    configs.forEach(config => {
        const button = document.getElementById(config.buttonId);
        const menu = document.getElementById(config.menuId);
        const textElement = document.getElementById(config.textId);
        if (!button || !menu || !textElement) {
            return;
        }

        embedDropdownRegistry.push({button, menu});

        button.addEventListener('click', (event) => {
            event.stopPropagation();
            const isOpen = !button.classList.contains('open');
            closeEmbedDropdowns();
            if (isOpen) {
                button.classList.add('open');
                menu.classList.add('open');
                button.setAttribute('aria-expanded', 'true');
            } else {
                button.setAttribute('aria-expanded', 'false');
            }
        });

        menu.querySelectorAll('.dropdown-option').forEach(option => {
            option.addEventListener('click', (event) => {
                event.stopPropagation();
                const value = option.dataset.value;
                config.setSelection(value);
                updateEmbedDropdownSelectedState(menu, value);
                updateEmbedDropdownText(config.menuId, config.textId, value);
                closeEmbedDropdowns();
            });
        });

        updateEmbedDropdownSelectedState(menu, config.getSelection());
    });

    document.addEventListener('click', (event) => {
        const targetElement = event.target instanceof Element ? event.target : event.target?.parentElement;
        if (!targetElement || !targetElement.closest('.embed-dropdown')) {
            closeEmbedDropdowns();
        }
    });

    refreshEmbedDropdownTranslations();
}

// Setup embed modal event listeners
function setupEmbedModal() {
    const copyEmbedCodeButton = document.getElementById('copyEmbedCode');

    if (copyEmbedCodeButton) {
        copyEmbedCodeButton.addEventListener('click', copyEmbedCode);
    }

    setupEmbedDropdowns();
}

// Open app information modal
function openAppInfoModal() {
    const modal = document.getElementById('appInfoModal');
    if (!modal) return;

    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

// Close app information modal
function closeAppInfoModal() {
    const modal = document.getElementById('appInfoModal');
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
}

// ============================================================================
// FORECAST VIEW CREATION FUNCTIONS
// ============================================================================

// Check if a day has windy conditions (at least 12 knots base wind)
function hasWindyConditions(dayForecasts) {
    return dayForecasts.some(forecast => forecast.wind >= 12);
}

// Get filter windy days preference from localStorage
function getFilterWindyDaysPreference() {
    return localStorage.getItem('filterWindyDays') === 'true';
}

// Set filter windy days preference in localStorage
function setFilterWindyDaysPreference(enabled) {
    localStorage.setItem('filterWindyDays', enabled ? 'true' : 'false');
}

// Create a Windguru-style horizontal forecast view
function createWindguruView(forecastData, hasWaveData) {
    if (!forecastData || forecastData.length === 0) {
        return '';
    }

    // Group forecasts by day
    const groupedByDay = {};
    forecastData.forEach(forecast => {
        const dayKey = forecast.date ? forecast.date.split(' ').slice(0, 4).join(' ') : 'Unknown';
        if (!groupedByDay[dayKey]) {
            groupedByDay[dayKey] = [];
        }
        groupedByDay[dayKey].push(forecast);
    });

    const filterWindyDays = getFilterWindyDaysPreference();
    let dayColumnsHtml = '';
    let hasVisibleDays = false;

    // Collect day columns first to determine if we have any visible days
    Object.keys(groupedByDay).forEach(dayKey => {
        const dayForecasts = groupedByDay[dayKey];

        // Filter to only show daytime hours (06:00 to 21:00)
        const daytimeForecasts = dayForecasts.filter(forecast => {
            if (!forecast.date) return false;
            const time = forecast.date.split(' ')[4];
            if (!time) return false;
            const hour = parseInt(time.split(':')[0]);
            return hour >= 6 && hour <= 21;
        });

        // Skip this day if no daytime forecasts
        if (daytimeForecasts.length === 0) return;

        // Skip this day if filtering windy days and no windy conditions
        if (filterWindyDays && !hasWindyConditions(daytimeForecasts)) return;

        hasVisibleDays = true;

        const firstForecast = daytimeForecasts[0];
        const dayLabel = formatDayLabel(firstForecast.date);

        dayColumnsHtml += `<div class="windguru-day-column">`;
        dayColumnsHtml += `<div class="windguru-day-header">${dayLabel}</div>`;

        // Time row
        dayColumnsHtml += `<div class="windguru-data-row">`;
        daytimeForecasts.forEach(forecast => {
            const time = forecast.date ? forecast.date.split(' ')[4] : '';
            dayColumnsHtml += `<div class="windguru-cell windguru-time-cell">${time}</div>`;
        });
        dayColumnsHtml += `</div>`;

        // Wind speed row
        dayColumnsHtml += `<div class="windguru-data-row">`;
        daytimeForecasts.forEach(forecast => {
            let windClass = '';
            if (forecast.wind < 12) windClass = 'wind-weak';
            else if (forecast.wind >= 12 && forecast.wind <= 18) windClass = 'wind-moderate';
            else if (forecast.wind >= 19 && forecast.wind <= 25) windClass = 'wind-strong';
            else windClass = 'wind-extreme';

            dayColumnsHtml += `<div class="windguru-cell ${windClass}">${forecast.wind}</div>`;
        });
        dayColumnsHtml += `</div>`;

        // Gust speed row
        dayColumnsHtml += `<div class="windguru-data-row">`;
        daytimeForecasts.forEach(forecast => {
            let windClass = '';
            if (forecast.gusts < 12) windClass = 'wind-weak';
            else if (forecast.gusts >= 12 && forecast.gusts <= 18) windClass = 'wind-moderate';
            else if (forecast.gusts >= 19 && forecast.gusts <= 25) windClass = 'wind-strong';
            else windClass = 'wind-extreme';

            dayColumnsHtml += `<div class="windguru-cell ${windClass}">${forecast.gusts}</div>`;
        });
        dayColumnsHtml += `</div>`;

        // Direction row
        dayColumnsHtml += `<div class="windguru-data-row">`;
        daytimeForecasts.forEach(forecast => {
            const windArrow = getWindArrow(forecast.direction);
            dayColumnsHtml += `<div class="windguru-cell"><span class="wind-arrow" style="display: block;">${windArrow}</span><span style="font-size: 0.7rem;">${forecast.direction}</span></div>`;
        });
        dayColumnsHtml += `</div>`;

        // Temperature row
        dayColumnsHtml += `<div class="windguru-data-row">`;
        daytimeForecasts.forEach(forecast => {
            const tempClass = forecast.temp >= 18 ? 'temp-positive' : 'temp-negative';
            dayColumnsHtml += `<div class="windguru-cell ${tempClass}">${forecast.temp}°</div>`;
        });
        dayColumnsHtml += `</div>`;

        // Precipitation row
        dayColumnsHtml += `<div class="windguru-data-row">`;
        daytimeForecasts.forEach(forecast => {
            const precipClass = forecast.precipitation === 0 ? 'precipitation-none' : 'precipitation';
            dayColumnsHtml += `<div class="windguru-cell ${precipClass}">${forecast.precipitation}</div>`;
        });
        dayColumnsHtml += `</div>`;

        // Wave row (if applicable)
        if (hasWaveData) {
            dayColumnsHtml += `<div class="windguru-data-row">`;
            daytimeForecasts.forEach(forecast => {
                let waveClass = '';
                let waveText = '-';
                if (forecast.wave !== undefined) {
                    if (forecast.wave === 0) {
                        waveClass = 'wave-none';
                        waveText = '0m';
                    } else {
                        waveClass = forecast.wave > 1.5 ? 'wave-high' : 'wave-low';
                        waveText = `${forecast.wave}m`;
                    }
                }
                dayColumnsHtml += `<div class="windguru-cell ${waveClass}">${waveText}</div>`;
            });
            dayColumnsHtml += `</div>`;
        }

        dayColumnsHtml += `</div>`; // Close day column
    });

    // Build windguru HTML based on whether we have visible days
    let windguruHtml = '<div class="forecast-view windguru-view">';

    if (filterWindyDays && !hasVisibleDays) {
        // Show "No windy days!" message centered
        windguruHtml += `
            <div class="no-windy-days-message">
                <span class="no-windy-days-icon">💨</span>
                <p class="no-windy-days-text">${t('noWindyDaysMessage')}</p>
            </div>
        `;
    } else {
        // Show normal windguru layout with headers and data
        windguruHtml += '<div class="windguru-wrapper">';

        // Sticky labels column
        windguruHtml += '<div class="windguru-labels">';
        windguruHtml += '<div class="windguru-label-header"></div>'; // Empty header for alignment
        windguruHtml += `<div class="windguru-label">${t('timeLabel')}</div>`;
        windguruHtml += `<div class="windguru-label">${t('windHeader')}</div>`;
        windguruHtml += `<div class="windguru-label">${t('gustsHeader')}</div>`;
        windguruHtml += `<div class="windguru-label">${t('directionHeader')}</div>`;
        windguruHtml += `<div class="windguru-label">${t('tempHeader')}</div>`;
        windguruHtml += `<div class="windguru-label">${t('rainHeader')}</div>`;
        if (hasWaveData) {
            windguruHtml += `<div class="windguru-label">${t('waveHeader')}</div>`;
        }
        windguruHtml += '</div>';

        // Scrollable data container
        windguruHtml += '<div class="windguru-data-container">';
        windguruHtml += '<div class="windguru-data">';
        windguruHtml += dayColumnsHtml;
        windguruHtml += '</div>'; // Close windguru-data
        windguruHtml += '</div>'; // Close windguru-data-container
        windguruHtml += '</div>'; // Close windguru-wrapper
    }

    windguruHtml += '</div>'; // Close windguru-view
    return windguruHtml;
}

// ============================================================================
// FORECAST TABS SETUP
// ============================================================================

// Setup forecast view tabs (table vs. windguru)
function setupForecastTabs() {
    const tabs = document.querySelectorAll('.forecast-tab');
    if (tabs.length === 0) return;

    // Restore saved preference from localStorage
    const savedView = localStorage.getItem('forecastViewPreference') || 'table';

    // Set the initial view based on saved preference
    const tableView = document.querySelector('.table-view');
    const windguruView = document.querySelector('.windguru-view');

    tabs.forEach(t => {
        if (t.dataset.tab === savedView) {
            t.classList.add('active');
        } else {
            t.classList.remove('active');
        }
    });

    if (savedView === 'table') {
        if (tableView) tableView.classList.add('active');
        if (windguruView) windguruView.classList.remove('active');
    } else if (savedView === 'windguru') {
        if (tableView) tableView.classList.remove('active');
        if (windguruView) windguruView.classList.add('active');
    }

    // Add click listeners
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const targetView = tab.dataset.tab;

            // Save preference to localStorage
            localStorage.setItem('forecastViewPreference', targetView);

            // Update tab active state
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            // Update view visibility
            if (targetView === 'table') {
                if (tableView) tableView.classList.add('active');
                if (windguruView) windguruView.classList.remove('active');
            } else if (targetView === 'windguru') {
                if (tableView) tableView.classList.remove('active');
                if (windguruView) windguruView.classList.add('active');
            }
        });
    });

    // Setup drag-to-scroll for windguru view
    const windguruContainer = document.querySelector('.windguru-data-container');
    if (windguruContainer) {
        let isDown = false;
        let startX;
        let scrollLeft;

        windguruContainer.addEventListener('mousedown', (e) => {
            isDown = true;
            windguruContainer.classList.add('dragging');
            startX = e.pageX - windguruContainer.offsetLeft;
            scrollLeft = windguruContainer.scrollLeft;
        });

        windguruContainer.addEventListener('mouseleave', () => {
            isDown = false;
            windguruContainer.classList.remove('dragging');
        });

        windguruContainer.addEventListener('mouseup', () => {
            isDown = false;
            windguruContainer.classList.remove('dragging');
        });

        windguruContainer.addEventListener('mousemove', (e) => {
            if (!isDown) return;
            e.preventDefault();
            const x = e.pageX - windguruContainer.offsetLeft;
            const walk = (x - startX) * 2; // Scroll speed multiplier
            windguruContainer.scrollLeft = scrollLeft - walk;
        });
    }
}

// Setup media (map/photo) tabs in desktop spot view
function setupSpotMediaTabs() {
    const tabButtons = Array.from(document.querySelectorAll('.spot-media-tab'));
    if (tabButtons.length === 0) {
        return;
    }

    const mapPanel = document.querySelector('.spot-media-panel-map');
    const photoPanel = document.querySelector('.spot-media-panel-photo');
    if (!mapPanel || !photoPanel) {
        return;
    }

    tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            if (button.classList.contains('active')) {
                return;
            }

            const targetMedia = button.dataset.media;
            tabButtons.forEach(tab => tab.classList.toggle('active', tab === button));
            mapPanel.classList.toggle('active', targetMedia === 'map');
            photoPanel.classList.toggle('active', targetMedia === 'photo');
        });
    });
}

// ============================================================================
// SPOT CARD CREATION
// ============================================================================

// Create a detailed spot card HTML with all forecast views
function createSpotCard(spot) {
    const countryFlag = getCountryFlag(spot.country);

    // Use forecastHourly if available, otherwise fall back to forecast
    const forecastData = (spot.forecastHourly && spot.forecastHourly.length > 0)
        ? spot.forecastHourly
        : (spot.forecast || []);

    // Check if any forecast has wave data
    const hasWaveData = forecastData && forecastData.some(day => day.wave !== undefined);

    // Determine layout (desktop vs mobile)
    const isDesktopView = window.matchMedia('(min-width: 769px)').matches;
    const spotPhotoUrl = typeof spot.spotPhotoUrl === 'string' ? spot.spotPhotoUrl.trim() : '';
    const hasSpotPhoto = isDesktopView && spotPhotoUrl.length > 0;

    // ========================================================================
    // BUILD CURRENT CONDITIONS ROW (for table)
    // ========================================================================

    let currentConditionsRow = '';
    if (spot.currentConditions && spot.currentConditions.wind !== undefined) {
        let windClass, windTextClass;
        const avgWind = (spot.currentConditions.wind + spot.currentConditions.gusts) / 2;
        if (avgWind < 12) {
            windClass = 'weak-wind';
            windTextClass = 'wind-weak';
        } else if (avgWind >= 12 && avgWind < 18) {
            windClass = 'moderate-wind';
            windTextClass = 'wind-moderate';
        } else if (avgWind >= 18 && avgWind <= 22) {
            windClass = 'strong-wind';
            windTextClass = 'wind-strong';
        } else {
            windClass = 'extreme-wind';
            windTextClass = 'wind-extreme';
        }

        const tempClass = spot.currentConditions.temp >= 20 ? 'temp-positive' : 'temp-negative';
        const windArrow = getWindArrow(spot.currentConditions.direction);

        // Current wave conditions
        let currentWaveClass = '';
        let currentWaveText = '-';
        if (spot.currentConditions.wave !== undefined) {
            if (spot.currentConditions.wave === 0) {
                currentWaveClass = 'wave-none';
                currentWaveText = '0m';
            } else {
                currentWaveClass = spot.currentConditions.wave > 1.5 ? 'wave-high' : 'wave-low';
                currentWaveText = `${spot.currentConditions.wave}m`;
            }
        }

        currentConditionsRow = `
                    <tr class="${windClass}" style="border-bottom: 2px solid #404040;">
                        <td>
                            <div class="live-indicator">
                                <strong class="live-text">${t('nowLabel')}</strong>
                                <div class="live-dot"></div>
                            </div>
                        </td>
                        <td class="${windTextClass}">${spot.currentConditions.wind} kts</td>
                        <td class="${windTextClass}">${spot.currentConditions.gusts} kts</td>
                        <td class="${windTextClass}">
                            <span class="wind-arrow">${windArrow}</span> ${spot.currentConditions.direction}
                        </td>
                        <td class="${tempClass}">${spot.currentConditions.temp}°C</td>
                        <td>-</td>
                        ${hasWaveData ? `<td class="${currentWaveClass}">${currentWaveText}</td>` : ''}
                    </tr>
                `;
    }

    // ========================================================================
    // BUILD FORECAST ROWS (for table)
    // ========================================================================

    let forecastRows = '';
    if (forecastData && forecastData.length > 0) {
        let previousDay = null;
        let dayColorIndex = 0;

        // Filter to only show daytime hours (06:00 to 21:00)
        let daytimeForecasts = forecastData.filter(day => {
            if (!day.date) return false;
            const time = day.date.split(' ')[4];
            if (!time) return false;
            const hour = parseInt(time.split(':')[0]);
            return hour >= 6 && hour <= 21;
        });

        // Apply windy days filter if enabled
        const filterWindyDays = getFilterWindyDaysPreference();
        if (filterWindyDays) {
            // Group by day and filter days without windy conditions
            const groupedByDay = {};
            daytimeForecasts.forEach(forecast => {
                const dayKey = forecast.date ? forecast.date.split(' ').slice(0, 4).join(' ') : 'Unknown';
                if (!groupedByDay[dayKey]) {
                    groupedByDay[dayKey] = [];
                }
                groupedByDay[dayKey].push(forecast);
            });

            // Filter out days without windy conditions
            daytimeForecasts = [];
            Object.keys(groupedByDay).forEach(dayKey => {
                const dayForecasts = groupedByDay[dayKey];
                if (hasWindyConditions(dayForecasts)) {
                    daytimeForecasts.push(...dayForecasts);
                }
            });
        }

        daytimeForecasts.forEach(day => {
            let windClass = '';
            let windTextClass = '';

            if (day.wind < 12) {
                windClass = 'weak-wind';
                windTextClass = 'wind-weak';
            } else if (day.wind >= 12 && day.wind <= 18) {
                windClass = 'moderate-wind';
                windTextClass = 'wind-moderate';
            } else if (day.wind >= 19 && day.wind <= 25) {
                windClass = 'strong-wind';
                windTextClass = 'wind-strong';
            } else {
                windClass = 'extreme-wind';
                windTextClass = 'wind-extreme';
            }

            const tempClass = day.temp >= 18 ? 'temp-positive' : 'temp-negative';
            const windArrow = getWindArrow(day.direction);
            const precipClass = day.precipitation === 0 ? 'precipitation-none' : 'precipitation';

            // Wave classes
            let waveClass = '';
            let waveText = '-';
            if (day.wave !== undefined) {
                if (day.wave === 0) {
                    waveClass = 'wave-none';
                    waveText = '0m';
                } else {
                    waveClass = day.wave > 1.5 ? 'wave-high' : 'wave-low';
                    waveText = `${day.wave}m`;
                }
            }

            // Detect day changes for alternating border colors
            const currentDay = day.date ? day.date.split(' ')[0] : null;
            const isDayChange = previousDay && currentDay && previousDay !== currentDay;

            if (isDayChange) {
                dayColorIndex = (dayColorIndex + 1) % 2;
            }

            const borderColor = dayColorIndex === 0 ? 'rgba(74, 158, 255, 0.4)' : 'rgba(255, 158, 74, 0.4)';
            const combinedStyle = `border-left: 3px solid ${borderColor};`;

            previousDay = currentDay;

            forecastRows += `
                        <tr class="${windClass}" style="${combinedStyle}">
                            <td><strong>${formatForecastDateLabel(day.date)}</strong></td>
                            <td class="${windTextClass}">${day.wind} kts</td>
                            <td class="${windTextClass}">${day.gusts} kts</td>
                            <td class="${windTextClass}">
                                <span class="wind-arrow">${windArrow}</span> ${day.direction}
                            </td>
                            <td class="${tempClass}">${day.temp}°C</td>
                            <td class="${precipClass}">${day.precipitation} mm</td>
                            ${hasWaveData ? `<td class="${waveClass}">${waveText}</td>` : ''}
                        </tr>
                    `;
        });
    }

    // ========================================================================
    // BUILD CURRENT CONDITIONS CARD (sidebar)
    // ========================================================================

    let currentConditionsCardHtml = '';
    let conditionsData = null;
    let conditionsLabel = '';

    // Check if we have current conditions
    if (spot.currentConditions && spot.currentConditions.wind !== undefined) {
        conditionsData = {
            wind: spot.currentConditions.wind,
            gusts: spot.currentConditions.gusts,
            direction: spot.currentConditions.direction,
            temp: spot.currentConditions.temp,
            precipitation: 0, // Current conditions don't have precipitation
            isCurrent: true
        };
        conditionsLabel = t('nowLabel');
    } else if (forecastData && forecastData.length > 0) {
        // Use the forecast closest to the current time
        const nearestForecast = findClosestForecast(forecastData);
        if (nearestForecast) {
            conditionsData = {
                wind: nearestForecast.wind,
                gusts: nearestForecast.gusts,
                direction: nearestForecast.direction,
                temp: nearestForecast.temp,
                precipitation: nearestForecast.precipitation || 0,
                isCurrent: false
            };
            conditionsLabel = formatForecastDateLabel(nearestForecast.date);
        }
    }

    if (conditionsData) {
        const windArrow = getWindArrow(conditionsData.direction);
        const avgWind = (conditionsData.wind + conditionsData.gusts) / 2;

        // Determine wind quality class
        let windQualityClass = '';
        if (avgWind < 12) {
            windQualityClass = 'wind-weak';
        } else if (avgWind >= 12 && avgWind < 18) {
            windQualityClass = 'wind-moderate';
        } else if (avgWind >= 18 && avgWind <= 25) {
            windQualityClass = 'wind-strong';
        } else {
            windQualityClass = 'wind-extreme';
        }

        currentConditionsCardHtml = `
                <div class="current-conditions-card">
                    <div class="conditions-header">
                        <div class="conditions-label">${conditionsLabel}</div>
                        ${conditionsData.isCurrent ? '<div class="live-dot"></div>' : ''}
                    </div>
                    <div class="conditions-main">
                        <div class="wind-arrow-large ${windQualityClass}" style="transform: rotate(${getWindRotation(conditionsData.direction)}deg);">
                            ↓
                        </div>
                        <div class="wind-details">
                            <div class="wind-speed ${windQualityClass}">${conditionsData.wind} kts</div>
                            <div class="wind-label">${t('windLabel')}</div>
                        </div>
                    </div>
                    <div class="conditions-grid">
                        <div class="condition-item">
                            <div class="condition-label">${t('gustsLabel')}</div>
                            <div class="condition-value ${windQualityClass}">${conditionsData.gusts} kts</div>
                        </div>
                        <div class="condition-item">
                            <div class="condition-label">${t('directionLabel')}</div>
                            <div class="condition-value">${conditionsData.direction}</div>
                        </div>
                        <div class="condition-item">
                            <div class="condition-label">${t('temperatureLabel')}</div>
                            <div class="condition-value ${conditionsData.temp >= 18 ? 'temp-positive' : 'temp-negative'}">${conditionsData.temp}°C</div>
                        </div>
                        <div class="condition-item">
                            <div class="condition-label">${t('precipitationLabel')}</div>
                            <div class="condition-value ${conditionsData.precipitation === 0 ? 'precipitation-none' : 'precipitation'}">${conditionsData.precipitation} mm</div>
                        </div>
                    </div>
                </div>
            `;
    }

    // ========================================================================
    // BUILD SPOT INFO CARD (sidebar)
    // ========================================================================

    let spotInfoCardHtml = '';
    if (spot.spotInfo) {
        const info = getSpotInfo(spot);
        const coordinatesHtml = spot.coordinates
            ? `<div class="info-item" style="grid-column: 1 / -1;">
                   <div class="info-label">${t('coordinatesLabel')}</div>
                   <div class="info-value">${spot.coordinates.lat.toFixed(6)}, ${spot.coordinates.lon.toFixed(6)}</div>
               </div>`
            : '';
        spotInfoCardHtml = `
                <div class="spot-info-card">
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
                        ${coordinatesHtml}
                    </div>
                </div>
            `;
    }

    // ========================================================================
    // BUILD EMBEDDED MAP (desktop only)
    // ========================================================================

    const hasEmbeddedMap = isDesktopView && spot.embeddedMap && spot.embeddedMap.trim().length > 0;

    let embeddedMapHtml = '';
    if (hasEmbeddedMap && hasSpotPhoto) {
        embeddedMapHtml = `
                <div class="spot-media-switcher">
                    <div class="spot-media-tabs">
                        <button type="button" class="spot-media-tab active" data-media="map">
                            ${MAP_TAB_ICON}
                            <span>${t('mapTabLabel')}</span>
                        </button>
                        <button type="button" class="spot-media-tab" data-media="photo">
                            ${PHOTO_TAB_ICON}
                            <span>${t('photoTabLabel')}</span>
                        </button>
                    </div>
                    <div class="spot-media-panels">
                        <div class="spot-media-panel spot-media-panel-map active">
                            <div class="spot-embedded-map">
                                <div class="spot-embedded-map-frame">${spot.embeddedMap}</div>
                            </div>
                        </div>
                        <div class="spot-media-panel spot-media-panel-photo">
                            <div class="spot-photo-frame">
                                <img src="${spotPhotoUrl}" alt="${spot.name} photo" loading="lazy" />
                            </div>
                        </div>
                    </div>
                </div>
            `;
    } else if (hasEmbeddedMap) {
        embeddedMapHtml = `
                <div class="spot-embedded-map">
                    <div class="spot-embedded-map-frame">${spot.embeddedMap}</div>
                </div>
            `;
    }

    // ========================================================================
    // BUILD AI ANALYSIS CARD (sidebar)
    // ========================================================================

    const aiAnalysisText = getAiAnalysisForCurrentLanguage(spot);

    let aiAnalysisCardHtml = '';
    if (aiAnalysisText) {
        aiAnalysisCardHtml = `
                <div class="ai-analysis-card">
                    <div class="ai-analysis">
                        <p>${aiAnalysisText}</p>
                    </div>
                    <div class="modal-disclaimer">${t('aiDisclaimer')}</div>
                </div>
            `;
    }

    // ========================================================================
    // BUILD SPONSORS CARD (sidebar)
    // ========================================================================

    let sponsorsCardHtml = '';
    if (spot.sponsors && spot.sponsors.length > 0) {
        let sponsorsListHtml = '';
        for (const sponsor of spot.sponsors) {
            sponsorsListHtml += `
                <div class="sponsor-item">
                    <a href="${sponsor.link}" target="_blank" rel="noopener noreferrer" class="sponsor-link">
                        ${sponsor.name}
                    </a>
                </div>
            `;
        }
        sponsorsCardHtml = `
                <div class="sponsors-card">
                    <div class="sponsors-list">
                        ${sponsorsListHtml}
                    </div>
                </div>
            `;
    }

    // ========================================================================
    // ASSEMBLE COMPLETE SPOT CARD
    // ========================================================================

    return `
            <div class="spot-card" style="width: 100%;">
                <div class="spot-header" style="margin-left: 0;">
                    <div class="spot-title">
                        <h2 class="spot-name spot-name-single">${spot.name}</h2>
                        <div class="last-updated">${spot.lastUpdated || t('noData')}</div>
                    </div>
                    <div class="spot-meta">
                        <span class="country-tag">${countryFlag} ${t(spot.country.replace(/\s+/g, ''))}</span>
                    </div>
                </div>
                <div class="external-links">
                    ${spot.spotInfo ? `<span class="external-link info-link" onclick="openInfoModal('${spot.name}')"></span>` : ''}
                    ${spot.windguruUrl ? `<a href="${spot.windguruUrl}" target="_blank" class="external-link">WG</a>` : ''}
                    ${spot.windfinderUrl ? `<a href="${spot.windfinderUrl}" target="_blank" class="external-link">WF</a>` : ''}
                    ${spot.icmUrl ? `<span class="external-link" onclick="openIcmModal('${spot.name}', '${spot.icmUrl}')">ICM</span>` : ''}
                    ${spot.webcamUrl ? `<a href="${spot.webcamUrl}" target="_blank" class="external-link webcam-link">CAM</a>` : ''}
                    ${spot.locationUrl ? `<a href="${spot.locationUrl}" target="_blank" class="external-link location-link">${t('mapLinkLabel')}</a>` : ''}
                    <span class="external-link embed-link" onclick="openEmbedModal()">${t('embedLinkLabel')}</span>
                    ${aiAnalysisText ? `<span class="external-link ai-link" onclick="openAIModal('${spot.name}')">AI</span>` : ''}
                </div>

                <div class="spot-detail-container">
                    <div class="spot-detail-left">
                        ${currentConditionsCardHtml}
                        ${spotInfoCardHtml}
                        ${aiAnalysisCardHtml}
                        ${sponsorsCardHtml}
                    </div>
                    <div class="spot-detail-right">
                        ${embeddedMapHtml}
                        ${isDesktopView ? `
                        <div class="forecast-tabs-container">
                            <div class="forecast-tabs">
                                <button class="forecast-tab active" data-tab="table">
                                    <svg class="tab-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <path d="M11,22H5c-2.757,0-5-2.243-5-5V9H11v13ZM24,7c0-2.757-2.243-5-5-5H5C2.243,2,0,4.243,0,7H24Zm-11,2v13h6c2.757,0,5-2.243,5-5V9H13Z" fill="currentColor"/>
                                    </svg>
                                    ${t('tableViewLabel')}
                                </button>
                                <button class="forecast-tab" data-tab="windguru">
                                    <svg class="tab-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <path d="M5,22c-2.757,0-5-2.243-5-5V7C0,4.243,2.243,2,5,2V22Zm2-11H24V7c0-2.757-2.243-5-5-5H7V11Zm0,2v9h12c2.757,0,5-2.243,5-5v-4H7Z" fill="currentColor"/>
                                    </svg>
                                    ${t('windguruViewLabel')}
                                </button>
                            </div>
                            <div class="filter-windy-days-container">
                                <label class="filter-windy-days-label">
                                    <input type="checkbox" id="filterWindyDaysCheckbox" ${getFilterWindyDaysPreference() ? 'checked' : ''} />
                                    <span class="filter-text">${t('filterWindyDaysLabel')}</span>
                                </label>
                            </div>
                        </div>
                        ` : ''}
                        <div class="forecast-view-container">
                            <div class="forecast-view table-view active">
                                ${getFilterWindyDaysPreference() && forecastRows === '' ? `
                                    <div class="no-windy-days-message">
                                        <span class="no-windy-days-icon">💨</span>
                                        <p class="no-windy-days-text">${t('noWindyDaysMessage')}</p>
                                    </div>
                                ` : `
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
                                `}
                            </div>
                            ${isDesktopView ? createWindguruView(forecastData, hasWaveData) : ''}
                        </div>
                    </div>
                </div>
            </div>
        `;
}

// ============================================================================
// DISPLAY FUNCTIONS
// ============================================================================

// Display spot card in the UI
function displaySpot(spot) {
    const spotContainer = document.getElementById('spotContainer');
    const loadingMessage = document.getElementById('loadingMessage');
    const errorMessage = document.getElementById('errorMessage');

    clearForecastPolling();

    if (loadingMessage) {
        loadingMessage.style.display = 'none';
    }

    if (errorMessage) {
        errorMessage.style.display = 'none';
    }

    const previousSpot = currentSpot;
    currentErrorKey = null;
    currentErrorText = '';
    currentLoadingKey = null;

    // Preserve embedded map iframe to avoid reload
    let preservedMapNode = null;
    const shouldPreserveMap = previousSpot
        && previousSpot.embeddedMap
        && spot.embeddedMap
        && previousSpot.embeddedMap === spot.embeddedMap;

    if (shouldPreserveMap) {
        const currentMapFrame = document.querySelector('.spot-embedded-map-frame');
        if (currentMapFrame && currentMapFrame.firstElementChild) {
            preservedMapNode = currentMapFrame.firstElementChild;
        }
    }

    if (spot) {
        spotContainer.innerHTML = createSpotCard(spot);

        // Restore preserved map iframe
        if (shouldPreserveMap && preservedMapNode) {
            const newMapFrame = document.querySelector('.spot-embedded-map-frame');
            if (newMapFrame) {
                newMapFrame.innerHTML = '';
                newMapFrame.appendChild(preservedMapNode);
            }
        }

        document.title = `${spot.name} - VARUN.SURF`;

        // Setup forecast tabs after spot card is rendered
        setupForecastTabs();

        // Setup filter windy days checkbox
        setupFilterWindyDaysCheckbox();

        // Setup media tabs if available
        setupSpotMediaTabs();
    }

    currentSpot = spot;

    if (currentSpotId) {
        startBackgroundRefresh(currentSpotId);
    }
}

// Setup filter windy days checkbox
function setupFilterWindyDaysCheckbox() {
    const checkbox = document.getElementById('filterWindyDaysCheckbox');
    if (!checkbox) return;

    checkbox.addEventListener('change', (e) => {
        const enabled = e.target.checked;
        setFilterWindyDaysPreference(enabled);

        // Re-render the spot to apply the filter
        if (currentSpot) {
            displaySpot(currentSpot);
        }
    });
}

// Display error message
function displayError(messageKey) {
    const loadingMessage = document.getElementById('loadingMessage');
    const errorMessage = document.getElementById('errorMessage');
    const errorTitle = document.getElementById('errorTitle');
    const errorDescription = document.getElementById('errorDescription');

    clearForecastPolling();
    clearBackgroundRefresh();

    if (loadingMessage) {
        loadingMessage.style.display = 'none';
    }
    errorMessage.style.display = 'flex';

    const hasTranslation = typeof messageKey === 'string' && (
        (translations.en && Object.prototype.hasOwnProperty.call(translations.en, messageKey)) ||
        (translations.pl && Object.prototype.hasOwnProperty.call(translations.pl, messageKey))
    );

    if (hasTranslation) {
        currentErrorKey = messageKey;
        currentErrorText = t(messageKey);
    } else {
        currentErrorKey = null;
        currentErrorText = typeof messageKey === 'string' ? messageKey : '';
    }

    errorTitle.textContent = t('error');
    errorDescription.textContent = currentErrorText;
    currentLoadingKey = null;
}

// ============================================================================
// THEME MANAGEMENT
// ============================================================================

function initTheme() {
    const savedTheme = localStorage.getItem('theme') || 'dark';
    const themeToggle = document.getElementById('themeToggle');
    const themeIcon = document.getElementById('themeIcon');

    function updateTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        if (theme === 'light') {
            themeIcon.innerHTML = '<path d="M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5,5-2.24,5-5-2.24-5-5-5Zm0,7c-1.1,0-2-.9-2-2s.9-2,2-2,2,.9,2,2-.9,2-2,2Zm4.95-6.95c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0,.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44ZM7.05,16.95c.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0ZM3.51,5.64c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41Zm16.97,12.73c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41Zm3.51-6.36c0,.83-.67,1.5-1.5,1.5h-2c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5h2c.83,0,1.5,.67,1.5,1.5ZM3.5,13.5H1.5c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5H3.5c.83,0,1.5,.67,1.5,1.5s-.67,1.5-1.5,1.5ZM10.5,3.5V1.5c0-.83,.67-1.5,1.5-1.5s1.5,.67,1.5,1.5V3.5c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5Zm3,17v2c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5v-2c0-.83,.67-1.5-1.5-1.5s1.5,.67,1.5,1.5Z"/>';
        } else {
            themeIcon.innerHTML = '<path d="M15,24a12.021,12.021,0,0,1-8.914-3.966,11.9,11.9,0,0,1-3.02-9.309A12.122,12.122,0,0,1,13.085.152a13.061,13.061,0,0,1,5.031.205,2.5,2.5,0,0,1,1.108,4.226c-4.56,4.166-4.164,10.644.807,14.41a2.5,2.5,0,0,1-.7,4.32A13.894,13.894,0,0,1,15,24Z"/>';
        }
        localStorage.setItem('theme', theme);
    }

    // Set the initial theme
    updateTheme(savedTheme);

    // Theme toggle event
    if (themeToggle) {
        themeToggle.addEventListener('click', () => {
            const currentTheme = document.documentElement.getAttribute('data-theme');
            const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
            updateTheme(newTheme);
        });
    }
}

// ============================================================================
// LANGUAGE/INTERNATIONALIZATION MANAGEMENT
// ============================================================================

// Update all UI translations
function updateUITranslations() {
    // Update app info modal
    const appInfoModalTitle = document.getElementById('appInfoModalTitle');
    if (appInfoModalTitle) {
        appInfoModalTitle.textContent = t('appInfoModalTitle');
    }

    const embedModalTitle = document.getElementById('embedModalTitle');
    if (embedModalTitle) {
        embedModalTitle.textContent = t('embedModalTitle');
    }

    const embedDescription = document.getElementById('embedDescription');
    if (embedDescription) {
        embedDescription.textContent = t('embedModalDescription');
    }

    refreshEmbedDropdownTranslations();

    const embedPreviewTitle = document.getElementById('embedPreviewTitle');
    if (embedPreviewTitle) {
        embedPreviewTitle.textContent = t('embedPreviewTitle');
    }

    const embedCodeTitle = document.getElementById('embedCodeTitle');
    if (embedCodeTitle) {
        embedCodeTitle.textContent = t('embedCodeTitle');
    }

    const copyButtonText = document.getElementById('copyButtonText');
    if (copyButtonText) {
        copyButtonText.textContent = t('embedCopyButtonDefault');
    }

    const aiModalDisclaimer = document.getElementById('aiModalDisclaimer');
    if (aiModalDisclaimer) {
        aiModalDisclaimer.textContent = t('aiDisclaimer');
    }

    const aiModalTitle = document.getElementById('aiModalTitle');
    if (aiModalTitle && currentSpot) {
        aiModalTitle.textContent = `${currentSpot.name} - ${t('aiAnalysisTitle')}`;
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

    // Update info toggle button label
    const infoToggleLabel = document.getElementById('infoToggleLabel');
    if (infoToggleLabel) {
        infoToggleLabel.textContent = t('infoButtonLabel');
    }

    // Update loading text
    const loadingMessage = document.getElementById('loadingMessage');
    if (loadingMessage && loadingMessage.style.display !== 'none') {
        const loadingTextEl = document.getElementById('loadingText');
        if (loadingTextEl) {
            const key = currentLoadingKey || 'loadingSpotData';
            loadingTextEl.textContent = t(key);
        }
    }

    // Update error message
    const errorMessage = document.getElementById('errorMessage');
    if (errorMessage && errorMessage.style.display !== 'none') {
        const errorTitle = document.getElementById('errorTitle');
        const errorDescription = document.getElementById('errorDescription');

        if (errorTitle) {
            errorTitle.textContent = t('error');
        }

        if (errorDescription) {
            if (currentErrorKey) {
                currentErrorText = t(currentErrorKey);
            }
            errorDescription.textContent = currentErrorText;
        }
    }
}

// Initialize language and setup toggle
function initLanguage() {
    const savedLang = localStorage.getItem('language') || 'en';
    currentLanguage = savedLang;
    embedLanguageSelection = savedLang;

    const languageToggle = document.getElementById('languageToggle');
    const langCode = document.getElementById('langCode');

    // Update UI translations on init
    updateUITranslations();

    if (languageToggle && langCode) {
        langCode.textContent = savedLang.toUpperCase();

        languageToggle.addEventListener('click', () => {
            // Toggle between EN and PL only
            const newLang = currentLanguage === 'en' ? 'pl' : 'en';

            currentLanguage = newLang;
            embedLanguageSelection = newLang;
            langCode.textContent = newLang.toUpperCase();
            localStorage.setItem('language', newLang);

            // Update UI translations
            updateUITranslations();

            // Reload the spot to apply translations
            if (currentSpot) {
                displaySpot(currentSpot);
            }

            updateEmbedCode();
        });
    }
}

// ============================================================================
// MODAL SETUP
// ============================================================================

function setupModals() {
    const infoModalClose = document.getElementById('infoModalClose');
    const aiModalClose = document.getElementById('aiModalClose');
    const icmModalClose = document.getElementById('icmModalClose');
    const appInfoModalClose = document.getElementById('appInfoModalClose');
    const embedModalClose = document.getElementById('embedModalClose');

    if (infoModalClose) {
        infoModalClose.addEventListener('click', closeInfoModal);
    }

    if (aiModalClose) {
        aiModalClose.addEventListener('click', closeAIModal);
    }

    if (icmModalClose) {
        icmModalClose.addEventListener('click', closeIcmModal);
    }

    if (appInfoModalClose) {
        appInfoModalClose.addEventListener('click', closeAppInfoModal);
    }

    if (embedModalClose) {
        embedModalClose.addEventListener('click', closeEmbedModal);
    }

    // Close modals on the overlay click
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                closeInfoModal();
                closeAIModal();
                closeIcmModal();
                closeAppInfoModal();
                closeEmbedModal();
            }
        });
    });
}

// ============================================================================
// UI SETUP FUNCTIONS
// ============================================================================

// Setup info toggle button
function setupInfoToggle() {
    const infoToggle = document.getElementById('infoToggle');
    if (infoToggle) {
        infoToggle.addEventListener('click', () => {
            openAppInfoModal();
        });
    }
}

// Setup mobile hamburger menu
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
// HEADER NAVIGATION
// ============================================================================

// Normalize country name for URL
function normalizeCountryForUrl(country) {
    return country.toLowerCase()
        .replace(/\s+/g, '')
        .replace(/[^a-z]/g, '');
}

// Setup header navigation (logo and title click)
function setupHeaderNavigation() {
    const headerLogo = document.getElementById('headerLogo');
    const headerTitle = document.getElementById('headerTitle');

    function navigateToHome() {
        const savedCountry = localStorage.getItem('selectedCountry') || 'all';
        if (savedCountry === 'all') {
            window.location.href = '/';
        } else {
            const normalizedCountry = normalizeCountryForUrl(savedCountry);
            window.location.href = `/country/${normalizedCountry}`;
        }
    }

    if (headerLogo) {
        headerLogo.addEventListener('click', navigateToHome);
    }

    if (headerTitle) {
        headerTitle.addEventListener('click', navigateToHome);
    }
}

// ============================================================================
// WINDOW RESIZE HANDLER
// ============================================================================

// Re-render spot when crossing a mobile/desktop threshold
function setupResizeHandler() {
    let resizeTimeout;
    let wasMobile = window.innerWidth <= 768;
    let wasNarrow = window.innerWidth <= 1430;

    window.addEventListener('resize', () => {
        clearTimeout(resizeTimeout);
        resizeTimeout = setTimeout(() => {
            const isMobile = window.innerWidth <= 768;
            const isNarrow = window.innerWidth <= 1430;

            // Check if we crossed the mobile/desktop threshold
            const crossedMobileThreshold = isMobile !== wasMobile;

            // Check if we crossed the 1430px threshold (for forecast tabs visibility)
            const crossedNarrowThreshold = isNarrow !== wasNarrow;

            if (crossedMobileThreshold || crossedNarrowThreshold) {
                wasMobile = isMobile;
                wasNarrow = isNarrow;
                if (currentSpot) {
                    displaySpot(currentSpot);
                }
            }
        }, 150); // Debounce resize events
    });
}

// ============================================================================
// MODEL DROPDOWN (GFS vs IFS)
// ============================================================================

// Setup forecast model dropdown
function setupModelDropdown() {
    const modelDropdown = document.getElementById('modelDropdown');
    const modelDropdownMenu = document.getElementById('modelDropdownMenu');
    const modelDropdownText = document.getElementById('modelDropdownText');

    if (!modelDropdown || !modelDropdownMenu || !modelDropdownText) {
        return;
    }

    // Initialize with the current model
    const currentModel = getSelectedModel();
    modelDropdownText.textContent = currentModel.toUpperCase();

    // Set up dropdown options
    const options = document.querySelectorAll('#modelDropdownMenu .dropdown-option');
    options.forEach(option => {
        // Mark current option as selected
        if (option.dataset.value === currentModel) {
            option.classList.add('selected');
        }

        // Add click handler
        option.addEventListener('click', () => {
            const newModel = option.dataset.value;
            setSelectedModel(newModel);
            modelDropdownText.textContent = newModel.toUpperCase();

            // Update the selected state
            options.forEach(opt => opt.classList.remove('selected'));
            option.classList.add('selected');

            // Close dropdown
            modelDropdownMenu.classList.remove('open');
            modelDropdown.classList.remove('open');

            // Reload data with a new model
            if (currentSpotId) {
                setLoadingMessage('loadingSpotData');
                fetchSpotData(currentSpotId)
                    .then(spot => {
                        if (hasForecastData(spot)) {
                            displaySpot(spot);
                        } else {
                            setLoadingMessage('loadingForecast');
                            startForecastPolling(currentSpotId);
                        }
                    })
                    .catch(error => {
                        if (error && error.status === 404) {
                            displayError('spotNotFound');
                        } else {
                            displayError('errorLoadingSpot');
                        }
                    });
            }
        });
    });

    // Toggle dropdown on the button click
    modelDropdown.addEventListener('click', () => {
        modelDropdownMenu.classList.toggle('open');
        modelDropdown.classList.toggle('open');
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (!modelDropdown.contains(e.target) && !modelDropdownMenu.contains(e.target)) {
            modelDropdownMenu.classList.remove('open');
            modelDropdown.classList.remove('open');
        }
    });
}

// ============================================================================
// SPONSORS FUNCTIONALITY
// ============================================================================

// Render spot sponsors from spot data
function renderSpotSponsors(spot) {
    const sponsorsContainer = document.getElementById('sponsorsContainer');
    if (!sponsorsContainer) {
        return;
    }

    if (!spot.sponsors || spot.sponsors.length === 0) {
        sponsorsContainer.innerHTML = '';
        return;
    }

    let sponsorsHTML = '<div class="sponsors-container"><div class="sponsors-list">';

    for (const sponsor of spot.sponsors) {
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
// MAIN INITIALIZATION
// ============================================================================

async function setupSpot() {
    const spotId = getSpotIdFromUrl();
    currentSpotId = spotId;

    if (!spotId) {
        displayError('invalidSpotId');
        return;
    }

    setLoadingMessage('loadingSpotData');

    // Wait at least 2 seconds before loading spot data
    const startTime = Date.now();
    const minDelay = 2000;

    try {
        const spot = await fetchSpotData(spotId);
        const elapsed = Date.now() - startTime;
        const remainingDelay = minDelay - elapsed;

        if (remainingDelay > 0) {
            await new Promise(resolve => setTimeout(resolve, remainingDelay));
        }

        if (hasForecastData(spot)) {
            displaySpot(spot);
            // Render sponsors from spot data
            renderSpotSponsors(spot);
        } else {
            setLoadingMessage('loadingForecast');
            startForecastPolling(spotId);
        }
    } catch (error) {
        if (error && error.status === 404) {
            displayError('spotNotFound');
        } else {
            displayError('errorLoadingSpot');
        }
    }
}

// ============================================================================
// GLOBAL WINDOW FUNCTIONS (for onclick handlers)
// ============================================================================

// Make functions global for onclick handlers
window.openAIModal = openAIModal;
window.closeAIModal = closeAIModal;
window.openInfoModal = openInfoModal;
window.closeInfoModal = closeInfoModal;
window.openIcmModal = openIcmModal;
window.closeIcmModal = closeIcmModal;
window.openEmbedModal = openEmbedModal;
window.closeEmbedModal = closeEmbedModal;

// ============================================================================
// START APPLICATION
// ============================================================================

document.addEventListener('DOMContentLoaded', async function () {
    initTheme();
    initLanguage();
    setupModals();
    setupEmbedModal();
    setupInfoToggle();
    setupHamburgerMenu();
    setupHeaderNavigation();
    setupResizeHandler();
    setupModelDropdown();
    setupSpot();
});
