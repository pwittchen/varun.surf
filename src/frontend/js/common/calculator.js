// ============================================================================
// KITE & BOARD SIZE CALCULATOR
// Calculates recommended kite and board sizes based on wind, weight, and skill
// ============================================================================

import * as modals from './modals.js';

/**
 * Calculate recommended kite size based on wind speed, rider weight, and skill level
 * @param {number} windSpeed - Wind speed in knots
 * @param {number} riderWeight - Rider weight in kg
 * @param {string} skillLevel - Skill level and conditions (e.g., 'beginner-flat', 'advanced-large')
 * @returns {number} Recommended kite size in m²
 */
export function calculateKiteSize(windSpeed, riderWeight, skillLevel) {
    // Base calculation: kite size (m²) = rider weight (kg) * factor / wind speed (kts)
    let factor = 2.5; // Default factor for intermediate flat water

    // Adjust factor based on skill level and conditions
    const factorAdjustments = {
        'beginner-flat': 3.0,         // Beginners need larger kites
        'beginner-small': 2.8,        // Slightly smaller for small waves
        'intermediate-flat': 2.5,     // Standard factor
        'intermediate-medium': 2.3,   // Medium waves need smaller kites
        'advanced-flat': 2.2,         // Advanced riders can use smaller kites
        'advanced-medium': 2.0,       // Advanced with medium waves
        'advanced-large': 1.8         // Advanced with large waves need smallest kites
    };

    if (skillLevel && factorAdjustments[skillLevel]) {
        factor = factorAdjustments[skillLevel];
    }

    // Calculate base kite size
    let kiteSize = (riderWeight * factor) / windSpeed;

    // Round to the nearest common kite size (7, 9, 10, 12, 14, 15, 17 m²)
    const commonSizes = [7, 9, 10, 12, 14, 15, 17];
    kiteSize = commonSizes.reduce((prev, curr) =>
        Math.abs(curr - kiteSize) < Math.abs(prev - kiteSize) ? curr : prev
    );

    return kiteSize;
}

/**
 * Calculate recommended board size based on rider weight and skill level
 * @param {number} riderWeight - Rider weight in kg
 * @param {string} skillLevel - Skill level and conditions
 * @returns {string} Recommended board size with dimensions and type
 */
export function calculateBoardSize(riderWeight, skillLevel) {
    // Determine a board type first
    let boardType = 'Twin Tip';
    if (skillLevel.includes('large') || (skillLevel.includes('medium') && skillLevel.includes('advanced'))) {
        boardType = 'Surfboard/Directional';
    }

    if (boardType === 'Twin Tip') {
        // Twin Tip calculation with realistic sizes (125-160 cm)
        let baseSize = 125; // Minimum realistic size

        // Add size based on weight (up to 115 kg)
        const effectiveWeight = Math.min(riderWeight, 115);
        baseSize += (effectiveWeight - 50) * 0.5; // ~32.5 cm range for 50-115 kg

        // Adjust based on skill level
        const sizeAdjustments = {
            'beginner-flat': 1.15,          // Larger for stability
            'beginner-small': 1.10,
            'intermediate-flat': 1.0,       // Standard size
            'intermediate-medium': 0.98,
            'advanced-flat': 0.92,          // Smaller for maneuverability
            'advanced-medium': 0.90
        };

        const adjustment = sizeAdjustments[skillLevel] || 1.0;
        let boardSize = Math.round(baseSize * adjustment);

        // Round to nearest 2 cm
        boardSize = Math.round(boardSize / 2) * 2;

        // Apply minimum and maximum size constraints
        const minSize = 136; // Minimum board size
        let maxSize = 160; // Default for beginners
        if (skillLevel.includes('intermediate')) {
            maxSize = 142;
        } else if (skillLevel.includes('advanced')) {
            maxSize = 142;
        }
        boardSize = Math.max(minSize, Math.min(boardSize, maxSize));

        // Calculate width (typically 38-46 cm for twin tips)
        let width = 38 + Math.floor((boardSize - 136) / 4);
        width = Math.min(width, 46);

        return `${boardSize} x ${width} cm (${boardType})`;
    } else {
        // Surfboard/Directional sizes (typically 5'4" to 6'2")
        const sizeInFeet = riderWeight < 70 ? "5'6\"" : riderWeight < 85 ? "5'10\"" : "6'0\"";
        return `${sizeInFeet} (${boardType})`;
    }
}

