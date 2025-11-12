# JaCoCo Gosu Filter - Injection Options Analysis

**Date**: 2025-11-10
**Problem**: Cannot inject filter via Java agent because JaCoCo core loads in Gradle's isolated classloader
**Goal**: Find a way to add GosuNullSafetyFilter to JaCoCo's filter chain

---

## The Core Challenge

```java
// ClassAnalyzer constructor (line 65-71)
public ClassAnalyzer(final ClassCoverageImpl coverage,
        final boolean[] probes, final StringPool stringPool) {
    this.coverage = coverage;
    this.probes = probes;
    this.stringPool = stringPool;
    this.filter = Filters.all();  // ← Need to intercept this call
}
```

**Target**: Make `Filters.all()` return a filter chain that includes `GosuNullSafetyFilter`

---

## Option 1: Shadow JAR with Custom Filters Class

### Approach
Create a new JAR that contains a replacement `org.jacoco.core.internal.analysis.filter.Filters` class, then ensure it loads BEFORE JaCoCo core.

### Implementation

**Step 1**: Create custom Filters class
```java
package org.jacoco.core.internal.analysis.filter;

// Import all original JaCoCo filters
import org.jacoco.core.internal.analysis.filter.*;

public class Filters {
    // Copy all original methods from JaCoCo's Filters class
    public static IFilter all() {
        final IFilter allCommonFilters = allCommonFilters();
        final IFilter allKotlinFilters = allKotlinFilters();
        final IFilter allNonKotlinFilters = allNonKotlinFilters();

        // ADD: Create Gosu filter
        final IFilter gosuFilter = createGosuFilter();

        return new IFilter() {
            public void filter(final MethodNode methodNode,
                    final IFilterContext context, final IFilterOutput output) {
                allCommonFilters.filter(methodNode, context, output);
                if (isKotlinClass(context)) {
                    allKotlinFilters.filter(methodNode, context, output);
                } else {
                    allNonKotlinFilters.filter(methodNode, context, output);
                }
                // ADD: Apply Gosu filter for all classes
                gosuFilter.filter(methodNode, context, output);
            }
        };
    }

    private static IFilter createGosuFilter() {
        // Load our GosuNullSafetyFilter
        // This is the tricky part - need to bridge between implementations
        return new GosuNullSafetyFilterAdapter();
    }

    // Copy all other methods from original Filters class...
}
```

**Step 2**: Build shadow JAR
```gradle
// jacoco-gosu-filter/build.gradle
plugins {
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

shadowJar {
    archiveClassifier = ''

    // Include JaCoCo core to get all filter classes
    configurations = [project.configurations.compileOnly]

    // Relocate JaCoCo's original Filters to a backup location
    relocate 'org.jacoco.core.internal.analysis.filter.Filters',
             'org.jacoco.core.internal.analysis.filter.FiltersOriginal'

    // Our custom Filters class stays at original location
}
```

**Step 3**: Add to Gradle's JaCoCo classpath
```gradle
// build.gradle
dependencies {
    // Add BEFORE JaCoCo core so it loads first
    jacocoAnt files('agents/gosu-filter-shadow.jar')
    jacocoAnt 'org.jacoco:org.jacoco.ant:0.8.13'
}
```

### Pros
- ✅ Clean approach (no bytecode manipulation at runtime)
- ✅ Works with standard Gradle JaCoCo plugin
- ✅ Filter applies during report generation

### Cons
- ❌ Must duplicate entire Filters class (maintenance burden)
- ❌ Tight coupling to JaCoCo version
- ❌ Need to update when JaCoCo updates
- ❌ Complex shadow JAR configuration

### Feasibility: ⭐⭐⭐ (3/5) - Workable but high maintenance

---

## Option 2: Java Proxy / Dynamic Proxy

### Approach
Use Java's dynamic proxy to wrap the original `Filters.all()` return value and inject our filter.

### Implementation

**Problem**: `Filters.all()` is a **static method**, and Java proxies only work for:
- Interface implementations (via `Proxy.newProxyInstance`)
- Instance methods (via `Proxy`)

**Cannot proxy**:
- Static methods
- Final classes
- Private methods

**Verdict**: ❌ **Not possible** - Cannot use `java.lang.reflect.Proxy` for static methods

### Alternative: ByteBuddy Agent with Proxy Pattern

