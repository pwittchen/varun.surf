// ============================================================================
// EMBED WIDGET (/embed)
// The single-spot card third-party sites drop into an iframe. It carries its own
// markup, styles and dictionary - a page that runs on somebody else's site can't
// assume anything of ours is loaded - and shares only what would otherwise be
// copied: the map's tile layers and wind field.
// ============================================================================

import * as map from '../common/map.js';
import * as weather from '../common/weather.js';
import { translations as appTranslations } from '../common/translations.js';

// Parse URL parameters
const params = new URLSearchParams(window.location.search);
const spotId = params.get('spotId');
const paramTheme = (params.get('theme') || 'dark').toLowerCase();
const theme = paramTheme === 'light' ? 'light' : 'dark';
const rawView = (params.get('view') || 'conditions').toLowerCase();
const view = ['forecast', 'map'].includes(rawView) ? rawView : 'conditions';
const paramLang = (params.get('lang') || 'en').toLowerCase();
const lang = paramLang === 'pl' ? 'pl' : 'en';
const rawMapStyle = (params.get('mapStyle') || 'satellite').toLowerCase();
const mapStyle = ['light', 'dark'].includes(rawMapStyle) ? rawMapStyle : 'satellite';

const translations = {
    en: {
        loadingSpotData: 'Loading spot data...',
        windSpeedLabel: 'Wind Speed',
        gustsLabel: 'Gusts',
        directionLabel: 'Direction',
        temperatureLabel: 'Temperature',
        precipitationLabel: 'Precipitation',
        timeHeader: 'Time',
        windHeader: 'Wind',
        gustsHeader: 'Gusts',
        dirHeader: 'Dir',
        tempHeader: 'Temp',
        noData: 'No data available',
        missingSpotId: 'Missing spotId parameter',
        spotNotFound: 'Spot not found',
        failedToLoad: 'Failed to load spot data',
        dataProvidedBy: 'Data provided by',
        liveDataLabel: 'Live data',
        estimatedFromForecastLabel: 'Estimated from forecast',
        loadingMap: 'Loading map...',
        mapUnavailable: 'Map unavailable',
        windFieldDisclaimer: 'Interpolated from spot data'
    },
    pl: {
        loadingSpotData: 'Ładowanie danych spotu...',
        windSpeedLabel: 'Prędkość wiatru',
        gustsLabel: 'Porywy',
        directionLabel: 'Kierunek',
        temperatureLabel: 'Temperatura',
        precipitationLabel: 'Opady',
        timeHeader: 'Czas',
        windHeader: 'Wiatr',
        gustsHeader: 'Porywy',
        dirHeader: 'Kier.',
        tempHeader: 'Temp.',
        noData: 'Brak danych',
        missingSpotId: 'Brak parametru spotId',
        spotNotFound: 'Nie znaleziono spotu',
        failedToLoad: 'Nie udało się załadować danych spotu',
        dataProvidedBy: 'Dane dostarcza',
        liveDataLabel: 'Dane na żywo',
        estimatedFromForecastLabel: 'Szacowane z prognozy',
        loadingMap: 'Ładowanie mapy...',
        mapUnavailable: 'Mapa niedostępna',
        windFieldDisclaimer: 'Interpolacja z danych spotów'
    }
};

// Country names in Polish, keyed by the English name with spaces removed
// (same convention as translations.js used by the main app)
const countriesPl = {
    Poland: 'Polska',
    CzechRepublic: 'Czechy',
    Austria: 'Austria',
    Belgium: 'Belgia',
    Switzerland: 'Szwajcaria',
    Latvia: 'Łotwa',
    Lithuania: 'Litwa',
    Estonia: 'Estonia',
    Denmark: 'Dania',
    Sweden: 'Szwecja',
    Norway: 'Norwegia',
    Iceland: 'Islandia',
    Spain: 'Hiszpania',
    Portugal: 'Portugalia',
    Italy: 'Włochy',
    Greece: 'Grecja',
    France: 'Francja',
    Germany: 'Niemcy',
    Netherlands: 'Holandia',
    Croatia: 'Chorwacja',
    Slovenia: 'Słowenia',
    Serbia: 'Serbia',
    Montenegro: 'Czarnogóra',
    Albania: 'Albania',
    Macedonia: 'Macedonia',
    Bulgaria: 'Bułgaria',
    Romania: 'Rumunia',
    Malta: 'Malta',
    Ireland: 'Irlandia',
    UnitedKingdom: 'Wielka Brytania',
    UK: 'Anglia',
    Turkey: 'Turcja',
    Morocco: 'Maroko',
    Egypt: 'Egipt',
    CapeVerde: 'Cabo Verde',
    Mauritius: 'Mauritius',
    Brazil: 'Brazylia',
    Peru: 'Peru',
    Chile: 'Chile',
    USA: 'USA',
    Namibia: 'Namibia',
    Mexico: 'Meksyk',
    CostaRica: 'Kostaryka',
    SouthAfrica: 'RPA',
    Tanzania: 'Tanzania',
    SriLanka: 'Sri Lanka',
    Vietnam: 'Wietnam',
    TurksandCaicos: 'Turks and Caicos'
};

