# Frontend Architecture

## Overview

The **varun.surf** frontend is a modern, single-page application (SPA) built with vanilla JavaScript, HTML5, and CSS3. It provides a responsive, user-friendly interface for browsing kitesurfing weather forecasts and real-time wind conditions across multiple kite spots worldwide.

**Key Characteristics**:
- Zero framework dependencies (Vanilla JS)
- Fully responsive design (mobile-first approach)
- Client-side routing with History API
- Internationalization support (English/Polish)
- Theme switching (dark/light mode)
- Progressive enhancement and graceful degradation

## Tech Stack

- **HTML5**: Semantic markup, inline SVGs for icons
- **CSS3**: CSS Variables, Grid Layout, Flexbox, Media Queries
- **JavaScript (ES6+)**: Async/await, Fetch API, LocalStorage, History API
- **Build Process**: Minification and bundling during Gradle build
- **Analytics**: DataFast (self-hosted analytics)

## Project Structure

```
varun.surf/
├── src/frontend/                  # Source files (NOT deployed)
│   ├── js/                        # JavaScript files
│   │   ├── common/
│   │   │   ├── state.js           # Centralized state management (localStorage/sessionStorage)
│   │   │   ├── translations.js    # i18n configuration
│   │   │   ├── flags.js           # Shared emoji flags helper
│   │   │   ├── api.js             # Fetch helpers for the REST API
│   │   │   ├── appShell.js        # Shared page shell (header, sidebar wiring)
│   │   │   ├── sideMenu.js        # Sidebar navigation and mobile drawer
│   │   │   ├── footer.js          # Shared footer
│   │   │   ├── map.js             # Leaflet map: markers, clustering, wind field, timeline
│   │   │   ├── modals.js          # Modal overlay system
│   │   │   ├── routing.js         # Client-side routing (History API)
│   │   │   ├── calculator.js      # Kite and board size calculator
│   │   │   ├── toolsPage.js       # Tools page wiring
│   │   │   ├── mainPageShortcuts.js # Keyboard shortcuts on the dashboard
│   │   │   ├── search.js          # Spot search matching (shared by both search fields)
│   │   │   ├── spotSearch.js      # Single spot page header search (jump to another spot)
│   │   │   ├── weather.js         # Wind formatting and color coding
│   │   │   ├── date.js            # Date and time helpers
│   │   │   └── constants.js       # Shared constants
│   │   └── page/
│   │       ├── index.js           # Dashboard page logic
│   │       ├── spot.js            # Single spot page logic
│   │       ├── status.js          # Status page logic
│   │       ├── sources.js         # Data sources page logic
│   │       ├── metrics.js         # Metrics dashboard logic
│   │       ├── logs.js            # Logs dashboard logic
│   │       ├── embed.js           # Embeddable spot widget logic
│   │       ├── tv.js              # TV view logic
│   │       └── mcp.js             # MCP server page logic
│   ├── html/                      # HTML templates
│   │   ├── index.html             # Dashboard page template
│   │   ├── spot.html              # Single spot page template
│   │   ├── status.html            # Status page template
│   │   ├── sources.html           # Data sources page template
│   │   ├── metrics.html           # Metrics dashboard template
│   │   ├── logs.html              # Logs dashboard template
│   │   ├── embed.html             # Embeddable spot widget
│   │   ├── tv.html                # TV view template
│   │   └── mcp.html               # MCP server page template
│   ├── css/                       # Stylesheets
│   │   ├── styles.css             # Global styles
│   │   └── tv.css                 # TV view styles
│   ├── images/                    # Spot photos
│   │   └── spots/                 # Spot photos by wgId (e.g., 48776.jpg)
│   └── assets/                    # Static assets
│       ├── logo.png               # Brand logo
│       ├── ai.txt                 # AI crawler instructions
│       ├── llms.txt               # LLM-friendly site info
│       └── robots.txt             # SEO configuration
├── src/main/resources/static/     # Compiled/minified files (deployed)
│   ├── index.html                 # Dashboard page
│   ├── spot.html                  # Single spot page
│   ├── status.html                # Status page
│   ├── sources.html               # Data sources page
│   ├── metrics.html               # Metrics dashboard
│   ├── logs.html                  # Logs dashboard
│   ├── embed.html                 # Embeddable spot widget
│   ├── tv.html                    # TV view
│   ├── mcp.html                   # MCP server page
│   ├── assets/                    # Content-hashed CSS/JS/logo (/assets/<name>.<hash>.<ext>)
│   ├── images/                    # Spot photos
│   ├── logo.png                   # Brand logo
│   ├── ai.txt                     # AI crawler instructions
│   ├── llms.txt                   # LLM-friendly site info
│   └── robots.txt                 # SEO configuration
```

Note: `sitemap.xml` is not a static file - it is generated by `SeoController`
(`GET /sitemap.xml`) from the current spot list.

**Important**: Files in `src/main/resources/static/` are generated artifacts. All source editing should happen in `src/frontend/` directory.

## Architecture Overview

### High-Level Frontend Flow

```
Browser
    ↓
HTML Pages (index.html, spot.html, status.html, sources.html, mcp.html,
            metrics.html, logs.html, embed.html, tv.html)
    ↓
JavaScript Entry Points (inline <script> tags)
    ├─→ common/state.js (centralized state management)
    ├─→ common/translations.js (i18n)
    ├─→ common/appShell.js + sideMenu.js (shared shell and navigation)
    ├─→ common/map.js (wind map: markers, clustering, wind field, timeline)
    ├─→ page/index.js (dashboard logic)
    ├─→ page/spot.js (spot detail logic)
    └─→ page/status.js (status page logic)
    ↓
API Calls (Fetch with credentials: 'same-origin')
    ├─→ GET /api/v1/spots (all spots, requires SESSION cookie)
    ├─→ GET /api/v1/spots/{id} (single spot with history)
    ├─→ GET /api/v1/spots/{id}/{model} (GFS or IFS)
    ├─→ GET /api/v1/wind?hours=N (hourly wind for every spot on one grid, for the
    │       maps: the whole forecast run on a desktop, five days on a phone)
    ├─→ GET /api/v1/forecast/{wgId} (one spot's full hourly forecast)
    ├─→ GET /api/v1/sponsors (main sponsors)
    ├─→ GET /api/v1/status, /status/history, /status/sources
    ├─→ GET /api/v1/metrics (application metrics)
    ├─→ GET /api/v1/metrics/history (time-series data)
    └─→ GET /api/v1/logs (application logs)
    ↓
DOM Manipulation (vanilla JS)
    ├─→ Dynamic rendering (spot cards, tables, modals)
    ├─→ Event handling (clicks, search, filters)
    └─→ LocalStorage persistence (theme, language, favorites)
```

