// ============================================================================
// API FUNCTIONS
// ============================================================================

export async function fetchWeatherData() {
    try {
        const response = await fetch('api/v1/spots', {cache: 'no-store'});

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

export async function fetchSpotData(spotId, model = 'gfs') {
    try {
        const url = `/api/v1/spots/${spotId}${model ? '/' + model : ''}`;
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

export async function fetchMainSponsors() {
    try {
        const response = await fetch('/api/v1/sponsors');
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

export async function fetchStatus() {
    return await fetch('/api/v1/status');
}

export function getStatusEndpointsToMonitor() {
    return ['/api/v1/health', '/api/v1/status', '/api/v1/spots'];
}