function t(key) {
    const dict = translations[lang] || translations.en;
    return dict[key] || translations.en[key] || key;
}

/**
 * Day and hour names for the forecast slider, taken from the app's dictionary in
 * the language the widget was embedded with. Reading the app's own language would
 * be wrong twice over: it lives in localStorage, which a third-party iframe may
 * not be allowed to touch, and it belongs to the visitor's use of the site rather
 * than to whoever chose the language of this widget.
 * @param {string} key - Translation key
 * @returns {string} Localized label
 */
function translateTimeline(key) {
    const dict = appTranslations[lang] || appTranslations.en;
    return dict[key] || appTranslations.en[key] || key;
}

function translateCountry(country) {
    if (!country || lang !== 'pl') {
        return country || '';
    }
    return countriesPl[country.replace(/\s+/g, '')] || country;
}

const apiBase = (window.location && window.location.origin && window.location.origin.startsWith('http'))
    ? window.location.origin.replace(/\/$/, '')
    : 'https://varun.surf';

// Set theme
const widget = document.getElementById('varunWidget');
widget.setAttribute('data-theme', theme);
document.documentElement.lang = lang;

const initialContent = document.getElementById('widgetContent');
if (initialContent) {
    initialContent.innerHTML = `<div class="varun-loading">${t('loadingSpotData')}</div>`;
}

const footer = document.getElementById('varunFooter');
if (footer) {
    footer.innerHTML = `${t('dataProvidedBy')} <a href="https://varun.surf" target="_blank" rel="noopener noreferrer">VARUN.SURF</a>`;
}

if (!spotId) {
    showError(t('missingSpotId'));
} else {
    fetchSpotData(spotId);
}

