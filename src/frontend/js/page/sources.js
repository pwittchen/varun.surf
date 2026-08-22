import * as toolsPage from '../common/toolsPage.js';
import { t, locale } from '../common/translations.js';

// ============================================================================
// EXTERNAL SOURCE HEALTH CHECK FUNCTIONS
// ============================================================================

// The last payload, kept so a language switch redraws the rows already on
// screen instead of waiting for the next 30s refresh
let lastSources = null;
let lastUpdatedAt = null;

async function checkSources() {
    try {
        const response = await fetch('/api/v1/status/sources', { credentials: 'same-origin' });
        if (!response.ok) {
            throw new Error('Failed to fetch sources');
        }
        lastSources = await response.json();
        lastUpdatedAt = new Date();
        renderAll();
    } catch (error) {
        console.error('Error checking sources:', error);
    }
}

function renderSources(containerId, sources) {
    const container = document.getElementById(containerId);
    if (!sources || sources.length === 0) {
        container.innerHTML = `<div class="status-endpoint"><span>${t('sourcesNone')}</span></div>`;
        return;
    }

    container.innerHTML = sources.map(source => {
        const dotClass = source.ok ? 'status-endpoint-dot status-endpoint-dot-up' : 'status-endpoint-dot status-endpoint-dot-down';
        const statusText = source.ok
            ? `<span class="status-endpoint-text">${t('statusEndpointOperational')}</span> <span class="status-endpoint-latency">(${source.latencyMs}ms)</span>`
            : `<span class="status-endpoint-text">${t('statusEndpointUnreachable')}</span>`;

        return `
            <div class="status-endpoint">
                <div class="status-endpoint-info">
                    <span class="${dotClass}"></span>
                    <span class="status-endpoint-name">${source.name} <a href="${source.url}" target="_blank" rel="noopener noreferrer" class="source-link">${source.displayUrl}</a></span>
                </div>
                <span class="status-endpoint-status">${statusText}</span>
            </div>
        `;
    }).join('');
}

function renderStationLinks(containerId, sources) {
    const container = document.getElementById(containerId);
    if (!sources || sources.length === 0) {
        container.innerHTML = `<div class="status-endpoint"><span>${t('sourcesNone')}</span></div>`;
        return;
    }

    container.innerHTML = sources.map(source => `
        <div class="status-endpoint">
            <div class="status-endpoint-info">
                <span class="status-endpoint-name">${source.name} <a href="${source.url}" target="_blank" rel="noopener noreferrer" class="source-link">${source.displayUrl}</a></span>
            </div>
        </div>
    `).join('');
}

function renderLastUpdated() {
    const el = document.getElementById('last-updated');
    if (!el) {
        return;
    }
    const time = lastUpdatedAt ? lastUpdatedAt.toLocaleTimeString(locale()) : '-';
    el.textContent = `${t('statusLastUpdated')}: ${time}`;
}

// Everything this page renders itself, redrawn from the last payload received
function renderAll() {
    renderLastUpdated();
    if (!lastSources) {
        return;
    }
    renderSources('forecast-sources', lastSources.forecastSources);
    renderStationLinks('live-station-sources', lastSources.liveStationSources);
    renderStationLinks('spots-data-sources', lastSources.spotsDataSources);
}

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    toolsPage.setup({ onLanguageChange: renderAll });

    // Initial load
    checkSources();
    // Auto-refresh every 30 seconds
    setInterval(checkSources, 30000);
});
