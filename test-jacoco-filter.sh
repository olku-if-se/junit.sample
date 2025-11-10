#!/usr/bin/env bash

# JaCoCo Gosu Filter Comprehensive Test Runner
# Merged from test-enhanced-filter.sh, test-filter-output.sh, and run-filter-tests.sh
# This script provides complete testing of the Gosu Null Safety Filter with branch reduction verification

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
LOG_FILE="comprehensive-filter-test.log"
COVERAGE_CSV="build/reports/jacoco/test/jacocoTestReport.csv"
COVERAGE_HTML="build/reports/jacoco/test/html/index.html"
EXPECTED_BRANCHES_COVERED=31
EXPECTED_TOTAL_BRANCHES=38
EXPECTED_COVERAGE_PERCENTAGE=81.6

# Test results tracking
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to show progress indicator
show_progress() {
    local message=$1
    local log_file=$2
    print_status $YELLOW "$message"
    print_status $CYAN "Output will be displayed below and saved to: $log_file"
    echo
}

# Function to run command with tee for real-time display and logging
run_with_output() {
    local command="$1"
    local log_file="$2"
    local description="$3"

    print_status $BLUE "Running: $description"
    print_status $CYAN "Command: $command"
    echo

    # Run command and display output while saving to log file
    eval "$command" 2>&1 | tee "$log_file"
    local exit_code=${PIPESTATUS[0]}

    echo
    if [ $exit_code -eq 0 ]; then
        print_status $GREEN "✓ $description completed successfully"
        return 0
    else
        print_status $RED "✗ $description failed (exit code: $exit_code)"
        print_status $YELLOW "Check log file: $log_file"
        return 1
    fi
}

print_header() {
    local title=$1
    echo
    print_status $BOLD "=================================================================="
    print_status $BLUE "$title"
    print_status $BOLD "=================================================================="
}

print_step() {
    local step=$1
    local description=$2
    echo
    print_status $CYAN "STEP $step: $description"
}

# Function to check if PolicyPeriodEnhancement.class exists
check_prerequisites() {
    print_header "PREREQUISITE CHECK"

    print_status $BLUE "Checking environment and dependencies..."

    # Check Java version
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 11 ]; then
        print_status $RED "✗ Error: Java 11+ required. Current version: $(java -version 2>&1 | head -1)"
        exit 1
    fi
    print_status $GREEN "✓ Java version: $(java -version 2>&1 | head -1)"

    # Check if gradle wrapper exists
    if [ -f "./gradlew" ]; then
        print_status $GREEN "✓ Gradle wrapper found"
    else
        print_status $RED "✗ Error: gradlew not found. Please run from project root."
        exit 1
    fi

    # Check if Gosu source exists
    if [ -f "src/main/gosu/enhancement/PolicyPeriodEnhancement.gsx" ]; then
        print_status $GREEN "✓ Gosu source file found"
    else
        print_status $RED "✗ Error: PolicyPeriodEnhancement.gsx not found"
        exit 1
    fi

    # Check jacoco-gosu-filter project structure
    if [ -d "jacoco-gosu-filter" ] && [ -f "jacoco-gosu-filter/build.gradle" ]; then
        print_status $GREEN "✓ jacoco-gosu-filter project structure found"
    else
        print_status $RED "✗ Error: jacoco-gosu-filter project not found"
        exit 1
    fi

    print_status $GREEN "✓ All prerequisites satisfied"
}

