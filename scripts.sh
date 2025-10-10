curl -X POST http://localhost:9100/triage/123 \
  -H "Content-Type: application/json" \
  -d '{"incident":"Payment down"}

curl -H "Content-Type: application/json" "http://localhost:9100/triage/123/"

curl -H "Content-Type: application/json" "http://localhost:9100/triage/123/state"