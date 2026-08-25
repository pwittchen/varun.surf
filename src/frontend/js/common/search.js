// ============================================================================
// SPOT SEARCH MATCHING
// Shared by the main page's spots filter and the single spot page's header
// search, so both accept the same typing: the query is folded down to plain
// ASCII letters first, which is what lets "swinoujscie" find "Świnoujście" and
// "leba" find "Łeba".
// ============================================================================

const SEARCH_CHAR_MAP = {
    'ł': 'l', 'Ł': 'l',
    'ø': 'o', 'Ø': 'o',
    'æ': 'ae', 'Æ': 'ae',
    'œ': 'oe', 'Œ': 'oe',
    'ß': 'ss', 'ẞ': 'ss',
    'đ': 'd', 'Đ': 'd',
    'ð': 'd', 'Ð': 'd',
    'þ': 'th', 'Þ': 'th',
    'ı': 'i', 'İ': 'i'
};

export function normalizeForSearch(text) {
    if (!text) return '';
    let result = '';
    for (const ch of text.toLowerCase()) {
        result += SEARCH_CHAR_MAP[ch] ?? ch;
    }
    return result.normalize('NFD').replace(/[̀-ͯ]/g, '');
}

/**
 * Does a spot answer to this query? Name and country both count, the same pair
 * the main page filters on.
 * @param {{name?: string, country?: string}} spot
 * @param {string} normalizedQuery - already run through normalizeForSearch
 * @returns {boolean}
 */
export function spotMatchesQuery(spot, normalizedQuery) {
    if (!normalizedQuery) return true;
    return normalizeForSearch(spot.name).includes(normalizedQuery)
        || (!!spot.country && normalizeForSearch(spot.country).includes(normalizedQuery));
}
