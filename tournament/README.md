# YIMO tournament control service

This directory contains the tournament control service for YIMO Graphwar 2.0.
It handles organizer registration, scheduled seeded single-elimination
tournaments, optional check-in, expiring match codes, participant sessions,
bounded room allocation, assigned joins, room heartbeats, and signed result
records. Gameplay still belongs to the authoritative Java room server.

## Requirements and commands

- Node.js 24.x. The service uses the built-in `node:sqlite` API and has no
  third-party runtime dependencies.
- `npm test` runs the service and HTTP smoke tests.
- `npm start` starts the HTTP service. The required environment variables are
  listed below.
- `npm run demo` starts an isolated in-memory scheduled tournament with four
  sample participants for local testing. It never opens a database or uses
  production secrets.

Node's SQLite API is marked experimental in Node 24, so the expected warning
may appear during tests and startup. The test suite must still finish with all
tests passing.

## Runtime configuration

Set these values outside the repository, for example through a systemd
environment file or a secret manager:

| Variable | Default | Purpose |
| --- | --- | --- |
| `YIMO_ADMIN_TOKEN` | required | Bearer token for organizer endpoints |
| `YIMO_ROOM_HMAC_SECRET` | required | Secret shared with YIMO tournament room processes |
| `YIMO_TOURNAMENT_DB` | `./data/tournament.sqlite` | SQLite database path |
| `YIMO_BUILD_ID` | `YIMO-Graphwar-2.0.0` | Accepted client/server build |
| `YIMO_PROTOCOL_VERSION` | `2` | Accepted wire-protocol version |
| `HOST` | `127.0.0.1` | HTTP bind address; use localhost behind Nginx |
| `PORT` | `8080` | HTTP bind port |

Never commit these values, participant codes, database files, or private keys.
The repository ignores the default database directory and local `.env` file.

Example local start from PowerShell:

```powershell
$env:YIMO_ADMIN_TOKEN = 'replace-with-a-local-secret'
$env:YIMO_ROOM_HMAC_SECRET = 'replace-with-a-different-local-secret'
$env:YIMO_TOURNAMENT_DB = './data/tournament.sqlite'
npm start
```

For a deployment, bind this service to localhost and put an HTTPS reverse
proxy in front of it. Do not expose SQLite or the HMAC secret to clients.

## API

All JSON errors have the form `{ "error": "CODE", "message": "..." }`.
Organizer routes require `Authorization: Bearer <YIMO_ADMIN_TOKEN>`.

| Method and path | Auth | Purpose |
| --- | --- | --- |
| `GET /healthz` | none | Build and protocol health check |
| `GET /admin` | none | Organizer console with schedule and lifecycle controls |
| `GET /participant` | none | Competitor login, entry, assigned join, and public bracket |
| `GET /api/v1/tournaments/{id}/bracket` | none | Public bracket data without match codes |
| `POST /api/v1/admin/participants` | admin | Add an organizer-issued participant code |
| `POST /api/v1/admin/tournaments` | admin | Create a scheduled tournament and its room slots |
| `POST /api/v1/admin/bracket/seed` | admin | Seed the single-elimination bracket |
| `GET /api/v1/admin/tournaments/{id}` | admin | Read status, schedule, counts, roster, and bracket |
| `POST /api/v1/admin/tournaments/{id}/registration/open` | admin | Open registration immediately |
| `POST /api/v1/admin/tournaments/{id}/registration/close` | admin | Close registration or enter check-in |
| `POST /api/v1/admin/tournaments/{id}/check-in/open` | admin | Open an enabled check-in window |
| `POST /api/v1/admin/tournaments/{id}/start` | admin | Freeze the eligible roster and start the bracket |
| `POST /api/v1/participant-sessions` | none | Exchange a participant code for a short-lived session |
| `POST /api/v1/tournaments/{id}/register` | session | Register the authenticated competitor |
| `POST /api/v1/tournaments/{id}/check-in` | session | Check in the authenticated competitor |
| `GET /api/v1/player/tournaments/{id}` | session | Read private entry and next-match schedule |
| `POST /api/v1/matches/join` | session | Validate a match code and receive room access |
| `POST /api/v1/matches/{matchId}/join-assigned` | session | Join the authenticated competitor’s assigned match |
| `POST /api/v1/rooms/heartbeat` | room token | Keep an assigned room alive |
| `POST /api/v1/matches/{matchId}/result` | room token | Record the authoritative room result |
| `GET /api/v1/player/matches` | session bearer | List the participant's matches |

### Lifecycle

New tournaments use this state sequence:

```text
DRAFT -> REGISTRATION_OPEN -> READY -> RUNNING -> COMPLETED
                         \-> CHECK_IN -/
                         \-> START_BLOCKED (too few eligible entrants)
```

When `requireCheckIn` is enabled, registration closes into `CHECK_IN` and only
checked-in entries are eligible. Otherwise, registered entries are eligible
directly. `autoStart` plus `startAt` lets the in-process scheduler advance the
tournament without an organizer request. Manual `/start` and scheduled start
share the same transaction, so a retry cannot create a second bracket.