### Pages and Responsibilities

#### 1. Dashboard (`index.html`)
**URL Patterns**:
- `/` - All spots
- `/country/{countryName}` - Filtered by country
- `/starred` - Favorites view

**Features**:
- Hero section with random spot photo, name/location, and slogan (EN/PL,
  toggleable). Two actions sit on the photo: refresh draws another spot, the X
  hides the banner - and because that is not obviously reversible, the X opens a
  confirmation modal naming the sidebar Banner button that brings it back
- Grid layout with spot cards (2 or 3 columns)
- Country dropdown filter
- Search functionality, focused from anywhere with the "/" key (a "/" hint sits
  in the field, giving way to the clear button once it is focused or typed into)
- Favorites system (star icons)
- Drag-and-drop spot reordering
- Auto-refresh every 60 seconds
- Stale live conditions indicators (yellow pulsing dot for outdated data)
- Map view with three controls in the bottom-left corner: the spot visibility
  toggle (markers and clusters on or off - hiding them leaves the wind field,
  which lives on its own layer, alone on the map), the wind field toggle (the
  same on/off button, lit while the overlay is drawn) and the base layer
  switcher. All three choices are remembered
- Modal overlays (AI analysis, spot info, ICM forecast, kite calculator, hide-banner confirmation)

**JavaScript Logic** (`page/index.js`):
- `fetchWeatherData()` - Fetch all spots from API
- `renderSpots()` - Render spot cards into grid
- `toggleFavorite()` - Add/remove favorites
- `initTheme()` - Theme switching
- `initLanguage()` - i18n switching
- `populateCountryDropdown()` - Dynamic country filter
- URL routing with `History API`

#### 2. Single Spot Page (`spot.html`)
**URL Pattern**:
- `/spot/{spotId}` - Individual spot detail view

**Features**:
- Two-column layout (desktop): left sidebar (map, current conditions, spot info, AI analysis), right main content (forecast table)
- On-demand generation under the map, desktop only (`isDesktopView`, min-width
  1005px), each of the two in its own card:
  - AI analysis: one slot with three states - the analysis, a spinner while it is
    being written, or the button offering to write one. The heading only appears
    with the text it labels; a failed generation puts a warning beside the button
  - ICM forecast: a button below the analysis. Both button and spinner disappear
    once the forecast is read, and come back when it expires a day later
  - Each button opens a confirmation modal first (`#aiGenerateModal`,
    `#icmGenerateModal`), since the click is what spends the money. The ICM one
    also says the forecast will appear in the model dropdown in the top right
    corner, which the button's own position does not suggest
  - Both share `.ondemand-generate-button`, whose `min-width` is sized for the
    longest label in any language so the two line up whichever language is on
  - Which state renders is derived from the spot data and the in-memory sets on
    every rebuild, never from what the previous render left in the DOM
- Forecast view tabs: Table View (vertical) and Windguru View (horizontal)
- Real-time current conditions card with live indicator
- Current conditions history chart (12-hour wind trend)
- Embedded Google Maps (satellite view)
- Wind map of the spot and its neighbours (`initSpotWindMap`): the shared wind
  field, a layer switcher, a crosshair that recenters and reopens the spot's own
  popup, and the forecast slider under it. Every popup - the page's own spot
  included, its name plain text because linking a page to itself leads nowhere -
  carries wind, gusts, direction and, off "now", the hour it describes. Stepping
  the slider rewrites open popups in place instead of closing them; the
  neighbour layer is rebuilt on zoom (clustering is in screen pixels), so
  `map.findOpenSpotPopup` / `map.openSpotPopup` carry an open popup across the
  rebuild
- Spot photo display (when available)
- ICM meteogram link (for Poland/Czech Republic spots)
- Dynamic forecast model selector (40+ Windguru models, populated from `availableModels`)
- Header spot search (`common/spotSearch.js`), desktop only: with no list on the
  page to filter, it opens a list of matching spots to jump straight to. Same
  match as the main page's filter (name or country, diacritics folded, shared via
  `common/search.js`), keyboard-driven (arrows, Enter, Escape) and focused from
  anywhere with the "/" key. The spots list is fetched once on the first focus,
  so a page nobody searches from never pays for it. Hidden on the drawer layout
  (<=929px), where the header has no room for it
- Auto-refresh every 60 seconds
- Polling mechanism for IFS forecast availability

**JavaScript Logic** (`page/spot.js`):
- `fetchSpotData(spotId)` - Fetch single spot data
- `displaySpot(spot)` - Render spot details
- `startForecastPolling()` - Poll for IFS forecast (5s interval, 30s timeout)
- `startBackgroundRefresh()` - Auto-refresh current conditions (60s)
- `renderWindguruView()` - Horizontal forecast view
- `renderConditionsChart()` - Canvas-based wind history chart
- `spotSearch.setup()` / `spotSearch.updateTranslations()` - header spot search
- Model selection persistence via `sessionStorage`

#### 3. Status Page (`status.html`)
**URL Pattern**:
- `/status` - System health and metrics dashboard

**Features**:
- Service uptime and version info
- API endpoint health checks
- System status indicators (green/red dots)
- Spots count, countries count, live stations count
- Application metrics dashboard (gauges, counters, timers)
- JVM metrics (memory, threads, CPU)
- HTTP client metrics (requests, latency)
- Historical metrics charts (time-series visualization)
- Password-protected metrics access
- Auto-refresh every 30 seconds

**JavaScript Logic** (`page/status.js`):
- `fetchStatus()` - Get system status from `/api/v1/status`
- `fetchMetrics()` - Get detailed metrics from `/api/v1/metrics`
- `fetchMetricsHistory()` - Get historical data for charts
- `checkEndpoint(url)` - Health check for individual endpoints
- `renderMetricsCharts()` - Canvas-based charts for metrics history
- `renderAll()` - Redraw every section from the payload it was last given,
  handed to `toolsPage.setup()` as the language-change callback
- Password authentication via HTTP Basic (`Authorization: Basic ...`, see `page/metrics.js`)
- Auto-refresh with 30s interval

