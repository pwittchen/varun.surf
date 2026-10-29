//! Station timestamps and their age. `CurrentConditions.date` is station-local wall
//! clock in one of several formats; like the web page, it is read as the viewer's
//! local time, and a reading 60 minutes old or more is stale.

use chrono::NaiveDateTime;

pub const STALE_AFTER_MINUTES: i64 = 60;

const FORMATS: [&str; 4] = ["%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%d.%m.%Y %H:%M", "%d/%m/%y %H:%M:%S"];

/// The formats `date.js` `isConditionsOutdated` accepts. None when unparseable.
pub fn parse_station_date(date: &str) -> Option<NaiveDateTime> {
    let date = date.trim();
    FORMATS.iter().find_map(|f| NaiveDateTime::parse_from_str(date, f).ok())
}

/// Minutes since the reading, None when the date cannot be read.
pub fn age_minutes(date: &str, now: NaiveDateTime) -> Option<i64> {
    parse_station_date(date).map(|d| (now - d).num_minutes())
}

/// Same rule as the web page and the Android app: unreadable counts as stale.
pub fn is_stale(date: Option<&str>, now: NaiveDateTime) -> bool {
    date.and_then(|d| age_minutes(d, now)).is_none_or(|age| age >= STALE_AFTER_MINUTES)
}

/// "5 min", "2 h 10 min", "3 d".
pub fn format_age(minutes: i64) -> String {
    let minutes = minutes.max(0);
    match minutes {
        0..60 => format!("{minutes} min"),
        60..1440 if minutes % 60 == 0 => format!("{} h", minutes / 60),
        60..1440 => format!("{} h {} min", minutes / 60, minutes % 60),
        _ => format!("{} d", minutes / 1440),
    }
}

pub fn now_local() -> NaiveDateTime {
    chrono::Local::now().naive_local()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn at(s: &str) -> NaiveDateTime {
        NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S").unwrap()
    }

    #[test]
    fn parses_every_station_format() {
        let expected = at("2026-10-29 22:15:00");
        assert_eq!(parse_station_date("2026-10-29 22:15:00"), Some(expected));
        assert_eq!(parse_station_date("2026-10-29 22:15"), Some(expected));
        assert_eq!(parse_station_date("29.10.2026 22:15"), Some(expected));
        assert_eq!(parse_station_date(" 29/10/26 22:15:00 "), Some(expected));
        assert_eq!(parse_station_date("yesterday"), None);
    }

    #[test]
    fn stale_after_an_hour_or_when_unreadable() {
        let now = at("2026-10-29 23:00:00");
        assert!(!is_stale(Some("2026-10-29 22:01:00"), now));
        assert!(is_stale(Some("2026-10-29 22:00:00"), now));
        assert!(is_stale(Some("garbage"), now));
        assert!(is_stale(None, now));
    }

    #[test]
    fn ages_read_naturally() {
        assert_eq!(format_age(-2), "0 min");
        assert_eq!(format_age(5), "5 min");
        assert_eq!(format_age(120), "2 h");
        assert_eq!(format_age(130), "2 h 10 min");
        assert_eq!(format_age(3000), "2 d");
    }
}
