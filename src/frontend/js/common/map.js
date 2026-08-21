// ============================================================================
// MAP UTILITIES
// Common map-related functions for OSM and satellite tile layers
// ============================================================================

import * as translations from './translations.js';
import * as weather from './weather.js';

// ============================================================================
// TILE LAYER CONFIGURATIONS
// ============================================================================

const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

const TILE_CONFIGS = {
    satellite: {
        url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        options: {
            attribution: 'Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community',
            maxZoom: 19
        }
    },
    osm: {
        url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        options: {
            attribution: OSM_ATTRIBUTION,
            maxZoom: 19
        }
    },
    osmDark: {
        url: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
        options: {
            attribution: `${OSM_ATTRIBUTION} &copy; <a href="https://carto.com/attributions">CARTO</a>`,
            subdomains: 'abcd',
            maxZoom: 20
        }
    }
};

/**
 * Get tile layer configuration for a given layer type
 * @param {string} layerType - 'satellite', 'osm' or 'osmDark'
 * @returns {object} Configuration object with url and options
 */
export function getMapTileConfig(layerType) {
    return TILE_CONFIGS[layerType] || TILE_CONFIGS.osm;
}

// ============================================================================
// TILE LAYER MANAGEMENT
// ============================================================================

/**
 * Create a tile layer for a Leaflet map
 * @param {string} layerType - 'satellite', 'osm' or 'osmDark'
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
 * @param {string} layerType - 'satellite', 'osm' or 'osmDark'
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
// VIEW UTILITIES
// ============================================================================

// Web Mercator stops at ±85.05°, so nothing is painted beyond that latitude.
const WORLD_EDGE_LAT = 85.05;

/**
 * Zoom in until the viewport no longer reaches past the poles.
 *
 * A world view fitted to the spot bounds can end up taller than the projected
 * world, which leaves an empty stripe above the northernmost tile row. Leaflet
 * only keeps fractional zoom while `zoomSnap` is below 1, so the snap is
 * relaxed for the correction and restored right after: the initial view gets
 * just enough extra zoom to close the gap, while every later zoom step still
 * lands on a crisp integer level.
 *
 * @param {L.Map} map - Leaflet map instance
 * @param {number} [step=0.25] - Zoom increment per iteration
 */
export function zoomToFillWorld(map, step = 0.25) {
    if (!map) {
        return;
    }

    const fitsWorld = () => {
        const bounds = map.getBounds();
        return bounds.getNorth() <= WORLD_EDGE_LAT && bounds.getSouth() >= -WORLD_EDGE_LAT;
    };

    if (fitsWorld()) {
        return;
    }

    const previousSnap = map.options.zoomSnap;
    map.options.zoomSnap = step;

    // A full zoom level doubles the world height, which always closes the gap;
    // the iteration cap is only a safety net.
    const maxSteps = Math.ceil(1 / step);
    for (let i = 0; i < maxSteps && !fitsWorld(); i++) {
        map.setZoom(map.getZoom() + step, { animate: false });
    }

    map.options.zoomSnap = previousSnap;
}

// ============================================================================
// LAYER SWITCHER CONTROL
// ============================================================================

// SVG icon for the layer switcher button
const LAYER_SWITCHER_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="m12,18.838c-.572,0-1.143-.153-1.653-.459L.485,12.462c-.474-.284-.627-.898-.343-1.372.283-.475.897-.627,1.372-.343l9.861,5.917c.385.23.864.23,1.249,0l9.861-5.917c.474-.284,1.088-.131,1.372.343s.131,1.088-.343,1.372l-9.861,5.917c-.51.306-1.082.459-1.653.459Zm1.653,3.836l9.861-5.917c.474-.284.627-.898.343-1.372s-.898-.627-1.372-.343l-9.862,5.917c-.384.23-.863.23-1.248,0L1.515,15.042c-.475-.285-1.089-.131-1.372.343-.284.474-.131,1.088.343,1.372l9.861,5.917c.51.307,1.082.459,1.654.459s1.144-.152,1.653-.459Zm-1.653-16.84l5.308-3.185L13.653.456c-1.02-.612-2.287-.612-3.307,0l-3.655,2.193,5.308,3.185Zm11.515.539l-4.263-2.558-5.308,3.185,5.692,3.415,3.879-2.327c.301-.181.485-.506.485-.857s-.184-.677-.485-.857Zm-13.459.627l-5.308-3.185L.485,6.373c-.301.181-.485.506-.485.857s.184.677.485.857l3.879,2.327,5.692-3.415Zm1.944,1.166l-5.692,3.415,4.039,2.423c.51.306,1.081.459,1.653.459s1.143-.153,1.653-.459l4.039-2.423-5.692-3.415Z"/></svg>';

