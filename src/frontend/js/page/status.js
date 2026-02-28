import * as translations from '../common/translations.js';
import * as footer from '../common/footer.js';
import * as api from '../common/api.js';
import * as routing from '../common/routing.js';
import * as state from '../common/state.js';

// ============================================================================
// THEME INITIALIZATION
// ============================================================================

// Set the initial theme
state.applyTheme(state.getTheme());

// ============================================================================
// STATUS API FUNCTIONS
// ============================================================================

async function fetchStatus() {
    try {
        const data = await api.fetchStatus();

        // Update status indicator
        const indicator = document.getElementById('status-indicator');
        const statusDot = indicator.querySelector('.status-dot');
        const statusText = indicator.querySelector('.status-text');

        if (data.status === 'UP') {
            statusDot.className = 'status-dot status-dot-up';
            statusText.textContent = 'All Systems Operational';
        } else {
            statusDot.className = 'status-dot status-dot-down';
            statusText.textContent = 'System Issues Detected';
        }

        // Update service information
        document.getElementById('version').textContent = data.version || 'unknown';
        document.getElementById('uptime').textContent = data.uptime || '-';
        document.getElementById('spots-count').textContent = data.spotsCount || '0';
        document.getElementById('countries-count').textContent = data.countriesCount || '0';
        document.getElementById('live-stations').textContent = data.liveStations || '0';

        if (data.startTime) {
            const startDate = new Date(data.startTime);
            document.getElementById('start-time').textContent = startDate.toLocaleString();
        }

        // Update last updated time
        document.getElementById('last-updated').textContent =
            'Last updated: ' + new Date().toLocaleTimeString();

    } catch (error) {
        console.error('Error fetching status:', error);
        const indicator = document.getElementById('status-indicator');
        const statusDot = indicator.querySelector('.status-dot');
        const statusText = indicator.querySelector('.status-text');
        statusDot.className = 'status-dot status-dot-down';
        statusText.textContent = 'Unable to Connect';
    }
}

// ============================================================================
// ENDPOINT HEALTH CHECK FUNCTIONS
// ============================================================================

async function checkEndpoint(endpoint) {
    const endpointEl = document.querySelector(`[data-endpoint="${endpoint}"]`);
    const statusSpan = endpointEl.querySelector('.status-endpoint-status');
    const dotEl = endpointEl.querySelector('.status-endpoint-dot');

    const result = await api.checkEndpointHealth(endpoint);

    if (result.ok) {
        statusSpan.innerHTML = `<span class="status-endpoint-text">operational</span> <span class="status-endpoint-latency">(${result.latency}ms)</span>`;
        dotEl.className = 'status-endpoint-dot status-endpoint-dot-up';
    } else if (result.error) {
        statusSpan.innerHTML = '<span class="status-endpoint-text">unreachable</span>';
        dotEl.className = 'status-endpoint-dot status-endpoint-dot-down';
    } else {
        statusSpan.innerHTML = `<span class="status-endpoint-text">error (${result.status})</span>`;
        dotEl.className = 'status-endpoint-dot status-endpoint-dot-down';
    }
}

async function checkAllEndpoints() {
    const endpoints = ['/api/v1/health', '/api/v1/status', '/api/v1/spots'];
    await Promise.all(endpoints.map(checkEndpoint));
}

// ============================================================================
// HEALTH HISTORY FUNCTIONS
// ============================================================================

async function fetchHealthHistory() {
    try {
        const response = await fetch('/api/v1/status/history', { credentials: 'same-origin' });
        if (!response.ok) {
            throw new Error('Failed to fetch health history');
        }
        const data = await response.json();
        renderHealthHistory(data);
    } catch (error) {
        console.error('Error fetching health history:', error);
    }
}

function renderHealthHistory(data) {
    const container = document.getElementById('health-history');
    const uptimeEl = document.getElementById('uptime-percentage');
    const periodEl = document.getElementById('history-period');

    if (!data || !data.history) {
        container.innerHTML = '<div class="health-history-empty">No history available</div>';
        return;
    }

    const { history, summary } = data;

    // Update uptime percentage
    const uptimePercent = summary.uptimePercentage || 100;
    uptimeEl.textContent = `${uptimePercent.toFixed(2)}% uptime`;
    uptimeEl.className = 'health-history-uptime' +
        (uptimePercent < 99 ? ' degraded' : '') +
        (uptimePercent < 95 ? ' down' : '');

    // Calculate time period
    if (summary.oldestCheckTimestamp) {
        const oldestDate = new Date(summary.oldestCheckTimestamp);
        const now = new Date();
        const diffMs = now - oldestDate;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMins / 60);
        const diffDays = Math.floor(diffHours / 24);

        if (diffDays > 0) {
            periodEl.textContent = `Last ${diffDays} day${diffDays > 1 ? 's' : ''}`;
        } else if (diffHours > 0) {
            periodEl.textContent = `Last ${diffHours} hour${diffHours > 1 ? 's' : ''}`;
        } else {
            periodEl.textContent = `Last ${diffMins} minute${diffMins > 1 ? 's' : ''}`;
        }
    }

    // Render bars
    const maxBars = 90;
    const barsToShow = Math.min(history.length, maxBars);

    // If we have fewer entries, pad with empty bars
    const emptyBars = maxBars - barsToShow;

    let html = '';

    // Add empty bars first (oldest)
    for (let i = 0; i < emptyBars; i++) {
        html += '<div class="health-history-bar empty"></div>';
    }

    // Add actual history bars (oldest to newest)
    for (let i = 0; i < barsToShow; i++) {
        const entry = history[i];
        const date = new Date(entry.timestamp);
        const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        const dateStr = date.toLocaleDateString([], { month: 'short', day: 'numeric' });
        const statusClass = entry.healthy ? '' : ' down';
        const statusText = entry.healthy ? 'Operational' : 'Down';
        const statusTextClass = entry.healthy ? 'up' : 'down';
        const latencyStr = entry.healthy && entry.latencyMs > 0 ? ` (${entry.latencyMs}ms)` : '';

        html += `
            <div class="health-history-bar${statusClass}">
                <div class="health-history-tooltip">
                    <span class="health-history-tooltip-time">${dateStr} ${timeStr}</span>
                    <span class="health-history-tooltip-status ${statusTextClass}">${statusText}${latencyStr}</span>
                </div>
            </div>
        `;
    }

    container.innerHTML = html;
}

async function refreshStatus() {
    await fetchStatus();
    await checkAllEndpoints();
    await fetchHealthHistory();
}

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    // Setup header title click handler
    const headerTitle = document.getElementById('headerTitle');
    if (headerTitle) {
        headerTitle.addEventListener('click', routing.navigateToHome);
    }

    // Initial load
    refreshStatus();
    // Auto-refresh every 30 seconds
    setInterval(refreshStatus, 30000);
    // Manual refresh button
    document.getElementById('refresh-status').addEventListener('click', refreshStatus);

    footer.updateFooter(translations.t);
});
