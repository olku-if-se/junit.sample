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
```

External Libraries should include:
- JUnit 5 (org.junit.jupiter:junit-jupiter*)
- JUnit Platform Engine (org.junit.platform:junit-platform*)
- Open Test4J (org.opentest4j:opentest4j) - There is no standard for testing on the JVM: the only common building block we have is java.lang.AssertionError.

Open Test4J can be easily replaced by:
- AssertJ (org.assertj:assertj-core) - AssertJ is a fluent assertion library for Java.
- Hamcrest (org.hamcrest:hamcrest) - Hamcrest is a library of matchers for building test expressions.

Activate JUnit 4 compatibility (migration).
Sample: https://github.com/junit-team/junit-examples/tree/main/junit-migration-gradle

References:
- https://junit.org/junit4/
- 