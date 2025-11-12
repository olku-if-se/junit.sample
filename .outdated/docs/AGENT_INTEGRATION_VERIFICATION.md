# JaCoCo Gosu Filter Agent Integration Verification Report

**Date**: 2025-11-10
**Purpose**: Document potential integration failure points and verification measures
**Status**: ✅ Verification measures implemented

---

## Executive Summary

This document analyzes the jacoco-gosu-filter integration and identifies **8 critical failure points** where the Java agent might not load correctly, plus **3 Gradle configuration risks** that could bypass agent loading entirely.

**Good News**: Verification measures have been implemented in [`build.gradle`](../../build.gradle) to detect and prevent most failure scenarios.

---

## Critical Integration Failure Points

### 1. ❌ Agent Loading Order Violation
**Severity**: CRITICAL
**Risk**: If JaCoCo agent loads before Gosu agent, bytecode injection fails
**Status**: ✅ **MITIGATED** (Lines 142-162 in build.gradle)

**The Problem**:
- Gosu filter agent MUST intercept JaCoCo's `Filters` class during loading
- If JaCoCo loads first, its classes are already initialized
- The transformer cannot modify already-loaded classes

**Detection Implementation**:
```gradle
// build.gradle:142-162
def allArgs = jvmArgs.join(' ')
def gosuIndex = allArgs.indexOf('gosu-filter-agent')
def jacocoIndex = allArgs.indexOf('jacocoagent')

if (jacocoIndex >= 0 && gosuIndex >= 0 && gosuIndex > jacocoIndex) {
    throw new GradleException("❌ AGENT ORDER VIOLATION!")
}
```

**What This Catches**:
- If Gradle JaCoCo plugin adds its agent before our `doFirst` block
- If custom configuration modifies JVM args in wrong order
- If plugin version changes behavior

**Verification Command**:
```bash
./gradlew test --dry-run 2>&1 | grep javaagent
```

---

### 2. ❌ Agent JAR Missing or Stale
**Severity**: HIGH
**Risk**: Tests run with old agent code or fail with cryptic errors
**Status**: ✅ **MITIGATED** (Lines 108-125 in build.gradle)

**The Problem**:
- Agent source modified but JAR not rebuilt
- JAR deleted but tests still attempt to load it
- Gradle caching serves stale JAR

**Detection Implementation**:
```gradle
// build.gradle:108-125
def agentJar = file(filterAgentPath)
if (!agentJar.exists()) {
    throw new GradleException("AGENT JAR MISSING: ${filterAgentPath}")
}

// Check freshness
def newerSources = fileTree(agentSourceDir).files.findAll {
    it.lastModified() > agentJar.lastModified()
}
if (newerSources) {
    println "⚠ WARNING: Agent JAR is stale!"
}
```

**What This Catches**:
- Missing agent JAR
- Stale agent JAR (source newer than compiled JAR)
- Build system issues

**Verification Command**:
```bash
# Delete agent JAR and verify detection
rm agents/gosu-filter-agent.jar
./gradlew test  # Should fail with clear error
```

---

### 3. ⚠️ Property Resolution Timing Issue
**Severity**: MEDIUM
**Risk**: Inconsistent filter enable/disable state
**Status**: ⚠️ **PARTIAL** (Property read once at configuration time)

**The Problem**:
```gradle
// Configuration phase (runs once)
def gosuFilterEnabled = project.hasProperty('jacoco.gosu.filter.enabled') ?
    project.property('jacoco.gosu.filter.enabled') : 'true'

if (gosuFilterEnabled == 'true') {
    dependsOn ':jacoco-gosu-filter:copyAgentJar'  // Evaluated here
}

doFirst {
    // Execution phase (runs later)
    if (gosuFilterEnabled == 'true') {
        jvmArgs "-javaagent:${filterAgentPath}"  // Uses same variable
    }
}
```

**Failure Scenario**:
1. Property is `true` at configuration → `dependsOn` added
2. Property changes to `false` before execution (unlikely but possible)
3. Agent dependency built but not loaded

**Mitigation**: Property is effectively immutable after configuration phase.

**Current Risk**: LOW (requires Gradle internals modification)

---

