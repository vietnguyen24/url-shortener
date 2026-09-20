#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

BIN_DIR="${TMP_DIR}/bin"
LOG_FILE="${TMP_DIR}/calls.log"
mkdir -p "${BIN_DIR}"
: > "${LOG_FILE}"

cat > "${BIN_DIR}/docker" <<'STUB'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "${DEMO_TEST_LOG}"
if [ "$1 $2 $3 $4" = "compose up -d --wait" ] && [ "${5:-}" = "postgres" ]; then
  exit 0
fi
printf 'unexpected docker args: %s\n' "$*" >&2
exit 1
STUB

cat > "${BIN_DIR}/curl" <<'STUB'
#!/usr/bin/env bash
printf 'curl %s\n' "$*" >> "${DEMO_TEST_LOG}"
args=" $* "
case "$args" in
  *'/actuator/health '*|*'/actuator/health')
    printf '{"status":"UP"}'
    ;;
  *' -X POST '*'/api/links '*|*' -X POST '*'/api/links')
    printf '{"shortCode":"abc1234","shortUrl":"http://localhost:18080/abc1234","destination":"https://example.com"}'
    ;;
  *' -w %{http_code} '*'/abc1234 '*|*' -w %{http_code} '*'/abc1234')
    printf '%s' "${DEMO_TEST_REDIRECT_STATUS:-200}"
    ;;
  *'/api/links/abc1234/stats '*|*'/api/links/abc1234/stats')
    printf '{"shortCode":"abc1234","totalClicks":1,"clicksByDay":{"2026-09-20":1},"referrers":{},"userAgents":{}}'
    ;;
  *)
    printf 'unexpected curl args: %s\n' "$*" >&2
    exit 1
    ;;
esac
STUB

cat > "${BIN_DIR}/mvnw" <<'STUB'
#!/usr/bin/env bash
printf 'mvnw %s\n' "$*" >> "${DEMO_TEST_LOG}"
trap 'printf "mvnw terminated\n" >> "${DEMO_TEST_LOG}"; exit 0' TERM INT
while true; do sleep 1 & wait "$!"; done
STUB

chmod +x "${BIN_DIR}/docker" "${BIN_DIR}/curl" "${BIN_DIR}/mvnw"
OUTPUT_FILE="${TMP_DIR}/output.txt"
(
  cd "${ROOT_DIR}"
  PATH="${BIN_DIR}:${PATH}" \
  DEMO_TEST_LOG="${LOG_FILE}" \
  MVNW="mvnw" \
  APP_URL="http://localhost:18080" \
  DESTINATION_URL="https://example.com" \
  make demo
) > "${OUTPUT_FILE}"

grep -Fq 'Starting PostgreSQL with Docker Compose...' "${OUTPUT_FILE}"
grep -Fq 'Waiting for application health...' "${OUTPUT_FILE}"
grep -Fq 'Created short code: abc1234' "${OUTPUT_FILE}"
grep -Fq 'Following redirect: http://localhost:18080/abc1234' "${OUTPUT_FILE}"
grep -Fq 'Redirect final HTTP status: 200' "${OUTPUT_FILE}"
grep -Fq 'Stats:' "${OUTPUT_FILE}"
grep -Fq '"totalClicks":1' "${OUTPUT_FILE}"

grep -Fq 'docker compose up -d --wait postgres' "${LOG_FILE}"
grep -Fq 'mvnw -q spring-boot:run' "${LOG_FILE}"
grep -Fq 'curl -fsS http://localhost:18080/actuator/health' "${LOG_FILE}"
grep -Fq 'curl -fsS -X POST http://localhost:18080/api/links' "${LOG_FILE}"
grep -Fq 'curl -fsSL -o /dev/null -w %{http_code} http://localhost:18080/abc1234' "${LOG_FILE}"
grep -Fq 'api/links/abc1234/stats' "${LOG_FILE}"
grep -Fq 'mvnw terminated' "${LOG_FILE}"

: > "${LOG_FILE}"
FAILURE_OUTPUT="${TMP_DIR}/failure-output.txt"
set +e
(
  cd "${ROOT_DIR}"
  PATH="${BIN_DIR}:${PATH}" \
  DEMO_TEST_LOG="${LOG_FILE}" \
  DEMO_TEST_REDIRECT_STATUS="503" \
  MVNW="mvnw" \
  APP_URL="http://localhost:18080" \
  DESTINATION_URL="https://example.com" \
  make demo
) >"${FAILURE_OUTPUT}" 2>&1
failure_status="$?"
set -e

if [ "${failure_status}" -eq 0 ]; then
  echo "Expected make demo to fail for a non-200 redirect status" >&2
  exit 1
fi

grep -Fq 'Redirect final HTTP status: 503' "${FAILURE_OUTPUT}"
grep -Fq 'Expected final redirect target status 200, got 503' "${FAILURE_OUTPUT}"
grep -Fq 'mvnw terminated' "${LOG_FILE}"
