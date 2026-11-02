#!/usr/bin/env bash
# Checks a running stack the way a browser does: upload, wait, ask, then clean up.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3000}"
METRICS_URL="${METRICS_URL:-http://localhost:8080/actuator/prometheus}"
SAMPLE="$(dirname "$0")/../samples/employee-handbook.md"

json_field() {
  grep -o "\"$1\":\"[^\"]*\"" | head -n 1 | cut -d '"' -f 4
}

echo "Uploading the sample document"
upload=$(curl --fail-with-body -sS -H "Origin: $BASE_URL" -F "file=@$SAMPLE" "$BASE_URL/api/documents")
id=$(echo "$upload" | json_field id)

echo "Waiting for document $id"
status=""
for _ in $(seq 1 30); do
  status=$(curl --fail-with-body -sS "$BASE_URL/api/documents/$id" | json_field status)
  [ "$status" != "PROCESSING" ] && break
  sleep 1
done
[ "$status" = "READY" ] || { echo "Expected READY but got '$status'"; exit 1; }

echo "Asking a question"
answer=$(curl --fail-with-body -sS -H "Origin: $BASE_URL" -H "Content-Type: application/json" \
  -d '{"question":"How many days of annual leave do employees get?"}' "$BASE_URL/api/ask")
echo "$answer" | grep -q 'employee-handbook.md' || { echo "The answer had no source: $answer"; exit 1; }

echo "Checking metrics"
curl --fail-with-body -sS "$METRICS_URL" | grep -q 'askdocs_ask_duration_seconds_count'

echo "Cleaning up"
curl --fail-with-body -sS -X DELETE "$BASE_URL/api/documents/$id"

echo "Smoke test passed"
