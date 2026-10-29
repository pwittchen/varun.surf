//! Bakes the git commit into `varun --version`: `VARUN_GIT_SHA` when the release
//! job sets it, otherwise whatever `git` says, otherwise "unknown".

use std::process::Command;

fn main() {
    println!("cargo:rerun-if-env-changed=VARUN_GIT_SHA");
    println!("cargo:rerun-if-changed=../.git/HEAD");
    let sha = std::env::var("VARUN_GIT_SHA")
        .ok()
        .filter(|s| !s.trim().is_empty())
        .or_else(|| {
            Command::new("git")
                .args(["rev-parse", "HEAD"])
                .output()
                .ok()
                .filter(|o| o.status.success())
                .and_then(|o| String::from_utf8(o.stdout).ok())
        })
        .map(|s| s.trim().chars().take(7).collect::<String>())
        .unwrap_or_else(|| "unknown".to_string());
    println!("cargo:rustc-env=VARUN_GIT_SHA={sha}");
}
