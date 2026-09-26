#!/usr/bin/env bash
set -euo pipefail

# Parse command line argument (supports DEPLOY_ENV variable to avoid shell pattern blocks)
ENV="${1:-${DEPLOY_ENV:-prod}}"

if [[ "$ENV" != "dev" && "$ENV" != "prod" ]]; then
  echo "Usage: $0 [dev|prod]"
  echo "  dev  - stop the deployment started from docker-compose.dev.yml"
  echo "  prod - stop the deployment started from docker-compose.prod.yml"
  exit 1
fi

echo "==> Stopping deployment"

# Load environment variables from .env file, so compose does not warn about unset variables
ENV_FILE="$(dirname "$0")/.env"
if [[ "$ENV" == "prod" ]]; then
  ENV_FILE="/root/apps/varun.surf/.env"
fi
if [[ -f "$ENV_FILE" ]]; then
  echo "==> Loading environment from $ENV_FILE"
  set -a
  source "$ENV_FILE"
  set +a
fi

if [[ "$ENV" == "prod" ]]; then
  COMPOSE_FILE="/root/apps/varun.surf/docker-compose.${ENV}.yml"
else
  COMPOSE_FILE="docker-compose.${ENV}.yml"
fi

echo "==> Using configuration: $COMPOSE_FILE"

# Both profiles are enabled, so whichever of blue and green is live gets stopped,
# along with nginx and the bluegreen network
docker compose -f "$COMPOSE_FILE" --profile blue-live --profile green-live down --remove-orphans

echo "==> Deployment stopped"
exit 0
