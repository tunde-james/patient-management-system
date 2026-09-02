#!/bin/sh
set -e

# Expand JAVA_OPTS and exec java directly as PID 1
# This ensures proper signal handling (SIGTERM) in Docker
exec java $JAVA_OPTS -jar app.jar