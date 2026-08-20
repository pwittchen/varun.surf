import * as toolsPage from '../common/toolsPage.js';

// ============================================================================
// EXTERNAL SOURCE HEALTH CHECK FUNCTIONS
// ============================================================================

async function checkSources() {
    try {
        const response = await fetch('/api/v1/status/sources', { credentials: 'same-origin' });
        if (!response.ok) {
            throw new Error('Failed to fetch sources');
        }
        const data = await response.json();
        renderSources('forecast-sources', data.forecastSources);
        renderStationLinks('live-station-sources', data.liveStationSources);
        renderStationLinks('spots-data-sources', data.spotsDataSources);

        document.getElementById('last-updated').textContent =
            'Last updated: ' + new Date().toLocaleTimeString();
    } catch (error) {
        console.error('Error checking sources:', error);
    }
}

function renderSources(containerId, sources) {
    const container = document.getElementById(containerId);
    if (!sources || sources.length === 0) {
        container.innerHTML = '<div class="status-endpoint"><span>No sources available</span></div>';
        return;
    }

    container.innerHTML = sources.map(source => {
        const dotClass = source.ok ? 'status-endpoint-dot status-endpoint-dot-up' : 'status-endpoint-dot status-endpoint-dot-down';
        const statusText = source.ok
            ? `<span class="status-endpoint-text">operational</span> <span class="status-endpoint-latency">(${source.latencyMs}ms)</span>`
            : '<span class="status-endpoint-text">unreachable</span>';

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
        container.innerHTML = '<div class="status-endpoint"><span>No sources available</span></div>';
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

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    toolsPage.setup();

    // Initial load
    checkSources();
    // Auto-refresh every 30 seconds
    setInterval(checkSources, 30000);
});
