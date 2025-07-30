# Roadmap

- [x] Simple Java Junit 5 Activated Project, Gradle build system
- [x] Enable Junit 4 Compatibility
- [x] Optimize Gradle and IDE execution for TDD testing
- [x] Add Mockito for Mocking the Dependencies in Unit Tests
- [x] Cleanup Noise in the Project, Print Test Results in Console (Execution Time, Success/Failure)
- [x] Provide Different Unit Tests Examples that covers common use cases
- [x] Resolve the Issue with Mockito and Manifold (core lib of the GOSU lang)
- [x] Provide REST API Client Example with e2e Testing
    - [x] Analyse PolicyCenter Project dependencies, identify what is used for REST API Client
    - [x] What is used for JSON parsing?
    - [x] Find any REST API server for testing purposes (with Swagger/OpenAPI definition)
    - [x] Identify what can be used for Mocking REST API, e2e testing
- [x] Provide the simplest version of the contact testing

## Dependencies

- Gosu (latest: 1.18.6):
    - Manifold (latest: 2025.1.25, used 2024.1.38)

- Gosu Test (latest: 1.18.6):
    - https://github.com/smallrye/jandex, latest: 3.4.0: jandex - Jandex is a space efficient Java class file indexer
      and offline reflection library. (https://mvnrepository.com/artifact/org.jboss/jandex, latest 3.3.1 )
    - https://smallrye.io/jandex/jandex/3.4.0/index.html

- Mockito, ByteBuddy:
    - The Byte Buddy agent offers convenience for attaching an agent to the local or a remote VM.
    - Byte Buddy is a Java library for creating Java classes at run time. This artifact is a build of Byte Buddy with
      all ASM dependencies repackaged into its own name space.