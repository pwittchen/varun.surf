// ============================================================================
// APP SHELL
// One source of truth for the chrome every page shares: the sidebar and the two
// modals it opens (about + kite size calculator). Pages render it on load and
// then wire the behaviour they can offer — the main page acts on its spot list
// in place, the others hand the mode over and navigate back to it.
// ============================================================================

import * as api from './api.js';

const SIDEBAR_HTML = `
<div class="sidebar-brand" id="sidebarBrand">
    <img src="/logo.png" alt="VARUN.SURF Logo" class="header-logo" id="headerLogo">
    <h1 class="header-title"><span id="headerTitle">VARUN.SURF</span></h1>
</div>
<nav class="sidebar-nav">
    <div class="sidebar-section">
        <button class="theme-toggle info-toggle sidebar-item" id="infoToggle" title="About VARUN.SURF">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M12,2A10,10,0,1,0,22,12,10.011,10.011,0,0,0,12,2Zm0,3a1.5,1.5,0,1,1-1.5,1.5A1.5,1.5,0,0,1,12,5ZM14,17H10a1,1,0,0,1,0-2h1V12H10a1,1,0,0,1,0-2h2a1,1,0,0,1,1,1v4h1a1,1,0,0,1,0,2Z"/>
            </svg>
            <span class="info-label" id="infoToggleLabel">Info</span>
            <span class="sidebar-label" id="infoToggleSidebarLabel">Info</span>
        </button>
        <button class="theme-toggle sidebar-item" id="themeToggle" title="Toggle theme">
            <svg class="icon theme-icon" id="themeIcon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M15,24a12.021,12.021,0,0,1-8.914-3.966,11.9,11.9,0,0,1-3.02-9.309A12.122,12.122,0,0,1,13.085.152a13.061,13.061,0,0,1,5.031.205,2.5,2.5,0,0,1,1.108,4.226c-4.56,4.166-4.164,10.644.807,14.41a2.5,2.5,0,0,1-.7,4.32A13.894,13.894,0,0,1,15,24Z"/>
            </svg>
            <span class="sidebar-label" id="themeToggleSidebarLabel">Theme</span>
        </button>
    </div>
    <div class="sidebar-section">
        <div class="sidebar-section-title" id="sidebarSectionView">View</div>
        <button class="theme-toggle view-toggle sidebar-item" id="gridViewBtn" title="Grid view">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
                <path d="M42.667,373.333H96c23.564,0,42.667,19.102,42.667,42.667v53.333C138.667,492.898,119.564,512,96,512H42.667C19.103,512,0,492.898,0,469.333V416C0,392.436,19.103,373.333,42.667,373.333z"/>
                <path d="M493.184,380.629c-7.039-4.768-15.349-7.31-23.851-7.296H416c-23.564,0-42.667,19.102-42.667,42.667v53.333C373.333,492.898,392.436,512,416,512h53.333C492.898,512,512,492.898,512,469.333V416C511.998,401.815,504.946,388.559,493.184,380.629z"/>
                <path d="M42.667,186.667H96c23.564,0,42.667,19.103,42.667,42.667v53.333c0,23.564-19.103,42.667-42.667,42.667H42.667C19.103,325.333,0,306.231,0,282.667v-53.333C0,205.769,19.103,186.667,42.667,186.667z"/>
                <path d="M493.184,193.963c-7.039-4.768-15.349-7.31-23.851-7.296H416c-23.564,0-42.667,19.103-42.667,42.667v53.333c0,23.564,19.103,42.667,42.667,42.667h53.333c23.564,0,42.667-19.103,42.667-42.667v-53.333C511.998,215.148,504.946,201.892,493.184,193.963z"/>
                <path d="M42.667,0H96c23.564,0,42.667,19.103,42.667,42.667V96c0,23.564-19.103,42.667-42.667,42.667H42.667C19.103,138.667,0,119.564,0,96V42.667C0,19.103,19.103,0,42.667,0z"/>
                <path d="M306.517,380.629c-7.039-4.768-15.349-7.31-23.851-7.296h-53.333c-23.564,0-42.667,19.102-42.667,42.667v53.333c0,23.564,19.103,42.667,42.667,42.667h53.333c23.564,0,42.667-19.102,42.667-42.667V416C325.331,401.815,318.279,388.559,306.517,380.629z"/>
                <path d="M306.517,193.963c-7.039-4.768-15.349-7.31-23.851-7.296h-53.333c-23.564,0-42.667,19.103-42.667,42.667v53.333c0,23.564,19.103,42.667,42.667,42.667h53.333c23.564,0,42.667-19.103,42.667-42.667v-53.333C325.331,215.148,318.279,201.892,306.517,193.963z"/>
                <path d="M306.517,7.296c-7.039-4.768-15.349-7.31-23.851-7.296h-53.333c-23.564,0-42.667,19.103-42.667,42.667V96c0,23.564,19.103,42.667,42.667,42.667h53.333c23.564,0,42.667-19.103,42.667-42.667V42.667C325.331,28.482,318.279,15.225,306.517,7.296z"/>
                <path d="M504.704,18.816C496.775,7.054,483.518,0.002,469.333,0H416c-23.564,0-42.667,19.103-42.667,42.667V96c0,23.564,19.103,42.667,42.667,42.667h53.333C492.898,138.667,512,119.564,512,96V42.667C512.014,34.165,509.472,25.855,504.704,18.816z"/>
            </svg>
            <span class="sidebar-label" id="gridViewBtnSidebarLabel">Grid</span>
        </button>
        <button class="theme-toggle view-toggle sidebar-item" id="listViewBtn" title="List view">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="m0,8.5c0-.829.671-1.5,1.5-1.5h21c.829,0,1.5.671,1.5,1.5s-.671,1.5-1.5,1.5H1.5c-.829,0-1.5-.671-1.5-1.5Zm22.5,5.5H1.5c-.829,0-1.5.671-1.5,1.5s.671,1.5,1.5,1.5h21c.829,0,1.5-.671,1.5-1.5s-.671-1.5-1.5-1.5Z"/>
            </svg>
            <span class="sidebar-label" id="listViewBtnSidebarLabel">List</span>
        </button>
        <button class="theme-toggle map-toggle sidebar-item" id="mapToggle" title="Map view">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M10,8a2,2,0,1,1,2,2A2,2,0,0,1,10,8Zm14,5.374v5.149a4.51,4.51,0,0,1-1.738,3.594,4.249,4.249,0,0,1-3.674.774c-.044-.011-2.328-.818-2.328-.818a2.114,2.114,0,0,0-1.148.011l-5.5,1.706A5.275,5.275,0,0,1,8.14,24a5.1,5.1,0,0,1-1.6-.256l-2.739-.9A5.494,5.494,0,0,1,0,17.576V13.5A5.52,5.52,0,0,1,1.707,9.462,5.294,5.294,0,0,1,4.013,8.2,8.084,8.084,0,0,1,6.337,2.374a7.941,7.941,0,0,1,11.326,0,8.088,8.088,0,0,1,2.329,5.652l.466.163A5.517,5.517,0,0,1,24,13.373ZM8.471,11.68,11.764,14.9a.34.34,0,0,0,.484,0l3.3-3.235a5.134,5.134,0,0,0-.016-7.182h0a4.945,4.945,0,0,0-7.058,0A5.14,5.14,0,0,0,8.471,11.68ZM21,13.373a2.5,2.5,0,0,0-1.552-2.358,8.026,8.026,0,0,1-1.785,2.774l-3.314,3.252a3.324,3.324,0,0,1-4.682,0L6.355,13.807A8.118,8.118,0,0,1,4.6,11.126a2.371,2.371,0,0,0-.818.508A2.545,2.545,0,0,0,3,13.5v4.075a2.487,2.487,0,0,0,1.7,2.409l2.776.908a2.155,2.155,0,0,0,1.272.023l5.511-1.708a5.158,5.158,0,0,1,2.937.015L19.383,20a1.256,1.256,0,0,0,1.038-.249A1.532,1.532,0,0,0,21,18.522Z"/>
            </svg>
            <span class="map-label" id="mapToggleLabel">Map</span>
            <span class="sidebar-label" id="mapToggleSidebarLabel">Map</span>
        </button>
        <button class="theme-toggle hero-toggle desktop-only sidebar-item sidebar-toggle" id="heroToggle" role="switch" aria-checked="false" title="Toggle hero banner">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M19,0H5A5.006,5.006,0,0,0,0,5V19a5.006,5.006,0,0,0,5,5H19a5.006,5.006,0,0,0,5-5V5A5.006,5.006,0,0,0,19,0ZM5,2H19a3,3,0,0,1,3,3V19a2.951,2.951,0,0,1-.3,1.285l-9.163-9.163a5,5,0,0,0-7.072,0L2,14.586V5A3,3,0,0,1,5,2ZM5,22a3,3,0,0,1-3-3V17.414l4.878-4.878a3,3,0,0,1,4.244,0L20.285,21.7A2.951,2.951,0,0,1,19,22Z"/>
                <circle cx="16" cy="8" r="2"/>
            </svg>
            <span class="sidebar-label" id="heroToggleSidebarLabel">Banner</span>
            <span class="sidebar-switch" aria-hidden="true"></span>
        </button>
    </div>
    <div class="sidebar-section">
        <div class="sidebar-section-title" id="sidebarSectionFilters">Filters</div>
        <button class="theme-toggle sidebar-item sidebar-toggle" id="favoritesToggle" role="switch" aria-checked="false" title="View favorites">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M1.327,12.4,4.887,15,3.535,19.187A3.178,3.178,0,0,0,4.719,22.8a3.177,3.177,0,0,0,3.8-.019L12,20.219l3.482,2.559a3.227,3.227,0,0,0,4.983-3.591L19.113,15l3.56-2.6a3.227,3.227,0,0,0-1.9-5.832H16.4L15.073,2.432a3.227,3.227,0,0,0-6.146,0L7.6,6.568H3.231a3.227,3.227,0,0,0-1.9,5.832Z"/>
            </svg>
            <span class="sidebar-label" id="favoritesToggleSidebarLabel">Favorites</span>
            <span class="sidebar-switch" aria-hidden="true"></span>
        </button>
        <button class="theme-toggle firing-toggle sidebar-item sidebar-toggle" id="firingSortToggle" role="switch" aria-checked="false" title="Sort by strongest wind now">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M12,23a7.5,7.5,0,0,1-5.138-12.963C8.204,8.774,11.5,6.5,11,.5c6,4,9,8,3,14,1,0,2.5,0,5-2.47a6.66,6.66,0,0,1,.5,2.5A7.5,7.5,0,0,1,12,23Z"/>
            </svg>
            <span class="sidebar-label" id="firingSortToggleSidebarLabel">Firing now</span>
            <span class="sidebar-switch" aria-hidden="true"></span>
        </button>
        <button class="theme-toggle live-stations-toggle sidebar-item sidebar-toggle" id="liveStationsToggle" role="switch" aria-checked="false" title="Live stations only">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="2.75"/>
                <path d="M8.11,7.05a1.25,1.25,0,0,1,0,1.77,4.5,4.5,0,0,0,0,6.36,1.25,1.25,0,0,1-1.77,1.77,7,7,0,0,1,0-9.9A1.25,1.25,0,0,1,8.11,7.05Z"/>
                <path d="M17.66,7.05a7,7,0,0,1,0,9.9,1.25,1.25,0,0,1-1.77-1.77,4.5,4.5,0,0,0,0-6.36,1.25,1.25,0,0,1,1.77-1.77Z"/>
                <path d="M4.93,3.87a1.25,1.25,0,0,1,0,1.77,9,9,0,0,0,0,12.72,1.25,1.25,0,0,1-1.77,1.77,11.5,11.5,0,0,1,0-16.26A1.25,1.25,0,0,1,4.93,3.87Z"/>
                <path d="M20.84,3.87a11.5,11.5,0,0,1,0,16.26,1.25,1.25,0,0,1-1.77-1.77,9,9,0,0,0,0-12.72,1.25,1.25,0,0,1,1.77-1.77Z"/>
            </svg>
            <span class="sidebar-label" id="liveStationsToggleSidebarLabel">Live stations</span>
            <span class="sidebar-switch" aria-hidden="true"></span>
        </button>
    </div>
    <div class="sidebar-section">
        <div class="sidebar-section-title" id="sidebarSectionTools">Tools</div>
        <button class="theme-toggle sidebar-item" id="kiteSizeToggle" title="Kite/Board Size Calculator">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="m18.5 24h-13a5.506 5.506 0 0 1 -5.5-5.5v-13a5.506 5.506 0 0 1 5.5-5.5h13a5.506 5.506 0 0 1 5.5 5.5v13a5.506 5.506 0 0 1 -5.5 5.5zm-13-21a2.5 2.5 0 0 0 -2.5 2.5v13a2.5 2.5 0 0 0 2.5 2.5h13a2.5 2.5 0 0 0 2.5-2.5v-13a2.5 2.5 0 0 0 -2.5-2.5zm13.5 4a2 2 0 0 0 -2-2h-10a2 2 0 0 0 -2 2 2 2 0 0 0 2 2h10a2 2 0 0 0 2-2zm-12.5 4.5a1.5 1.5 0 1 0 1.5 1.5 1.5 1.5 0 0 0 -1.5-1.5zm5 0a1.5 1.5 0 1 0 1.5 1.5 1.5 1.5 0 0 0 -1.5-1.5zm-5 4.5a1.5 1.5 0 1 0 1.5 1.5 1.5 1.5 0 0 0 -1.5-1.5zm5 0a1.5 1.5 0 1 0 1.5 1.5 1.5 1.5 0 0 0 -1.5-1.5zm7.5 1.5a1.5 1.5 0 0 0 -1.5-1.5h-1a1.5 1.5 0 0 0 0 3h1a1.5 1.5 0 0 0 1.5-1.5zm0-4.5a1.5 1.5 0 0 0 -1.5-1.5h-1a1.5 1.5 0 0 0 0 3h1a1.5 1.5 0 0 0 1.5-1.5z"/>
            </svg>
            <span class="sidebar-label" id="kiteSizeToggleSidebarLabel">Calculator</span>
        </button>
        <button class="theme-toggle desktop-only sidebar-item" id="randomSpotToggle" title="Open random spot">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M19,0H5A5.006,5.006,0,0,0,0,5V19a5.006,5.006,0,0,0,5,5H19a5.006,5.006,0,0,0,5-5V5A5.006,5.006,0,0,0,19,0Zm3,19a3,3,0,0,1-3,3H5a3,3,0,0,1-3-3V5A3,3,0,0,1,5,2H19a3,3,0,0,1,3,3Z"/>
                <circle cx="7.5" cy="7.5" r="1.75"/>
                <circle cx="16.5" cy="7.5" r="1.75"/>
                <circle cx="12" cy="12" r="1.75"/>
                <circle cx="7.5" cy="16.5" r="1.75"/>
                <circle cx="16.5" cy="16.5" r="1.75"/>
            </svg>
            <span class="sidebar-label" id="randomSpotToggleSidebarLabel">Random spot</span>
        </button>
    </div>
    <div class="sidebar-section">
        <div class="sidebar-section-title" id="sidebarSectionMore">More</div>
        <a class="theme-toggle sidebar-link" id="statusLink" href="/status" title="System status and stats">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <rect x="3" y="12" width="4.5" height="9" rx="1.5"/>
                <rect x="9.75" y="7" width="4.5" height="14" rx="1.5"/>
                <rect x="16.5" y="3" width="4.5" height="18" rx="1.5"/>
            </svg>
            <span class="sidebar-label" id="statusLinkSidebarLabel">Status</span>
        </a>
        <a class="theme-toggle sidebar-link" id="metricsLink" href="/metrics" title="Application metrics">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M12,2A10,10,0,1,0,22,12H12Z"/>
                <path d="M13.5,.5A10,10,0,0,1,23.5,10.5H13.5Z"/>
            </svg>
            <span class="sidebar-label" id="metricsLinkSidebarLabel">Metrics</span>
        </a>
        <a class="theme-toggle sidebar-link" id="sourcesLink" href="/sources" title="Data sources">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M12,2,22,7,12,12,2,7Z"/>
                <path d="M12,14.2,20.3,10.05,22,11,12,16,2,11l1.7-.95Z"/>
                <path d="M12,18.2,20.3,14.05,22,15,12,20,2,15l1.7-.95Z"/>
            </svg>
            <span class="sidebar-label" id="sourcesLinkSidebarLabel">Sources</span>
        </a>
        <a class="theme-toggle sidebar-link" id="mcpLink" href="/mcp" title="MCP server">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <rect x="8" y="1" width="2.5" height="6" rx="1.25"/>
                <rect x="13.5" y="1" width="2.5" height="6" rx="1.25"/>
                <path d="M5,7h14v2a7,7,0,0,1-14,0Z"/>
                <rect x="10.5" y="15.5" width="3" height="7.5" rx="1.5"/>
            </svg>
            <span class="sidebar-label" id="mcpLinkSidebarLabel">MCP</span>
        </a>
        <a class="theme-toggle sidebar-link" id="logsLink" href="/logs" title="Application logs">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <rect x="3" y="3.5" width="18" height="2.5" rx="1.25"/>
                <rect x="3" y="8.5" width="12.5" height="2.5" rx="1.25"/>
                <rect x="3" y="13.5" width="16" height="2.5" rx="1.25"/>
                <rect x="3" y="18.5" width="8.5" height="2.5" rx="1.25"/>
            </svg>
            <span class="sidebar-label" id="logsLinkSidebarLabel">Logs</span>
        </a>
        <a class="theme-toggle sidebar-link" id="githubLink" href="https://github.com/pwittchen/varun.surf" target="_blank" rel="noopener" title="Source code on GitHub">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M12,.3A12,12,0,0,0,8.2,23.7c.6.1.8-.3.8-.6s0-.9,0-2c-3.3.7-4-1.6-4-1.6a3.2,3.2,0,0,0-1.3-1.7c-1.1-.8.1-.8.1-.8a2.5,2.5,0,0,1,1.8,1.2,2.6,2.6,0,0,0,3.5,1,2.6,2.6,0,0,1,.8-1.6c-2.7-.3-5.5-1.3-5.5-5.9a4.6,4.6,0,0,1,1.2-3.2,4.3,4.3,0,0,1,.1-3.2s1-.3,3.3,1.2a11.3,11.3,0,0,1,6,0C17.3,5.1,18.3,5.4,18.3,5.4a4.3,4.3,0,0,1,.1,3.2,4.6,4.6,0,0,1,1.2,3.2c0,4.6-2.8,5.6-5.5,5.9a2.9,2.9,0,0,1,.8,2.2c0,1.6,0,2.9,0,3.3s.2.7.8.6A12,12,0,0,0,12,.3Z"/>
            </svg>
            <span class="sidebar-label" id="githubLinkSidebarLabel">GitHub</span>
        </a>
    </div>
</nav>
<div class="sidebar-footer">
    <button class="theme-toggle sidebar-collapse" id="sidebarCollapse" aria-label="Collapse menu">
        <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path d="M15.707,4.293a1,1,0,0,1,0,1.414L9.414,12l6.293,6.293a1,1,0,1,1-1.414,1.414l-7-7a1,1,0,0,1,0-1.414l7-7A1,1,0,0,1,15.707,4.293Z"/>
        </svg>
        <span class="sidebar-label" id="sidebarCollapseLabel">Collapse</span>
    </button>
</div>
`;

