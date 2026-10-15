#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$project_dir/.local"
if [[ -f "$project_dir/.env" ]]; then set -a; source "$project_dir/.env"; set +a; fi
case "${1:-help}" in
  database) cd "$project_dir"; docker compose up -d mysql ;;
  backend) cd "$project_dir/backend"; exec mvn spring-boot:run ;;
  frontend) cd "$project_dir/frontend"; exec npm run dev ;;
  build) cd "$project_dir/frontend"; npm ci; npm run build; cd "$project_dir/backend"; mvn clean package ;;
  *) echo 'Usage: bash scripts/dev.sh {database|backend|frontend|build}' ;;
esac
