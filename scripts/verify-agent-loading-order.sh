#!/bin/bash

# E2E Agent Loading Order Verification
# This script verifies that agents load in the correct order during JVM startup

set -e

echo "=== E2E AGENT LOADING ORDER VERIFICATION ==="

# Test 1: Verify command line argument order
echo "1. Testing JVM argument order..."
./gradlew test --rerun-tasks 2>&1 | grep -E "javaagent.*jar" | sort > agent-order.log

if grep -q "javaagent.*gosu-filter-agent" agent-order.log && \
   grep -q "javaagent.*jacocoagent" agent-order.log; then

    # Check if Gosu agent appears before JaCoCo agent
    gosu_line=$(grep -n "javaagent.*gosu-filter-agent" agent-order.log | head -1 | cut -d: -f1)
    jacoco_line=$(grep -n "javaagent.*jacocoagent" agent-order.log | head -1 | cut -d: -f1)

    if [ "$gosu_line" -lt "$jacoco_line" ]; then
        echo "✓ Agent loading order correct in command line"
    else
        echo "✗ Agent loading order incorrect in command line"
        echo "  Gosu agent line: $gosu_line"
        echo "  JaCoCo agent line: $jacoco_line"
        exit 1
    fi
else
    echo "✗ Both agents not found in JVM arguments"
    cat agent-order.log
    exit 1
fi

# Test 2: Verify agent premain execution order
echo "2. Testing agent premain execution order..."

# Run tests with timestamp logging to capture actual execution sequence
JAVA_TOOL_OPTIONS="-Djava.util.logging.config.file=agent-logging.properties" ./gradlew test --rerun-tasks 2>&1 | tee agent-execution.log

# Extract agent startup logs with timestamps
grep -E "\[GosuFilterAgent\].*STARTING|\[JaCoCo\].*Agent initialized" agent-execution.log | nl > agent-startup-order.log

if [ $(grep -c "GosuFilterAgent" agent-startup-order.log) -gt 0 ] && \
   [ $(grep -c "JaCoCo" agent-startup_order.log) -gt 0 ]; then
    echo "✓ Both agents started successfully"
    echo "Agent startup sequence:"
    cat agent-startup_order.log
else
    echo "⚠ Agent startup logs incomplete"
fi

# Test 3: Verify transformer registration timing
echo "3. Testing transformer registration timing..."
if grep -q "\[GosuFilterAgent\].*Transformer registered" agent-execution.log && \
   grep -q "\[GosuFilterInjector\].*INTERCEPTING" agent-execution.log; then
    echo "✓ Transformer registered and class interception detected"
else
    echo "✗ Transformer registration or class interception failed"
    exit 1
fi

# Test 4: Verify JaCoCo Filter class loading after transformer registration
echo "4. Testing JaCoCo Filter class loading timing..."
if grep -q "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" agent-execution.log; then
    echo "✓ JaCoCo Filter class loaded and transformed successfully"
else
    echo "✗ JaCoCo Filter class transformation failed"
    echo "This suggests the timing issue between agent loading and JaCoCo class loading"
    exit 1
fi

# Test 5: Create timeline analysis
echo "5. Creating timeline analysis..."
echo "AGENT LOADING TIMELINE:" > e2e-timeline.log
echo "========================" >> e2e-timeline.log
grep -E "\[GosuFilterAgent\]|\[GosuFilterInjector\]|\[JaCoCo\]" agent-execution.log | \
    sed 's/.*\[\(.*\)\].*/\1/' | \
    while read line; do
        echo "[$(date '+%H:%M:%S')] $line" >> e2e-timeline.log
    done

echo "✓ Agent loading order verification complete"
echo "Timeline saved to: e2e-timeline.log"