# JaCoCo Gosu Filter - Two-Step Integration Guide

**Version**: 2.0
**Date**: 2025-11-10
**Status**: ✅ Production Ready

---

## Executive Summary

The JaCoCo Gosu filter integration follows a **two-step process** because JaCoCo filters are applied during **report generation**, not test execution:

1. **Step 1**: Build the agent JAR (one-time setup)
2. **Step 2**: Load agent into Gradle daemon JVM for report generation

This document explains why this approach is necessary and how to implement it.

---

## Why Two Steps?

### The Problem

JaCoCo's filter mechanism works differently than initially understood:

| Phase | What Happens | Filters Used? |
|-------|--------------|---------------|
| **Test Execution** (`test` task) | Classes instrumented, probes record hits | ❌ NO |
| **Report Generation** (`jacocoTestReport` task) | Coverage calculated from probe data | ✅ YES |

**Key Insight**: Filters are called during `jacocoTestReport`, specifically when `ClassAnalyzer` creates the filter chain via `Filters.all()`.

See [JACOCO_FILTER_LOADING_ANALYSIS.md](JACOCO_FILTER_LOADING_ANALYSIS.md) for detailed technical analysis.

### The Challenge

The `jacocoTestReport` task runs in the **Gradle daemon JVM**, not a forked test JVM. This means:

- We cannot use `-javaagent` on the `test` task (wrong phase)
- We cannot use `jvmArgs` on `jacocoTestReport` (not a Test task)
- **We must load the agent into the Gradle daemon itself**

### The Solution

**Two-Step Integration**:

1. **Build Phase**: Create the agent JAR with version tracking
2. **Load Phase**: Inject agent into Gradle daemon via:
   - `gradle.properties` (recommended)
   - `GRADLE_OPTS` environment variable
   - OR fallback: no-daemon forked process

---

## Quick Start

### Method 1: Gradle Properties (Recommended)

**One-time setup:**

```bash
# 1. Build the agent
./gradlew :jacoco-gosu-filter:build

# 2. Add to gradle.properties
echo "org.gradle.jvmargs=-Xmx4g -javaagent:agents/gosu-filter-agent.jar" >> gradle.properties

# 3. Restart Gradle daemon
./gradlew --stop

# 4. Generate report with filter
./gradlew jacocoTestReport
```

### Method 2: Environment Variable

```bash
# 1. Build the agent
./gradlew :jacoco-gosu-filter:build

# 2. Set environment variable
export GRADLE_OPTS="-javaagent:$(pwd)/agents/gosu-filter-agent.jar"

# 3. Restart Gradle daemon
./gradlew --stop

# 4. Generate report
./gradlew jacocoTestReport
```

### Method 3: Fallback Script (No Daemon Modification)

```bash
# One command does everything
./scripts/generate-jacoco-report-with-filter.sh
```

This script:
- Builds agent if needed
- Stops current daemon
- Runs `jacocoTestReport` with `--no-daemon` and `GRADLE_OPTS`
- Generates filtered report

---

## Detailed Implementation

### Step 1: Build the Versioned Agent

#### Agent JAR Naming

The agent JAR is now versioned for better control:

```
agents/
├── gosu-filter-agent-1.0.0.jar  # Versioned (immutable)
└── gosu-filter-agent.jar         # Latest (symlink/copy)
```

**Version**: Defined in [`jacoco-gosu-filter/build.gradle`](../../jacoco-gosu-filter/build.gradle):

```gradle
version = '1.0.0'
group = 'org.jacoco.gosu'
```

#### Build Command

```bash
./gradlew :jacoco-gosu-filter:build
```

This:
1. Compiles agent source code
2. Packages as JAR with manifest
3. Copies to `agents/gosu-filter-agent-1.0.0.jar`
4. Creates `agents/gosu-filter-agent.jar` (latest)

#### Automatic Build

The integration script ([`gradle/jacoco-filter-integration.gradle`](../gradle/jacoco-filter-integration.gradle)) automatically builds the agent if missing:

```gradle
if (!agentStatus.available) {
    println "Building agent..."
    project.exec {
        commandLine './gradlew', ':jacoco-gosu-filter:build', '--quiet'
    }
}
```

---

### Step 2: Load Agent into Gradle Daemon

#### Option A: gradle.properties (Permanent)

**File**: `gradle.properties`

