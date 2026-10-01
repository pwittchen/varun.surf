// ============================================================================
// GLOBE
// The map view's spots and wind field, drawn on a rotating planet instead of a
// flat projection. Desktop only: dragging a sphere around with a thumb while
// the page scrolls under it is no way to read a forecast, and a phone has the
// map for that.
//
// Everything is painted on three stacked canvases:
//   - base:      the sphere, land, borders and the interpolated wind wash
//   - particles: animated streaklines, faded frame by frame into trails
//   - overlay:   spot dots and cluster bubbles, drawn last so nothing covers them
//
// The globe is meant to read exactly like the map, so whatever the map decides
// in screen pixels is decided here in screen pixels too, through the map's own
// constants: how far a spot's wind reaches, how fast and how long a particle
// travels, how many particles a spot gets, how close two spots have to be to
// merge into a cluster and from which zoom they stop merging. To compare zooms
// the globe is given an "equivalent" Leaflet zoom - the one at which the flat
// map's world is as wide as the globe's circumference.
//
// d3-geo (for clipping land at the horizon) and the world-atlas outlines are
// loaded on first use, so a visitor who never opens the globe never pays for
// them. The finer 1:50m outlines follow once the globe is zoomed in.
// ============================================================================

import * as translations from './translations.js';
import * as weather from './weather.js';
import {
    CARDINAL_DIRECTIONS,
    CLUSTER_MAX_ZOOM,
    CLUSTER_RADIUS_PX,
    PARTICLE_HALO_ALPHA,
    PARTICLE_HALO_WIDTH,
    PARTICLE_LIFE,
    PARTICLE_MIN_FIELD,
    PARTICLE_SPEED,
    PARTICLE_STATIC_STEPS,
    PARTICLE_TRAIL_FADE,
    PARTICLE_WIDTH,
    PARTICLES_MAX,
    PARTICLES_MIN,
    PARTICLES_PER_SPOT,
    WIND_FIELD_FADE_FROM,
    WIND_FIELD_MAX_DIST,
    averageWindSpeed,
    clusterBubbleSize,
    getWindSample,
    windFieldColor,
    windFieldRadius,
    windParticleColor
} from './map.js';

// ----------------------------------------------------------------------------
// External dependencies, loaded on first use
// ----------------------------------------------------------------------------

const SCRIPT_URLS = [
    'https://unpkg.com/d3-array@3.2.4/dist/d3-array.min.js',
    'https://unpkg.com/d3-geo@3.1.1/dist/d3-geo.min.js',
    'https://unpkg.com/topojson-client@3.1.0/dist/topojson-client.min.js'
];

const WORLD_ATLAS_URL = 'https://unpkg.com/world-atlas@2.0.2/countries-110m.json';
const WORLD_ATLAS_DETAILED_URL = 'https://unpkg.com/world-atlas@2.0.2/countries-50m.json';

let scriptsPromise = null;
let outlinesPromise = null;
let detailedOutlinesPromise = null;

function loadScript(src) {
    return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = src;
        script.async = false;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error(`Failed to load ${src}`));
        document.head.appendChild(script);
    });
}

// d3-geo needs d3-array on the page before it runs, so the scripts go in one
// after the other rather than all at once. A failed load is forgotten, so the
// next visit to the globe tries again.
function loadScripts() {
    if (!scriptsPromise) {
        scriptsPromise = SCRIPT_URLS
            .reduce((chain, src) => chain.then(() => loadScript(src)), Promise.resolve())
            .catch(error => {
                scriptsPromise = null;
                throw error;
            });
    }
    return scriptsPromise;
}

/**
 * Fetch a world-atlas topology and turn it into GeoJSON land and border mesh.
 * @param {string} url - Topology URL
 * @returns {Promise<{land:object, borders:object}>}
 */
function loadOutlines(url) {
    const topology = fetch(url).then(response => {
        if (!response.ok) {
            throw new Error(`World atlas: HTTP ${response.status}`);
        }
        return response.json();
    });

    return Promise.all([loadScripts(), topology]).then(([, data]) => ({
        land: window.topojson.feature(data, data.objects.land),
        borders: window.topojson.mesh(data, data.objects.countries, (a, b) => a !== b)
    }));
}

function loadCoarseOutlines() {
    if (!outlinesPromise) {
        outlinesPromise = loadOutlines(WORLD_ATLAS_URL).catch(error => {
            outlinesPromise = null;
            throw error;
        });
    }
    return outlinesPromise;
}

function loadDetailedOutlines() {
    if (!detailedOutlinesPromise) {
        detailedOutlinesPromise = loadOutlines(WORLD_ATLAS_DETAILED_URL).catch(error => {
            detailedOutlinesPromise = null;
            throw error;
        });
    }
    return detailedOutlinesPromise;
}

// ----------------------------------------------------------------------------
// Tuning
// ----------------------------------------------------------------------------

const DEG = Math.PI / 180;

// Leaflet's world is 256 px wide at zoom 0 and doubles per level
const LEAFLET_TILE_SIZE = 256;

// Globe radius at zoom 1, as a fraction of the shorter side of the viewport
const BASE_RADIUS_FRACTION = 0.42;

// Zoom limits. The globe can be pulled a little further out than a whole
// planet, and taken in as far as the map's cluster zoom plus a few levels, so
// every cluster can be opened up into its spots the way it can on the map.
const MIN_ZOOM = 0.8;
const MAX_EQUIVALENT_ZOOM = 13;

// One wheel notch, one zoom button press or a double click: one map zoom level
const ZOOM_STEP_FACTOR = 2;
const WHEEL_DELTA_PER_LEVEL = 100;

// From this equivalent zoom on, the coarse outlines look blocky and the 1:50m
// set is swapped in
const DETAILED_OUTLINES_FROM_ZOOM = 3.5;

// Framing a cluster: the map fits its spots with 40 px of padding and stops two
// levels past the cluster zoom. A country filter is framed a bit wider.
const CLUSTER_FIT_PADDING_PX = 40;
const CLUSTER_FIT_MAX_ZOOM = CLUSTER_MAX_ZOOM + 2;
const FILTER_FIT_PADDING_PX = 60;
const FILTER_FIT_MAX_ZOOM = 9;

// A filter whose spots spread wider than this is shown as the whole planet
const WHOLE_GLOBE_SPREAD_DEG = 70;

// The view never tips far enough to put a pole at the centre: the rotation
// would get twitchy there, and nobody kites at the pole.
const MAX_TILT = 75;

// Idle spin, in degrees of longitude per second at zoom 1 - one turn in about
// 2.5 min. It slows down as the globe is zoomed in, so a zoomed-in coastline
// drifts past at the pace the whole planet would.
const AUTO_ROTATE_DEG_PER_SEC = 2.4;

// After the visitor lets go, the spin waits this long before it picks up again
const AUTO_ROTATE_RESUME_MS = 4000;

// A drag released with some speed keeps turning the globe and slows down by
// this factor per frame
const INERTIA_DECAY = 0.92;

const FLY_DURATION_MS = 1200;

// Where the globe looks first when there is no filter to centre on
const DEFAULT_CENTER = { lon: 10, lat: 35 };

// The wash is sampled every few screen pixels into a small canvas that is then
// stretched over the globe with smoothing - the same coarse sampling and blur
// the map's colour pass uses
const WASH_STEP = 6;
const WASH_ALPHA = 0.6;

// Map marker geometry: a 14 px dot inside a 2 px border
const DOT_RADIUS = 8;
const DOT_BORDER = 2;
const DOT_HIT_RADIUS = 10;

// Canvases are drawn at the device pixel ratio, capped: a 3x canvas the size of
// the content column is a lot of pixels to repaint every frame
const MAX_PIXEL_RATIO = 2;

