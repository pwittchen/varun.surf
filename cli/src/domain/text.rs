//! Text helpers shared with the backend: the country slug, diacritic folding for
//! name search, and Java's `String.format` number rounding.

use unicode_normalization::UnicodeNormalization;
use unicode_normalization::char::is_combining_mark;

/// `LlmController.toSlug`: lowercased, trimmed, runs of whitespace → one hyphen.
pub fn to_slug(country: &str) -> String {
    country.to_lowercase().split_whitespace().collect::<Vec<_>>().join("-")
}

/// Lowercased with diacritics removed, so `mrzezyno` finds Mrzeżyno and `lodz` Łódź.
/// Letters that do not decompose into base + mark (ł, ø, ß, ...) are mapped by hand.
pub fn fold(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for c in text.nfd() {
        if is_combining_mark(c) {
            continue;
        }
        match c {
            'ł' | 'Ł' => out.push('l'),
            'ø' | 'Ø' => out.push('o'),
            'đ' | 'Đ' => out.push('d'),
            'ı' => out.push('i'),
            'ß' => out.push_str("ss"),
            'æ' | 'Æ' => out.push_str("ae"),
            'œ' | 'Œ' => out.push_str("oe"),
            _ => out.extend(c.to_lowercase()),
        }
    }
    out
}

/// Java's `Math.round` for the non-negative values wind comes in: half rounds up.
pub fn java_round(value: f64) -> i64 {
    (value + 0.5).floor() as i64
}

/// `String.format(Locale.ROOT, "%.<decimals>f", value)`. Java rounds the shortest
/// decimal representation half-up, so `0.15` prints `0.2` where Rust's `{:.1}`
/// (which rounds the exact binary value) prints `0.1`.
pub fn java_fixed(value: f64, decimals: usize) -> String {
    if !value.is_finite() {
        return if value.is_nan() {
            "NaN".to_string()
        } else if value > 0.0 {
            "Infinity".to_string()
        } else {
            "-Infinity".to_string()
        };
    }
    let negative = value.is_sign_negative();
    let repr = format!("{}", value.abs());
    let (int_part, frac_part) = repr.split_once('.').unwrap_or((&repr, ""));
    let mut digits: Vec<u8> = int_part.bytes().map(|b| b - b'0').collect();
    let int_len = digits.len();
    let frac: Vec<u8> = frac_part.bytes().map(|b| b - b'0').collect();
    for i in 0..decimals {
        digits.push(*frac.get(i).unwrap_or(&0));
    }
    if frac.get(decimals).is_some_and(|&d| d >= 5) {
        let mut i = digits.len();
        loop {
            if i == 0 {
                digits.insert(0, 1);
                break;
            }
            i -= 1;
            if digits[i] == 9 {
                digits[i] = 0;
            } else {
                digits[i] += 1;
                break;
            }
        }
    }
    let int_digits = digits.len() - decimals;
    debug_assert!(int_digits >= int_len);
    let mut out = String::new();
    if negative {
        out.push('-');
    }
    out.extend(digits[..int_digits].iter().map(|d| (b'0' + d) as char));
    if decimals > 0 {
        out.push('.');
        out.extend(digits[int_digits..].iter().map(|d| (b'0' + d) as char));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn slug_matches_the_backend() {
        assert_eq!(to_slug("Poland"), "poland");
        assert_eq!(to_slug("  Czech   Republic "), "czech-republic");
        assert_eq!(to_slug("Turks and Caicos"), "turks-and-caicos");
    }

    #[test]
    fn folding_drops_diacritics() {
        assert_eq!(fold("Mrzeżyno"), "mrzezyno");
        assert_eq!(fold("Łódź"), "lodz");
        assert_eq!(fold("Międzybrodzie Bialskie"), "miedzybrodzie bialskie");
        assert_eq!(fold("El Médano"), "el medano");
        assert_eq!(fold("Sønderstrand"), "sonderstrand");
        assert_eq!(fold("Großenbrode"), "grossenbrode");
    }

    #[test]
    fn java_round_is_half_up() {
        assert_eq!(java_round(11.5), 12);
        assert_eq!(java_round(11.49), 11);
        assert_eq!(java_round(0.0), 0);
    }

    #[test]
    fn java_fixed_matches_string_format() {
        assert_eq!(java_fixed(12.0, 0), "12");
        assert_eq!(java_fixed(12.5, 0), "13");
        assert_eq!(java_fixed(9.5, 0), "10");
        assert_eq!(java_fixed(0.15, 1), "0.2");
        assert_eq!(java_fixed(2.25, 1), "2.3");
        assert_eq!(java_fixed(12.0, 1), "12.0");
        assert_eq!(java_fixed(-0.4, 0), "-0");
        assert_eq!(java_fixed(-3.06, 1), "-3.1");
        assert_eq!(java_fixed(99.96, 1), "100.0");
        assert_eq!(java_fixed(54.123456, 5), "54.12346");
        assert_eq!(java_fixed(18.5, 5), "18.50000");
    }
}
