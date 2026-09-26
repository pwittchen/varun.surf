#!/usr/bin/env bash
set -euo pipefail

# Switches the site into (or out of) maintenance mode.
#
#   on     - nginx starts answering every request with a 503 and the static page in
#            nginx/maintenance/maintenance.html, then the application is stopped, so
#            nothing fetches forecasts, live readings or anything else meanwhile
#   off    - the application is started again through deployment.sh, and only once
#            it is healthy does nginx go back to proxying to it
#   status - says whether maintenance mode is on
#
# Supports the MAINTENANCE_ACTION and DEPLOY_ENV variables to avoid shell pattern blocks.

ACTION="${1:-${MAINTENANCE_ACTION:-}}"
ENV="${2:-${DEPLOY_ENV:-prod}}"

usage() {
  echo "Usage: $0 [on|off|status] [dev|prod]"
  echo "  on     - show the maintenance page and stop the application"
  echo "  off    - start the application and take the maintenance page down"
  echo "  status - tell whether maintenance mode is on"
  exit 1
}

if [[ "$ACTION" != "on" && "$ACTION" != "off" && "$ACTION" != "status" ]]; then
  usage
fi

if [[ "$ENV" != "dev" && "$ENV" != "prod" ]]; then
  usage
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ "$ENV" == "prod" ]]; then
  APP_DIR="/root/apps/varun.surf"
else
  APP_DIR="$SCRIPT_DIR"
fi

COMPOSE_FILE="$APP_DIR/docker-compose.${ENV}.yml"
MAINTENANCE_DIR="$APP_DIR/nginx/maintenance"
FLAG_FILE="$MAINTENANCE_DIR/maintenance.on"

if [[ "$ACTION" == "status" ]]; then
  if [[ -f "$FLAG_FILE" ]]; then
    echo "==> Maintenance mode is ON (since $(date -r "$FLAG_FILE"))"
  else
    echo "==> Maintenance mode is OFF"
  fi
  exit 0
fi

# Load environment variables from .env file, so compose does not warn about unset variables
ENV_FILE="$APP_DIR/.env"
if [[ -f "$ENV_FILE" ]]; then
  echo "==> Loading environment from $ENV_FILE"
  set -a
  source "$ENV_FILE"
  set +a
fi

echo "==> Using configuration: $COMPOSE_FILE"
COMPOSE_CMD="docker compose -f $COMPOSE_FILE"

if [[ "$ACTION" == "on" ]]; then
  if [[ ! -f "$MAINTENANCE_DIR/maintenance.html" ]]; then
    echo "==> ERROR: $MAINTENANCE_DIR/maintenance.html is missing, copy nginx/maintenance/ first"
    exit 1
  fi

  # The flag goes up first, so visitors see the page rather than a 502 while the
  # application shuts down
  echo "==> Enabling the maintenance page"
  touch "$FLAG_FILE"

  # Recreates nginx if it is missing or still runs without the maintenance mount
  $COMPOSE_CMD up -d varun-nginx

  # An nginx.conf predating maintenance mode would ignore the flag, and stopping the
  # application would then leave visitors with a bare 502
  if ! docker exec varun-nginx grep -q 'maintenance.on' /etc/nginx/nginx.conf; then
    rm -f "$FLAG_FILE"
    echo "==> ERROR: nginx.conf has no maintenance mode, copy the current nginx/nginx.conf first"
    exit 1
  fi
  docker exec varun-nginx nginx -s reload

  echo "==> Stopping the application"
  $COMPOSE_CMD --profile blue-live --profile green-live stop varun-app-blue-live varun-app-green-live
  $COMPOSE_CMD --profile blue-live --profile green-live rm -f varun-app-blue-live varun-app-green-live

  echo "==> Maintenance mode is ON"
  exit 0
fi

# off: deployment.sh refuses to run while the flag is up, so a release pushed during
# maintenance cannot bring the application back behind the operator's back - this
# is the one caller allowed past that check. With no application running it takes
# its first-deployment path and waits for the healthcheck, which talks to the
# container directly, so the maintenance page stays up until the app can serve.
echo "==> Starting the application"
ALLOW_DURING_MAINTENANCE=1 DEPLOY_ENV="$ENV" "$SCRIPT_DIR/deployment.sh" "$ENV"

echo "==> Disabling the maintenance page"
rm -f "$FLAG_FILE"

echo "==> Maintenance mode is OFF"
exit 0