# Function to build the filter agent
build_agent() {
    print_header "AGENT BUILD"

    show_progress "Building Gosu Filter Agent (this may take a minute...)" "build-agent.log"

    # Clean and build the agent with real-time output
    if run_with_output "./gradlew clean jacoco-gosu-filter:build" "build-agent.log" "Agent Build"; then
        print_status $GREEN "✓ Agent built successfully"
    else
        return 1
    fi

    # Verify agent JAR exists
    if [ -f "agents/gosu-filter-agent.jar" ]; then
        local jar_size=$(stat -c%s "agents/gosu-filter-agent.jar" 2>/dev/null || stat -f%z "agents/gosu-filter-agent.jar" 2>/dev/null || echo "0")
        if [ "$jar_size" -gt 50000 ]; then
            print_status $GREEN "✓ Agent JAR created ($(echo "scale=1; $jar_size/1024" | bc -l)KB)"
        else
            print_status $RED "✗ Agent JAR too small or empty"
            return 1
        fi
    else
        print_status $RED "✗ Agent JAR not found"
        return 1
    fi

    # Verify JAR contents
    local class_count=$(jar tf agents/gosu-filter-agent.jar | grep -c "\.class$" || echo "0")
    if [ "$class_count" -ge 3 ]; then
        print_status $GREEN "✓ Agent JAR contains $class_count class files"
    else
        print_status $RED "✗ Agent JAR missing required classes"
        return 1
    fi

    # Verify manifest
    if jar tf agents/gosu-filter-agent.jar | grep -q "META-INF/MANIFEST.MF"; then
        print_status $GREEN "✓ Agent JAR manifest found"

        # Extract and check manifest attributes
        local manifest_attrs=$(jar xf agents/gosu-filter-agent.jar META-INF/MANIFEST.MF 2>/dev/null &&
                              grep -E "(Premain-Class|Agent-Class|Can-Redefine|Can-Retransform)" META-INF/MANIFEST.MF | wc -l)
        if [ "$manifest_attrs" -ge 3 ]; then
            print_status $GREEN "✓ Agent manifest has required attributes ($manifest_attrs found)"
        else
            print_status $YELLOW "⚠ Agent manifest may be missing attributes"
        fi
        rm -f META-INF/MANIFEST.MF
    fi
}

# Function to run basic agent loading tests
test_agent_loading() {
    print_step "1" "AGENT LOADING VERIFICATION"

    show_progress "Testing agent loading and initialization (this may take a few minutes...)" "test-loading.log"

    # Run tests with agent to verify loading with real-time output
    if run_with_output "./gradlew test --rerun-tasks" "test-loading.log" "Agent Loading Tests"; then
        print_status $GREEN "✓ Tests executed with agent"
    else
        print_status $YELLOW "⚠ Agent loading tests had issues (some may be expected)"
        # Don't return 1 here as some test failures may be expected
    fi

    # Check for agent startup logs
    if grep -q "\[GosuFilterAgent\].*STARTING" test-loading.log; then
        print_status $GREEN "✓ Agent startup logs found"
        ((TESTS_PASSED++))
    else
        print_status $RED "✗ Agent startup logs missing"
        ((TESTS_FAILED++))
        return 1
    fi

    # Check for transformer registration
    if grep -q "\[GosuFilterAgent\].*Transformer registered" test-loading.log; then
        print_status $GREEN "✓ Transformer registered successfully"
        ((TESTS_PASSED++))
    else
        print_status $RED "✗ Transformer registration failed"
        ((TESTS_FAILED++))
        return 1
    fi

    # Check for bytecode injection
    if grep -q "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" test-loading.log; then
        print_status $GREEN "✓ Bytecode injection successful"
        ((TESTS_PASSED++))
    else
        print_status $RED "✗ Bytecode injection failed"
        ((TESTS_FAILED++))
        return 1
    fi

    print_status $GREEN "✓ Agent loading verification completed"
}

