// ============================================================================
// FOOTER UTILITIES
// ============================================================================

// One wave period is 600 units and the path spans four of them, so sliding a
// layer by half its width (two periods) lands it exactly where it started
function wavePath(baseline, amplitude) {
    let d = `M0 ${baseline}`;
    for (let x = 0; x < 2400; x += 300) {
        const crest = (x / 300) % 2 === 0 ? baseline - amplitude : baseline + amplitude;
        d += ` Q${x + 150} ${crest} ${x + 300} ${baseline}`;
    }
    return `${d} V120 H0 Z`;
}

const FOOTER_ART = `
    <div class="footer-art-waves">
        <svg class="footer-wave footer-wave-back" viewBox="0 0 2400 120" preserveAspectRatio="none" focusable="false"><path d="${wavePath(56, 24)}"/></svg>
        <svg class="footer-wave footer-wave-mid" viewBox="0 0 2400 120" preserveAspectRatio="none" focusable="false"><path d="${wavePath(76, 18)}"/></svg>
        <svg class="footer-wave footer-wave-front" viewBox="0 0 2400 120" preserveAspectRatio="none" focusable="false"><path d="${wavePath(96, 13)}"/></svg>
    </div>
`;

// The art sits beside .footer-content rather than in it: the content is
// rewritten on every language switch, while the art is drawn once per page
function ensureFooterArt(footerContent) {
    const footer = footerContent.parentElement;
    if (!footer || footer.querySelector('.footer-art')) {
        return;
    }
    const art = document.createElement('div');
    art.className = 'footer-art';
    art.setAttribute('aria-hidden', 'true');
    art.innerHTML = FOOTER_ART;
    footer.insertBefore(art, footerContent);
}

/**
 * Renders the entire footer content dynamically with translated text.
 * @param {Function} t - Translation function that takes a key and returns translated string
 */
export function updateFooter(t) {
    const footerContent = document.querySelector('.footer-content');
    if (!footerContent) {
        return;
    }

    ensureFooterArt(footerContent);

    const currentYear = new Date().getFullYear();

    footerContent.innerHTML = `
        <p class="footer-disclaimer">${t('footerDisclaimer')}</p>
        <p class="footer-link">&copy; <span class="footer-year">${currentYear} </span><a href="https://varun.surf">varun.surf</a> • <a href="https://github.com/pwittchen/varun.surf">${t('footerOpenSource')}</a> • <span class="footer-made-in">${t('footerMadeInLabel')}</span> <a href="https://wittchen.io" target="_blank" class="footer-external-link">Piotr Wittchen</a> • <a href="https://github.com/pwittchen/varun.surf/releases">${t('footerChangelog')}</a> • <a href="/status">${t('footerStatus')}</a> • <a href="/sources">${t('footerSources')}</a> • <a href="/mcp">${t('footerMcp')}</a> • <a href="/llms.txt">${t('footerLlms')}</a></p>
    `;
}
