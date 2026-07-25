// ============================================================================
// MAP UTILITIES
// Common map-related functions for OSM and satellite tile layers
// ============================================================================

import * as translations from './translations.js';
import * as weather from './weather.js';

// ============================================================================
// TILE LAYER CONFIGURATIONS
// ============================================================================

/**
 * Get tile layer configuration for a given layer type
 * @param {string} layerType - 'satellite' or 'osm'
 * @returns {object} Configuration object with url and options
 */
export function getMapTileConfig(layerType) {
    if (layerType === 'satellite') {
        return {
            url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
            options: {
                attribution: 'Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community',
                maxZoom: 19
            }
        };
    } else {
        // OSM default layer
        return {
            url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
            options: {
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
                maxZoom: 19
            }
        };
    }
}

// ============================================================================
// TILE LAYER MANAGEMENT
// ============================================================================

/**
 * Create a tile layer for a Leaflet map
 * @param {string} layerType - 'satellite' or 'osm'
 * @returns {L.TileLayer} Leaflet tile layer
 */
export function createTileLayer(layerType) {
    const config = getMapTileConfig(layerType);
    return L.tileLayer(config.url, config.options);
}

/**
 * Update the tile layer on a map
 * @param {L.Map} map - Leaflet map instance
 * @param {L.TileLayer|null} currentTileLayer - Current tile layer (will be removed)
 * @param {string} layerType - 'satellite' or 'osm'
 * @returns {L.TileLayer} The new tile layer that was added
 */
export function updateTileLayer(map, currentTileLayer, layerType) {
    if (!map) {
        return null;
    }

    if (currentTileLayer) {
        map.removeLayer(currentTileLayer);
    }

    const newTileLayer = createTileLayer(layerType);
    newTileLayer.addTo(map);
    return newTileLayer;
}

// ============================================================================
// LAYER SWITCHER CONTROL
// ============================================================================

// SVG icon for the layer switcher button
const LAYER_SWITCHER_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="m12,18.838c-.572,0-1.143-.153-1.653-.459L.485,12.462c-.474-.284-.627-.898-.343-1.372.283-.475.897-.627,1.372-.343l9.861,5.917c.385.23.864.23,1.249,0l9.861-5.917c.474-.284,1.088-.131,1.372.343s.131,1.088-.343,1.372l-9.861,5.917c-.51.306-1.082.459-1.653.459Zm1.653,3.836l9.861-5.917c.474-.284.627-.898.343-1.372s-.898-.627-1.372-.343l-9.862,5.917c-.384.23-.863.23-1.248,0L1.515,15.042c-.475-.285-1.089-.131-1.372.343-.284.474-.131,1.088.343,1.372l9.861,5.917c.51.307,1.082.459,1.654.459s1.144-.152,1.653-.459Zm-1.653-16.84l5.308-3.185L13.653.456c-1.02-.612-2.287-.612-3.307,0l-3.655,2.193,5.308,3.185Zm11.515.539l-4.263-2.558-5.308,3.185,5.692,3.415,3.879-2.327c.301-.181.485-.506.485-.857s-.184-.677-.485-.857Zm-13.459.627l-5.308-3.185L.485,6.373c-.301.181-.485.506-.485.857s.184.677.485.857l3.879,2.327,5.692-3.415Zm1.944,1.166l-5.692,3.415,4.039,2.423c.51.306,1.081.459,1.653.459s1.143-.153,1.653-.459l4.039-2.423-5.692-3.415Z"/></svg>';

/**
 * Create a Leaflet layer switcher control
 * @param {object} options - Configuration options
 * @param {function} options.getCurrentLayer - Function that returns current layer type ('satellite' or 'osm')
 * @param {function} options.onLayerChange - Callback when layer is changed, receives new layer type
 * @param {string} [options.position='bottomleft'] - Control position
 * @returns {L.Control} Leaflet control instance
 */
