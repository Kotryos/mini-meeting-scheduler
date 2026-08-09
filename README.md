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

**Time slot management** — create available slots of configurable duration, modify or
delete them, and mark them busy or free.

**Meeting scheduling** — convert an available slot into a meeting with a title,
description and participants. Booking marks the corresponding time as busy for everyone
involved.

**Availability** — query free and busy time for one or more users across a selected time
frame, aggregated into a single view.

## Running locally

Requires Docker. From the project root:

```bash
docker compose up --build
```

The service listens on `http://localhost:8080` and reports its health at:

```bash
curl http://localhost:8080/actuator/health
```

Stop it with `docker compose down`.

---

API documentation and the design rationale are added as the implementation lands.
