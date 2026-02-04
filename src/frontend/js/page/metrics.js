import * as footer from '../common/footer.js';
import * as routing from '../common/routing.js';
import * as state from '../common/state.js';
import * as translations from '../common/translations.js';

// ============================================================================
// THEME INITIALIZATION
// ============================================================================

state.applyTheme(state.getTheme());

// ============================================================================
// STATE
// ============================================================================

let autoRefreshEnabled = true;
let refreshInterval = null;
const REFRESH_INTERVAL_MS = 5000;
const SESSION_CREDENTIALS_KEY = 'metrics_credentials';
const METRICS_USERNAME = 'admin';

// History data for charts (keep last 60 data points = 5 minutes of data)
const MAX_HISTORY_POINTS = 60;
const cpuHistory = {
    process: [],
    system: []
};
const ramHistory = {
    used: [],
    max: []
};
const threadsHistory = {
    live: [],
    daemon: []
};

// ============================================================================
// AUTHENTICATION
// ============================================================================

function getStoredCredentials() {
    return sessionStorage.getItem(SESSION_CREDENTIALS_KEY) || '';
}

function storeCredentials(password) {
    const credentials = btoa(`${METRICS_USERNAME}:${password}`);
    sessionStorage.setItem(SESSION_CREDENTIALS_KEY, credentials);
}

function clearCredentials() {
    sessionStorage.removeItem(SESSION_CREDENTIALS_KEY);
}

function showLoginForm() {
    // Stop auto-refresh when showing login form
    stopAutoRefresh();
    autoRefreshEnabled = false;

    const container = document.querySelector('.status-container');
    container.innerHTML = `
        <div class="status-page-header">
            <div class="status-page-header-content">
                <h1 class="status-page-title"><span id="headerTitle">VARUN.SURF</span></h1>
            </div>
        </div>
        <h2>Application Metrics</h2>
        <div class="status-card">
            <h3>Authentication Required</h3>
            <form id="login-form" class="metrics-login-form">
                <div class="metrics-login-field">
                    <label for="password">Password</label>
                    <input type="password" id="password" name="password" autocomplete="current-password" required>
                </div>
                <div id="login-error" class="metrics-login-error"></div>
                <button type="submit" class="btn btn-primary">Login</button>
            </form>
        </div>
        <div class="status-actions">
            <a href="/status" class="btn btn-secondary">View Status</a>
            <a href="/logs/" class="btn btn-secondary">View Logs</a>
            <a href="/" class="btn btn-secondary">View Dashboard</a>
        </div>
    `;

    const headerTitle = document.getElementById('headerTitle');
    if (headerTitle) {
        headerTitle.addEventListener('click', routing.navigateToHome);
    }

    document.getElementById('login-form').addEventListener('submit', handleLogin);
}

async function handleLogin(e) {
    e.preventDefault();
    const password = document.getElementById('password').value;
    const errorEl = document.getElementById('login-error');

    try {
        const credentials = btoa(`${METRICS_USERNAME}:${password}`);
        const response = await fetch('/api/v1/metrics', {
            headers: { 'Authorization': `Basic ${credentials}` }
        });

        if (response.ok) {
            storeCredentials(password);
            window.location.reload();
        } else if (response.status === 401) {
            errorEl.textContent = 'Invalid password';
        } else {
            errorEl.textContent = 'Authentication failed';
        }
    } catch (error) {
        errorEl.textContent = 'Authentication failed';
    }
}

// ============================================================================
// API
// ============================================================================

async function fetchMetrics() {
    const credentials = getStoredCredentials();
    const headers = {};
    if (credentials) {
        headers['Authorization'] = `Basic ${credentials}`;
    }

    const response = await fetch('/api/v1/metrics', { headers });
    if (response.status === 401) {
        clearCredentials();
        showLoginForm();
        throw new Error('Unauthorized');
    }
    if (!response.ok) {
        throw new Error('Failed to fetch metrics');
    }
    return await response.json();
}