export function createLayerSwitcher(options) {
    const {
        getCurrentLayer,
        onLayerChange,
        position = 'bottomleft'
    } = options;

    const LayerSwitcher = L.Control.extend({
        options: {
            position: position
        },

        onAdd: function(map) {
            const container = L.DomUtil.create('div', 'leaflet-bar leaflet-control leaflet-control-layer-switcher');

            // Create button with layer icon
            const button = L.DomUtil.create('button', 'layer-switcher-button', container);
            button.type = 'button';
            button.title = 'Switch map layer';
            button.innerHTML = LAYER_SWITCHER_ICON;

            // Create dropdown menu
            const dropdown = L.DomUtil.create('div', 'layer-switcher-dropdown', container);
            dropdown.style.display = 'none';

            // Create dropdown options with translation keys
            const layerOptions = [
                { value: 'osm', translationKey: 'mapLayerOsm' },
                { value: 'satellite', translationKey: 'mapLayerSatellite' }
            ];

            layerOptions.forEach(option => {
                const optionEl = L.DomUtil.create('div', 'layer-switcher-option', dropdown);
                optionEl.textContent = translations.t(option.translationKey);
                optionEl.dataset.value = option.value;
                optionEl.dataset.translationKey = option.translationKey;

                if (option.value === getCurrentLayer()) {
                    optionEl.classList.add('active');
                }

                L.DomEvent.on(optionEl, 'click', function(e) {
                    L.DomEvent.stopPropagation(e);

                    // Update active state
                    dropdown.querySelectorAll('.layer-switcher-option').forEach(opt => {
                        opt.classList.remove('active');
                    });
                    optionEl.classList.add('active');

                    // Notify about layer change
                    onLayerChange(option.value);

                    // Close dropdown
                    dropdown.style.display = 'none';
                    button.classList.remove('open');
                });
            });

            // Toggle dropdown on the button click
            L.DomEvent.on(button, 'click', function(e) {
                L.DomEvent.stopPropagation(e);
                const isOpen = dropdown.style.display === 'block';
                dropdown.style.display = isOpen ? 'none' : 'block';
                button.classList.toggle('open', !isOpen);
            });

            // Close dropdown when clicking outside
            L.DomEvent.on(map.getContainer(), 'click', function() {
                dropdown.style.display = 'none';
                button.classList.remove('open');
            });

            L.DomEvent.disableClickPropagation(container);

            return container;
        }
    });

    return new LayerSwitcher();
}

// ============================================================================
// RESET VIEW CONTROL
// ============================================================================

// SVG crosshair icon for the reset view button
const RESET_VIEW_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="2" x2="12" y2="6"/><line x1="12" y1="18" x2="12" y2="22"/><line x1="2" y1="12" x2="6" y2="12"/><line x1="18" y1="12" x2="22" y2="12"/><circle cx="12" cy="12" r="3"/></svg>';

/**
 * Create a Leaflet control that recenters the map on the spot location
 * @param {object} options - Configuration options
 * @param {number} options.lat - Spot latitude
 * @param {number} options.lon - Spot longitude
 * @param {number} [options.zoom=13] - Zoom level to reset to
 * @param {string} [options.position='bottomright'] - Control position
 * @returns {L.Control} Leaflet control instance
 */
export function createResetViewControl(options) {
    const {
        lat,
        lon,
        zoom = 13,
        position = 'bottomright'
    } = options;

    const ResetViewControl = L.Control.extend({
        options: {
            position: position
        },

        onAdd: function(map) {
            const container = L.DomUtil.create('div', 'leaflet-bar leaflet-control leaflet-control-reset-view');

            const button = L.DomUtil.create('button', 'reset-view-button', container);
            button.type = 'button';
            button.title = translations.t('mapResetView');
            button.innerHTML = RESET_VIEW_ICON;

            L.DomEvent.on(button, 'click', function(e) {
                L.DomEvent.stopPropagation(e);
                map.setView([lat, lon], zoom);
            });

            L.DomEvent.disableClickPropagation(container);

            return container;
        }
    });

    return new ResetViewControl();
}

/**
 * Update layer switcher labels when language changes
 * Finds all layer switcher dropdowns and updates their option labels
 */
export function updateLayerSwitcherLabels() {
    const dropdowns = document.querySelectorAll('.layer-switcher-dropdown');
    dropdowns.forEach(dropdown => {
        const options = dropdown.querySelectorAll('.layer-switcher-option');
        options.forEach(optionEl => {
            const translationKey = optionEl.dataset.translationKey;
            if (translationKey) {
                optionEl.textContent = translations.t(translationKey);
            }
        });
    });
}

// ============================================================================
// MARKER UTILITIES
// ============================================================================

/**
 * Create a custom marker icon for Leaflet
 * @param {string} [colorClass=''] - CSS class for marker color (e.g., 'custom-marker-red', 'wind-light', 'wind-medium')
 * @returns {L.DivIcon} Leaflet div icon
 */
export function createMarkerIcon(colorClass = '') {
    return L.divIcon({
        className: 'custom-marker-icon',
        html: `<div class="custom-marker ${colorClass}"><div class="marker-dot"></div></div>`,
        iconSize: [18, 18],
        iconAnchor: [9, 9]
    });
}

