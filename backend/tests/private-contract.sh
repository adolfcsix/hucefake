#!/usr/bin/env bash
set -euo pipefail
jar=target/huce-backend-1.0.0.jar
docker exec "$PG_CONTAINER" createdb -U fm05 huce_private_ci
temp=$(mktemp -d)
pid=''
trap 'if [ -n "$pid" ]; then kill "$pid" 2>/dev/null || true; fi; cat "$temp/server.log" 2>/dev/null || true; rm -rf "$temp"' EXIT
sql() { docker exec "$PG_CONTAINER" psql -U fm05 -d huce_private_ci -Atqc "$1"; }
java -jar "$jar" setup catalog ../config/local.template.json
test "$(sql 'SELECT count(*) FROM users')" = 0
test "$(sql 'SELECT count(*) FROM semesters')" = 0
test "$(sql 'SELECT count(*) FROM roles')" = 4
test "$(sql 'SELECT demo FROM fm05_runtime_mode')" = f
cat > "$temp/bad.json" <<'JSON'
{"format":"fm05-local","version":1,"units":[{"code":"ROLLBACK","name":"Rollback"}],"subjects":[{"code":"X","name":"Invalid","unitCode":"MISSING","credits":3}]}
JSON
if java -jar "$jar" setup catalog "$temp/bad.json"; then exit 1; fi
test "$(sql 'SELECT count(*) FROM organizational_units')" = 0
cat > "$temp/catalog.json" <<'JSON'
{"format":"fm05-local","version":1,"units":[{"code":"QA","name":"Khoa kiểm thử"}],"campuses":[{"code":"CS","name":"Cơ sở kiểm thử"}],"semesters":[{"yearCode":"2026-2027","code":"PRIVATE.1","startDate":"2026-08-31","endDate":"2026-12-26"}],"subjects":[{"code":"SUB","name":"Học phần kiểm thử","unitCode":"QA","credits":3}],"groups":[{"code":"GROUP","curriculumCode":"QA26","capacity":40}]}
JSON
java -jar "$jar" setup catalog "$temp/catalog.json"
java -jar "$jar" setup catalog "$temp/catalog.json"
test "$(sql 'SELECT count(*) FROM subjects')" = 1
java -jar "$jar" setup account --username private_staff --email staff@example.test --role STAFF --unit QA --campus CS
java -jar "$jar" setup account --username private_manager --email manager@example.test --role MANAGER
java -jar "$jar" setup account --username private_lecturer --email lecturer@example.test --role LECTURER --unit QA
java -jar "$jar" setup account --username private_student --email student@example.test --role STUDENT
if java -jar "$jar" setup account --username private_staff --email duplicate@example.test --role STUDENT; then exit 1; fi
if java -jar "$jar" setup account --username bad_staff --email bad@example.test --role STAFF --unit QA --campus MISSING; then exit 1; fi
test "$(sql 'SELECT count(*) FROM users')" = 4
test "$(sql 'SELECT count(*) FROM staff_profiles')" = 2
test "$(sql 'SELECT count(*) FROM staff_campuses')" = 1
test "$(sql "SELECT count(*) FROM users WHERE password_hash='PrivateTest@2026'")" = 0
if DEMO=true java -jar "$jar" setup catalog "$temp/catalog.json"; then exit 1; fi
if DB_URL=jdbc:postgresql://127.0.0.1:5432/huce_spring java -jar "$jar" setup catalog "$temp/catalog.json"; then exit 1; fi
HUCE_LOGIN_ATTEMPTS_PER_MINUTE=16 java -jar "$jar" > "$temp/server.log" 2>&1 &
pid=$!
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/health; then break; fi
  kill -0 "$pid"
  sleep 2
done
curl -fsS http://127.0.0.1:8080/health
HUCE_TEST_URL=http://127.0.0.1:8080 node tests/private-contract.mjs
kill "$pid"
wait "$pid" || true
pid=''
echo 'PASS: private CLI, empty database, four real accounts, rollback, no demo leakage, private API'

# Back up only the disposable private database; do not restore authenticated sessions.
test "$(sql 'SELECT demo FROM fm05_runtime_mode')" = f
docker exec "$PG_CONTAINER" pg_dump -U fm05 -d huce_private_ci -Fc --exclude-table-data=public.spring_session --exclude-table-data=public.spring_session_attributes > "$temp/private.dump"
test -s "$temp/private.dump"
docker exec "$PG_CONTAINER" createdb -U fm05 huce_restore_ci
docker exec -i "$PG_CONTAINER" pg_restore -U fm05 -d huce_restore_ci --exit-on-error --single-transaction --no-owner --no-privileges < "$temp/private.dump"
restored() { docker exec "$PG_CONTAINER" psql -U fm05 -d huce_restore_ci -Atqc "$1"; }
test "$(restored 'SELECT count(*) FROM spring_session')" = 0
test "$(restored 'SELECT count(*) FROM spring_session_attributes')" = 0
test "$(restored 'SELECT demo FROM fm05_runtime_mode')" = f
for table in users roles organizational_units staff_profiles staff_campuses campuses semesters subjects academic_groups course_sections section_assignments section_target_groups section_enrollments; do
  query="SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,E'\\n' ORDER BY to_jsonb(t)::text),'')) FROM $table t"
  test "$(sql "$query")" = "$(restored "$query")"
done
DB_URL=jdbc:postgresql://127.0.0.1:5432/huce_restore_ci java -jar "$jar" > "$temp/server.log" 2>&1 &
pid=$!
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8080/health; then break; fi
  kill -0 "$pid"
  sleep 2
done
curl -fsS http://127.0.0.1:8080/health
HUCE_TEST_URL=http://127.0.0.1:8080 node tests/restore-contract.mjs
kill "$pid"
wait "$pid" || true
pid=''
echo 'PASS: private PostgreSQL dump, transactional restore, data checksums, fresh authentication and post-restore API writes'
