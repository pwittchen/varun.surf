// ============================================================================
// STATE MANAGEMENT MODULE
// ============================================================================
// Centralized state management utilities for localStorage and sessionStorage

// ============================================================================
// STORAGE KEYS
// ============================================================================

export const STORAGE_KEYS = {
    // Theme
    THEME: 'theme',
    TV_THEME: 'tvTheme',

    // Language
    LANGUAGE: 'language',
    TV_LANGUAGE: 'tvLanguage',

    // Favorites
    FAVORITE_SPOTS: 'favoriteSpots',
    SHOWING_FAVORITES: 'showingFavorites',

    // Country filter
    SELECTED_COUNTRY: 'selectedCountry',

    // View preferences
    DESKTOP_VIEW_MODE: 'desktopViewMode',
    PREVIOUS_URL: 'previousUrl',

    // Forecast preferences (spot page)
    FORECAST_VIEW_PREFERENCE: 'forecastViewPreference',
    FILTER_WINDY_DAYS: 'filterWindyDays',
    FORECAST_MODEL: 'forecastModel' // sessionStorage
};

// ============================================================================
// THEME MANAGEMENT
// ============================================================================

export function getTheme(isTvMode = false) {
    const key = isTvMode ? STORAGE_KEYS.TV_THEME : STORAGE_KEYS.THEME;
    return localStorage.getItem(key) || 'dark';
}

export function setTheme(theme, isTvMode = false) {
    const key = isTvMode ? STORAGE_KEYS.TV_THEME : STORAGE_KEYS.THEME;
    localStorage.setItem(key, theme);
}

export function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
}

export function getCurrentTheme() {
    return document.documentElement.getAttribute('data-theme') || 'dark';
}

export function toggleTheme(isTvMode = false) {
    const current = getCurrentTheme();
    const newTheme = current === 'dark' ? 'light' : 'dark';
    setTheme(newTheme, isTvMode);
    applyTheme(newTheme);
    return newTheme;
}

// ============================================================================
// LANGUAGE MANAGEMENT
// ============================================================================

export function getLanguage(isTvMode = false) {
    const key = isTvMode ? STORAGE_KEYS.TV_LANGUAGE : STORAGE_KEYS.LANGUAGE;
    return localStorage.getItem(key) || 'en';
}

export function setLanguage(lang, isTvMode = false) {
    const key = isTvMode ? STORAGE_KEYS.TV_LANGUAGE : STORAGE_KEYS.LANGUAGE;
    localStorage.setItem(key, lang);
}

export function toggleLanguage(isTvMode = false) {
    const current = getLanguage(isTvMode);
    const newLang = current === 'en' ? 'pl' : 'en';
    setLanguage(newLang, isTvMode);
    return newLang;
}

// ============================================================================
// FAVORITES MANAGEMENT
// ============================================================================

export function getFavorites() {
    const favorites = localStorage.getItem(STORAGE_KEYS.FAVORITE_SPOTS);
    return favorites ? JSON.parse(favorites) : [];
}

export function saveFavorites(favorites) {
    localStorage.setItem(STORAGE_KEYS.FAVORITE_SPOTS, JSON.stringify(favorites));
}

export function isFavorite(spotName) {
    const favorites = getFavorites();
    return favorites.includes(spotName);
}

export function toggleFavorite(spotName) {
    let favorites = getFavorites();
    const index = favorites.indexOf(spotName);

    if (index > -1) {
        favorites.splice(index, 1);
    } else {
        favorites.push(spotName);
    }

    saveFavorites(favorites);
    return index === -1; // returns true if added, false if removed
}

export function getShowingFavorites() {
    return localStorage.getItem(STORAGE_KEYS.SHOWING_FAVORITES) === 'true';
}

export function setShowingFavorites(showing) {
    localStorage.setItem(STORAGE_KEYS.SHOWING_FAVORITES, showing ? 'true' : 'false');
}

// ============================================================================
// COUNTRY FILTER
// ============================================================================

export function getSelectedCountry() {
    return localStorage.getItem(STORAGE_KEYS.SELECTED_COUNTRY) || 'all';
}

export function setSelectedCountry(country) {
    localStorage.setItem(STORAGE_KEYS.SELECTED_COUNTRY, country);
}

// ============================================================================
// VIEW MODE
// ============================================================================

export function getDesktopViewMode() {
    return localStorage.getItem(STORAGE_KEYS.DESKTOP_VIEW_MODE) || 'grid';
}

export function setDesktopViewMode(mode) {
    localStorage.setItem(STORAGE_KEYS.DESKTOP_VIEW_MODE, mode);
}

// ============================================================================
// PREVIOUS URL (for favorites toggle navigation)
// ============================================================================

export function getPreviousUrl() {
    return localStorage.getItem(STORAGE_KEYS.PREVIOUS_URL) || '/';
}

export function setPreviousUrl(url) {
    localStorage.setItem(STORAGE_KEYS.PREVIOUS_URL, url);
}

// ============================================================================
// CUSTOM SPOT ORDERING
// ============================================================================

function buildSpotOrderKey(columnMode, filter, searchQuery) {
    return `spotOrder_${columnMode}_${filter}_${searchQuery}`;
}

function buildListOrderKey(filter, searchQuery) {
    return `listOrder_${filter}_${searchQuery}`;
}

export function getSpotOrder(columnMode, filter, searchQuery) {
    const key = buildSpotOrderKey(columnMode, filter, searchQuery);
    const savedOrder = localStorage.getItem(key);
    return savedOrder ? JSON.parse(savedOrder) : null;
}

export function saveSpotOrder(columnMode, filter, searchQuery, order) {
    const key = buildSpotOrderKey(columnMode, filter, searchQuery);
    localStorage.setItem(key, JSON.stringify(order));
}

export function removeSpotOrder(columnMode, filter, searchQuery) {
    const key = buildSpotOrderKey(columnMode, filter, searchQuery);
    localStorage.removeItem(key);
}

export function getListOrder(filter, searchQuery) {
    const key = buildListOrderKey(filter, searchQuery);
    const savedOrder = localStorage.getItem(key);
    return savedOrder ? JSON.parse(savedOrder) : null;
}

export function saveListOrder(filter, searchQuery, order) {
    const key = buildListOrderKey(filter, searchQuery);
    localStorage.setItem(key, JSON.stringify(order));
}

export function removeListOrder(filter, searchQuery) {
    const key = buildListOrderKey(filter, searchQuery);
    localStorage.removeItem(key);
}

// ============================================================================
// FORECAST PREFERENCES (spot page)
// ============================================================================

export function getForecastViewPreference() {
    return localStorage.getItem(STORAGE_KEYS.FORECAST_VIEW_PREFERENCE) || 'table';
}

export function setForecastViewPreference(view) {
    localStorage.setItem(STORAGE_KEYS.FORECAST_VIEW_PREFERENCE, view);
}

export function getFilterWindyDays() {
    return localStorage.getItem(STORAGE_KEYS.FILTER_WINDY_DAYS) === 'true';
}

export function setFilterWindyDays(enabled) {
    localStorage.setItem(STORAGE_KEYS.FILTER_WINDY_DAYS, enabled ? 'true' : 'false');
}

// ============================================================================
// FORECAST MODEL (session storage - spot page)
// ============================================================================

export function getSelectedModel() {
    const model = sessionStorage.getItem(STORAGE_KEYS.FORECAST_MODEL);
    if (model && (model === 'gfs' || model === 'ifs')) {
        return model;
    }
    return 'gfs';
}

export function setSelectedModel(model) {
    sessionStorage.setItem(STORAGE_KEYS.FORECAST_MODEL, model);
}