// ============================================================================
// DOM UTILITIES
// ============================================================================

/**
 * Updates all footer year elements with the current year.
 */
export function updateFooterYear() {
    const yearElements = document.querySelectorAll('.footer-year');
    if (!yearElements.length) {
        return;
    }
    const currentYear = new Date().getFullYear();
    yearElements.forEach((el) => {
        el.textContent = currentYear + " ";
    });
}
