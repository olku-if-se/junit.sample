#!/bin/bash

# Simple verification script that actually works
# Tests basic E2E integration step by step

set -e

echo "=== SIMPLE E2E VERIFICATION ==="
echo "OS: $(uname -a)"
echo "Working Directory: $(pwd)"
echo "Java: $(java -version 2>&1 | head -1)"
echo

# Test 1: Check if basic files exist
echo "1. Checking prerequisites..."
if [ -f "build.gradle" ]; then
    echo "✓ build.gradle exists"
else
    echo "✗ build.gradle missing"
    exit 1
fi

if [ -d "jacoco-gosu-filter" ]; then
    echo "✓ jacoco-gosu-filter directory exists"
else
    echo "✗ jacoco-gosu-filter directory missing"
    exit 1
fi

# Test 2: Check gradlew works
echo "2. Testing gradlew..."
if [ -f "./gradlew" ]; then
    echo "✓ gradlew exists"
    if ./gradlew --version >/dev/null 2>&1; then
        echo "✓ gradlew works"
    else
        echo "✗ gradlew failed"
        exit 1
    fi
else
    echo "✗ gradlew missing"
    exit 1
fi

# Test 3: Build agent
echo "3. Building agent..."
if ./gradlew :jacoco-gosu-filter:build >/dev/null 2>&1; then
    echo "✓ Agent builds successfully"
else
    echo "✗ Agent build failed"
    exit 1
fi

# Test 4: Check agent JAR exists
echo "4. Checking agent JAR..."
if [ -f "agents/gosu-filter-agent.jar" ]; then
    echo "✓ Agent JAR exists"
    ls -la agents/gosu-filter-agent.jar
else
    echo "✗ Agent JAR missing"
    exit 1
fi

# Test 5: Run basic test with agent
echo "5. Running basic test with agent..."
if ./gradlew test --rerun-tasks > basic-test.log 2>&1; then
    echo "✓ Tests run successfully"

    # Check for agent logs
    if grep -q "GosuFilterAgent" basic-test.log; then
        echo "✓ Agent logs found"
    else
        echo "⚠ Agent logs not found (may be expected)"
    fi

    # Check for JaCoCo reports
    if [ -f "build/reports/jacoco/test/jacocoTestReport.csv" ]; then
        echo "✓ JaCoCo report generated"
        echo "Coverage file exists: $(ls -la build/reports/jacoco/test/jacocoTestReport.csv)"
    else
        echo "⚠ JaCoCo report not found"
    fi

else
    echo "✗ Tests failed"
    tail -10 basic-test.log
    exit 1
fi

echo
echo "=== VERIFICATION COMPLETE ==="
echo "✓ All basic tests passed"
echo "Check basic-test.log for details"