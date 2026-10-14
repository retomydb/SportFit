#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home; fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if command -v mvn >/dev/null 2>&1; then MVN=mvn; else MVN=./.tools/apache-maven-3.9.11/bin/mvn; fi
"$MVN" -q -Dmaven.repo.local=.tools/repository package
exec java -jar target/sportfit-pro-1.0.0.jar
