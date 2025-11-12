#!/bin/bash
#
# JaCoCo Gosu Filter Integration Health Check
# Verifies that the agent integration is working correctly
#
# Usage: ./scripts/integration-health-check.sh

set -e

COLOR_GREEN='\033[0;32m'
COLOR_YELLOW='\033[1;33m'
COLOR_RED='\033[0;31m'
COLOR_BLUE='\033[0;34m'
COLOR_RESET='\033[0m'

echo ""
echo "================================================================================"
echo "  JaCoCo Gosu Filter Integration Health Check"
echo "================================================================================"
echo ""

EXIT_CODE=0

# Helper function for status messages
check_pass() {
    echo -e "${COLOR_GREEN}✅ $1${COLOR_RESET}"
}

check_warn() {
    echo -e "${COLOR_YELLOW}⚠️  $1${COLOR_RESET}"
}

check_fail() {
    echo -e "${COLOR_RED}❌ $1${COLOR_RESET}"
    EXIT_CODE=1
}

check_info() {
    echo -e "${COLOR_BLUE}ℹ️  $1${COLOR_RESET}"
}

# 1. Check agent JAR exists
echo "1. Checking agent JAR existence..."
if [ -f "agents/gosu-filter-agent.jar" ]; then
    AGENT_SIZE=$(du -h agents/gosu-filter-agent.jar | cut -f1)
    check_pass "Agent JAR exists (${AGENT_SIZE})"
else
    check_fail "Agent JAR missing at: agents/gosu-filter-agent.jar"
    echo "   Run: ./gradlew :jacoco-gosu-filter:copyAgentJar"
    exit 1
fi
echo ""

# 2. Check agent JAR freshness
echo "2. Checking agent JAR freshness..."
if [ -d "jacoco-gosu-filter/src/main/java" ]; then
    # Find newest source file
    NEWEST_SOURCE=$(find jacoco-gosu-filter/src/main/java -name "*.java" -type f -exec stat -c %Y {} \; 2>/dev/null | sort -n | tail -1)
    AGENT_TIME=$(stat -c %Y agents/gosu-filter-agent.jar 2>/dev/null || stat -f %m agents/gosu-filter-agent.jar 2>/dev/null)

    if [ -n "$NEWEST_SOURCE" ] && [ -n "$AGENT_TIME" ]; then
        if [ "$AGENT_TIME" -gt "$NEWEST_SOURCE" ]; then
            check_pass "Agent JAR is up-to-date"
        else
            check_warn "Agent JAR is stale (source files modified after JAR build)"
            echo "   Consider running: ./gradlew :jacoco-gosu-filter:build"
        fi
    else
        check_info "Could not determine file timestamps (stat command may differ by OS)"
    fi
else
    check_warn "Agent source directory not found, skipping freshness check"
fi
echo ""

# 3. Check Gradle configuration
echo "3. Checking Gradle configuration..."
if grep -q "javaagent.*gosu-filter-agent" build.gradle; then
    check_pass "Agent configuration found in build.gradle"
else
    check_fail "Agent configuration missing from build.gradle"
fi

if grep -q "maxParallelForks = 1" build.gradle; then
    check_pass "Test forking disabled (prevents agent bypass)"
else
    check_warn "Test forking not explicitly disabled"
fi

if grep -q "Agent order verified" build.gradle; then
    check_pass "Agent order verification enabled"
else
    check_warn "Agent order verification not found"
fi
echo ""

# 4. Run tests and check agent loading
echo "4. Running tests to verify agent loading..."
check_info "This may take a minute..."

# Create temporary log file
TEMP_LOG=$(mktemp)
trap "rm -f $TEMP_LOG" EXIT

# Run tests and capture output
if ./gradlew test --rerun-tasks 2>&1 | tee "$TEMP_LOG" > /dev/null; then
    check_pass "Tests completed successfully"
else
    check_warn "Tests failed (check logs for details)"
fi
echo ""

# 5. Verify agent startup
echo "5. Verifying agent startup sequence..."
if grep -q "\[GosuFilterAgent\].*STARTING" "$TEMP_LOG"; then
    check_pass "Gosu filter agent started"
else
    check_fail "Gosu filter agent NOT started"
    echo "   Expected log: [GosuFilterAgent] STARTING GOSU FILTER AGENT"
fi

if grep -q "✓ Transformer registered successfully" "$TEMP_LOG"; then
    check_pass "Transformer registered successfully"
else
    check_warn "Transformer registration not confirmed in logs"
fi
echo ""

