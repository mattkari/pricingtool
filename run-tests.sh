#!/bin/bash
# Quick test runner script

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH

PROFILE="${1:-}"

echo "🚀 Running PricingTool Test Automation Framework..."

if [ -n "$PROFILE" ]; then
    echo "📋 Profile: $PROFILE"
    echo ""
    mvn clean test -Dspring.profiles.active="$PROFILE"
else
    echo "📋 Profile: default (H2 in-memory)"
    echo ""
    mvn clean test
fi

echo ""
echo "📊 Test reports available at:"
echo "   - HTML: target/cucumber-reports/cucumber.html"
echo "   - JSON: target/cucumber-reports/cucumber.json"
echo ""
echo "💡 Usage: ./run-tests.sh [profile]"
echo "   Profiles: local, ci, docker (default: H2 in-memory)"