/**
 * Create a Leaflet layer switcher control
 * @param {object} options - Configuration options
 * @param {function} options.getCurrentLayer - Function that returns current layer type ('satellite', 'osm' or 'osmDark')
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
                { value: 'satellite', translationKey: 'mapLayerSatellite' },
                { value: 'osm', translationKey: 'mapLayerOsm' },
                { value: 'osmDark', translationKey: 'mapLayerOsmDark' }
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
// WIND OVERLAY (arrows + interpolated field)
// Renders wind visualization from the app's own per-spot data (no external
// map embeds). Modes: 'off' | 'arrows' | 'field'.
//
// The 'field' mode draws one overlay in two passes: a colour wash for how hard
// it blows, and animated particles on top for where it blows. Both interpolate
// from the same samples over the same influence radius, so they always describe
// the same patch of water.
// ============================================================================

export const WIND_OVERLAY_MODES = ['off', 'arrows', 'field'];

// Upward-pointing arrow (north) used as the base glyph; rotated per wind direction.
const WIND_ARROW_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M12 2l6 9h-4v11h-4V11H6z"/></svg>';

// Colour for no wind at all (< 5 kts): flat grey. Nothing is happening, so it
// must not compete with the colours that mean something.
const WIND_FIELD_GRAY = [100, 116, 139];

// Gradient stops in knots, cold -> warm. Grey (no wind) fades into blue for wind
// that blows but is not rideable (5-11 kts), then green from exactly 12 kts up
// (aligned with the app's wind classes), through amber to red.
const WIND_FIELD_STOPS = [
    { kt: 5, rgb: [100, 116, 139] },  // calm (grey) - start of the blue ramp
    { kt: 8, rgb: [59, 130, 246] },   // light (blue)
    { kt: 11, rgb: [34, 211, 238] },  // light (cyan), just below rideable
    { kt: 12, rgb: [46, 230, 109] },  // moderate (green)
    { kt: 18, rgb: [245, 158, 11] },  // strong (amber)
    { kt: 25, rgb: [245, 72, 74] },   // extreme (red)
    { kt: 35, rgb: [176, 20, 42] }    // very extreme (deep red)
];

// How far a single spot influences the field, in screen pixels, and the
// fraction of that radius held at full strength before it fades out. Shared by
// the colour wash and the particles so the two passes cover the same ground.
const WIND_FIELD_MAX_DIST = 70;
const WIND_FIELD_FADE_FROM = 0.5;

/**
 * Map a wind speed (knots) to an [r,g,b] colour. Below 5 kts the field is grey
 * (no wind), 5-11 kts ramps through blue (blowing but not rideable), and from
 * 12 kts up it interpolates green -> amber -> red.
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

// ============================================================================
// CLUSTERING
// Spots that sit on top of each other in a zoomed-out view are merged into a
// single marker. Grouping happens in screen pixels, so it dissolves by itself
// as the user zooms in and never depends on the geographic scale.
// ============================================================================

// Spots closer than this many screen pixels are merged into one cluster.
const CLUSTER_RADIUS_PX = 60;

// From this zoom level up every spot is drawn on its own, however close it is.
const CLUSTER_MAX_ZOOM = 10;

// Cardinal directions with a meaningful bearing. An unknown/empty direction is
// skipped when averaging instead of being treated as north.
const CARDINAL_DIRECTIONS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];

/**
 * Group nearby points into clusters based on their distance on screen.
 *
 * Greedy single-pass grouping: each ungrouped point seeds a cluster and takes
 * every remaining point within the pixel radius. With ~100 spots the quadratic
 * scan is far cheaper than maintaining an index, and the result is stable for a
 * given zoom level.
 *
 * @param {L.Map} map - Leaflet map instance (provides the projection and zoom)
 * @param {Array} points - Items to cluster; each needs { lat, lon }
 * @param {object} [options] - { radius, maxZoom }
 * @returns {Array<{lat:number, lon:number, points:Array}>} Clusters (single-point ones included)
 */