// ============================================================================
// URL BUILDERS
// ============================================================================

/**
 * Build a Windy.com embed URL for a location
 * @param {number} lat - Latitude
 * @param {number} lon - Longitude
 * @returns {string} Windy embed URL
 */
export function buildWindyEmbedUrl(lat, lon) {
    return `https://embed.windy.com/embed.html?type=map&location=coordinates&metricRain=mm&metricTemp=°C&metricWind=kt&zoom=11&overlay=wind&product=ecmwf&level=surface&lat=${lat}&lon=${lon}&message=true`;
}

// ============================================================================
// WIND OVERLAY (arrows + heatmap)
// Renders wind visualization from the app's own per-spot data (no external
// map embeds). Modes: 'off' | 'arrows' | 'heatmap'.
// ============================================================================

export const WIND_OVERLAY_MODES = ['off', 'arrows', 'heatmap'];

// Upward-pointing arrow (north) used as the base glyph; rotated per wind direction.
const WIND_ARROW_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M12 2l6 9h-4v11h-4V11H6z"/></svg>';

// Colour for wind below the rideable threshold: flat grey (not rideable, so it
// must NOT read as green). Matches the app's "weak" wind class (< 12 kts).
const WIND_FIELD_GRAY = [100, 116, 139];

// Gradient stops for rideable wind (>= 12 kts), in knots, aligned with the
// app's wind classes. Green starts exactly at 12 kts; below that it stays grey.
const WIND_FIELD_STOPS = [
    { kt: 12, rgb: [46, 230, 109] },  // moderate (green)
    { kt: 18, rgb: [245, 158, 11] },  // strong (amber)
    { kt: 25, rgb: [245, 72, 74] },   // extreme (red)
    { kt: 35, rgb: [176, 20, 42] }    // very extreme (deep red)
];

/**
 * Map a wind speed (knots) to an [r,g,b] colour. Below 12 kts the field is grey
 * (not rideable); from 12 kts up it interpolates green -> amber -> red.
 * @param {number} kt - Wind speed in knots
 * @returns {number[]} [r, g, b]
 */
function windFieldColor(kt) {
    const stops = WIND_FIELD_STOPS;
    if (kt < stops[0].kt) return WIND_FIELD_GRAY;
    if (kt >= stops[stops.length - 1].kt) return stops[stops.length - 1].rgb;
    for (let i = 1; i < stops.length; i++) {
        if (kt <= stops[i].kt) {
            const a = stops[i - 1];
            const b = stops[i];
            const f = (kt - a.kt) / (b.kt - a.kt);
            return [
                Math.round(a.rgb[0] + (b.rgb[0] - a.rgb[0]) * f),
                Math.round(a.rgb[1] + (b.rgb[1] - a.rgb[1]) * f),
                Math.round(a.rgb[2] + (b.rgb[2] - a.rgb[2]) * f)
            ];
        }
    }
    return stops[stops.length - 1].rgb;
}

/**
 * Extract a plottable wind sample from a spot.
 * @param {object} spot - Spot object (needs coordinates)
 * @param {function} getConditions - Returns { wind, gusts, direction, isCurrent } or null
 * @returns {{lat:number, lon:number, wind:number, direction:string, isCurrent:boolean}|null}
 */
function getWindSample(spot, getConditions) {
    if (!spot || !spot.coordinates) {
        return null;
    }
    const { lat, lon } = spot.coordinates;
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
        return null;
    }
    const conditions = getConditions ? getConditions(spot) : null;
    if (!conditions || !Number.isFinite(conditions.wind)) {
        return null;
    }
    return {
        lat,
        lon,
        wind: conditions.wind,
        direction: conditions.direction || '',
        isCurrent: !!conditions.isCurrent
    };
}

/**
 * Create a Leaflet layer group of wind arrows for the given spots.
 * Arrows point in the direction the wind blows toward (matching the app's
 * arrow glyphs) and are coloured by wind strength. When a popup builder is
 * provided the arrows act as clickable spot markers (used when the dot markers
 * are hidden); otherwise they render as a non-interactive visual overlay.
 * @param {Array} spots - Spot objects
 * @param {function} getConditions - Returns wind conditions for a spot
 * @param {function} [buildPopup] - Optional (spot) => popup HTML string
 * @returns {L.LayerGroup} Leaflet layer group
 */
