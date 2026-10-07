# YIMO Graphwar tournament service

This Node.js 24 service owns tournament registration, the seeded single-elimination
bracket, room assignment, and result records. Java room processes remain
authoritative for game turns, collisions, eliminations, and winners.

## Run and test

```powershell
npm test
npm start
```

`npm run demo` starts an in-memory four-player bracket. The demo uses fake
participant codes and a fake room HMAC secret; do not reuse them in production.
Node's built-in SQLite module may print its experimental-feature warning.

The production service requires `YIMO_ADMIN_PASSWORD` and
`YIMO_ROOM_HMAC_SECRET`. It also accepts `YIMO_TOURNAMENT_DB`, `YIMO_BUILD_ID`,
`YIMO_PROTOCOL_VERSION`, `YIMO_TOURNAMENT_ROOM_COUNT`, `HOST`, and `PORT`.
`YIMO_TOURNAMENT_ROOM_COUNT` defaults to one and controls the default number of
simultaneously available tournament rooms (1–50). Keep secrets in the service's
environment file, never in source, a public page, or a client build.

On the VPS, `yimo-tournament.service` provides the room launcher variables:

```text
YIMO_JAVA_COMMAND=/opt/yimo/java8/bin/java
YIMO_TOURNAMENT_ROOM_SERVER_JAR=/opt/yimo-graphwar/current/roomServer.jar
```

The launcher inherits `YIMO_ROOM_HMAC_SECRET` from the service environment and
starts one hidden Java process per assigned match. The 1 GB staging setup
defaults to one tournament port (`31000`); a 2 GB staging setup can use four
rooms by setting `YIMO_TOURNAMENT_ROOM_COUNT=4` in `/etc/yimo/tournament.env`.
That is a cautious starting capacity, not a 200-player load-test guarantee;
the current 1 vCPU can become the bottleneck before RAM. Each room count
reserves a consecutive port range beginning at `31000`.
Practice rooms use `30000–30049`; tournament-only room ports use
`31000–31049`. The firewall must allow both ranges, but the tournament range is
never published in the public lobby.

## Competitor flow

There is one active tournament at a time. Competitors open `/participant`; the
page discovers the active event automatically, shows its public bracket, and
polls their schedule every seven seconds. They enter their organizer-issued
candidate code and display name to log in. One button changes with the event:
register, check in, wait for an opponent, or join the assigned match. The page
does not ask for a tournament ID or generate a local player ID.

When a match is ready:

1. The competitor clicks **Join Room** in the portal. This allocates the room
   and displays its tournament port.
2. In the desktop app, they choose **Join Room**, enter that port, and enter
   their candidate code. The candidate-code box appears only for ports in
   `31000–31049`; the server hostname is fixed to
  `graphwar-server.yimo-official.org`.
3. The desktop client sends the code over HTTPS to `/api/v1/game/join`. The
   service checks the active tournament, eligibility, assigned match, build,
   and port, then returns a short-lived signed room token and the official
   display name. The client passes the opaque token to the Java room server.
4. After a verified result, the bracket advances in SQLite. The still-open
   portal refreshes automatically and presents **Join Room** once the next
   match is ready; it does not launch the desktop app automatically.

## Room security and results

Each tournament room process is bound to one match ID and one reserved port.
It accepts only signed tokens with the expected build, match, slot, expiry,
and unused nonce. It admits two distinct candidates, one player each, fixes
their teams and soldier count, and ignores client attempts to change game mode,
trajectory, preview, turn duration, map, teams, or roster. The room is not
registered in the public lobby.

The winning team is derived from server-owned elimination state. The room
server posts `matchId`, winner, loser, reason, a random server nonce, and
`base64url(HMAC-SHA256(matchId|winner|loser|reason|nonce, secret))` to the result
endpoint. The tournament service rejects participant room tokens as result
authority. Identical signed retries are idempotent; conflicting results are
rejected. Completing the final match sets the tournament to `COMPLETED`, which
frees the single-active slot. A disconnect during a live match is reported as
a server-authoritative forfeit.

