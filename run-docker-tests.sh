#!/bin/bash
# Run tests in Docker using Playwright image

set -e

echo "🐳 PricingTool Test Automation - Docker Runner"
echo ""
echo "📦 Starting Playwright test runner (H2 database)..."
docker compose up --build test-runner --abort-on-container-exit
EXIT_CODE=$?
docker compose down

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ Tests completed successfully!"
else
    echo "❌ Tests failed with exit code: $EXIT_CODE"
fi
echo "📊 Test reports available in: ./target/cucumber-reports/"

exit $EXIT_CODE
