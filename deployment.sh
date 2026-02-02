#!/usr/bin/env bash
set -euo pipefail

# Parse command line argument
ENV="${1:-prod}"

if [[ "$ENV" != "dev" && "$ENV" != "prod" ]]; then
  echo "Usage: $0 [dev|prod]"
  echo "  dev - use docker-compose.dev.yml (builds from source)"
  echo "  prod  - use docker-compose.prod.yml (pulls from GHCR)"
  exit 1
fi

echo "==> Starting deployment"

# Load environment variables from .env file
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

# Get version from git tag
export VERSION=$(git describe --tags --abbrev=0 2>/dev/null || echo '0.0.1-SNAPSHOT')
echo "==> Version: $VERSION"

COMPOSE_FILE="docker-compose.${ENV}.yml"
if [[ "$ENV" == "prod" ]]; then
  COMPOSE_FILE="/root/apps/varun.surf/docker-compose.${ENV}.yml"
else
  COMPOSE_FILE="docker-compose.${ENV}.yml"
fi

echo "==> Using configuration: $COMPOSE_FILE"

# Set docker compose command
COMPOSE_CMD="docker compose -f $COMPOSE_FILE"

# Pull the latest docker image (prod only)
if [[ "$ENV" == "prod" ]]; then
  echo "==> Pulling latest image from GHCR..."
  docker pull ghcr.io/pwittchen/varun.surf:latest
fi

# Determine which environment is currently live
if docker ps --format '{{.Names}}' | grep -q '^varun-app-blue-live$'; then
  CURRENT="blue"
  NEXT="green"
  CURRENT_CONTAINER="varun-app-blue-live"
  NEXT_CONTAINER="varun-app-green-live"
  NEXT_PROFILE="green-live"
else
  CURRENT="green"
  NEXT="blue"
  CURRENT_CONTAINER="varun-app-green-live"
  NEXT_CONTAINER="varun-app-blue-live"
  NEXT_PROFILE="blue-live"
fi

# Check if this is the first run (no containers running)
if ! docker ps --format '{{.Names}}' | grep -qE '^varun-app-(blue|green)-live$'; then
  echo "==> First deployment: starting nginx and blue environment"
  $COMPOSE_CMD --profile blue-live up -d --wait varun-nginx varun-app-blue-live
  echo "==> Blue environment is live"
  exit 0
fi

# Blue-green swap
echo "==> Current: $CURRENT | Deploying: $NEXT"
echo "==> Starting $NEXT environment..."

# Use --build flag only for dev environment
if [[ "$ENV" == "dev" ]]; then
  $COMPOSE_CMD --profile "$NEXT_PROFILE" up -d --build --wait "$NEXT_CONTAINER"
else
  $COMPOSE_CMD --profile "$NEXT_PROFILE" up -d --wait "$NEXT_CONTAINER"
fi

echo "==> Waiting for nginx to discover new backend (DNS TTL: 5s)..."
sleep 6

echo "==> Stopping $CURRENT environment..."
$COMPOSE_CMD stop "$CURRENT_CONTAINER"
$COMPOSE_CMD rm -f "$CURRENT_CONTAINER"

echo "==> Deployment complete: $NEXT is now live"