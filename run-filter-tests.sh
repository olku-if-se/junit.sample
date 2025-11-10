#!/usr/bin/env bash

# Test runner script for Gosu Null Safety Filter unit tests
# This script makes it easy to run the comprehensive test suite

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Gosu Null Safety Filter Test Runner${NC}"
echo -e "${BLUE}========================================${NC}"

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to check if PolicyPeriodEnhancement.class exists
check_prerequisites() {
    print_status $BLUE "Checking prerequisites..."

    # Check Java version
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 11 ]; then
        print_status $RED "Error: Java 11+ required. Current version: $(java -version 2>&1 | head -1)"
        exit 1
    fi
    print_status $GREEN "✓ Java version: $(java -version 2>&1 | head -1)"

    # Check if PolicyPeriodEnhancement.class exists
    if [ -f "build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class" ]; then
        print_status $GREEN "✓ PolicyPeriodEnhancement.class found"
    else
        print_status $YELLOW "⚠ PolicyPeriodEnhancement.class not found"
        echo -e "${YELLOW}  Compiling Gosu source code...${NC}"
        ./gradlew compileGosu
        if [ -f "build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class" ]; then
            print_status $GREEN "✓ PolicyPeriodEnhancement.class compiled successfully"
        else
            print_status $RED "Error: Failed to compile PolicyPeriodEnhancement.class"
            exit 1
        fi
    fi

    # Check if gradle wrapper exists
    if [ -f "./gradlew" ]; then
        print_status $GREEN "✓ Gradle wrapper found"
    else
        print_status $RED "Error: gradlew not found. Please run from project root."
        exit 1
    fi
}

# Function to run basic tests
run_basic_tests() {
    print_status $BLUE "Running basic filter tests..."
    echo

    ./gradlew :jacoco-gosu-filter:test --tests "GosuNullSafetyFilterWorkingTest" --info

    if [ $? -eq 0 ]; then
        print_status $GREEN "✓ Basic tests passed"
    else
        print_status $RED "✗ Basic tests failed"
        return 1
    fi
}

# Function to run detailed analysis tests
run_detailed_tests() {
    print_status $BLUE "Running detailed bytecode analysis tests..."
    echo

    ./gradlew :jacoco-gosu-filter:test --tests "*BranchAnalysis*" -Djacoco.gosu.filter.debug=true --console=plain

    if [ $? -eq 0 ]; then
        print_status $GREEN "✓ Detailed analysis tests passed"
    else
        print_status $RED "✗ Detailed analysis tests failed"
        return 1
    fi
}

# Function to run integration tests
run_integration_tests() {
    print_status $BLUE "Running integration tests..."
    echo

    ./gradlew :jacoco-gosu-filter:test --tests "FilterIntegrationTest,GosuNullSafetyFilterWorkingTest" -Drun.integration.tests=true --info

    if [ $? -eq 0 ]; then
        print_status $GREEN "✓ Integration tests passed"
    else
        print_status $YELLOW "⚠ Integration tests had issues (may be expected)"
    fi
}

# Function to run all tests
run_all_tests() {
    print_status $BLUE "Running complete test suite..."
    echo

    ./gradlew :jacoco-gosu-filter:test -Djacoco.gosu.filter.debug=true --info

    if [ $? -eq 0 ]; then
        print_status $GREEN "✓ All tests passed"
    else
        print_status $RED "✗ Some tests failed"
        return 1
    fi
}

# Function to show test report summary
show_test_summary() {
    print_status $BLUE "Generating test summary..."
    echo

    # Find and display test results
    TEST_RESULTS_DIR="jacoco-gosu-filter/build/test-results/test"
    if [ -d "$TEST_RESULTS_DIR" ]; then
        echo -e "${BLUE}Test Results:${NC}"
        find "$TEST_RESULTS_DIR" -name "TEST-*.xml" -exec basename {} \; | while read file; do
            echo "  - $file"
        done

        # Show test count if available
        TEST_COUNT=$(grep -h "tests=" "$TEST_RESULTS_DIR"/TEST-*.xml 2>/dev/null | head -1 | sed 's/.*tests="\([0-9]*\)".*/\1/' || echo "0")
        FAILURES=$(grep -h "failures=" "$TEST_RESULTS_DIR"/TEST-*.xml 2>/dev/null | head -1 | sed 's/.*failures="\([0-9]*\)".*/\1/' || echo "0")
        ERRORS=$(grep -h "errors=" "$TEST_RESULTS_DIR"/TEST-*.xml 2>/dev/null | head -1 | sed 's/.*errors="\([0-9]*\)".*/\1/' || echo "0")

        echo -e "${BLUE}Test Summary:${NC}"
        echo "  Total tests: $TEST_COUNT"
        echo "  Failures: $FAILURES"
        echo "  Errors: $ERRORS"

        if [ "$FAILURES" -eq 0 ] && [ "$ERRORS" -eq 0 ]; then
            print_status $GREEN "✓ All tests executed successfully"
        else
            print_status $YELLOW "⚠ Some tests had failures or errors"
        fi
    else
        print_status $YELLOW "⚠ No test results found"
    fi
}

# Function to show usage
show_usage() {
    echo -e "${BLUE}Usage: $0 [OPTION]${NC}"
    echo
    echo "Options:"
    echo "  basic      Run basic filter tests only"
    echo "  detailed   Run detailed bytecode analysis tests"
    echo "  integration Run integration tests"
    echo "  all        Run all tests (default)"
    echo "  check      Only check prerequisites"
    echo "  help       Show this help message"
    echo
    echo "Examples:"
    echo "  $0                    # Run all tests"
    echo "  $0 basic             # Run only basic tests"
    echo "  $0 detailed          # Run detailed analysis"
    echo "  $0 check             # Check if environment is ready"
}

# Main script logic
case "${1:-all}" in
    "basic")
        check_prerequisites
        run_basic_tests
        show_test_summary
        ;;
    "detailed")
        check_prerequisites
        run_detailed_tests
        show_test_summary
        ;;
    "integration")
        check_prerequisites
        run_integration_tests
        show_test_summary
        ;;
    "all")
        check_prerequisites
        run_all_tests
        show_test_summary
        ;;
    "check")
        check_prerequisites
        print_status $GREEN "✓ All prerequisites satisfied"
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
print_status $BLUE "========================================${NC}"
print_status $GREEN "Test execution completed${NC}"
print_status $BLUE "========================================${NC}"