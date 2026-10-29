//! Output: aligned tables on a terminal, tab-separated text in a pipe, JSON, and the
//! Markdown of `md.rs`. Answers go to stdout, everything else to stderr (SPEC 5.1).

pub mod md;

use comfy_table::presets::UTF8_FULL_CONDENSED;
use comfy_table::{Cell, CellAlignment, Color, ContentArrangement, Table};
use owo_colors::OwoColorize;
use serde::Serialize;

use crate::config::{Format, Lang, Units};
use crate::domain::wind_class::{WindClass, unit_label, wind_class};

/// What every renderer needs to know about the reader.
#[derive(Debug, Clone)]
pub struct Out {
    pub format: Format,
    pub color: bool,
    pub width: u16,
    pub lang: Lang,
    pub units: Units,
}

impl Out {
    /// English or Polish, by the reader's `--lang`.
    pub fn tr<'a>(&self, en: &'a str, pl: &'a str) -> &'a str {
        match self.lang {
            Lang::En => en,
            Lang::Pl => pl,
        }
    }

    /// "Wind (kts)", "Wind (m/s)", ...
    pub fn speed_header(&self, en: &str, pl: &str) -> String {
        format!("{} ({})", self.tr(en, pl), unit_label(self.units))
    }

    pub fn paint(&self, text: &str, style: Style) -> String {
        if !self.color {
            return text.to_string();
        }
        match style {
            Style::None => text.to_string(),
            Style::Bold => text.bold().to_string(),
            Style::Dim => text.dimmed().to_string(),
            Style::Warn => text.yellow().to_string(),
            Style::Wind(class) => match class {
                WindClass::Weak => text.bright_black().to_string(),
                WindClass::Moderate => text.green().to_string(),
                WindClass::Strong => text.yellow().to_string(),
                WindClass::Extreme => text.red().bold().to_string(),
            },
        }
    }

