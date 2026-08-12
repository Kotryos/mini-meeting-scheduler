# mini-meeting-scheduler

[![CI](https://github.com/Kotryos/mini-meeting-scheduler/actions/workflows/ci.yml/badge.svg)](https://github.com/Kotryos/mini-meeting-scheduler/actions/workflows/ci.yml)

*A miniature meeting-scheduling service, built as a time-boxed backend design exercise.*

## Overview

Users publish the time slots during which they are available. Any of those slots can be
turned into a meeting with a title, a description and participants. Every user has a
personal calendar tracking which of their time is free and which is taken, and that
calendar can be viewed over a chosen time frame.

## Capabilities

**Time slot management** — publish availability over any range, which is split into
whole-hour slots; delete slots, or mark them busy to block time.

**Meeting scheduling** — turn a free hour into a meeting with a title, description and
participants. Everyone involved must be free at that hour, and the meeting marks the
time busy in each of their calendars.

**Availability** — view your own calendar over a selected time frame, narrowed to just
the free or just the busy time, or summarised into merged blocks.

## Running locally

Requires Docker. From the project root:

```bash
docker compose up --build
```

This starts PostgreSQL and the service. Flyway applies the schema on startup, and the
application only reports healthy once the database is reachable:

```bash
curl http://localhost:8080/actuator/health
```

| Service | Address | Credentials |
|---------|---------|-------------|
| API | `http://localhost:8080` | — |
| PostgreSQL | `localhost:5432` | `minischeduler` / `minischeduler` |

Database contents survive restarts in a named volume. Stop with `docker compose down`,
or `docker compose down -v` to discard the data as well.

Running the tests requires Docker too — they start a real PostgreSQL container via
Testcontainers, so migrations and constraints are verified against the same database the
service runs on.

## Walkthrough

One script exercises the whole API — 38 requests in six acts, using the demo keys below.
It starts its own stack from an empty database, so the output is the same every time:

```bash
bash demo.sh
```

That is the only command needed; it runs `docker compose` itself. Note that it discards
the local database volume first, which is what makes it repeatable.

Every step declares the status it expects and marks whether it got it, ending with a
tally; the script exits non-zero if anything misbehaved. Responses are printed exactly as
the API returns them, with a short pause between steps so the run reads rather than
flashes past.

**CI runs it on every push**, which is why it checks itself rather than only printing.
The unit and integration tests all run against Testcontainers, so until this existed
nothing proved that the Compose stack starts and answers — the build only ever built the
image. This is also where concurrent booking is guarded: act 6 is the one thing no
in-process test reproduces faithfully.

Alice publishes her morning, blocks an hour by hand, and books a standup with Bob. Carol
misses out. Along the way it hits the interesting failures: a range that covers no whole
hour, an hour published twice, a slot belonging to someone else, a participant who never
published the time, an unaligned meeting start, and a cancellation by the wrong person.

The last act is the one worth watching. Eight requests race for the same free hour:

```
201
409
409
409
409
409
409
409
```

One booking wins because the check and the write are the same statement — see
*A meeting is booked by one statement* below.

<details>
<summary>Full expected output</summary>

```
=========== ACT 1 — getting in
1. health needs no key                                  200
2. no key at all                                        401
3. a key nobody issued                                  401
4. a user key on an admin endpoint                      403
5. the admin key on the same endpoint                   200

=========== ACT 2 — Alice publishes her availability
6.  09:00-12:00 becomes three hourly slots              201  ids 1,2,3
7.  a range inside one hour                             400  "Range does not cover a whole hour"
8.  a range ending before it starts                     400  "A range must end after it starts"
9.  overlapping an hour she already published           409  "Slots already published for [11:00Z]"
10. the hour after her last one                         201  id 4
11. Bob publishes 09:00-11:00                           201  ids 5,6

=========== ACT 3 — reading a calendar
12. Alice's whole day                                   four slots, all FREE
13. she blocks 13:00 by hand                            204
14. status=FREE                                         09:00, 10:00, 11:00
15. status=BUSY                                         13:00
16. the summary                                         [09:00-12:00 FREE] [13:00-14:00 BUSY]
17. status=MAYBE                                        400
18. Alice touching Bob's slot                           404, not 403

=========== ACT 4 — the standup
19. Alice books 09:00 with Bob                          201  participantIds [1,2]
20. Alice's meetings                                    the standup
21. Bob's meetings                                      the same one, though he booked nothing
22. Carol's meetings                                    []
23. Carol reading it directly                           404
24. Alice's 09:00                                       BUSY
25. freeing a slot the meeting holds                    409  "belongs to a meeting; cancel it first"
26. deleting it                                         409  same
27. booking the same hour twice                         409
28. inviting Carol, who published nothing               409  "Not everyone has a free slot"
29. a meeting starting at 10:30                         400  "An hour must start on a whole hour"
30. a meeting with a blank title                        400
31. an hour Alice never published                       409

=========== ACT 5 — calling it off
32. Bob is not the organiser                            404
33. Alice cancels                                       204
34. her 09:00                                           FREE again
35. the cancelled meeting                               404
36. rebooking under a new name                          201  — this is how you edit

=========== ACT 6 — two people, one hour
37. eight requests race for 10:00                       one 201, seven 409
38. Alice's meetings                                    exactly two
```

Meeting ids skip numbers — a rolled-back booking still consumes a sequence value, because
Postgres sequences are not transactional.

</details>

## Authentication

Every request except `/actuator/health` needs an API key in the `X-API-Key` header. Keys
are stored only as SHA-256 hashes; the values below are seeded by a migration for local
use and are not secrets.

| User  | API key           | Role  |
|-------|-------------------|-------|
| Alice | `alice-demo-key`  | USER  |
| Bob   | `bob-demo-key`    | USER  |
| Carol | `carol-demo-key`  | USER  |
| Admin | `admin-demo-key`  | ADMIN |

Health is public, everything under `/actuator` requires the admin key:

```bash
curl http://localhost:8080/actuator/health
curl -H "X-API-Key: admin-demo-key" http://localhost:8080/actuator/info
```

A missing or unknown key returns `401`; a valid key without the required role returns
`403`.

## Time slots

Availability is published as whole hours. A range is split onto that grid and any partial
hour at either end is dropped, so `09:15–11:45` becomes the slots `09:00` and `10:00`.

| Method   | Path                     | Purpose                                |
|----------|--------------------------|----------------------------------------|
| `POST`   | `/api/v1/slots`          | publish free hours over a range        |
| `GET`    | `/api/v1/slots`          | list your slots in a window            |
| `GET`    | `/api/v1/slots/summary`  | the same hours merged into blocks      |
| `PATCH`  | `/api/v1/slots/{id}`     | mark a slot busy or free               |
| `DELETE` | `/api/v1/slots/{id}`     | withdraw a slot                        |

Add `status=FREE` or `status=BUSY` to the list to see only free or only busy time; leave
it out to see the whole calendar.

```bash
curl -X POST http://localhost:8080/api/v1/slots \
  -H "X-API-Key: alice-demo-key" -H "Content-Type: application/json" \
  -d '{"from":"2026-10-01T09:00:00Z","to":"2026-10-01T12:00:00Z"}'

curl -H "X-API-Key: alice-demo-key" \
  "http://localhost:8080/api/v1/slots?from=2026-10-01T00:00:00Z&to=2026-10-02T00:00:00Z"

curl -H "X-API-Key: alice-demo-key" \
  "http://localhost:8080/api/v1/slots?from=2026-10-01T00:00:00Z&to=2026-10-02T00:00:00Z&status=FREE"

curl -X PATCH http://localhost:8080/api/v1/slots/1 \
  -H "X-API-Key: alice-demo-key" -H "Content-Type: application/json" \
  -d '{"status":"BUSY"}'

curl -X DELETE http://localhost:8080/api/v1/slots/1 -H "X-API-Key: alice-demo-key"
```

The summary answers the same question in fewer lines. It merges neighbouring hours that
share a status:

```bash
curl -H "X-API-Key: alice-demo-key" \
  "http://localhost:8080/api/v1/slots/summary?from=2026-12-01T00:00:00Z&to=2026-12-02T00:00:00Z"
```

```json
[
  {"startAt":"2026-12-01T09:00:00Z","endAt":"2026-12-01T12:00:00Z","status":"FREE"},
  {"startAt":"2026-12-01T12:00:00Z","endAt":"2026-12-01T13:00:00Z","status":"BUSY"}
]
```

Two hours only merge if they touch, so an hour you never published splits the block in
two — a gap means you offered nothing then, which is not the same as being busy. Blocks
carry no ids, because a summary is not something you edit; use the list for that.

Slots are per-user: every call acts on the calendar belonging to the presented key, so a
slot owned by someone else is reported as `404` rather than `403`. Publishing a range that
covers an hour you already published returns `409`. Errors follow RFC 7807.

There is no way to move a slot to a different hour. Delete it and publish the new hour —
the same pattern meetings use, and it keeps every change to a slot a single statement.

## Meetings

A meeting occupies one hour. Everyone taking part must already have published a free slot
for it — the meeting takes those slots and marks them busy.

| Method   | Path                    | Purpose                            |
|----------|-------------------------|------------------------------------|
| `POST`   | `/api/v1/meetings`      | book an hour as a meeting          |
| `GET`    | `/api/v1/meetings`      | list the meetings you are in       |
| `GET`    | `/api/v1/meetings/{id}` | read one of them                   |
| `DELETE` | `/api/v1/meetings/{id}` | cancel it and free everyone's time |

```bash
curl -X POST http://localhost:8080/api/v1/meetings \
  -H "X-API-Key: alice-demo-key" -H "Content-Type: application/json" \
  -d '{"title":"Standup","description":"Daily sync",
       "startAt":"2026-12-01T09:00:00Z","participantIds":[2]}'
```

```json
{"id":1,"title":"Standup","description":"Daily sync",
 "startAt":"2026-12-01T09:00:00Z","endAt":"2026-12-01T10:00:00Z","participantIds":[1,2]}
```

You are always a participant in the meeting you book, so there is no need to list
yourself. If anyone named has no free slot at that hour — because they never published
one, or because it is already taken — the whole request fails with `409` and nothing is
booked. A meeting is visible only to the people in it; to anyone else it is `404`.

Only the organiser can cancel. Cancelling frees every participant's hour, so the time
becomes bookable again:

```bash
curl -X DELETE http://localhost:8080/api/v1/meetings/1 -H "X-API-Key: alice-demo-key"
```

There is no way to edit a meeting. Change one by cancelling it and booking again — the
same hour is free the moment the cancellation returns. While a meeting holds a slot, that
slot cannot be marked free or deleted through the slot endpoints; both answer `409` and
tell you to cancel first. That is what stops a calendar from saying someone is free while
a meeting still expects them.

## Design notes

**Every slot is exactly one hour.** Any published range is normalised onto that grid.
Uniform duration is what lets a unique index on `(user_id, start_at)` guarantee that no
two slots overlap — two slots are then either identical or disjoint, so overlap becomes
a uniqueness problem the database already solves. With variable durations the same
guarantee needs a GiST exclusion constraint over ranges. The grid size is a deployment
decision, not a structural one: a finer grid is a migration.

**A meeting is booked by one statement.** Reserving an hour is a single
`UPDATE ... WHERE start_at = ? AND user_id IN (?) AND status = 'FREE' AND meeting_id IS NULL`.
If the number of rows it changed is not the number of participants, somebody was not free
and the whole transaction rolls back — including the meeting row, so a failed booking
leaves nothing behind. Because the check and the write are the same statement, two people
racing for the same hour cannot both win: the second one changes zero rows.

**Editing is cancel and rebook.** A meeting has no update endpoint. Changing the time or
the people means cancelling and booking again, which is one path through the code instead
of two and cannot leave a half-changed meeting behind. The cost is that the meeting gets a
new id; for a service where a booking is an hour and a title, that is cheaper than an edit
path that has to release some slots and claim others atomically.

**Participants are the slots, not a list.** Booking writes `meeting_id` onto each
participant's slot, so who attends is derived from whose time is taken. There is no
participant table, which means the two can never disagree — being invited and being busy
are one fact. It also makes attendance impossible without availability, which is the rule
the service is built on.

**Availability is computed, not stored.** Both the filter and the summary ride on the
query that already backs the slot list — no availability table, no extra index, nothing
to keep in step. Merging into blocks is a loop over rows that are already sorted.

**The merge could have been SQL.** Postgres 14 has `range_agg`, which unions adjacent
ranges in one statement and gets the gaps right. It was left out because it needs a native
query. Every other query here is JPQL, which Spring Data checks when the application
starts — a typo fails the boot, not the request. Native SQL gives that up, and the window
is a few hundred rows at most, so the check is worth more than the cleverness.

**Demo users exist only under the `demo` profile.** Their accounts and API keys live in
`db/seed`. Flyway reads that folder only when the profile is on, and Docker Compose turns
it on — so the keys above work right after `docker compose up`. Start without the profile
and the database has no users at all.

**Coverage is a minimum, not a goal.** The build fails below 85% line and branch coverage.
It currently sits at 99% and 100%. The number proves less than it looks: repositories are
interfaces whose SQL lives in annotations, so JaCoCo cannot see them. The rule that stops
one user from editing another user's slots counts for nothing in the report. What actually
covers it are tests that try the cross-user write and expect it to fail.

**Each test layer mocks the layer below.** Controller tests mock the service; the service
test mocks the repository. Repository, schema and authentication tests run against a real
PostgreSQL container and insert their own rows with DBUnit. No test relies on the demo
data, and the mocked ones run without Docker.

**Credentials are in plain text.** Database passwords sit in `docker-compose.yml` and
demo API keys in the seed migration, so the stack starts with no setup. Production would
resolve both from a secrets manager and inject them as environment variables.

**API keys cannot be issued or rotated through the API.** The schema supports several
live keys per user and revocation through `revoked_at` — both deliberate, since rotation
requires two valid keys at once — but no endpoint exercises either. Issuance and
revocation would be the first additions.

**Updates put their check in the SQL.** Instead of loading a row, checking it in Java and
saving it back, each update and delete carries the check in the statement itself —
`UPDATE ... WHERE id = ? AND user_id = ?` — and the code looks at how many rows changed.
If nothing changed, the check failed and the API answers `404`. That is one query instead
of two. It matters more once meetings exist: when the check is something that can change
while the request is running, such as whether an hour is still free, keeping the check and
the write in one statement stops two requests from both passing it.

**Module boundaries are enforced by a test, not the compiler.** `calendar.internal` is a
naming convention; a `public` class there would be importable from anywhere and compile
fine. `ModularityTest` is what fails the build. Compile-time enforcement would mean
separate Maven modules or JPMS, both heavier than a single deployable justifies. In
practice every class inside the two `internal` packages is package-private today, so the
compiler happens to agree — the test is what keeps it that way.

**`scheduling` reaches `calendar` through one interface.** `TimeSlot` and its repository
are package-private, so the scheduling code physically cannot touch them; it calls
`SlotBooking`, the only thing `calendar` publishes for it. That is two methods — reserve
an hour, and list who holds it. Both modules still share one database and one
transaction, which is deliberate: booking has to be atomic, and splitting them into
services would turn a single `UPDATE` into a distributed saga.

---

API documentation and the remaining design rationale are added as the implementation
lands.