### 4. ❌ Hidden Test Task Bypass
**Severity**: CRITICAL
**Risk**: Test tasks created without agent configuration
**Status**: ⚠️ **NEEDS MONITORING**

**The Problem**:
```gradle
// gradle/test-custom-terminal-reporter.gradle:65
tasks.withType(Test).configureEach {
    // This applies to ALL Test tasks
}
```

Any plugin or script can create new `Test` tasks that:
- Don't inherit our agent configuration
- Run tests in separate JVM without agents
- Produce misleading coverage reports

**Known Hidden Test Tasks**:

| Task | Location | Agent Loaded? | Risk |
|------|----------|---------------|------|
| `test` | Main build.gradle:90 | ✅ Yes | Safe |
| `runtimeAgentTest` | jacoco-gosu-filter/build.gradle:116 | ❌ No (intentional) | Safe (tests agent itself) |
| Any Gosu plugin tasks | Unknown | ⚠️ Unknown | Needs investigation |

**Verification Command**:
```bash
# List all Test tasks
./gradlew tasks --all | grep -i test

# Check which tasks have JaCoCo enabled
./gradlew test --dry-run --info 2>&1 | grep -i "jacoco"
```

**Mitigation Strategy**:
```gradle
// Recommended addition to build.gradle
tasks.withType(Test).configureEach {
    // Force all Test tasks to inherit agent configuration
    if (it.name != 'runtimeAgentTest') {  // Exclude agent self-tests
        it.dependsOn 'test'  // Or copy agent config
    }
}
```

---

### 5. ❌ JUnit Platform Test Forking
**Severity**: HIGH
**Risk**: Forked test processes don't inherit agents
**Status**: ✅ **MITIGATED** (Lines 167-170 in build.gradle)

**The Problem**:
JUnit 5 can fork test processes for:
- Parallel execution
- Test isolation
- Module path separation

Forked JVMs may not inherit `-javaagent` arguments.

**Mitigation Implementation**:
```gradle
// build.gradle:167-170
maxParallelForks = 1  // Run tests serially
forkEvery = 0         // Never fork (reuse same JVM)
```

**What This Prevents**:
- Race conditions in agent loading
- Agent missing from forked processes
- Inconsistent coverage between test runs

**Trade-off**: Tests run slower (serial instead of parallel)

**Alternative** (if performance matters):
```gradle
maxParallelForks = 4  // Allow parallelism
forkOptions {
    jvmArgs = ["-javaagent:${filterAgentPath}"]  // Explicit agent in forks
}
```

---

### 6. ⚠️ Gradle Daemon State Pollution
**Severity**: MEDIUM
**Risk**: Cached state affects agent loading
**Status**: ⚠️ **MONITORING NEEDED**

**The Problem**:
```properties
# gradle.properties
org.gradle.parallel = true
org.gradle.caching = true
```

With parallel + caching:
- Test tasks run in different daemon processes
- Agent loading state not shared
- Cached test results may bypass agent

**Current Mitigation**: `maxParallelForks = 1` prevents parallel test execution

**Verification**:
```bash
# Kill daemon and verify clean build
./gradlew --stop
./gradlew clean test
```

---

### 7. ⚠️ Dynamic Agent Loading Restrictions (Java 21+)
**Severity**: MEDIUM
**Risk**: Agent loading silently fails on newer Java
**Status**: ✅ **MITIGATED** (Line 103 in build.gradle)

**The Problem**:
```gradle
jvmArgs "-XX:+EnableDynamicAgentLoading"
```

- Java 9-17: Warning if flag missing, agent still works
- Java 18-21: Agent loading restricted by default
- Future Java: Flag may be removed/ignored

**Current Protection**: Flag is always added

**Future Risk**: Java version changes deprecate flag

**Recommended Addition**:
```gradle
doFirst {
    def javaVersion = JavaVersion.current()
    if (javaVersion.majorVersion >= 21) {
        println "⚠️ Java 21+ detected - agent loading restrictions apply"
        println "   If tests fail, add: --add-opens java.base/java.lang=ALL-UNNAMED"
    }
}
```

---

### 8. ❌ JaCoCo Plugin Automatic Agent Injection Timing
**Severity**: CRITICAL
**Risk**: JaCoCo adds agent before our `doFirst` block
**Status**: ✅ **DETECTED** (Agent order verification catches this)

