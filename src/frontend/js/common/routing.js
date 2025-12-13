// ============================================================================
// URL ROUTING UTILITIES
// Common routing functions used across all frontend pages
// ============================================================================

// ============================================================================
// URL NORMALIZATION
// ============================================================================

/**
 * Convert country name to URL-safe format: lowercase, no spaces or special chars
 * @param {string} country - Country name to normalize
 * @returns {string} URL-safe country name
 */
export function normalizeCountryForUrl(country) {
    return country.toLowerCase()
        .replace(/\s+/g, '')
        .replace(/[^a-z]/g, '');
}

// ============================================================================
// URL EXTRACTION
// ============================================================================

/**
 * Get the current URL pathname
 * @returns {string} Current pathname
 */
export function getCurrentPath() {
    return window.location.pathname;
}

/**
 * Extract country from the URL pattern: /country/{countryName}
 * @returns {string|null} Country name from URL or null if not present
 */
export function getCountryFromUrl() {
    const pathParts = window.location.pathname.split('/');
    const countryIndex = pathParts.indexOf('country');
    if (countryIndex !== -1 && pathParts.length > countryIndex + 1) {
        return pathParts[countryIndex + 1];
    }
    return null;
}

/**
 * Extract spot ID from the URL pattern: /spot/{spotId} or /spot/{spotId}/tv
 * @returns {string|null} Spot ID from URL or null if not present
 */
export function getSpotIdFromUrl() {
    const pathParts = window.location.pathname.split('/');
    const spotIndex = pathParts.indexOf('spot');
    if (spotIndex !== -1 && pathParts.length > spotIndex + 1) {
        return pathParts[spotIndex + 1];
    }
    return null;
}

// ============================================================================
// URL CHECKS
// ============================================================================

/**
 * Check if the current URL is /starred
 * @returns {boolean} True if on starred page
 */
export function isStarredUrl() {
    return window.location.pathname === '/starred';
}

/**
 * Check if the current URL is /map
 * @returns {boolean} True if on map page
 */
export function isMapUrl() {
    return window.location.pathname === '/map';
}

// ============================================================================
// NAVIGATION
// ============================================================================

/**
 * Navigate to the home page
 */
export function navigateToHome() {
    window.location.href = '/';
}

/**
 * Navigate to country page
 * @param {string} country - Country name (will be normalized for URL)
 */
export function navigateToCountry(country) {
    const normalizedCountry = normalizeCountryForUrl(country);
    window.location.href = `/country/${normalizedCountry}`;
}

/**
 * Navigate to spot page
 * @param {string|number} spotId - Spot ID
 */
export function navigateToSpot(spotId) {
    window.location.href = `/spot/${spotId}`;
}

// ============================================================================
// HISTORY API
// ============================================================================

/**
 * Update browser URL for country filter without page reload
 * @param {string} country - Country name or 'all'
 */
export function pushCountryUrl(country) {
    if (country === 'all') {
        window.history.pushState({country: 'all'}, '', '/');
    } else {
        const normalizedCountry = normalizeCountryForUrl(country);
        window.history.pushState({country: country}, '', `/country/${normalizedCountry}`);
    }
}

/**
 * Update browser URL to /starred without a page reload
 */
export function pushStarredUrl() {
    window.history.pushState({starred: true}, '', '/starred');
}

/**
 * Update browser URL to /map without a page reload
 */
export function pushMapUrl() {
    window.history.pushState({map: true}, '', '/map');
}

/**
 * Update browser URL without a page reload (generic)
 * @param {string} url - URL to push
 * @param {object} state - State object for history
 */
export function pushUrl(url, state = {}) {
    window.history.pushState(state, '', url);
}

// ============================================================================
// URL BUILDING
// ============================================================================

/**
 * Build country URL path
 * @param {string} country - Country name or 'all'
 * @returns {string} URL path
 */
export function buildCountryUrl(country) {
    if (country === 'all') {
        return '/';
    }
    return `/country/${normalizeCountryForUrl(country)}`;
}

/**
 * Build spot URL path
 * @param {string|number} spotId - Spot ID
 * @returns {string} URL path
 */
export function buildSpotUrl(spotId) {
    return `/spot/${spotId}`;
}

/**
 * Build spot TV URL path
 * @param {string|number} spotId - Spot ID
 * @returns {string} URL path
 */
export function buildSpotTvUrl(spotId) {
    return `/spot/${spotId}/tv`;
}

/**
 * Get the base URL (origin) of the current page
 * @returns {string} Base URL
 */
export function getBaseUrl() {
    try {
        return window.location.origin.replace(/\/$/, '');
    } catch (e) {
        return 'https://varun.surf';
    }
}

// ============================================================================
// PAGE UTILITIES
// ============================================================================

/**
 * Reload the current page
 */
export function reloadPage() {
    window.location.reload();
}