**Add/Modify**:
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -javaagent:agents/gosu-filter-agent.jar
```

**Pros**:
- Permanent (applies to all Gradle invocations)
- Version controlled (shared with team)
- Automatic (no manual steps)

**Cons**:
- Affects all Gradle builds in this project
- Requires Gradle daemon restart
- Agent must exist before first build

**Restart Daemon**:
```bash
./gradlew --stop
./gradlew jacocoTestReport  # Agent loads automatically
```

---

#### Option B: GRADLE_OPTS (Temporary)

**Command**:
```bash
export GRADLE_OPTS="-javaagent:$(pwd)/agents/gosu-filter-agent.jar"
./gradlew --stop
./gradlew jacocoTestReport
```

**Pros**:
- Temporary (doesn't modify files)
- Easy to enable/disable
- Can use different agent versions

**Cons**:
- Must be set every shell session
- Not shared with team
- Easy to forget

**Per-Command**:
```bash
GRADLE_OPTS="-javaagent:agents/gosu-filter-agent.jar" ./gradlew jacocoTestReport --no-daemon
```

---

#### Option C: Fallback Script (No Daemon Modification)

**Script**: [`scripts/generate-jacoco-report-with-filter.sh`](../scripts/generate-jacoco-report-with-filter.sh)

**Usage**:
```bash
./scripts/generate-jacoco-report-with-filter.sh
```

**How It Works**:
1. Verifies agent exists (builds if needed)
2. Stops current Gradle daemon
3. Runs `jacocoTestReport` with `--no-daemon` and `GRADLE_OPTS=-javaagent:...`
4. Agent loads into the one-off Gradle process
5. Filter applies during report generation
6. Process exits (agent unloaded)

**Pros**:
- No permanent configuration changes
- Works without modifying `gradle.properties`
- Self-contained (builds agent if needed)
- Safe (doesn't affect other builds)

**Cons**:
- Slower (no daemon caching)
- Must run script each time
- Stops existing daemon (interrupts other builds)

---

## Verification

### Check Agent is Loaded

The integration script automatically detects if the agent is loaded:

```bash
./gradlew jacocoTestReport
```

**Output if NOT loaded**:
```
================================================================================
JaCoCo Gosu Filter Integration Status
================================================================================
✓ Using Gosu filter agent v1.0.0
⚠️  Agent is NOT loaded in Gradle daemon JVM

AGENT MUST BE LOADED INTO GRADLE DAEMON

To activate the Gosu filter, you must restart Gradle with the agent:

METHOD 1: Modify gradle.properties (RECOMMENDED)
  Add to gradle.properties:
    org.gradle.jvmargs=-Xmx4g -javaagent:agents/gosu-filter-agent.jar

  Then restart Gradle:
    ./gradlew --stop
    ./gradlew jacocoTestReport

[... additional methods ...]

WITHOUT THE AGENT:
  - Report generation will proceed
  - But Gosu null-safety branches will NOT be filtered
  - Coverage will include compiler-generated null checks

Press Ctrl+C to cancel, or wait 10 seconds to continue without filter...
================================================================================
```

**Output if loaded**:
```
================================================================================
JaCoCo Gosu Filter Integration Status
================================================================================
✓ Using Gosu filter agent v1.0.0
✓ Agent is loaded in Gradle daemon JVM
✓ Filter will be applied during report generation
================================================================================
```

### Manual Verification

Check if agent is in Gradle JVM args:

```bash
# Method 1: Check gradle.properties
grep javaagent gradle.properties

# Method 2: Check running Gradle daemon
jps -lvm | grep GradleDaemon | grep javaagent

