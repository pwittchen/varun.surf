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
    'Ireland': '🇮🇪',
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
    'Mexico': '🇲🇽',
    'Costa Rica': '🇨🇷'
};

export function getCountryFlag(country) {
    return COUNTRY_FLAGS[country] || '🏴';
}