const ROTATE_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7"/><polyline points="21 3 21 9 15 9"/></svg>';
const RESET_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="2" x2="12" y2="6"/><line x1="12" y1="18" x2="12" y2="22"/><line x1="2" y1="12" x2="6" y2="12"/><line x1="18" y1="12" x2="22" y2="12"/><circle cx="12" cy="12" r="3"/></svg>';
const WIND_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8h11a2.5 2.5 0 1 0-2.5-2.5"/><path d="M3 12h15a2.5 2.5 0 1 1-2.5 2.5"/><path d="M3 16h9a2.5 2.5 0 1 1-2.5 2.5"/></svg>';
const SPOTS_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z"/><circle cx="12" cy="10" r="3"/></svg>';
const CLOSE_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="5" y1="5" x2="19" y2="19"/><line x1="19" y1="5" x2="5" y2="19"/></svg>';

// ----------------------------------------------------------------------------
// Theme
// ----------------------------------------------------------------------------

const PALETTES = {
    dark: {
        oceanInner: '#123049',
        oceanOuter: '#07121d',
        land: '#26323b',
        landStroke: 'rgba(255,255,255,0.10)',
        borders: 'rgba(255,255,255,0.10)',
        graticule: 'rgba(255,255,255,0.05)',
        atmosphere: '34,195,230',
        limb: 'rgba(0,0,0,0.45)',
        highlight: 'rgba(255,255,255,0.06)'
    },
    light: {
        oceanInner: '#d6ecf6',
        oceanOuter: '#a9d1e4',
        land: '#f5f2ea',
        landStroke: 'rgba(0,0,0,0.12)',
        borders: 'rgba(0,0,0,0.12)',
        graticule: 'rgba(0,0,0,0.06)',
        atmosphere: '8,145,178',
        limb: 'rgba(15,40,60,0.18)',
        highlight: 'rgba(255,255,255,0.35)'
    }
};

const WIND_CLASS_VARS = {
    'wind-calm': '--wind-calm',
    'wind-light': '--wind-light',
    'wind-weak': '--wind-weak',
    'wind-moderate': '--wind-moderate',
    'wind-strong': '--wind-strong',
    'wind-extreme': '--wind-extreme',
    'wind-no-data': '--marker-no-data'
};

function currentTheme() {
    return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
}

// Dot and bubble colours come from the stylesheet, so the globe's markers are
// the map's markers
function readMarkerColors(element) {
    const style = getComputedStyle(element);
    const colors = {};
    Object.entries(WIND_CLASS_VARS).forEach(([windClass, cssVar]) => {
        colors[windClass] = style.getPropertyValue(cssVar).trim() || '#9ca3af';
    });
    colors.border = style.getPropertyValue('--marker-border').trim() || '#ffffff';
    colors.font = style.fontFamily || 'Inter, system-ui, sans-serif';
    return colors;
}

// ----------------------------------------------------------------------------
// Geometry
// ----------------------------------------------------------------------------

function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

function normalizeLon(lon) {
    return ((lon % 360) + 540) % 360 - 180;
}

