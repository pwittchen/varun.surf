// ============================================================================
// DATE AND TIME UTILITIES
// ============================================================================

/**
 * Month name to month number mapping (0-indexed).
 */
const MONTH_MAP = {
    'Jan': 0, 'Feb': 1, 'Mar': 2, 'Apr': 3,
    'May': 4, 'Jun': 5, 'Jul': 6, 'Aug': 7,
    'Sep': 8, 'Oct': 9, 'Nov': 10, 'Dec': 11
};

/**
 * Parse forecast date string to Date object.
 * Format: "Tue 28 Oct 2025 14:00"
 * @param {string} dateStr - Date string in forecast format
 * @returns {Date} Parsed date or current date if parsing fails
 */
export function parseForecastDate(dateStr) {
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

            const monthNumber = MONTH_MAP[monthName];
            if (monthNumber !== undefined) {
                const parsed = new Date(year, monthNumber, dayOfMonth, hours, minutes);
                if (!isNaN(parsed.getTime())) {
                    return parsed;
                }
            }
        }
    } catch (error) {
        console.warn('Error parsing forecast date:', dateStr, error);
    }

    return new Date();
}

/**
 * Find the forecast entry closest to the current time.
 * @param {Array} forecastData - Array of forecast objects with 'date' property
 * @returns {Object|null} The forecast closest to now, or null if array is empty
 */
export function findClosestForecast(forecastData) {
    if (!Array.isArray(forecastData) || forecastData.length === 0) {
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

/**
 * Format time string from date (extracts HH:MM part).
 * @param {string} dateStr - Date string in forecast format
 * @returns {string} Time portion (HH:MM) or empty string
 */
export function formatTime(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split(' ');
    if (parts.length >= 5) {
        return parts[4]; // HH:MM
    }
    return dateStr;
}
