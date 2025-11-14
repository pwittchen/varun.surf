# FRONTEND_ARCH.md - Frontend Architecture Documentation

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
├── frontend/                      # Source files (NOT deployed)
│   ├── translations.js            # i18n configuration
│   ├── script-dashboard.js        # Dashboard page logic
│   ├── script-spot.js             # Single spot page logic
│   └── script-status.js           # Status page logic
├── src/main/resources/static/     # Compiled/minified files (deployed)
│   ├── index.html                 # Dashboard page (130KB minified)
│   ├── spot.html                  # Single spot page (104KB minified)
│   ├── status.html                # Status page (48KB minified)
│   ├── styles.css                 # Global styles (57KB)
│   ├── logo.png                   # Brand logo
│   ├── robots.txt                 # SEO configuration
│   └── sitemap.xml                # SEO sitemap
```

**Important**: Files in `src/main/resources/static/` are generated artifacts. All source editing should happen in `frontend/` directory.

## Architecture Overview

### High-Level Frontend Flow

```
Browser
    ↓
HTML Pages (index.html, spot.html, status.html)
    ↓
JavaScript Entry Points (inline <script> tags)
    ├─→ translations.js (i18n)
    ├─→ script-dashboard.js (dashboard logic)
    ├─→ script-spot.js (spot detail logic)
    └─→ script-status.js (status page logic)
    ↓
API Calls (Fetch)
    ├─→ GET /api/v1/spots (all spots)
    ├─→ GET /api/v1/spots/{id} (single spot + IFS)
    ├─→ GET /api/v1/spots/{id}/{model} (GFS or IFS)
    └─→ GET /api/v1/status (health check)
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
- Grid layout with spot cards (2 or 3 columns)
- Country dropdown filter
- Search functionality
- Favorites system (star icons)
- Drag-and-drop spot reordering
- Auto-refresh every 60 seconds
- Modal overlays (AI analysis, spot info, ICM forecast, kite calculator)

**JavaScript Logic** (`script-dashboard.js`):
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
- Forecast view tabs: Table View (vertical) and Windguru View (horizontal)
- Real-time current conditions card
- Embedded Google Maps (satellite view)
- Forecast model selector (GFS/IFS)
- Auto-refresh every 60 seconds
- Polling mechanism for IFS forecast availability

**JavaScript Logic** (`script-spot.js`):
- `fetchSpotData(spotId)` - Fetch single spot data
- `displaySpot(spot)` - Render spot details
- `startForecastPolling()` - Poll for IFS forecast (5s interval, 30s timeout)
- `startBackgroundRefresh()` - Auto-refresh current conditions (60s)
- `renderWindguruView()` - Horizontal forecast view
- Model selection persistence via `sessionStorage`

#### 3. Status Page (`status.html`)
**URL Pattern**:
- `/status` - System health dashboard

**Features**:
- Service uptime and version info
- API endpoint health checks
- System status indicators (green/red dots)
- Auto-refresh every 30 seconds

**JavaScript Logic** (`script-status.js`):
- `fetchStatus()` - Get system status from `/api/v1/status`
- `checkEndpoint(url)` - Health check for individual endpoints
- Auto-refresh with 30s interval

### Core JavaScript Modules

#### `translations.js` - Internationalization
```javascript
const translations = {
    en: { /* English translations */ },
    pl: { /* Polish translations */ }
};

function t(key) {
    const lang = localStorage.getItem('language') || 'en';
    return translations[lang][key] || translations.en[key] || key;
}
```

**Features**:
- 200+ translation keys
- Fallback mechanism: PL → EN → key
- Dynamic UI updates on language change
- Covers all UI text, errors, labels, tooltips

#### `script-dashboard.js` - Dashboard Logic
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
- **Modals**: `openInfoModal()`, `openAIModal()`, `openIcmModal()`, `openKiteSizeModal()`

