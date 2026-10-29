#!/bin/sh
# Installs the varun.surf CLI (`varun`) from its GitHub Release.
#
#   curl -fsSL https://varun.surf/cli/install.sh | sh
#   curl -fsSL https://varun.surf/cli/install.sh | sh -s -- --version 1.2.0 --dir /usr/local/bin
#
# Supported: Linux x86_64, Linux arm64, macOS on Apple Silicon. Never uses sudo and
# never edits shell rc files. Re-running upgrades (or, with --version, downgrades).
#
# This script ships with the website, not with a CLI release, so it must not
# hard-code a CLI version: it works for every release whose archives are named
# varun-<version>-<target>.tar.gz (see cli/SPEC.md, section 9.1).

set -eu

REPO="pwittchen/varun.surf"
TAG_PREFIX="cli-v"

usage() {
    cat <<'EOF'
Install the varun.surf CLI.

Usage: install.sh [--version X.Y.Z] [--dir DIR]

Options:
  --version X.Y.Z  install this version instead of the newest one (env: VARUN_VERSION)
  --dir DIR        install into DIR, default ~/.local/bin (env: VARUN_INSTALL_DIR)
  -h, --help       show this help

Supported platforms: Linux x86_64, Linux arm64, macOS on Apple Silicon.
EOF
}

say() {
    printf 'varun-install: %s\n' "$*"
}

die() {
    printf 'varun-install: error: %s\n' "$*" >&2
    exit 1
}

has() {
    command -v "$1" >/dev/null 2>&1
}

# download URL [OUTPUT] - prints to stdout when no output file is given
download() {
    if has curl; then
        if [ $# -ge 2 ]; then
            curl -fsSL --retry 2 -o "$2" "$1"
        else
            curl -fsSL --retry 2 "$1"
        fi
    elif has wget; then
        if [ $# -ge 2 ]; then
            wget -q -O "$2" "$1"
        else
            wget -q -O - "$1"
        fi
    else
        die "neither curl nor wget is installed"
    fi
}

detect_target() {
    os=$(uname -s)
    arch=$(uname -m)
    case "$os" in
        Linux)
            case "$arch" in
                x86_64 | amd64) echo "x86_64-unknown-linux-musl" ;;
                aarch64 | arm64) echo "aarch64-unknown-linux-musl" ;;
                *) unsupported "$os" "$arch" ;;
            esac
            ;;
        Darwin)
            case "$arch" in
                arm64) echo "aarch64-apple-darwin" ;;
                x86_64)
                    # a shell running under Rosetta on an Apple Silicon Mac reports x86_64
                    if [ "$(sysctl -n sysctl.proc_translated 2>/dev/null || echo 0)" = "1" ]; then
                        echo "aarch64-apple-darwin"
                    else
                        die "Intel Macs are not supported (only macOS on Apple Silicon is)"
                    fi
                    ;;
                *) unsupported "$os" "$arch" ;;
            esac
            ;;
        *) unsupported "$os" "$arch" ;;
    esac
}

unsupported() {
    die "unsupported platform: $1 $2. Supported: Linux x86_64, Linux arm64, macOS on Apple Silicon (arm64)."
}

# The CLI releases are not marked "latest" (the backend's are), so the newest one is
# the first cli-v* tag in the release list, which GitHub returns newest first.
latest_version() {
    releases=$(download "https://api.github.com/repos/${REPO}/releases?per_page=100") ||
        die "could not ask GitHub for the latest release (rate limited?). Retry with --version X.Y.Z"
    version=$(printf '%s\n' "$releases" |
        grep -o "\"tag_name\": *\"${TAG_PREFIX}[^\"]*\"" |
        head -n 1 |
        sed "s/.*\"${TAG_PREFIX}\([^\"]*\)\"/\1/")
    [ -n "$version" ] || die "no CLI release found on GitHub. Retry with --version X.Y.Z"
    echo "$version"
}

sha256_of() {
    if has sha256sum; then
        sha256sum "$1" | cut -d ' ' -f 1
    elif has shasum; then
        shasum -a 256 "$1" | cut -d ' ' -f 1
    else
        die "neither sha256sum nor shasum is installed, cannot verify the download"
    fi
}

main() {
    version="${VARUN_VERSION:-}"
    dir="${VARUN_INSTALL_DIR:-}"

    while [ $# -gt 0 ]; do
        case "$1" in
            --version)
                [ $# -ge 2 ] || die "--version needs a value"
                version="$2"
                shift 2
                ;;
            --version=*)
                version="${1#--version=}"
                shift
                ;;
            --dir)
                [ $# -ge 2 ] || die "--dir needs a value"
                dir="$2"
                shift 2
                ;;
            --dir=*)
                dir="${1#--dir=}"
                shift
                ;;
            -h | --help)
                usage
                return 0
                ;;
            *)
                usage >&2
                die "unknown option: $1"
                ;;
        esac
    done

    target=$(detect_target)
    version="${version#v}"
    version="${version#"${TAG_PREFIX}"}"
    if [ -z "$version" ]; then
        version=$(latest_version)
    fi
    [ -n "$dir" ] || dir="${HOME}/.local/bin"

    archive="varun-${version}-${target}.tar.gz"
    url="https://github.com/${REPO}/releases/download/${TAG_PREFIX}${version}/${archive}"

    if ! mkdir -p "$dir" 2>/dev/null || [ ! -w "$dir" ]; then
        die "cannot write to ${dir}. Pick another directory with --dir, or re-run the whole command with sudo"
    fi

    tmp=$(mktemp -d 2>/dev/null || mktemp -d -t varun)
    trap 'rm -rf "$tmp"' EXIT INT TERM

    say "downloading varun ${version} for ${target}"
    download "$url" "${tmp}/${archive}" ||
        die "download failed: ${url} (does version ${version} exist?)"
    download "${url}.sha256" "${tmp}/${archive}.sha256" ||
        die "checksum download failed: ${url}.sha256"

    expected=$(cut -d ' ' -f 1 <"${tmp}/${archive}.sha256")
    actual=$(sha256_of "${tmp}/${archive}")
    if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
        die "checksum mismatch for ${archive} (expected ${expected}, got ${actual}); nothing installed"
    fi

    mkdir "${tmp}/x"
    tar -xzf "${tmp}/${archive}" -C "${tmp}/x"
    bin=$(find "${tmp}/x" -type f -name varun | head -n 1)
    [ -n "$bin" ] || die "the archive holds no varun binary"

    # copy next to the target and rename, so an upgrade never leaves a half-written binary
    staged="${dir}/.varun.$$"
    cp "$bin" "$staged"
    chmod 755 "$staged"
    mv -f "$staged" "${dir}/varun"

    say "installed $("${dir}/varun" --version 2>/dev/null || echo "varun ${version}") to ${dir}/varun"

    case ":${PATH}:" in
        *":${dir}:"*) ;;
        *)
            say "${dir} is not on your PATH. Add this line to ~/.profile or ~/.zshrc:"
            # shellcheck disable=SC2016 # $PATH is meant literally, for the rc file
            printf '\n    export PATH="%s:$PATH"\n\n' "$dir"
            ;;
    esac
}

main "$@"