#### 4. Sources Page (`sources.html`)
**URL Pattern**:
- `/sources` - External data sources used by the application

**Features**:
- Spots data sources (links to the providers behind `spots.json`)
- Forecast sources with live health checks (green/red dots + latency)
- Live weather station sources
- Auto-refresh every 30 seconds

**JavaScript Logic** (`page/sources.js`):
- `checkSources()` - Get sources from `/api/v1/status/sources`
- `renderSources()` - Render health-checked sources with status dots
- `renderStationLinks()` - Render link-only source lists

#### 5. MCP Page (`mcp.html`)
**URL Pattern**:
- `/mcp` - Model Context Protocol server configuration

**Features**:
- SSE endpoint URL, resolved from `window.location.origin`
- Claude Code install command and JSON client config, both copyable
- List of tools exposed by the MCP server

**JavaScript Logic** (`page/mcp.js`):
- `initMcpConfig()` - Build endpoint/command/JSON config for the current origin
  and wire up the copy-to-clipboard buttons

> Note: only the exact `/mcp` path serves this page. The MCP server itself is
> exposed by Spring AI under `/mcp/sse` and `/mcp/message`.

#### 6. Metrics Dashboard (`metrics.html`)
**URL Pattern**:
- `/metrics` - Application metrics dashboard

**Features**:
- Gauges, counters, timers, JVM and HTTP client metrics
- Canvas-based charts from `/api/v1/metrics/history`
- No password: the session cookie every visitor gets is enough

**JavaScript Logic** (`page/metrics.js`)

#### 7. Logs Dashboard (`logs.html`)
**URL Pattern**:
- `/logs` - Application logs dashboard

**Features**:
- Last 1000 log entries from the in-memory buffer
- Level filtering (ERROR, WARN, INFO, DEBUG, TRACE) and text search
- Auto-refresh every 5 seconds
- HTTP Basic authentication when `app.analytics.password` is set (the only
  password-protected page)

**JavaScript Logic** (`page/logs.js`):
- `renderAll()` - Redraw the auto-refresh controls, the timestamp and the log
  table from what the page last held, handed to `toolsPage.setup()` as the
  language-change callback. `metrics.js` carries the same pair; both keep the
  login form translated in place (it renders with `data-i18n`, so a switch
  leaves a half-typed password alone) and remember the error as a key rather
  than a sentence

#### 8. Embed Widget (`embed.html` + `page/embed.js`)
**URL Pattern**:
- `/embed` - Embeddable single-spot widget for external sites

**Features**:
- Compact spot card with live conditions, forecast or a map of the spot
- Query parameters: `spotId`, `theme` (dark/light), `view` (conditions/forecast/map),
  `lang` (en/pl), `mapStyle` (satellite/light, map view only)
- The map view carries the same interpolated wind field the site's maps paint
  (colour wash + animated particles, shared from `common/map.js`), with the spot
  marked and named on it. Leaflet is loaded on demand, so the other two views
  stay plain markup
- Under that map sits the same forecast slider the site's maps carry
  (`map.createForecastTimeline`), stepping the field through the next five days
  off `/api/v1/wind?hours=120`. Its labels come from the app's dictionary in the
  language the widget was embedded with, passed in as `translate` - the app's own
  language lives in localStorage, which a third-party iframe may not touch and
  which belongs to the visitor's use of the site, not to this widget
- Its snippet asks for a 560px iframe (500px for the other views), the room the
  map and the slider need under the shared header and footer
- Its markup, styles and dictionary are its own: a widget running inside somebody
  else's page cannot assume anything of the app is loaded
- Embed code with these options is generated from the embed modal on the spot page

#### 9. TV View (`tv.html`)
**URL Pattern**:
- `/spot/{id}/tv` - Full-screen display of one spot (served by `WebConfig.tvRouter`)

**Features**:
- Large-type live conditions and forecast, own stylesheet (`css/tv.css`)

**JavaScript Logic** (`page/tv.js`)

> Additional routes serving `index.html`: `/starred` (favorites view) and `/map`
> (map view), both wired in `WebConfig`.

### Core JavaScript Modules

#### `appShell.js` - Shared Sidebar and Modal Markup
`renderSidebar()` and `renderModals()` inject the chrome every page carries, so
the sidebar and the about modal live in one place rather than in each page's
markup. `loadAppVersion()` fills the greyed-out version span beside the about
modal's title from `/api/v1/status`; it is called when the modal opens rather
than on load - a page that never opens it never pays for the request - and the
answer is memoized for the session.

#### `toolsPage.js` - Shared Wiring for the Status / Sources / MCP / Logs / Metrics Pages
`setup(options)` renders the shared chrome (sidebar, minimal header, modals),
marks the current page in the sidebar and wires theme, language and the
calculator. Copy held in the markup is translated through `data-i18n` /
`data-i18n-html`; copy a page renders itself (status readouts, source rows, copy
buttons) comes back through `options.onLanguageChange`, a callback run once on
load and again on every language switch. Pages keep the payload they last
fetched so the callback can redraw without waiting for the next refresh.

#### `state.js` - Centralized State Management
Exports functions for all localStorage/sessionStorage operations.

**Storage Keys**: `THEME`, `TV_THEME`, `LANGUAGE`, `TV_LANGUAGE`, `FAVORITE_SPOTS`, `SHOWING_FAVORITES`, `SELECTED_COUNTRY`, `DESKTOP_VIEW_MODE`, `PREVIOUS_URL`, `FORECAST_VIEW_PREFERENCE`, `FILTER_WINDY_DAYS`, `FORECAST_MODEL` (sessionStorage), `HERO_VISIBLE`, `CALCULATOR_INPUTS`, `FIRING_SORT`, `LIVE_STATIONS_ONLY`, `WIND_OVERLAY_MODE`, `MAP_SPOTS_VISIBLE`, `SIDEBAR_COLLAPSED`

**Exported Functions**:
- Theme: `getTheme()`, `setTheme()`, `applyTheme()`, `getCurrentTheme()`, `toggleTheme()`
- Language: `getLanguage()` (auto-detects from browser), `setLanguage()`, `toggleLanguage()`
- Favorites: `getFavorites()`, `saveFavorites()`, `isFavorite()`, `toggleFavorite()`
- Country: `getSelectedCountry()`, `setSelectedCountry()`
- View: `getDesktopViewMode()`, `setDesktopViewMode()`
- Ordering: `getSpotOrder()`, `saveSpotOrder()`, `getListOrder()`, `saveListOrder()`
- Forecast: `getForecastViewPreference()`, `setForecastViewPreference()`, `getSelectedModel()`, `setSelectedModel()`
- Hero: `getHeroVisible()`, `setHeroVisible()`

