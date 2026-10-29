#!/bin/sh
# Captures the fixtures of the CLI tests from a running backend: the JSON the CLI
# computes from, and the /llms Markdown the server renders from the same data at
# the same moment, which the parity tests hold the client-side renderers to.
#
#   ./gradlew bootRun                      # in the repo root, wait for the forecasts
#   cli/tests/fixtures/capture.sh          # or: capture.sh https://varun.surf
#
# The spots list is trimmed to a few countries to keep the fixtures small; every
# document captured depends only on those countries, so the trim changes nothing.
set -eu

BASE="${1:-http://localhost:8080}"
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

COUNTRIES="Poland,Lithuania,Switzerland,Czech Republic"

get() { # <path> <file> [cookie]
  curl -fsS --compressed -H 'X-Client: varun-cli/fixtures' -b "$JAR" -c "$JAR" "$BASE$1" -o "$DIR/$2"
}

curl -fsS -c "$JAR" -o /dev/null "$BASE/api/v1/session"

get /api/v1/spots spots.full.json
get '/api/v1/wind?hours=24' wind-24.full.json
get '/api/v1/wind?hours=48' wind-48.full.json
get /api/v1/status status.json
get /api/v1/status/forecast status-forecast.json
for id in 500760 1584; do
  get "/api/v1/spots/$id" "spot-$id.json"
  get "/api/v1/forecast/$id" "forecast-$id.json"
  get "/llms/spots/$id.md" "llms-spot-$id.md"
  get "/llms/spots/$id/wind.md" "llms-wind-$id.md"
done
get '/llms/spots/500760/wind.md?hours=12&minWind=12' llms-wind-500760-h12-m12.md
get '/llms/spots/1584/wind.md?minWind=40' llms-wind-1584-m40.md
get /llms/countries/poland.md llms-country-poland.md
get /llms/countries/czech-republic.md llms-country-czech-republic.md
get '/llms/wind.md?country=poland' llms-windy-poland.md
get '/llms/wind.md?country=czech-republic' llms-windy-czech-republic.md
get '/llms/wind.md?country=Lithuania&minWind=8&hours=48&limit=3' llms-windy-lithuania-m8-h48-l3.md

python3 - "$DIR" "$COUNTRIES" <<'PY'
import json, os, sys
d, keep = sys.argv[1], set(sys.argv[2].split(","))
spots = [s for s in json.load(open(f"{d}/spots.full.json")) if s.get("country") in keep]
ids = {s["wgId"] for s in spots}
json.dump(spots, open(f"{d}/spots.json", "w"), ensure_ascii=False, separators=(",", ":"))
for hours in (24, 48):
    wind = json.load(open(f"{d}/wind-{hours}.full.json"))
    wind["spots"] = [s for s in wind["spots"] if s["wgId"] in ids]
    json.dump(wind, open(f"{d}/wind-{hours}.json", "w"), separators=(",", ":"))
    os.remove(f"{d}/wind-{hours}.full.json")
os.remove(f"{d}/spots.full.json")
PY
echo "fixtures captured from $BASE into $DIR"
