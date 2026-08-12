# mini-meeting-scheduler

[![CI](https://github.com/Kotryos/mini-meeting-scheduler/actions/workflows/ci.yml/badge.svg)](https://github.com/Kotryos/mini-meeting-scheduler/actions/workflows/ci.yml)

A small service for arranging meetings. People publish the hours they are free, and any of
those hours can be turned into a meeting with a title, a description and participants.

Java 21, Spring Boot, PostgreSQL. Runs with one command.

## Running it

Requires Docker.

```bash
docker compose up --build
```

This starts PostgreSQL and the service. Flyway creates the schema on startup, and the
service reports healthy once the database is reachable:

```bash
curl http://localhost:8080/actuator/health
```

Four demo accounts are seeded so there is something to log in with:

| User  | API key          | Role  |
|-------|------------------|-------|
| Alice | `alice-demo-key` | USER  |
| Bob   | `bob-demo-key`   | USER  |
| Carol | `carol-demo-key` | USER  |
| Admin | `admin-demo-key` | ADMIN |

Every request except `/actuator/health` needs one of these in an `X-API-Key` header.

## API

| Method   | Path                    | What it does                                    |
|----------|-------------------------|-------------------------------------------------|
| `POST`   | `/api/v1/slots`         | publish the hours you are free over a range     |
| `GET`    | `/api/v1/slots`         | list your slots in a window                     |
| `GET`    | `/api/v1/slots/summary` | the same hours, neighbours merged into blocks   |
| `PATCH`  | `/api/v1/slots/{id}`    | mark one slot busy or free                      |
| `DELETE` | `/api/v1/slots/{id}`    | withdraw a slot                                 |
| `POST`   | `/api/v1/meetings`      | book an hour as a meeting                       |
| `GET`    | `/api/v1/meetings`      | list the meetings you are in                    |
| `GET`    | `/api/v1/meetings/{id}` | read one of them                                |
| `DELETE` | `/api/v1/meetings/{id}` | cancel it and free everyone's time              |
| `GET`    | `/actuator/health`      | public                                          |
| `GET`    | `/actuator/prometheus`  | metrics, admin key only                         |

Add `status=FREE` or `status=BUSY` to the slot list to narrow it. When something goes
wrong the response is a JSON object with the status and a message explaining it.

**The API describes itself.** Swagger UI is at <http://localhost:8080/swagger-ui.html> and
the OpenAPI document at <http://localhost:8080/v3/api-docs>. Both are open without a key,
so the API can be read before anyone has one. The `X-API-Key` header is declared there, so
**Authorize** in the UI accepts a demo key, and *Try it out* works against the running
service.

**The complete scenario can be run as a script.** `bash demo.sh` starts its own clean stack and
makes 41 requests in seven acts: publishing hours, reading a calendar, booking a standup,
the failure cases, cancelling, eight simultaneous bookings of the same hour and finally
the metrics that run produced. Each step prints the status it expected next to the one it
got, and the script exits non-zero if any of them disagree. CI runs it on every push.

## Design and trade-offs

### The service

**A high-performance meeting scheduling platform, built with Spring Boot and Java.**  
Java 21 and Spring Boot. A request here does very little except wait for the database, and
virtual threads suit that exactly: a waiting request gives up its system thread instead of
occupying one, so a great many can be in flight at once without a large pool behind them.
The code stays ordinary in return — the usual transactions, JPA and stack traces that read
top to bottom. A reactive stack would reach the same thread economics, and would be better
at streaming or waiting on slow third parties, but neither happens here: every call is a
short trip to one database.

**Users manage their time slots, schedule meetings and view their availability.**  
Three groups of endpoints, in the table above.

**Users define available slots that can later be converted into meetings.**  
`POST /api/v1/slots` publishes free hours. `POST /api/v1/meetings` converts one of them
into a meeting.

**Each user has a personal calendar where their time is managed.**  
Every endpoint acts on the calendar belonging to the key presented. A slot owned by
somebody else answers `404`, not `403`, so the API never confirms that something exists to
someone with no business knowing. The cost is that a `404` no longer separates "there is no
such slot" from "it is not yours", which makes a confused caller harder to help.

**Calendar is a domain term only.**  
There is a `calendar` module in the code, and that is where all of this lives. The word
appears in no URL and in no field name — the API talks about slots and meetings, which is
what a caller actually manipulates.

**A slot can be booked as a meeting with a title and participants.**  
Booking takes a title, an optional description and the participants. Everyone named must
already have that hour free; if even one of them does not, nothing is booked at all.

**Querying free or busy slots, aggregated over a selected time frame.**  
`GET /api/v1/slots?from=…&to=…` returns the window, and `status=FREE` or `status=BUSY`
narrows it. `GET /api/v1/slots/summary` gives the same window with neighbouring hours of
the same status merged into blocks, so three free hours read as one `09:00`–`12:00` block.

**All data is persisted.**  
PostgreSQL. The schema is created by Flyway migrations that run on startup, so a fresh
database and an existing one end up in the same place.

### Time slots

**Slots with configurable duration.**  
Availability goes in as a real span of time (e.g. `09:00` to `12:00`) and comes back the same
way. Inside, that span is stored as whole-hour rows, one per hour, and the summary endpoint
puts the range back together by merging neighbours. Callers deal in ranges at both ends;
hours are an implementation detail in between.

That storage choice is what makes overlapping availability impossible. Because every row is
the same length, two rows either sit on exactly the same hour or they do not touch at all,
so a unique index on `(user_id, start_at)` is enough to rule overlap out. No code anywhere
checks whether two spans collide, because they cannot.

What it costs is precision. A span is rounded to the whole hours it covers, so
`09:15`–`11:45` becomes `09:00`–`11:00`, and there is no such thing as a twenty-minute
slot. Meetings inherit the same limit and take exactly one hour.

Making the grid finer — a minute instead of an hour — is a migration rather than a rewrite,
because nothing reasons about the length of a row, and sub-hour availability would follow
immediately. Meetings would not: one currently claims a single row per person, and covering
a longer stretch means claiming several at once and releasing them together. That is real
work, and it is why the grid is an hour today.

**Delete or modify existing slots.**  
`DELETE` removes a slot, and `PATCH` changes whether it is busy or free, but there is no way
to move one to a different hour — delete it and publish the new one. Keeping every change
to a single statement is worth more here than an edit path that would have to release one
row and claim another without ever landing halfway.

**Mark slots busy or free.**  
`PATCH /api/v1/slots/{id}` with `{"status":"BUSY"}` or `{"status":"FREE"}`. A slot held by
a meeting cannot be changed this way — it answers `409` and says to cancel the meeting
first, so a calendar can never claim someone is free while a meeting still expects them.

### Meetings

**Convert available slots into meetings.**  
Booking claims the hour in every participant's calendar and marks it busy. Cancelling gives
it back, and the hour is immediately bookable again, which is also how a meeting is changed:
cancel it and book again. The identifier changes, so a client holding the old one has to
read the new meeting, and that is the price of having one path through the code instead of
two.

**Title, description and participants.**  
All three are on the booking request, and you are always a participant in a meeting you
book, so there is no need to name yourself. Who attends is worked out from whose time is
taken — there is no separate list of participants, so the two can never disagree. Being in
a meeting and having that hour booked are the same fact, recorded once.

The limit is that nobody can be invited who has not already published that hour as free;
the booking fails instead. That keeps a meeting from ever existing in someone's calendar
without their availability behind it, but a real product would want invitations that can
sit pending.

### Scale

**Hundreds of users and thousands of slots.**  
Every request costs a fixed number of queries, however large its input. Booking is one
`UPDATE` regardless of how many people are involved, rather than one per person. Listing
meetings fetches all their participants together instead of asking once per meeting.
Nothing fans out into a loop of queries.

The reads lean on an index that had to exist anyway: the unique constraint on
`(user_id, start_at)` that prevents overlap is exactly the shape the calendar queries
filter on, so it does the second job for nothing.

The same idea keeps it correct under load. Nothing loads a row, inspects it and writes it
back — the condition that has to hold is part of the statement that does the work, and
the code looks at how many rows changed:

```sql
UPDATE time_slot SET status = 'BUSY', meeting_id = ?
WHERE start_at = ? AND user_id IN (?) AND status = 'FREE' AND meeting_id IS NULL
```

If the number of rows changed is not the number of participants, somebody was not free and
the whole transaction rolls back. Two people racing for the same hour cannot both succeed,
because the second one no longer matches the `WHERE` — nothing is locked, nothing is
retried and there is no window between checking and writing in which things can change.
The walkthrough demonstrates it: eight simultaneous bookings of one hour produce one `201`
and seven `409`s.

What this gives up is a rich domain model. The rules live in SQL rather than in objects,
there is no `slot.markBusy()` to read, and the entity is deliberately thin — giving it
behaviour would mean loading it first and re-opening the very gap this closes.

### Delivery

**Runnable locally with docker-compose, with all dependencies included.**  
`docker compose up --build` starts PostgreSQL and the service. Compose waits for the
database to be healthy before starting the service, and the service reports healthy only
once it can reach the database.

**Clear documentation of how the service can be consumed.**  
This file, the OpenAPI document generated from the code so it cannot drift from it and
`demo.sh`, which shows every endpoint working against a real stack.

**Metrics.**  
Actuator gives JVM, connection pool and HTTP timings for free at `/actuator/prometheus`,
behind the admin key. Two more are kept by hand, because nothing standard can know them:
`meetings_requested_total`, tagged `booked` or `conflict`, which shows how often people
want the same hour, and `meetings_participants`, which shows how large meetings tend to be.

**Tests.**  
96 of them, in three layers. Controller tests mock the service and check what it was asked
to do. The service test mocks the repository. Repository and schema tests run against a
real PostgreSQL container with fixed datasets. The build fails below 85% coverage; it
currently sits at 99% of lines and 100% of branches. On top of that, `demo.sh` runs the
whole API against a real stack on every push.

## Also worth mentioning

**API keys.**  
A personal calendar has to know whose calendar it is. Keys are stored only as hashes, and
there are two roles so that metrics are not public. There is no way to issue or rotate a
key through the API — that would be the first thing to add.

**Module boundaries, with Spring Modulith.**  
The code is split into `identity`, `calendar` and `scheduling`. Each keeps its internals
package-private, so the compiler stops one reaching into another, and `scheduling` can only
book time through the small interface `calendar` publishes. Spring Modulith checks the
boundaries hold and fails the build when one is crossed, which is what keeps the split
honest as the code grows — a naming convention alone would not.

**A runnable walkthrough.**  
`demo.sh` exists because a list of endpoints is not proof. It doubles as the integration
test in CI, and it is the only thing that checks the running stack starts and answers.

**Readable errors.**  
Every failure returns a JSON body saying what went wrong, rather than a bare status code.
