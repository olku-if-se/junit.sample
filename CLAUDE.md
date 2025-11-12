# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a hybrid Java/Gosu project demonstrating Test Driven Development with JUnit, Mockito, and JaCoCo code coverage. The project includes a custom JaCoCo filter module that improves branch coverage accuracy for Gosu code by filtering out null-safe navigation patterns.

**Key Features:**
- Java 11 + Gosu language hybrid project
- JUnit 5 testing framework with JUnit 4 compatibility
- JaCoCo code coverage with custom Gosu null-safety filter
- Mockito for mocking, AssertJ for fluent assertions
- REST API testing with MockWebServer
- Comprehensive test scripts for verification

## Build System

This project uses Gradle with custom plugins and configurations:

### Core Build Commands

```bash
# Build entire project
./gradlew build

# Run all tests (primary development command)
./gradlew test

# Run tests with continuous build (auto-rerun on changes)
./gradlew test --continuous

# Generate JaCoCo coverage report
./gradlew jacocoTestReport

# Build the Gosu filter module specifically
./gradlew clean jacoco-gosu-filter:build

# View JaCoCo HTML report (opens browser)
./gradlew viewReport

# Compare branch coverage summary
./gradlew compareCoverage
```

### Test Execution Commands

```bash
# Run specific test class
./gradlew test --tests test.PolicyPeriodEnhancementTest

# Run tests with detailed output
./gradlew test --info

# Run tests without reports (faster)
./gradlew test --rerun -PNoReports

# Run Gosu filter tests with comprehensive verification
./run-gosu-filter-tests.sh

# Run complete JaCoCo filter testing
./test-jacoco-filter.sh
```

### Development Workflow Commands

```bash
# Clean and rebuild
./gradlew clean build

# Check task dependencies
./gradlew build taskTree

# Capture build task graph (useful for understanding Gosu compilation)
./gradlew build taskTree
```

## Architecture

### Multi-Module Structure

```
junit.sample/
├── src/
│   ├── main/
│   │   ├── java/           # Java source code
│   │   └── gosu/           # Gosu source code (.gs, .gsx, .gst, .gsp)
│   └── test/
│       ├── java/           # Java test code
│       └── gosu/           # Gosu test code
├── jacoco-gosu-filter/     # Custom JaCoCo filter module
│   └── src/main/java/org/jacoco/gosu/
│       ├── GosuFilterAgent.java        # Java agent for runtime injection
│       ├── GosuFilterInjector.java     # Injects filter into JaCoCo
│       └── GosuNullSafetyFilter.java   # Filter implementation
└── scripts/                # Verification and testing scripts
```

### Key Components

1. **Main Application Code**
   - `src/main/gosu/enhancement/PolicyPeriodEnhancement.gsx` - Primary Gosu enhancement class
   - `src/main/gosu/entity/` - Entity classes in Gosu
   - `src/main/java/` - Supporting Java code

2. **Test Infrastructure**
   - `src/test/java/test/PolicyPeriodEnhancementTest.java` - Comprehensive test suite (10 tests)
   - MockWebServer for HTTP API testing
   - Mockito for service layer mocking

3. **JaCoCo Gosu Filter**
   - **GosuFilterAgent**: Java agent that must be loaded before JaCoCo agent
   - **GosuFilterInjector**: Dynamically injects Gosu filter into JaCoCo runtime
   - **GosuNullSafetyFilter**: Filters null-safe navigation patterns from coverage

### Filter Mechanism

The custom filter solves Gosu's null-safe navigation (`?.`) which generates unnecessary branches in JaCoCo coverage:

1. **Agent Loading**: `GosuFilterAgent` loads via `-javaagent:` JVM argument
2. **Runtime Injection**: `GosuFilterInjector` detects JaCoCo `Filters` class and injects the Gosu filter
3. **Pattern Detection**: `GosuNullSafetyFilter` identifies bytecode patterns for null-safe operations
4. **Branch Filtering**: Excludes artificial branches from coverage calculations

## Dependencies

### Core Dependencies
- **Java**: 11 (forced via toolchain)
- **Gosu**: `org.gosu-lang.gosu:gosu-core:+`
- **JUnit**: JUnit 5 (Jupiter) with JUnit 4 compatibility via junit-vintage-engine
- **JaCoCo**: Built-in Gradle plugin
- **Mockito**: `5.+` for mocking
- **AssertJ**: Fluent assertions
- **Manifold**: Required by Gosu language

