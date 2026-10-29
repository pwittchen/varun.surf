use thiserror::Error;

/// Every way a command can fail, each mapped to the exit code of SPEC section 4.12.
#[derive(Debug, Error)]
pub enum CliError {
    /// Bad flag, empty query, unknown model.
    #[error("{0}")]
    Usage(String),
    /// Spot, country or search with no match.
    #[error("{0}")]
    NotFound(String),
    /// A spot name matching several spots, with no terminal to pick one on.
    #[error("{message}")]
    Ambiguous { message: String, candidates: Vec<String> },
    /// Backend unreachable, 5xx, or a session the backend keeps refusing.
    #[error("{0}")]
    Network(String),
    /// 429 from nginx. Never retried: the rate limit is what protects the backend.
    #[error("varun.surf is rate limiting requests (HTTP 429). Wait a minute before trying again.")]
    RateLimited,
    #[error("{0}")]
    Other(String),
}

impl CliError {
    pub fn exit_code(&self) -> i32 {
        match self {
            CliError::Other(_) => 1,
            CliError::Usage(_) => 2,
            CliError::NotFound(_) => 3,
            CliError::Ambiguous { .. } => 4,
            CliError::Network(_) => 5,
            CliError::RateLimited => 6,
        }
    }
}

pub type Result<T> = std::result::Result<T, CliError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exit_codes_follow_the_spec() {
        assert_eq!(CliError::Other(String::new()).exit_code(), 1);
        assert_eq!(CliError::Usage(String::new()).exit_code(), 2);
        assert_eq!(CliError::NotFound(String::new()).exit_code(), 3);
        let ambiguous = CliError::Ambiguous { message: String::new(), candidates: vec![] };
        assert_eq!(ambiguous.exit_code(), 4);
        assert_eq!(CliError::Network(String::new()).exit_code(), 5);
        assert_eq!(CliError::RateLimited.exit_code(), 6);
    }
}