#### `translations.js` - Internationalization
```javascript
const translations = {
    en: { /* English translations */ },
    pl: { /* Polish translations */ }
};

function t(key) {
    const lang = getLanguage(); // auto-detects from browser if not stored
    return translations[lang][key] || translations.en[key] || key;
}
```

**Features**:
- 200+ translation keys
- Fallback mechanism: PL → EN → key
- Dynamic UI updates on language change
- Covers all UI text, errors, labels, tooltips

**Helpers alongside `t()`**:
- `applyStaticTranslations(root = document)` - fills every element carrying
  `data-i18n` (text), `data-i18n-html` (markup, for copy with links or
  `<code>`) or `data-i18n-placeholder` (input placeholders) from the table. The
  `<title>` carries one too, so the browser tab follows the language switch.
  Pages keeping their wording in HTML use this instead of listing element ids
  one by one.
- `plural(count, key)` - Polish takes three forms, so the base key holds the
  "many" form and `${key}One` / `${key}Few` the other two (few = counts ending
  in 2-4 except 12-14). A missing variant falls back to the base key, which is
  how English gets by with just the singular and the plural.
- `locale()` - `pl-PL` or `en-GB`, for `toLocaleString()` and friends. It
  follows the language switch rather than the machine the page runs on;
  otherwise an English page prints Polish month names on a Polish desktop.

#### `page/index.js` - Dashboard Logic
**State Management**:
```javascript
let globalWeatherData = [];           // Cached spot data
let availableCountries = new Set();   // Extracted country list
let currentSearchQuery = '';          // Active search term
let showingFavorites = false;         // Favorites view flag
let autoRefreshInterval = null;       // Auto-refresh timer
```

**Key Functions**:
- **Routing**: `updateUrlForCountry()`, `getCountryFromUrl()`, `updateUrlForStarred()`
- **Favorites**: `getFavorites()`, `toggleFavorite()`, `isFavorite()` (persisted in `localStorage`)
- **Rendering**: `renderSpots()`, `createSpotCard()`, `renderForecastTable()`
- **Filtering**: `filterSpots()`, `searchSpots()`, `populateCountryDropdown()`
- **Drag & Drop**: `initDragAndDrop()` (custom ordering, persisted in `localStorage`)
- **Modals**: `openInfoModal()`, `openAIModal()`, `openIcmModal()`, `openKiteSizeModal()`,
  `openAiGenerateModal()` / `openIcmGenerateModal()` (spot page, confirm a generation)

#### `page/spot.js` - Spot Detail Logic
**State Management**:
```javascript
let currentSpot = null;               // Loaded spot data
let currentSpotId = null;             // Spot ID from URL (a string - see spotKey())
let selectedModel = 'gfs';            // Forecast model (GFS/IFS)
let forecastPollIntervalId = null;    // IFS polling timer
let backgroundRefreshIntervalId = null; // Auto-refresh timer

// Which on-demand generations are running, kept here rather than in the DOM: the
// card is rebuilt from scratch every minute by the background refresh, so a
// spinner living only in the markup would vanish mid-generation.
const aiAnalysisGenerating = new Set();  // keys: `${wgId}:${language}`
const icmForecastGenerating = new Set(); // keys: wgId
const aiAnalysisErrors = new Map();      // last failure per spot
const icmForecastErrors = new Map();
```

`spotKey(wgId)` coerces to Number before touching any of those four: the route
hands over a string while the spot object carries a number, and Set/Map compare by
identity, so mixing the two would silently lose every lookup.

**Key Functions**:
- **Data Fetching**: `fetchSpotData(spotId)`, `hasForecastData(spot)`
- **Polling**: `startForecastPolling()` (5s interval, 30s timeout), `clearForecastPolling()`
- **Rendering**: `displaySpot()`, `renderForecastTable()`, `renderWindguruView()`, `renderCurrentConditionsCard()`
- **Forecast Views**: `switchToTableView()`, `switchToWindguruView()` (desktop only)
- **On-demand generation**: `requestAiAnalysis(wgId)`, `requestIcmForecast(wgId)`,
  `setupOnDemandGenerationButtons()` (re-wired after every card rebuild),
  `openAiGenerateModal()` / `openIcmGenerateModal()` and their close pairs,
  `updateConfirmModalTranslations(ids)` (each element id doubles as its key)
- **Helpers**: `getWindArrow()`, `getWindRotation()`, `translateDayName()`, `formatForecastDateLabel()`

## Component Architecture

### 1. Spot Card Component (Dashboard)
**HTML Structure**:
```html
<div class="spot-card" draggable="true">
    <div class="drag-handle">⋮⋮</div>
    <div class="spot-header">
        <div class="spot-title">
            <div class="country-tag-wrapper">
                <div class="favorite-icon">★</div>
                <span class="country-tag">🇵🇱 Poland</span>
            </div>
            <div class="spot-name">Władysławowo</div>
        </div>
        <div class="spot-meta">
            <span class="last-updated">3h ago</span>
        </div>
    </div>
    <div class="external-links">
        <a class="external-link">Windguru</a>
        <a class="external-link">Windfinder</a>
        <!-- ... -->
    </div>
    <table class="weather-table">
        <!-- Forecast rows -->
    </table>
</div>
```

**CSS Classes**:
- `.spot-card` - Main container with border and padding
- `.drag-handle` - Drag-and-drop grip (desktop only)
- `.favorite-icon` - Star icon with favorited state
- `.weather-table` - Forecast table with color-coded wind conditions

**Interactions**:
- Click spot name → Navigate to `/spot/{id}`
- Click favorite star → Toggle favorite status
- Drag card → Reorder spots (persisted)
- Click external link → Open in new tab

### 2. Weather Table Component
**Structure**:
```html
<table class="weather-table">
    <thead>
        <tr>
            <th>Date</th>
            <th>Wind</th>
            <th>Gusts</th>
            <th>Direction</th>
            <th>Temp</th>
            <th>Rain</th>
        </tr>
    </thead>
    <tbody>
        <tr class="moderate-wind"> <!-- Dynamic class -->
            <td>14. Mon 12:00</td>
            <td class="wind-moderate">15 kts</td>
            <td class="wind-moderate">18 kts</td>
            <td>↗ SW</td>
            <td class="temp-positive">12°C</td>
            <td class="precipitation-none">0 mm</td>
        </tr>
    </tbody>
</table>
```

