#!/usr/bin/env bash
if [ "$1" == "--help" ] || [ -z "$1" ]; then
  echo ""
  echo "  varun.sh | script for managing varun.surf deployment"
  echo ""
  echo "  USAGE: varun.sh [options]"
  echo ""
  echo "  --help      shows help"
  echo "  --login     logs into ghcr.io"
  echo "  --logs      shows app logs"
  echo "  --run       runs the app"
  echo "  --stop      stops the app"
  echo "  --pull      pull the most recent app docker container"
  echo "  --restart   restarts currently running app"
  echo "  --reload    stops the app, pull the most recent container and starts the app"
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
fi
if [ "$1" == "--stop" ]; then
  docker stop varun.surf
  docker rm varun.surf
fi
if [ "$1" == "--pull" ]; then
  docker pull ghcr.io/pwittchen/varun.surf
fi
if [ "$1" == "--restart" ]; then
  docker stop varun.surf
  docker rm varun.surf
  docker run --name varun.surf -d -p 20245:8080 ghcr.io/pwittchen/varun.surf
fi
if [ "$1" == "--reload" ]; then
  docker stop varun.surf
  docker rm varun.surf
  docker pull ghcr.io/pwittchen/varun.surf
  docker run --name varun.surf -d -p 20245:8080 ghcr.io/pwittchen/varun.surf
fi
if [ "$1" == "--ps" ]; then
  docker ps
fi