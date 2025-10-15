#!/usr/bin/env bash
set -u

URL="https://varun.surf/api/v1/health"
DOWN_FILE="/tmp/varun.surf.down"
DOWN_THRESHOLD=120  # 2 minutes in seconds

STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$URL") || STATUS=0

if [ "$STATUS" -ne 200 ]; then
  # Site is DOWN
  if [ -f "$DOWN_FILE" ]; then
    DOWN_SINCE=$(cat "$DOWN_FILE")
    DOWN_SINCE_EPOCH=$(date -j -f "%Y-%m-%dT%H:%M:%S%z" "$DOWN_SINCE" "+%s" 2>/dev/null || date -d "$DOWN_SINCE" +%s 2>/dev/null)
    CURRENT_EPOCH=$(date +%s)
    DOWN_DURATION=$((CURRENT_EPOCH - DOWN_SINCE_EPOCH))

    echo "Health check failed (status: $STATUS). DOWN for $DOWN_DURATION seconds (since: $DOWN_SINCE)."

    if [ "$DOWN_DURATION" -ge "$DOWN_THRESHOLD" ]; then
      echo "Service has been DOWN for at least 2 minutes. Attempting restart..."
      rm -f "$DOWN_FILE"
      "/root/apps/varun.surf/deployment.sh" --restart
      echo "Restart command executed. Service should recover shortly."
      pusher varun.surf_RESTARTED_AFTER_DOWNTIME
    fi
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