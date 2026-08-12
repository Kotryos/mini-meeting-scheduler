#!/usr/bin/env bash
# A walk through mini-meeting-scheduler. Alice and Bob arrange a standup; Carol misses out.
#
# Starts from an empty database every time, so the ids and responses below are always the
# same. That means it DISCARDS whatever is currently in the local Postgres volume.
set -e

API=http://localhost:8080
PACE=0.4   # a beat between steps, so the run reads rather than flashes past

ALICE="X-API-Key: alice-demo-key"
BOB="X-API-Key: bob-demo-key"
CAROL="X-API-Key: carol-demo-key"
ADMIN="X-API-Key: admin-demo-key"
JSON="Content-Type: application/json"

if [ -t 1 ]; then
  BOLD=$'\033[1m'; DIM=$'\033[2m'; GREEN=$'\033[32m'; RED=$'\033[31m'; RESET=$'\033[0m'
else
  BOLD=""; DIM=""; GREEN=""; RED=""; RESET=""
fi

n=0
failures=0
expected=""

act() { echo ""; echo "${BOLD}── $1 ${RESET}"; sleep "$PACE"; }

step() {
  n=$((n + 1))
  expected=$2
  local trailer="$2"
  [ -n "${3:-}" ] && trailer="$2 · $3"
  printf "\n%s%2d.%s %-52s %sexpect %s%s\n" "$BOLD" "$n" "$RESET" "$1" "$DIM" "$trailer" "$RESET"
}

verdict() {
  if [ "$1" = "$expected" ]; then
    printf "    %s✓ %s%s\n" "$GREEN" "$1" "$RESET"
  else
    printf "    %s✗ %s, expected %s%s\n" "$RED" "$1" "$expected" "$RESET"
    failures=$((failures + 1))
  fi
}

code() {
  verdict "$(curl -s -o /dev/null -w '%{http_code}' "$@")"
  sleep "$PACE"
}