async function fetchMetricsHistory() {
    const credentials = getStoredCredentials();
    const headers = {};
    if (credentials) {
        headers['Authorization'] = `Basic ${credentials}`;
    }

    const response = await fetch('/api/v1/metrics/history', { headers });
    if (response.status === 401) {
        clearCredentials();
        showLoginForm();
        throw new Error('Unauthorized');
    }
    if (!response.ok) {
        throw new Error('Failed to fetch metrics history');
    }
    return await response.json();
}

function loadHistoryData(historyData) {
    // Clear existing history
    cpuHistory.process.length = 0;
    cpuHistory.system.length = 0;
    ramHistory.used.length = 0;
    ramHistory.max.length = 0;
    threadsHistory.live.length = 0;
    threadsHistory.daemon.length = 0;

    // Populate with historical data
    for (const snapshot of historyData) {
        cpuHistory.process.push(snapshot.cpuProcess || 0);
        cpuHistory.system.push(snapshot.cpuSystem || 0);
        ramHistory.used.push((snapshot.heapUsed || 0) / (1024 * 1024)); // Convert to MB
        ramHistory.max.push((snapshot.heapMax || 0) / (1024 * 1024));
        threadsHistory.live.push(snapshot.threadsLive || 0);
        threadsHistory.daemon.push(snapshot.threadsDaemon || 0);
    }
}

// ============================================================================
// FORMATTERS
// ============================================================================

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function formatNumber(num) {
    if (num === null || num === undefined || isNaN(num)) return '-';
    return Math.round(num).toLocaleString();
}

function formatDecimal(num, decimals = 1) {
    if (num === null || num === undefined || isNaN(num)) return '-';
    return num.toFixed(decimals);
}

function formatDuration(seconds) {
    if (!seconds || seconds <= 0) return '-';
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);

    if (days > 0) return `${days}d ${hours}h ${minutes}m`;
    if (hours > 0) return `${hours}h ${minutes}m ${secs}s`;
    if (minutes > 0) return `${minutes}m ${secs}s`;
    return `${secs}s`;
}

function formatTimestamp(timestamp) {
    if (!timestamp || timestamp <= 0) return '-';
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
}

function formatMsDuration(ms) {
    if (ms === null || ms === undefined || isNaN(ms) || ms <= 0) return { value: '-', unit: '' };
    const seconds = ms / 1000;
    if (seconds >= 60) {
        const minutes = seconds / 60;
        return { value: formatDecimal(minutes, 2), unit: 'min' };
    }
    return { value: formatDecimal(seconds, 2), unit: 's' };
}

// ============================================================================
// UI UPDATES
// ============================================================================

function updateJvmMetrics(jvm) {
    // Heap memory
    const heapUsed = jvm.heapUsed || 0;
    const heapMax = jvm.heapMax || 1;
    const heapPercent = jvm.heapUsedPercent || 0;

    const heapBar = document.getElementById('heap-bar');
    heapBar.style.width = `${Math.min(heapPercent, 100)}%`;
    heapBar.className = 'metrics-progress-bar' +
        (heapPercent > 80 ? ' metrics-progress-danger' : heapPercent > 60 ? ' metrics-progress-warning' : '');

    document.getElementById('heap-value').textContent =
        `${formatBytes(heapUsed)} / ${formatBytes(heapMax)} (${formatDecimal(heapPercent)}%)`;

    // CPU
    const cpuUsage = jvm.cpuUsage || 0;
    document.getElementById('cpu-value').textContent = `${formatDecimal(cpuUsage)}%`;
    updateMiniChart('cpu-chart', cpuUsage);

    // Store CPU in history
    cpuHistory.process.push(cpuUsage);
    cpuHistory.system.push(jvm.systemCpuUsage || 0);
    if (cpuHistory.process.length > MAX_HISTORY_POINTS) {
        cpuHistory.process.shift();
        cpuHistory.system.shift();
    }

    // Store RAM in history (in MB for readability)
    ramHistory.used.push(heapUsed / (1024 * 1024));
    ramHistory.max.push(heapMax / (1024 * 1024));
    if (ramHistory.used.length > MAX_HISTORY_POINTS) {
        ramHistory.used.shift();
        ramHistory.max.shift();
    }

    // Threads
    document.getElementById('threads-value').textContent = formatNumber(jvm.threadsLive);
    document.getElementById('threads-detail').textContent =
        `peak: ${formatNumber(jvm.threadsPeak)} | daemon: ${formatNumber(jvm.threadsDaemon)}`;

    // Store threads in history
    threadsHistory.live.push(jvm.threadsLive || 0);
    threadsHistory.daemon.push(jvm.threadsDaemon || 0);
    if (threadsHistory.live.length > MAX_HISTORY_POINTS) {
        threadsHistory.live.shift();
        threadsHistory.daemon.shift();
    }

    // Uptime
    document.getElementById('jvm-uptime').textContent = formatDuration(jvm.uptimeSeconds);

    // GC
    const gcCount = jvm.gcPauseTime?.count || 0;
    const gcTotalTime = jvm.gcPauseTime?.totalTimeMs || 0;
    const gcAvgTime = jvm.gcPauseTime?.meanMs || 0;
    document.getElementById('gc-count').textContent = formatNumber(gcCount);
    document.getElementById('gc-time').textContent = `total: ${formatDecimal(gcTotalTime, 0)} ms | avg: ${formatDecimal(gcAvgTime, 1)} ms`;
}