/**
 * Validate input and return warning message if invalid
 * @param {number} windSpeed - Wind speed in knots
 * @param {number} riderWeight - Rider weight in kg
 * @param {string} skillLevel - Skill level
 * @returns {string|null} Warning message or null if valid
 */
export function validateInput(windSpeed, riderWeight, skillLevel) {
    if (!windSpeed || windSpeed < 5 || windSpeed > 50) {
        return '⚠️ <strong>INVALID WIND SPEED</strong><br><br>Please enter a valid wind speed between 5 and 50 knots.';
    }

    if (!riderWeight || riderWeight > 150) {
        return '⚠️ <strong>INVALID RIDER WEIGHT</strong><br><br>Please enter a valid rider weight between 40 and 150 kg.';
    }

    if (riderWeight < 40) {
        return '⚠️ <strong>WEIGHT TOO LOW</strong><br><br>Your weight is too low and riding may be dangerous. Kitesurfing requires sufficient body weight for control and safety.';
    }

    if (!skillLevel) {
        return '⚠️ <strong>SKILL LEVEL REQUIRED</strong><br><br>Please select your skill level and conditions.';
    }

    if (riderWeight > 120) {
        return '😉 Maybe you should lose some weight before going onto the water!';
    }

    if (windSpeed > 40) {
        return '⚠️ <strong>EXTREME CONDITIONS!</strong><br><br>Wind speeds above 40 knots are extremely dangerous. We strongly recommend NOT going onto the water in these conditions. Stay safe!';
    }

    if (windSpeed < 12) {
        return '💨 <strong>LOW WIND CONDITIONS</strong><br><br>There is not enough wind for riding with a regular setup. However, you could try going on a <strong>kite foil board</strong>, which works great in light wind conditions!';
    }

    if (windSpeed < 15 && (skillLevel.includes('medium') || skillLevel.includes('large'))) {
        return '🌊 <strong>INSUFFICIENT WIND FOR WAVE CONDITIONS</strong><br><br>Wind speed below 15 knots is not enough for riding in medium or large waves. Waves create additional resistance and you need more wind power to maintain speed and control. We recommend NOT going onto the water in these conditions.';
    }

    return null;
}

/**
 * Setup the kite size calculator modal and event handlers
 */
export function setupKiteSizeCalculator() {
    const kiteSizeButton = document.getElementById('kiteSizeToggle');
    const kiteSizeModal = document.getElementById('kiteSizeModal');
    const kiteSizeCloseButton = document.getElementById('kiteSizeModalClose');
    const calculateBtn = document.getElementById('calculateBtn');

    if (!kiteSizeButton || !kiteSizeModal) return;

    // Close modal function
    function closeKiteSizeModal() {
        modals.closeModal('kiteSizeModal');
    }

    // Open modal
    kiteSizeButton.addEventListener('click', () => {
        modals.openModal('kiteSizeModal');
        // Reset result visibility
        const calcResult = document.getElementById('calcResult');
        if (calcResult) {
            calcResult.classList.remove('show');
        }
    });

    // Close button
    if (kiteSizeCloseButton) {
        kiteSizeCloseButton.addEventListener('click', closeKiteSizeModal);
    }

    // Click outside to close
    kiteSizeModal.addEventListener('click', (e) => {
        if (e.target === kiteSizeModal) {
            closeKiteSizeModal();
        }
    });

    // Calculate button
    if (calculateBtn) {
        calculateBtn.addEventListener('click', () => {
            const windSpeed = parseFloat(document.getElementById('windSpeed').value);
            const riderWeight = parseFloat(document.getElementById('riderWeight').value);
            const skillLevel = document.getElementById('skillLevel').value;
            const calcWarning = document.getElementById('calcWarning');
            const calcResult = document.getElementById('calcResult');

            // Hide previous warnings and results
            calcWarning.style.display = 'none';
            calcResult.classList.remove('show');

            // Validate input
            const warning = validateInput(windSpeed, riderWeight, skillLevel);
            if (warning) {
                calcWarning.innerHTML = warning;
                calcWarning.style.display = 'block';
                return;
            }

            // Calculate
            const kiteSize = calculateKiteSize(windSpeed, riderWeight, skillLevel);
            const boardSize = calculateBoardSize(riderWeight, skillLevel);

            // Display results
            document.getElementById('kiteSize').textContent = `${kiteSize} m²`;
            document.getElementById('boardSize').textContent = boardSize;
            calcResult.classList.add('show');
        });
    }

    // Escape key support
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && modals.isModalActive('kiteSizeModal')) {
            closeKiteSizeModal();
        }
    });
}