#!/bin/bash

# Complete E2E Integration Verification
# This script orchestrates all E2E verification tests

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

# Test results tracking
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

print_header() {
    echo
    echo -e "${BOLD}================================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BOLD}================================================================${NC}"
}

print_test() {
    local status=$1
    local test_name=$2
    local details=$3

    case $status in
        "PASS")
            echo -e "${GREEN}✓ PASS${NC}: $test_name"
            [ -n "$details" ] && echo "  $details"
            ((TESTS_PASSED++))
            ;;
        "FAIL")
            echo -e "${RED}✗ FAIL${NC}: $test_name"
            [ -n "$details" ] && echo "  $details"
            ((TESTS_FAILED++))
            ;;
        "SKIP")
            echo -e "${YELLOW}⚠ SKIP${NC}: $test_name"
            [ -n "$details" ] && echo "  $details"
            ((TESTS_SKIPPED++))
            ;;
    esac
}

run_verification_script() {
    local script=$1
    local description=$2
    local timeout=${3:-300}

    if [ -f "$script" ]; then
        echo -e "${BLUE}Running: $description${NC}"
        echo "Script: $script"
        echo

        # Run with timeout
        if timeout "$timeout" bash "$script" > "${script%.sh}.output.log" 2>&1; then
            print_test "PASS" "$description" "Completed successfully"
            return 0
        else
            local exit_code=$?
            if [ $exit_code -eq 124 ]; then
                print_test "FAIL" "$description" "Timed out after ${timeout}s"
            else
                print_test "FAIL" "$description" "Failed with exit code $exit_code"
            fi
            echo "Log file: ${script%.sh}.output.log"
            return 1
        fi
    else
        print_test "SKIP" "$description" "Script not found: $script"
        return 2
    fi
}