# Method 3: Check Gradle daemon logs
cat ~/.gradle/daemon/*/daemon-*.out.log | grep GosuFilterAgent
```

### Verify Filter is Working

After generating report with agent loaded:

```bash
# 1. Generate report
./gradlew test jacocoTestReport

# 2. Check for filter application
grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv

# Expected: Line 13 should show 2 branches (filtered), not 4 (unfiltered)
```

---

## Troubleshooting

### Problem: Agent Not Building

**Symptom**:
```
❌ Agent JAR not found
Building agent...
❌ Agent build failed!
```

**Solution**:
```bash
# Build manually with verbose output
./gradlew :jacoco-gosu-filter:build --info

# Check for compilation errors
./gradlew :jacoco-gosu-filter:build --stacktrace
```

### Problem: Agent Not Loading

**Symptom**:
```
⚠️  Agent is NOT loaded in Gradle daemon JVM
```

**Solutions**:

**Check 1**: Is agent in gradle.properties?
```bash
grep javaagent gradle.properties
```

**Check 2**: Did you restart daemon?
```bash
./gradlew --stop
./gradlew jacocoTestReport
```

**Check 3**: Is agent path correct?
```bash
ls -la agents/gosu-filter-agent.jar
```

**Check 4**: Use absolute path in gradle.properties
```properties
org.gradle.jvmargs=-Xmx4g -javaagent:/absolute/path/to/agents/gosu-filter-agent.jar
```

### Problem: Filter Not Applying

**Symptom**: Report shows same branch counts with/without filter

**Solution**:

```bash
# 1. Verify agent loads
./gradlew jacocoTestReport 2>&1 | grep "Agent is loaded"

# 2. Enable debug logging in agent source
# Edit jacoco-gosu-filter/src/main/java/org/jacoco/gosu/GosuFilterInjector.java
# Add more System.out.println statements

# 3. Rebuild agent
./gradlew :jacoco-gosu-filter:build

# 4. Restart daemon and regenerate
./gradlew --stop
./gradlew clean test jacocoTestReport

# 5. Check Gradle daemon logs
tail -f ~/.gradle/daemon/*/daemon-*.out.log | grep Gosu
```

### Problem: Stale Agent JAR

**Symptom**: Changes to agent code not reflected in reports

**Solution**:
```bash
# 1. Clean and rebuild agent
./gradlew :jacoco-gosu-filter:clean
./gradlew :jacoco-gosu-filter:build

# 2. Verify new timestamp
ls -la agents/gosu-filter-agent*.jar

# 3. Restart daemon (critical!)
./gradlew --stop

# 4. Regenerate report
./gradlew clean test jacocoTestReport
```

### Problem: Multiple Gradle Versions

**Symptom**: Agent loads in some projects but not others

**Solution**: Use absolute path in gradle.properties
```properties
# Bad (relative path, breaks in multi-project builds)
org.gradle.jvmargs=-javaagent:agents/gosu-filter-agent.jar

# Good (absolute path, works everywhere)
org.gradle.jvmargs=-javaagent:/home/user/project/agents/gosu-filter-agent.jar
```

---

## Version Management

### Updating Agent Version

1. **Modify version** in `jacoco-gosu-filter/build.gradle`:
   ```gradle
   version = '1.1.0'  // Increment
   ```

2. **Rebuild agent**:
   ```bash
   ./gradlew :jacoco-gosu-filter:build
   ```

3. **Verify new version**:
   ```bash
   ls agents/
   # Should show:
   #   gosu-filter-agent-1.0.0.jar (old)
   #   gosu-filter-agent-1.1.0.jar (new)
   #   gosu-filter-agent.jar (symlink to 1.1.0)
   ```

4. **Restart daemon**:
   ```bash
   ./gradlew --stop
   ./gradlew jacocoTestReport
   ```

5. **Optional: Update gradle.properties** to pin version:
   ```properties
   org.gradle.jvmargs=-Xmx4g -javaagent:agents/gosu-filter-agent-1.1.0.jar
   ```

### Version Compatibility

| Agent Version | JaCoCo Version | Status |
|---------------|----------------|--------|
| 1.0.0 | 0.8.14+ | ✅ Tested |
| 1.0.0 | 0.8.12-0.8.13 | ⚠️ Untested |
| 1.0.0 | < 0.8.12 | ❌ Not supported |

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: JaCoCo with Gosu Filter

on: [push, pull_request]

jobs:
  test-with-coverage:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'corretto'

      - name: Build Gosu Filter Agent
        run: ./gradlew :jacoco-gosu-filter:build

      - name: Run Tests
        run: ./gradlew test

      - name: Generate Coverage Report with Filter
        run: |
          export GRADLE_OPTS="-javaagent:$(pwd)/agents/gosu-filter-agent.jar"
          ./gradlew jacocoTestReport --no-daemon

      - name: Upload Coverage Report
        uses: actions/upload-artifact@v3
        with:
          name: jacoco-report
          path: build/reports/jacoco/test/html/
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any

    stages {
        stage('Build Agent') {
            steps {
                sh './gradlew :jacoco-gosu-filter:build'
            }
        }

        stage('Test') {
            steps {
                sh './gradlew test'
            }
        }

        stage('Coverage Report') {
            environment {
                GRADLE_OPTS = "-javaagent:${WORKSPACE}/agents/gosu-filter-agent.jar"
            }
            steps {
                sh './gradlew jacocoTestReport --no-daemon'
            }
        }

        stage('Publish Report') {
            steps {
                publishHTML([
                    reportDir: 'build/reports/jacoco/test/html',
                    reportFiles: 'index.html',
                    reportName: 'JaCoCo Coverage Report'
                ])
            }
        }
    }
}
```