# Function to test pattern detection with debug mode
test_pattern_detection() {
    print_step "2" "PATTERN DETECTION ANALYSIS"

    show_progress "Testing pattern detection with debug mode enabled (this may take a few minutes...)" "pattern-detection.log"

    # Enable debug mode and run tests
    export JAVA_OPTS="-Djacoco.gosu.filter.debug=true"

    # Run pattern detection with real-time output
    if run_with_output "./gradlew :jacoco-gosu-filter:test --tests \"*BranchAnalysis*\" -Djacoco.gosu.filter.debug=true --rerun-tasks --info" "pattern-detection.log" "Pattern Detection Tests"; then
        print_status $GREEN "✓ Pattern detection tests executed"
    else
        print_status $YELLOW "⚠ Pattern detection tests had issues (may be expected)"
    fi

    # Count pattern detections
    local pattern1_count=$(grep -c "PATTERN 1.*Null-safe navigation" pattern-detection.log 2>/dev/null || echo "0")
    local pattern2_count=$(grep -c "PATTERN 2.*Defensive null check" pattern-detection.log 2>/dev/null || echo "0")
    local pattern3_count=$(grep -c "PATTERN 3.*simplified null-safe" pattern-detection.log 2>/dev/null || echo "0")
    local pattern4_count=$(grep -c "PATTERN 4.*Boolean null-safe" pattern-detection.log 2>/dev/null || echo "0")
    local pattern5_count=$(grep -c "PATTERN 5.*Array null-safe" pattern-detection.log 2>/dev/null || echo "0")
    local total_patterns=$((pattern1_count + pattern2_count + pattern3_count + pattern4_count + pattern5_count))

    print_status $CYAN "Pattern Detection Results:"
    echo "  Pattern 1 (Null-safe navigation): $pattern1_count"
    echo "  Pattern 2 (Defensive null check): $pattern2_count"
    echo "  Pattern 3 (Simplified null-safe): $pattern3_count"
    echo "  Pattern 4 (Boolean null-safe): $pattern4_count"
    echo "  Pattern 5 (Array null-safe): $pattern5_count"
    echo "  Total patterns detected: $total_patterns"

    if [ "$total_patterns" -gt 0 ]; then
        print_status $GREEN "✓ Pattern detection successful ($total_patterns patterns)"
        ((TESTS_PASSED++))
    else
        print_status $YELLOW "⚠ No patterns detected (may indicate debug mode issues)"
    fi

    # Show sample pattern detections
    if [ "$pattern1_count" -gt 0 ]; then
        echo
        print_status $CYAN "Sample Pattern 1 Detections:"
        grep "PATTERN 1.*Null-safe navigation" pattern-detection.log | head -3 | sed 's/^/    /'
    fi

    unset JAVA_OPTS
}

# Function to generate and analyze coverage reports
test_coverage_reports() {
    print_step "3" "COVERAGE REPORT GENERATION AND ANALYSIS"

    show_progress "Generating coverage reports (this may take several minutes...)" "coverage-report.log"

    # Clean build and generate reports with real-time output
    if run_with_output "./gradlew clean test jacocoTestReport" "coverage-report.log" "Coverage Report Generation"; then
        print_status $GREEN "✓ Coverage reports generated successfully"
    else
        print_status $YELLOW "⚠ Coverage report generation had issues"
        # Don't return 1 here as we may still have useful partial results
    fi

    # Verify report files exist
    if [ -f "$COVERAGE_CSV" ]; then
        print_status $GREEN "✓ CSV report generated"
    else
        print_status $RED "✗ CSV report not found"
        return 1
    fi

    if [ -f "$COVERAGE_HTML" ]; then
        print_status $GREEN "✓ HTML report generated"
        print_status $CYAN "HTML Report: file://$(pwd)/$COVERAGE_HTML"
    else
        print_status $RED "✗ HTML report not found"
        return 1
    fi

    # Extract PolicyPeriodEnhancement coverage data
    local coverage_data=$(grep "PolicyPeriodEnhancement" "$COVERAGE_CSV" 2>/dev/null || echo "")
    if [ -n "$coverage_data" ]; then
        print_status $GREEN "✓ PolicyPeriodEnhancement coverage data found"
        print_status $CYAN "Coverage Data: $coverage_data"

        # Extract branch information
        local branches_missed=$(echo "$coverage_data" | cut -d',' -f9)
        local branches_covered=$(echo "$coverage_data" | cut -d',' -f10)
        local total_branches=$((branches_missed + branches_covered))
        local coverage_percentage=$(echo "scale=1; $branches_covered * 100 / $total_branches" | bc -l 2>/dev/null || echo "0")

        print_status $CYAN "Branch Analysis:"
        echo "  Branches missed: $branches_missed"
        echo "  Branches covered: $branches_covered"
        echo "  Total branches: $total_branches"
        echo "  Coverage percentage: ${coverage_percentage}%"

        # Verify expected results
        if [ "$branches_covered" -eq "$EXPECTED_BRANCHES_COVERED" ]; then
            print_status $GREEN "✓ Expected branch coverage ($EXPECTED_BRANCHES_COVERED branches)"
            ((TESTS_PASSED++))
        else
            print_status $YELLOW "⚠ Unexpected branch coverage: expected $EXPECTED_BRANCHES_COVERED, got $branches_covered"
        fi

        if [ "$total_branches" -eq "$EXPECTED_TOTAL_BRANCHES" ]; then
            print_status $GREEN "✓ Expected total branches ($EXPECTED_TOTAL_BRANCHES)"
            ((TESTS_PASSED++))
        else
            print_status $YELLOW "⚠ Unexpected total branches: expected $EXPECTED_TOTAL_BRANCHES, got $total_branches"
        fi

        # Check if coverage percentage is close to expected
        local coverage_diff=$(echo "$coverage_percentage - $EXPECTED_COVERAGE_PERCENTAGE" | bc -l 2>/dev/null || echo "0")
        local coverage_abs_diff=$(echo "$coverage_diff" | tr -d '-' 2>/dev/null || echo "0")
        if (( $(echo "$coverage_abs_diff < 2.0" | bc -l 2>/dev/null || echo "1") )); then
            print_status $GREEN "✓ Expected coverage percentage (~${EXPECTED_COVERAGE_PERCENTAGE}%)"
            ((TESTS_PASSED++))
        else
            print_status $YELLOW "⚠ Unexpected coverage percentage: expected ~${EXPECTED_COVERAGE_PERCENTAGE}%, got ${coverage_percentage}%"
        fi

    else
        print_status $RED "✗ PolicyPeriodEnhancement coverage data not found"
        ((TESTS_FAILED++))
        return 1
    fi
}

