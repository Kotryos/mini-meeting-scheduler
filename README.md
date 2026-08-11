# mini-meeting-scheduler

[![CI](https://github.com/Kotryos/mini-meeting-scheduler/actions/workflows/ci.yml/badge.svg)](https://github.com/Kotryos/mini-meeting-scheduler/actions/workflows/ci.yml)

*A miniature meeting-scheduling service, built as a time-boxed backend design exercise.*

## Overview

Users publish the time slots during which they are available. Any of those slots can be
turned into a meeting with a title, a description and participants. Every user has a
personal calendar tracking which of their time is free and which is taken, and
availability can be queried over a chosen time frame — for one person or for several at
once, so a time that works for everybody can be found.

## Capabilities

**Time slot management** — publish availability over any range, which is split into
whole-hour slots; delete slots, or mark them busy to block time.

**Meeting scheduling** — convert available slots into a meeting with a title,
description and participants. A meeting spans one or more consecutive hours and marks
that time busy for everyone involved.

**Availability** — query free and busy time for one or more users across a selected time
frame, aggregated into a single view.

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

| Method   | Path                   | Purpose                            |
|----------|------------------------|------------------------------------|
| `POST`   | `/api/v1/slots`        | publish free hours over a range    |
| `GET`    | `/api/v1/slots`        | list your own slots in a window    |
| `PATCH`  | `/api/v1/slots/{id}`   | mark a slot busy or free           |
| `DELETE` | `/api/v1/slots/{id}`   | withdraw a slot                    |

```bash
curl -X POST http://localhost:8080/api/v1/slots \
  -H "X-API-Key: alice-demo-key" -H "Content-Type: application/json" \
  -d '{"from":"2026-10-01T09:00:00Z","to":"2026-10-01T12:00:00Z"}'

curl -H "X-API-Key: alice-demo-key" \
  "http://localhost:8080/api/v1/slots?from=2026-10-01T00:00:00Z&to=2026-10-02T00:00:00Z"

curl -X PATCH http://localhost:8080/api/v1/slots/1 \
  -H "X-API-Key: alice-demo-key" -H "Content-Type: application/json" \
  -d '{"status":"BUSY"}'

curl -X DELETE http://localhost:8080/api/v1/slots/1 -H "X-API-Key: alice-demo-key"
```

Slots are per-user: every call acts on the calendar belonging to the presented key, so a
slot owned by someone else is reported as `404` rather than `403`. Publishing a range that
covers an hour you already published returns `409`. Errors follow RFC 7807.

## Design notes

**Slots are fixed one-hour blocks.** Any published range is normalised onto that grid.
Uniform duration is what lets a unique index on `(user_id, start_at)` guarantee that no
two slots overlap — two blocks are then either identical or disjoint, so overlap becomes
a uniqueness problem the database already solves. With variable durations the same
guarantee needs a GiST exclusion constraint over ranges. The grid size is a deployment
decision, not a structural one: a finer grid is a migration.

**Demo data lives in a migration.** Demo users and their API keys are seeded by
`V3__seed_demo_users.sql` so the service is usable immediately after
`docker compose up`. In a real deployment that seed would move to a profile-gated Flyway
location so it never reaches production, and tests would build their own fixtures rather
than depend on it.

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
naming convention; a `public` class there is importable from anywhere and compiles fine.
`ModularityTest` is what fails the build. Compile-time enforcement would mean separate
Maven modules or JPMS, both heavier than a single deployable justifies.

---

API documentation and the remaining design rationale are added as the implementation
lands.
