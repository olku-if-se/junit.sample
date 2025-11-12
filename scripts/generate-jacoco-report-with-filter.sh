#!/bin/bash
#
# Generate JaCoCo Report with Gosu Filter (Fallback Method)
#
# This script generates the JaCoCo coverage report in a forked Gradle process
# with the Gosu filter agent pre-loaded. Use this as a fallback if you cannot
# modify gradle.properties to load the agent into the Gradle daemon.
#
# Usage: ./scripts/generate-jacoco-report-with-filter.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
AGENT_DIR="$PROJECT_DIR/agents"
AGENT_JAR="$AGENT_DIR/gosu-filter-agent.jar"
AGENT_VERSION="1.0.0"
AGENT_VERSIONED="$AGENT_DIR/gosu-filter-agent-$AGENT_VERSION.jar"

echo "================================================================================"
echo "JaCoCo Report Generation with Gosu Filter (Fallback Method)"
echo "================================================================================"
echo ""

# Check if agent exists
if [ ! -f "$AGENT_JAR" ] && [ ! -f "$AGENT_VERSIONED" ]; then
    echo "❌ Agent JAR not found!"
    echo ""
    echo "Building agent..."
    cd "$PROJECT_DIR"
    ./gradlew :jacoco-gosu-filter:build
    echo ""
fi

# Determine which agent JAR to use
if [ -f "$AGENT_VERSIONED" ]; then
    AGENT_TO_USE="$AGENT_VERSIONED"
    echo "✓ Using versioned agent: gosu-filter-agent-$AGENT_VERSION.jar"
elif [ -f "$AGENT_JAR" ]; then
    AGENT_TO_USE="$AGENT_JAR"
    echo "✓ Using latest agent: gosu-filter-agent.jar"
else
    echo "❌ Agent build failed!"
    exit 1
fi

echo ""
echo "Agent Path: $AGENT_TO_USE"
echo ""
echo "This will:"
echo "  1. Stop the current Gradle daemon"
echo "  2. Start a new Gradle daemon with the Gosu filter agent"
echo "  3. Generate the JaCoCo report with filtering applied"
echo "  4. Stop the daemon (to avoid affecting other builds)"
echo ""
echo "================================================================================"
echo ""

# Stop current daemon to ensure clean state
echo "Stopping current Gradle daemon..."
cd "$PROJECT_DIR"
./gradlew --stop

echo ""
echo "Starting Gradle with Gosu filter agent..."
echo ""

# Run Gradle with agent loaded via GRADLE_OPTS
GRADLE_OPTS="-javaagent:$AGENT_TO_USE" ./gradlew jacocoTestReport --no-daemon

echo ""
echo "================================================================================"
echo "✓ Report generated successfully with Gosu filter applied"
echo "================================================================================"
echo ""
echo "View report:"
echo "  ./gradlew viewReport"
echo ""
echo "Or open directly:"
echo "  build/reports/jacoco/test/html/index.html"
echo ""
echo "Note: This used --no-daemon to ensure the agent was loaded."
echo "For regular builds without the filter, just use: ./gradlew test"
echo ""
echo "================================================================================"