The hard match timer starts when the tournament room is assigned, not when the
bracket is seeded or a competitor logs in. It is capped at 20 minutes. If no
verified result arrives by that deadline, the service uses a cryptographically
secure random choice between the two assigned competitors, records a signed
`TIMEOUT_RANDOM` result, advances the bracket, and stops the room process.
Unassigned matches do not consume play time; expired access can be reissued
before a room is assigned, but an assigned match's deadline cannot be extended.

## HTTP routes

| Method and route | Auth | Purpose |
| --- | --- | --- |
| `GET /healthz` | none | Build/protocol health check |
| `GET /admin` | page public; API protected | Organizer console, automatically loads the current tournament |
| `GET /participant` | none | Competitor portal and public bracket |
| `GET /api/v1/tournaments/active` | none | Current public bracket or `null` |
| `GET /api/v1/admin/tournament/current` | organizer bearer | Resume the current active tournament, latest draft, or latest completed tournament without its ID |
| `POST /api/v1/admin/participants` | organizer bearer | Add an organizer-issued candidate code |
| `POST /api/v1/admin/tournaments` | organizer bearer | Create the next tournament and its room slots |
| `DELETE /api/v1/admin/tournaments/{id}` | organizer bearer | Delete a non-running tournament and its entries/bracket; retain an audit event |
| `POST /api/v1/admin/tournaments/{id}/...` | organizer bearer | Registration, check-in, and start controls |
| `POST /api/v1/admin/tournaments/{id}/matches/extend-expired` | organizer bearer | Reissue expired access for unassigned matches |
| `POST /api/v1/admin/matches/{id}/forfeit-expired` | organizer bearer | Organizer override for an expired match before automatic timeout resolution |
| `POST /api/v1/admin/matches/{id}/release-room` | organizer bearer | Stop the Java room after the result is recorded |
| `POST /api/v1/participant-sessions` | candidate code | Create a short-lived player session and update display name |
| `POST /api/v1/tournaments/{id}/register` | player session | Register the candidate |
| `POST /api/v1/tournaments/{id}/check-in` | player session | Check in the candidate |
| `GET /api/v1/player/tournaments/{id}` | player session | Private entry and next-match state |
| `POST /api/v1/matches/{id}/join-assigned` | player session | Allocate/launch the assigned tournament room |
| `POST /api/v1/game/join` | candidate code | Desktop candidate-code-to-room-token exchange |
| `POST /api/v1/rooms/heartbeat` | room token | Room status heartbeat |
| `POST /api/v1/matches/{id}/result` | room-server HMAC | Verify and record the authoritative result |

The old unauthenticated local-player-ID self-registration route is removed.
Participant codes are stored as a keyed lookup hash plus salted `scrypt`
verification; session tokens and room tokens are never returned by public
bracket routes.

The organizer console can delete drafts, registration/check-in/ready events,
start-blocked events, and completed tournaments. A running tournament or one
with an assigned/in-progress/draining room is protected. Deletion removes the
tournament's entries, room slots, and bracket rows while retaining a
`TOURNAMENT_DELETED` audit event. The latest completed tournament remains
loadable in the organizer console so it can be removed when desired.

## Database and tests

SQLite enables foreign keys, a five-second busy timeout, and WAL for file-backed
databases. The service stores participants, sessions, tournaments, entries,
matches, match players, room slots, and audit events. The active-tournament
guard and bracket updates run inside database transactions; the active public
endpoint contains display names and match states only.

`npm test` covers the 5,000-participant storage check, 100 concurrent joins,
automatic scheduling, single-active lifecycle, forwarded-IP rate limiting,
signed result verification, final-bracket completion, room assignment, the
candidate-code desktop endpoint, and removal of local-ID registration.
