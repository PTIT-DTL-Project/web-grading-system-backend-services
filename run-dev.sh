#!/usr/bin/env bash
SERVICE=$1
if [ -z "$SERVICE" ]; then
    echo "Usage: ./run-dev.sh <course-service|submission-service|executor-service|result-service|api-gateway|ai-assistant-service>"
    exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 1. Load root .env
if [ -f "$ROOT_DIR/.env" ]; then
    set -a
    source "$ROOT_DIR/.env"
    set +a
fi

# 2. Load service .env
if [ -f "$ROOT_DIR/$SERVICE/.env" ]; then
    set -a
    source "$ROOT_DIR/$SERVICE/.env"
    set +a
fi

echo "=========================================================="
echo " Loaded .env for [$SERVICE]"
echo " DB_USERNAME : $DB_USERNAME"
echo " DB_NAME     : $DB_NAME"
echo " JDBC_URL    : $JDBC_URL"
echo "=========================================================="

cd "$ROOT_DIR/$SERVICE" && mvn spring-boot:run