const MODALS_HTML = `
<div class="modal-overlay" id="appInfoModal">
    <div class="modal">
        <div class="modal-header">
            <div class="modal-title">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" style="fill: white; vertical-align: middle;"><path d="M8,13H0V11H8Zm12.915-1.894A5,5,0,1,0,12,8V9h2V8a3,3,0,1,1,3,3H10v2H20a2,2,0,1,1-2,2H16a4,4,0,1,0,4.915-3.894ZM11,16H0v2H11a2,2,0,1,1-2,2H7a4,4,0,1,0,4-4ZM11,4A4,4,0,0,0,3,4H5A2,2,0,1,1,7,6H0V8H7A4,4,0,0,0,11,4Z"/></svg>
                <span id="appInfoModalTitle">About VARUN.SURF</span>
                <span class="modal-title-version" id="appInfoModalVersion"></span>
            </div>
            <button class="modal-close" id="appInfoModalClose">&times;</button>
        </div>
        <div class="modal-content">
            <p class="modal-intro" id="appInfoDescription">VARUN.SURF aggregates weather forecasts and live wind data for kitesurfers worldwide.</p>
            <div class="modal-section">
                <h3 id="appInfoContactTitle">Contact</h3>
                <p id="appInfoContactText">Questions or ideas? Email <a href="mailto:hello@varun.surf" class="modal-link">hello@varun.surf</a>.</p>
            </div>
            <div class="modal-section">
                <h3 id="appInfoNewSpotTitle">Suggest a new kite spot</h3>
                <p id="appInfoNewSpotText">If you want to suggest a new kite spot, <a href="https://pwittchen.notion.site/28a649d2871780368191c6ce5a64029e" class="modal-link">fill in the form</a>.</p>
            </div>
            <div class="modal-section">
                <h3 id="appInfoCollaborationTitle">Collaboration</h3>
                <p id="appInfoCollaborationText">Want to collaborate or supply new data sources? Reach out and let's talk about ideas for kitesurfers.</p>
            </div>
            <div class="modal-section">
                <h3 id="appInfoDevTitle">Developers and AI assistants</h3>
                <p id="appInfoDevText">Kite spots, forecasts and live conditions are available to AI assistants through the <a href="/mcp" class="modal-link">MCP server</a> and the <a href="/llms.txt" class="modal-link">llms.txt</a> file with LLM-friendly Markdown endpoints. Service statistics and uptime are on the <a href="/status" class="modal-link">status page</a>.</p>
            </div>
        </div>
    </div>
</div>

<div class="modal-overlay" id="kiteSizeModal">
    <div class="modal">
        <div class="modal-header">
            <div class="modal-title">
                <span>Kite & Board Size Calculator</span>
            </div>
            <button class="modal-close" id="kiteSizeModalClose">&times;</button>
        </div>
        <div class="modal-content">
            <div class="calc-input-group">
                <label class="calc-label" for="windSpeed">Wind Speed (knots)</label>
                <input type="number" id="windSpeed" class="calc-input" placeholder="Enter wind speed in knots" min="5"
                       max="50" step="1">
            </div>

            <div class="calc-input-group">
                <label class="calc-label" for="riderWeight">Rider Weight (kg)</label>
                <input type="number" id="riderWeight" class="calc-input" placeholder="Enter your weight in kg" min="40"
                       max="150" step="1">
            </div>

            <div class="calc-input-group">
                <label class="calc-label" for="skillLevel">Skill Level & Conditions</label>
                <select id="skillLevel" class="calc-select">
                    <option value="">Select skill level & conditions</option>
                    <option value="beginner-flat">Beginner - Flat Water</option>
                    <option value="beginner-small">Beginner - Small Waves</option>
                    <option value="intermediate-flat">Intermediate - Flat Water</option>
                    <option value="intermediate-medium">Intermediate - Medium Waves</option>
                    <option value="advanced-flat">Advanced - Flat Water</option>
                    <option value="advanced-medium">Advanced - Medium Waves</option>
                    <option value="advanced-large">Advanced - Large Waves</option>
                </select>
            </div>

            <button class="calc-button" id="calculateBtn">Calculate</button>

            <div class="calc-warning" id="calcWarning"></div>

            <div class="calc-result" id="calcResult">
                <div class="calc-result-title">Recommended Equipment</div>
                <div class="calc-result-item">
                    <div class="calc-result-label">Kite Size</div>
                    <div class="calc-result-value" id="kiteSize">-</div>
                </div>
                <div class="calc-result-item">
                    <div class="calc-result-label">Board Size</div>
                    <div class="calc-result-value" id="boardSize">-</div>
                </div>
                <div class="modal-disclaimer">
                    ⚠️ These are general recommendations. Actual equipment choice may vary based on specific kite model,
                    board type, and personal preference. Always consult with experienced riders or instructors for your
                    local conditions.
                </div>
            </div>
        </div>
    </div>
</div>
`;

