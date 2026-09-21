#!/usr/bin/env bash
set -Eeuo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
DESTINATION_URL="${DESTINATION_URL:-https://example.com}"
DEMO_API_KEY="${DEMO_API_KEY:-dev-key-not-a-secret}"
MVNW="${MVNW:-./mvnw}"
APP_LOG="${APP_LOG:-${TMPDIR:-/tmp}/url-shortener-demo.$$.log}"
APP_PID=""

cleanup() {
  if [[ -n "${APP_PID}" ]]; then
    kill -- "-${APP_PID}" 2>/dev/null || kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

json_string_value() {
  local key="$1"
  # The application returns compact, flat JSON for this demo; avoid requiring jq.
  sed -nE "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"([^\"]+)\".*/\1/p"
}

wait_for_health() {
  local health_response
  for _ in {1..60}; do
    if health_response="$(curl -fsS "${APP_URL}/actuator/health" 2>/dev/null)" \
      && grep -Fq '"status":"UP"' <<<"${health_response}"; then
      return 0
    fi

    if ! kill -0 "${APP_PID}" 2>/dev/null; then
      echo "Application exited before becoming healthy. Log: ${APP_LOG}" >&2
      cat "${APP_LOG}" >&2 || true
      return 1
    fi

    sleep 2
  done

  echo "Timed out waiting for ${APP_URL}/actuator/health. Log: ${APP_LOG}" >&2
  cat "${APP_LOG}" >&2 || true
  return 1
}

auth_header=()
if [[ -n "${DEMO_API_KEY}" ]]; then
  auth_header=(-H "X-API-Key: ${DEMO_API_KEY}")
fi

echo "Starting PostgreSQL with Docker Compose..."
docker compose up -d --wait postgres

echo "Starting application..."
set -m
"${MVNW}" -q spring-boot:run >"${APP_LOG}" 2>&1 &
APP_PID="$!"
set +m

echo "Waiting for application health..."
wait_for_health

echo "Creating link for ${DESTINATION_URL}..."
create_response="$(
  curl -fsS \
    -X POST "${APP_URL}/api/links" \
    -H "Content-Type: application/json" \
    "${auth_header[@]}" \
    -d "{\"destination\":\"${DESTINATION_URL}\"}"
)"
short_code="$(json_string_value shortCode <<<"${create_response}")"
short_url="$(json_string_value shortUrl <<<"${create_response}")"

if [[ -z "${short_code}" || -z "${short_url}" ]]; then
  echo "Create response did not include shortCode and shortUrl: ${create_response}" >&2
  exit 1
fi

echo "Created short code: ${short_code}"
echo "Following redirect: ${short_url}"
redirect_status="$(curl -fsSL -o /dev/null -w "%{http_code}" "${short_url}")"
echo "Redirect final HTTP status: ${redirect_status}"

if [[ "${redirect_status}" != "200" ]]; then
  echo "Expected final redirect target status 200, got ${redirect_status}" >&2
  exit 1
fi

echo "Stats:"
curl -fsS "${auth_header[@]}" "${APP_URL}/api/links/${short_code}/stats"
echo