#### `script-spot.js` - Spot Detail Logic
**State Management**:
```javascript
let currentSpot = null;               // Loaded spot data
let currentSpotId = null;             // Spot ID from URL
let selectedModel = 'gfs';            // Forecast model (GFS/IFS)
let forecastPollIntervalId = null;    // IFS polling timer
let backgroundRefreshIntervalId = null; // Auto-refresh timer
```

**Key Functions**:
- **Data Fetching**: `fetchSpotData(spotId)`, `hasForecastData(spot)`
- **Polling**: `startForecastPolling()` (5s interval, 30s timeout), `clearForecastPolling()`
- **Rendering**: `displaySpot()`, `renderForecastTable()`, `renderWindguruView()`, `renderCurrentConditionsCard()`
- **Forecast Views**: `switchToTableView()`, `switchToWindguruView()` (desktop only)
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
- **App Info Modal**: About page (contact, collaboration)

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
| `gridLayout` | string | `'2-column'` or `'3-column'` |

### SessionStorage Keys

| Key | Type | Description |
|-----|------|-------------|
| `forecastModel` | string | `'gfs'` or `'ifs'` (per spot) |

### In-Memory State

**Dashboard** (`script-dashboard.js`):
- `globalWeatherData`: Cached spot data (array of 74+ spots)
- `availableCountries`: Set of unique countries
- `currentSearchQuery`: Active search term
- `showingFavorites`: Boolean flag

**Single Spot** (`script-spot.js`):
- `currentSpot`: Loaded spot object
- `currentSpotId`: Spot ID from URL
- `selectedModel`: Forecast model (`'gfs'` or `'ifs'`)
- `forecastPollIntervalId`: Timer for IFS polling
- `backgroundRefreshIntervalId`: Timer for auto-refresh

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
3. globalWeatherData = response (74+ spots)
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
5. Render: header, map, current conditions, spot info, AI, forecast table/windguru
   ↓
6. startBackgroundRefresh() → refresh every 60s
   ↓
7. Auto-update DOM with new data
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
    const lang = localStorage.getItem('language') || 'en';
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
- **Country Names**: Full list of 25+ countries

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
- Auto-populated from spot data (74+ spots → 25+ countries)
- Click country → filter spots + update URL (`/country/{name}`)
- "All" option → show all spots + reset URL to `/`
- Selected country persists in `localStorage`

### 4. Search Functionality
**Real-Time Search**:
- Input text → filter spots by name (case-insensitive)
- Search works across filtered country results
- Clear button (×) appears when text entered
- No API calls (client-side filtering)

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
- Backend resolves short URLs (goo.gl) to coordinates
- Cached in backend (no repeated API calls)

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
1. **Source Files** (`frontend/`):
   - `translations.js`
   - `script-dashboard.js`
   - `script-spot.js`
   - `script-status.js`

2. **Build Script** (Gradle task):
   - Minify JavaScript (remove whitespace, comments)
   - Inline JS into HTML files
   - Minify HTML (inline CSS and JS)
   - Optimize CSS (remove unused rules)

3. **Output** (`src/main/resources/static/`):
   - `index.html` (130KB minified)
   - `spot.html` (104KB minified)
   - `status.html` (48KB minified)
   - `styles.css` (57KB minified)

### Deployment
- Static files served directly by Spring Boot (`/static/`)
- No CDN dependencies (all assets self-hosted)
- Single request for HTML + inline JS/CSS (no external HTTP requests)

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
- **ARCH.md**: System architecture diagrams, high-level overview
- **README.md**: User guide, build instructions, deployment
- **prompts/new-kite-spot.md**: Adding new kite spots

## Contact & Contributing

For frontend-related issues, feature requests, or contributions:
- **GitHub Issues**: https://github.com/pwittchen/varun.surf/issues
- **Email**: piotr@varun.surf
- **Pull Requests**: Welcome! Follow existing code style and conventions

---

**Last Updated**: November 2025
**Maintained By**: @pwittchen