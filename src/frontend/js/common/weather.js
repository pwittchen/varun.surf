// ============================================================================
// WEATHER DISPLAY UTILITIES
// ============================================================================

import * as date from './date.js';

/**
 * Get wind direction arrow character based on a cardinal direction.
 * @param {string} direction - Cardinal direction (N, NE, E, SE, S, SW, W, NW)
 * @returns {string} Arrow character pointing in wind direction
 */
export function getWindArrow(direction) {
    const arrows = {
        'N': '↓', 'NE': '↙', 'E': '←', 'SE': '↖',
        'S': '↑', 'SW': '↗', 'W': '→', 'NW': '↘'
    };
    return arrows[direction] || '•';
}

/**
 * Get rotation angle for wind direction arrow.
 * @param {string} direction - Cardinal direction (N, NE, E, SE, S, SW, W, NW)
 * @returns {number} Rotation angle in degrees
 */
export function getWindRotation(direction) {
    const rotations = {
        'N': 0, 'NE': 45, 'E': 90, 'SE': 135,
        'S': 180, 'SW': 225, 'W': 270, 'NW': 315
    };
    return rotations[direction] || 0;
}

/**
 * Get CSS class for wind intensity (with 'wind-' prefix for index page).
 * @param {number} windValue - Wind speed in knots
 * @returns {string} CSS class name (wind-weak, wind-moderate, wind-strong, wind-extreme)
 */
export function getWindClass(windValue) {
    if (windValue < 12) {
        return 'wind-weak';
    } else if (windValue >= 12 && windValue < 18) {
        return 'wind-moderate';
    } else if (windValue >= 18 && windValue <= 25) {
        return 'wind-strong';
    } else {
        return 'wind-extreme';
    }
}

/**
 * Get CSS class for wind intensity on the map. Unlike getWindClass it splits the
 * sub-rideable range so the map reads as a cold -> warm scale: grey when there is
 * effectively no wind, blue when it blows but is still not rideable.
 * @param {number} windValue - Wind speed in knots
 * @returns {string} CSS class name (wind-calm, wind-light, wind-moderate, wind-strong, wind-extreme)
 */
export function getMapWindClass(windValue) {
    if (windValue < 5) {
        return 'wind-calm';
    } else if (windValue < 12) {
        return 'wind-light';
    }
    return getWindClass(windValue);
}

/**
 * Get simple wind class for TV display (without 'wind-' prefix).
 * @param {number} wind - Wind speed in knots
 * @param {number} gusts - Gust speed in knots
 * @returns {string} Class name (weak, moderate, strong, extreme)
 */
export function getWindClassSimple(wind, gusts) {
    const avgWind = (wind + gusts) / 2;
    if (avgWind < 12) return 'weak';
    if (avgWind >= 12 && avgWind < 18) return 'moderate';
    if (avgWind >= 18 && avgWind <= 25) return 'strong';
    return 'extreme';
}

// ============================================================================
// CURRENT CONDITIONS
// ============================================================================

/**
 * Coerce an API value to a finite number.
 * @param {*} value - Raw value
 * @returns {number|null} The number, or null when it isn't one
 */
export function toNumber(value) {
    if (typeof value === 'number' && Number.isFinite(value)) {
        return value;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
}

/**
 * Pick the forecast entry that best stands in for "right now": the hourly entry
 * closest to the current time, falling back to today's daily entry.
 * @param {object} spot - Spot object
 * @returns {object|null} Forecast entry, or null when the spot has none
 */
function selectForecastForConditions(spot) {
    if (!spot) {
        return null;
    }

    if (Array.isArray(spot.forecastHourly) && spot.forecastHourly.length > 0) {
        const closest = date.findClosestForecast(spot.forecastHourly);
        if (closest) {
            return closest;
        }
    }

    if (Array.isArray(spot.forecast) && spot.forecast.length > 0) {
        const todayForecast = spot.forecast.find(forecast => typeof forecast.date === 'string' && forecast.date.toLowerCase() === 'today');
        return todayForecast || spot.forecast[0];
    }

    return null;
}

/**
 * Current wind conditions for a spot: the live station reading when the spot has
 * one, otherwise the forecast entry closest to now. `isCurrent` says which of the
 * two it is, and `forecastDate` carries the raw date of the forecast fallback so
 * callers can label it however they format dates.
 *
 * @param {object} spot - Spot object
 * @returns {{wind:number, gusts:number, direction:string, temp:number|null,
 *   precipitation:number|null, isCurrent:boolean, forecastDate:string|null}|null}
 */
export function getWindConditions(spot) {
    if (!spot) {
        return null;
    }

    const current = spot.currentConditions;
    if (current) {
        const wind = toNumber(current.wind);
        const gusts = toNumber(current.gusts);
        if (wind !== null && gusts !== null) {
            return {
                wind,
                gusts,
                direction: current.direction || '',
                temp: toNumber(current.temp),
                precipitation: null,
                isCurrent: true,
                forecastDate: null
            };
        }
    }

    const forecast = selectForecastForConditions(spot);
    if (!forecast) {
        return null;
    }

    const wind = toNumber(forecast.wind);
    const gusts = toNumber(forecast.gusts);
    if (wind === null || gusts === null) {
        return null;
    }

    return {
        wind,
        gusts,
        direction: forecast.direction || '',
        temp: toNumber(forecast.temp),
        precipitation: toNumber(forecast.precipitation),
        isCurrent: false,
        forecastDate: forecast.date || null
    };
}
