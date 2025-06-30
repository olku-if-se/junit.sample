## Test Driven Development In Java, Junit and Mockito

### Step 1: Create a new Java Project, IDE defaults

- Choose GRADLE as the build tool.

### Step 2: Upgrade the Gradle Wrapper

```bash
# ref: https://docs.gradle.org/current/userguide/gradle_wrapper.html
gradlew wrapper --gradle-version latest
```

### Step 3: Run All Tests Configuration in IDE

- Create JUnit execution configuration in your IDE.
- Create source code package/dirs: `org.ifit`

```bash
# execute from command line
# -t, --continuous - Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change.

gradlew test --tests org.ifit.* --info --continuous 

# simplest way
gradlew test -t
```

External Libraries should include:

- JUnit 5 (org.junit.jupiter:junit-jupiter*)
- JUnit Platform Engine (org.junit.platform:junit-platform*)
- Open Test4J (org.opentest4j:opentest4j) - There is no standard for testing on the JVM: the only common building block
  we have is java.lang.AssertionError.

Open Test4J can be easily replaced by:

- AssertJ (org.assertj:assertj-core) - AssertJ is a fluent assertion library for Java.
- Hamcrest (org.hamcrest:hamcrest) - Hamcrest is a library of matchers for building test expressions.

Activate JUnit 4 compatibility (migration).
Sample: https://github.com/junit-team/junit-examples/tree/main/junit-migration-gradle

References:

- https://junit.org/junit4/

### Step 4: Enable JaCoCo Code Coverage

- Activate JaCoCo plugin in your `build.gradle` file:
- Make JaCoCo report generation task depend on the test task.

ref: https://docs.gradle.org/current/userguide/jacoco_plugin.html

### Step 5: Optimize reporting and execution speed

https://docs.gradle.org/current/userguide/java_testing.html#test_reporting

```bash
# Run tests with no reports (Custom Property)
gradlew test --rerun -PNoReports
```

### Step 5: Activate Mocks

- Add Mockito dependency to your `build.gradle` file, ref: https://site.mockito.org/
- Alternatives:
    - EasyMock (org.easymock:easymock), ref: https://easymock.org/
    - PowerMock (org.powermock:powermock-module-junit4) - PowerMock only if absolutely necessary for unmockable legacy
      code, Consider refactoring instead of using PowerMock.
- REST API mocking libraries:
    - WireMock, ref: http://wiremock.org/
    - MockWebServer (OkHttp) - https://github.com/square/okhttp/tree/master/mockwebserver

### Hidden Troubles

```shell
# that will force different locale for the java application!
set LANG=fr_FR.UTF8 && gradlew test --rerun

# LANG, LC_ALL - used by JVM to determine the locale for formatting messages
```

### Step 6: Activate GOSU language support

```bash
# capture the build task graph (compileGosu, compileTestGosu)
gradlew build taskTree
```

Known issues:

- JUnit IDE configuration stop working for Mockito tests (java.lang.Error: Circular loading of installed providers
  detected).;

https://plugins.gradle.org/plugin/org.gosu-lang.gosu
https://github.com/gosu-lang/gradle-gosu-plugin

### Step 7: Resolve Mockito and Manifold issues

- We can solve it by forcing the Gradle to exclude transitive dependencies from Gosu Lang, and by including the specific
  version by hands.

```groovy
dependencies {
    // Gosu Language dependencies
    implementation('org.gosu-lang.gosu:gosu-core:+') {
        exclude group: 'systems.manifold'
    }
    testImplementation('org.gosu-lang.gosu:gosu-test:+') {
        exclude group: 'systems.manifold'
    }

    // Mockito dependencies
    /* ... */

    // Manifold - is Gosu Core (latest: 2025.1.22, used: 2024.1.38)
    implementation 'systems.manifold:manifold:+'
}
```

How it resolves the issue?

1. Uses the latest Manifold library;
2. Forces IDE to load Manifold library after Mockito; (order of including the libraries in the build.gradle file matters
   for IntelliJ IDE!)
