// ============================================================================
// KITE & BOARD SIZE CALCULATOR
// Calculates recommended kite and board sizes based on wind, weight, and skill
// ============================================================================

import * as modals from './modals.js';
import * as state from './state.js';
import { t } from './translations.js';

// Key of the warning currently shown, so a language switch can rewrite it in place
let shownWarningKey = null;

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
 * Validate input and return the translation key of the warning to show.
 * The key rather than the wording, so the message follows the language switch
 * and can be rewritten in place while the calculator stays open.
 * @param {number} windSpeed - Wind speed in knots
 * @param {number} riderWeight - Rider weight in kg
 * @param {string} skillLevel - Skill level
 * @returns {string|null} Warning translation key or null if valid
 */
export function validateInput(windSpeed, riderWeight, skillLevel) {
    if (!windSpeed || windSpeed < 5 || windSpeed > 50) {
        return 'warningInvalidWindSpeed';
    }

    if (!riderWeight || riderWeight > 150) {
        return 'warningInvalidWeight';
    }

    if (riderWeight < 40) {
        return 'warningWeightTooLow';
    }

    if (!skillLevel) {
        return 'warningSkillLevelRequired';
    }

    if (riderWeight > 120) {
        return 'warningWeightTooHigh';
    }

    if (windSpeed > 40) {
        return 'warningExtremeWind';
    }

    if (windSpeed < 12) {
        return 'warningLowWind';
    }

    if (windSpeed < 15 && (skillLevel.includes('medium') || skillLevel.includes('large'))) {
        return 'warningInsufficientWindWaves';
    }

    return null;
}

/**
 * Restore the last entered inputs into the calculator form.
 * On the single spot view the spot wind speed always wins over the remembered one
 * and overwrites it, so the remembered value stays in sync with the last used data.
 * @param {number|null} spotWindSpeed - Wind speed of the displayed spot in knots
 */
function restoreInputs(spotWindSpeed) {
    const windSpeedInput = document.getElementById('windSpeed');
    const riderWeightInput = document.getElementById('riderWeight');
    const skillLevelSelect = document.getElementById('skillLevel');
    const saved = state.getCalculatorInputs() || {};

    if (windSpeedInput) {
        if (spotWindSpeed !== null && spotWindSpeed !== undefined) {
            windSpeedInput.value = spotWindSpeed;
        } else if (saved.windSpeed !== undefined && saved.windSpeed !== null) {
            windSpeedInput.value = saved.windSpeed;
        }
    }

    if (riderWeightInput && saved.riderWeight !== undefined && saved.riderWeight !== null) {
        riderWeightInput.value = saved.riderWeight;
    }

    if (skillLevelSelect && saved.skillLevel) {
        skillLevelSelect.value = saved.skillLevel;
    }

    // Persist right away, so the spot wind speed overwrites the remembered one
    rememberInputs();
}

/**
 * Remember the currently entered inputs so they are restored next time
 */
function rememberInputs() {
    const windSpeedInput = document.getElementById('windSpeed');
    const riderWeightInput = document.getElementById('riderWeight');
    const skillLevelSelect = document.getElementById('skillLevel');

    state.setCalculatorInputs({
        windSpeed: windSpeedInput && windSpeedInput.value !== '' ? windSpeedInput.value : null,
        riderWeight: riderWeightInput && riderWeightInput.value !== '' ? riderWeightInput.value : null,
        skillLevel: skillLevelSelect ? skillLevelSelect.value : ''
    });
}

/**
 * Read the wind speed provided by the page (single spot view) in knots
 * @param {function(): (number|null)} [getSpotWindSpeed] - Spot wind speed provider
 * @returns {number|null} Rounded wind speed in knots or null when unavailable
 */
function readSpotWindSpeed(getSpotWindSpeed) {
    if (typeof getSpotWindSpeed !== 'function') return null;
    const windSpeed = getSpotWindSpeed();
    if (windSpeed === null || windSpeed === undefined) return null;
    const parsed = parseFloat(windSpeed);
    return Number.isFinite(parsed) ? Math.round(parsed) : null;
}

/**
 * Setup the kite size calculator modal and event handlers
 * @param {Object} [options] - Setup options
 * @param {function(): (number|null)} [options.getSpotWindSpeed] - Returns the current wind speed
 *        in knots for the displayed spot (live station or forecast), used on the single spot view
 *        to always prefill and overwrite the remembered wind speed
 */
