# YIMO Tournament Lifecycle, Scheduling, and Competitor Portal

## Goal

Turn the existing YIMO tournament control service from an API-only bracket
seed into a usable competition workflow. Organizers must be able to create,
schedule, open, and start a tournament from the admin page. Competitors must
be able to register, check in when required, see their schedule and bracket,
and join their assigned match without manually copying an opaque room code.

The Java YIMO lobby will expose a `Tournament` entry that opens the
competitor portal. The tournament control plane remains a separate Node 24
service; the Java room server remains authoritative for gameplay and results.

## Root cause and current behavior

The current deployed service has these capabilities:

- create a tournament through `POST /api/v1/admin/tournaments`;
- seed a single-elimination bracket through
  `POST /api/v1/admin/bracket/seed`;
- expose a read-only public bracket after a bracket already exists;
- validate sessions, joins, room tokens, heartbeats, and results.

It does not have tournament lifecycle state, competitor-to-tournament entries,
schedule timestamps, a scheduler, registration/check-in routes, or a start
button. The current `/admin` page only documents API routes. The Java lobby
screen lists public rooms and online players and is not an organizer console.

## Approved decisions

- Keep seeded single elimination as the initial format.
- Keep the existing ten-player room capacity and bounded room pool.
- Use UTC epoch seconds in the database and ISO-8601 timestamps in the UI.
- Use the existing organizer bearer token for admin actions.
- Use the existing short-lived participant session for competitor actions.
- Keep match codes as a fallback protocol; the competitor portal can join by
  authenticated assignment so codes do not need to be copied manually.
- Make check-in configurable and default it to disabled for compatibility with
  existing tournaments. When enabled, only checked-in competitors enter the
  starting roster.
- Do not expose admin tokens, room HMAC secrets, raw participant codes, or raw
  room tokens in public pages or source releases.
- Do not replace the authoritative Java room result path.

## Tournament lifecycle

The service stores an explicit status and advances it atomically:

```text
DRAFT -> REGISTRATION_OPEN -> CHECK_IN -> READY -> RUNNING -> COMPLETED
  |             |                 |          |
  +-------------+-----------------+----------+--> START_BLOCKED
```

`CHECK_IN` is used only when `requireCheckIn` is enabled. If it is disabled,
registration closes directly into `READY`. `START_BLOCKED` means the scheduled
or manual start had fewer than two eligible competitors; an organizer must add
eligible competitors and press Start again. A start is idempotent:
repeating it after `RUNNING` or `COMPLETED` returns the current state without
creating a second bracket.

The legal transitions are:

| Current state | Manual action | Automatic action |
| --- | --- | --- |
| `DRAFT` | Open registration | Move to `REGISTRATION_OPEN` at `registrationOpenAt` |
| `REGISTRATION_OPEN` | Close registration | Move to `CHECK_IN` at `checkInOpenAt`, or `READY` at `registrationCloseAt` when check-in is disabled |
| `CHECK_IN` | Close check-in | Move to `READY` |
| `READY` | Start tournament | Start at `startAt` when `autoStart` is enabled |
| `START_BLOCKED` | Start tournament after roster correction | Retry at the next scheduler tick when `autoStart` remains enabled |

The scheduler does not silently open registration when no schedule was
provided; an organizer must use the explicit Open registration action in that
case. A scheduled start is attempted only from `READY` (or after the
check-in-disabled close transition), so a missed check-in window cannot bypass
the configured requirement.

The schedule contains:

- `registrationOpenAt`;
- `registrationCloseAt`;
- `checkInOpenAt` and `checkInCloseAt` when check-in is enabled;
- `startAt`;
- `autoStart`; when false, the organizer must press Start.

The scheduler runs inside the existing Node service and checks due events on a
short interval. Every transition is also evaluated when the service starts and
when an admin or competitor requests the tournament. This makes a restart
safe: a missed interval is recovered from persisted timestamps, and a SQLite
transaction prevents duplicate starts.

At start, the service:

1. selects registered competitors, or checked-in competitors when required;
2. rejects the start with `START_BLOCKED` when fewer than two are eligible;
3. freezes the roster and assigns deterministic seed positions;
4. creates the existing single-elimination bracket and automatic byes;
5. creates the first match assignments and room availability;
6. changes the tournament to `RUNNING` and records an audit event.

## Data model and migration

Existing SQLite databases remain readable. Startup applies a small, idempotent
migration before serving requests.

Add schedule/lifecycle fields to `tournaments`:

- `require_check_in INTEGER NOT NULL DEFAULT 0`;
- `registration_open_at INTEGER`;
- `registration_close_at INTEGER`;
- `check_in_open_at INTEGER`;
- `check_in_close_at INTEGER`;
- `start_at INTEGER`;
- `auto_start INTEGER NOT NULL DEFAULT 0`;
- `started_at INTEGER`;
- `started_by TEXT`;
- `roster_frozen_at INTEGER`.

