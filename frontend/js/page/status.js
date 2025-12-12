import { t } from '../common/translations.js';
import { updateFooter } from '../common/footer.js';
import { fetchStatus as fetchStatusApi, checkEndpointHealth } from '../common/api.js';
import { navigateToHome } from '../common/routing.js';

// ============================================================================
// THEME INITIALIZATION
// ============================================================================

// Set the initial theme from localStorage
const savedTheme = localStorage.getItem('theme') || 'dark';
document.documentElement.setAttribute('data-theme', savedTheme);

// ============================================================================
// STATUS API FUNCTIONS
// ============================================================================

async function fetchStatus() {
    try {
        const data = await fetchStatusApi();

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

    const result = await checkEndpointHealth(endpoint);

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

async function refreshStatus() {
    await fetchStatus();
    await checkAllEndpoints();
}

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    // Setup header title click handler
    const headerTitle = document.getElementById('headerTitle');
    if (headerTitle) {
        headerTitle.addEventListener('click', navigateToHome);
    }

    // Initial load
    refreshStatus();
    // Auto-refresh every 30 seconds
    setInterval(refreshStatus, 30000);
    // Manual refresh button
    document.getElementById('refresh-status').addEventListener('click', refreshStatus);

    updateFooter(t);
});
