# YIMO Graphwar 2.0 — Stages 5 and 6

This document records the implementation and operating contract for the
tournament control service and signed tournament rooms. It is written for the
GitHub source tree so another organizer can reproduce the local checks without
receiving any private deployment material.

## Scope and status

| Stage | Delivered | Gate |
| --- | --- | --- |
| 5 — tournament control | Node 24 service, SQLite schema/migration, scheduled lifecycle, registration, optional check-in, admin console, competitor portal, bracket seeding, sessions, joins, room slots, heartbeats, results, disposable demo, tests | Local service test suite passes |
| 6 — signed room access | Java HMAC token verifier, replay protection, required room policy, bounded 20/50 room pool, protocol messages, tests | Java suite and two-sided token check pass |

Stage 7 (Cloudzy staging and load testing) remains separate. No public VPS,
organizer token, participant code, HMAC secret, or private key belongs in this
repository.

## Runtime flow

```text
organizer -> /admin -> schedule + lifecycle action
participant -> /participant -> session + register/check-in
scheduler/admin -> frozen roster -> bracket + assigned match
participant -> assigned-join API -> room slot + signed room token
Java client -> HELLO -> Java room server
Java client -> TOURNAMENT_JOIN(token) -> token gate -> room state
Java room server -> heartbeat/result API -> bracket advancement + room release
```

The tournament service owns identity, match assignment, and bracket state. The
Java room server remains authoritative for turn order, function validation,
collisions, damage, and anti-cheat decisions. A valid tournament token is an
additional admission credential; it does not make client-supplied gameplay
data authoritative.

## Stage 5: control service

The service lives in [`tournament/`](../tournament/) and intentionally uses
only Node 24 built-ins: `node:http`, `node:sqlite`, and `node:crypto`. There is
no `node_modules` requirement for the checked-in implementation.

The SQLite schema creates these records:

| Table | Role |
| --- | --- |
| `participants` | Active participant identity, keyed code lookup, and salted verification hash |
| `participant_sessions` | Expiring session-token hashes |
| `tournaments` | Build/protocol, room-port settings, lifecycle, and schedule |
| `tournament_entries` | Registration, check-in, seed, and eligibility state |
| `matches` | Bracket nodes, codes, assignment, and result signature |
| `match_players` | Participant-to-match membership and side |
| `room_slots` | Bounded `AVAILABLE`/`ASSIGNED`/`IN_PROGRESS` slot state |
| `audit_events` | Append-only operational trail |

### Brackets

Seeding pads the participant list to the next power of two. Missing slots
become automatic byes. A two-player match is `OPEN` immediately; later matches
become `OPEN` only when both winners are known. Match codes are ten characters
from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, excluding visually ambiguous `I`,
`O`, `0`, and `1`.

The service returns a match code when the match is created, but stores only its
SHA-256 hash. Codes expire at the configured match timeout or when the match is
closed. Participant codes have a keyed lookup hash plus salted `scrypt`
verification; session tokens are stored as SHA-256 hashes. The lookup hash
keeps participant login bounded by an indexed lookup instead of scanning all
5,000 records.

### Lifecycle and scheduling

New tournaments start in `DRAFT`. The admin can open/close registration,
open enabled check-in, or start manually from `/admin`. A scheduled tournament
uses `registrationOpenAt`, `registrationCloseAt`, optional
`checkInOpenAt`/`checkInCloseAt`, `startAt`, `autoStart`, and
`requireCheckIn`. Epoch seconds are stored in SQLite; the web forms accept
local `datetime-local` values and send converted epoch seconds.

At start, the service selects registered entries (or checked-in entries when
required) in deterministic registration order, assigns seeds, freezes the
roster, writes one bracket in the same SQLite transaction, and records an
audit event. A roster with fewer than two eligible entries becomes
`START_BLOCKED` and can be retried after an organizer corrects the roster.
The five-second scheduler is process-local but restart-safe because status and
bracket writes are persisted transactionally. Existing `SEEDED` tournaments
continue to use the legacy seed/match-code path.

### API contract

The complete route table and request sequence are in
[`tournament/README.md`](../tournament/README.md). In short:

- organizer endpoints require the configured bearer token;
- `/admin` provides schedule, lifecycle, roster, counts, and bracket controls;
  the organizer token is kept in browser `sessionStorage` only;
- `/participant` supports participant-code login, registration, check-in,
  private schedule, and assigned-match joining;
- `GET /api/v1/tournaments/{id}/bracket` returns a public, match-code-free
  bracket view for spectators and the participant page;