The core request sequence is:

1. The organizer opens `/admin`, creates a tournament schedule, and adds
   organizer-issued participant codes through the admin API.
2. Competitors open `/participant`, exchange their participant code for a
   one-hour session, register, and optionally check in.
3. The scheduler or organizer starts the tournament, freezes the eligible
   roster, and creates the bracket.
4. A competitor loads their private schedule and joins the assigned match;
   the legacy match-code endpoint remains available for compatibility.
5. The service allocates one room slot and returns a per-participant signed
   room token. The Java room server validates it before exposing room state.
6. The room sends heartbeats and submits exactly one result. An identical
   retry is idempotent; a conflicting retry is rejected.

## Local bracket demo

From this directory, run:

```powershell
npm run demo
```

Open the printed `/participant?tournament=yimo-demo-2026` URL. The page loads
the public bracket and groups matches into round columns. The demo starts with
four scheduled sample entrants, advances them through registration, and then
starts the bracket. It prints only demo-local admin and participant values to
the terminal so the complete session/register/assigned-join/result flow can be
exercised with PowerShell or another HTTP client. Stop it with `Ctrl+C`; all
data then disappears.

The public bracket intentionally returns participant display names, statuses,
rounds, winners, and byes only. Match codes and signed room tokens remain in
the protected join flow.

Example organizer requests (use test values only):

```text
POST /api/v1/admin/participants
Authorization: Bearer <admin-token>
{"participantId":"p-1","displayName":"Player 1","participantCode":"<issued-code>"}

POST /api/v1/admin/tournaments
Authorization: Bearer <admin-token>
{"tournamentId":"yimo-cup-2026","name":"YIMO Cup 2026","buildId":"YIMO-Graphwar-2.0.0","protocolVersion":2}

POST /api/v1/admin/bracket/seed
Authorization: Bearer <admin-token>
{"tournamentId":"yimo-cup-2026","participantIds":["p-1","p-2"]}
```

Participant requests use the `sessionToken` returned by
`POST /api/v1/participant-sessions`. The `roomToken` returned by
`POST /api/v1/matches/join` is opaque and must be sent only to the assigned
YIMO room process.

The important response shapes are:

```text
POST /api/v1/participant-sessions
{"participantId":"p-1","sessionToken":"<opaque-session>","expiresAt":1700003600}

POST /api/v1/matches/join
{"matchId":"yimo-cup-2026-r1-m1","roomSlot":30000,"port":30000,
 "roomToken":"<opaque-room-token>","expiresAt":1700000900}

POST /api/v1/rooms/heartbeat
{"matchId":"yimo-cup-2026-r1-m1","roomSlot":30000,"state":"IN_PROGRESS"}

POST /api/v1/matches/yimo-cup-2026-r1-m1/result
{"duplicate":false,"matchId":"yimo-cup-2026-r1-m1",
 "resultSignature":"<server-signature>","nextMatch":{}}
```

The numeric timestamps above are illustrative only. The service returns the
actual values for the current clock and configured timeout.

## Storage and security

SQLite enables foreign keys, a five-second busy timeout, and WAL mode for file
databases. The tables are:

- `participants` and `participant_sessions`
- `tournaments`, `tournament_entries`, `matches`, and `match_players`
- `room_slots`
- `audit_events`

Participant codes use a keyed lookup hash followed by salted `scrypt`
verification; match codes are stored as SHA-256 hashes. The raw values are
returned only at the controlled creation boundary. The lookup hash avoids a
full-participant scan for each login while the `scrypt` check remains the
password-strength verification.
Room tokens use HMAC-SHA256 and carry the protocol version, build ID, match ID,
participant ID, room slot, expiry, and nonce. Session and join attempts are
rate-limited per client key. The Java room gate additionally rejects expired,
replayed, wrong-build, wrong-match, wrong-slot, or wrong-secret tokens.

The in-memory rate limiter is intentionally process-local for this first
implementation. `ponytail:` the ceiling is that multiple API replicas do not
share counters; use a shared limiter before horizontal scaling.

## Tests

The tests cover:

- seeded non-power-of-two brackets and automatic byes;
- wrong builds, wrong participants, and expired/invalid access;
- signed room allocation and result idempotency;
- 5,000 participant records without raw participant-code storage;
- 100 concurrent session/join calls;
- scheduled transitions, restart recovery, roster freezing, and assigned joins;
- every HTTP route used by the local operator/participant flow.
- public bracket rendering and the disposable scheduled demo.

Run them with:

```powershell
npm test
```

The Java lobby’s public-room list is not the tournament bracket. Public rooms
are practice rooms visible to everyone; tournament matches are created from
the frozen roster, assigned to private room slots, and reached through the
competitor portal. Cloud/VPS deployment, Nginx, process supervision, backups,
and load testing remain a separate staging gate.
