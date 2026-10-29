//! What a `<SPOT>` argument names (SPEC 4.1): a wgId, a spot or Windguru URL, a
//! favourite alias, or part of a spot name.

use crate::api::dto::Spot;
use crate::domain::text::fold;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SpotArg {
    Id(i64),
    Name(String),
}

/// Integers and URLs carry the id; anything else is a name (or an alias, which
/// the caller checks first since it needs the config).
pub fn parse_arg(arg: &str) -> SpotArg {
    let arg = arg.trim();
    if let Ok(id) = arg.parse::<i64>() {
        return SpotArg::Id(id);
    }
    if let Some(id) = id_from_url(arg) {
        return SpotArg::Id(id);
    }
    SpotArg::Name(arg.to_string())
}

/// `https://varun.surf/spot/500760`, `https://www.windguru.cz/500760`,
/// `varun.surf/spot/500760?x=1`: the last all-digit path segment.
fn id_from_url(arg: &str) -> Option<i64> {
    let lower = arg.to_ascii_lowercase();
    let looks_like_url = lower.contains("://")
        || lower.starts_with("varun.surf/")
        || lower.starts_with("www.")
        || lower.contains("windguru.cz/");
    if !looks_like_url {
        return None;
    }
    let path = arg.split(['?', '#']).next().unwrap_or(arg);
    path.trim_end_matches('/').rsplit('/').next().and_then(|segment| segment.parse().ok())
}

#[derive(Debug)]
pub enum NameMatch<'a> {
    One(&'a Spot),
    Many(Vec<&'a Spot>),
    None,
}

/// Case- and diacritic-insensitive substring match. Several matches resolve to
/// the one whose whole name is the query, if there is exactly one such.
pub fn match_name<'a>(spots: &'a [Spot], query: &str) -> NameMatch<'a> {
    let needle = fold(query.trim());
    if needle.is_empty() {
        return NameMatch::None;
    }
    let matches: Vec<&Spot> = spots.iter().filter(|s| fold(&s.name).contains(&needle)).collect();
    match matches.len() {
        0 => NameMatch::None,
        1 => NameMatch::One(matches[0]),
        _ => {
            let exact: Vec<&&Spot> = matches.iter().filter(|s| fold(s.name.trim()) == needle).collect();
            if exact.len() == 1 { NameMatch::One(exact[0]) } else { NameMatch::Many(matches) }
        }
    }
}

/// `find_spot_by_name`: what `varun search` lists. A superset of the MCP tool's
/// plain lowercase match, since folding only ever adds matches.
pub fn search<'a>(spots: &'a [Spot], query: &str) -> Vec<&'a Spot> {
    let needle = fold(query.trim());
    let plain = query.trim().to_lowercase();
    spots.iter().filter(|s| s.name.to_lowercase().contains(&plain) || fold(&s.name).contains(&needle)).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn spot(wg_id: i64, name: &str) -> Spot {
        Spot { wg_id, name: name.into(), country: "Poland".into(), ..Default::default() }
    }

    #[test]
    fn ids_and_urls() {
        assert_eq!(parse_arg("500760"), SpotArg::Id(500760));
        assert_eq!(parse_arg("https://varun.surf/spot/500760"), SpotArg::Id(500760));
        assert_eq!(parse_arg("varun.surf/spot/500760/?lang=pl"), SpotArg::Id(500760));
        assert_eq!(parse_arg("https://www.windguru.cz/48009"), SpotArg::Id(48009));
        assert_eq!(parse_arg("windguru.cz/48009#tab"), SpotArg::Id(48009));
        assert_eq!(
            parse_arg("https://varun.surf/country/poland"),
            SpotArg::Name("https://varun.surf/country/poland".into())
        );
        assert_eq!(parse_arg(" jastarnia "), SpotArg::Name("jastarnia".into()));
    }

    fn ids(m: NameMatch) -> Option<Vec<i64>> {
        match m {
            NameMatch::One(s) => Some(vec![s.wg_id]),
            NameMatch::Many(v) => Some(v.iter().map(|s| s.wg_id).collect()),
            NameMatch::None => None,
        }
    }

    #[test]
    fn single_substring_match() {
        let spots = [spot(1, "Jastarnia"), spot(2, "Mrzeżyno")];
        assert_eq!(ids(match_name(&spots, "mrzezyno")), Some(vec![2]));
        assert_eq!(ids(match_name(&spots, "JASTAR")), Some(vec![1]));
        assert_eq!(ids(match_name(&spots, "tarifa")), None);
        assert_eq!(ids(match_name(&spots, "  ")), None);
    }

    #[test]
    fn exact_name_breaks_a_tie() {
        let spots = [spot(1, "Rewa"), spot(2, "Mewia Rewa")];
        assert!(matches!(match_name(&spots, "rewa"), NameMatch::One(s) if s.wg_id == 1));
    }

    #[test]
    fn ambiguous_without_an_exact_name() {
        let spots = [spot(1, "Turawa Północ"), spot(2, "Turawa Południe")];
        assert!(matches!(match_name(&spots, "turawa"), NameMatch::Many(v) if v.len() == 2));
        let twins = [spot(1, "Puck"), spot(2, "Puck")];
        assert!(matches!(match_name(&twins, "puck"), NameMatch::Many(_)));
    }

    #[test]
    fn search_is_a_superset_of_the_mcp_match() {
        let spots = [spot(1, "Kuźnica"), spot(2, "Jastarnia")];
        assert_eq!(search(&spots, "KUŹ").len(), 1);
        assert_eq!(search(&spots, "kuz").len(), 1);
        assert_eq!(search(&spots, "a").len(), 2);
    }
}