// Header of the pages built around the sidebar alone (status, sources, MCP,
// logs, metrics): the language switch is the only control they need, and the
// wordmark moves in here at mobile widths, where the sidebar steps aside.
const MINIMAL_HEADER_HTML = `
<div class="header-content">
    <div class="header-actions">
        <button class="theme-toggle" id="languageToggle" title="Change language">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path d="M24,7v2c0,.552-.448,1-1,1s-1-.448-1-1v-2c0-1.103-.897-2-2-2h-2.029l1.25,1.307c.383,.398,.371,1.031-.028,1.414-.194,.187-.443,.279-.693,.279-.262,0-.524-.103-.721-.307l-2.212-2.301c-.761-.761-.761-2.023,.013-2.798L17.779,.307c.383-.398,1.017-.41,1.414-.028,.398,.383,.411,1.016,.028,1.414l-1.257,1.307h2.036c2.206,0,4,1.794,4,4ZM6.221,16.307c-.383-.398-1.016-.409-1.414-.027-.398,.383-.411,1.016-.028,1.414l1.25,1.307h-2.029c-1.103,0-2-.897-2-2v-2c0-.553-.448-1-1-1s-1,.447-1,1v2c0,2.206,1.794,4,4,4h2.035l-1.256,1.307c-.383,.398-.371,1.031,.028,1.414,.194,.187,.443,.279,.693,.279,.262,0,.524-.103,.721-.307l2.199-2.288c.773-.774,.773-2.036,.013-2.798l-2.212-2.301Zm5.779-8.307c0,2.209-1.791,4-4,4H4c-2.209,0-4-1.791-4-4V4C0,1.791,1.791,0,4,0h4c2.209,0,4,1.791,4,4v4Zm-2.5-4.384c0-.34-.276-.616-.616-.616h-2.257v-.384c0-.34-.276-.616-.616-.616h-.021c-.34,0-.616,.276-.616,.616v.384H3.116c-.34,0-.616,.276-.616,.616v.021c0,.34,.276,.616,.616,.616H7.308c-.111,.963-.484,2.151-1.303,3.071-.276-.31-.507-.648-.692-1-.106-.202-.318-.325-.545-.325-.464,0-.769,.492-.553,.903,.225,.43,.501,.843,.83,1.22-.539,.328-1.189,.559-1.977,.635-.32,.031-.568,.293-.568,.614v.021c0,.365,.316,.648,.679,.614,1.146-.107,2.079-.485,2.832-1.022,.749,.533,1.671,.913,2.808,1.022,.364,.035,.68-.248,.68-.613v-.021c0-.316-.24-.583-.555-.613-.792-.075-1.442-.31-1.984-.639,.99-1.135,1.485-2.591,1.607-3.866h.316c.34,0,.616-.276,.616-.616v-.021Zm14.5,12.384v4c0,2.209-1.791,4-4,4h-4c-2.209,0-4-1.791-4-4v-4c0-2.209,1.791-4,4-4h4c2.209,0,4,1.791,4,4Zm-3.196,5.144l-1.363-5.948c-.107-.464-.403-.886-.842-1.07-.919-.385-1.855,.155-2.056,1.021l-1.413,5.993c-.104,.439,.23,.86,.681,.86h0c.324,0,.606-.223,.681-.539l.274-1.161h2.409l.265,1.157c.073,.318,.356,.543,.682,.543h.002c.449,0,.782-.418,.682-.856Zm-2.818-5.744c-.038,0-.071,.026-.079,.063l-.811,3.437h1.757l-.787-3.437c-.009-.037-.041-.063-.079-.063Z"/>
            </svg>
            <span id="langCode">EN</span>
        </button>
    </div>
</div>
`;