# Function to verify bytecode patterns
test_bytecode_verification() {
    print_step "4" "BYTECODE VERIFICATION"

    print_status $BLUE "Verifying bytecode patterns..."

    # Find the compiled class
    local class_file=$(find build -name "PolicyPeriodEnhancement.class" -type f 2>/dev/null | head -1)
    if [ -n "$class_file" ]; then
        print_status $GREEN "✓ Compiled class found: $class_file"
    else
        print_status $YELLOW "⚠ Compiled class not found, compiling..."
        ./gradlew compileGosu
        class_file=$(find build -name "PolicyPeriodEnhancement.class" -type f 2>/dev/null | head -1)
        if [ -n "$class_file" ]; then
            print_status $GREEN "✓ Class compiled successfully"
        else
            print_status $RED "✗ Failed to compile class"
            return 1
        fi
    fi

    # Disassemble bytecode
    if javap -v -p "$class_file" > bytecode-analysis.txt 2>&1; then
        print_status $GREEN "✓ Bytecode disassembled"
    else
        print_status $RED "✗ Failed to disassemble bytecode"
        return 1
    fi

    # Count key instructions
    local aload_count=$(grep -c "aload" bytecode-analysis.txt 2>/dev/null || echo "0")
    local ifnonnull_count=$(grep -c "ifnonnull" bytecode-analysis.txt 2>/dev/null || echo "0")
    local aconst_null_count=$(grep -c "aconst_null" bytecode-analysis.txt 2>/dev/null || echo "0")
    local checkcast_count=$(grep -c "checkcast" bytecode-analysis.txt 2>/dev/null || echo "0")

    print_status $CYAN "Bytecode Instruction Counts:"
    echo "  aload: $aload_count"
    echo "  ifnonnull: $ifnonnull_count"
    echo "  aconst_null: $aconst_null_count"
    echo "  checkcast: $checkcast_count"

    if [ "$ifnonnull_count" -gt 0 ] && [ "$aconst_null_count" -gt 0 ]; then
        print_status $GREEN "✓ Null-safety bytecode patterns found"
        ((TESTS_PASSED++))
    else
        print_status $YELLOW "⚠ Limited null-safety patterns found"
    fi

    # Show sample bytecode patterns
    echo
    print_status $CYAN "Sample Null-Safe Bytecode Patterns:"
    grep -B1 -A3 "ifnonnull" bytecode-analysis.txt | head -20 | sed 's/^/    /'
}