**Wind Classification**:
- `weak-wind`: < 12 kts (gray, not rideable)
- `moderate-wind`: 12-19 kts (green, good conditions)
- `strong-wind`: 20-27 kts (orange, strong)
- `extreme-wind`: 28+ kts (red, dangerous)

**Dynamic Styling**:
- Row background color based on wind speed
- Color-coded values (wind, temp, rain)
- Responsive font sizes (3-column view on large screens)

### 3. Current Conditions Card (Single Spot)
**Structure**:
```html
<div class="current-conditions-card">
    <div class="conditions-header">
        <span class="conditions-label">Current Conditions</span>
        <div class="live-indicator">
            <span class="live-text">LIVE</span>
            <span class="live-dot"></span>
        </div>
    </div>
    <div class="conditions-main">
        <div class="wind-arrow-large wind-moderate">↗</div>
        <div class="wind-details">
            <div class="wind-speed wind-moderate">15</div>
            <div class="wind-label">KNOTS</div>
        </div>
    </div>
    <div class="conditions-grid">
        <div class="condition-item">
            <div class="condition-label">Gusts</div>
            <div class="condition-value wind-moderate">18 kts</div>
        </div>
        <!-- ... -->
    </div>
</div>
```

**Features**:
- Large wind arrow with rotation animation
- Wind speed with color-coded background
- Live indicator with pulsing dot animation
- Desktop only (hidden on mobile)

### 4. Modal Overlay System
**Types**:
- **Info Modal**: Spot details (type, best wind, hazards, season)
- **AI Modal**: LLM-generated forecast analysis
- **ICM Modal**: ICM forecast image viewer
- **Kite Size Modal**: Kite/board size calculator
- **App Info Modal**: About page (contact, collaboration), with the running
  version greyed out next to its title
- **Confirm Modal** (`.modal-confirm`): narrow dialog with a question, a hint and
  an action row of one secondary and one primary button - used by the hero
  banner's X

**Structure**:
```html
<div class="modal-overlay" id="infoModal">
    <div class="modal">
        <div class="modal-header">
            <div class="modal-title">🏄 Spot Name</div>
            <button class="modal-close">×</button>
        </div>
        <div class="modal-content">
            <!-- Dynamic content -->
        </div>
    </div>
</div>
```

**Interactions**:
- Click overlay → Close modal
- Click × button → Close modal
- ESC key → Close modal (implemented in JS)

### 5. Windguru View (Horizontal Forecast)
**Desktop Only Feature**:
- Windguru-inspired horizontal scrolling layout
- Row labels (time, wind, gusts, direction, temp, rain)
- Day columns with 3-hour intervals
- Drag-to-scroll interaction

**Structure**:
```html
<div class="windguru-wrapper">
    <div class="windguru-labels">
        <div class="windguru-label-header">Time</div>
        <div class="windguru-label">Wind</div>
        <!-- ... -->
    </div>
    <div class="windguru-data-container">
        <div class="windguru-data">
            <div class="windguru-day-column">
                <div class="windguru-day-header">Mon 14</div>
                <div class="windguru-data-row">
                    <div class="windguru-cell">12:00</div>
                    <div class="windguru-cell">15:00</div>
                    <!-- ... -->
                </div>
            </div>
        </div>
    </div>
</div>
```

## State Management

### LocalStorage Keys

| Key | Type | Description |
|-----|------|-------------|
| `theme` | string | `'dark'` or `'light'` |
| `language` | string | `'en'` or `'pl'` |
| `favoriteSpots` | JSON array | List of favorite spot names |
| `spotOrder` | JSON array | Custom spot ordering (spot IDs) |
| `selectedCountry` | string | Last selected country filter |
| `previousUrl` | string | URL before entering `/starred` view |
| `desktopViewMode` | string | `'grid'` (default), view mode preference |
| `forecastViewPreference` | string | `'table'` or `'windguru'` |
| `filterWindyDays` | string | `'true'` or `'false'` |
| `heroVisible` | string | `'true'` or `'false'` (hero section visibility) |
| `showingFavorites` | string | `'true'` or `'false'` |
| `tvTheme` | string | Theme for the TV view (kept separate from `theme`) |
| `tvLanguage` | string | Language for the TV view (kept separate from `language`) |
| `calculatorInputs` | JSON object | Remembered kite/board calculator inputs |
| `firingSort` | string | `'true'` or `'false'` (sort by strongest wind) |
| `liveStationsOnly` | string | `'true'` or `'false'` (show only spots with live stations) |
| `windOverlayMode` | string | Wind overlay on the map: `'off'` or `'field'` (default) |
| `mapSpotsVisible` | string | `'true'` (default) or `'false'` (map spot markers and clusters) |
| `sidebarCollapsed` | string | `'true'` or `'false'` (sidebar state) |

### SessionStorage Keys

| Key | Type | Description |
|-----|------|-------------|
| `forecastModel` | string | Any Windguru model key, e.g. `'gfs'`, `'ifs'`, `'icon'` |

### In-Memory State

**Dashboard** (`page/index.js`):
- `globalWeatherData`: Cached spot data (array of ~780 spots)
- `availableCountries`: Set of unique countries
- `currentSearchQuery`: Active search term
- `showingFavorites`: Boolean flag

**Single Spot** (`page/spot.js`):
- `currentSpot`: Loaded spot object (includes currentConditionsHistory)
- `currentSpotId`: Spot ID from URL
- `selectedModel`: Forecast model (`'gfs'` or `'ifs'`)
- `forecastPollIntervalId`: Timer for IFS polling
- `backgroundRefreshIntervalId`: Timer for auto-refresh
- `conditionsHistoryChart`: Canvas chart instance for wind history

## Routing Strategy

### Client-Side Routing (History API)
The app uses `pushState()` for SPA-like navigation without page reloads:

```javascript
// Dashboard routing
function updateUrlForCountry(country) {
    if (country === 'all') {
        window.history.pushState({country: 'all'}, '', '/');
    } else {
        const normalized = normalizeCountryForUrl(country);
        window.history.pushState({country}, '', `/country/${normalized}`);
    }
}

function updateUrlForStarred() {
    window.history.pushState({starred: true}, '', '/starred');
}
```