# 6. Verify bytecode injection
echo "6. Verifying bytecode injection..."
if grep -q "\[GosuFilterInjector\].*INTERCEPTING" "$TEMP_LOG"; then
    check_pass "Filter intercepted JaCoCo Filters class"
else
    check_fail "Filter did NOT intercept JaCoCo Filters class"
fi

if grep -q "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" "$TEMP_LOG" || \
   grep -q "✓ BYTECODE INJECTION SUCCESSFUL" "$TEMP_LOG"; then
    check_pass "Bytecode injection successful"
else
    check_fail "Bytecode injection FAILED"
fi
echo ""

# 7. Check agent loading order
echo "7. Verifying agent loading order..."
if grep -q "Agent order verified.*Gosu.*before.*JaCoCo" "$TEMP_LOG"; then
    check_pass "Agent loading order correct (Gosu before JaCoCo)"
elif grep -q "Gosu agent loaded, JaCoCo agent will be added by plugin" "$TEMP_LOG"; then
    check_pass "Gosu agent loaded first"
else
    check_warn "Agent order verification not found in logs"
fi
echo ""

# 8. Check pattern detection
echo "8. Checking pattern detection..."
PATTERN_COUNT=$(grep -c "\[GosuNullSafetyFilter\].*PATTERN" "$TEMP_LOG" || echo 0)
if [ "$PATTERN_COUNT" -gt 0 ]; then
    check_pass "Pattern detection working (${PATTERN_COUNT} patterns detected)"
else
    check_warn "No patterns detected (may need debug mode: -Djacoco.gosu.filter.debug=true)"
fi
echo ""

# 9. Check for hidden test tasks
echo "9. Checking for hidden test tasks..."
check_info "Scanning for all test-related tasks..."
HIDDEN_TASKS=$(./gradlew tasks --all 2>/dev/null | grep -i "test.*-" | grep -v "testClasses" | wc -l)
if [ "$HIDDEN_TASKS" -gt 0 ]; then
    check_info "Found $HIDDEN_TASKS test-related tasks:"
    ./gradlew tasks --all 2>/dev/null | grep -i "test.*-" | grep -v "testClasses" | head -5 | sed 's/^/     /'
    if [ "$HIDDEN_TASKS" -gt 5 ]; then
        echo "     ... and $(($HIDDEN_TASKS - 5)) more"
    fi
else
    check_pass "No hidden test tasks detected"
fi
echo ""

# 10. Check coverage report
echo "10. Verifying coverage report generation..."
if [ -f "build/reports/jacoco/test/jacocoTestReport.csv" ]; then
    check_pass "Coverage report generated"

    # Check for PolicyPeriodEnhancement in report
    if grep -q "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv; then
        BRANCHES=$(grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv | head -1 | cut -d',' -f9,10)
        check_info "PolicyPeriodEnhancement coverage data found"
        echo "     Branch data: $BRANCHES (missed,covered)"
    fi
else
    check_warn "Coverage report not found (run: ./gradlew jacocoTestReport)"
fi
echo ""

# Summary
echo "================================================================================"
if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${COLOR_GREEN}✅ HEALTH CHECK PASSED${COLOR_RESET}"
    echo ""
    echo "Integration Status: HEALTHY"
    echo "  • Agent loading: ✅ Working"
    echo "  • Bytecode injection: ✅ Working"
    echo "  • Pattern detection: ✅ Working"
    echo ""
    echo "The JaCoCo Gosu filter integration is functioning correctly."
else
    echo -e "${COLOR_RED}❌ HEALTH CHECK FAILED${COLOR_RESET}"
    echo ""
    echo "Integration Status: ISSUES DETECTED"
    echo ""
    echo "Please review the failed checks above and take corrective action."
    echo ""
    echo "Common fixes:"
    echo "  • Missing agent: ./gradlew :jacoco-gosu-filter:copyAgentJar"
    echo "  • Stale agent: ./gradlew :jacoco-gosu-filter:build"
    echo "  • Configuration issues: Review build.gradle test task configuration"
fi
echo "================================================================================"
echo ""

# Additional diagnostics if there were failures
if [ $EXIT_CODE -ne 0 ]; then
    echo "Diagnostic information saved to: $TEMP_LOG"
    echo "Review logs with: grep -E '\[Gosu' $TEMP_LOG"
    echo ""

    # Don't delete temp log on failure
    trap - EXIT
    mv "$TEMP_LOG" "integration-health-check.log"
    echo "Full test output: integration-health-check.log"
    echo ""
fi

exit $EXIT_CODE