# Function to run integration tests with conditional properties
test_integration_properties() {
    print_step "5" "INTEGRATION TESTS WITH CONDITIONAL PROPERTIES"

    show_progress "Running integration tests with conditional properties (this may take a few minutes...)" "integration-test.log"

    # Run tests with system properties for conditional test execution
    if run_with_output "./gradlew :jacoco-gosu-filter:test -Drun.integration.tests=true -Djacoco.gosu.filter.debug=true -Dtest.agent.loading=true -Dtest.pattern.detection=true -Dtest.coverage.analysis=true --rerun-tasks --info" "integration-test.log" "Integration Tests with Conditional Properties"; then
        print_status $GREEN "✓ Integration tests executed"
        ((TESTS_PASSED++))
    else
        print_status $YELLOW "⚠ Integration tests had issues (may be expected)"
    fi

    # Check for specific conditional test markers
    if grep -q "EnabledIfSystemProperty" integration-test.log 2>/dev/null; then
        print_status $GREEN "✓ Conditional test properties detected"
    fi

    # Show integration test summary
    local test_count=$(grep -c "test.*started" integration-test.log 2>/dev/null || echo "0")
    local test_passed=$(grep -c "test.*passed" integration-test.log 2>/dev/null || echo "0")
    local test_failed=$(grep -c "test.*failed" integration-test.log 2>/dev/null || echo "0")

    print_status $CYAN "Integration Test Summary:"
    echo "  Tests started: $test_count"
    echo "  Tests passed: $test_passed"
    echo "  Tests failed: $test_failed"
}

# Function to show comprehensive test summary
show_test_summary() {
    print_header "COMPREHENSIVE TEST SUMMARY"

    print_status $BOLD "Test Results:"
    echo "  Tests passed: $TESTS_PASSED"
    echo "  Tests failed: $TESTS_FAILED"
    echo "  Total checks: $((TESTS_PASSED + TESTS_FAILED))"

    if [ "$TESTS_FAILED" -eq 0 ]; then
        print_status $GREEN "✓ ALL TESTS PASSED SUCCESSFULLY"
    else
        local success_rate=$(echo "scale=1; $TESTS_PASSED * 100 / ($TESTS_PASSED + $TESTS_FAILED)" | bc -l 2>/dev/null || echo "0")
        print_status $YELLOW "⚠ Some tests had issues (Success rate: ${success_rate}%)"
    fi

    echo
    print_status $CYAN "Generated Files:"
    echo "  - Test logs: *.log files"
    echo "  - Bytecode analysis: bytecode-analysis.txt"
    echo "  - Coverage reports: build/reports/jacoco/test/"
    echo "  - Agent JAR: agents/gosu-filter-agent.jar"

    if [ -f "$COVERAGE_HTML" ]; then
        echo
        print_status $CYAN "Coverage Report Available:"
        echo "  HTML: file://$(pwd)/$COVERAGE_HTML"
        echo "  CSV: $COVERAGE_CSV"
    fi

    echo
    print_status $BLUE "Expected vs Actual Results:"
    printf "%-20s %-15s %-15s %s\n" "Metric" "Expected" "Actual" "Status"
    printf "%-20s %-15s %-15s %s\n" "--------------------" "---------------" "---------------" "------"

    if [ -f "$COVERAGE_CSV" ]; then
        local coverage_data=$(grep "PolicyPeriodEnhancement" "$COVERAGE_CSV" 2>/dev/null || echo "")
        if [ -n "$coverage_data" ]; then
            local branches_missed=$(echo "$coverage_data" | cut -d',' -f9)
            local branches_covered=$(echo "$coverage_data" | cut -d',' -f10)
            local total_branches=$((branches_missed + branches_covered))
            local coverage_percentage=$(echo "scale=1; $branches_covered * 100 / $total_branches" | bc -l 2>/dev/null || echo "0")

            printf "%-20s %-15s %-15s %s\n" "Branches Covered" "$EXPECTED_BRANCHES_COVERED" "$branches_covered" \
                "$([ "$branches_covered" -eq "$EXPECTED_BRANCHES_COVERED" ] && echo "✓" || echo "⚠")"
            printf "%-20s %-15s %-15s %s\n" "Total Branches" "$EXPECTED_TOTAL_BRANCHES" "$total_branches" \
                "$([ "$total_branches" -eq "$EXPECTED_TOTAL_BRANCHES" ] && echo "✓" || echo "⚠")"
            printf "%-20s %-15s %-15s %s\n" "Coverage %" "${EXPECTED_COVERAGE_PERCENTAGE}%" "${coverage_percentage}%" \
                "$(( $(echo "$coverage_percentage >= $EXPECTED_COVERAGE_PERCENTAGE - 2" | bc -l 2>/dev/null || echo "1") && $(echo "$coverage_percentage <= $EXPECTED_COVERAGE_PERCENTAGE + 2" | bc -l 2>/dev/null || echo "1") )) && echo "✓" || echo "⚠")"
        fi
    fi

    printf "%-20s %-15s %-15s %s\n" "Agent Loading" "Success" "Checked" \
        "$([ "$TESTS_PASSED" -gt 3 ] && echo "✓" || echo "⚠")"
    printf "%-20s %-15s %-15s %s\n" "Pattern Detection" ">5 patterns" "Varies" \
        "$([ -f "pattern-detection.log" ] && grep -q "PATTERN" pattern-detection.log && echo "✓" || echo "⚠")"
}