export function createWindArrowLayer(spots, getConditions, buildPopup) {
    const group = L.layerGroup();
    if (!Array.isArray(spots)) {
        return group;
    }

    const interactive = typeof buildPopup === 'function';

    spots.forEach(spot => {
        const sample = getWindSample(spot, getConditions);
        if (!sample) {
            return;
        }

        const windClass = weather.getWindClass(sample.wind);
        // App convention: arrow points toward where the wind blows (N wind -> down),
        // so rotate the north-pointing base glyph by 180deg from the "from" angle.
        const rotation = (weather.getWindRotation(sample.direction) + 180) % 360;

        const icon = L.divIcon({
            className: `wind-arrow-icon${interactive ? ' wind-arrow-interactive' : ''}`,
            html: `<div class="wind-arrow-marker ${windClass}" style="transform: rotate(${rotation}deg)">${WIND_ARROW_SVG}</div>`,
            iconSize: [26, 26],
            iconAnchor: [13, 13]
        });

        const marker = L.marker([sample.lat, sample.lon], {
            icon,
            interactive,
            keyboard: false
        });

        if (interactive) {
            marker.bindPopup(buildPopup(spot));
        }

        marker.addTo(group);
    });

    return group;
}

/**
 * Create an interpolated wind-field layer for the given spots.
 *
 * Unlike a density heatmap (which sums overlapping points and therefore lights
 * up wherever spots are clustered), this uses inverse-distance weighting to
 * compute the *average* wind magnitude at each screen pixel. Colour reflects
 * how hard it blows; clustering of spots does not inflate it. Opacity fades
 * toward the edge of each spot's influence radius so isolated spots don't draw
 * hard circles, and does NOT grow with density.
 *
 * Rendered as an L.GridLayer of canvas tiles (handles pan/zoom for free, no
 * external plugin required).
 *
 * @param {Array} spots - Spot objects
 * @param {function} getConditions - Returns wind conditions for a spot
 * @returns {L.GridLayer} Leaflet grid layer
 */
export function createWindHeatLayer(spots, getConditions) {
    const samples = [];
    if (Array.isArray(spots)) {
        spots.forEach(spot => {
            const sample = getWindSample(spot, getConditions);
            if (sample) {
                samples.push(sample);
            }
        });
    }

    const STEP = 8;              // sampling block size in px (divides 256 -> seamless across tiles)
    const MAX_DIST = 70;         // influence radius in px
    const MAX_DIST_SQ = MAX_DIST * MAX_DIST;
    const BASE_ALPHA = 0.6;
    const FADE_FROM = MAX_DIST * 0.5; // full opacity until half the radius, then fade

    const WindFieldLayer = L.GridLayer.extend({
        createTile: function(coords) {
            const tile = document.createElement('canvas');
            const size = this.getTileSize();
            tile.width = size.x;
            tile.height = size.y;

            const map = this._map;
            if (!map || samples.length === 0) {
                return tile;
            }

            const zoom = coords.z;
            const originX = coords.x * size.x;
            const originY = coords.y * size.y;

            // Project samples into layer-pixel space at this zoom; keep only those
            // that can influence this tile (within MAX_DIST of the tile bounds).
            const pts = [];
            for (const s of samples) {
                const p = map.project([s.lat, s.lon], zoom);
                if (p.x < originX - MAX_DIST || p.x > originX + size.x + MAX_DIST ||
                    p.y < originY - MAX_DIST || p.y > originY + size.y + MAX_DIST) {
                    continue;
                }
                pts.push({ x: p.x, y: p.y, wind: s.wind });
            }
            if (pts.length === 0) {
                return tile;
            }

            const ctx = tile.getContext('2d');

            for (let y = 0; y < size.y; y += STEP) {
                for (let x = 0; x < size.x; x += STEP) {
                    const px = originX + x + STEP / 2;
                    const py = originY + y + STEP / 2;

                    let wsum = 0;
                    let vsum = 0;
                    let nearestSq = Infinity;

                    for (let i = 0; i < pts.length; i++) {
                        const dx = pts[i].x - px;
                        const dy = pts[i].y - py;
                        const dSq = dx * dx + dy * dy;
                        if (dSq < nearestSq) {
                            nearestSq = dSq;
                        }
                        if (dSq > MAX_DIST_SQ) {
                            continue;
                        }
                        const w = 1 / (dSq + 1); // inverse-distance, power 2
                        wsum += w;
                        vsum += w * pts[i].wind;
                    }

                    if (wsum === 0) {
                        continue;
                    }

                    const wind = vsum / wsum; // averaged magnitude (density-independent)
                    const nearest = Math.sqrt(nearestSq);
                    const edge = Math.max(0, Math.min(1, (MAX_DIST - nearest) / (MAX_DIST - FADE_FROM)));
                    const alpha = BASE_ALPHA * edge;
                    if (alpha <= 0.01) {
                        continue;
                    }

                    const rgb = windFieldColor(wind);
                    ctx.fillStyle = `rgba(${rgb[0]},${rgb[1]},${rgb[2]},${alpha.toFixed(3)})`;
                    ctx.fillRect(x, y, STEP, STEP);
                }
            }

            return tile;
        }
    });

    return new WindFieldLayer({
        // Render above the base tiles (default GridLayer pane is 'tilePane', which
        // gets covered when the satellite/OSM base layer is swapped).
        pane: 'overlayPane',
        className: 'wind-field-layer',
        updateWhenZooming: false,
        keepBuffer: 2
    });
}

