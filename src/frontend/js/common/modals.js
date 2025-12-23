// ============================================================================
// MODAL UTILITIES
// Common modal open/close functions and event setup helpers
// ============================================================================

/**
 * Opens a modal by adding 'active' class and preventing body scroll
 * @param {string} modalId - The ID of the modal element
 * @returns {HTMLElement|null} The modal element or null if not found
 */
export function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) {
        return null;
    }
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
    return modal;
}

/**
 * Closes a modal by removing 'active' class and restoring body scroll
 * @param {string} modalId - The ID of the modal element
 * @returns {HTMLElement|null} The modal element or null if not found
 */
export function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) {
        return null;
    }
    modal.classList.remove('active');
    document.body.style.overflow = 'auto';
    return modal;
}

/**
 * Checks if a modal is currently active/open
 * @param {string} modalId - The ID of the modal element
 * @returns {boolean} True if modal is active
 */
export function isModalActive(modalId) {
    const modal = document.getElementById(modalId);
    return modal ? modal.classList.contains('active') : false;
}

/**
 * Sets up a close button for a modal
 * @param {string} buttonId - The ID of the close button
 * @param {Function} closeCallback - Function to call when button is clicked
 */
export function setupCloseButton(buttonId, closeCallback) {
    const button = document.getElementById(buttonId);
    if (button) {
        button.addEventListener('click', closeCallback);
    }
}

/**
 * Sets up click-outside-to-close for a modal overlay
 * @param {string} modalId - The ID of the modal/overlay element
 * @param {Function} closeCallback - Function to call when overlay is clicked
 */
export function setupOverlayClose(modalId, closeCallback) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeCallback();
            }
        });
    }
}

/**
 * Sets up Escape key handler to close modals
 * @param {Array<{modalId: string, closeCallback: Function}>} modalConfigs - Array of modal configurations
 */
export function setupEscapeKeyHandler(modalConfigs) {
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            modalConfigs.forEach(({ modalId, closeCallback }) => {
                if (isModalActive(modalId)) {
                    closeCallback();
                }
            });
        }
    });
}

/**
 * Sets up a complete modal with close button, overlay click, and escape key
 * @param {Object} config - Modal configuration
 * @param {string} config.modalId - The ID of the modal element
 * @param {string} config.closeButtonId - The ID of the close button
 * @param {Function} config.closeCallback - Function to call to close the modal
 */
export function setupModal(config) {
    const { modalId, closeButtonId, closeCallback } = config;
    setupCloseButton(closeButtonId, closeCallback);
    setupOverlayClose(modalId, closeCallback);
}

/**
 * Batch setup for multiple modals with escape key handling
 * @param {Array<{modalId: string, closeButtonId: string, closeCallback: Function}>} modalConfigs
 */
export function setupModals(modalConfigs) {
    modalConfigs.forEach(config => {
        setupModal(config);
    });
    setupEscapeKeyHandler(modalConfigs);
}