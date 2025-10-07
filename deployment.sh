#!/usr/bin/env bash
if [ "$1" == "--help" ] || [ -z "$1" ]; then
  echo ""
  echo "  deployment.sh | script for managing varun.surf deployment"
  echo ""
  echo "  USAGE: deployment.sh [options]"
  echo ""
  echo "  --help      shows help"
  echo "  --login     logs into ghcr.io"
  echo "  --logs      shows app logs"
  echo "  --run       runs the app"
  echo "  --stop      stops the app"
  echo "  --pull      pulls the most recent docker image from registry"
  echo "  --restart   restarts currently running app"
  echo "  --reload    stops the app, pulls the most recent image and starts the app again"
  echo "  --ps        shows currently running containers"
  echo ""
fi
if [ "$1" == "--login" ]; then
  PAT=YOUR_GITHUB_GHCR_PERSONAL_ACCESS_TOKEN
  echo $PAT | docker login ghcr.io -u pwittchen --password-stdin
fi
if [ "$1" == "--logs" ]; then
  docker logs -f varun.surf
fi
if [ "$1" == "--run" ]; then
  docker run --name varun.surf -d -p 20245:8080 ghcr.io/pwittchen/varun.surf
  echo "SUCCESS!"
  exit 0
fi
if [ "$1" == "--stop" ]; then
  docker stop varun.surf
  docker rm varun.surf
  echo "STOPPED"
  exit 0
fi
if [ "$1" == "--pull" ]; then
  docker pull ghcr.io/pwittchen/varun.surf
fi
if [ "$1" == "--restart" ]; then
  docker stop varun.surf
  docker rm varun.surf
  docker run --name varun.surf -d -p 20245:8080 ghcr.io/pwittchen/varun.surf
  echo "SUCCESS!"
  exit 0
fi
if [ "$1" == "--reload" ]; then
  docker stop varun.surf
  docker rm varun.surf
  docker pull ghcr.io/pwittchen/varun.surf
  docker run --name varun.surf -d -p 20245:8080 ghcr.io/pwittchen/varun.surf
  echo "SUCCESS!"
  exit 0
fi
if [ "$1" == "--ps" ]; then
  docker ps
fi
