# YIMO Graphwar 2.0 — Stages 5 and 6

This document records the implementation and operating contract for the
tournament control service and signed tournament rooms. It is written for the
GitHub source tree so another organizer can reproduce the local checks without
receiving any private deployment material.

## Scope and status

| Stage | Delivered | Gate |
| --- | --- | --- |
| 5 — tournament control | Node 24 service, SQLite schema/migration, scheduled lifecycle, registration, optional check-in, admin console, competitor portal, bracket seeding, sessions, joins, room slots, heartbeats, results, disposable demo, tests | Local service test suite passes |
| 6 — signed room access | Separate tournament ports, on-demand hidden Java room processes, candidate-bound HMAC access, fixed 1v1 rules, server-signed results and bracket advancement | Node and Java regression suites pass |

Stage 7 (Cloudzy staging and load testing) remains separate. No public VPS,
organizer token, participant code, HMAC secret, or private key belongs in this
repository.

## Runtime flow

```text
organizer -> /admin -> schedule + lifecycle action
participant -> /participant -> candidate-code login + one active bracket
scheduler/admin -> frozen roster -> bracket + assigned match
portal Join Room -> assigned-join API -> port + on-demand room process
desktop Join Room -> HTTPS candidate-code exchange -> signed room token
Java client -> HELLO -> TOURNAMENT_JOIN(token) -> hidden fixed-policy room
Java room server -> HMAC result -> bracket advancement + room release
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
`START_BLOCKED`. The organizer can recover it with **Reopen registration**;
that clears stale schedule/auto-start deadlines so the scheduler will not
immediately close it again. After registration closes, required check-in can
be manually closed from the admin panel before retrying the start. The
five-second scheduler is process-local but restart-safe because status and
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
protocolVersion|buildId|matchId|participantId|displayName|roomSlot|expiryMillis|nonce
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
- the nonce has not already been accepted by that room process;
- the same candidate is not already connected in that room.

The nonce set is process-local and persists for that room's lifetime. A
disconnect releases the candidate reservation, while replay protection still
rejects the old token. A newly issued token can reconnect the same candidate.

### Room process lifecycle

Practice rooms remain on `30000–30049`. Hidden tournament rooms use
`31000–31049`; the 1 GB staging configuration defaults to the single port
`31000`, so only one match process runs at a time. SQLite `room_slots` is the
allocation source of truth. On assignment, `TournamentRoomManager` starts
`RoomServer.TournamentRoomMain` with the match ID, exact port, server HMAC
secret, and YIMO network configuration. The process binds before reporting
ready and is never registered in the public lobby.

Tournament rooms admit two distinct candidates, one player/team/soldier each,
and ignore lobby setting changes. On a normal finish, the surviving team is
reported; a live-match disconnect is a forfeit. The Java server signs
`matchId|winnerParticipantId|loserParticipantId|reason|serverNonce` with
HMAC-SHA256. The API verifies the signature, advances the winner in the
bracket, marks the final tournament complete, and releases the room slot. The
Java process exits after reporting so the next match can reuse the port.

The portal polls the bracket and changes its primary action to **Join Room**
when a competitor's next match is ready. The player then enters the assigned
port and candidate code in the desktop game; no address or tournament ID is
needed.

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
`/participant`. The public route deliberately omits
match codes and room tokens.

Compile the Java source and tests with the Java 8 toolchain, then run:

```text
GraphServer.RoomAccessTokenTest
GraphServer.TournamentRoomAccessTest
GraphServer.TournamentRoomSettingsTest
GraphServer.TournamentResultReporterTest
```

The full regression suite must be run alongside those three Stage 6 checks.
The Java tests cover token issue/verify, expiry, wrong-secret and replay
rejection, duplicate-candidate rejection, immutable tournament room rules,
HMAC result reporting, and a client-room handshake that accepts a valid token
before room state is available.

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
