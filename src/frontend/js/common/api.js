// ============================================================================
// SHARED API FUNCTIONS
// ============================================================================

// API Endpoints
const API_ENDPOINT_SPOTS = '/api/v1/spots';
const API_ENDPOINT_SPONSORS = '/api/v1/sponsors';
const API_ENDPOINT_STATUS = '/api/v1/status';

// ============================================================================
// SPOTS API
// ============================================================================

/**
 * Fetch all spots with forecasts and current conditions
 * @returns {Promise<Array>} Array of spot objects
 */
export async function fetchAllSpots() {
    try {
        const response = await fetch(API_ENDPOINT_SPOTS, { cache: 'no-store' });

        if (!response.ok) {
            throw new Error(`HTTP Error: ${response.status}`);
        }

        const data = await response.json();

        if (!Array.isArray(data)) {
            throw new Error('Invalid data format: Expected array of spots');
        }

        return data;
    } catch (error) {
        console.error('Error fetching weather data:', error);
        throw error;
    }
}

/**
 * Fetch single spot data with optional forecast model
 * @param {string|number} spotId - The spot ID
 * @param {string|null} model - Optional forecast model (e.g., 'gfs', 'ifs')
 * @returns {Promise<Object>} Spot object with forecast data
 */
export async function fetchSpot(spotId, model = null) {
    try {
        const url = `${API_ENDPOINT_SPOTS}/${spotId}${model ? '/' + model : ''}`;
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
// SPONSORS API
// ============================================================================

/**
 * Fetch main sponsors
 * @returns {Promise<Array>} Array of sponsor objects (empty array on error)
 */
export async function fetchSponsors() {
    try {
        const response = await fetch(API_ENDPOINT_SPONSORS);

        if (!response.ok) {
            return [];
        }

        const sponsors = await response.json();
        return sponsors || [];
    } catch (error) {
        console.error('Error fetching main sponsors:', error);
        return [];
    }
}

// ============================================================================
// STATUS API
// ============================================================================

/**
 * Fetch system status
 * @returns {Promise<Object>} Status object
 */
export async function fetchStatus() {
    const response = await fetch(API_ENDPOINT_STATUS);

    if (!response.ok) {
        throw new Error('Failed to fetch status');
    }

    return await response.json();
}

/**
 * Check endpoint health with latency measurement
 * @param {string} endpoint - The endpoint to check
 * @returns {Promise<{ok: boolean, status: number|null, latency: number|null, error: string|null}>}
 */
export async function checkEndpointHealth(endpoint) {
    try {
        const startTime = performance.now();
        const response = await fetch(endpoint);
        const endTime = performance.now();
        const latency = Math.round(endTime - startTime);

        return {
            ok: response.ok,
            status: response.status,
            latency,
            error: null
        };
    } catch (error) {
        return {
            ok: false,
            status: null,
            latency: null,
            error: 'unreachable'
        };
    }
}
