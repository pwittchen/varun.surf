#!/usr/bin/env bash
set -u

URL="https://varun.surf/api/v1/health"
DOWN_FILE="/tmp/varun.surf.down"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$URL") || STATUS=0

if [ "$STATUS" -ne 200 ]; then
  # Site is DOWN
  if [ -f "$DOWN_FILE" ]; then
    echo "Health check failed (status: $STATUS). Already marked DOWN since: $(cat "$DOWN_FILE")."
  else
    TS="$(date -Is)"
    echo "$TS" > "$DOWN_FILE"
    echo "Health check failed (status: $STATUS). Marking DOWN at $TS and running pusher..."
    pusher varun.surf_IS_DOWN
  fi
else
  echo "Health check OK (status $STATUS)."
  if [ -f "$DOWN_FILE" ]; then
    DOWN_SINCE="$(cat "$DOWN_FILE")"
    rm -f "$DOWN_FILE"
    echo "Service recovered (was DOWN since $DOWN_SINCE). Running pusher..."
    pusher varun.surf_IS_UP
  fi
fi