**URL Patterns**:
- `/` → All spots
- `/country/poland` → Poland spots only
- `/starred` → Favorites view
- `/spot/123` → Single spot detail (spot ID 123)
- `/status` → System status page
- `/sources` → Data sources page
- `/mcp` → MCP server configuration page

**Popstate Handling**:
```javascript
window.addEventListener('popstate', (event) => {
    // Re-render UI based on URL state
    if (isStarredUrl()) {
        renderFavorites();
    } else {
        const urlCountry = getCountryFromUrl();
        filterSpotsByCountry(urlCountry || 'all');
    }
});
```

## Data Flow

### Dashboard Data Flow
```
1. Page Load
   ↓
2. fetchWeatherData() → GET /api/v1/spots
   ↓
3. globalWeatherData = response (~780 spots)
   ↓
4. populateCountryDropdown() (extract unique countries)
   ↓
5. Parse URL → determine initial filter (country or starred)
   ↓
6. renderSpots(filter, searchQuery)
   ↓
7. DOM Update (spot cards)
   ↓
8. Auto-refresh every 60s → repeat from step 2
```

### Single Spot Data Flow
```
1. Page Load → Extract spot ID from URL
   ↓
2. fetchSpotData(spotId) → GET /api/v1/spots/{id}
   ↓
3. if (forecast.length === 0) → startForecastPolling()
   │  ↓
   │  Poll every 5s for up to 30s → GET /api/v1/spots/{id}/{model}
   │  ↓
   │  if (forecast available) → displaySpot()
   ↓
4. displaySpot(spot)
   ↓
5. Render: header, map, current conditions, spot photo, spot info, AI, forecast table/windguru
   ↓
6. if (currentConditionsHistory) → renderConditionsChart()
   │  ↓
   │  Canvas-based line chart showing 12-hour wind trend
   ↓
7. startBackgroundRefresh() → refresh every 60s
   ↓
8. Auto-update DOM with new data
```

## Styling Architecture

### CSS Organization

**CSS Variables** (`:root`):
```css
:root {
    /* Dark theme (default) */
    --bg-primary: #0f0f0f;
    --bg-secondary: #1a1a1a;
    --text-primary: #e8e8e8;
    --accent-primary: #4a9eff;
    /* ... */
}

[data-theme="light"] {
    /* Light theme overrides */
    --bg-primary: #ffffff;
    --text-primary: #0a0a0a;
    /* ... */
}
```

**Benefits**:
- Instant theme switching (no page reload)
- Consistent color palette
- Easy maintenance and customization

### Buttons

Every button in the app is one flat control: a 36px box on the `--radius-md`
(6px) step of the radius scale, a 1px border, weight 500. `.modal-button`,
`.calc-button`, `.mcp-copy-btn` and `.embed-copy-button` are aliases on the same
rules rather than families of their own, so per-page markup and JS hooks select
what they always did.

Two variants, and only two: **neutral** (surface fill + hairline border) for
anything reversible, **accent** (`.btn-primary`, `.modal-button-primary`) for the
one action a dialog or form exists to perform. Both carry the border, so the two
sit at the same height when they share a row.

The accent fill is `--button-accent-bg` / `--button-accent-hover` (and
`--button-success-bg` for confirmations), not the raw `--accent-primary`: white
on the raw accent measured 2.1:1 in dark and 3.7:1 in light, below AA.

### Layout Systems

#### Grid Layout (Dashboard)
```css
.spots-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(600px, 1fr));
    gap: 30px;
}

.spots-grid.three-columns {
    grid-template-columns: repeat(3, 1fr);
    gap: 20px;
}
```

#### Two-Column Layout (Single Spot - Desktop)
```css
.spot-detail-container {
    display: grid;
    grid-template-columns: 400px 1fr;
    gap: 24px;
}
```

#### Flexbox (Headers, Cards)
```css
.spot-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
}
```

### Responsive Design Strategy

**Breakpoints**:
- `1430px` - Switch from 2/3 columns to 1 column
- `768px` - Tablet/mobile view (hide sidebar, show mobile menu)
- `600px` - Small mobile adjustments

**Mobile-First Adaptations**:
```css
@media (max-width: 1430px) {
    .spots-grid {
        grid-template-columns: 1fr !important;
    }

    .hamburger-menu {
        display: block; /* Show mobile menu */
    }

    .header-controls {
        /* Collapse header controls */
        flex-direction: column;
    }
}

@media (max-width: 768px) {
    .spot-detail-container {
        grid-template-columns: 1fr; /* Stack layout */
    }

    .spot-detail-left {
        display: none; /* Hide sidebar */
    }

    .windguru-view {
        display: none !important; /* Hide horizontal view */
    }
}
```

### Animation & Transitions

**Smooth Transitions**:
```css
body {
    transition: background-color 0.3s ease, color 0.3s ease;
}

.spot-card {
    transition: border-color 0.2s ease;
}

.modal-overlay {
    animation: fadeIn 0.3s ease;
}
```

**Keyframe Animations**:
```css
@keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
}

@keyframes pulse-glow {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.8; transform: scale(1.1); }
}
```

### Color-Coded Wind Conditions

**Wind Strength Classes**:
```css
.wind-weak { color: #6b7280; } /* Gray - not rideable */
.wind-moderate { color: #22c55e; } /* Green - good */
.wind-strong { color: #f59e0b; } /* Orange - strong */
.wind-extreme { color: #ef4444; } /* Red - dangerous */
```

**Light Theme Adjustments**:
```css
[data-theme="light"] .wind-moderate {
    color: #059669; /* Darker green for readability */
    font-weight: 700;
}
```

## Internationalization (i18n)

### Implementation

**Translation Function**:
```javascript
function t(key) {
    const lang = getLanguage(); // auto-detects from browser if not stored
    return translations[lang][key] || translations.en[key] || key;
}
```

**Usage Examples**:
```javascript
// In HTML templates
modalTitle.textContent = t('aiAnalysisTitle');

// In error messages
showErrorMessage(t('errorLoadingSpot'));

// In dynamic content
searchInput.placeholder = t('searchPlaceholder');
```

### Language Toggle
```javascript
languageToggle.addEventListener('click', () => {
    const currentLang = localStorage.getItem('language') || 'en';
    const newLang = currentLang === 'en' ? 'pl' : 'en';
    localStorage.setItem('language', newLang);
    updateUITranslations(); // Re-render all text elements
});
```

