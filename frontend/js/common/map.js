// ============================================================================
// MAP UTILITIES
// Common map-related functions for OSM and satellite tile layers
// ============================================================================

import { t } from './translations.js';

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
                optionEl.textContent = t(option.translationKey);
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
                optionEl.textContent = t(translationKey);
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