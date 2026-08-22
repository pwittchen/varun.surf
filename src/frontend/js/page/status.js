import * as toolsPage from '../common/toolsPage.js';
import * as api from '../common/api.js';
import { t, plural, locale } from '../common/translations.js';

// ============================================================================
// LAST RENDERED STATE
// Every section keeps the payload it was last drawn from, so switching the
// language redraws what is on screen instead of leaving half the page in the
// old language until the next 30s refresh.
// ============================================================================

let lastStatus = null;
let lastStatusFailed = false;
let lastUpdatedAt = null;
const lastEndpointResults = new Map();
let lastHistory = null;

// ============================================================================
// STATUS API FUNCTIONS
// ============================================================================

function renderStatus() {
    const indicator = document.getElementById('status-indicator');
    const statusDot = indicator.querySelector('.status-dot');
    const statusText = indicator.querySelector('.status-text');

    if (lastStatusFailed) {
        statusDot.className = 'status-dot status-dot-down';
        statusText.textContent = t('statusUnableToConnect');
        return;
    }

    if (!lastStatus) {
        return;
    }

    if (lastStatus.status === 'UP') {
        statusDot.className = 'status-dot status-dot-up';
        statusText.textContent = t('statusAllOperational');
    } else {
        statusDot.className = 'status-dot status-dot-down';
        statusText.textContent = t('statusIssuesDetected');
    }

    document.getElementById('version').textContent = lastStatus.version || t('statusUnknownVersion');
    document.getElementById('uptime').textContent = lastStatus.uptime || '-';
    document.getElementById('spots-count').textContent = lastStatus.spotsCount || '0';
    document.getElementById('countries-count').textContent = lastStatus.countriesCount || '0';
    document.getElementById('live-stations').textContent = lastStatus.liveStations || '0';

    if (lastStatus.startTime) {
        document.getElementById('start-time').textContent =
            new Date(lastStatus.startTime).toLocaleString(locale());
    }
}

function renderLastUpdated() {
    const el = document.getElementById('last-updated');
    if (!el) {
        return;
    }
    const time = lastUpdatedAt ? lastUpdatedAt.toLocaleTimeString(locale()) : '-';
    el.textContent = `${t('statusLastUpdated')}: ${time}`;
}

async function fetchStatus() {
    try {
        lastStatus = await api.fetchStatus();
        lastStatusFailed = false;
        lastUpdatedAt = new Date();
        renderStatus();
        renderLastUpdated();
    } catch (error) {
        console.error('Error fetching status:', error);
        lastStatusFailed = true;
        renderStatus();
    }
}

// ============================================================================
// ENDPOINT HEALTH CHECK FUNCTIONS
// ============================================================================

const ENDPOINTS = ['/api/v1/health', '/api/v1/status', '/api/v1/spots', '/api/v1/wind'];

function renderEndpoint(endpoint) {
    const endpointEl = document.querySelector(`[data-endpoint="${endpoint}"]`);
    const result = lastEndpointResults.get(endpoint);
    if (!endpointEl || !result) {
        return;
    }

    const statusSpan = endpointEl.querySelector('.status-endpoint-status');
    const dotEl = endpointEl.querySelector('.status-endpoint-dot');

    // The placeholder is translated from the markup until the first probe comes
    // back; from here on this function owns the text
    statusSpan.removeAttribute('data-i18n');

    if (result.ok) {
        statusSpan.innerHTML = `<span class="status-endpoint-text">${t('statusEndpointOperational')}</span> <span class="status-endpoint-latency">(${result.latency}ms)</span>`;
        dotEl.className = 'status-endpoint-dot status-endpoint-dot-up';
    } else if (result.error) {
        statusSpan.innerHTML = `<span class="status-endpoint-text">${t('statusEndpointUnreachable')}</span>`;
        dotEl.className = 'status-endpoint-dot status-endpoint-dot-down';
    } else {
        statusSpan.innerHTML = `<span class="status-endpoint-text">${t('statusEndpointError')} (${result.status})</span>`;
        dotEl.className = 'status-endpoint-dot status-endpoint-dot-down';
    }
}

async function checkEndpoint(endpoint) {
    lastEndpointResults.set(endpoint, await api.checkEndpointHealth(endpoint));
    renderEndpoint(endpoint);
}

async function checkAllEndpoints() {
    await Promise.all(ENDPOINTS.map(checkEndpoint));
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
        lastHistory = await response.json();
        renderHealthHistory();
    } catch (error) {
        console.error('Error fetching health history:', error);
    }
}

// "Last 3 days" / "Ostatnie 3 dni" - the largest unit the history spans wins
function formatHistoryPeriod(oldestTimestamp) {
    const diffMs = new Date() - new Date(oldestTimestamp);
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    let count = diffMins;
    let key = 'statusPeriodMinutes';
    if (diffDays > 0) {
        count = diffDays;
        key = 'statusPeriodDays';
    } else if (diffHours > 0) {
        count = diffHours;
        key = 'statusPeriodHours';
    }

    return plural(count, key).replace('{n}', count);
}

function renderHealthHistory() {
    const container = document.getElementById('health-history');
    const uptimeEl = document.getElementById('uptime-percentage');
    const periodEl = document.getElementById('history-period');

    if (!lastHistory || !lastHistory.history) {
        container.innerHTML = `<div class="health-history-empty">${t('statusNoHistory')}</div>`;
        return;
    }

    const { history, summary } = lastHistory;

    // Update uptime percentage
    const uptimePercent = summary.uptimePercentage || 100;
    const uptimeValue = uptimePercent.toLocaleString(locale(), {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
    uptimeEl.textContent = `${uptimeValue}% ${t('statusUptimeSuffix')}`;
    uptimeEl.className = 'health-history-uptime' +
        (uptimePercent < 99 ? ' degraded' : '') +
        (uptimePercent < 95 ? ' down' : '');

    // Calculate time period
    if (summary.oldestCheckTimestamp) {
        periodEl.textContent = formatHistoryPeriod(summary.oldestCheckTimestamp);
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
        const timeStr = date.toLocaleTimeString(locale(), { hour: '2-digit', minute: '2-digit' });
        const dateStr = date.toLocaleDateString(locale(), { month: 'short', day: 'numeric' });
        const statusClass = entry.healthy ? '' : ' down';
        const statusText = entry.healthy ? t('statusOperational') : t('statusDown');
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

// ============================================================================
// STATUS REFRESH
// ============================================================================

async function refreshStatus() {
    await fetchStatus();
    await checkAllEndpoints();
    await fetchHealthHistory();
}

// Everything this page renders itself, redrawn from the last payload received
function renderAll() {
    renderStatus();
    renderLastUpdated();
    ENDPOINTS.forEach(renderEndpoint);
    if (lastHistory) {
        renderHealthHistory();
    }
}

// ============================================================================
// INITIALIZATION
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    toolsPage.setup({ onLanguageChange: renderAll });

    // Initial load
    refreshStatus();
    // Auto-refresh every 30 seconds
    setInterval(refreshStatus, 30000);
    // Manual refresh button
    document.getElementById('refresh-status').addEventListener('click', refreshStatus);
});