---

## FAQ

### Q: Why not load agent during test task?

**A**: Filters are only used during report generation, not test execution. Loading the agent during `test` task has no effect because the `Filters` class is never loaded in the test JVM.

See [JACOCO_FILTER_LOADING_ANALYSIS.md](JACOCO_FILTER_LOADING_ANALYSIS.md) for technical details.

### Q: Can I use the filter without modifying gradle.properties?

**A**: Yes, use the fallback script:
```bash
./scripts/generate-jacoco-report-with-filter.sh
```

Or set `GRADLE_OPTS` temporarily:
```bash
GRADLE_OPTS="-javaagent:agents/gosu-filter-agent.jar" ./gradlew jacocoTestReport --no-daemon
```

### Q: Does the agent affect other Gradle builds?

**A**: Only if you use `gradle.properties` or a persistent `GRADLE_OPTS`. The fallback script uses `--no-daemon` to avoid this.

### Q: How do I disable the filter?

**Methods**:

1. **Disable in gradle.properties**:
   ```properties
   jacoco.gosu.filter.enabled=false
   ```

2. **Remove agent from gradle.properties**:
   ```properties
   # Comment out or remove:
   # org.gradle.jvmargs=-javaagent:agents/gosu-filter-agent.jar
   ```

3. **Restart daemon**:
   ```bash
   ./gradlew --stop
   ./gradlew jacocoTestReport  # Runs without filter
   ```

### Q: Can I use different filter versions in different projects?

**A**: Yes, use project-specific `gradle.properties`:

```properties
# Project A
org.gradle.jvmargs=-javaagent:/path/to/projectA/agents/gosu-filter-agent-1.0.0.jar

# Project B
org.gradle.jvmargs=-javaagent:/path/to/projectB/agents/gosu-filter-agent-1.1.0.jar
```

Or use `GRADLE_OPTS` per-project.

### Q: What if I forget to restart the daemon?

**A**: The integration script warns you:
```
⚠️  Agent is NOT loaded in Gradle daemon JVM
Press Ctrl+C to cancel, or wait 10 seconds to continue without filter...
```

You have 10 seconds to cancel and restart properly.

---

## Summary

### Two-Step Process

1. **Build Agent** (one-time):
   ```bash
   ./gradlew :jacoco-gosu-filter:build
   ```

2. **Load into Daemon** (choose one):
   - **Permanent**: Add to `gradle.properties`
   - **Temporary**: Set `GRADLE_OPTS`
   - **Fallback**: Run `./scripts/generate-jacoco-report-with-filter.sh`

### Key Points

- ✅ Agent loads into Gradle daemon JVM (report generation phase)
- ✅ Version-tracked agent JARs (e.g., `gosu-filter-agent-1.0.0.jar`)
- ✅ Automatic build if agent missing
- ✅ Automatic detection if agent not loaded
- ✅ Fallback script for no-daemon usage
- ✅ CI/CD friendly

### Files Modified

- [`jacoco-gosu-filter/build.gradle`](../../jacoco-gosu-filter/build.gradle) - Version management
- [`gradle/jacoco-filter-integration.gradle`](../gradle/jacoco-filter-integration.gradle) - Integration logic
- [`scripts/generate-jacoco-report-with-filter.sh`](../scripts/generate-jacoco-report-with-filter.sh) - Fallback script

---

**Next**: [Test the Integration](../scripts/integration-health-check.sh)

**Reference**: [JaCoCo Filter Loading Analysis](JACOCO_FILTER_LOADING_ANALYSIS.md)

**Version**: 2.0
**Last Updated**: 2025-11-10