function updateMiniChart(elementId, value) {
    const container = document.getElementById(elementId);
    const percent = Math.min(Math.max(value, 0), 100);

    // Create a simple circular progress indicator
    const size = 40;
    const strokeWidth = 4;
    const radius = (size - strokeWidth) / 2;
    const circumference = radius * 2 * Math.PI;
    const offset = circumference - (percent / 100) * circumference;

    container.innerHTML = `
        <svg width="${size}" height="${size}" class="metrics-mini-chart">
            <circle
                class="metrics-mini-chart-bg"
                stroke-width="${strokeWidth}"
                fill="transparent"
                r="${radius}"
                cx="${size / 2}"
                cy="${size / 2}"
            />
            <circle
                class="metrics-mini-chart-progress"
                stroke-width="${strokeWidth}"
                fill="transparent"
                r="${radius}"
                cx="${size / 2}"
                cy="${size / 2}"
                stroke-dasharray="${circumference} ${circumference}"
                stroke-dashoffset="${offset}"
                transform="rotate(-90 ${size / 2} ${size / 2})"
            />
        </svg>
    `;
}

function updateFetchingMetrics(counters, timers) {
    // Forecasts
    document.getElementById('forecast-total').textContent = formatNumber(counters.forecastsTotal);
    document.getElementById('forecast-success').textContent = formatNumber(counters.forecastsSuccess);
    document.getElementById('forecast-failure').textContent = `${formatNumber(counters.forecastsFailure)} failed`;
    const forecastDuration = formatMsDuration(timers.forecastsDuration?.meanMs);
    document.getElementById('forecast-duration').textContent =
        `avg: ${forecastDuration.value} ${forecastDuration.unit}`;

    // Conditions
    document.getElementById('conditions-total').textContent = formatNumber(counters.conditionsTotal);
    document.getElementById('conditions-success').textContent = formatNumber(counters.conditionsSuccess);
    document.getElementById('conditions-failure').textContent = `${formatNumber(counters.conditionsFailure)} failed`;
    const conditionsDuration = formatMsDuration(timers.conditionsDuration?.meanMs);
    document.getElementById('conditions-duration').textContent =
        `avg: ${conditionsDuration.value} ${conditionsDuration.unit}`;

    // AI
    document.getElementById('ai-total').textContent = formatNumber(counters.aiTotal);
    document.getElementById('ai-success').textContent = formatNumber(counters.aiSuccess);
    document.getElementById('ai-failure').textContent = `${formatNumber(counters.aiFailure)} failed`;
    const aiDuration = formatMsDuration(timers.aiDuration?.meanMs);
    document.getElementById('ai-duration').textContent =
        `avg: ${aiDuration.value} ${aiDuration.unit}`;
}