### Testing Dependencies
- **MockWebServer**: `4.12.0` for HTTP API mocking
- **JUnit Pioneer**: Additional JUnit 5 extensions
- **Everit JSON Schema**: For contract testing

### HTTP Client Stack
- **OpenFeign**: Declarative HTTP client
- **OkHttpClient**: Default HTTP implementation
- **Jackson**: JSON serialization/deserialization

## Configuration

### Gradle Configuration
- **Java Toolchain**: Enforces Java 11 across environments
- **Gosu Source Sets**: Configured for both main and test source sets
- **JaCoCo Integration**: Custom test configuration with agent loading
- **Dynamic Agent Loading**: Enabled for Java 9+ compatibility

### Test Configuration
```groovy
test {
    // Load Gosu filter agent FIRST
    jvmArgs "-javaagent:${filterAgentPath}"

    // Enable dynamic agent loading for Java 9+
    jvmArgs "-XX:+EnableDynamicAgentLoading"

    useJUnitPlatform()
    testLogging {
        events("failed")
        showStandardStreams = true
    }
}
```

## Special Considerations

### Agent Loading Order
**Critical**: The Gosu filter agent MUST be loaded before the JaCoCo agent. The build.gradle enforces this through:
1. `dependsOn ':jacoco-gosu-filter:copyAgentJar'` dependency
2. Explicit jvmArgs configuration in test task

### Known Issues and Solutions

1. **Mockito + Manifold Conflicts**: Resolved by excluding transitive Manifold dependencies and explicitly declaring versions
2. **IDE JUnit Configuration**: May break due to agent loading; use command line for reliable test execution
3. **Locale Testing**: Use `set LANG=fr_FR.UTF8 && gradlew test --rerun` for locale-specific testing

### Performance Optimizations
- Use `--continuous` flag for rapid development cycles
- `-PNoReports` property skips report generation for faster test runs
- Parallel test execution configured by default

## Verification and Debugging

### Filter Operation Verification
```bash
# Check if filter logs appear in test output
./gradlew test 2>&1 | grep "\[Gosu"

# Expected log patterns:
# [GosuFilterAgent] STARTING GOSU FILTER AGENT
# [GosuFilterInjector] FILTER INJECTION SUCCESSFUL
# [GosuNullSafetyFilter] PATTERN 1 DETECTED
```

### Coverage Analysis
- **HTML Report**: `build/reports/jacoco/test/html/index.html`
- **CSV Data**: `build/reports/jacoco/test/jacocoTestReport.csv`
- **Expected Results**: PolicyPeriodEnhancement should show reduced branch counts (line 13: 2 branches instead of 4)

### Bytecode Verification
```bash
# Inspect compiled bytecode
javap -v -p build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class

# Count null-check patterns
javap -v build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class | grep -c "ifnonnull"
```

## Testing Strategy

### Test Categories
1. **Unit Tests**: JUnit 5 tests for individual methods
2. **Integration Tests**: MockWebServer for HTTP API testing
3. **Coverage Tests**: JaCoCo with custom Gosu filter
4. **Contract Tests**: JSON Schema validation for API contracts

### Key Test Files
- `PolicyPeriodEnhancementTest.java` - 10 comprehensive tests covering all enhancement methods
- Tests use both Mockito for service mocking and MockWebServer for API testing
- All tests verify both business logic and coverage accuracy

## Environment Setup

### Required Tools
- Java 11+ (managed by Gradle toolchain)
- Gradle 8+ (wrapper included)
- For local development: IntelliJ IDEA with Gosu plugin recommended

### Repository Structure
- Uses offline Maven repository for dependency isolation
- Custom Gradle helpers in `gradle/helpers.gradle`
- Comprehensive documentation in `docs/` directory

## Development Best Practices

1. **Always run tests via command line** to ensure proper agent loading
2. **Use `./gradlew test --continuous`** for rapid development cycles
3. **Check filter logs** when verifying coverage accuracy
4. **Run `./test-jacoco-filter.sh`** for comprehensive validation
5. **Review `docs/` directory** for detailed implementation guides and verification procedures

## Project Documentation

The `docs/` directory contains comprehensive documentation:
- `docs/README.md` - Complete documentation index and usage guide
- `docs/WORK_COMPLETED.md` - Executive summary of enhancements
- `docs/COMPREHENSIVE_SUMMARY.md` - Complete implementation details
- `docs/BYTECODE_VERIFICATION_GUIDE.md` - Technical bytecode analysis reference
- `docs/TEST_EXECUTION_REPORT.md` - Live test execution results and verification