#!/bin/bash

# Script to toggle Gosu filter on/off for testing baseline vs filtered coverage

set -e

BUILD_GRADLE="build.gradle"
BACKUP_FILE="build.gradle.backup"

print_usage() {
    echo "Usage: $0 [on|off|status]"
    echo
    echo "Commands:"
    echo "  on     - Enable Gosu filter (default)"
    echo "  off    - Disable Gosu filter for baseline testing"
    echo "  status - Show current filter status"
    echo
    echo "Examples:"
    echo "  $0 off    # Disable filter to get baseline coverage"
    echo "  $0 on     # Re-enable filter"
    echo "  $0 status # Check current status"
}

check_status() {
    echo "=== GOSU FILTER STATUS ==="

    if [ -f "$BACKUP_FILE" ]; then
        echo "Status: DISABLED (backup exists)"
        echo "Backup file: $BACKUP_FILE"
        echo "To restore: $0 on"
    else
        echo "Status: ENABLED (no backup found)"
        if grep -q "jvmArgs.*gosu-filter-agent" "$BUILD_GRADLE"; then
            echo "✓ Filter agent line present in build.gradle"
        else
            echo "⚠ Filter agent line not found in build.gradle"
        fi
    fi
    echo
}

enable_filter() {
    echo "=== ENABLING GOSU FILTER ==="

    if [ -f "$BACKUP_FILE" ]; then
        echo "Restoring backup..."
        cp "$BACKUP_FILE" "$BUILD_GRADLE"
        rm "$BACKUP_FILE"
        echo "✓ Filter enabled (backup restored)"
    else
        echo "No backup found - filter appears to already be enabled"
        if grep -q "jvmArgs.*gosu-filter-agent" "$BUILD_GRADLE"; then
            echo "✓ Filter agent line confirmed in build.gradle"
        else
            echo "⚠ Warning: Filter agent line not found in build.gradle"
        fi
    fi

    echo "Filter line in build.gradle:"
    grep -n "javaagent.*gosu-filter-agent" "$BUILD_GRADLE" || echo "  (Not found)"
    echo
}

disable_filter() {
    echo "=== DISABLING GOSU FILTER ==="

    if [ ! -f "$BUILD_GRADLE" ]; then
        echo "Error: build.gradle not found"
        exit 1
    fi

    if [ -f "$BACKUP_FILE" ]; then
        echo "Filter already disabled (backup exists)"
    else
        echo "Creating backup..."
        cp "$BUILD_GRADLE" "$BACKUP_FILE"

        # Comment out the filter agent line
        sed -i 's|        jvmArgs "-javaagent:${filterAgentPath}"|        // jvmArgs "-javaagent:${filterAgentPath}"|' "$BUILD_GRADLE"

        echo "✓ Filter disabled (agent line commented out)"
        echo "Backup saved to: $BACKUP_FILE"
    fi

    echo "Filter line in build.gradle:"
    grep -n "javaagent.*gosu-filter-agent" "$BUILD_GRADLE" || echo "  (Commented out or not found)"
    echo
}

# Main logic
case "${1:-status}" in
    "on")
        enable_filter
        ;;
    "off")
        disable_filter
        ;;
    "status")
        check_status
        ;;
    "help"|"-h"|"--help")
        print_usage
        ;;
    *)
        echo "Unknown command: $1"
        echo
        print_usage
        exit 1
        ;;
esac

echo "=== NEXT STEPS ==="
if [ "${1:-status}" = "off" ]; then
    echo "Run tests without filter:"
    echo "  ./gradlew clean test jacocoTestReport"
    echo "  # Results will show baseline coverage (inflated by null-safety code)"
    echo
    echo "To re-enable filter:"
    echo "  $0 on"
elif [ "${1:-status}" = "on" ]; then
    echo "Run tests with filter:"
    echo "  ./gradlew clean test jacocoTestReport"
    echo "  # Results will show business logic coverage (null-safety filtered)"
    echo
    echo "To compare with baseline:"
    echo "  $0 off"
else
    echo "To toggle filter:"
    echo "  $0 off  # Disable for baseline"
    echo "  $0 on   # Re-enable"
fi