function updateCacheMetrics(gauges) {
    document.getElementById('spots-total').textContent = formatNumber(gauges.spotsTotal);
    document.getElementById('countries-total').textContent = formatNumber(gauges.countriesTotal);
    document.getElementById('forecasts-cached').textContent = formatNumber(gauges.forecastsCacheSize);
    document.getElementById('conditions-cached').textContent = formatNumber(gauges.conditionsCacheSize);

    document.getElementById('last-forecast-fetch').textContent = formatTimestamp(gauges.lastForecastFetch);
    document.getElementById('last-conditions-fetch').textContent = formatTimestamp(gauges.lastConditionsFetch);
}

function updateHttpServerMetrics(http, counters) {
    // Server metrics
    const serverRequests = http.serverRequests || {};
    document.getElementById('server-total').textContent = formatNumber(serverRequests.count);
    document.getElementById('server-avg-time').textContent = `${formatDecimal(serverRequests.meanMs)} ms`;
    document.getElementById('server-max-time').textContent = `${formatDecimal(serverRequests.maxMs)} ms`;
    document.getElementById('server-total-time').textContent = `${formatDecimal((serverRequests.totalTimeMs || 0) / 1000, 1)} s`;

    // API endpoint counters
    document.getElementById('api-spots-requests').textContent = formatNumber(counters.apiSpotsRequests);
    document.getElementById('api-spot-requests').textContent = formatNumber(counters.apiSpotRequests);
}

function updateHttpClientMetrics(http) {
    // Basic stats
    document.getElementById('http-active').textContent = formatNumber(http.activeRequests);
    document.getElementById('http-total').textContent = formatNumber(http.totalRequests);
    document.getElementById('http-failed').textContent = formatNumber(http.failedRequests);

    const successRate = http.totalRequests > 0
        ? (http.successRequests / http.totalRequests) * 100
        : 100;
    document.getElementById('http-success-rate').textContent = `${formatDecimal(successRate)}%`;

    // Timing metrics
    document.getElementById('http-duration').textContent =
        `${formatDecimal(http.requestDuration?.meanMs)} ms`;
    document.getElementById('http-max-duration').textContent =
        `${formatDecimal(http.requestDuration?.maxMs)} ms`;
    document.getElementById('http-connect-time').textContent =
        `${formatDecimal(http.connectDuration?.meanMs)} ms`;
    document.getElementById('http-dns-time').textContent =
        `${formatDecimal(http.dnsDuration?.meanMs)} ms`;

    // Connection pool
    document.getElementById('http-conn-acquired').textContent = formatNumber(http.connectionsAcquired);
    document.getElementById('http-conn-released').textContent = formatNumber(http.connectionsReleased);
}