// Insert the sidebar, before anything wires up its buttons. It goes after the
// header: both are fixed on the same layer, so the later one wins the overlap —
// and the sidebar owns the left inset the header leaves for it.
export function renderSidebar() {
    if (document.getElementById('sideMenu')) {
        return;
    }
    const aside = document.createElement('aside');
    aside.className = 'side-menu';
    aside.id = 'sideMenu';
    aside.setAttribute('aria-label', 'Menu');
    aside.innerHTML = SIDEBAR_HTML;

    const header = document.querySelector('.fixed-header');
    if (header) {
        header.after(aside);
    } else {
        document.body.insertBefore(aside, document.body.firstChild);
    }
}

// The running version, shown greyed out next to the about modal's title. Asked
// for the first time the modal opens rather than on load: a page that never
// opens it never pays for the request, and the answer is the same all session.
let appVersion = null;

export async function loadAppVersion() {
    const el = document.getElementById('appInfoModalVersion');
    if (!el) {
        return;
    }
    if (appVersion) {
        el.textContent = appVersion;
        return;
    }
    try {
        const status = await api.fetchStatus();
        if (!status.version) {
            return;
        }
        appVersion = status.version.startsWith('v') ? status.version : `v${status.version}`;
        el.textContent = appVersion;
    } catch (error) {
        console.error('Error fetching app version:', error);
    }
}

export function renderModals() {
    if (document.getElementById('appInfoModal')) {
        return;
    }
    const holder = document.createElement('div');
    holder.innerHTML = MODALS_HTML;
    while (holder.firstElementChild) {
        document.body.appendChild(holder.firstElementChild);
    }
}

export function renderMinimalHeader() {
    if (document.querySelector('.fixed-header')) {
        return;
    }
    const header = document.createElement('div');
    header.className = 'fixed-header';
    header.innerHTML = MINIMAL_HEADER_HTML;
    document.body.insertBefore(header, document.body.firstChild);
}