export function clusterPoints(map, points, options = {}) {
    const {
        radius = CLUSTER_RADIUS_PX,
        maxZoom = CLUSTER_MAX_ZOOM
    } = options;

    const valid = Array.isArray(points)
        ? points.filter(p => p && Number.isFinite(p.lat) && Number.isFinite(p.lon))
        : [];

    const asSingletons = () => valid.map(p => ({ lat: p.lat, lon: p.lon, points: [p] }));

    if (!map || valid.length === 0) {
        return asSingletons();
    }

    const zoom = map.getZoom();
    if (!Number.isFinite(zoom) || zoom > maxZoom) {
        return asSingletons();
    }

    const projected = valid.map(p => ({ point: p, xy: map.project([p.lat, p.lon], zoom) }));
    const grouped = new Array(projected.length).fill(false);
    const radiusSq = radius * radius;
    const clusters = [];

    for (let i = 0; i < projected.length; i++) {
        if (grouped[i]) {
            continue;
        }
        grouped[i] = true;

        const members = [projected[i]];
        for (let j = i + 1; j < projected.length; j++) {
            if (grouped[j]) {
                continue;
            }
            const dx = projected[j].xy.x - projected[i].xy.x;
            const dy = projected[j].xy.y - projected[i].xy.y;
            if (dx * dx + dy * dy <= radiusSq) {
                grouped[j] = true;
                members.push(projected[j]);
            }
        }

        if (members.length === 1) {
            // Keep the exact position so a lone spot never drifts off its coast.
            clusters.push({ lat: valid[i].lat, lon: valid[i].lon, points: [valid[i]] });
            continue;
        }

        // Centroid in projected space, converted back to lat/lon.
        let sumX = 0;
        let sumY = 0;
        members.forEach(m => {
            sumX += m.xy.x;
            sumY += m.xy.y;
        });
        const center = map.unproject(L.point(sumX / members.length, sumY / members.length), zoom);

        clusters.push({
            lat: center.lat,
            lon: center.lng,
            points: members.map(m => m.point)
        });
    }

    return clusters;
}

/**
 * Vector-average a set of wind directions, weighted by wind speed so the
 * stronger spots dominate the cluster arrow.
 * @param {Array<{wind:number, direction:string}>} samples - Wind samples
 * @returns {number|null} Averaged "wind from" bearing in degrees, or null
 */
function averageWindBearing(samples) {
    let x = 0;
    let y = 0;

    samples.forEach(sample => {
        if (!CARDINAL_DIRECTIONS.includes(sample.direction)) {
            return;
        }
        const rad = weather.getWindRotation(sample.direction) * Math.PI / 180;
        const weight = Math.max(sample.wind, 0.1);
        x += Math.sin(rad) * weight;
        y += Math.cos(rad) * weight;
    });

    if (x === 0 && y === 0) {
        return null;
    }

    return (Math.atan2(x, y) * 180 / Math.PI + 360) % 360;
}

/**
 * Average wind speed of a set of samples.
 * @param {Array<{wind:number}>} samples - Wind samples
 * @returns {number|null} Mean wind speed in knots, or null when there is none
 */
function averageWindSpeed(samples) {
    const winds = samples.map(s => s.wind).filter(Number.isFinite);
    if (winds.length === 0) {
        return null;
    }
    return winds.reduce((sum, w) => sum + w, 0) / winds.length;
}

/**
 * Bubble diameter for a cluster: grows with the number of spots but stays
 * bounded so a dense area never swallows the map.
 * @param {number} count - Number of spots in the cluster
 * @returns {number} Diameter in pixels
 */
function clusterBubbleSize(count) {
    if (count < 5) return 32;
    if (count < 10) return 38;
    if (count < 25) return 44;
    return 50;
}

/**
 * Tooltip text for a cluster marker.
 * @param {number} count - Number of spots in the cluster
 * @returns {string} Localized "N spots" label
 */
function clusterTitle(count) {
    return `${count} ${translations.t('mapClusterSpotsLabel')}`;
}

/**
 * Zoom the map in on the spots behind a cluster marker.
 * @param {L.Map} map - Leaflet map instance
 * @param {Array<{lat:number, lon:number}>} points - Clustered points
 */
function zoomToCluster(map, points) {
    if (!map || points.length === 0) {
        return;
    }

    const bounds = L.latLngBounds(points.map(p => [p.lat, p.lon]));
    if (bounds.getNorth() === bounds.getSouth() && bounds.getEast() === bounds.getWest()) {
        // Spots share the exact same coordinates - bounds have no extent to fit.
        map.setView([points[0].lat, points[0].lon], Math.min(map.getZoom() + 2, map.getMaxZoom() || 19));
        return;
    }

    map.fitBounds(bounds, { padding: [40, 40], maxZoom: CLUSTER_MAX_ZOOM + 2 });
}

