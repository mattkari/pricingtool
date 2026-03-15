#!/bin/bash
# Run tests in Docker using Playwright image

set -e

usage() {
    echo "Usage: $0 [--with-sqlserver]"
    echo ""
    echo "Options:"
    echo "  --with-sqlserver    Run tests with SQL Server (uses docker profile)"
    echo "  (default)           Run tests with H2 in-memory database"
}

echo "🐳 PricingTool Test Automation - Docker Runner"
echo ""

if [ "$1" == "--with-sqlserver" ]; then
    echo "📦 Starting SQL Server + Playwright test runner..."
    docker compose --profile sqlserver up --build test-runner-sqlserver --abort-on-container-exit
    EXIT_CODE=$?
    docker compose --profile sqlserver down
else
    echo "📦 Starting Playwright test runner (H2 database)..."
    docker compose up --build test-runner --abort-on-container-exit
    EXIT_CODE=$?
    docker compose down
fi

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ Tests completed successfully!"
else
    echo "❌ Tests failed with exit code: $EXIT_CODE"
fi
echo "📊 Test reports available in: ./target/cucumber-reports/"

exit $EXIT_CODE