```java
public class FilterProxyAgent {
    public static void premain(String args, Instrumentation inst) {
        new AgentBuilder.Default()
            .type(named("org.jacoco.core.internal.analysis.filter.Filters"))
            .transform((builder, typeDescription, classLoader, module) ->
                builder.method(named("all"))
                    .intercept(MethodDelegation.to(FilterInterceptor.class))
            )
            .installOn(inst);
    }
}

public class FilterInterceptor {
    @RuntimeType
    public static IFilter intercept(@SuperCall Callable<IFilter> zuper) {
        try {
            IFilter original = zuper.call();  // Call original Filters.all()
            IFilter gosuFilter = new GosuNullSafetyFilter();

            // Return composite that calls both
            return new CompositeFilter(original, gosuFilter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Pros
- ✅ No need to duplicate Filters class
- ✅ Clean delegation pattern
- ✅ Can wrap existing implementation

### Cons
- ❌ Still requires agent to load in correct classloader
- ❌ Same classloader isolation problem
- ❌ ByteBuddy adds complexity

### Feasibility: ⭐⭐ (2/5) - Same classloader problem

---

## Option 3: Gradle BuildScript Classloader Injection

### Approach
Load the agent into Gradle's buildscript classloader BEFORE JaCoCo plugin initializes.

### Implementation

```gradle
// build.gradle
buildscript {
    dependencies {
        classpath files('agents/gosu-filter-agent.jar')
    }

    // Load agent into buildscript JVM
    configurations.classpath.each {
        if (it.name.contains('gosu-filter-agent')) {
            // Attach agent to current JVM
            def agentPath = it.absolutePath
            try {
                def vm = java.lang.management.ManagementFactory.getPlatformMBeanServer()
                def vmClass = Class.forName("com.sun.tools.attach.VirtualMachine")
                def currentPid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]
                def attachedVM = vmClass.attach(currentPid)
                attachedVM.loadAgent(agentPath)
                attachedVM.detach()
                println "✓ Agent attached to buildscript classloader"
            } catch (Exception e) {
                println "⚠ Could not attach agent: ${e.message}"
            }
        }
    }
}

plugins {
    id 'java'
    id 'jacoco'  // Now loads AFTER agent is attached
}
```

### Pros
- ✅ Agent loads in Gradle's JVM
- ✅ Can potentially intercept plugin classloader

### Cons
- ❌ Requires JDK tools.jar (not available in all environments)
- ❌ Gradle may have already initialized some classloaders
- ❌ Timing is tricky
- ❌ May not work with Gradle daemon

### Feasibility: ⭐⭐⭐ (3/5) - Worth trying but risky

---

## Option 4: Classpath Manipulation (Prepend JAR)

### Approach
Create a JAR with ONLY the custom Filters class, then prepend it to JaCoCo's classpath so it loads first.

### Implementation

**Step 1**: Create minimal JAR with just Filters class
```
gosu-filter-override.jar
└── org/jacoco/core/internal/analysis/filter/
    └── Filters.class  (our custom implementation)
```

**Step 2**: Ensure it loads BEFORE JaCoCo core
```gradle
dependencies {
    // Order matters! First JAR wins for class loading
    jacocoAnt files('agents/gosu-filter-override.jar')
    jacocoAnt 'org.jacoco:org.jacoco.ant:0.8.13'  // Original Filters ignored
}
```

**Step 3**: Custom Filters delegates to original
```java
package org.jacoco.core.internal.analysis.filter;

public class Filters {
    // Use reflection to access original Filters from JaCoCo core
    private static Class<?> originalFiltersClass;

    static {
        try {
            // Load original from parent classloader
            originalFiltersClass = Class.forName(
                "org.jacoco.core.internal.analysis.filter.Filters",
                true,
                Filters.class.getClassLoader().getParent()
            );
        } catch (Exception e) {
            throw new RuntimeException("Cannot load original Filters", e);
        }
    }

    public static IFilter all() {
        // Call original all() via reflection
        IFilter original = (IFilter) originalFiltersClass
            .getMethod("all")
            .invoke(null);

        // Wrap with our filter
        return new CompositeFilter(original, new GosuNullSafetyFilter());
    }