### Translation Coverage
- **UI Elements**: Buttons, labels, placeholders, tooltips
- **Weather Data**: Day names, month names, table headers
- **Error Messages**: All error states with localized text
- **Modal Content**: Titles, descriptions, disclaimers
- **Country Names**: Named list in `translations.js`, keyed by the country name
  with its spaces removed (`CzechRepublic`). A country with no entry falls back
  to that key, so a missing entry surfaces as a name without its spaces

## Features & Interactions

### 1. Favorites System
**How It Works**:
- Click star icon on any spot card → toggle favorite status
- Favorites stored in `localStorage` (array of spot names)
- Click "Favorites" button in header → show only favorited spots
- URL changes to `/starred` when viewing favorites
- Exit favorites → restore previous URL

### 2. Drag-and-Drop Spot Ordering
**Implementation**:
- Each spot card has `draggable="true"` and drag handle (`⋮⋮`)
- On drag start → store spot ID in `dataTransfer`
- On drop → reorder spots array and persist to `localStorage`
- Desktop only (drag handle hidden on mobile)

### 3. Country Filtering
**Dropdown Behavior**:
- Auto-populated from spot data (~780 spots → 43 countries)
- Click country → filter spots + update URL (`/country/{name}`)
- "All" option → show all spots + reset URL to `/`
- Selected country persists in `localStorage`

### 4. Search Functionality
**Real-Time Search** (main page):
- Input text → filter spots by name or country (case-insensitive, diacritics folded)
- Search works across filtered country results
- Clear button (×) appears when text entered
- No API calls (client-side filtering)

**Jump Search** (single spot page, desktop only):
- The same field, but with no list on the page it drops a list of matching spots
  and navigates to the one picked (click, or arrows + Enter)
- Matching is shared with the main page via `common/search.js`, so the same
  typing finds the same spots on both
- The spots list is fetched once, lazily, on the first focus of the field

### 5. Theme Switching
**Dark/Light Mode**:
- Toggle button in header (sun/moon icon)
- Instant switch via CSS variables
- Persists in `localStorage`
- Initial theme from user preference

### 6. Auto-Refresh
**Dashboard**:
- Fetch new spot data every 60 seconds
- Update DOM only if data changed
- Visual indicator (last updated timestamp)

**Single Spot**:
- Background refresh every 60 seconds
- Poll for IFS forecast (5s interval, 30s timeout)
- Silent updates (no page reload)

### 7. Kite Size Calculator
**Inputs**:
- Wind speed (knots)
- Rider weight (kg)
- Skill level & conditions (dropdown)

**Output**:
- Recommended kite size (m²)
- Recommended board size (cm)
- Warnings for extreme/low wind

**Formula** (example):
```javascript
// Base calculation: kite size inversely proportional to wind speed
let kiteSize = (riderWeight / windSpeed) * 2.5;

// Adjust for skill level and conditions
if (skillLevel.includes('Beginner')) kiteSize *= 1.2;
if (conditions.includes('Waves')) kiteSize *= 1.1;
```

### 8. Modal System
**Types**:
- **Spot Info**: Static spot data (type, hazards, season)
- **AI Analysis**: LLM-generated forecast summary
- **ICM Forecast**: Large image viewer
- **Kite Calculator**: Interactive form with validation
- **App Info**: About page with contact info

**Interactions**:
- Click backdrop → close modal
- Click × button → close modal
- ESC key → close modal (if implemented)