function fetchSpotData(id) {
    fetch(`${apiBase}/api/v1/spots/${id}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('spotNotFound');
            }
            return response.json();
        })
        .then(spot => {
            displaySpot(spot);
        })
        .catch(error => {
            const key = error && translations.en[error.message] ? error.message : 'failedToLoad';
            showError(t(key));
        });
}

function displaySpot(spot) {
    const spotName = document.getElementById('spotName');
    const spotCountry = document.getElementById('spotCountry');
    const widgetContent = document.getElementById('widgetContent');

    if (spotName) {
        const spotLink = document.createElement('a');
        spotLink.href = `${apiBase}/spot/${encodeURIComponent(spotId)}`;
        spotLink.target = '_blank';
        spotLink.rel = 'noopener noreferrer';
        spotLink.textContent = spot.name;
        spotName.innerHTML = '';
        spotName.appendChild(spotLink);
    }
    spotCountry.textContent = translateCountry(spot.country);

    if (view === 'conditions') {
        displayCurrentConditions(spot, widgetContent);
    } else if (view === 'map') {
        displayMap(spot, widgetContent);
    } else {
        displayForecastVertical(spot, widgetContent);
    }
}

function displayCurrentConditions(spot, container) {
    let data = null;
    let isRealTime = false;

    // Prefer current conditions if available and valid, fallback to nearest forecast
    if (spot.currentConditions &&
        spot.currentConditions.wind != null &&
        spot.currentConditions.wind >= 0) {
        data = {
            wind: spot.currentConditions.wind,
            gusts: spot.currentConditions.gusts,
            direction: spot.currentConditions.direction,
            temp: spot.currentConditions.temp,
            precipitation: 0 // Current conditions don't have precipitation
        };
        isRealTime = true;
    } else if (spot.forecastHourly && spot.forecastHourly.length > 0) {
        // Use current hour forecast as fallback
        const currentHourForecast = getCurrentOrNextForecast(spot.forecastHourly);
        if (currentHourForecast) {
            data = {
                wind: currentHourForecast.wind,
                gusts: currentHourForecast.gusts,
                direction: currentHourForecast.direction,
                temp: currentHourForecast.temp,
                precipitation: sanitizeNumber(currentHourForecast.precipitation, 0)
            };
            isRealTime = false;
        }
    }

    if (!data) {
        container.innerHTML = `<div class="varun-error">${t('noData')}</div>`;
        return;
    }

    const avgWind = getAverageWind(data.wind, data.gusts);
    let windClass = 'weak';
    if (avgWind >= 12 && avgWind < 18) windClass = 'moderate';
    else if (avgWind >= 18 && avgWind <= 25) windClass = 'strong';
    else if (avgWind > 25) windClass = 'extreme';

    const arrow = getWindArrow(data.direction);
    const dataSourceLabel = isRealTime ? t('liveDataLabel') : t('estimatedFromForecastLabel');
    const dataSource = `<div style="font-size: 11px; opacity: 0.6; margin-top: 8px;">${dataSourceLabel}</div>`;

    container.innerHTML = `
        <div class="varun-current-conditions">
            <div class="varun-wind-main ${windClass}">
                <div class="varun-wind-speed ${windClass}">${data.wind} kts</div>
                <div class="varun-wind-label">${t('windSpeedLabel')}</div>
                ${dataSource}
            </div>
            <div class="varun-conditions-grid">
                <div class="varun-condition-item">
                    <div class="varun-condition-label">${t('gustsLabel')}</div>
                    <div class="varun-condition-value">${data.gusts} kts</div>
                </div>
                <div class="varun-condition-item">
                    <div class="varun-condition-label">${t('directionLabel')}</div>
                    <div class="varun-condition-value">${arrow} ${data.direction}</div>
                </div>
                <div class="varun-condition-item">
                    <div class="varun-condition-label">${t('temperatureLabel')}</div>
                    <div class="varun-condition-value">${data.temp}°C</div>
                </div>
                <div class="varun-condition-item">
                    <div class="varun-condition-label">${t('precipitationLabel')}</div>
                    <div class="varun-condition-value">${data.precipitation ?? 0} mm</div>
                </div>
            </div>
        </div>
    `;
}

function displayForecastVertical(spot, container) {
    const forecastData = spot.forecastHourly || spot.forecast || [];

    if (forecastData.length === 0) {
        container.innerHTML = `<div class="varun-error">${t('noData')}</div>`;
        return;
    }

    // Filter to show only future forecasts starting from current hour
    const futureForecasts = filterFutureForecasts(forecastData);

    if (futureForecasts.length === 0) {
        container.innerHTML = `<div class="varun-error">${t('noData')}</div>`;
        return;
    }

    // Show next 8 hours of forecast
    const limitedForecast = futureForecasts.slice(0, 8);

    let tableHtml = `
        <table class="varun-forecast-table">
            <thead>
                <tr>
                    <th>${t('timeHeader')}</th>
                    <th>${t('windHeader')}</th>
                    <th>${t('gustsHeader')}</th>
                    <th>${t('dirHeader')}</th>
                    <th>${t('tempHeader')}</th>
                </tr>
            </thead>
            <tbody>
    `;

    limitedForecast.forEach(forecast => {
        const time = formatTime(forecast.date);
        const arrow = getWindArrow(forecast.direction);
        const windClass = getWindClassFromValues(forecast.wind, forecast.gusts);
        const windText = `${forecast.wind} kts`;
        const gustText = `${forecast.gusts} kts`;
        tableHtml += `
            <tr class="${windClass}">
                <td><strong>${time}</strong></td>
                <td class="varun-forecast-wind ${windClass}">${windText}</td>
                <td class="varun-forecast-gusts ${windClass}">${gustText}</td>
                <td>${arrow} ${forecast.direction}</td>
                <td>${forecast.temp}°C</td>
            </tr>
        `;
    });

    tableHtml += `
            </tbody>
        </table>
    `;

    container.innerHTML = tableHtml;
}

// ============================================================================
// MAP VIEW
// The same wind field the site paints - a bare basemap says nothing a screenshot
// wouldn't - with the spot marked and named on top of it.
// ============================================================================

// Widget style names mapped onto the site's tile layers, so a widget and the
// spot page it links to show the same world.
const MAP_TILE_LAYERS = {
    satellite: 'satellite',
    light: 'osm',
    dark: 'osmDark'
};

// Credit lines kept to one row: the site's full Esri notice wraps twice in a
// 600px widget and eats a third of the map. Short forms, still attributing every
// source the tiles come from.
const MAP_ATTRIBUTIONS = {
    satellite: 'Tiles &copy; Esri',
    light: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    dark: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
};

// Close enough that the spot itself is readable - a wider view of a lake spot
// shows no water at all, just land. Same zoom the spot page opens at.
const MAP_ZOOM = 12;

// Coordinates are resolved off the request path, so the first read of a spot
// nobody has asked for in a while can come back without them. They land moments
// later, hence a couple of retries before giving up.
const MAP_COORDINATES_RETRIES = 3;
const MAP_COORDINATES_RETRY_DELAY_MS = 1500;

let leafletRequest = null;
let fieldSpotsRequest = null;
let windTimelineRequest = null;

/**
 * Load Leaflet on demand: the conditions and forecast views are plain markup and
 * must not pay for a map they never draw.
 * @returns {Promise<object>} The Leaflet namespace
 */
function loadLeaflet() {
    if (window.L) {
        return Promise.resolve(window.L);
    }
    if (!leafletRequest) {
        leafletRequest = new Promise((resolve, reject) => {
            const styles = document.createElement('link');
            styles.rel = 'stylesheet';
            styles.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';
            document.head.appendChild(styles);

            const script = document.createElement('script');
            script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
            script.onload = () => resolve(window.L);
            script.onerror = () => reject(new Error('leafletUnavailable'));
            document.head.appendChild(script);
        });
    }
    return leafletRequest;
}

/**
 * Every spot, so the field has neighbours to interpolate between. The spot on its
 * own paints a single blob, which is what a failed request falls back to.
 * @param {object} spot - The widget's spot
 * @returns {Promise<Array>} Spots to build the field from
 */
function loadFieldSpots(spot) {
    if (!fieldSpotsRequest) {
        fieldSpotsRequest = fetch(`${apiBase}/api/v1/spots`)
            .then(response => (response.ok ? response.json() : Promise.reject(new Error('spots'))))
            .then(spots => (Array.isArray(spots) && spots.length > 0 ? spots : [spot]))
            .catch(() => [spot]);
    }
    return fieldSpotsRequest;
}

/**
 * The hourly wind grid the slider steps through - the five days the widget's
 * strip has room to address, the same span a phone gets on the site's own maps.
 * @returns {Promise<object>} Indexed timeline (empty when the request fails)
 */
function loadWindTimeline() {
    if (!windTimelineRequest) {
        windTimelineRequest = fetch(`${apiBase}/api/v1/wind?hours=${map.TIMELINE_HOURS_COMPACT}`)
            .then(response => (response.ok ? response.json() : Promise.reject(new Error('wind'))))
            .then(timeline => weather.indexWindTimeline(timeline))
            .catch(() => weather.indexWindTimeline(null));
    }
    return windTimelineRequest;
}

function getCoordinates(spot) {
    const coords = spot && spot.coordinates;
    if (!coords) {
        return null;
    }
    const lat = Number(coords.lat);
    const lon = Number(coords.lon);
    return Number.isFinite(lat) && Number.isFinite(lon) ? { lat, lon } : null;
}

function displayMap(spot, container, attempt = 0) {
    const coords = getCoordinates(spot);

    if (!coords) {
        if (attempt < MAP_COORDINATES_RETRIES) {
            container.innerHTML = `<div class="varun-loading">${t('loadingMap')}</div>`;
            setTimeout(() => {
                fetch(`${apiBase}/api/v1/spots/${encodeURIComponent(spotId)}`)
                    .then(response => (response.ok ? response.json() : null))
                    .then(refreshed => displayMap(refreshed || spot, container, attempt + 1))
                    .catch(() => displayMap(spot, container, attempt + 1));
            }, MAP_COORDINATES_RETRY_DELAY_MS);
            return;
        }
        container.innerHTML = `<div class="varun-error">${t('mapUnavailable')}</div>`;
        return;
    }

    container.innerHTML = `
        <div class="varun-map-view">
            <div class="varun-map-frame">
                <div class="varun-map" id="varunMap"></div>
                <div class="varun-map-note">${t('windFieldDisclaimer')}</div>
            </div>
        </div>
    `;

    loadLeaflet()
        .then(L => {
            const element = document.getElementById('varunMap');
            if (!element) {
                return;
            }

            const leafletMap = L.map(element, {
                center: [coords.lat, coords.lon],
                zoom: MAP_ZOOM,
                // A widget lives inside somebody else's page: swallowing the wheel
                // would trap the reader scrolling past it.
                scrollWheelZoom: false
            });

            const tiles = map.getMapTileConfig(MAP_TILE_LAYERS[mapStyle] || 'satellite');
            L.tileLayer(tiles.url, {
                ...tiles.options,
                attribution: MAP_ATTRIBUTIONS[mapStyle] || MAP_ATTRIBUTIONS.satellite
            }).addTo(leafletMap);

            // The marker goes on before the field: the map is worth looking at as
            // soon as the tiles arrive, and the marker pane sits above the overlay
            // one either way.
            L.marker([coords.lat, coords.lon], {
                icon: L.divIcon({
                    className: '',
                    html: '<div class="varun-map-marker"></div>',
                    iconSize: [18, 18],
                    iconAnchor: [9, 9]
                }),
                interactive: false,
                keyboard: false
            })
                .addTo(leafletMap)
                .bindTooltip(spot.name || '', {
                    permanent: true,
                    direction: 'right',
                    offset: [10, 0],
                    className: 'varun-map-label',
                    interactive: false
                });

            // Both passes of the field live in one group, so stepping the slider
            // swaps them together and can never strand a layer on the map.
            const fieldLayer = L.layerGroup().addTo(leafletMap);

            Promise.all([loadFieldSpots(spot), loadWindTimeline()]).then(([spots, timeline]) => {
                if (!leafletMap.getContainer().isConnected) {
                    return;
                }

                // Step 0 is now - live readings where a spot has them; every later
                // step is one hour of the shared forecast grid.
                let step = 0;
                const conditionsAt = (fieldSpot) => weather.getWindConditionsAtStep(fieldSpot, step, timeline);

                const renderField = () => {
                    fieldLayer.clearLayers();
                    fieldLayer.addLayer(map.createWindHeatLayer(spots, conditionsAt));
                    fieldLayer.addLayer(map.createWindParticleLayer(spots, conditionsAt));
                };

                renderField();

                // Nothing to step through if the grid never arrived; the map still
                // shows the hour it opened on.
                map.createForecastTimeline({
                    container: document.querySelector('.varun-map-view'),
                    hours: timeline.hours,
                    translate: translateTimeline,
                    onChange: (nextStep) => {
                        step = nextStep;
                        renderField();
                    }
                });
            });
        })
        .catch(() => {
            container.innerHTML = `<div class="varun-error">${t('mapUnavailable')}</div>`;
        });
}

function formatTime(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split(' ');
    if (parts.length >= 5) {
        return parts[4]; // HH:MM
    }
    return dateStr;
}

function getWindArrow(direction) {
    const arrows = {
        'N': '↓', 'NE': '↙', 'E': '←', 'SE': '↖',
        'S': '↑', 'SW': '↗', 'W': '→', 'NW': '↘'
    };
    return arrows[direction] || '•';
}

function sanitizeNumber(value, fallback = 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
}

function getAverageWind(wind, gusts) {
    return (sanitizeNumber(wind) + sanitizeNumber(gusts)) / 2;
}

function getWindClassFromValues(wind, gusts) {
    const avgWind = getAverageWind(wind, gusts);
    if (avgWind < 12) return 'varun-wind-weak';
    if (avgWind < 18) return 'varun-wind-moderate';
    if (avgWind <= 25) return 'varun-wind-strong';
    return 'varun-wind-extreme';
}

/**
 * Parse forecast date string to Date object
 * Expected format: "Day Mon DD YYYY HH:MM:SS GMT+OFFSET (ZONE)"
 * Example: "Mon Nov 30 2025 14:00:00 GMT+0100 (CET)"
 */
function parseForecastDate(dateStr) {
    if (!dateStr) return null;
    try {
        // Try to parse the date directly
        const parsed = new Date(dateStr);
        if (!isNaN(parsed.getTime())) {
            return parsed;
        }
        return null;
    } catch (e) {
        return null;
    }
}

/**
 * Filter forecasts to only include future hours (from current hour onwards)
 */
function filterFutureForecasts(forecasts) {
    const now = new Date();
    const currentHour = new Date(now.getFullYear(), now.getMonth(), now.getDate(), now.getHours(), 0, 0);

    return forecasts.filter(forecast => {
        const forecastDate = parseForecastDate(forecast.date);
        if (!forecastDate) return false;
        return forecastDate >= currentHour;
    });
}

/**
 * Get the forecast for current or next hour
 */
function getCurrentOrNextForecast(forecasts) {
    const now = new Date();
    const currentHour = new Date(now.getFullYear(), now.getMonth(), now.getDate(), now.getHours(), 0, 0);

    // Find forecast for current hour or next available hour
    for (const forecast of forecasts) {
        const forecastDate = parseForecastDate(forecast.date);
        if (forecastDate && forecastDate >= currentHour) {
            return forecast;
        }
    }

    // Fallback to first forecast if no future forecast found
    return forecasts.length > 0 ? forecasts[0] : null;
}

function showError(message) {
    const widgetContent = document.getElementById('widgetContent');
    widgetContent.innerHTML = `<div class="varun-error">${message}</div>`;
}
