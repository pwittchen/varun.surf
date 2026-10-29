//! `varun status`: `get_status`, with the backend version and the forecast sweep.

use crate::api::dto::ForecastFetchProgress;
use crate::commands::Ctx;
use crate::config::Format;
use crate::error::{CliError, Result};
use crate::render::{Style, md, pairs};

pub fn run(ctx: &mut Ctx, show_forecast: bool) -> Result<String> {
    let (status, body) = match ctx.client.status() {
        Ok(found) => found,
        Err(e @ (CliError::Network(_) | CliError::RateLimited)) => return health_fallback(ctx, e),
        Err(e) => return Err(e),
    };
    // A sweep that cannot be read is not worth failing the status over.
    let progress = ctx.client.forecast_progress().ok();
    let sweep = progress.as_ref().filter(|(p, _)| show_forecast || p.in_progress);

    Ok(match ctx.out.format {
        Format::Json => match (show_forecast, &progress) {
            (true, Some((_, progress_body))) => progress_body.trim_end().to_string() + "\n",
            _ => body.trim_end().to_string() + "\n",
        },
        Format::Md => {
            let mut text = md::status_sentence(&status) + "\n";
            if let Some((p, _)) = sweep {
                text.push_str(&format!("\n{}\n", sweep_sentence(p)));
            }
            text
        }
        _ => {
            let out = &ctx.out;
            let mut text = String::new();
            if out.format == Format::Table {
                text.push_str(&md::status_sentence(&status));
                text.push_str("\n\n");
            }
            let mut lines = vec![
                ("status".to_string(), status.status.clone().unwrap_or_default()),
                ("version".to_string(), status.version.clone().unwrap_or_default()),
                ("uptime".to_string(), status.uptime.clone().unwrap_or_default()),
                ("spots".to_string(), status.spots_count.to_string()),
                ("countries".to_string(), status.countries_count.to_string()),
                ("live_stations".to_string(), status.live_stations.to_string()),
            ];
            if let Some((p, _)) = sweep {
                lines.push((
                    "forecast_sweep".to_string(),
                    if p.in_progress { "running" } else { "finished" }.to_string(),
                ));
                lines.push(("forecast_completed".to_string(), format!("{}/{}", p.completed, p.total)));
                lines.push(("forecast_fetched".to_string(), p.fetched.to_string()));
                lines.push(("forecast_empty".to_string(), p.empty.to_string()));
                lines.push(("forecast_failed".to_string(), p.failed.to_string()));
                lines.push(("forecast_cached".to_string(), p.cached.to_string()));
            }
            text.push_str(&pairs(out, &lines));
            if let Some((p, _)) = sweep.filter(|(p, _)| p.in_progress)
                && out.format == Format::Table
            {
                text.push_str(&format!("\n{}\n", out.paint(&sweep_sentence(p), Style::Warn)));
            }
            text
        }
    })
}

fn sweep_sentence(p: &ForecastFetchProgress) -> String {
    if p.in_progress {
        format!(
            "The forecast sweep is running: {} of {} spots done, so some spots have no forecast yet.",
            p.completed, p.total
        )
    } else {
        format!(
            "The last forecast sweep covered {} spots in {} s: {} fetched, {} empty, {} failed.",
            p.total,
            p.elapsed_ms / 1000,
            p.fetched,
            p.empty,
            p.failed
        )
    }
}

/// The session failed: `/api/v1/health` needs none, and at least tells whether
/// the backend is up.
fn health_fallback(ctx: &mut Ctx, cause: CliError) -> Result<String> {
    match ctx.client.health() {
        Ok(health) => {
            ctx.warn(&format!("detailed status unavailable ({cause}); showing the health check"));
            Ok(match ctx.out.format {
                Format::Json => format!("{{\"status\":\"{health}\"}}\n"),
                Format::Md => format!("varun.surf health check: {health}.\n"),
                Format::Table => format!("varun.surf health check: {health}\n"),
                Format::Plain => format!("status\t{health}\n"),
            })
        }
        Err(_) => Err(cause),
    }
}
