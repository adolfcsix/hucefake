#!/usr/bin/env bash
set -euo pipefail
test "${HUCE_BROWSER_CI:-}" = true
test -n "${FM05_SETUP_PASSWORD:-}"
export FM05_WEB_PORT=8082 FM05_DB_NAME=huce_private_browser
compose=(docker compose -f compose.share.yaml -f compose.private.yaml)
temp=$(mktemp -d)
trap '"${compose[@]}" logs --tail 80 || true; "${compose[@]}" down -v; rm -rf "$temp"' EXIT
chmod 755 "$temp"
cat > "$temp/base.json" <<'JSON'
{"format":"fm05-local","version":1,"units":[{"code":"QA","name":"Khoa kiểm thử"}],"campuses":[{"code":"CS","name":"Cơ sở kiểm thử"}]}
JSON
cat > "$temp/term.json" <<'JSON'
{"format":"fm05-local","version":1,"semesters":[{"yearCode":"2026-2027","code":"PRIVATE.1","startDate":"2026-08-31","endDate":"2026-12-26"}],"subjects":[{"code":"SUB","name":"Học phần kiểm thử","unitCode":"QA","credits":3}],"groups":[{"code":"GROUP","curriculumCode":"QA26","capacity":40}]}
JSON
chmod 644 "$temp/"*.json
"${compose[@]}" up -d --build --wait --wait-timeout 180
"${compose[@]}" run --rm -T -v "$temp:/config:ro" app setup catalog /config/base.json
"${compose[@]}" run --rm -T -e FM05_SETUP_PASSWORD app setup account --username private_staff --email staff@example.test --role STAFF --unit QA --campus CS
"${compose[@]}" run --rm -T -e FM05_SETUP_PASSWORD app setup account --username private_manager --email manager@example.test --role MANAGER
"${compose[@]}" run --rm -T -e FM05_SETUP_PASSWORD app setup account --username private_lecturer --email lecturer@example.test --role LECTURER --unit QA
"${compose[@]}" run --rm -T -e FM05_SETUP_PASSWORD app setup account --username private_student --email student@example.test --role STUDENT
export HUCE_TEST_URL=http://127.0.0.1:8082
HUCE_PRIVATE_PHASE=empty node frontend/e2e/private-browser.mjs
"${compose[@]}" run --rm -T -v "$temp:/config:ro" app setup catalog /config/term.json
HUCE_PRIVATE_PHASE=term node frontend/e2e/private-browser.mjs
