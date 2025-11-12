#!/bin/bash
# Test various Ant-related environment variables

echo "Testing Ant environment variables..."
echo ""

# ANT_OPTS - Traditional Ant JVM options
export ANT_OPTS="-javaagent:/mnt/c/Users/KUCOLE/workspace/junit.sample/agents/gosu-filter-agent.jar"
echo "Set ANT_OPTS=${ANT_OPTS}"
echo ""

# Also set JAVA_TOOL_OPTIONS as fallback
export JAVA_TOOL_OPTIONS="-javaagent:/mnt/c/Users/KUCOLE/workspace/junit.sample/agents/gosu-filter-agent.jar"
echo "Set JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}"
echo ""

./gradlew jacocoTestReport --no-daemon --rerun-tasks 2>&1 | grep -E "\[GosuFilter|ANT_OPTS|JAVA_TOOL_OPTIONS" | head -30
