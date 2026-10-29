//! The varun.surf CLI as a library: the binary (`main.rs`) wires it to clap and
//! exit codes, and the integration tests reach the renderers and domain logic.

pub mod api;
pub mod cli;
pub mod commands;
pub mod config;
pub mod domain;
pub mod error;
pub mod render;
