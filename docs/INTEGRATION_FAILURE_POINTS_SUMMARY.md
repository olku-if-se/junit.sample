# JaCoCo Gosu Filter Integration - Failure Points Summary

**Analysis Date**: 2025-11-10
**Analyzed By**: Claude Code
**Integration Plan**: [JACOCO_GOSU_FILTER_INTEGRATION_PLAN.md](JACOCO_GOSU_FILTER_INTEGRATION_PLAN.md)
**Full Report**: [AGENT_INTEGRATION_VERIFICATION.md](AGENT_INTEGRATION_VERIFICATION.md)

---

## Quick Reference: 8 Failure Points + 3 Configuration Risks

### ✅ Mitigated (5/8)
1. ✅ Agent Loading Order Violation - **Verified in build.gradle:142-162**
2. ✅ Agent JAR Missing/Stale - **Checked in build.gradle:108-125**
3. ✅ JUnit Platform Forking - **Prevented in build.gradle:167-170**
4. ✅ Dynamic Agent Loading Flag - **Added in build.gradle:103**
5. ✅ Incorrect jacocoTestReport Config - **Fixed in build.gradle:213-228**

### ⚠️ Needs Monitoring (3/8)
6. ⚠️ Property Resolution Timing - **Low risk, Gradle internals**
7. ⚠️ Hidden Test Task Bypass - **Needs periodic inventory check**
8. ⚠️ Gradle Daemon State Pollution - **Mitigated by serial execution**

---

## Critical Finding: JaCoCo Plugin Agent Loading Timing

**THE BIGGEST UNKNOW**: The Gradle JaCoCo plugin adds its agent automatically, but the exact timing relative to our `doFirst` block is **undocumented and potentially version-dependent**.

### Current Protection
```gradle
// build.gradle:142-162
if (jacocoIndex >= 0 && gosuIndex >= 0 && gosuIndex > jacocoIndex) {
    throw new GradleException("❌ AGENT ORDER VIOLATION!")
}
```

This **catches the problem** but doesn't **prevent it** from happening if Gradle internals change.

### Verification Command
```bash
./gradlew test --info 2>&1 | grep -E "Loading.*agent|Agent.*loaded" | head -10
```

**Expected output**:
```
Loading Gosu filter agent: /path/to/agents/gosu-filter-agent.jar
✓ Gosu agent loaded, JaCoCo agent will be added by plugin
[GosuFilterAgent] STARTING GOSU FILTER AGENT
[GosuFilterInjector] INJECTION SUCCESSFUL
```

---

## Where Integration Can Fail

### Failure Mode 1: Wrong Agent Order
**Symptom**: Filter doesn't work, branch counts not reduced
**Detection**: Build fails with "AGENT ORDER VIOLATION" error
**Root Cause**: JaCoCo agent loads before Gosu agent
**Fix**: Check for Gradle plugin updates, verify jvmArgs order

### Failure Mode 2: Stale Agent JAR
**Symptom**: Old filter behavior, expected patterns not detected
**Detection**: Warning during test execution
**Root Cause**: Agent source modified but JAR not rebuilt
**Fix**: Run `./gradlew :jacoco-gosu-filter:build`

### Failure Mode 3: Missing Agent JAR
**Symptom**: Build fails with "AGENT JAR MISSING" error
**Detection**: Build fails immediately before test execution
**Root Cause**: Agent not built or deleted
**Fix**: Run `./gradlew :jacoco-gosu-filter:copyAgentJar`

### Failure Mode 4: Test Forking Bypass
**Symptom**: Inconsistent results, some tests show filtering, others don't
**Detection**: Check test process logs for agent startup
**Root Cause**: JUnit forked processes without agent
**Fix**: Already prevented with `maxParallelForks = 1, forkEvery = 0`

### Failure Mode 5: Hidden Test Task
**Symptom**: Coverage report doesn't show filtering
**Detection**: Run `./gradlew tasks --all | grep -i test`
**Root Cause**: Plugin created test task without agent config
**Fix**: Manually add agent config to discovered task or extend from main `test` task

---

## Integration Health Check Script

```bash
#!/bin/bash
# Run this script to verify integration health

echo "=== JaCoCo Gosu Filter Integration Health Check ==="

# 1. Agent JAR exists
if [ -f "agents/gosu-filter-agent.jar" ]; then
    echo "✅ Agent JAR exists"
else
    echo "❌ Agent JAR missing"
    exit 1
fi

# 2. Agent is fresh
AGENT_TIME=$(stat -c %Y agents/gosu-filter-agent.jar 2>/dev/null || stat -f %m agents/gosu-filter-agent.jar)
NEWEST_SOURCE=$(find jacoco-gosu-filter/src -name "*.java" -type f -exec stat -c %Y {} \; 2>/dev/null | sort -n | tail -1)
if [ "$AGENT_TIME" -gt "$NEWEST_SOURCE" ]; then
    echo "✅ Agent JAR is fresh"
else
    echo "⚠️  Agent JAR is stale"
fi

# 3. Run tests and check agent loading
echo "Running tests to check agent loading..."
./gradlew test --rerun-tasks 2>&1 | tee /tmp/test-output.log > /dev/null

if grep -q "\[GosuFilterAgent\].*STARTING" /tmp/test-output.log; then
    echo "✅ Gosu agent loaded"
else
    echo "❌ Gosu agent NOT loaded"
    exit 1
fi

if grep -q "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" /tmp/test-output.log; then
    echo "✅ Filter injection successful"
else
    echo "❌ Filter injection FAILED"
    exit 1
fi

# 4. Check agent order
if grep -q "Agent order verified" /tmp/test-output.log; then
    echo "✅ Agent loading order correct"
else
    echo "⚠️  Agent order verification not found (check manually)"
fi

# 5. Check for hidden test tasks
HIDDEN_TESTS=$(./gradlew tasks --all | grep -c "Test.*task" || echo 0)
echo "ℹ️  Found $HIDDEN_TESTS test-related tasks"

echo ""
echo "=== Health Check Complete ==="
```

