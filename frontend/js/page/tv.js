import {getCountryFlag} from '../common/country-flags.js';
import {translations} from '../common/translations.js';

// ============================================================================
// GLOBAL STATE
// ============================================================================

let currentSpot = null;
let currentSpotId = null;
let refreshIntervalId = null;
let currentLanguage = 'en';

// ============================================================================
// CONFIGURATION
// ============================================================================

const API_ENDPOINT = '/api/v1/spots';
const REFRESH_INTERVAL = 60000; // 1 minute

// ============================================================================
// URL HELPERS
// ============================================================================

// Extract spot ID from the URL pattern: /spot/{spotId}/tv
function getSpotIdFromUrl() {
    const pathParts = window.location.pathname.split('/');
    const spotIndex = pathParts.indexOf('spot');
    if (spotIndex !== -1 && pathParts.length > spotIndex + 1) {
        return pathParts[spotIndex + 1];
    }
    return null;
}

// ============================================================================
// API FUNCTIONS
// ============================================================================

async function fetchSpotData(spotId) {
    try {
        const url = `${API_ENDPOINT}/${spotId}`;
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching spot data:', error);
        throw error;
    }
}

// ============================================================================
// TRANSLATION FUNCTIONS
// ============================================================================

function t(key) {
    return translations[currentLanguage]?.[key] || key;
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

function getWindArrow(direction) {
    const arrows = {
        'N': '↓', 'NE': '↙', 'E': '←', 'SE': '↖',
        'S': '↑', 'SW': '↗', 'W': '→', 'NW': '↘'
    };
    return arrows[direction] || '•';
}

function getWindRotation(direction) {
    const rotations = {
        'N': 0, 'NE': 45, 'E': 90, 'SE': 135,
        'S': 180, 'SW': 225, 'W': 270, 'NW': 315
    };
    return rotations[direction] || 0;
}

function getWindClass(wind, gusts) {
    const avgWind = (wind + gusts) / 2;
    if (avgWind < 12) return 'weak';
    if (avgWind >= 12 && avgWind < 18) return 'moderate';
    if (avgWind >= 18 && avgWind <= 25) return 'strong';
    return 'extreme';
}

function formatTime(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split(' ');
    if (parts.length >= 5) {
        return parts[4]; // HH:MM
    }
    return dateStr;
}

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

function filterFutureForecasts(forecasts) {
    const now = new Date();
    const currentHour = new Date(now.getFullYear(), now.getMonth(), now.getDate(), now.getHours(), 0, 0);

    return forecasts.filter(forecast => {
        const forecastDate = parseForecastDate(forecast.date);
        return forecastDate >= currentHour;
    });
}

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
// DISPLAY FUNCTIONS
// ============================================================================

function displaySpot(spot) {
    currentSpot = spot;

    // Update header
    const spotName = document.getElementById('tvSpotName');
    const spotCountry = document.getElementById('tvSpotCountry');
    const providerText = document.getElementById('tvProviderText');

    if (spotName) {
        spotName.textContent = spot.name;
    }
    if (spotCountry) {
        const countryFlag = getCountryFlag(spot.country);
        const countryName = t(spot.country) || spot.country;
        spotCountry.textContent = `${countryFlag} ${countryName}`;
    }
    if (providerText) {
        providerText.textContent = t('tvProviderLabel');
    }

    // Update title
    document.title = `${spot.name} - TV View - VARUN.SURF`;

    // Get forecast data
    const forecastData = (spot.forecastHourly && spot.forecastHourly.length > 0)
        ? spot.forecastHourly
        : (spot.forecast || []);

    // Determine current conditions
    let conditionsData = null;
    let conditionsLabel = '';
    let isRealTime = false;

    if (spot.currentConditions && spot.currentConditions.wind !== undefined) {
        conditionsData = {
            wind: spot.currentConditions.wind,
            gusts: spot.currentConditions.gusts,
            direction: spot.currentConditions.direction,
            temp: spot.currentConditions.temp,
            precipitation: 0
        };
        conditionsLabel = t('tvCurrentConditionsLive');
        isRealTime = true;
    } else if (forecastData && forecastData.length > 0) {
        const nearestForecast = findClosestForecast(forecastData);
        if (nearestForecast) {
            conditionsData = {
                wind: nearestForecast.wind,
                gusts: nearestForecast.gusts,
                direction: nearestForecast.direction,
                temp: nearestForecast.temp,
                precipitation: nearestForecast.precipitation || 0
            };
            conditionsLabel = t('tvCurrentConditionsEstimated');
            isRealTime = false;
        }
    }

    // Build content HTML
    let contentHtml = '';

    // Current conditions section
    if (conditionsData) {
        const windClass = getWindClass(conditionsData.wind, conditionsData.gusts);
        const windArrow = getWindArrow(conditionsData.direction);
        const rotation = getWindRotation(conditionsData.direction);

        contentHtml += `
            <div class="tv-current-conditions">
                <div class="tv-conditions-label">${conditionsLabel}</div>
                <div class="tv-wind-main">
                    <div class="tv-wind-arrow" style="transform: rotate(${rotation}deg);">↓</div>
                    <div class="tv-wind-speed ${windClass}">${conditionsData.wind} kts</div>
                </div>
                <div class="tv-conditions-grid">
                    <div class="tv-condition-item">
                        <div class="tv-condition-label">${t('gustsLabel')}</div>
                        <div class="tv-condition-value ${windClass}">${conditionsData.gusts} kts</div>
                    </div>
                    <div class="tv-condition-item">
                        <div class="tv-condition-label">${t('directionLabel')}</div>
                        <div class="tv-condition-value">${windArrow} ${conditionsData.direction}</div>
                    </div>
                    <div class="tv-condition-item">
                        <div class="tv-condition-label">${t('tempLabel')}</div>
                        <div class="tv-condition-value">${conditionsData.temp}°C</div>
                    </div>
                    <div class="tv-condition-item">
                        <div class="tv-condition-label">${t('precipitationLabel')}</div>
                        <div class="tv-condition-value">${conditionsData.precipitation} mm</div>
                    </div>
                </div>
            </div>
        `;
    }

    // Forecast section
    if (forecastData && forecastData.length > 0) {
        const futureForecasts = filterFutureForecasts(forecastData);
        const limitedForecasts = futureForecasts.slice(0, 7); // Show next 7 hours

        if (limitedForecasts.length > 0) {
            contentHtml += `
                <div class="tv-forecast-section">
                    <div class="tv-forecast-horizontal">
            `;

            limitedForecasts.forEach(forecast => {
                const time = formatTime(forecast.date);
                const windClass = getWindClass(forecast.wind, forecast.gusts);
                const windArrow = getWindArrow(forecast.direction);

                contentHtml += `
                    <div class="tv-forecast-hour">
                        <div class="tv-forecast-time">${time}</div>
                        <div class="tv-forecast-data">
                            <div class="tv-forecast-item">
                                <div class="tv-forecast-item-value ${windClass}">${forecast.wind} kts</div>
                            </div>
                            <div class="tv-forecast-item">
                                <div class="tv-forecast-item-value ${windClass}">${forecast.gusts} kts</div>
                            </div>
                            <div class="tv-forecast-item">
                                <div class="tv-forecast-item-value">${windArrow} ${forecast.direction}</div>
                            </div>
                            <div class="tv-forecast-item">
                                <div class="tv-forecast-item-value">${forecast.temp}°C</div>
                            </div>
                        </div>
                    </div>
                `;
            });

            contentHtml += `
                    </div>
                </div>
            `;
        }
    }

    // Update content
    const tvContent = document.getElementById('tvContent');
    if (tvContent) {
        tvContent.innerHTML = contentHtml;
    }
}

function displayError(message) {
    const tvContent = document.getElementById('tvContent');
    if (tvContent) {
        tvContent.innerHTML = `<div class="tv-error">${message}</div>`;
    }
}

// ============================================================================
// THEME MANAGEMENT
// ============================================================================

function initTheme() {
    const savedTheme = localStorage.getItem('tvTheme') || 'dark';
    const themeToggle = document.getElementById('tvThemeToggle');
    const themeIcon = document.getElementById('tvThemeIcon');

    function updateTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        if (theme === 'light') {
            themeIcon.innerHTML = '<path d="M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5,5-2.24,5-5-2.24-5-5-5Zm0,7c-1.1,0-2-.9-2-2s.9-2,2-2,2,.9,2,2-.9,2-2,2Zm4.95-6.95c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0,.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44ZM7.05,16.95c.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0ZM3.51,5.64c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41Zm16.97,12.73c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41Zm3.51-6.36c0,.83-.67,1.5-1.5,1.5h-2c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5h2c.83,0,1.5,.67,1.5,1.5ZM3.5,13.5H1.5c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5H3.5c.83,0,1.5,.67,1.5,1.5s-.67,1.5-1.5,1.5ZM10.5,3.5V1.5c0-.83,.67-1.5,1.5-1.5s1.5,.67,1.5,1.5V3.5c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5Zm3,17v2c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5v-2c0-.83,.67-1.5-1.5-1.5s1.5,.67,1.5,1.5Z"/>';
        } else {
            themeIcon.innerHTML = '<path d="M15,24a12.021,12.021,0,0,1-8.914-3.966,11.9,11.9,0,0,1-3.02-9.309A12.122,12.122,0,0,1,13.085.152a13.061,13.061,0,0,1,5.031.205,2.5,2.5,0,0,1,1.108,4.226c-4.56,4.166-4.164,10.644.807,14.41a2.5,2.5,0,0,1-.7,4.32A13.894,13.894,0,0,1,15,24Z"/>';
        }
        localStorage.setItem('tvTheme', theme);
    }

    // Set initial theme
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
// LANGUAGE MANAGEMENT
// ============================================================================

function initLanguage() {
    const savedLang = localStorage.getItem('tvLanguage') || 'en';
    const languageToggle = document.getElementById('tvLanguageToggle');
    const langCode = document.getElementById('tvLangCode');

    function updateLanguage(lang) {
        currentLanguage = lang;
        localStorage.setItem('tvLanguage', lang);

        if (langCode) {
            langCode.textContent = lang.toUpperCase();
        }

        // Re-render the current spot with new language
        if (currentSpot) {
            displaySpot(currentSpot);
        }
    }

    // Set initial language
    updateLanguage(savedLang);

    // Language toggle event
    if (languageToggle) {
        languageToggle.addEventListener('click', () => {
            const newLang = currentLanguage === 'en' ? 'pl' : 'en';
            updateLanguage(newLang);
        });
    }
}

// ============================================================================
// AUTO REFRESH
// ============================================================================

function startAutoRefresh(spotId) {
    if (refreshIntervalId || !spotId) {
        return;
    }

    refreshIntervalId = setInterval(async () => {
        try {
            const latestSpot = await fetchSpotData(spotId);
            displaySpot(latestSpot);
        } catch (error) {
            console.warn('Auto refresh failed:', error);
        }
    }, REFRESH_INTERVAL);
}

function stopAutoRefresh() {
    if (refreshIntervalId) {
        clearInterval(refreshIntervalId);
        refreshIntervalId = null;
    }
}

// ============================================================================
// INITIALIZATION
// ============================================================================

async function setupTvView() {
    const spotId = getSpotIdFromUrl();
    currentSpotId = spotId;

    if (!spotId) {
        displayError('Invalid spot ID');
        return;
    }

    try {
        const spot = await fetchSpotData(spotId);
        displaySpot(spot);
        startAutoRefresh(spotId);
    } catch (error) {
        displayError('Failed to load spot data. Please try again.');
        console.error('Error loading spot:', error);
    }
}

// ============================================================================
// START APPLICATION
// ============================================================================

document.addEventListener('DOMContentLoaded', function () {
    initTheme();
    initLanguage();
    setupTvView();
});

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    stopAutoRefresh();
});
