// ============================================================================
// COUNTRY FLAGS
// ============================================================================

export const COUNTRY_FLAGS = {
    'Poland': '🇵🇱',
    'Czech Republic': '🇨🇿',
    'Austria': '🇦🇹',
    'Belgium': '🇧🇪',
    'Switzerland': '🇨🇭',
    'Latvia': '🇱🇻',
    'Lithuania': '🇱🇹',
    'Estonia': '🇪🇪',
    'Denmark': '🇩🇰',
    'Sweden': '🇸🇪',
    'Norway': '🇳🇴',
    'Iceland': '🇮🇸',
    'Spain': '🇪🇸',
    'Portugal': '🇵🇹',
    'Italy': '🇮🇹',
    'Malta': '🇲🇹',
    'Greece': '🇬🇷',
    'France': '🇫🇷',
    'Germany': '🇩🇪',
    'Netherlands': '🇳🇱',
    'Croatia': '🇭🇷',
    'Slovenia': '🇸🇮',
    'Serbia': '🇷🇸',
    'Montenegro': '🇲🇪',
    'Albania': '🇦🇱',
    'Macedonia': '🇲🇰',
    'Bulgaria': '🇧🇬',
    'Romania': '🇷🇴',
    'Ireland': '🇮🇪',
    'United Kingdom': '🇬🇧',
    'UK': '🇬🇧',
    'Turkey': '🇹🇷',
    'Morocco': '🇲🇦',
    'Egypt': '🇪🇬',
    'Cape Verde': '🇨🇻',
    'Mauritius': '🇲🇺',
    'Brazil': '🇧🇷',
    'Peru': '🇵🇪',
    'Chile': '🇨🇱',
    'USA': '🇺🇸',
    'Namibia': '🇳🇦',
    'South Africa': '🇿🇦',
    'Tanzania': '🇹🇿',
    'Mexico': '🇲🇽',
    'Costa Rica': '🇨🇷',
    'Turks and Caicos': '🇹🇨',
    'Sri Lanka': '🇱🇰',
    'Vietnam': '🇻🇳'
};

export function getCountryFlag(country) {
    return COUNTRY_FLAGS[country] || '🏴';
}
