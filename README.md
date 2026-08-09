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

---

API documentation and the design rationale are added as the implementation lands.
