#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
umask 077
mkdir -p .runtime
if [ ! -s .runtime/data-key ]; then echo 'Missing existing encryption key at .runtime/data-key. Restore it before starting.' >&2; exit 1; fi
if [ -z "${SPORTFIT_GITHUB_TOKEN:-}" ]; then SPORTFIT_GITHUB_TOKEN=$(gh auth token); fi
printf '%s' "$SPORTFIT_GITHUB_TOKEN" > .runtime/github-token
chmod 600 .runtime/github-token .runtime/data-key
if ! docker info >/dev/null 2>&1; then docker desktop start; fi
if [ ! -s .runtime/resend-api-key ] || [ ! -s .runtime/email-from ]; then node scripts/configure-email.mjs; fi
docker compose up -d --build
exec node scripts/connect-pages.mjs