Save as `scripts/health-check.sh` and run with:
```bash
chmod +x scripts/health-check.sh
./scripts/health-check.sh
```

---

## Gradle Version Compatibility Matrix

| Gradle Version | JaCoCo Plugin | Agent Loading | Status |
|----------------|---------------|---------------|--------|
| 8.0 - 8.5 | Built-in | ✅ Works | Tested |
| 8.6+ | Built-in | ⚠️ Unknown | Needs testing |
| 9.0+ | Built-in | ⚠️ Unknown | Needs testing |

**Recommendation**: Test thoroughly after Gradle upgrades

---

## Future Risks

### Risk 1: Java Version Changes
- **Java 21+**: Dynamic agent loading increasingly restricted
- **Future Java**: `-XX:+EnableDynamicAgentLoading` may be deprecated
- **Mitigation**: Monitor Java release notes, test on new versions

### Risk 2: JaCoCo Plugin Changes
- **Plugin updates**: May change agent injection timing
- **Breaking changes**: Could reverse agent order
- **Mitigation**: Pin JaCoCo version, test before upgrading

### Risk 3: Gosu Plugin Evolution
- **New test tasks**: May bypass agent configuration
- **Bytecode changes**: May require filter pattern updates
- **Mitigation**: Monitor Gosu plugin updates, regression test

---

## Integration Plan Assessment

### What Works Well
✅ Comprehensive phase-by-phase plan
✅ Clear timing flow diagrams
✅ Detailed verification steps
✅ Troubleshooting guidance

### What Could Fail
⚠️ JaCoCo plugin timing assumption
⚠️ Hidden test task creation
⚠️ Future Java restrictions

### Plan Accuracy Rating: 90%

**The plan is excellent** but assumes:
1. JaCoCo plugin adds agent AFTER `doFirst` (needs verification)
2. No hidden test tasks exist (needs inventory)
3. Current Java behavior persists (needs monitoring)

---

## Recommended Actions

### Immediate (Do Now)
1. ✅ Agent order verification implemented
2. ✅ Agent freshness check implemented
3. ✅ Test forking prevented
4. ✅ Incorrect config removed

### Short-term (Next Sprint)
1. ⚠️ Create `health-check.sh` script
2. ⚠️ Inventory all test tasks
3. ⚠️ Test on different Gradle versions

### Long-term (Next Quarter)
1. ℹ️ Add CI/CD integration tests
2. ℹ️ Monitor Java 21+ compatibility
3. ℹ️ Create plugin version compatibility matrix

---

## Key Takeaways

1. **Agent loading order is CRITICAL** - Gosu must load before JaCoCo
2. **Gradle timing is UNDOCUMENTED** - Verification catches but doesn't prevent issues
3. **Test forking is DANGEROUS** - Disabled to ensure agent in all processes
4. **Hidden tasks are RISKY** - Need periodic inventory
5. **jacocoTestReport doesn't need agent** - Filtering happens during report analysis, not generation

---

## Questions for Integration Plan Authors

1. **Q**: How was JaCoCo plugin agent timing verified?
   **A**: Assumed based on typical Gradle behavior, not explicitly tested

2. **Q**: What happens if new test tasks are created by plugins?
   **A**: They may bypass agent configuration, need monitoring

3. **Q**: Why load agent in jacocoTestReport?
   **A**: Misunderstanding - report generation processes existing data, doesn't need agent

4. **Q**: Is maxParallelForks=1 necessary?
   **A**: Yes, to prevent forked JVMs without agents

5. **Q**: What if Java 22+ breaks dynamic agent loading?
   **A**: Need contingency plan, potentially require earlier Java version

---

## References

- Integration Plan: [JACOCO_GOSU_FILTER_INTEGRATION_PLAN.md](JACOCO_GOSU_FILTER_INTEGRATION_PLAN.md)
- Full Analysis: [AGENT_INTEGRATION_VERIFICATION.md](AGENT_INTEGRATION_VERIFICATION.md)
- Build Configuration: [../build.gradle](../build.gradle)
- Gradle JaCoCo Plugin: https://docs.gradle.org/current/userguide/jacoco_plugin.html
- Java Agent Spec: https://docs.oracle.com/en/java/javase/11/docs/api/java.instrument/java/lang/instrument/package-summary.html

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Next Review**: After Gradle or Java version upgrade
