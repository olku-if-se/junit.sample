#!/bin/bash
export JAVA_TOOL_OPTIONS="-javaagent:/mnt/c/Users/KUCOLE/workspace/junit.sample/agents/gosu-filter-agent.jar"
./gradlew clean test jacocoTestReport --no-daemon
