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
        const response = await fetch('/api/v1/status');
        if (!response.ok) throw new Error('Failed to fetch status');

        const data = await response.json();

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

    try {
        const startTime = performance.now();
        const response = await fetch(endpoint);
        const endTime = performance.now();
        const latency = Math.round(endTime - startTime);

        if (response.ok) {
            statusSpan.innerHTML = `<span class="status-endpoint-text">operational</span> <span class="status-endpoint-latency">(${latency}ms)</span>`;
            dotEl.className = 'status-endpoint-dot status-endpoint-dot-up';
        } else {
            statusSpan.innerHTML = `<span class="status-endpoint-text">error (${response.status})</span>`;
            dotEl.className = 'status-endpoint-dot status-endpoint-dot-down';
        }
    } catch (error) {
        statusSpan.innerHTML = '<span class="status-endpoint-text">unreachable</span>';
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

// Goes to the index.html page
function goToIndex() {
    window.location.href = '/'
}

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    // Initial load
    refreshStatus();
    // Auto-refresh every 30 seconds
    setInterval(refreshStatus, 30000);
    // Manual refresh button
    document.getElementById('refresh-status').addEventListener('click', refreshStatus);
});