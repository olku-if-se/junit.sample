#!/usr/bin/env bash

echo "=== Testing Enhanced Filter Pattern Detection ==="
echo

# Force clean rebuild
./gradlew clean > /dev/null 2>&1

# Run test with debug and capture all output
echo "Running test with debug mode enabled..."
echo "Command: ./gradlew test --tests *BranchAnalysis* -Djacoco.gosu.filter.debug=true --rerun-tasks"
echo

./gradlew :jacoco-gosu-filter:test --tests "*BranchAnalysis*" \
  -Djacoco.gosu.filter.debug=true \
  --rerun-tasks \
  --info 2>&1 | tee enhanced-filter-output.log

echo
echo "=== Extracting Pattern Detection Results ==="
echo

# Extract only the relevant parts from the log
grep -E "(PATTERN|BRANCH|IGNORED|REDUCTION|AvailableBrandConceptsForProdCode)" enhanced-filter-output.log | head -30

echo
echo "=== Summary ==="
echo "Full output saved to: enhanced-filter-output.log"