    // Delegate all other static methods to original...
}
```

### Pros
- ✅ Minimal JAR (just one class)
- ✅ Delegates to original (less maintenance)
- ✅ Works with classpath ordering

### Cons
- ❌ Reflection complexity
- ❌ Fragile (depends on classloader hierarchy)
- ❌ May not work if Gradle isolates classloaders

### Feasibility: ⭐⭐⭐⭐ (4/5) - Most promising!

---

## Option 5: Gradle Plugin with Custom JaCoCo Task

### Approach
Create a Gradle plugin that replaces the standard `jacocoTestReport` task with one that uses our modified JaCoCo.

### Implementation

**Step 1**: Create Gradle plugin
```groovy
// buildSrc/src/main/groovy/GosuFilterPlugin.groovy
class GosuFilterPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.apply('jacoco')

        // Replace jacocoTestReport task
        project.tasks.named('jacocoTestReport') {
            // Override to use custom Analyzer with our filter
            doFirst {
                // Inject our filter into classpath
                ant.taskdef(
                    name: 'jacocoReport',
                    classname: 'org.jacoco.ant.ReportTask',
                    classpath: project.configurations.jacocoAnt.asPath +
                               ':' + project.file('agents/gosu-filter-override.jar')
                )
            }
        }
    }
}
```

**Step 2**: Apply plugin
```gradle
plugins {
    id 'gosu-filter'  // Our custom plugin
}
```

### Pros
- ✅ Full control over JaCoCo execution
- ✅ Can inject custom filters
- ✅ Gradle-native approach

### Cons
- ❌ Complex plugin development
- ❌ Must maintain compatibility with Gradle JaCoCo plugin
- ❌ Still need custom Filters class

### Feasibility: ⭐⭐⭐ (3/5) - Powerful but complex

---

## Option 6: Fork JaCoCo and Add Filter

### Approach
Fork the JaCoCo project, add GosuNullSafetyFilter to the Filters class, publish as custom artifact.

### Implementation

**Step 1**: Fork JaCoCo
```bash
git clone https://github.com/jacoco/jacoco.git
cd jacoco
```

**Step 2**: Modify Filters.java
```java
// org.jacoco.core/src/org/jacoco/core/internal/analysis/filter/Filters.java

private static IFilter allNonKotlinFilters() {
    return new FilterSet(
        new SyntheticFilter(),
        new GosuNullSafetyFilter()  // ADD HERE
    );
}
```

**Step 3**: Build and publish
```bash
mvn clean install
mvn deploy -Drepository=custom-repo
```

**Step 4**: Use custom JaCoCo
```gradle
dependencies {
    jacocoAnt 'com.custom:org.jacoco.ant:0.8.13-gosu'
}
```

### Pros
- ✅ Clean integration
- ✅ No classloader hacks
- ✅ Full control

### Cons
- ❌ Must maintain fork
- ❌ Must merge upstream updates
- ❌ Team must use custom artifact

### Feasibility: ⭐⭐⭐⭐⭐ (5/5) - Most reliable, but high maintenance

---

## Recommended Approach: Option 4 (Classpath Prepend)

### Why This is Best

1. **Minimal code duplication** - Only Filters class, delegates rest
2. **Works with standard Gradle** - No plugin development needed
3. **Classloader-friendly** - Uses natural Java classloading order
4. **Testable** - Can verify with simple classpath tests

### Implementation Plan

```
Step 1: Create gosu-filter-override module
├── src/main/java/
│   └── org/jacoco/core/internal/analysis/filter/
│       ├── Filters.java           (custom, delegates to original)
│       └── GosuNullSafetyFilter.java  (implements IFilter)

Step 2: Build override JAR
./gradlew :gosu-filter-override:jar

Step 3: Configure Gradle
dependencies {
    jacocoAnt files('build/gosu-filter-override.jar')  // First!
    jacocoAnt 'org.jacoco:org.jacoco.ant:0.8.13'      // Second
}

Step 4: Test
./gradlew clean test jacocoTestReport
# Check if Gosu filter applies
```

### Fallback: Option 6 (Fork JaCoCo)

If classpath prepending doesn't work due to Gradle's classloader isolation, forking JaCoCo is the nuclear option that **will definitely work**.

---

## Quick Comparison Table

| Option | Complexity | Maintenance | Reliability | Feasibility |
|--------|-----------|-------------|-------------|-------------|
| 1. Shadow JAR | High | High | Medium | 3/5 |
| 2. Proxy | Medium | Low | Low | 2/5 |
| 3. BuildScript Agent | Medium | Medium | Medium | 3/5 |
| **4. Classpath Prepend** | **Low** | **Low** | **High** | **4/5** ⭐ |
| 5. Gradle Plugin | High | High | High | 3/5 |
| 6. Fork JaCoCo | Medium | High | Very High | 5/5 |

---

## Next Steps

**Recommended**: Implement Option 4 (Classpath Prepend)

1. Create `gosu-filter-override` module
2. Implement custom `Filters` class that delegates to original
3. Add to `jacocoAnt` configuration BEFORE JaCoCo
4. Test and verify

**If Option 4 fails**: Fall back to Option 6 (Fork JaCoCo)

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