### 9. Embedded Maps
**Google Maps Integration**:
- Lazy-loaded on single spot page (left sidebar)
- Satellite view with marker
- Backend extracts coordinates from location URLs (goo.gl, maps.app.goo.gl)
- Coordinates cached in backend (no repeated API calls)
- Frontend generates embedded iframe from coordinates:
  ```javascript
  const mapUrl = `https://maps.google.com/maps?q=${lat},${lon}&z=13&t=k&output=embed`;
  const iframe = `<iframe src="${mapUrl}" ...></iframe>`;
  ```

## Performance Optimizations

### 1. Lazy Loading
- **Maps**: Only load when spot page accessed
- **IFS Forecast**: Only fetch when spot opened (on-demand)
- **Images**: Deferred loading for modal content

### 2. Caching Strategy
**Client-Side**:
- `globalWeatherData` cached in memory (dashboard)
- `currentSpot` cached during single spot session
- `localStorage` for persistent data (theme, favorites, order)

**Backend Caching**:
- Forecasts: 3-hour TTL
- Current conditions: 1-minute refresh
- Embedded maps: Cached forever (unless spot updated)

### 3. Efficient DOM Updates
```javascript
// Only update if data changed
if (JSON.stringify(latestSpot) !== JSON.stringify(currentSpot)) {
    displaySpot(latestSpot);
}
```

### 4. Debounced Search
```javascript
// Search input with debounce (not shown in code, but recommended)
const debounceSearch = debounce((query) => {
    renderSpots(currentFilter, query);
}, 300);
```

### 5. Minification
- HTML: Inline CSS and JS minified (130KB → ~50KB gzipped)
- CSS: Single file, minified (57KB)
- JS: Inline, minified (no external dependencies)

## Error Handling

### Error States

**Dashboard Errors**:
- No spots found (empty filter/search result)
- API fetch failure (connection error, 404, 500)
- Invalid country in URL

**Single Spot Errors**:
- Invalid spot ID
- Spot not found (404)
- Forecast timeout (IFS not available after 30s)
- Connection errors

### Error Display
```javascript
function showErrorMessage(errorKey) {
    const spotsGrid = document.getElementById('spotsGrid');
    spotsGrid.innerHTML = `
        <div class="error-message">
            <span class="error-icon">⚠️</span>
            <div class="error-title">${t('error')}</div>
            <div class="error-description">${t(errorKey)}</div>
        </div>
    `;
}
```

### Graceful Degradation
- Missing AI analysis → hide AI button
- Missing current conditions → hide conditions card
- No webcam URL → hide webcam link
- Empty forecast → show loading/polling message

## Browser Compatibility

### Supported Browsers
- **Chrome/Edge**: 90+ (full support)
- **Firefox**: 88+ (full support)
- **Safari**: 14+ (full support)
- **Mobile Safari**: iOS 14+ (full support)
- **Chrome Mobile**: Android 5+ (full support)

### Required APIs
- **Fetch API**: Async data fetching
- **LocalStorage**: Persistent state
- **SessionStorage**: Temporary state (forecast model)
- **History API**: Client-side routing (`pushState`)
- **CSS Grid**: Layout system
- **CSS Variables**: Theming
- **Drag & Drop API**: Spot reordering (desktop only)

### Polyfills
None required (vanilla JS, modern browsers only).

## Build Process

### Compilation Pipeline
1. **Source Files** (`src/frontend/`):
   - `js/common/state.js` - Centralized state management
   - `js/common/translations.js` - i18n translations
   - `js/page/index.js` - Dashboard logic
   - `js/page/spot.js` - Single spot logic
   - `js/page/status.js` - Status page logic
   - `js/page/embed.js` - Embeddable widget logic
   - `html/index.html` - Dashboard template
   - `html/spot.html` - Spot page template
   - `html/status.html` - Status page template
   - `css/styles.css` - Global styles
   - `assets/*` - Static assets (logo, robots.txt, etc.)

2. **Build Script** (`build.ts`, run with Bun):
   - Bundle and minify JS per page into `assets/<page>.<hash>.js`
   - Minify CSS into `assets/<name>.<hash>.css`
   - Copy the logo into `assets/logo.<hash>.png`
   - Rewrite CSS/JS/image references in HTML to the hashed paths
   - Minify HTML (remove whitespace, comments)
   - Copy assets from `assets/` and spot photos from `images/spots/` to `static/`

3. **Output** (`src/main/resources/static/`):
   - `index.html`, `spot.html`, `status.html`, `sources.html`, `mcp.html`,
     `metrics.html`, `logs.html`, `embed.html`, `tv.html` (minified)
   - `assets/*.<hash>.{js,css,png}` - content-hashed bundles
   - `images/spots/<wgId>.jpg` - spot photos
   - `logo.png`, `ai.txt`, `llms.txt`, `robots.txt` (`sitemap.xml` is served dynamically)

### Cache Busting
- Hashed filenames mean a changed file always gets a new URL
- Spot photos keep a stable filename and get a `?v=<content hash>` suffix from the backend
- `CacheControlFilter` marks hashed/versioned URLs `immutable` for a year and forces HTML to be
  revalidated, so a deployment is visible without waiting for the Cloudflare cache
- `deployment.sh` additionally purges the Cloudflare cache when credentials are configured

### Deployment
- Static files served directly by Spring Boot (`/static/`)
- No CDN dependencies (all assets self-hosted, Cloudflare in front as a cache/proxy)

## Accessibility

### ARIA Support
- Semantic HTML5 tags (`<header>`, `<main>`, `<footer>`, `<nav>`)
- Button roles for interactive elements
- Alt text for images (logo, flags)
- `aria-label` for icon-only buttons

### Keyboard Navigation
- Tab through interactive elements (buttons, links, inputs)
- Enter/Space to activate buttons
- Drag-and-drop alternative: reorder via custom controls (future enhancement)

### Color Contrast
- WCAG AA compliance for text contrast
- Color-coded wind conditions with sufficient contrast
- Light theme optimized for readability

### Screen Reader Support
- Descriptive link text (not just "click here")
- Error messages announced via live regions
- Modal titles and content properly structured

## Security Considerations

### Session Cookie Authentication
- All API calls (`/api/v1/**`) require a valid `SESSION` cookie
- Session is automatically created when the frontend page loads (browser visit initializes the session)
- All `fetch()` calls include `credentials: 'same-origin'` to send the session cookie
- Direct API access without a session returns HTTP 401
- Exempt paths: `/api/v1/health`, `/actuator/**`, static assets

### XSS Prevention
- All user input sanitized before rendering
- `textContent` used instead of `innerHTML` where possible
- External links use `rel="noopener noreferrer"`

### CORS
- API endpoints restricted to same-origin
- External APIs (Windguru, Google Maps) accessed via backend proxy

### Content Security Policy (CSP)
- No inline event handlers (`onclick`, `onerror`)
- All scripts in `<script>` tags (no `eval()` or `new Function()`)
- External resources limited to analytics (DataFast)

## Future Enhancements

### Planned Features
1. **Progressive Web App (PWA)**:
   - Service worker for offline support
   - App manifest for "Add to Home Screen"
   - Push notifications for wind alerts

2. **Advanced Filtering**:
   - Multi-select countries
   - Wind range filter (e.g., 15-25 kts)
   - Water type filter (flat, choppy, waves)

3. **User Accounts**:
   - Cloud sync for favorites and spot order
   - Personal wind alerts (email/push)
   - Custom spot notes

4. **Data Visualization**:
   - Wind charts (line/bar graphs)
   - Historical data trends
   - Wind rose diagrams

5. **Social Features**:
   - User-submitted spot photos
   - Community comments and tips
   - Spot ratings and reviews

6. **Mobile App**:
   - Native iOS/Android apps
   - Geolocation-based spot recommendations
   - Offline mode with cached forecasts

## Testing Strategy

### Manual Testing
- Cross-browser testing (Chrome, Firefox, Safari, Edge)
- Mobile device testing (iOS, Android)
- Theme switching validation
- Language switching validation
- Drag-and-drop functionality

### Automated Testing (Future)
- Unit tests for utility functions (e.g., `getWindArrow()`, `parseForecastDate()`)
- Integration tests for API interactions
- E2E tests for critical user flows (Playwright/Cypress)

## Troubleshooting

### Common Issues

**Problem**: Spots not loading
- **Solution**: Check browser console for API errors, verify backend is running

**Problem**: Theme not persisting
- **Solution**: Check LocalStorage (F12 → Application tab), clear cache

**Problem**: Drag-and-drop not working
- **Solution**: Desktop only feature, check screen width (> 1430px)

**Problem**: IFS forecast not appearing
- **Solution**: Wait 30s for polling timeout, fallback to GFS forecast

**Problem**: Translations missing
- **Solution**: Check `translations.js` for missing keys, fallback to English

## Related Documentation

- **CLAUDE.md**: Backend architecture, API endpoints, data models
- **BACKEND.md**: System architecture diagrams, high-level overview (same directory)
- **README.md**: User guide, build instructions, deployment

## Contact & Contributing

For frontend-related issues, feature requests, or contributions:
- **GitHub Issues**: https://github.com/pwittchen/varun.surf/issues
- **Email**: hello@varun.surf
- **Pull Requests**: Welcome! Follow existing code style and conventions

---

**Last Updated**: March 2026
**Maintained By**: @pwittchen
