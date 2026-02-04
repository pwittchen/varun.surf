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
const SESSION_CREDENTIALS_KEY = 'logs_credentials';
const LOGS_USERNAME = 'admin';

let allLogs = [];

// ============================================================================
// AUTHENTICATION
// ============================================================================

function getStoredCredentials() {
    // Try logs-specific credentials first, then fall back to metrics credentials
    return sessionStorage.getItem(SESSION_CREDENTIALS_KEY) ||
           sessionStorage.getItem('metrics_credentials') || '';
}

function storeCredentials(password) {
    const credentials = btoa(`${LOGS_USERNAME}:${password}`);
    sessionStorage.setItem(SESSION_CREDENTIALS_KEY, credentials);
}

function clearCredentials() {
    sessionStorage.removeItem(SESSION_CREDENTIALS_KEY);
}

function showLoginForm() {
    stopAutoRefresh();
    autoRefreshEnabled = false;

    const container = document.querySelector('.status-container');
    container.innerHTML = `
        <div class="status-page-header">
            <div class="status-page-header-content">
                <h1 class="status-page-title"><span id="headerTitle">VARUN.SURF</span></h1>
            </div>
        </div>
        <h2>Application Logs</h2>
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
            <a href="/metrics/" class="btn btn-secondary">View Metrics</a>
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
        const credentials = btoa(`${LOGS_USERNAME}:${password}`);
        const response = await fetch('/api/v1/logs', {
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

async function fetchLogs() {
    const credentials = getStoredCredentials();
    const headers = {};
    if (credentials) {
        headers['Authorization'] = `Basic ${credentials}`;
    }

    const response = await fetch('/api/v1/logs', { headers });
    if (response.status === 401) {
        clearCredentials();
        showLoginForm();
        throw new Error('Unauthorized');
    }
    if (!response.ok) {
        throw new Error('Failed to fetch logs');
    }
    return await response.json();
}

// ============================================================================
// FORMATTERS
// ============================================================================

function formatTimestamp(timestamp) {
    if (!timestamp) return '-';
    const date = new Date(timestamp);
    return date.toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    }) + '.' + String(date.getMilliseconds()).padStart(3, '0');
}

function formatLoggerName(loggerName) {
    if (!loggerName) return '-';
    const parts = loggerName.split('.');
    if (parts.length <= 2) return loggerName;
    return '...' + parts.slice(-2).join('.');
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ============================================================================
// UI UPDATES
// ============================================================================

function getLevelClass(level) {
    switch (level) {
        case 'ERROR': return 'logs-level-error';
        case 'WARN': return 'logs-level-warn';
        case 'INFO': return 'logs-level-info';
        case 'DEBUG': return 'logs-level-debug';
        case 'TRACE': return 'logs-level-trace';
        default: return '';
    }
}

function renderLogs(logs) {
    const tbody = document.getElementById('logs-body');
    const logsCount = document.getElementById('logs-count');

    if (!logs || logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="logs-empty">No logs to display</td></tr>';
        logsCount.textContent = '0 logs';
        return;
    }

    // Sort logs by timestamp descending (newest first)
    const sortedLogs = [...logs].sort((a, b) => b.timestamp - a.timestamp);

    logsCount.textContent = `${sortedLogs.length} logs`;

    tbody.innerHTML = sortedLogs.map(log => `
        <tr class="logs-row ${getLevelClass(log.level)}">
            <td class="logs-td-time">${formatTimestamp(log.timestamp)}</td>
            <td class="logs-td-level"><span class="logs-level-badge ${getLevelClass(log.level)}">${log.level}</span></td>
            <td class="logs-td-logger" title="${escapeHtml(log.loggerName)}">${escapeHtml(formatLoggerName(log.loggerName))}</td>
            <td class="logs-td-message">${escapeHtml(log.message)}</td>
        </tr>
    `).join('');
}

function filterLogs() {
    const levelFilter = document.getElementById('level-filter').value;
    const searchFilter = document.getElementById('search-filter').value.toLowerCase();

    let filteredLogs = allLogs;

    if (levelFilter) {
        filteredLogs = filteredLogs.filter(log => log.level === levelFilter);
    }

    if (searchFilter) {
        filteredLogs = filteredLogs.filter(log =>
            (log.message && log.message.toLowerCase().includes(searchFilter)) ||
            (log.loggerName && log.loggerName.toLowerCase().includes(searchFilter)) ||
            (log.threadName && log.threadName.toLowerCase().includes(searchFilter))
        );
    }

    renderLogs(filteredLogs);
}

// ============================================================================
// MAIN REFRESH FUNCTION
// ============================================================================

async function refreshLogs() {
    try {
        allLogs = await fetchLogs();
        filterLogs();

        document.getElementById('last-updated').textContent =
            'Last updated: ' + new Date().toLocaleTimeString();

    } catch (error) {
        console.error('Error fetching logs:', error);
    }
}

// ============================================================================
// AUTO-REFRESH CONTROLS
// ============================================================================

function toggleAutoRefresh() {
    autoRefreshEnabled = !autoRefreshEnabled;
    const button = document.getElementById('toggle-refresh');
    const statusEl = document.getElementById('refresh-status');
    const dotEl = document.querySelector('.logs-refresh-dot');

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
    refreshInterval = setInterval(refreshLogs, REFRESH_INTERVAL_MS);
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

const WIDE_VIEW_KEY = 'logs_wide_view';

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

async function initializeLogs() {
    const headerTitle = document.getElementById('headerTitle');
    if (headerTitle) {
        headerTitle.addEventListener('click', routing.navigateToHome);
    }

    const toggleButton = document.getElementById('toggle-refresh');
    if (toggleButton) {
        toggleButton.addEventListener('click', toggleAutoRefresh);
    }

    const toggleWideButton = document.getElementById('toggle-wide');
    if (toggleWideButton) {
        toggleWideButton.addEventListener('click', toggleWideView);
    }

    // Restore wide view preference
    restoreWideView();

    const levelFilter = document.getElementById('level-filter');
    if (levelFilter) {
        levelFilter.addEventListener('change', filterLogs);
    }

    const searchFilter = document.getElementById('search-filter');
    if (searchFilter) {
        searchFilter.addEventListener('input', filterLogs);
    }

    try {
        await refreshLogs();
    } catch (error) {
        console.error('Error loading logs:', error);
        if (error.message === 'Unauthorized') {
            return;
        }
    }

    startAutoRefresh();
    footer.updateFooter(translations.t);
}

document.addEventListener('DOMContentLoaded', initializeLogs);