export function setupKiteSizeCalculator(options = {}) {
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
        // Restore last entered data, prefilling wind speed with the spot wind speed when available
        restoreInputs(readSpotWindSpeed(options.getSpotWindSpeed));
        // Reset warning and result visibility
        const calcWarning = document.getElementById('calcWarning');
        if (calcWarning) {
            calcWarning.style.display = 'none';
        }
        shownWarningKey = null;
        const calcResult = document.getElementById('calcResult');
        if (calcResult) {
            calcResult.classList.remove('show');
        }
    });

    // Remember inputs as they change, so they survive closing the modal without calculating
    ['windSpeed', 'riderWeight', 'skillLevel'].forEach(id => {
        const element = document.getElementById(id);
        if (element) {
            element.addEventListener('change', rememberInputs);
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
            shownWarningKey = null;

            // Remember the entered data for the next time the calculator is opened
            rememberInputs();

            // Validate input
            const warningKey = validateInput(windSpeed, riderWeight, skillLevel);
            if (warningKey) {
                shownWarningKey = warningKey;
                calcWarning.innerHTML = t(warningKey);
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

/**
 * Apply the current language to the kite/board size calculator modal.
 * @param {function(string): string} t - Translation lookup
 */
export function updateTranslations(t) {
    // A warning left on screen follows the switch too, rather than staying in the
    // language it was shown in
    const calcWarning = document.getElementById('calcWarning');
    if (calcWarning && shownWarningKey) {
        calcWarning.innerHTML = t(shownWarningKey);
    }

    const kiteSizeModalTitle = document.querySelector('#kiteSizeModal .modal-title span');
    if (kiteSizeModalTitle) {
        kiteSizeModalTitle.textContent = t('kiteSizeCalculatorTitle');
    }

    const windSpeedLabel = document.querySelector('label[for="windSpeed"]');
    if (windSpeedLabel) {
        windSpeedLabel.textContent = t('windSpeedLabel');
    }

    const windSpeedInput = document.getElementById('windSpeed');
    if (windSpeedInput) {
        windSpeedInput.placeholder = t('windSpeedPlaceholder');
    }

    const riderWeightLabel = document.querySelector('label[for="riderWeight"]');
    if (riderWeightLabel) {
        riderWeightLabel.textContent = t('riderWeightLabel');
    }

    const riderWeightInput = document.getElementById('riderWeight');
    if (riderWeightInput) {
        riderWeightInput.placeholder = t('riderWeightPlaceholder');
    }

    const skillLevelLabel = document.querySelector('label[for="skillLevel"]');
    if (skillLevelLabel) {
        skillLevelLabel.textContent = t('skillLevelLabel');
    }

    const skillLevelSelect = document.getElementById('skillLevel');
    if (skillLevelSelect) {
        const options = skillLevelSelect.querySelectorAll('option');
        options[0].textContent = t('skillLevelPlaceholder');
        options[1].textContent = t('skillBeginnerFlat');
        options[2].textContent = t('skillBeginnerSmall');
        options[3].textContent = t('skillIntermediateFlat');
        options[4].textContent = t('skillIntermediateMedium');
        options[5].textContent = t('skillAdvancedFlat');
        options[6].textContent = t('skillAdvancedMedium');
        options[7].textContent = t('skillAdvancedLarge');
    }

    const calculateBtn = document.getElementById('calculateBtn');
    if (calculateBtn) {
        calculateBtn.textContent = t('calculateButton');
    }

    const calcResultTitle = document.querySelector('#calcResult .calc-result-title');
    if (calcResultTitle) {
        calcResultTitle.textContent = t('recommendedEquipment');
    }

    const kiteSizeLabel = document.querySelector('#calcResult .calc-result-item:nth-child(2) .calc-result-label');
    if (kiteSizeLabel) {
        kiteSizeLabel.textContent = t('kiteSizeLabel');
    }

    const boardSizeLabel = document.querySelector('#calcResult .calc-result-item:nth-child(3) .calc-result-label');
    if (boardSizeLabel) {
        boardSizeLabel.textContent = t('boardSizeLabel');
    }

    const calcDisclaimer = document.querySelector('#calcResult .modal-disclaimer');
    if (calcDisclaimer) {
        calcDisclaimer.textContent = t('calcDisclaimer');
    }
}