body() {
  local response status payload
  response=$(curl -s -w $'\n%{http_code}' "$@")
  status=${response##*$'\n'}
  payload=${response%$'\n'*}

  verdict "$status"
  if [ -n "$payload" ]; then
    echo "      $payload"
  fi
  sleep "$PACE"
}

echo "${BOLD}Starting a clean stack...${RESET}"
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d --build >/dev/null

printf "Waiting for health"
for _ in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' $API/actuator/health)" = "200" ]; then
    echo " up."
    break
  fi
  printf "."
  sleep 2
done

act "ACT 1 — getting in"

step "health needs no key" 200
code $API/actuator/health

step "no key at all" 401
code "$API/api/v1/slots?from=2026-12-01T00:00:00Z&to=2026-12-02T00:00:00Z"

step "a key nobody issued" 401
code -H "X-API-Key: not-a-real-key" "$API/api/v1/slots?from=2026-12-01T00:00:00Z&to=2026-12-02T00:00:00Z"

step "a user key on an admin endpoint" 403
code -H "$ALICE" $API/actuator/info

step "the admin key on the same endpoint" 200
code -H "$ADMIN" $API/actuator/info

act "ACT 2 — Alice publishes her availability"

step "09:00-12:00 becomes three hourly slots" 201 "3 slots"
body -X POST $API/api/v1/slots -H "$ALICE" -H "$JSON" \
  -d '{"from":"2026-12-01T09:00:00Z","to":"2026-12-01T12:00:00Z"}'

step "a range inside a single hour covers nothing" 400
body -X POST $API/api/v1/slots -H "$ALICE" -H "$JSON" \
  -d '{"from":"2026-12-01T13:15:00Z","to":"2026-12-01T13:45:00Z"}'

step "a range ending before it starts" 400
body -X POST $API/api/v1/slots -H "$ALICE" -H "$JSON" \
  -d '{"from":"2026-12-01T15:00:00Z","to":"2026-12-01T14:00:00Z"}'

step "overlapping an hour she already published" 409
body -X POST $API/api/v1/slots -H "$ALICE" -H "$JSON" \
  -d '{"from":"2026-12-01T11:00:00Z","to":"2026-12-01T13:00:00Z"}'

step "the hour after her last one" 201 "1 slot"
body -X POST $API/api/v1/slots -H "$ALICE" -H "$JSON" \
  -d '{"from":"2026-12-01T13:00:00Z","to":"2026-12-01T14:00:00Z"}'

step "Bob publishes 09:00-11:00" 201 "2 slots"
body -X POST $API/api/v1/slots -H "$BOB" -H "$JSON" \
  -d '{"from":"2026-12-01T09:00:00Z","to":"2026-12-01T11:00:00Z"}'

act "ACT 3 — reading a calendar"

step "Alice's whole day" 200 "4 slots, all FREE"
body -H "$ALICE" "$API/api/v1/slots?from=2026-12-01T00:00:00Z&to=2026-12-02T00:00:00Z"

step "she blocks 13:00 by hand" 204
code -X PATCH $API/api/v1/slots/4 -H "$ALICE" -H "$JSON" -d '{"status":"BUSY"}'

step "only her free time" 200 "09, 10, 11"
body -H "$ALICE" "$API/api/v1/slots?from=2026-12-01T00:00:00Z&to=2026-12-02T00:00:00Z&status=FREE"

step "only her busy time" 200 "13 only"
body -H "$ALICE" "$API/api/v1/slots?from=2026-12-01T00:00:00Z&to=2026-12-02T00:00:00Z&status=BUSY"

step "the summary merges neighbours, a gap splits them" 200 "2 blocks"
body -H "$ALICE" "$API/api/v1/slots/summary?from=2026-12-01T00:00:00Z&to=2026-12-02T00:00:00Z"

step "a status that does not exist" 400
code -H "$ALICE" "$API/api/v1/slots?from=2026-12-01T00:00:00Z&to=2026-12-02T00:00:00Z&status=MAYBE"

step "Alice touching Bob's slot" 404 "not 403"
code -X PATCH $API/api/v1/slots/5 -H "$ALICE" -H "$JSON" -d '{"status":"BUSY"}'

act "ACT 4 — the standup"

step "Alice books 09:00 with Bob" 201 "she is a participant too"
body -X POST $API/api/v1/meetings -H "$ALICE" -H "$JSON" \
  -d '{"title":"Standup","description":"Daily sync","startAt":"2026-12-01T09:00:00Z","participantIds":[2]}'

step "Alice's meetings" 200 "the standup"
body -H "$ALICE" $API/api/v1/meetings

step "Bob's meetings, though he booked nothing" 200 "the same one"
body -H "$BOB" $API/api/v1/meetings

step "Carol's meetings" 200 "none"
body -H "$CAROL" $API/api/v1/meetings

step "Carol reading it directly" 404
code -H "$CAROL" $API/api/v1/meetings/1

step "Alice's 09:00 now" 200 "BUSY"
body -H "$ALICE" "$API/api/v1/slots?from=2026-12-01T09:00:00Z&to=2026-12-01T10:00:00Z"

step "freeing a slot the meeting holds" 409
body -X PATCH $API/api/v1/slots/1 -H "$ALICE" -H "$JSON" -d '{"status":"FREE"}'

step "deleting that slot" 409
body -X DELETE $API/api/v1/slots/1 -H "$ALICE"

step "booking the same hour twice" 409
code -X POST $API/api/v1/meetings -H "$ALICE" -H "$JSON" \
  -d '{"title":"Clash","startAt":"2026-12-01T09:00:00Z","participantIds":[2]}'

step "inviting Carol, who published nothing" 409
body -X POST $API/api/v1/meetings -H "$ALICE" -H "$JSON" \
  -d '{"title":"With Carol","startAt":"2026-12-01T10:00:00Z","participantIds":[3]}'

step "a meeting starting at 10:30" 400
body -X POST $API/api/v1/meetings -H "$ALICE" -H "$JSON" \
  -d '{"title":"Half past","startAt":"2026-12-01T10:30:00Z","participantIds":[2]}'

step "a meeting with a blank title" 400
code -X POST $API/api/v1/meetings -H "$ALICE" -H "$JSON" \
  -d '{"title":"   ","startAt":"2026-12-01T10:00:00Z","participantIds":[2]}'

step "an hour Alice never published" 409
code -X POST $API/api/v1/meetings -H "$ALICE" -H "$JSON" \
  -d '{"title":"Midnight","startAt":"2026-12-01T03:00:00Z"}'

act "ACT 5 — calling it off"

step "Bob is not the organiser" 404
code -X DELETE $API/api/v1/meetings/1 -H "$BOB"

step "Alice cancels" 204
code -X DELETE $API/api/v1/meetings/1 -H "$ALICE"

step "her 09:00 again" 200 "FREE"
body -H "$ALICE" "$API/api/v1/slots?from=2026-12-01T09:00:00Z&to=2026-12-01T10:00:00Z"

step "the cancelled meeting" 404
code -H "$ALICE" $API/api/v1/meetings/1

step "rebooking under a new name (this is how you edit)" 201 "a new id"
body -X POST $API/api/v1/meetings -H "$ALICE" -H "$JSON" \
  -d '{"title":"Standup, renamed","startAt":"2026-12-01T09:00:00Z","participantIds":[2]}'

act "ACT 6 — two people, one hour"

step "eight requests race for 10:00" 201 "exactly one wins"
races=$(mktemp -d)
for i in $(seq 1 8); do
  (
    curl -s -o /dev/null -w '%{http_code}\n' -X POST $API/api/v1/meetings \
      -H "$ALICE" -H "$JSON" \
      -d '{"title":"Race","startAt":"2026-12-01T10:00:00Z","participantIds":[2]}' >"$races/$i"
  ) &
done
wait

won=$(cat "$races"/* | tr -d '\r' | grep -c '^201$' || true)
lost=$(cat "$races"/* | tr -d '\r' | grep -c '^409$' || true)
rm -rf "$races"
if [ "$won" = "1" ] && [ "$lost" = "7" ]; then verdict 201; else verdict "$won booked, $lost rejected"; fi
printf "      %s booked, %s rejected as conflicts\n" "$won" "$lost"
sleep "$PACE"

step "so Alice ends up in" 200 "exactly two meetings"
body -H "$ALICE" $API/api/v1/meetings

act "ACT 7 — what the run left behind"

# Same shape as body(), but the scrape is hundreds of lines, so each step keeps its own.
metrics() {
  local response status
  response=$(curl -s -w $'\n%{http_code}' -H "$ADMIN" $API/actuator/prometheus)
  status=${response##*$'\n'}
  verdict "$status"
  echo "${response%$'\n'*}" | grep -E "$1" | sed 's/^/      /' || true
  sleep "$PACE"
}

step "the counters this service keeps itself" 200 "3 booked, 10 refused"
metrics '^meetings_'

step "the request timings Actuator adds for free" 200 "one line per endpoint"
metrics '^http_server_requests_seconds_count.*/api/v1/'

echo ""
if [ "$failures" -eq 0 ]; then
  echo "${BOLD}${GREEN}── all $n steps behaved as expected ${RESET}"
else
  echo "${BOLD}${RED}── $failures of $n steps did not behave as expected ${RESET}"
fi
echo ""
exit "$failures"