/**
 * Create a Leaflet layer group of spot markers (dots), clustered when the map
 * is zoomed out. A cluster renders as a bubble carrying the number of spots,
 * coloured by their average wind; clicking it zooms in on its members.
 * @param {L.Map} map - Leaflet map instance
 * @param {Array} spots - Spot objects
 * @param {function} getConditions - Returns wind conditions for a spot
 * @param {function} [buildPopup] - Optional (spot) => popup HTML string
 * @returns {L.LayerGroup} Leaflet layer group
 */
export function createSpotMarkerLayer(map, spots, getConditions, buildPopup) {
    const group = L.layerGroup();
    if (!Array.isArray(spots)) {
        return group;
    }

    const points = [];
    spots.forEach(spot => {
        if (!spot || !spot.coordinates) {
            return;
        }
        const { lat, lon } = spot.coordinates;
        if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
            return;
        }
        const conditions = getConditions ? getConditions(spot) : null;
        points.push({
            lat,
            lon,
            spot,
            wind: conditions && Number.isFinite(conditions.wind) ? conditions.wind : null
        });
    });

    clusterPoints(map, points).forEach(cluster => {
        if (cluster.points.length === 1) {
            const point = cluster.points[0];
            const windClass = point.wind === null ? 'wind-no-data' : weather.getMapWindClass(point.wind);

            const marker = L.marker([cluster.lat, cluster.lon], {
                icon: createMarkerIcon(windClass)
            });

            if (typeof buildPopup === 'function') {
                marker.bindPopup(buildPopup(point.spot));
            }

            marker.addTo(group);
            return;
        }

        const count = cluster.points.length;
        const avgWind = averageWindSpeed(cluster.points.filter(p => p.wind !== null));
        const windClass = avgWind === null ? 'wind-no-data' : weather.getMapWindClass(avgWind);
        const size = clusterBubbleSize(count);

        const icon = L.divIcon({
            className: 'map-cluster-icon',
            html: `<div class="map-cluster-bubble ${windClass}" style="width:${size}px;height:${size}px">${count}</div>`,
            iconSize: [size, size],
            iconAnchor: [size / 2, size / 2]
        });

        const marker = L.marker([cluster.lat, cluster.lon], {
            icon,
            title: clusterTitle(count),
            keyboard: false
        });
        marker.on('click', () => zoomToCluster(map, cluster.points));
        marker.addTo(group);
    });

    return group;
}

/**
 * Create a Leaflet layer group of wind arrows for the given spots.
 * Arrows point in the direction the wind blows toward (matching the app's
 * arrow glyphs) and are coloured by wind strength. When the map is zoomed out,
 * nearby spots collapse into a single averaged arrow badged with the number of
 * spots behind it. When a popup builder is provided the arrows act as clickable
 * spot markers (used when the dot markers are hidden); otherwise they render as
 * a non-interactive visual overlay.
 * @param {L.Map} map - Leaflet map instance
 * @param {Array} spots - Spot objects
 * @param {function} getConditions - Returns wind conditions for a spot
 * @param {function} [buildPopup] - Optional (spot) => popup HTML string
 * @returns {L.LayerGroup} Leaflet layer group
 */
