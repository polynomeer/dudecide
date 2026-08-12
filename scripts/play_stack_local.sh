#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="${ROOT_DIR}/apps/web"
GRADLE_USER_HOME_DIR="${GRADLE_USER_HOME:-/private/tmp/dudecide-gradle-home}"
GRADLE_TMP_DIR="${DUDECIDE_GRADLE_TMP_DIR:-/private/tmp/dudecide-gradle-tmp}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
WEB_PORT="${WEB_PORT:-3000}"
START_INFRA=1
START_WORKER=0
PIDS=()

usage() {
  cat <<'EOF'
Usage: ./scripts/play_stack_local.sh [options]

Start Dudecide local stack for frontend and backend together.

Options:
  --skip-infra     Do not run docker compose up -d before starting services
  --with-worker    Also start the Redis worker module
  -h, --help       Show this help
EOF
}

while (($# > 0)); do
  case "$1" in
    --skip-infra)
      START_INFRA=0
      ;;
    --with-worker)
      START_WORKER=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

find_gradle_command() {
  if [[ -x "${ROOT_DIR}/gradlew" ]]; then
    printf '%s\n' "${ROOT_DIR}/gradlew"
    return
  fi

  if command -v gradle >/dev/null 2>&1; then
    printf '%s\n' "gradle"
    return
  fi

  if [[ -x "/opt/homebrew/bin/gradle" ]]; then
    printf '%s\n' "/opt/homebrew/bin/gradle"
    return
  fi

  echo "Gradle executable not found. Install Gradle or add a wrapper first." >&2
  exit 1
}

cleanup() {
  local exit_code=$?

  if ((${#PIDS[@]} > 0)); then
    echo
    echo "Stopping local stack..."
    for pid in "${PIDS[@]}"; do
      if kill -0 "${pid}" >/dev/null 2>&1; then
        kill "${pid}" >/dev/null 2>&1 || true
      fi
    done
    wait "${PIDS[@]}" 2>/dev/null || true
  fi

  exit "${exit_code}"
}

trap cleanup EXIT INT TERM

require_command docker
require_command npm

GRADLE_CMD="$(find_gradle_command)"

if [[ ! -d "${WEB_DIR}" ]]; then
  echo "Web app directory not found: ${WEB_DIR}" >&2
  exit 1
fi

if [[ ! -f "${WEB_DIR}/package.json" ]]; then
  echo "Web app package.json not found: ${WEB_DIR}/package.json" >&2
  exit 1
fi

if [[ ! -d "${WEB_DIR}/node_modules" ]]; then
  cat >&2 <<EOF
Missing frontend dependencies at ${WEB_DIR}/node_modules.
Run:
  cd ${WEB_DIR}
  npm install
EOF
  exit 1
fi

mkdir -p "${GRADLE_USER_HOME_DIR}" "${GRADLE_TMP_DIR}"

if ((START_INFRA)); then
  echo "Starting local infrastructure with docker compose..."
  (
    cd "${ROOT_DIR}"
    docker compose up -d
  )
fi

echo "Starting backend on http://localhost:${BACKEND_PORT} ..."
(
  cd "${ROOT_DIR}"
  GRADLE_USER_HOME="${GRADLE_USER_HOME_DIR}" \
  GRADLE_OPTS="-Djava.io.tmpdir=${GRADLE_TMP_DIR}" \
  "${GRADLE_CMD}" --no-daemon :backend:app:bootRun
) &
PIDS+=($!)

if ((START_WORKER)); then
  echo "Starting worker..."
  (
    cd "${ROOT_DIR}"
    GRADLE_USER_HOME="${GRADLE_USER_HOME_DIR}" \
    GRADLE_OPTS="-Djava.io.tmpdir=${GRADLE_TMP_DIR}" \
    "${GRADLE_CMD}" --no-daemon :backend:worker:bootRun
  ) &
  PIDS+=($!)
fi

echo "Starting frontend on http://localhost:${WEB_PORT} ..."
(
  cd "${WEB_DIR}"
  NEXT_PUBLIC_API_BASE_URL="http://localhost:${BACKEND_PORT}" \
  npm run dev -- --hostname 0.0.0.0 --port "${WEB_PORT}"
) &
PIDS+=($!)

echo
echo "Dudecide local stack is starting."
echo "Frontend: http://localhost:${WEB_PORT}"
echo "Backend:  http://localhost:${BACKEND_PORT}"
if ((START_WORKER)); then
  echo "Worker:   enabled"
fi
echo "Press Ctrl+C to stop all started processes."

wait -n "${PIDS[@]}"