**The Problem**:

The Gradle JaCoCo plugin (`id 'jacoco'`) automatically adds its agent:

```gradle
jacoco {
    enabled = true  // When does this add -javaagent:jacocoagent.jar?
}
```

**Timing Analysis**:

| Phase | What Happens | When |
|-------|--------------|------|
| Configuration | JaCoCo plugin configures `test` task | Before `doFirst` |
| Execution | `doFirst` block runs | Just before test JVM starts |
| Execution | JaCoCo adds agent to JVM args | ??? |

**Documentation Gap**: Gradle JaCoCo plugin docs don't specify exact timing

**Current Protection**: Agent order verification (lines 142-162) catches reversed order

**Verification**:
```bash
./gradlew test --dry-run --info 2>&1 | grep -E "(javaagent|jvmArgs)"
```

---

## Additional Configuration Risks

### A. Test Task Finalization Chain Issue
**Location**: build.gradle:182, 186

**Problem**:
```gradle
test {
    finalizedBy(jacocoTestReport)  // Line 182
}
jacocoTestReport {
    dependsOn test  // Line 186 (DUPLICATE at line 218!)
}
```

**Risk**: Circular dependency if test fails but report attempts re-run

**Impact**: LOW (Gradle handles this correctly, but redundant)

**Recommendation**: Remove duplicate `dependsOn` at line 186 or 218

---

### B. Multiple Test Configuration Scripts
**Location**: build.gradle:11

```gradle
apply from: "$rootDir/gradle/test-custom-terminal-reporter.gradle"
```

**Risk**: Script modifies `Test` task behavior:
- Adds `afterTest` hook
- Could interfere with agent timing
- May override configurations

**Current Status**: No conflicts detected

**Monitoring**: Review this script after Gradle version upgrades

---

### C. Gosu Plugin Unknown Interactions
**Location**: build.gradle:4

```gradle
id 'org.gosu-lang.gosu' version '+'
```

**Risk**: Gosu plugin may:
- Add custom test tasks
- Modify bytecode loading
- Interfere with agent instrumentation

**Current Status**: No issues observed

**Monitoring**: Test after Gosu plugin updates

---

## CRITICAL BUG: JaCoCo Report Agent Loading

### ❌ INCORRECT CONFIGURATION DETECTED

**Location**: build.gradle:217-235

**The Problem**:
```gradle
jacocoTestReport {
    dependsOn test

    doFirst {
        // ❌ THIS DOES NOT WORK!
        jvmArgs "-XX:+EnableDynamicAgentLoading"
        jvmArgs "-javaagent:${filterAgentPath}"
        println "Loading Gosu filter agent for JaCoCo report generation: ${filterAgentPath}"
    }
}
```

**Why This Is Wrong**:

1. **`jacocoTestReport` is NOT a `Test` task** - it's a `JacocoReport` task
2. **It doesn't launch a new JVM** - it runs in Gradle's JVM
3. **`jvmArgs` has no effect** - not a valid property for JacocoReport tasks
4. **Agent loading happens during test execution**, not report generation
5. **Filter is applied during report analysis** - JaCoCo calls `Filters.allNonKotlinFilters()` when processing coverage data

**The Truth**:
- Agent loads during `test` task execution ✅ (Lines 90-183)
- Filter injection happens when JaCoCo `Filters` class loads ✅
- Report generation analyzes already-collected data ✅
- **No additional agent loading needed for report task** ✅

**Fix**: Remove this incorrect configuration entirely

**Correct Flow**:
```
1. test task starts → JVM launches with agents
2. GosuFilterAgent.premain() → registers transformer
3. JaCoCo loads Filters class → GosuFilterInjector intercepts
4. Tests run → JaCoCo collects coverage data
5. test task ends
6. jacocoTestReport task runs → processes collected data
7. JaCoCo calls Filters.allNonKotlinFilters() → our filter runs
8. Report generated with filtered branches
```

**Recommended Action**: Delete lines 220-235 (but keep the `doLast` logging block)

---

## Test Task Inventory

### Main Project Test Tasks