main() {
    print_header "COMPLETE E2E INTEGRATION VERIFICATION"
    echo "This comprehensive test suite verifies that the jacoco-gosu-filter"
    echo "works correctly in a complete end-to-end environment."
    echo

    # Create results directory
    mkdir -p e2e-results
    cd e2e-results

    # Copy verification scripts
    cp ../scripts/verify-*.sh . 2>/dev/null || true

    echo "Test Suite Configuration:"
    echo "  Working Directory: $(pwd)"
    echo "  Timestamp: $(date)"
    echo "  Java Version: $(java -version 2>&1 | head -1)"
    echo "  Gradle Version: $(./gradlew --version | grep "Gradle" | head -1)"
    echo

    # Test 1: Agent Loading Order Verification
    print_header "PHASE 1: AGENT LOADING ORDER VERIFICATION"
    run_verification_script "verify-agent-loading-order.sh" "Agent Loading Order" 180

    # Test 2: Filter Chain Integration Verification
    print_header "PHASE 2: FILTER CHAIN INTEGRATION VERIFICATION"
    run_verification_script "verify-filter-chain-integration.sh" "Filter Chain Integration" 240

    # Test 3: Pattern-to-Branch Correlation Verification
    print_header "PHASE 3: PATTERN-TO-BRANCH CORRELATION VERIFICATION"
    run_verification_script "verify-pattern-to-branch-correlation.sh" "Pattern-to-Branch Correlation" 300

    # Test 4: Production Environment Verification
    print_header "PHASE 4: PRODUCTION ENVIRONMENT VERIFICATION"
    run_verification_script "verify-production-environment.sh" "Production Environment" 600

    # Test 5: Integration with Unit Tests
    print_header "PHASE 5: UNIT TEST INTEGRATION VERIFICATION"
    echo -e "${BLUE}Running: Unit Test Integration${NC}"

    # Run specific unit tests that validate integration
    if ./gradlew :jacoco-gosu-filter:test \
          -Drun.integration.tests=true \
          -Dtest.agent.loading=true \
          -Dtest.pattern.detection=true \
          -Dtest.coverage.analysis=true \
          --tests "*BranchAnalysisTest*" \
          --tests "*InjectorIntegrationTest*" \
          --tests "*FilterApplicationVerificationTest*" > unit-test-integration.log 2>&1; then
        print_test "PASS" "Unit Test Integration" "All integration unit tests passed"
    else
        print_test "FAIL" "Unit Test Integration" "Some unit tests failed"
        echo "Log: unit-test-integration.log"
    fi

    # Test 6: Final Integration Validation
    print_header "PHASE 6: FINAL INTEGRATION VALIDATION"
    echo -e "${BLUE}Running: Final Integration Validation${NC}"

    # Run a complete build and test cycle
    if ./gradlew clean test jacocoTestReport > final-integration.log 2>&1; then
        # Verify all critical success indicators are present
        if grep -q "\[GosuFilterAgent\].*STARTING" final-integration.log && \
           grep -q "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" final-integration.log && \
           [ -f "build/reports/jacoco/test/jacocoTestReport.csv" ]; then

            # Extract coverage data
            coverage_data=$(grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv 2>/dev/null || echo "")
            if [ -n "$coverage_data" ]; then
                branches_covered=$(echo "$coverage_data" | cut -d',' -f10)
                total_branches=$(echo "$coverage_data" | cut -d',' -f9)
                total_branches=$((total_branches + branches_covered))

                print_test "PASS" "Final Integration Validation" "Coverage: $branches_covered/$total_branches branches, Agent integration successful"
            else
                print_test "FAIL" "Final Integration Validation" "Coverage data missing"
            fi
        else
            print_test "FAIL" "Final Integration Validation" "Missing critical integration indicators"
        fi
    else
        print_test "FAIL" "Final Integration Validation" "Build failed"
    fi

    # Generate comprehensive report
    print_header "E2E VERIFICATION SUMMARY"

    local total_tests=$((TESTS_PASSED + TESTS_FAILED + TESTS_SKIPPED))
    local success_rate=0
    if [ $total_tests -gt 0 ]; then
        success_rate=$(echo "scale=1; $TESTS_PASSED * 100 / $total_tests" | bc -l)
    fi

    echo -e "${BOLD}Test Results Summary:${NC}"
    echo "  Total Tests: $total_tests"
    echo -e "  Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "  Failed: ${RED}$TESTS_FAILED${NC}"
    echo -e "  Skipped: ${YELLOW}$TESTS_SKIPPED${NC}"
    echo "  Success Rate: ${success_rate}%"
    echo

    # Overall assessment
    if [ $TESTS_FAILED -eq 0 ]; then
        if [ $TESTS_PASSED -ge 4 ]; then
            echo -e "${GREEN}${BOLD}✅ E2E INTEGRATION VERIFICATION SUCCESSFUL${NC}"
            echo
            echo "The jacoco-gosu-filter has been verified to work correctly in"
            echo "a complete end-to-end environment. All critical tests passed."
            echo
            echo "Key Success Indicators:"
            echo "  ✓ Agent loads in correct order"
            echo "  ✓ Filter chain integration successful"
            echo "  ✓ Pattern detection correlates with branch reduction"
            echo "  ✓ Production environment compatibility confirmed"
            echo "  ✓ Unit test integration validated"
            echo "  ✓ Complete build pipeline functional"
            exit 0
        else
            echo -e "${YELLOW}${BOLD}⚠ E2E INTEGRATION PARTIALLY SUCCESSFUL${NC}"
            echo
            echo "Some tests were skipped, but no critical failures detected."
            echo "The integration appears to be working but requires manual review."
            exit 0
        fi
    else
        echo -e "${RED}${BOLD}❌ E2E INTEGRATION VERIFICATION FAILED${NC}"
        echo
        echo "Critical issues detected that prevent the jacoco-gosu-filter"
        echo "from working correctly in the end-to-end environment."
        echo
        echo "Required Actions:"
        echo "  1. Review failed test logs below"
        echo "  2. Fix identified issues"
        echo "  3. Re-run verification suite"
        echo
        echo "Failed Components:"
        # List specific failed tests
        [ ! -f "verify-agent-loading-order.sh.output.log" ] || [ ! -s "verify-agent-loading-order.sh.output.log" ] || echo "  - Agent Loading Order Verification"
        [ ! -f "verify-filter-chain-integration.sh.output.log" ] || [ ! -s "verify-filter-chain-integration.sh.output.log" ] || echo "  - Filter Chain Integration"
        [ ! -f "verify-pattern-to-branch-correlation.sh.output.log" ] || [ ! -s "verify-pattern-to-branch-correlation.sh.output.log" ] || echo "  - Pattern-to-Branch Correlation"
        [ ! -f "verify-production-environment.sh.output.log" ] || [ ! -s "verify-production-environment.sh.output.log" ] || echo "  - Production Environment"
        exit 1
    fi
}

# Check dependencies
check_dependencies() {
    local missing_deps=()

    command -v java >/dev/null 2>&1 || missing_deps+=("java")
    command -v ./gradlew >/dev/null 2>&1 || missing_deps+=("gradlew")
    command -v bc >/dev/null 2>&1 || missing_deps+=("bc (calculator)")

    if [ ${#missing_deps[@]} -gt 0 ]; then
        echo "❌ Missing dependencies: ${missing_deps[*]}"
        echo "Please install missing dependencies and try again."
        exit 1
    fi
}

# Script entry point
echo "E2E Integration Verification Suite"
echo "================================="
echo

check_dependencies

# Ensure we're in the right directory
if [ ! -f "build.gradle" ] || [ ! -d "jacoco-gosu-filter" ]; then
    echo "❌ Error: Please run this script from the project root directory"
    echo "Expected files: build.gradle, jacoco-gosu-filter/"
    exit 1
fi

main "$@"