    pub fn heading(&self, text: &str) -> String {
        self.paint(text, Style::Bold)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Style {
    None,
    Bold,
    Dim,
    Warn,
    Wind(WindClass),
}

impl Style {
    pub fn wind(knots: f64) -> Style {
        Style::Wind(wind_class(knots))
    }
}

#[derive(Debug, Clone)]
pub struct Col {
    pub header: String,
    pub right: bool,
    /// Dropped first when the terminal is too narrow (country, wgId).
    pub low_priority: bool,
}

impl Col {
    pub fn left(header: impl Into<String>) -> Col {
        Col { header: header.into(), right: false, low_priority: false }
    }
    pub fn right(header: impl Into<String>) -> Col {
        Col { header: header.into(), right: true, low_priority: false }
    }
    pub fn low(mut self) -> Col {
        self.low_priority = true;
        self
    }
}

/// One cell: what a table shows (glyphs allowed), what plain text shows, and how
/// it is coloured on a terminal.
#[derive(Debug, Clone)]
pub struct Val {
    pub table: String,
    pub plain: String,
    pub style: Style,
}

impl Val {
    pub fn new(text: impl Into<String>) -> Val {
        let text = text.into();
        Val { table: text.clone(), plain: text, style: Style::None }
    }
    pub fn styled(text: impl Into<String>, style: Style) -> Val {
        Val { style, ..Val::new(text) }
    }
    /// A table rendering differing from the plain one, e.g. with an arrow.
    pub fn fancy(table: impl Into<String>, plain: impl Into<String>, style: Style) -> Val {
        Val { table: table.into(), plain: plain.into(), style }
    }
}

impl From<String> for Val {
    fn from(s: String) -> Val {
        Val::new(s)
    }
}

impl From<&str> for Val {
    fn from(s: &str) -> Val {
        Val::new(s)
    }
}

#[derive(Debug, Clone, Default)]
pub struct Grid {
    pub cols: Vec<Col>,
    pub rows: Vec<Vec<Val>>,
}

impl Grid {
    pub fn new(cols: Vec<Col>) -> Grid {
        Grid { cols, rows: Vec::new() }
    }

    pub fn push(&mut self, row: Vec<Val>) {
        debug_assert_eq!(row.len(), self.cols.len());
        self.rows.push(row);
    }

    /// A table on a terminal, tab-separated text otherwise.
    pub fn render(&self, out: &Out) -> String {
        match out.format {
            Format::Table => self.table(out),
            _ => self.plain(),
        }
    }

    pub fn plain(&self) -> String {
        let clean = |s: &str| s.replace(['\t', '\n'], " ");
        let mut text = self.cols.iter().map(|c| clean(&c.header)).collect::<Vec<_>>().join("\t");
        text.push('\n');
        for row in &self.rows {
            text.push_str(&row.iter().map(|v| clean(&v.plain)).collect::<Vec<_>>().join("\t"));
            text.push('\n');
        }
        text
    }

    fn table(&self, out: &Out) -> String {
        let visible = self.visible_columns(out.width as usize);
        let mut table = Table::new();
        table.load_style(UTF8_FULL_CONDENSED);
        table.set_content_arrangement(ContentArrangement::Dynamic);
        table.set_width(out.width);
        if out.color {
            table.enforce_styling();
        } else {
            table.force_no_tty();
        }
        table.set_header(visible.iter().map(|&i| {
            let cell = Cell::new(&self.cols[i].header);
            if out.color { cell.add_attribute(comfy_table::Attribute::Bold) } else { cell }
        }));
        for row in &self.rows {
            table.add_row(visible.iter().map(|&i| {
                let mut cell = Cell::new(&row[i].table);
                if self.cols[i].right {
                    cell = cell.set_alignment(CellAlignment::Right);
                }
                if out.color {
                    cell = style_cell(cell, row[i].style);
                }
                cell
            }));
        }
        table.to_string() + "\n"
    }

    /// Every column, minus low-priority ones (rightmost first) while the table
    /// would not fit.
    fn visible_columns(&self, width: usize) -> Vec<usize> {
        let widths: Vec<usize> = (0..self.cols.len())
            .map(|i| {
                self.rows
                    .iter()
                    .map(|r| r[i].table.chars().count())
                    .chain(std::iter::once(self.cols[i].header.chars().count()))
                    .max()
                    .unwrap_or(0)
            })
            .collect();
        let mut visible: Vec<usize> = (0..self.cols.len()).collect();
        let total = |v: &[usize]| v.iter().map(|&i| widths[i] + 3).sum::<usize>() + 1;
        while total(&visible) > width {
            let Some(pos) = visible.iter().rposition(|&i| self.cols[i].low_priority) else { break };
            visible.remove(pos);
        }
        visible
    }
}

fn style_cell(cell: Cell, style: Style) -> Cell {
    match style {
        Style::None => cell,
        Style::Bold => cell.add_attribute(comfy_table::Attribute::Bold),
        Style::Dim => cell.fg(Color::DarkGrey),
        Style::Warn => cell.fg(Color::Yellow),
        Style::Wind(class) => match class {
            WindClass::Weak => cell.fg(Color::DarkGrey),
            WindClass::Moderate => cell.fg(Color::Green),
            WindClass::Strong => cell.fg(Color::Yellow),
            WindClass::Extreme => cell.fg(Color::Red).add_attribute(comfy_table::Attribute::Bold),
        },
    }
}

/// "key: value" lines, aligned on a terminal and tab-separated in a pipe.
pub fn pairs(out: &Out, pairs: &[(String, String)]) -> String {
    let width = pairs.iter().map(|(k, _)| k.chars().count()).max().unwrap_or(0);
    let mut text = String::new();
    for (key, value) in pairs {
        if out.format == Format::Table {
            let pad = " ".repeat(width - key.chars().count());
            text.push_str(&format!("{}{pad}  {value}\n", out.paint(key, Style::Dim)));
        } else {
            text.push_str(&format!("{key}\t{value}\n"));
        }
    }
    text
}

pub fn json<T: Serialize>(value: &T) -> String {
    serde_json::to_string_pretty(value).expect("serialisable output") + "\n"
}

/// The JSON schemas of CLI-computed output carry this version (cli/schemas).
pub const SCHEMA_VERSION: u32 = 1;

#[cfg(test)]
mod tests {
    use super::*;

    fn out(format: Format, width: u16) -> Out {
        Out { format, color: false, width, lang: Lang::En, units: Units::Kts }
    }

    fn grid() -> Grid {
        let mut grid = Grid::new(vec![
            Col::left("Spot"),
            Col::left("Country").low(),
            Col::right("wgId").low(),
            Col::right("Wind"),
        ]);
        grid.push(vec![
            "Jastarnia".into(),
            "Poland".into(),
            "500760".into(),
            Val::fancy("15 →", "15", Style::wind(15.0)),
        ]);
        grid
    }

    #[test]
    fn plain_is_tab_separated_without_glyphs() {
        assert_eq!(
            grid().render(&out(Format::Plain, 80)),
            "Spot\tCountry\twgId\tWind\nJastarnia\tPoland\t500760\t15\n"
        );
    }

    #[test]
    fn narrow_tables_drop_low_priority_columns_rightmost_first() {
        let g = grid();
        assert_eq!(g.visible_columns(200), vec![0, 1, 2, 3]);
        assert_eq!(g.visible_columns(30), vec![0, 1, 3]);
        assert_eq!(g.visible_columns(10), vec![0, 3]);
    }

    #[test]
    fn table_has_no_ansi_without_colour() {
        let text = grid().render(&out(Format::Table, 80));
        assert!(text.contains("Jastarnia"));
        assert!(text.contains("15 →"));
        assert!(!text.contains('\u{1b}'));
    }
}