// SVG wind icon for the overlay switcher button.
const WIND_OVERLAY_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8h11a2.5 2.5 0 1 0-2.5-2.5"/><path d="M3 12h15a2.5 2.5 0 1 1-2.5 2.5"/><path d="M3 16h9a2.5 2.5 0 1 1-2.5 2.5"/></svg>';

/**
 * Create a Leaflet control that toggles the wind overlay mode
 * (off -> arrows -> heatmap). Mirrors the layer switcher UI.
 * @param {object} options - Configuration options
 * @param {function} options.getMode - Returns current mode ('off'|'arrows'|'heatmap')
 * @param {function} options.onModeChange - Callback with the new mode
 * @param {string} [options.position='bottomleft'] - Control position
 * @returns {L.Control} Leaflet control instance
 */
export function createWindOverlayControl(options) {
    const {
        getMode,
        onModeChange,
        position = 'bottomleft'
    } = options;

    const modeOptions = [
        { value: 'off', translationKey: 'windOverlayOff' },
        { value: 'arrows', translationKey: 'windOverlayArrows' },
        { value: 'heatmap', translationKey: 'windOverlayHeatmap' }
    ];

    const WindOverlayControl = L.Control.extend({
        options: {
            position: position
        },

        onAdd: function(map) {
            const container = L.DomUtil.create('div', 'leaflet-bar leaflet-control leaflet-control-wind-overlay');

            const button = L.DomUtil.create('button', 'layer-switcher-button', container);
            button.type = 'button';
            button.title = translations.t('windOverlayTooltip');
            button.innerHTML = WIND_OVERLAY_ICON;
            if (getMode() !== 'off') {
                button.classList.add('active');
            }

            const dropdown = L.DomUtil.create('div', 'layer-switcher-dropdown', container);
            dropdown.style.display = 'none';

            modeOptions.forEach(option => {
                const optionEl = L.DomUtil.create('div', 'layer-switcher-option', dropdown);
                optionEl.textContent = translations.t(option.translationKey);
                optionEl.dataset.value = option.value;
                optionEl.dataset.translationKey = option.translationKey;

                if (option.value === getMode()) {
                    optionEl.classList.add('active');
                }

                L.DomEvent.on(optionEl, 'click', function(e) {
                    L.DomEvent.stopPropagation(e);

                    dropdown.querySelectorAll('.layer-switcher-option').forEach(opt => {
                        opt.classList.remove('active');
                    });
                    optionEl.classList.add('active');

                    button.classList.toggle('active', option.value !== 'off');

                    onModeChange(option.value);

                    dropdown.style.display = 'none';
                    button.classList.remove('open');
                });
            });

            L.DomEvent.on(button, 'click', function(e) {
                L.DomEvent.stopPropagation(e);
                const isOpen = dropdown.style.display === 'block';
                dropdown.style.display = isOpen ? 'none' : 'block';
                button.classList.toggle('open', !isOpen);
            });

            L.DomEvent.on(map.getContainer(), 'click', function() {
                dropdown.style.display = 'none';
                button.classList.remove('open');
            });

            L.DomEvent.disableClickPropagation(container);

            return container;
        }
    });

    return new WindOverlayControl();
}

/**
 * Update wind overlay switcher labels when language changes.
 */
export function updateWindOverlayLabels() {
    const dropdowns = document.querySelectorAll('.leaflet-control-wind-overlay .layer-switcher-dropdown');
    dropdowns.forEach(dropdown => {
        dropdown.querySelectorAll('.layer-switcher-option').forEach(optionEl => {
            const translationKey = optionEl.dataset.translationKey;
            if (translationKey) {
                optionEl.textContent = translations.t(translationKey);
            }
        });
    });
    document.querySelectorAll('.leaflet-control-wind-overlay .layer-switcher-button').forEach(button => {
        button.title = translations.t('windOverlayTooltip');
    });
}