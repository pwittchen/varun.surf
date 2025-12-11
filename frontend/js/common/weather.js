// ============================================================================
// WEATHER DISPLAY UTILITIES
// ============================================================================

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