export function createWindArrowLayer(map, spots, getConditions, buildPopup) {
    const group = L.layerGroup();
    if (!Array.isArray(spots)) {
        return group;
    }

    const interactive = typeof buildPopup === 'function';

    const samples = [];
    spots.forEach(spot => {
        const sample = getWindSample(spot, getConditions);
        if (sample) {
            samples.push({ ...sample, spot });
        }
    });

    clusterPoints(map, samples).forEach(cluster => {
        // App convention: arrow points toward where the wind blows (N wind -> down),
        // so rotate the north-pointing base glyph by 180deg from the "from" angle.
        if (cluster.points.length === 1) {
            const sample = cluster.points[0];
            const windClass = weather.getMapWindClass(sample.wind);
            const rotation = (weather.getWindRotation(sample.direction) + 180) % 360;

            const icon = L.divIcon({
                className: `wind-arrow-icon${interactive ? ' wind-arrow-interactive' : ''}`,
                html: `<div class="wind-arrow-marker ${windClass}" style="transform: rotate(${rotation}deg)">${WIND_ARROW_SVG}</div>`,
                iconSize: [26, 26],
                iconAnchor: [13, 13]
            });

            const marker = L.marker([cluster.lat, cluster.lon], {
                icon,
                interactive,
                keyboard: false
            });

            if (interactive) {
                marker.bindPopup(buildPopup(sample.spot));
            }

            marker.addTo(group);
            return;
        }

        const count = cluster.points.length;
        const avgWind = averageWindSpeed(cluster.points);
        const windClass = weather.getMapWindClass(avgWind);
        const bearing = averageWindBearing(cluster.points);
        const rotation = bearing === null ? 0 : (bearing + 180) % 360;
        const arrowStyle = bearing === null ? '' : ` style="transform: rotate(${rotation.toFixed(1)}deg)"`;

        const icon = L.divIcon({
            className: `wind-arrow-icon${interactive ? ' wind-arrow-interactive' : ''} wind-arrow-cluster-icon`,
            html: `<div class="wind-arrow-cluster ${windClass}">`
                + `<div class="wind-arrow-marker ${windClass}"${arrowStyle}>${WIND_ARROW_SVG}</div>`
                + `<span class="wind-arrow-cluster-count">${count}</span>`
                + `</div>`,
            iconSize: [34, 34],
            iconAnchor: [17, 17]
        });

        const marker = L.marker([cluster.lat, cluster.lon], {
            icon,
            interactive: true,
            title: clusterTitle(count),
            keyboard: false
        });
        marker.on('click', () => zoomToCluster(map, cluster.points));
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
    const MAX_DIST = WIND_FIELD_MAX_DIST;
    const MAX_DIST_SQ = MAX_DIST * MAX_DIST;
    const BASE_ALPHA = 0.6;
    const FADE_FROM = MAX_DIST * WIND_FIELD_FADE_FROM;

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

// ============================================================================
// WIND PARTICLES
// The moving half of the field overlay: animated streaklines drifting along the
// same inverse-distance interpolation the colour wash paints, the way
// Windfinder/Windy animate their maps. Direction is interpolated too (as a
// vector), so particles bend between neighbouring spots instead of jumping.
// ============================================================================

// Vector-field resolution in screen pixels. Small enough that trajectories look
// smooth after bilinear sampling, coarse enough to rebuild in a few ms.
const PARTICLE_GRID_STEP = 16;

// Particles die where the field is this weak - i.e. far from every spot.
const PARTICLE_MIN_FIELD = 0.05;

// Screen pixels travelled per knot per frame (at 60 fps).
const PARTICLE_SPEED = 0.09;

// Frames a particle lives before it respawns somewhere else. Randomised per
// particle so the whole field never blinks at once.
const PARTICLE_LIFE = 60;

// Per-frame alpha erased from the canvas; controls how long the trails linger.
const PARTICLE_TRAIL_FADE = 0.12;

// Particle budget: scaled by the number of spots in view so a single visible
// spot doesn't get a dense blob crammed into its influence radius.
const PARTICLES_PER_SPOT = 18;
const PARTICLES_MIN = 40;
const PARTICLES_MAX = 1400;

// The canvas is grown beyond the viewport so a short pan (during which the
// animation is frozen) doesn't reveal an empty edge.
const PARTICLE_CANVAS_MARGIN = 96;

// Frames advected for the single static frame drawn under reduced motion.
const PARTICLE_STATIC_STEPS = 26;

// How far the streak colour is pushed toward white. The wash underneath already
// carries the speed as hue, so the particles only have to stay legible on top of
// it - lightness, not hue, is what separates the two passes.
const PARTICLE_LIGHTEN = 0.65;

// Streak width, and the darker halo stroked underneath it so the light streaks
// survive a pale base map as well as the satellite one.
const PARTICLE_WIDTH = 1.4;
const PARTICLE_HALO_WIDTH = 3;
const PARTICLE_HALO_ALPHA = 0.35;

/**
 * Particle colour for a wind speed: the field palette lightened toward white.
 * @param {number} kt - Wind speed in knots
 * @returns {number[]} [r, g, b]
 */
function windParticleColor(kt) {
    const rgb = windFieldColor(kt);
    return [
        Math.round(rgb[0] + (255 - rgb[0]) * PARTICLE_LIGHTEN),
        Math.round(rgb[1] + (255 - rgb[1]) * PARTICLE_LIGHTEN),
        Math.round(rgb[2] + (255 - rgb[2]) * PARTICLE_LIGHTEN)
    ];
}

/**
 * Project wind samples into canvas pixel space and turn each into a velocity
 * vector. Samples without a usable cardinal direction are dropped - guessing
 * north for them would drag the whole neighbourhood the wrong way.
 * @param {L.Map} map - Leaflet map instance
 * @param {Array} samples - Wind samples ({ lat, lon, wind, direction })
 * @param {{x:number, y:number}} size - Canvas size in pixels
 * @param {number} margin - Offset between container and canvas coordinates
 * @returns {Array<{x:number, y:number, u:number, v:number}>} Velocity points
 */
function projectWindVectors(map, samples, size, margin) {
    const points = [];

    samples.forEach(sample => {
        if (!CARDINAL_DIRECTIONS.includes(sample.direction)) {
            return;
        }

        const projected = map.latLngToContainerPoint([sample.lat, sample.lon]);
        const x = projected.x + margin;
        const y = projected.y + margin;
        if (x < -WIND_FIELD_MAX_DIST || x > size.x + WIND_FIELD_MAX_DIST ||
            y < -WIND_FIELD_MAX_DIST || y > size.y + WIND_FIELD_MAX_DIST) {
            return;
        }

        // App convention: the wind direction names where the wind comes FROM,
        // so particles have to travel along the opposite bearing.
        const rad = ((weather.getWindRotation(sample.direction) + 180) % 360) * Math.PI / 180;
        points.push({
            x,
            y,
            u: Math.sin(rad) * sample.wind,
            v: -Math.cos(rad) * sample.wind
        });
    });

    return points;
}

/**
 * Build the interpolated velocity field covering the canvas.
 *
 * Each grid node averages the surrounding velocity vectors with inverse-distance
 * weighting, and carries a strength in [0,1] that fades toward the edge of the
 * sampled area. Vectors are averaged as components, so opposing winds cancel
 * into a calm zone instead of averaging into a meaningless middle bearing.
 *
 * @param {Array} points - Velocity points from projectWindVectors
 * @param {{x:number, y:number}} size - Canvas size in pixels
 * @returns {{cols:number, rows:number, u:Float32Array, v:Float32Array, strength:Float32Array}}
 */
function buildWindField(points, size) {
    const cols = Math.ceil(size.x / PARTICLE_GRID_STEP) + 1;
    const rows = Math.ceil(size.y / PARTICLE_GRID_STEP) + 1;
    const u = new Float32Array(cols * rows);
    const v = new Float32Array(cols * rows);
    const strength = new Float32Array(cols * rows);
    const maxDistSq = WIND_FIELD_MAX_DIST * WIND_FIELD_MAX_DIST;
    const fadeSpan = WIND_FIELD_MAX_DIST * (1 - WIND_FIELD_FADE_FROM);

    for (let row = 0; row < rows; row++) {
        for (let col = 0; col < cols; col++) {
            const px = col * PARTICLE_GRID_STEP;
            const py = row * PARTICLE_GRID_STEP;

            let weightSum = 0;
            let uSum = 0;
            let vSum = 0;
            let nearestSq = Infinity;

            for (let i = 0; i < points.length; i++) {
                const dx = points[i].x - px;
                const dy = points[i].y - py;
                const distSq = dx * dx + dy * dy;
                if (distSq < nearestSq) {
                    nearestSq = distSq;
                }
                if (distSq > maxDistSq) {
                    continue;
                }
                const weight = 1 / (distSq + 1); // inverse-distance, power 2
                weightSum += weight;
                uSum += weight * points[i].u;
                vSum += weight * points[i].v;
            }

            if (weightSum === 0) {
                continue;
            }

            const index = row * cols + col;
            u[index] = uSum / weightSum;
            v[index] = vSum / weightSum;

            const nearest = Math.sqrt(nearestSq);
            strength[index] = Math.max(0, Math.min(1, (WIND_FIELD_MAX_DIST - nearest) / fadeSpan));
        }
    }

    return { cols, rows, u, v, strength };
}

/**
 * Bilinearly sample the velocity field at a pixel position.
 * @param {object} field - Field from buildWindField
 * @param {number} x - Canvas x coordinate
 * @param {number} y - Canvas y coordinate
 * @param {number[]} out - Reused [u, v, strength] output array
 * @returns {boolean} False when the position lies outside the field
 */
function sampleWindField(field, x, y, out) {
    const gx = x / PARTICLE_GRID_STEP;
    const gy = y / PARTICLE_GRID_STEP;
    const col = Math.floor(gx);
    const row = Math.floor(gy);

    if (col < 0 || row < 0 || col + 1 >= field.cols || row + 1 >= field.rows) {
        return false;
    }

    const fx = gx - col;
    const fy = gy - row;
    const i00 = row * field.cols + col;
    const i10 = i00 + 1;
    const i01 = i00 + field.cols;
    const i11 = i01 + 1;

    const w00 = (1 - fx) * (1 - fy);
    const w10 = fx * (1 - fy);
    const w01 = (1 - fx) * fy;
    const w11 = fx * fy;

    out[0] = field.u[i00] * w00 + field.u[i10] * w10 + field.u[i01] * w01 + field.u[i11] * w11;
    out[1] = field.v[i00] * w00 + field.v[i10] * w10 + field.v[i01] * w01 + field.v[i11] * w11;
    out[2] = field.strength[i00] * w00 + field.strength[i10] * w10
        + field.strength[i01] * w01 + field.strength[i11] * w11;

    return true;
}

/**
 * Pick a random spawn position inside some spot's influence radius.
 *
 * Spawning uniformly across the canvas would waste most particles on empty
 * water, so a random spot is picked first and the particle is dropped in its
 * disc (square-rooted radius keeps the distribution even across the disc).
 *
 * @param {Array} points - Velocity points from projectWindVectors
 * @param {object} particle - Particle to reposition (mutated)
 */
function respawnParticle(points, particle) {
    const origin = points[Math.floor(Math.random() * points.length)];
    const angle = Math.random() * Math.PI * 2;
    const radius = Math.sqrt(Math.random()) * WIND_FIELD_MAX_DIST;

    particle.x = origin.x + Math.cos(angle) * radius;
    particle.y = origin.y + Math.sin(angle) * radius;
    particle.age = Math.floor(Math.random() * PARTICLE_LIFE);
}

/**
 * Create an animated wind-particle layer for the given spots.
 *
 * Particles are advected through the interpolated wind field on a canvas that
 * covers the viewport, leaving fading trails. Meant to be stacked on top of
 * createWindHeatLayer: both read the same samples over the same radius, and the
 * streaks are drawn in a lightened version of the wash's own palette so they
 * stay legible against it.
 *
 * The animation is frozen while the map is being panned or zoomed (the canvas
 * rides along with the overlay pane, keeping the frozen frame geographically
 * anchored) and the field is rebuilt once the view settles. Under
 * `prefers-reduced-motion` a single static frame of streaklines is drawn.
 *
 * @param {Array} spots - Spot objects
 * @param {function} getConditions - Returns wind conditions for a spot
 * @returns {L.Layer} Leaflet layer
 */
export function createWindParticleLayer(spots, getConditions) {
    const samples = [];
    if (Array.isArray(spots)) {
        spots.forEach(spot => {
            const sample = getWindSample(spot, getConditions);
            if (sample) {
                samples.push(sample);
            }
        });
    }

    const prefersReducedMotion = typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const WindParticleLayer = L.Layer.extend({
        onAdd: function(map) {
            this._canvas = L.DomUtil.create('canvas', 'wind-particle-layer leaflet-zoom-hide');
            this._ctx = this._canvas.getContext('2d');
            map.getPane('overlayPane').appendChild(this._canvas);

            map.on('moveend zoomend resize', this._restart, this);
            map.on('movestart zoomstart', this._freeze, this);

            this._restart();
        },

        onRemove: function(map) {
            this._freeze();
            map.off('moveend zoomend resize', this._restart, this);
            map.off('movestart zoomstart', this._freeze, this);

            if (this._canvas && this._canvas.parentNode) {
                this._canvas.parentNode.removeChild(this._canvas);
            }
            this._canvas = null;
            this._ctx = null;
        },

        // Stop the loop without touching the canvas: the drawn frame stays put
        // and is carried along by the overlay pane while the map moves.
        _freeze: function() {
            if (this._frame) {
                L.Util.cancelAnimFrame(this._frame);
                this._frame = null;
            }
            if (this._pendingRebuild) {
                L.Util.cancelAnimFrame(this._pendingRebuild);
                this._pendingRebuild = null;
            }
        },

        // A single gesture fires several view events (a zoom ends with both
        // 'zoomend' and 'moveend'), so the rebuild is deferred by a frame and
        // collapses into one.
        _restart: function() {
            this._freeze();
            this._pendingRebuild = L.Util.requestAnimFrame(this._rebuild, this);
        },

        // Re-measure, re-anchor, rebuild the field and refill the particles.
        _rebuild: function() {
            this._pendingRebuild = null;

            const map = this._map;
            if (!map || !this._canvas) {
                return;
            }

            const viewport = map.getSize();
            const margin = PARTICLE_CANVAS_MARGIN;
            const size = { x: viewport.x + margin * 2, y: viewport.y + margin * 2 };

            this._canvas.width = size.x;
            this._canvas.height = size.y;
            L.DomUtil.setPosition(this._canvas, map.containerPointToLayerPoint([-margin, -margin]));
            this._ctx.clearRect(0, 0, size.x, size.y);

            this._size = size;
            this._points = projectWindVectors(map, samples, size, margin);
            if (this._points.length === 0) {
                this._particles = [];
                return;
            }

            this._field = buildWindField(this._points, size);

            const count = Math.max(
                PARTICLES_MIN,
                Math.min(PARTICLES_MAX, this._points.length * PARTICLES_PER_SPOT)
            );
            this._particles = [];
            for (let i = 0; i < count; i++) {
                const particle = { x: 0, y: 0, age: 0 };
                respawnParticle(this._points, particle);
                this._particles.push(particle);
            }

            if (prefersReducedMotion) {
                this._drawStaticFrame();
                return;
            }

            this._lastFrameTime = 0;
            this._frame = L.Util.requestAnimFrame(this._step, this);
        },

        _step: function() {
            if (!this._ctx || !this._field) {
                return;
            }

            // Keep the motion frame-rate independent; the clamp stops a long
            // stall (background tab, GC pause) from teleporting every particle.
            const now = performance.now();
            const elapsed = this._lastFrameTime ? now - this._lastFrameTime : 16.67;
            this._lastFrameTime = now;
            const dt = Math.min(Math.max(elapsed, 8), 50) / 16.67;

            this._fadeTrails();
            this._advance(dt, true);

            this._frame = L.Util.requestAnimFrame(this._step, this);
        },

        // Erase a slice of the previous frame instead of clearing it, which is
        // what leaves the comet-like trails behind the particles.
        _fadeTrails: function() {
            const ctx = this._ctx;
            ctx.globalCompositeOperation = 'destination-out';
            ctx.fillStyle = `rgba(0,0,0,${PARTICLE_TRAIL_FADE})`;
            ctx.fillRect(0, 0, this._size.x, this._size.y);
            ctx.globalCompositeOperation = 'source-over';
        },

        /**
         * Advance every particle one step and draw the segment it travelled.
         * @param {number} dt - Frame time in 60fps units
         * @param {boolean} allowRespawn - Whether exhausted particles are recycled
         */
        _advance: function(dt, allowRespawn) {
            const ctx = this._ctx;
            const sample = this._sampleOut || (this._sampleOut = [0, 0, 0]);

            ctx.lineCap = 'round';

            for (let i = 0; i < this._particles.length; i++) {
                const particle = this._particles[i];

                const inside = sampleWindField(this._field, particle.x, particle.y, sample);
                if (!inside || sample[2] < PARTICLE_MIN_FIELD || particle.age++ > PARTICLE_LIFE) {
                    if (allowRespawn) {
                        respawnParticle(this._points, particle);
                    }
                    continue;
                }

                const x = particle.x + sample[0] * PARTICLE_SPEED * dt;
                const y = particle.y + sample[1] * PARTICLE_SPEED * dt;

                const speed = Math.sqrt(sample[0] * sample[0] + sample[1] * sample[1]);
                const rgb = windParticleColor(speed);
                // Fade with the field so particles dissolve at its edge rather
                // than stopping dead on an invisible boundary.
                const strength = Math.min(1, sample[2]);
                const alpha = (0.35 + 0.5 * strength).toFixed(3);
                const haloAlpha = (PARTICLE_HALO_ALPHA * strength).toFixed(3);

                // The same segment stroked twice: a dark halo first, the light
                // streak over it. The halo is what keeps the streaks readable on
                // a pale base map, where the colour wash alone is too light to
                // separate them; on the satellite map it barely shows.
                ctx.beginPath();
                ctx.moveTo(particle.x, particle.y);
                ctx.lineTo(x, y);

                ctx.lineWidth = PARTICLE_HALO_WIDTH;
                ctx.strokeStyle = `rgba(15,23,42,${haloAlpha})`;
                ctx.stroke();

                ctx.lineWidth = PARTICLE_WIDTH;
                ctx.strokeStyle = `rgba(${rgb[0]},${rgb[1]},${rgb[2]},${alpha})`;
                ctx.stroke();

                particle.x = x;
                particle.y = y;
            }
        },

        // Reduced-motion fallback: draw the streaklines once, no animation.
        _drawStaticFrame: function() {
            for (let step = 0; step < PARTICLE_STATIC_STEPS; step++) {
                this._advance(1, false);
            }
        }
    });

    return new WindParticleLayer();
}

// SVG wind icon for the overlay switcher button.
const WIND_OVERLAY_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8h11a2.5 2.5 0 1 0-2.5-2.5"/><path d="M3 12h15a2.5 2.5 0 1 1-2.5 2.5"/><path d="M3 16h9a2.5 2.5 0 1 1-2.5 2.5"/></svg>';

/**
 * Create a Leaflet control that toggles the wind overlay mode
 * (off -> arrows -> field). Mirrors the layer switcher UI.
 * @param {object} options - Configuration options
 * @param {function} options.getMode - Returns current mode ('off'|'arrows'|'field')
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
        { value: 'field', translationKey: 'windOverlayField' }
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