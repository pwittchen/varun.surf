// ============================================================================
// SHARED API FUNCTIONS
// ============================================================================

// API Endpoints
const API_ENDPOINT_SPOTS = '/api/v1/spots';
const API_ENDPOINT_WIND = '/api/v1/wind';
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
        const response = await fetch(API_ENDPOINT_SPOTS, { cache: 'no-store', credentials: 'same-origin' });

        if (!response.ok) {
            throw new Error(`HTTP Error: ${response.status}`);
        }

        const data = await response.json();

        if (!Array.isArray(data)) {
            throw new Error('Invalid data format: Expected array of spots');
        }

        // Default ordering: alphabetical by spot name
        return data.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
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
        const response = await fetch(url, { credentials: 'same-origin' });

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

/**
 * Ask the server to write this spot's AI analysis.
 *
 * Nothing generates one in the background any more - an analysis costs an LLM
 * call, so it is written only when a visitor presses the button and then held for
 * 24 hours. A spot that already has a valid one is answered from that cache, so
 * calling this twice within the day is free.
 *
 * @param {string|number} spotId - Windguru id of the spot
 * @param {string} language - 'pl' or 'en'
 * @returns {Promise<Object>} the spot, with the analysis filled in
 * @throws {Error} carrying `status` when the server refused or failed
 */
export async function generateAiAnalysis(spotId, language) {
    const url = `${API_ENDPOINT_SPOTS}/${spotId}/analysis?lang=${encodeURIComponent(language || 'en')}`;
    const response = await fetch(url, { method: 'POST', credentials: 'same-origin' });

    if (!response.ok) {
        const error = new Error(`HTTP error! status: ${response.status}`);
        error.status = response.status;
        throw error;
    }

    return await response.json();
}

/**
 * Ask the server to read this spot's ICM meteogram through the vision model.
 *
 * Same bargain as the analysis above: one call, cached for 24 hours. The spot
 * comes back carrying ICM among its `availableModels`, so the caller can
 * repopulate the model dropdown without a second request.
 *
 * @param {string|number} spotId - Windguru id of the spot
 * @returns {Promise<Object>} the spot, with ICM among its available models
 * @throws {Error} carrying `status` when the forecast could not be produced
 */
export async function generateIcmForecast(spotId) {
    const url = `${API_ENDPOINT_SPOTS}/${spotId}/icm`;
    const response = await fetch(url, { method: 'POST', credentials: 'same-origin' });

    if (!response.ok) {
        const error = new Error(`HTTP error! status: ${response.status}`);
        error.status = response.status;
        throw error;
    }

    return await response.json();
}

/**
 * Fetch the hourly wind timeline: every spot's wind laid out on one shared grid
 * of hours, which is what the map's forecast slider steps through. Per-spot
 * hourly forecasts are stripped from the spots response, so the map reads this
 * instead.
 * @param {number} [hours] - How far the grid should reach; the server trims it to
 *   the hours the forecast holds, and serves its own default span when omitted
 * @returns {Promise<{hours: Array<string>, spots: Array<object>}>} Timeline (empty on error)
 */
export async function fetchWindTimeline(hours) {
    const url = Number.isFinite(hours) && hours > 0
        ? `${API_ENDPOINT_WIND}?hours=${Math.round(hours)}`
        : API_ENDPOINT_WIND;

    try {
        const response = await fetch(url, { credentials: 'same-origin' });

        if (!response.ok) {
            throw new Error(`HTTP Error: ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        console.error('Error fetching wind timeline:', error);
        return { hours: [], spots: [] };
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
        const response = await fetch(API_ENDPOINT_SPONSORS, { credentials: 'same-origin' });

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
    const response = await fetch(API_ENDPOINT_STATUS, { credentials: 'same-origin' });

    if (!response.ok) {
        throw new Error('Failed to fetch status');
    }

    return await response.json();
}

/**
 * Fetch how far the server's forecast sweep got. The sweep runs over the whole spot
 * list and publishes spot by spot, so a freshly started instance serves spots without
 * a forecast for as long as it lasts.
 * @returns {Promise<{inProgress: boolean, total: number, completed: number, fetched: number,
 *   empty: number, failed: number, cached: number, elapsedMs: number}|null>} null on error
 */
export async function fetchForecastProgress() {
    try {
        const response = await fetch(`${API_ENDPOINT_STATUS}/forecast`, {
            cache: 'no-store',
            credentials: 'same-origin'
        });

        if (!response.ok) {
            return null;
        }

        return await response.json();
    } catch (error) {
        console.error('Error fetching forecast progress:', error);
        return null;
    }
}

/**
 * Check endpoint health with latency measurement
 * @param {string} endpoint - The endpoint to check
 * @returns {Promise<{ok: boolean, status: number|null, latency: number|null, error: string|null}>}
 */
export async function checkEndpointHealth(endpoint) {
    try {
        const startTime = performance.now();
        const response = await fetch(endpoint, { credentials: 'same-origin' });
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