function drawCpuHistoryChart() {
    const canvas = document.getElementById('cpu-history-chart');
    const ctx = canvas.getContext('2d');

    // Get computed styles for theme colors
    const computedStyle = getComputedStyle(document.documentElement);
    const textColor = computedStyle.getPropertyValue('--text-secondary').trim() || '#888';
    const borderColor = computedStyle.getPropertyValue('--border-primary').trim() || '#333';
    const processColor = '#3b82f6'; // Blue
    const systemColor = '#ef4444'; // Red

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const padding = { top: 10, right: 10, bottom: 20, left: 40 };
    const chartWidth = canvas.width - padding.left - padding.right;
    const chartHeight = canvas.height - padding.top - padding.bottom;

    // Draw background grid
    ctx.strokeStyle = borderColor;
    ctx.lineWidth = 0.5;
    ctx.beginPath();

    // Horizontal lines (0%, 50%, 100%)
    for (let i = 0; i <= 2; i++) {
        const y = padding.top + (chartHeight / 2) * i;
        ctx.moveTo(padding.left, y);
        ctx.lineTo(canvas.width - padding.right, y);
    }
    ctx.stroke();

    // Draw Y-axis labels
    ctx.fillStyle = textColor;
    ctx.font = '10px sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText('100%', padding.left - 5, padding.top + 4);
    ctx.fillText('50%', padding.left - 5, padding.top + chartHeight / 2 + 4);
    ctx.fillText('0%', padding.left - 5, padding.top + chartHeight + 4);

    // Draw lines if we have data
    if (cpuHistory.process.length > 1) {
        const pointWidth = chartWidth / (MAX_HISTORY_POINTS - 1);

        // Draw system CPU line
        ctx.strokeStyle = systemColor;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        cpuHistory.system.forEach((value, index) => {
            const x = padding.left + index * pointWidth;
            const y = padding.top + chartHeight - (value / 100) * chartHeight;
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();

        // Draw process CPU line
        ctx.strokeStyle = processColor;
        ctx.lineWidth = 2;
        ctx.beginPath();
        cpuHistory.process.forEach((value, index) => {
            const x = padding.left + index * pointWidth;
            const y = padding.top + chartHeight - (value / 100) * chartHeight;
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();
    }

    // Draw X-axis time labels
    ctx.fillStyle = textColor;
    ctx.textAlign = 'center';
    const now = new Date();
    ctx.fillText('now', canvas.width - padding.right, canvas.height - 5);
    ctx.fillText('-5min', padding.left, canvas.height - 5);
}

function drawRamHistoryChart() {
    const canvas = document.getElementById('ram-history-chart');
    const ctx = canvas.getContext('2d');

    // Get computed styles for theme colors
    const computedStyle = getComputedStyle(document.documentElement);
    const textColor = computedStyle.getPropertyValue('--text-secondary').trim() || '#888';
    const borderColor = computedStyle.getPropertyValue('--border-primary').trim() || '#333';
    const heapColor = '#10b981'; // Green
    const heapMaxColor = '#6b7280'; // Gray

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const padding = { top: 10, right: 10, bottom: 20, left: 50 };
    const chartWidth = canvas.width - padding.left - padding.right;
    const chartHeight = canvas.height - padding.top - padding.bottom;

    // Calculate max value for Y-axis (use heap max or a reasonable default)
    const maxHeapValue = ramHistory.max.length > 0
        ? Math.max(...ramHistory.max, ...ramHistory.used)
        : 512;
    const yAxisMax = Math.ceil(maxHeapValue / 100) * 100; // Round up to nearest 100 MB

    // Draw background grid
    ctx.strokeStyle = borderColor;
    ctx.lineWidth = 0.5;
    ctx.beginPath();

    // Horizontal lines (0, 50%, 100% of max)
    for (let i = 0; i <= 2; i++) {
        const y = padding.top + (chartHeight / 2) * i;
        ctx.moveTo(padding.left, y);
        ctx.lineTo(canvas.width - padding.right, y);
    }
    ctx.stroke();

    // Draw Y-axis labels
    ctx.fillStyle = textColor;
    ctx.font = '10px sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText(`${yAxisMax} MB`, padding.left - 5, padding.top + 4);
    ctx.fillText(`${Math.round(yAxisMax / 2)} MB`, padding.left - 5, padding.top + chartHeight / 2 + 4);
    ctx.fillText('0 MB', padding.left - 5, padding.top + chartHeight + 4);

    // Draw lines if we have data
    if (ramHistory.used.length > 1) {
        const pointWidth = chartWidth / (MAX_HISTORY_POINTS - 1);

        // Draw heap max line (dashed)
        ctx.strokeStyle = heapMaxColor;
        ctx.lineWidth = 1;
        ctx.setLineDash([4, 4]);
        ctx.beginPath();
        ramHistory.max.forEach((value, index) => {
            const x = padding.left + index * pointWidth;
            const y = padding.top + chartHeight - (value / yAxisMax) * chartHeight;
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();
        ctx.setLineDash([]);

        // Draw heap used line
        ctx.strokeStyle = heapColor;
        ctx.lineWidth = 2;
        ctx.beginPath();
        ramHistory.used.forEach((value, index) => {
            const x = padding.left + index * pointWidth;
            const y = padding.top + chartHeight - (value / yAxisMax) * chartHeight;
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();
    }

    // Draw X-axis time labels
    ctx.fillStyle = textColor;
    ctx.textAlign = 'center';
    ctx.fillText('now', canvas.width - padding.right, canvas.height - 5);
    ctx.fillText('-5min', padding.left, canvas.height - 5);
}

function drawThreadsHistoryChart() {
    const canvas = document.getElementById('threads-history-chart');
    const ctx = canvas.getContext('2d');

    // Get computed styles for theme colors
    const computedStyle = getComputedStyle(document.documentElement);
    const textColor = computedStyle.getPropertyValue('--text-secondary').trim() || '#888';
    const borderColor = computedStyle.getPropertyValue('--border-primary').trim() || '#333';
    const liveColor = '#8b5cf6'; // Purple
    const daemonColor = '#f59e0b'; // Amber

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const padding = { top: 10, right: 10, bottom: 20, left: 40 };
    const chartWidth = canvas.width - padding.left - padding.right;
    const chartHeight = canvas.height - padding.top - padding.bottom;

    // Calculate max value for Y-axis
    const maxThreads = threadsHistory.live.length > 0
        ? Math.max(...threadsHistory.live, ...threadsHistory.daemon)
        : 100;
    const yAxisMax = Math.ceil(maxThreads / 10) * 10 + 10; // Round up to nearest 10 + buffer

    // Draw background grid
    ctx.strokeStyle = borderColor;
    ctx.lineWidth = 0.5;
    ctx.beginPath();

    // Horizontal lines (0, 50%, 100% of max)
    for (let i = 0; i <= 2; i++) {
        const y = padding.top + (chartHeight / 2) * i;
        ctx.moveTo(padding.left, y);
        ctx.lineTo(canvas.width - padding.right, y);
    }
    ctx.stroke();

    // Draw Y-axis labels
    ctx.fillStyle = textColor;
    ctx.font = '10px sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText(`${yAxisMax}`, padding.left - 5, padding.top + 4);
    ctx.fillText(`${Math.round(yAxisMax / 2)}`, padding.left - 5, padding.top + chartHeight / 2 + 4);
    ctx.fillText('0', padding.left - 5, padding.top + chartHeight + 4);

    // Draw lines if we have data
    if (threadsHistory.live.length > 1) {
        const pointWidth = chartWidth / (MAX_HISTORY_POINTS - 1);

        // Draw daemon threads line
        ctx.strokeStyle = daemonColor;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        threadsHistory.daemon.forEach((value, index) => {
            const x = padding.left + index * pointWidth;
            const y = padding.top + chartHeight - (value / yAxisMax) * chartHeight;
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();

        // Draw live threads line
        ctx.strokeStyle = liveColor;
        ctx.lineWidth = 2;
        ctx.beginPath();
        threadsHistory.live.forEach((value, index) => {
            const x = padding.left + index * pointWidth;
            const y = padding.top + chartHeight - (value / yAxisMax) * chartHeight;
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();
    }

    // Draw X-axis time labels
    ctx.fillStyle = textColor;
    ctx.textAlign = 'center';
    ctx.fillText('now', canvas.width - padding.right, canvas.height - 5);
    ctx.fillText('-5min', padding.left, canvas.height - 5);
}

// ============================================================================
// MAIN REFRESH FUNCTION
// ============================================================================

async function refreshMetrics() {
    try {
        const data = await fetchMetrics();

        // Update all sections
        updateJvmMetrics(data.jvm || {});
        updateFetchingMetrics(data.counters || {}, data.timers || {});
        updateCacheMetrics(data.gauges || {});
        updateHttpServerMetrics(data.httpClient || {}, data.counters || {});
        updateHttpClientMetrics(data.httpClient || {});

        // Draw history charts
        drawCpuHistoryChart();
        drawRamHistoryChart();
        drawThreadsHistoryChart();

        // Update last updated time
        document.getElementById('last-updated').textContent =
            'Last updated: ' + new Date().toLocaleTimeString();

    } catch (error) {
        console.error('Error fetching metrics:', error);
    }
}

// ============================================================================
// AUTO-REFRESH CONTROLS
// ============================================================================

function toggleAutoRefresh() {
    autoRefreshEnabled = !autoRefreshEnabled;
    const button = document.getElementById('toggle-refresh');
    const statusEl = document.getElementById('refresh-status');
    const dotEl = document.querySelector('.metrics-refresh-dot');

    if (autoRefreshEnabled) {
        button.textContent = 'Pause Auto-Refresh';
        statusEl.textContent = 'Auto-refresh: 5s';
        dotEl.classList.remove('paused');
        dotEl.classList.add('status-dot-up');
        dotEl.classList.remove('status-dot-down');
        startAutoRefresh();
    } else {
        button.textContent = 'Resume Auto-Refresh';
        statusEl.textContent = 'Auto-refresh: paused';
        dotEl.classList.add('paused');
        dotEl.classList.remove('status-dot-up');
        stopAutoRefresh();
    }
}

function startAutoRefresh() {
    if (refreshInterval) {
        clearInterval(refreshInterval);
    }
    refreshInterval = setInterval(refreshMetrics, REFRESH_INTERVAL_MS);
}

function stopAutoRefresh() {
    if (refreshInterval) {
        clearInterval(refreshInterval);
        refreshInterval = null;
    }
}

// ============================================================================
// WIDE VIEW TOGGLE
// ============================================================================

const WIDE_VIEW_KEY = 'metrics_wide_view';

function toggleWideView() {
    const isWide = document.body.classList.toggle('wide-view');
    const button = document.getElementById('toggle-wide');
    const iconExpand = document.getElementById('icon-expand');
    const iconCollapse = document.getElementById('icon-collapse');

    if (isWide) {
        button.title = 'Narrow View';
        iconExpand.style.display = 'none';
        iconCollapse.style.display = 'block';
    } else {
        button.title = 'Wide View';
        iconExpand.style.display = 'block';
        iconCollapse.style.display = 'none';
    }
    localStorage.setItem(WIDE_VIEW_KEY, isWide ? 'true' : 'false');
}

function restoreWideView() {
    const isWide = localStorage.getItem(WIDE_VIEW_KEY) === 'true';
    if (isWide) {
        document.body.classList.add('wide-view');
        const button = document.getElementById('toggle-wide');
        const iconExpand = document.getElementById('icon-expand');
        const iconCollapse = document.getElementById('icon-collapse');
        if (button) {
            button.title = 'Narrow View';
        }
        if (iconExpand) {
            iconExpand.style.display = 'none';
        }
        if (iconCollapse) {
            iconCollapse.style.display = 'block';
        }
    }
}

// ============================================================================
// INITIALIZATION
// ============================================================================

async function initializeMetrics() {
    // Setup header title click handler
    const headerTitle = document.getElementById('headerTitle');
    if (headerTitle) {
        headerTitle.addEventListener('click', routing.navigateToHome);
    }

    // Setup toggle buttons
    document.getElementById('toggle-refresh').addEventListener('click', toggleAutoRefresh);
    document.getElementById('toggle-wide').addEventListener('click', toggleWideView);

    // Restore wide view preference
    restoreWideView();

    try {
        // Load historical data first
        const historyData = await fetchMetricsHistory();
        loadHistoryData(historyData);

        // Draw charts with historical data
        drawCpuHistoryChart();
        drawRamHistoryChart();
        drawThreadsHistoryChart();
    } catch (error) {
        console.error('Error loading metrics history:', error);
        // If unauthorized, the fetchMetricsHistory will show login form
        if (error.message === 'Unauthorized') {
            return;
        }
    }

    // Initial load of current metrics
    refreshMetrics();

    // Start auto-refresh
    startAutoRefresh();

    // Update footer
    footer.updateFooter(translations.t);
}

document.addEventListener('DOMContentLoaded', initializeMetrics);