| Task Name | Type | Agent Status | JaCoCo | Purpose |
|-----------|------|--------------|--------|---------|
| `test` | Test | ✅ Loaded | ✅ Enabled | Main test execution |
| `jacocoTestReport` | JacocoReport | ❌ N/A | ❌ N/A | Report generation |
| `jacocoTestCoverageVerification` | JacocoCoverageVerification | ❌ N/A | ❌ N/A | Coverage verification |

### jacoco-gosu-filter Module Test Tasks

| Task Name | Type | Agent Status | JaCoCo | Purpose |
|-----------|------|--------------|--------|---------|
| `test` | Test | ❌ No agent | ❌ Disabled | Agent unit tests |
| `runtimeAgentTest` | Test | ❌ No agent | ❌ Disabled | Agent loading tests |

**Note**: The agent module's tests intentionally don't load the agent because they test the agent itself.

---

## Verification Procedures

### 1. Agent Loading Order Verification
```bash
# Run tests and check order
./gradlew test --info 2>&1 | grep -E "javaagent|Agent" | head -20

# Expected output:
# Loading Gosu filter agent: .../agents/gosu-filter-agent.jar
# ✓ Gosu agent loaded, JaCoCo agent will be added by plugin
# [GosuFilterAgent] STARTING GOSU FILTER AGENT
```

### 2. Agent Freshness Verification
```bash
# Modify agent source
touch jacoco-gosu-filter/src/main/java/org/jacoco/gosu/GosuFilterAgent.java

# Run tests
./gradlew test 2>&1 | grep -i "stale"

# Expected output:
# ⚠ WARNING: Agent JAR is stale! Source files modified after JAR build:
```

### 3. Test Task Discovery
```bash
# List all tasks
./gradlew tasks --all > all-tasks.txt

# Find Test tasks
grep -i "test" all-tasks.txt

# Check for unexpected test tasks
grep "extends.*Test" all-tasks.txt
```

### 4. Agent Injection Verification
```bash
# Run tests with verbose logging
./gradlew test --info 2>&1 | tee test-full.log

# Verify injection
grep "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" test-full.log

# Verify pattern detection
grep "\[GosuNullSafetyFilter\].*PATTERN" test-full.log
```

### 5. Coverage Comparison
```bash
# Baseline (no filter)
./gradlew -Pjacoco.gosu.filter.enabled=false clean test jacocoTestReport
cp build/reports/jacoco/test/jacocoTestReport.csv baseline.csv

# With filter
./gradlew -Pjacoco.gosu.filter.enabled=true clean test jacocoTestReport
cp build/reports/jacoco/test/jacocoTestReport.csv filtered.csv

# Compare
diff baseline.csv filtered.csv
```

---

## Success Criteria Checklist

- [x] Agent JAR existence verified before test execution
- [x] Agent JAR freshness checked (source vs compiled)
- [x] Agent loading order verified (Gosu before JaCoCo)
- [x] Test forking disabled to prevent agent bypass
- [ ] All Test tasks inventoried and documented
- [ ] Incorrect jacocoTestReport agent loading removed
- [x] Verification procedures documented
- [x] Failure modes identified and mitigated

---

## Recommendations

### High Priority
1. ❌ **Remove incorrect agent loading from `jacocoTestReport` task** (lines 220-235)
2. ✅ Keep agent loading verification in `test` task
3. ✅ Keep agent freshness check

### Medium Priority
4. ⚠️ Monitor for new Test tasks after plugin upgrades
5. ⚠️ Add Java version detection for future compatibility
6. ⚠️ Remove duplicate `dependsOn test` in jacocoTestReport

### Low Priority
7. ℹ️ Consider re-enabling parallel tests with explicit fork configuration
8. ℹ️ Add integration test for agent loading order
9. ℹ️ Create GitHub Action workflow to verify agent in CI

---

## Conclusion

The current implementation includes **excellent verification measures** that catch most failure scenarios:

✅ **Implemented Protections**:
- Agent JAR existence check
- Agent JAR freshness warning
- Agent loading order verification
- Test forking prevention

❌ **Critical Bug Found**:
- Incorrect agent loading in `jacocoTestReport` task (doesn't work, needs removal)

⚠️ **Monitoring Needed**:
- Hidden Test tasks from plugins
- Gradle daemon state
- Java version compatibility

**Overall Assessment**: The integration is well-protected against most failure modes, but the incorrect `jacocoTestReport` configuration should be removed to avoid confusion.