- participant session and join calls check build ID and protocol version;
- join checks participant membership before allocating a slot;
- a transaction prevents two simultaneous joins from taking the same room;
- a room heartbeat moves the match to `IN_PROGRESS`;
- a matching result retry returns the original signature;
- a conflicting result, invalid room token, expired code, or wrong participant
  is rejected.

## Stage 6: signed room access

### Wire messages

After the existing handshake, a required tournament room expects:

```text
TOURNAMENT_JOIN&<opaque-room-token>
```

It replies with:

```text
TOURNAMENT_ACCEPTED&<matchId>&<participantId>&<roomSlot>
```

or:

```text
TOURNAMENT_REJECTED
```

The gate runs in `ClientConnection` before `GraphServer.addClient` publishes
room state. Practice rooms keep the open policy and continue to work without a
tournament token. Old official clients fail the earlier YIMO build/protocol
handshake. The Java client API accepts the opaque token through the
`Graphwar.joinGame(host, port, playerName, tournamentToken)` overload; the
ordinary three-argument join path remains unchanged for practice rooms.

### Cross-language token format

The Node service and Java room server sign the same UTF-8 payload:

```text
protocolVersion|buildId|matchId|participantId|roomSlot|expiryMillis|nonce
```

The token is:

```text
base64url(payload-without-padding).base64url(HMAC-SHA256(payload, shared-secret))
```

`RoomAccessPolicy` accepts a token only when all of these are true:

- HMAC is valid;
- it is not expired;
- protocol and build match `Constants`;
- match ID and room slot match the process configuration;
- the nonce has not already been accepted by that room process.

The nonce set is process-local and is cleared when a room process is replaced.
The tournament service issues a fresh nonce for every participant join. A
future reconnect policy can issue a replacement token through the service; it
must not disable replay protection.

### Room pool

`TournamentRoomPool` models ports `30000–30049` with 20 warm slots by default.
It is synchronized and exposes these states:

```text
AVAILABLE -> ASSIGNED -> IN_PROGRESS -> AVAILABLE
                    \-> DRAINING -> AVAILABLE/OFFLINE (supervisor decision)

OFFLINE --expand--> AVAILABLE
```

The Node `room_slots` table mirrors the same initial 20/50 capacity for match
allocation. A room supervisor is responsible for starting a Java process on
the assigned port with a policy equivalent to:

```java
RoomAccessPolicy policy = RoomAccessPolicy.required(
    roomHmacSecret, matchId, roomSlot);
GraphServer server = new GraphServer(roomSlot, policy);
new Thread(server, "yimo-room-" + matchId).start();
```

Do not put `roomHmacSecret` in source or a client artifact. The existing
`RemoteGraphServer` also has a policy-aware constructor for deployments that
need its status bridge; public practice rooms continue using its open
constructor.

## Local verification

From the repository root:

```powershell
Push-Location tournament
npm test
Pop-Location
```

To exercise the UI and the full local request flow without a database or VPS,
run `npm run demo` from `tournament/`. It schedules four disposable
participants through registration, prints the short-lived demo values, and
serves the bracket at
`/participant?tournament=yimo-demo-2026`. The public route deliberately omits
match codes and room tokens.

Compile the Java source and tests with the Java 8 toolchain, then run:

```text
GraphServer.RoomAccessTokenTest
GraphServer.TournamentRoomPoolTest
GraphServer.TournamentRoomAccessTest
```

The full regression suite must be run alongside those three Stage 6 checks.
The Java test demonstrates token issue/verify, expiry, wrong-secret rejection,
nonce replay rejection, pool assignment/heartbeat/release, and a client-room
handshake that accepts a valid token before room state is available.

## Operations and rollback

- Keep the SQLite database outside the application directory and back it up
  while the service is stopped or after a verified SQLite backup operation.
- Keep the last approved Java artifact and the database backup together when
  changing the room supervisor.
- If a room heartbeat is lost, mark the slot `DRAINING`, stop the process,
  inspect its logs, and release/reassign only after the match state is known.
- Never manually edit a completed result. Use an authenticated organizer
  correction procedure in a later operations stage so the audit trail remains
  intact.
- The Stage 6 checkpoint tag is `yimo-2.0-stage-6-tournament-rooms`; the
  previous approved Stage 4 point is `yimo-2.0-stage-4-yimo-network`.

Cloudzy provisioning, Nginx/HTTPS, systemd units, firewall rules, backups,
metrics, and 100-player load testing belong to Stage 7 and must be reviewed
before tournament production use.