Add `tournament_entries`:

```text
tournament_id TEXT REFERENCES tournaments(tournament_id)
participant_id TEXT REFERENCES participants(participant_id)
entry_status TEXT            -- REGISTERED, CHECKED_IN, WITHDRAWN, ELIMINATED
registered_at INTEGER
checked_in_at INTEGER
seed INTEGER
PRIMARY KEY(tournament_id, participant_id)
```

Add indexes for `(tournament_id, entry_status)` and
`(participant_id, tournament_id)`. Existing `matches`, `room_slots`, and
`audit_events` remain the source of truth for bracket and room state.

## API contract

Existing create, seed, join, heartbeat, result, and public bracket endpoints
remain compatible. Add these routes:

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/admin/tournaments/{id}` | admin | Full organizer status and counts |
| `POST` | `/api/v1/admin/tournaments/{id}/registration/open` | admin | Open registration |
| `POST` | `/api/v1/admin/tournaments/{id}/registration/close` | admin | Close registration |
| `POST` | `/api/v1/admin/tournaments/{id}/check-in/open` | admin | Open check-in |
| `POST` | `/api/v1/admin/tournaments/{id}/start` | admin | Start now using the same atomic start path |
| `POST` | `/api/v1/tournaments/{id}/register` | session | Register the current competitor |
| `POST` | `/api/v1/tournaments/{id}/check-in` | session | Check in the current competitor |
| `GET` | `/api/v1/player/tournaments/{id}` | session | Schedule, entry, next match, and bracket summary |
| `POST` | `/api/v1/matches/{matchId}/join-assigned` | session | Join the authenticated competitor's assigned match |

Admin actions validate legal status transitions and return the resulting
tournament state. Competitor registration is idempotent. Check-in is rejected
after the check-in deadline or roster freeze. Assigned join verifies that the
session participant is one of the match players before issuing the same signed
room token as the existing match-code path.

## Admin and competitor UI

### Admin page

`/admin` becomes a functional organizer console with:

- organizer token input held only in `sessionStorage`;
- tournament creation form with name, schedule, check-in toggle, and room
  settings;
- lifecycle buttons: `Open registration`, `Open check-in`, `Start tournament`;
- roster and check-in counts;
- visible lifecycle state and schedule in the organizer's local time;
- bracket status and match-result refresh;
- clear errors for too few competitors, closed windows, invalid transitions,
  and duplicate starts.

The page never displays raw room HMAC secrets. Public bracket data remains
match-code-free.

### Competitor page

`/participant` becomes a real portal with:

- participant-code login;
- tournament ID selection or an organizer-provided tournament link;
- registration and check-in actions;
- countdown/status text for registration, check-in, and start;
- the competitor's next match and schedule;
- a direct `Join assigned match` action when the match is open;
- the existing responsive public bracket view.

The Java lobby adds a `Tournament` button that opens the configured tournament
API base URL in the system browser. It does not embed admin credentials in the
Java client.

## Error handling and safety

- All lifecycle changes use a SQLite transaction and audit event.
- A scheduled start cannot create a second bracket if an admin starts at the
  same time.
- A tournament cannot start with fewer than two eligible competitors.
- The roster is immutable after start; later registrations are rejected.
- Participant sessions and assigned joins continue to enforce build and
  protocol compatibility.
- Public endpoints return names/statuses only; no match codes, room tokens,
  participant codes, or secrets.
- Existing legacy tournaments with `SEEDED` status remain viewable and their
  match/result flow remains usable.

## Testing and acceptance gates

Add assertion-based service tests for:

- registration and idempotent duplicate registration;
- check-in enabled/disabled behavior and deadlines;
- every legal and illegal lifecycle transition;
- fake-clock automatic opening, check-in, and start;
- restart recovery for a due schedule;
- atomic manual-versus-scheduled start;
- roster freeze and deterministic seeding;
- automatic byes and winner advancement;
- assigned join authorization;
- legacy seeded tournament compatibility.

Add HTTP tests for:

- admin page containing the lifecycle controls;
- competitor page containing registration, check-in, schedule, and join
  controls;
- all new routes and their auth/error responses.

Acceptance requires a local eight-player scheduled tournament to move from
registration to running, display its bracket, allow a competitor to join an
assigned match, accept one authoritative result, and advance the next round.
The staging VPS will then receive a disposable test tournament only; no real
competitor codes are used during verification.

## Non-goals

- Double elimination, Swiss rounds, payments, prizes, or public admin access.
- Replacing the Java gameplay server with Node.
- Treating the Java public-room list as the tournament bracket.
- Persisting raw participant or room credentials.
