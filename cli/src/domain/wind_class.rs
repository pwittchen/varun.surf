//! Wind classes, direction arrows and display units. The thresholds are those of
//! `src/frontend/js/common/weather.js` (and `android/core/domain`), so a colour
//! means the same thing on every client.

use crate::config::Units;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WindClass {
    /// Below 12 kts: not a twin-tip session.
    Weak,
    /// 12 to under 18 kts.
    Moderate,
    /// 18 to 25 kts.
    Strong,
    /// Above 25 kts.
    Extreme,
}

/// `weather.js` `getWindClass`.
pub fn wind_class(knots: f64) -> WindClass {
    if knots < 12.0 {
        WindClass::Weak
    } else if knots < 18.0 {
        WindClass::Moderate
    } else if knots <= 25.0 {
        WindClass::Strong
    } else {
        WindClass::Extreme
    }
}

/// `weather.js` `getWindArrow`: an arrow pointing where the wind blows to.
pub fn arrow(direction: &str) -> &'static str {
    match direction.trim().to_ascii_uppercase().as_str() {
        "N" => "↓",
        "NE" => "↙",
        "E" => "←",
        "SE" => "↖",
        "S" => "↑",
        "SW" => "↗",
        "W" => "→",
        "NW" => "↘",
        _ => "•",
    }
}

/// Upper bounds (in knots, exclusive) of Beaufort forces 0 to 11; 64 kts and more is 12.
const BEAUFORT_LIMITS: [f64; 12] = [1.0, 4.0, 7.0, 11.0, 17.0, 22.0, 28.0, 34.0, 41.0, 48.0, 56.0, 64.0];

pub fn beaufort(knots: f64) -> u8 {
    let rounded = knots.round();
    BEAUFORT_LIMITS.iter().position(|&limit| rounded < limit).unwrap_or(12) as u8
}

/// A wind speed in the chosen units, as shown in a table. Filters stay in knots.
pub fn speed(knots: f64, units: Units) -> String {
    match units {
        Units::Kts => format!("{:.0}", knots.round()),
        Units::Ms => format!("{:.1}", knots * 0.514_444),
        Units::Kmh => format!("{:.0}", (knots * 1.852).round()),
        Units::Mph => format!("{:.0}", (knots * 1.150_779).round()),
        Units::Bft => beaufort(knots).to_string(),
    }
}

pub fn unit_label(units: Units) -> &'static str {
    match units {
        Units::Kts => "kts",
        Units::Ms => "m/s",
        Units::Kmh => "km/h",
        Units::Mph => "mph",
        Units::Bft => "Bft",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The boundaries of `getWindClass` in weather.js.
    #[test]
    fn classes_match_weather_js() {
        assert_eq!(wind_class(0.0), WindClass::Weak);
        assert_eq!(wind_class(11.9), WindClass::Weak);
        assert_eq!(wind_class(12.0), WindClass::Moderate);
        assert_eq!(wind_class(17.9), WindClass::Moderate);
        assert_eq!(wind_class(18.0), WindClass::Strong);
        assert_eq!(wind_class(25.0), WindClass::Strong);
        assert_eq!(wind_class(25.1), WindClass::Extreme);
    }

    #[test]
    fn arrows_match_weather_js() {
        assert_eq!(arrow("N"), "↓");
        assert_eq!(arrow("sw"), "↗");
        assert_eq!(arrow("NNE"), "•");
        assert_eq!(arrow(""), "•");
    }

    #[test]
    fn beaufort_scale() {
        assert_eq!(beaufort(0.0), 0);
        assert_eq!(beaufort(3.0), 1);
        assert_eq!(beaufort(10.0), 3);
        assert_eq!(beaufort(11.0), 4);
        assert_eq!(beaufort(16.0), 4);
        assert_eq!(beaufort(17.0), 5);
        assert_eq!(beaufort(33.0), 7);
        assert_eq!(beaufort(63.0), 11);
        assert_eq!(beaufort(80.0), 12);
    }

    #[test]
    fn unit_conversion() {
        assert_eq!(speed(20.0, Units::Kts), "20");
        assert_eq!(speed(20.0, Units::Ms), "10.3");
        assert_eq!(speed(20.0, Units::Kmh), "37");
        assert_eq!(speed(20.0, Units::Mph), "23");
        assert_eq!(speed(20.0, Units::Bft), "5");
    }
}
