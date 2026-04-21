#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Create .env from example if it doesn't exist
if [ ! -f .env ]; then
  cp env-example.txt .env
  echo "Created .env from env-example.txt — edit it to change settings."
fi

mkdir -p logs

echo "Building clob-system..."
mvn -q clean install -DskipTests -f ../clob-system/pom.xml

echo "Building clob-load-test..."
mvn -q clean package -DskipTests

echo "Running load test..."
java -jar target/clob-load-test-1.0-jar-with-dependencies.jar
