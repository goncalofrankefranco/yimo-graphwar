# YIMO tournament control service

This directory contains the Stage 5 tournament control service for YIMO
Graphwar 2.0. It handles organizer registration, seeded single-elimination
brackets, expiring match codes, participant sessions, bounded room allocation,
room heartbeats, and signed result records. Gameplay still belongs to the
authoritative Java room server.

## Requirements and commands

- Node.js 24.x. The service uses the built-in `node:sqlite` API and has no
  third-party runtime dependencies.
- `npm test` runs the service and HTTP smoke tests.
- `npm start` starts the HTTP service. The required environment variables are
  listed below.

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
| `GET /admin` | none | Small operator landing page |
| `GET /participant` | none | Small participant landing page |
| `POST /api/v1/admin/participants` | admin | Add an organizer-issued participant code |
| `POST /api/v1/admin/tournaments` | admin | Create a tournament and its room slots |
| `POST /api/v1/admin/bracket/seed` | admin | Seed the single-elimination bracket |
| `POST /api/v1/participant-sessions` | none | Exchange a participant code for a short-lived session |
| `POST /api/v1/matches/join` | session | Validate a match code and receive room access |
| `POST /api/v1/rooms/heartbeat` | room token | Keep an assigned room alive |
| `POST /api/v1/matches/{matchId}/result` | room token | Record the authoritative room result |
| `GET /api/v1/player/matches` | session bearer | List the participant's matches |

The core request sequence is:

1. The organizer creates participants, a tournament, and a seeded bracket.
2. The organizer distributes the returned match code to the assigned players.
3. A participant exchanges the organizer-issued participant code for a
   one-hour session.
4. The participant submits the match code and build information. The service
   allocates one available room slot and returns a per-participant signed room
   token.
5. The Java room server validates that token before exposing any room state.
6. The room sends heartbeats and submits exactly one result. An identical
   retry is idempotent; a conflicting retry is rejected.

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
- `tournaments`, `matches`, and `match_players`
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
- every HTTP route used by the local operator/participant flow.

Run them with:

```powershell
npm test
```

Cloud/VPS deployment, Nginx, process supervision, backups, and load testing
are deliberately Stage 7 work. This service is not a production endpoint
until that staging gate passes.