function easeInOutCubic(t) {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function unitVector(lat, lon) {
    const phi = lat * DEG;
    const lambda = lon * DEG;
    return [Math.cos(phi) * Math.cos(lambda), Math.cos(phi) * Math.sin(lambda), Math.sin(phi)];
}

/**
 * Centre and spread of a set of points on the sphere: the mean of their unit
 * vectors, and the largest angle between it and any point.
 * @param {Array<{lat:number, lon:number}>} points - Points to frame
 * @returns {{lat:number, lon:number, spread:number}|null} Spread in degrees
 */
function sphericalFrame(points) {
    if (points.length === 0) {
        return null;
    }

    let x = 0;
    let y = 0;
    let z = 0;
    const vectors = points.map(p => unitVector(p.lat, p.lon));
    vectors.forEach(v => {
        x += v[0];
        y += v[1];
        z += v[2];
    });

    const length = Math.sqrt(x * x + y * y + z * z);
    if (length < 1e-6) {
        // Points spread evenly round the planet have no centre to look at
        return { ...DEFAULT_CENTER, spread: 180 };
    }
    x /= length;
    y /= length;
    z /= length;

    let spread = 0;
    vectors.forEach(v => {
        const dot = v[0] * x + v[1] * y + v[2] * z;
        spread = Math.max(spread, Math.acos(clamp(dot, -1, 1)) / DEG);
    });

    return { lat: Math.asin(z) / DEG, lon: Math.atan2(y, x) / DEG, spread };
}

// ----------------------------------------------------------------------------
// Wind field
// ----------------------------------------------------------------------------

/**
 * Index the samples in lat/lon buckets one influence radius wide, so a query
 * only looks at the spots that can reach it.
 * @param {Array} samples - Wind samples with east/north components
 * @param {number} radiusDeg - Influence radius in degrees of latitude
 * @param {number} pxPerDeg - Screen pixels per degree at the globe's centre
 * @returns {object} Field index
 */
function buildFieldIndex(samples, radiusDeg, pxPerDeg) {
    const cell = Math.max(radiusDeg, 0.01);
    const cols = Math.ceil(360 / cell);
    const rows = Math.ceil(180 / cell) + 1;
    const buckets = new Map();

    samples.forEach(sample => {
        const row = clamp(Math.floor((sample.lat + 90) / cell), 0, rows - 1);
        const col = clamp(Math.floor((normalizeLon(sample.lon) + 180) / cell), 0, cols - 1);
        const key = row * cols + col;
        let bucket = buckets.get(key);
        if (!bucket) {
            bucket = [];
            buckets.set(key, bucket);
        }
        bucket.push(sample);
    });

    return { cell, cols, rows, buckets, radiusDeg, pxPerDeg };
}

/**
 * Interpolate the wind at a point, the way the map does at a pixel: inverse
 * distance weighting (power 2, in screen pixels) over every spot within reach,
 * with a strength that fades toward the edge of the reach. The magnitude is
 * averaged over every spot (the colour wash), the direction over the spots
 * with a usable bearing (the particles), each with its own strength.
 *
 * @param {object} index - Field index from buildFieldIndex
 * @param {number} lon - Longitude
 * @param {number} lat - Latitude
 * @param {number[]} out - Reused [mag, u, v, washStrength, particleStrength]
 * @returns {boolean} False when no spot reaches the point
 */
function sampleField(index, lon, lat, out) {
    out[0] = 0;
    out[1] = 0;
    out[2] = 0;
    out[3] = 0;
    out[4] = 0;

    if (!index) {
        return false;
    }

    const { cell, cols, rows, buckets, radiusDeg, pxPerDeg } = index;
    const lonN = normalizeLon(lon);
    const row = Math.floor((lat + 90) / cell);
    const col = Math.floor((lonN + 180) / cell);
    const cosLat = Math.max(0.05, Math.cos(lat * DEG));
    // A degree of longitude shrinks toward the poles, so the search widens
    const span = Math.min(Math.ceil(1 / cosLat), Math.ceil(cols / 2));
    const radiusSq = radiusDeg * radiusDeg;
    const pxPerDegSq = pxPerDeg * pxPerDeg;

    let weightSum = 0;
    let magSum = 0;
    let vectorWeight = 0;
    let uSum = 0;
    let vSum = 0;
    let nearestAll = Infinity;
    let nearestDir = Infinity;

    for (let r = row - 1; r <= row + 1; r++) {
        if (r < 0 || r >= rows) {
            continue;
        }
        for (let c = col - span; c <= col + span; c++) {
            const bucket = buckets.get(r * cols + ((c % cols) + cols) % cols);
            if (!bucket) {
                continue;
            }
            for (let i = 0; i < bucket.length; i++) {
                const s = bucket[i];
                const dLat = s.lat - lat;
                const dLon = normalizeLon(s.lon - lonN) * cosLat;
                const distSq = dLat * dLat + dLon * dLon;
                if (distSq < nearestAll) {
                    nearestAll = distSq;
                }
                if (s.hasDirection && distSq < nearestDir) {
                    nearestDir = distSq;
                }
                if (distSq > radiusSq) {
                    continue;
                }
                const weight = 1 / (distSq * pxPerDegSq + 1);
                weightSum += weight;
                magSum += weight * s.wind;
                if (s.hasDirection) {
                    vectorWeight += weight;
                    uSum += weight * s.u;
                    vSum += weight * s.v;
                }
            }
        }
    }

    if (weightSum === 0) {
        return false;
    }

    const fadeSpan = radiusDeg * (1 - WIND_FIELD_FADE_FROM);
    out[0] = magSum / weightSum;
    out[3] = clamp((radiusDeg - Math.sqrt(nearestAll)) / fadeSpan, 0, 1);
    if (vectorWeight > 0) {
        out[1] = uSum / vectorWeight;
        out[2] = vSum / vectorWeight;
        out[4] = clamp((radiusDeg - Math.sqrt(nearestDir)) / fadeSpan, 0, 1);
    }
    return true;
}

// Colour lookups a quarter knot apart - windFieldColor allocates an array per
// call, which is too much for every pixel of every frame
const COLOR_LUT_STEP = 0.25;
const COLOR_LUT_MAX = 40;
const WASH_LUT = [];
const PARTICLE_LUT = [];
for (let kt = 0; kt <= COLOR_LUT_MAX; kt += COLOR_LUT_STEP) {
    WASH_LUT.push(windFieldColor(kt));
    const rgb = windParticleColor(kt);
    PARTICLE_LUT.push(`${rgb[0]},${rgb[1]},${rgb[2]}`);
}

function lutIndex(kt) {
    return Math.min(WASH_LUT.length - 1, Math.max(0, Math.round(kt / COLOR_LUT_STEP)));
}

// ----------------------------------------------------------------------------
// The globe
// ----------------------------------------------------------------------------

/**
 * Create the globe inside a container.
 *
 * @param {object} options - Configuration options
 * @param {HTMLElement} options.container - Element the globe fills
 * @param {function} options.getConditions - Wind conditions for a spot, as on the map
 * @param {function} options.buildPopup - (spot) => popup HTML
 * @param {boolean} [options.windVisible=true] - Wind field shown on creation
 * @param {boolean} [options.spotsVisible=true] - Spot markers shown on creation
 * @param {boolean} [options.autoRotate=true] - Idle spin on creation
 * @param {function} [options.onWindToggle] - Called with the new wind visibility
 * @param {function} [options.onSpotsToggle] - Called with the new spot visibility
 * @param {function} [options.onAutoRotateToggle] - Called with the new spin state
 * @returns {object} Globe handle
 */
export function createGlobe(options) {
    const {
        container,
        getConditions,
        buildPopup,
        onWindToggle = null,
        onSpotsToggle = null,
        onAutoRotateToggle = null
    } = options;

    const prefersReducedMotion = typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    let windVisible = options.windVisible !== false;
    let spotsVisible = options.spotsVisible !== false;
    let autoRotate = options.autoRotate !== false && !prefersReducedMotion;

    // ---- DOM ---------------------------------------------------------------

    const root = document.createElement('div');
    root.className = 'globe';
    container.appendChild(root);

    const baseCanvas = document.createElement('canvas');
    baseCanvas.className = 'globe-canvas globe-canvas-base';
    const particleCanvas = document.createElement('canvas');
    particleCanvas.className = 'globe-canvas globe-canvas-particles';
    const overlayCanvas = document.createElement('canvas');
    overlayCanvas.className = 'globe-canvas globe-canvas-overlay';
    root.append(baseCanvas, particleCanvas, overlayCanvas);

    const baseCtx = baseCanvas.getContext('2d');
    const particleCtx = particleCanvas.getContext('2d');
    const overlayCtx = overlayCanvas.getContext('2d');

    // Low-resolution canvas the wash is painted into before being stretched
    const washCanvas = document.createElement('canvas');
    const washCtx = washCanvas.getContext('2d');

    const status = document.createElement('div');
    status.className = 'globe-status';
    root.appendChild(status);

    const tooltip = document.createElement('div');
    tooltip.className = 'globe-tooltip';
    tooltip.hidden = true;
    root.appendChild(tooltip);

    const popup = document.createElement('div');
    popup.className = 'globe-popup';
    popup.hidden = true;
    const popupClose = document.createElement('button');
    popupClose.type = 'button';
    popupClose.className = 'globe-popup-close';
    popupClose.innerHTML = CLOSE_ICON;
    const popupContent = document.createElement('div');
    popupContent.className = 'globe-popup-content';
    popup.append(popupClose, popupContent);
    root.appendChild(popup);

    // Controls, laid out like the map's: zoom top left, toggles bottom left
    const zoomControls = document.createElement('div');
    zoomControls.className = 'globe-controls globe-controls-zoom';
    const zoomIn = makeButton('globe-zoom-in', '+');
    const zoomOut = makeButton('globe-zoom-out', '−');
    zoomControls.append(zoomIn, zoomOut);
    root.appendChild(zoomControls);

    const toggleControls = document.createElement('div');
    toggleControls.className = 'globe-controls globe-controls-toggles';
    const windButton = makeButton('globe-toggle-wind', WIND_ICON);
    const spotsButton = makeButton('globe-toggle-spots', SPOTS_ICON);
    const rotateButton = makeButton('globe-toggle-rotate', ROTATE_ICON);
    const resetButton = makeButton('globe-reset', RESET_ICON);
    toggleControls.append(windButton, spotsButton, rotateButton, resetButton);
    root.appendChild(toggleControls);

    function makeButton(className, html) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `globe-button ${className}`;
        button.innerHTML = html;
        return button;
    }

    // ---- State -------------------------------------------------------------

    let width = 0;
    let height = 0;
    let pixelRatio = 1;

    let center = { ...DEFAULT_CENTER };
    let zoom = 1;
    let fly = null;
    let inertia = null;
    let dragging = null;
    let lastInteraction = 0;
    let hovered = null;
    let held = false;

    let coarseOutlines = null;
    let detailedOutlines = null;
    let projection = null;
    let path = null;
    let graticule = null;

    // Markers: every spot drawn, and the clusters they are merged into at the
    // current zoom
    let markerSpots = [];
    let markerFrameSpots = [];
    let clusters = [];
    let clusterZoom = null;
    let hitTargets = [];

    // Wind field: samples, and the bucket index for the current reach
    let samples = [];
    let fieldIndex = null;
    let visibleSamples = [];

    let particles = [];
    const sampleOut = [0, 0, 0, 0, 0];

    let palette = PALETTES[currentTheme()];
    let markerColors = readMarkerColors(root);

    let popupSpot = null;
    let frame = null;
    let lastFrameTime = 0;
    let viewDirty = true;
    let running = false;
    let destroyed = false;

    // ---- Projection helpers -------------------------------------------------

    function baseRadius() {
        return Math.max(1, Math.min(width, height) * BASE_RADIUS_FRACTION);
    }

    // The Leaflet zoom at which the flat map's world is as wide as the globe's
    // circumference - the scale every borrowed map constant is read at
    function equivalentZoom(r) {
        return Math.log2((2 * Math.PI * r) / LEAFLET_TILE_SIZE);
    }

    function zoomForEquivalent(leafletZoom) {
        return (LEAFLET_TILE_SIZE * Math.pow(2, leafletZoom)) / (2 * Math.PI) / baseRadius();
    }

    function maxZoom() {
        return zoomForEquivalent(MAX_EQUIVALENT_ZOOM);
    }

    // Per-frame constants of the orthographic projection, so projecting a
    // point is a handful of multiplications instead of a call into d3
    let view = { cx: 0, cy: 0, r: 1, sinLat0: 0, cosLat0: 1, lon0: 0, leafletZoom: 0 };

    function updateView() {
        const r = baseRadius() * zoom;
        view = {
            cx: width / 2,
            cy: height / 2,
            r,
            sinLat0: Math.sin(center.lat * DEG),
            cosLat0: Math.cos(center.lat * DEG),
            lon0: center.lon,
            leafletZoom: equivalentZoom(r)
        };
        if (projection) {
            projection
                .rotate([-center.lon, -center.lat])
                .translate([view.cx, view.cy])
                .scale(view.r)
                .clipExtent([[-2, -2], [width + 2, height + 2]]);
        }
    }

    /**
     * Project a point onto the screen.
     * @returns {boolean} False when the point is on the far side of the globe
     */
    function project(lon, lat, out) {
        const lambda = (lon - view.lon0) * DEG;
        const phi = lat * DEG;
        const cosPhi = Math.cos(phi);
        const sinPhi = Math.sin(phi);
        const cosLambda = Math.cos(lambda);
        const cosC = view.sinLat0 * sinPhi + view.cosLat0 * cosPhi * cosLambda;
        out[0] = view.cx + view.r * cosPhi * Math.sin(lambda);
        out[1] = view.cy - view.r * (view.cosLat0 * sinPhi - view.sinLat0 * cosPhi * cosLambda);
        out[2] = cosC;
        return cosC > 0;
    }

    function onScreen(point, margin) {
        return point[0] > -margin && point[0] < width + margin
            && point[1] > -margin && point[1] < height + margin;
    }

    // ---- Sizing ------------------------------------------------------------

    function resize() {
        const rect = root.getBoundingClientRect();
        const nextWidth = Math.max(1, Math.round(rect.width));
        const nextHeight = Math.max(1, Math.round(rect.height));
        const nextRatio = Math.min(MAX_PIXEL_RATIO, window.devicePixelRatio || 1);
        if (nextWidth === width && nextHeight === height && nextRatio === pixelRatio) {
            return;
        }
        width = nextWidth;
        height = nextHeight;
        pixelRatio = nextRatio;

        [baseCanvas, particleCanvas, overlayCanvas].forEach(canvas => {
            canvas.width = Math.round(width * pixelRatio);
            canvas.height = Math.round(height * pixelRatio);
            canvas.style.width = `${width}px`;
            canvas.style.height = `${height}px`;
        });
        [baseCtx, particleCtx, overlayCtx].forEach(ctx => {
            ctx.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
        });

        zoom = clamp(zoom, MIN_ZOOM, maxZoom());
        clusterZoom = null;
        viewDirty = true;
    }

    const resizeObserver = typeof ResizeObserver === 'function'
        ? new ResizeObserver(() => resize())
        : null;
    if (resizeObserver) {
        resizeObserver.observe(root);
    }

    // ---- Theme -------------------------------------------------------------

    const themeObserver = new MutationObserver(() => {
        palette = PALETTES[currentTheme()];
        markerColors = readMarkerColors(root);
        viewDirty = true;
    });
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

    // ---- Wind field --------------------------------------------------------

    // The map's reach in screen pixels at this zoom, turned into degrees. The
    // index is only rebuilt when the reach really changed, which keeps a slow
    // zoom from rebuilding it every frame.
    function updateFieldIndex() {
        const radiusPx = windFieldRadius(view.leafletZoom);
        const pxPerDeg = view.r * DEG;
        const radiusDeg = radiusPx / pxPerDeg;

        if (samples.length === 0) {
            fieldIndex = null;
            return;
        }
        if (fieldIndex && Math.abs(fieldIndex.radiusDeg - radiusDeg) / radiusDeg < 0.03) {
            fieldIndex.pxPerDeg = pxPerDeg;
            return;
        }
        fieldIndex = buildFieldIndex(samples, radiusDeg, pxPerDeg);
        fieldIndex.radiusPx = radiusPx;
    }

    // ---- Clustering --------------------------------------------------------

    /**
     * Merge spots that sit within the map's cluster radius of each other on
     * screen, the way the map does - greedy, seeded in spot order - but on the
     * sphere: the radius is an angle, so the clusters only depend on the zoom
     * and never shuffle while the globe turns. Past the map's cluster zoom
     * every spot is drawn on its own.
     */
    function rebuildClusters() {
        clusterZoom = view.leafletZoom;

        if (view.leafletZoom > CLUSTER_MAX_ZOOM) {
            clusters = markerSpots.map(entry => ({ single: entry, lat: entry.lat, lon: entry.lon }));
            return;
        }

        const threshold = Math.cos(CLUSTER_RADIUS_PX / view.r);
        const grouped = new Array(markerSpots.length).fill(false);
        clusters = [];

        for (let i = 0; i < markerSpots.length; i++) {
            if (grouped[i]) {
                continue;
            }
            grouped[i] = true;
            const seed = markerSpots[i].vector;
            const members = [markerSpots[i]];

            for (let j = i + 1; j < markerSpots.length; j++) {
                if (grouped[j]) {
                    continue;
                }
                const v = markerSpots[j].vector;
                if (seed[0] * v[0] + seed[1] * v[1] + seed[2] * v[2] >= threshold) {
                    grouped[j] = true;
                    members.push(markerSpots[j]);
                }
            }

            if (members.length === 1) {
                clusters.push({ single: members[0], lat: members[0].lat, lon: members[0].lon });
                continue;
            }

            let x = 0;
            let y = 0;
            let z = 0;
            members.forEach(m => {
                x += m.vector[0];
                y += m.vector[1];
                z += m.vector[2];
            });
            const length = Math.sqrt(x * x + y * y + z * z) || 1;
            const avgWind = averageWindSpeed(members.filter(m => m.wind !== null));

            clusters.push({
                members,
                lat: Math.asin(z / length) / DEG,
                lon: Math.atan2(y, x) / DEG,
                size: clusterBubbleSize(members.length),
                windClass: avgWind === null ? 'wind-no-data' : weather.getMapWindClass(avgWind)
            });
        }

        // A popup belongs to a dot; once its spot is swallowed by a bubble there
        // is no dot left for it to point at
        if (popupSpot && !clusters.some(c => c.single && c.single.spot.wgId === popupSpot.wgId)) {
            closePopup();
        }
    }

    function clustersStale() {
        return clusterZoom === null || Math.abs(clusterZoom - view.leafletZoom) > 0.05;
    }

    // ---- Drawing -----------------------------------------------------------

    function drawBase() {
        const ctx = baseCtx;
        const { cx, cy, r } = view;
        ctx.clearRect(0, 0, width, height);

        // Atmosphere: a soft glow just outside the rim
        const glow = ctx.createRadialGradient(cx, cy, r * 0.96, cx, cy, r * 1.14);
        glow.addColorStop(0, `rgba(${palette.atmosphere},0.35)`);
        glow.addColorStop(1, `rgba(${palette.atmosphere},0)`);
        ctx.fillStyle = glow;
        ctx.beginPath();
        ctx.arc(cx, cy, r * 1.14, 0, Math.PI * 2);
        ctx.fill();

        // Ocean, lit from the upper left
        const ocean = ctx.createRadialGradient(cx - r * 0.35, cy - r * 0.35, r * 0.1, cx, cy, r);
        ocean.addColorStop(0, palette.oceanInner);
        ocean.addColorStop(1, palette.oceanOuter);
        ctx.fillStyle = ocean;
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.fill();

        const outlines = view.leafletZoom >= DETAILED_OUTLINES_FROM_ZOOM && detailedOutlines
            ? detailedOutlines
            : coarseOutlines;

        if (path && outlines) {
            ctx.beginPath();
            path(graticule);
            ctx.strokeStyle = palette.graticule;
            ctx.lineWidth = 0.6;
            ctx.stroke();

            ctx.beginPath();
            path(outlines.land);
            ctx.fillStyle = palette.land;
            ctx.fill();
            ctx.strokeStyle = palette.landStroke;
            ctx.lineWidth = 0.6;
            ctx.stroke();

            ctx.beginPath();
            path(outlines.borders);
            ctx.strokeStyle = palette.borders;
            ctx.lineWidth = 0.5;
            ctx.stroke();
        }

        if (windVisible && fieldIndex) {
            drawWash(ctx);
        }

        // Shade toward the rim, which is what makes a disc read as a ball
        const limb = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.3, r * 0.2, cx, cy, r);
        limb.addColorStop(0, palette.highlight);
        limb.addColorStop(0.6, 'rgba(0,0,0,0)');
        limb.addColorStop(1, palette.limb);
        ctx.fillStyle = limb;
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.fill();
    }

    /**
     * Paint the wind wash: invert the projection every few pixels, interpolate
     * the field there and stretch the result over the globe.
     */
    function drawWash(ctx) {
        const { cx, cy, r, sinLat0, cosLat0, lon0 } = view;

        // Only the part of the disc that is on screen
        const left = Math.max(0, Math.floor(cx - r));
        const top = Math.max(0, Math.floor(cy - r));
        const right = Math.min(width, Math.ceil(cx + r));
        const bottom = Math.min(height, Math.ceil(cy + r));
        const cols = Math.ceil((right - left) / WASH_STEP);
        const rows = Math.ceil((bottom - top) / WASH_STEP);
        if (cols <= 0 || rows <= 0) {
            return;
        }

        if (washCanvas.width !== cols || washCanvas.height !== rows) {
            washCanvas.width = cols;
            washCanvas.height = rows;
        }
        const image = washCtx.createImageData(cols, rows);
        const data = image.data;

        for (let row = 0; row < rows; row++) {
            const py = (cy - (top + (row + 0.5) * WASH_STEP)) / r;
            for (let col = 0; col < cols; col++) {
                const px = (left + (col + 0.5) * WASH_STEP - cx) / r;
                const rhoSq = px * px + py * py;
                if (rhoSq >= 1) {
                    continue;
                }
                const cosC = Math.sqrt(1 - rhoSq);
                const lat = Math.asin(clamp(cosC * sinLat0 + py * cosLat0, -1, 1)) / DEG;
                const lon = lon0 + Math.atan2(px, cosC * cosLat0 - py * sinLat0) / DEG;

                if (!sampleField(fieldIndex, lon, lat, sampleOut)) {
                    continue;
                }
                const alpha = WASH_ALPHA * sampleOut[3];
                if (alpha <= 0.01) {
                    continue;
                }

                const rgb = WASH_LUT[lutIndex(sampleOut[0])];
                const offset = (row * cols + col) * 4;
                data[offset] = rgb[0];
                data[offset + 1] = rgb[1];
                data[offset + 2] = rgb[2];
                data[offset + 3] = Math.round(255 * alpha);
            }
        }

        washCtx.putImageData(image, 0, 0);
        ctx.save();
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        ctx.drawImage(washCanvas, left, top, cols * WASH_STEP, rows * WASH_STEP);
        ctx.restore();
    }

    function isPopupSpot(entry) {
        return popupSpot && entry.spot.wgId === popupSpot.wgId;
    }

    function drawOverlay() {
        const ctx = overlayCtx;
        ctx.clearRect(0, 0, width, height);
        hitTargets = [];

        if (!spotsVisible) {
            return;
        }

        const point = [0, 0, 0];

        clusters.forEach(cluster => {
            if (!project(cluster.lon, cluster.lat, point) || !onScreen(point, 40)) {
                return;
            }
            // Markers fade as they slide over the horizon instead of popping out
            ctx.globalAlpha = clamp(point[2] * 4, 0, 1);

            if (cluster.single) {
                const entry = cluster.single;
                const emphasized = entry === (hovered && hovered.single) || isPopupSpot(entry);
                hitTargets.push({ x: point[0], y: point[1], radius: DOT_HIT_RADIUS, cluster });

                ctx.beginPath();
                ctx.arc(point[0], point[1], DOT_RADIUS - DOT_BORDER / 2, 0, Math.PI * 2);
                ctx.fillStyle = markerColors[entry.windClass] || markerColors['wind-no-data'];
                ctx.fill();
                ctx.lineWidth = emphasized ? DOT_BORDER + 1 : DOT_BORDER;
                ctx.strokeStyle = markerColors.border;
                ctx.stroke();
                return;
            }

            drawBubble(ctx, point[0], point[1], cluster, cluster === hovered);
            hitTargets.push({ x: point[0], y: point[1], radius: cluster.size / 2 + 4, cluster });
        });

        ctx.globalAlpha = 1;
    }

    // The map's cluster bubble: a wind-coloured disc with a white border, a
    // faint halo ring and the count in the middle
    function drawBubble(ctx, x, y, cluster, hover) {
        const radius = (cluster.size / 2) * (hover ? 1.08 : 1);

        ctx.save();
        ctx.shadowColor = 'rgba(0,0,0,0.35)';
        ctx.shadowBlur = 6;
        ctx.shadowOffsetY = 2;
        ctx.beginPath();
        ctx.arc(x, y, radius + 4, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(255,255,255,0.18)';
        ctx.fill();
        ctx.restore();

        ctx.beginPath();
        ctx.arc(x, y, radius - 1, 0, Math.PI * 2);
        ctx.fillStyle = markerColors[cluster.windClass] || markerColors['wind-no-data'];
        ctx.fill();
        ctx.lineWidth = 2;
        ctx.strokeStyle = markerColors.border;
        ctx.stroke();

        ctx.save();
        ctx.font = `700 12.5px ${markerColors.font}`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.shadowColor = 'rgba(0,0,0,0.45)';
        ctx.shadowBlur = 2;
        ctx.shadowOffsetY = 1;
        ctx.fillStyle = '#ffffff';
        ctx.fillText(String(cluster.members.length), x, y + 0.5);
        ctx.restore();
    }

    // ---- Particles ---------------------------------------------------------

    // The spots with a bearing that can reach the screen - the map's own count
    // of "points in view", which is what its particle budget is made of
    function updateVisibleSamples() {
        const point = [0, 0, 0];
        const margin = fieldIndex ? fieldIndex.radiusPx : WIND_FIELD_MAX_DIST;
        visibleSamples = samples.filter(sample => sample.hasDirection
            && project(sample.lon, sample.lat, point)
            && onScreen(point, margin));
    }

    // Same budget as the map: per spot in view, scaled by how far a spot reaches
    function particleBudget() {
        if (!fieldIndex || visibleSamples.length === 0) {
            return 0;
        }
        const perSpot = PARTICLES_PER_SPOT * (fieldIndex.radiusPx / WIND_FIELD_MAX_DIST);
        return Math.max(
            PARTICLES_MIN,
            Math.min(PARTICLES_MAX, Math.round(visibleSamples.length * perSpot))
        );
    }

    // Dropped somewhere in a random spot's reach (square-rooted distance keeps
    // the spread even across the disc), exactly as on the map
    function respawn(particle) {
        if (visibleSamples.length === 0 || !fieldIndex) {
            particle.dead = true;
            return;
        }
        const origin = visibleSamples[Math.floor(Math.random() * visibleSamples.length)];
        const angle = Math.random() * Math.PI * 2;
        const distance = Math.sqrt(Math.random()) * fieldIndex.radiusDeg;
        particle.lat = clamp(origin.lat + Math.sin(angle) * distance, -89, 89);
        particle.lon = origin.lon + Math.cos(angle) * distance / Math.max(0.05, Math.cos(origin.lat * DEG));
        particle.age = Math.floor(Math.random() * PARTICLE_LIFE);
        particle.dead = false;
    }

    function fillParticles() {
        const target = particleBudget();
        while (particles.length < target) {
            const particle = { lon: 0, lat: 0, age: 0, dead: true };
            respawn(particle);
            particles.push(particle);
        }
        if (particles.length > target) {
            particles.length = target;
        }
    }

    /**
     * Advance every particle one step and draw the segment it travelled. The
     * step is the map's - PARTICLE_SPEED screen pixels per knot per 60 fps
     * frame - turned into degrees at the current scale, so a streak crosses the
     * screen (and a spot's reach) at the same pace on both views.
     * @param {number} dt - Frame time in 60 fps units
     * @param {boolean} fadeFast - Cut the trails short while the globe moves
     * @param {boolean} allowRespawn - Whether exhausted particles are recycled
     */
    function stepParticles(dt, fadeFast, allowRespawn = true) {
        const ctx = particleCtx;

        // Erase part of the last frame to leave trails. While the globe is
        // turning under them the trails would smear, so they are cut short.
        ctx.globalCompositeOperation = 'destination-out';
        ctx.fillStyle = `rgba(0,0,0,${fadeFast ? 0.45 : PARTICLE_TRAIL_FADE})`;
        ctx.fillRect(0, 0, width, height);
        ctx.globalCompositeOperation = 'source-over';

        if (!windVisible || !fieldIndex || particles.length === 0) {
            return;
        }

        const degPerPx = 1 / (view.r * DEG);
        const from = [0, 0, 0];
        const to = [0, 0, 0];

        ctx.lineCap = 'round';

        for (let i = 0; i < particles.length; i++) {
            const particle = particles[i];
            if (particle.dead) {
                if (allowRespawn) {
                    respawn(particle);
                }
                continue;
            }

            const inside = sampleField(fieldIndex, particle.lon, particle.lat, sampleOut);
            const strength = sampleOut[4];
            if (!inside || strength < PARTICLE_MIN_FIELD || particle.age++ > PARTICLE_LIFE) {
                if (allowRespawn) {
                    respawn(particle);
                } else {
                    particle.dead = true;
                }
                continue;
            }

            const u = sampleOut[1];
            const v = sampleOut[2];
            const step = PARTICLE_SPEED * dt * degPerPx;
            const nextLat = clamp(particle.lat + v * step, -89, 89);
            const nextLon = particle.lon + u * step / Math.max(0.05, Math.cos(particle.lat * DEG));

            const visible = project(particle.lon, particle.lat, from) && project(nextLon, nextLat, to);
            particle.lon = nextLon;
            particle.lat = nextLat;
            if (!visible || !onScreen(from, 10)) {
                continue;
            }

            const speed = Math.sqrt(u * u + v * v);
            const edge = Math.min(1, strength) * clamp(from[2] * 4, 0, 1);

            ctx.beginPath();
            ctx.moveTo(from[0], from[1]);
            ctx.lineTo(to[0], to[1]);

            ctx.lineWidth = PARTICLE_HALO_WIDTH;
            ctx.strokeStyle = `rgba(15,23,42,${(PARTICLE_HALO_ALPHA * edge).toFixed(3)})`;
            ctx.stroke();

            ctx.lineWidth = PARTICLE_WIDTH;
            ctx.strokeStyle = `rgba(${PARTICLE_LUT[lutIndex(speed)]},${(0.35 + 0.5 * edge).toFixed(3)})`;
            ctx.stroke();
        }
    }

    // Reduced-motion fallback: the streaklines drawn once whenever the view
    // settles, no animation
    function drawStaticParticles() {
        particleCtx.clearRect(0, 0, width, height);
        if (!windVisible || !fieldIndex) {
            return;
        }
        particles.forEach(respawn);
        for (let i = 0; i < PARTICLE_STATIC_STEPS; i++) {
            stepParticles(1, false, false);
        }
    }

    // ---- Popup and tooltip -------------------------------------------------

    /**
     * Put a bubble above a point, or below it when the top edge leaves no room,
     * and keep it inside the globe's frame sideways.
     */
    function placeBubble(element, x, y, gap) {
        const bubbleWidth = element.offsetWidth;
        const bubbleHeight = element.offsetHeight;
        const below = y - bubbleHeight - gap < 4;
        const left = clamp(x - bubbleWidth / 2, 4, Math.max(4, width - bubbleWidth - 4));
        const top = below ? y + gap : y - bubbleHeight - gap;
        element.classList.toggle('globe-bubble-below', below);
        element.style.transform = `translate(${Math.round(left)}px, ${Math.round(top)}px)`;
        // The tip keeps pointing at the dot when the bubble is pushed sideways
        element.style.setProperty('--globe-tip-x', `${Math.round(x - left)}px`);
    }

    function positionPopup() {
        if (!popupSpot) {
            return;
        }
        const point = [0, 0, 0];
        const { lat, lon } = popupSpot.coordinates;
        const visible = project(lon, lat, point) && onScreen(point, 0);
        popup.hidden = !visible;
        if (visible) {
            placeBubble(popup, point[0], point[1], DOT_RADIUS + 4);
        }
    }

    function openPopup(spot) {
        popupSpot = spot;
        popupContent.innerHTML = buildPopup(spot);
        popup.hidden = false;
        tooltip.hidden = true;
        positionPopup();
        viewDirty = true;
    }

    function closePopup() {
        if (!popupSpot) {
            return;
        }
        popupSpot = null;
        popup.hidden = true;
        lastInteraction = performance.now();
        viewDirty = true;
    }

    function findTargetAt(x, y) {
        let best = null;
        let bestDist = Infinity;
        hitTargets.forEach(target => {
            const dist = Math.hypot(target.x - x, target.y - y);
            if (dist <= target.radius && dist < bestDist) {
                best = target;
                bestDist = dist;
            }
        });
        return best;
    }

    // ---- Interaction -------------------------------------------------------

    function localPoint(event) {
        const rect = overlayCanvas.getBoundingClientRect();
        return { x: event.clientX - rect.left, y: event.clientY - rect.top };
    }

    function setZoom(next) {
        const clamped = clamp(next, MIN_ZOOM, maxZoom());
        if (clamped !== zoom) {
            zoom = clamped;
            viewDirty = true;
        }
        lastInteraction = performance.now();
    }

    overlayCanvas.addEventListener('pointerdown', event => {
        if (event.button !== 0) {
            return;
        }
        const { x, y } = localPoint(event);
        overlayCanvas.setPointerCapture(event.pointerId);
        dragging = { x, y, startX: x, startY: y, moved: false, time: performance.now(), vLon: 0, vLat: 0 };
        fly = null;
        inertia = null;
        lastInteraction = performance.now();
    });

    overlayCanvas.addEventListener('pointermove', event => {
        const { x, y } = localPoint(event);

        if (dragging) {
            const dx = x - dragging.x;
            const dy = y - dragging.y;
            if (!dragging.moved && Math.hypot(x - dragging.startX, y - dragging.startY) > 3) {
                dragging.moved = true;
                root.classList.add('globe-dragging');
                tooltip.hidden = true;
            }
            if (dragging.moved) {
                const degPerPx = 1 / (view.r * DEG);
                const dLon = -dx * degPerPx / Math.max(0.2, view.cosLat0);
                const dLat = dy * degPerPx;
                center = {
                    lon: normalizeLon(center.lon + dLon),
                    lat: clamp(center.lat + dLat, -MAX_TILT, MAX_TILT)
                };
                const now = performance.now();
                const elapsed = Math.max(1, now - dragging.time);
                dragging.vLon = dLon / elapsed * 16.67;
                dragging.vLat = dLat / elapsed * 16.67;
                dragging.time = now;
                viewDirty = true;
            }
            dragging.x = x;
            dragging.y = y;
            lastInteraction = performance.now();
            return;
        }

        const target = findTargetAt(x, y);
        const next = target ? target.cluster : null;
        if (next !== hovered) {
            hovered = next;
            overlayCanvas.style.cursor = hovered ? 'pointer' : '';
            viewDirty = true;
        }

        if (!target || (target.cluster.single && isPopupSpot(target.cluster.single))) {
            tooltip.hidden = true;
            return;
        }
        tooltip.textContent = target.cluster.single
            ? target.cluster.single.spot.name
            : `${target.cluster.members.length} ${translations.t('mapClusterSpotsLabel')}`;
        tooltip.hidden = false;
        placeBubble(tooltip, target.x, target.y, target.radius + 2);
    });

    function endDrag(event) {
        if (!dragging) {
            return;
        }
        const { x, y } = localPoint(event);
        const wasClick = !dragging.moved;
        const recentMove = performance.now() - dragging.time < 80;
        if (dragging.moved && recentMove && !prefersReducedMotion) {
            inertia = { vLon: dragging.vLon, vLat: dragging.vLat };
        }
        dragging = null;
        root.classList.remove('globe-dragging');
        lastInteraction = performance.now();

        if (!wasClick) {
            return;
        }

        const target = findTargetAt(x, y);
        if (!target) {
            closePopup();
        } else if (target.cluster.single) {
            openPopup(target.cluster.single.spot);
        } else {
            // A cluster opens up the way it does on the map: zoomed in on its spots
            tooltip.hidden = true;
            hovered = null;
            frameSpots(target.cluster.members.map(m => m.spot), {
                padding: CLUSTER_FIT_PADDING_PX,
                maxLeafletZoom: CLUSTER_FIT_MAX_ZOOM,
                // Spots sharing one position have no extent to fit: two levels in
                coincidentZoom: zoom * ZOOM_STEP_FACTOR * ZOOM_STEP_FACTOR
            });
        }
    }

    overlayCanvas.addEventListener('pointerup', endDrag);
    overlayCanvas.addEventListener('pointercancel', endDrag);

    overlayCanvas.addEventListener('pointerleave', () => {
        if (!dragging) {
            hovered = null;
            tooltip.hidden = true;
            viewDirty = true;
        }
    });

    overlayCanvas.addEventListener('wheel', event => {
        event.preventDefault();
        fly = null;
        // A wheel notch is a map zoom level; a trackpad's small deltas glide
        setZoom(zoom * Math.pow(ZOOM_STEP_FACTOR, -event.deltaY / WHEEL_DELTA_PER_LEVEL));
    }, { passive: false });

    overlayCanvas.addEventListener('dblclick', event => {
        event.preventDefault();
        setZoom(zoom * ZOOM_STEP_FACTOR);
    });

    zoomIn.addEventListener('click', () => {
        fly = null;
        setZoom(zoom * ZOOM_STEP_FACTOR);
    });
    zoomOut.addEventListener('click', () => {
        fly = null;
        setZoom(zoom / ZOOM_STEP_FACTOR);
    });

    windButton.addEventListener('click', () => {
        windVisible = !windVisible;
        particleCtx.clearRect(0, 0, width, height);
        refreshButtons();
        viewDirty = true;
        if (typeof onWindToggle === 'function') {
            onWindToggle(windVisible);
        }
    });

    spotsButton.addEventListener('click', () => {
        spotsVisible = !spotsVisible;
        if (!spotsVisible) {
            closePopup();
            tooltip.hidden = true;
            hovered = null;
        }
        refreshButtons();
        viewDirty = true;
        if (typeof onSpotsToggle === 'function') {
            onSpotsToggle(spotsVisible);
        }
    });

    rotateButton.addEventListener('click', () => {
        autoRotate = !autoRotate;
        lastInteraction = 0;
        refreshButtons();
        if (typeof onAutoRotateToggle === 'function') {
            onAutoRotateToggle(autoRotate);
        }
    });

    resetButton.addEventListener('click', () => focusOn(markerFrameSpots, { force: true }));

    popupClose.addEventListener('click', closePopup);

    const onKeyDown = event => {
        if (event.key === 'Escape' && popupSpot) {
            closePopup();
        }
    };
    document.addEventListener('keydown', onKeyDown);

    function refreshButtons() {
        const set = (button, active, onKey, offKey) => {
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', active ? 'true' : 'false');
            const label = translations.t(active ? onKey : offKey);
            button.title = label;
            button.setAttribute('aria-label', label);
        };
        set(windButton, windVisible, 'windOverlayHide', 'windOverlayShow');
        set(spotsButton, spotsVisible, 'mapSpotsHide', 'mapSpotsShow');
        set(rotateButton, autoRotate, 'globeRotateStop', 'globeRotateStart');

        const plain = (button, key) => {
            const label = translations.t(key);
            button.title = label;
            button.setAttribute('aria-label', label);
        };
        plain(resetButton, 'globeResetView');
        plain(zoomIn, 'globeZoomIn');
        plain(zoomOut, 'globeZoomOut');
        plain(popupClose, 'globePopupClose');
    }

    // ---- Framing -----------------------------------------------------------

    function flyTo(target) {
        inertia = null;
        lastInteraction = performance.now();
        const to = {
            lon: normalizeLon(target.lon),
            lat: clamp(target.lat, -MAX_TILT, MAX_TILT),
            zoom: clamp(target.zoom, MIN_ZOOM, maxZoom())
        };

        if (prefersReducedMotion) {
            center = { lon: to.lon, lat: to.lat };
            zoom = to.zoom;
            fly = null;
            viewDirty = true;
            return;
        }

        fly = {
            start: performance.now(),
            from: { ...center, zoom },
            to,
            // Turn the short way round
            dLon: normalizeLon(to.lon - center.lon)
        };
    }

    /**
     * Fly to a set of spots: their centre, zoomed in until the furthest one sits
     * the padding away from the edge of the shorter side - the globe's
     * fitBounds. Spots spread over too much of the planet get the whole globe.
     */
    function frameSpots(spots, { padding, maxLeafletZoom, coincidentZoom = null }) {
        const points = spots
            .filter(spot => spot && spot.coordinates
                && Number.isFinite(spot.coordinates.lat)
                && Number.isFinite(spot.coordinates.lon))
            .map(spot => ({ lat: spot.coordinates.lat, lon: spot.coordinates.lon }));

        const frameData = sphericalFrame(points);
        if (!frameData) {
            flyTo({ ...DEFAULT_CENTER, zoom: 1 });
            return;
        }
        if (frameData.spread > WHOLE_GLOBE_SPREAD_DEG) {
            flyTo({ lon: frameData.lon, lat: frameData.lat, zoom: 1 });
            return;
        }

        const limit = zoomForEquivalent(maxLeafletZoom);
        if (frameData.spread < 1e-4 && coincidentZoom !== null) {
            flyTo({ lon: frameData.lon, lat: frameData.lat, zoom: Math.min(coincidentZoom, maxZoom()) });
            return;
        }

        const available = Math.max(40, Math.min(width, height) / 2 - padding);
        const spread = Math.max(frameData.spread, 1e-3) * DEG;
        const fitRadius = available / Math.sin(Math.min(spread, Math.PI / 2));
        flyTo({
            lon: frameData.lon,
            lat: frameData.lat,
            zoom: clamp(fitRadius / baseRadius(), 1, limit)
        });
    }

    let lastFrameKey = null;

    /**
     * Frame the spots in view after a data update - but only when the set of
     * spots changed (a new filter), so a background refresh never yanks the
     * globe away from where the visitor turned it.
     */
    function focusOn(spots, { force = false } = {}) {
        const key = spots.map(spot => spot.wgId).join(',');
        if (!force && key === lastFrameKey) {
            return;
        }
        lastFrameKey = key;
        frameSpots(spots, { padding: FILTER_FIT_PADDING_PX, maxLeafletZoom: FILTER_FIT_MAX_ZOOM });
    }

    /**
     * Turn a point out from under something covering the right edge - the side
     * peek - when it sits beneath it: it lands in the middle of what is left.
     * The zoom and tilt stay where the visitor put them.
     */
    function revealPoint(lat, lon, rightInset) {
        const point = [0, 0, 0];
        if (project(lon, lat, point) && point[0] <= width - rightInset) {
            return;
        }

        const targetDx = (width - rightInset) / 2 - view.cx;
        const cosLat = Math.cos(lat * DEG);
        const ratio = targetDx / (view.r * Math.max(0.05, cosLat));
        const lonOffset = Math.abs(ratio) <= 1 ? Math.asin(ratio) / DEG : 0;
        flyTo({ lon: lon - lonOffset, lat: center.lat, zoom });
    }

    // ---- Main loop ---------------------------------------------------------

    function tick(now) {
        frame = null;
        if (!running || destroyed) {
            return;
        }

        const elapsed = lastFrameTime ? now - lastFrameTime : 16.67;
        lastFrameTime = now;
        // Frame-rate independent, and clamped like the map's so a stalled tab
        // doesn't teleport every particle
        const dt = clamp(elapsed, 8, 50) / 16.67;

        let moving = false;

        if (fly) {
            const t = clamp((now - fly.start) / FLY_DURATION_MS, 0, 1);
            const e = easeInOutCubic(t);
            center = {
                lon: normalizeLon(fly.from.lon + fly.dLon * e),
                lat: fly.from.lat + (fly.to.lat - fly.from.lat) * e
            };
            // Zoom is eased in log space, so a fly from the whole planet to a
            // single bay doesn't spend the whole trip at the far end, and lifts
            // a little mid-flight on a long hop, the way a camera would
            const logZoom = Math.log(fly.from.zoom) + (Math.log(fly.to.zoom) - Math.log(fly.from.zoom)) * e;
            const hop = Math.min(1, Math.abs(fly.dLon) / 120 + Math.abs(fly.to.lat - fly.from.lat) / 90);
            const lift = Math.sin(Math.PI * e) * hop * 0.35;
            zoom = Math.max(MIN_ZOOM, Math.exp(logZoom) * (1 - lift));
            viewDirty = true;
            moving = true;
            if (t >= 1) {
                fly = null;
                lastInteraction = now;
            }
        } else if (inertia && !dragging) {
            center = {
                lon: normalizeLon(center.lon + inertia.vLon * dt),
                lat: clamp(center.lat + inertia.vLat * dt, -MAX_TILT, MAX_TILT)
            };
            inertia.vLon *= Math.pow(INERTIA_DECAY, dt);
            inertia.vLat *= Math.pow(INERTIA_DECAY, dt);
            if (Math.abs(inertia.vLon) < 0.005 / zoom && Math.abs(inertia.vLat) < 0.005 / zoom) {
                inertia = null;
            }
            viewDirty = true;
            moving = true;
        } else if (autoRotate && !held && !dragging && !popupSpot && !hovered
            && now - lastInteraction > AUTO_ROTATE_RESUME_MS) {
            center = {
                lon: normalizeLon(center.lon + AUTO_ROTATE_DEG_PER_SEC * (elapsed / 1000) / zoom),
                lat: center.lat
            };
            viewDirty = true;
        }

        if (dragging && dragging.moved) {
            moving = true;
        }

        if (viewDirty) {
            viewDirty = false;
            updateView();
            maybeLoadDetailedOutlines();
            updateFieldIndex();
            if (clustersStale()) {
                rebuildClusters();
            }
            drawBase();
            drawOverlay();
            updateVisibleSamples();
            fillParticles();
            positionPopup();
            if (prefersReducedMotion) {
                drawStaticParticles();
            }
        }

        if (!prefersReducedMotion) {
            stepParticles(dt, moving);
        }

        frame = requestAnimationFrame(tick);
    }

    function start() {
        if (running || destroyed) {
            return;
        }
        running = true;
        lastFrameTime = 0;
        resize();
        viewDirty = true;
        frame = requestAnimationFrame(tick);
    }

    function stop() {
        running = false;
        if (frame) {
            cancelAnimationFrame(frame);
            frame = null;
        }
    }

    // ---- Loading -----------------------------------------------------------

    let statusKey = null;

    function setStatus(key) {
        statusKey = key;
        status.textContent = key ? translations.t(key) : '';
        status.hidden = !key;
    }

    setStatus('globeLoading');

    loadCoarseOutlines()
        .then(outlines => {
            if (destroyed) {
                return;
            }
            coarseOutlines = outlines;
            projection = window.d3.geoOrthographic().clipAngle(90).precision(0.5);
            path = window.d3.geoPath(projection, baseCtx);
            graticule = window.d3.geoGraticule10();
            setStatus(null);
            viewDirty = true;
        })
        .catch(error => {
            console.error('Failed to load globe outlines:', error);
            // The globe still works without coastlines - an ocean with spots
            // and wind on it - so only the status line says something is off
            setStatus('globeLoadError');
            viewDirty = true;
        });

    let detailedRequested = false;

    function maybeLoadDetailedOutlines() {
        if (detailedRequested || view.leafletZoom < DETAILED_OUTLINES_FROM_ZOOM) {
            return;
        }
        detailedRequested = true;
        loadDetailedOutlines()
            .then(outlines => {
                if (destroyed) {
                    return;
                }
                detailedOutlines = outlines;
                viewDirty = true;
            })
            .catch(error => {
                // The coarse set stays on; a later zoom in tries again
                detailedRequested = false;
                console.error('Failed to load detailed globe outlines:', error);
            });
    }

    refreshButtons();
    resize();

    // ---- Public API --------------------------------------------------------

    return {
        element: root,
        start,
        stop,

        /**
         * Hand the globe new data.
         * @param {Array} dotSpots - Spots drawn as markers (favorites filter applied)
         * @param {Array} fieldSpots - Spots the wind field is interpolated from
         */
        setSpots(dotSpots, fieldSpots) {
            markerSpots = [];
            (dotSpots || []).forEach(spot => {
                if (!spot || !spot.coordinates) {
                    return;
                }
                const { lat, lon } = spot.coordinates;
                if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
                    return;
                }
                const conditions = getConditions(spot);
                const wind = conditions && Number.isFinite(conditions.wind) ? conditions.wind : null;
                markerSpots.push({
                    spot,
                    lat,
                    lon,
                    vector: unitVector(lat, lon),
                    wind,
                    windClass: wind === null ? 'wind-no-data' : weather.getMapWindClass(wind)
                });
            });
            markerFrameSpots = dotSpots || [];
            clusterZoom = null;

            samples = [];
            (fieldSpots || []).forEach(spot => {
                const sample = getWindSample(spot, getConditions);
                if (!sample) {
                    return;
                }
                // The direction names where the wind comes from; it blows the
                // other way. East/north components, in knots.
                const hasDirection = CARDINAL_DIRECTIONS.includes(sample.direction);
                const rad = ((weather.getWindRotation(sample.direction) + 180) % 360) * DEG;
                samples.push({
                    ...sample,
                    hasDirection,
                    u: hasDirection ? Math.sin(rad) * sample.wind : 0,
                    v: hasDirection ? Math.cos(rad) * sample.wind : 0
                });
            });
            fieldIndex = null;

            // Particles carry no data of their own, but they were spawned
            // around the old samples - start them afresh
            particles = [];
            particleCtx.clearRect(0, 0, width, height);
            hovered = null;

            // The open popup follows the spot it belongs to, or closes when the
            // filter took the spot away
            if (popupSpot) {
                const still = markerSpots.find(entry => entry.spot.wgId === popupSpot.wgId);
                if (still && spotsVisible) {
                    popupSpot = still.spot;
                    popupContent.innerHTML = buildPopup(still.spot);
                } else {
                    closePopup();
                }
            }

            focusOn(markerFrameSpots);
            viewDirty = true;
        },

        revealPoint,

        // Holds the idle spin while something the visitor is reading - the side
        // peek - is tied to a spot on the globe
        setHold(value) {
            held = !!value;
            lastInteraction = performance.now();
        },

        refreshLabels() {
            refreshButtons();
            if (popupSpot) {
                popupContent.innerHTML = buildPopup(popupSpot);
            }
            setStatus(statusKey);
        },

        resize,

        destroy() {
            destroyed = true;
            stop();
            if (resizeObserver) {
                resizeObserver.disconnect();
            }
            themeObserver.disconnect();
            document.removeEventListener('keydown', onKeyDown);
            root.remove();
        }
    };
}
