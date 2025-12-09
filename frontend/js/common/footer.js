// ============================================================================
// FOOTER UTILITIES
// ============================================================================

/**
 * Renders the entire footer content dynamically with translated text.
 * @param {Function} t - Translation function that takes a key and returns translated string
 */
export function updateFooter(t) {
    const footerContent = document.querySelector('.footer-content');
    if (!footerContent) {
        return;
    }

    const currentYear = new Date().getFullYear();

    footerContent.innerHTML = `
        <p class="footer-disclaimer">${t('footerDisclaimer')}</p>
        <p class="footer-link">&copy; <span class="footer-year">${currentYear} </span><a href="https://varun.surf">varun.surf</a> • <a href="https://github.com/pwittchen/varun.surf">${t('footerOpenSource')}</a> • <span class="footer-made-in">${t('footerMadeInLabel')}</span> <a href="https://wittchen.io" target="_blank" class="footer-external-link">Piotr Wittchen</a> • <a href="https://github.com/pwittchen/varun.surf/releases">${t('footerChangelog')}</a> • <a href="/status">${t('footerStatus')}</a></p>
    `;
}