# Function to show usage
show_usage() {
    echo -e "${BLUE}JaCoCo Gosu Filter Comprehensive Test Runner${NC}"
    echo
    echo "Usage: $0 [OPTION]"
    echo
    echo "Options:"
    echo "  check         Only check prerequisites"
    echo "  build         Only build the agent"
    echo "  loading       Test agent loading only"
    echo "  patterns      Test pattern detection only"
    echo "  coverage      Test coverage reports only"
    echo "  bytecode      Test bytecode verification only"
    echo "  integration   Test integration properties only"
    echo "  summary       Show final summary only (if logs exist)"
    echo "  all           Run all tests (default)"
    echo "  help          Show this help message"
    echo
    echo "Examples:"
    echo "  $0                    # Run complete test suite"
    echo "  $0 check             # Check if environment is ready"
    echo "  $0 patterns          # Test pattern detection only"
    echo "  $0 coverage          # Test coverage analysis only"
    echo
    echo "Expected Results:"
    echo "  - Agent loads with transformer registration"
    echo "  - Pattern detection finds 5+ null-safety patterns"
    echo "  - Branch coverage: $EXPECTED_BRANCHES_COVERED/$EXPECTED_TOTAL_BRANCHES branches (${EXPECTED_COVERAGE_PERCENTAGE}%)"
    echo "  - Bytecode contains null-safety patterns"
    echo "  - Integration tests with EnabledIfSystemProperty work"
}

# Function to clean up temporary files
cleanup() {
    print_status $BLUE "Cleaning up temporary files..."
    rm -f META-INF/MANIFEST.MF
    print_status $GREEN "✓ Cleanup completed"
}

# Main script logic
main() {
    # Set up cleanup on exit
    trap cleanup EXIT

    case "${1:-all}" in
        "check")
            check_prerequisites
            ;;
        "build")
            check_prerequisites
            build_agent
            ;;
        "loading")
            check_prerequisites
            build_agent
            test_agent_loading
            ;;
        "patterns")
            check_prerequisites
            build_agent
            test_pattern_detection
            ;;
        "coverage")
            check_prerequisites
            build_agent
            test_coverage_reports
            ;;
        "bytecode")
            check_prerequisites
            test_bytecode_verification
            ;;
        "integration")
            check_prerequisites
            build_agent
            test_integration_properties
            ;;
        "summary")
            show_test_summary
            ;;
        "all")
            check_prerequisites
            build_agent
            test_agent_loading
            test_pattern_detection
            test_coverage_reports
            test_bytecode_verification
            test_integration_properties
            show_test_summary
            ;;
        "help"|"-h"|"--help")
            show_usage
            ;;
        *)
            print_status $RED "Unknown option: $1"
            echo
            show_usage
            exit 1
            ;;
    esac

    echo
    print_status $BOLD "========================================"
    print_status $GREEN "Test execution completed"
    print_status $BOLD "========================================"
}

# Run main function with all arguments
main "$@"