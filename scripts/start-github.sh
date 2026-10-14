#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home; fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [ -z "${SPORTFIT_GITHUB_TOKEN:-}" ]; then SPORTFIT_GITHUB_TOKEN=$(gh auth token); export SPORTFIT_GITHUB_TOKEN; fi
if [ -z "${SPORTFIT_DATA_KEY:-}" ]; then
  if [ ! -f .runtime/data-key ]; then echo 'Missing data encryption key. Follow docs/GITHUB-DEPLOYMENT.md.' >&2; exit 1; fi
  SPORTFIT_DATA_KEY=$(cat .runtime/data-key); export SPORTFIT_DATA_KEY
fi
if [ -f .runtime/resend-api-key ]; then SPORTFIT_RESEND_API_KEY=$(cat .runtime/resend-api-key); export SPORTFIT_RESEND_API_KEY; fi
if [ -f .runtime/email-from ]; then SPORTFIT_EMAIL_FROM=$(cat .runtime/email-from); export SPORTFIT_EMAIL_FROM; fi
export SPRING_PROFILES_ACTIVE=github
export SPORTFIT_ALLOWED_ORIGINS="${SPORTFIT_ALLOWED_ORIGINS:-https://retomydb.github.io,http://localhost:8087,http://127.0.0.1:8087}"
exec java -jar target/sportfit-pro-1.0.0.jar --debug=false "$@"
