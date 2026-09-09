# YIMO Tournament Lifecycle, Scheduling, and Competitor Portal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Add a persistent YIMO tournament lifecycle with registration, optional check-in, automatic scheduling, admin start controls, competitor match access, bracket advancement, and a Java-lobby link to the competitor portal.

**Architecture:** Keep the Node 24/SQLite service as the tournament control plane and the Java room server authoritative for gameplay. Add a pure lifecycle module, migrate the existing SQLite schema in place, expose authenticated admin and participant APIs, run an idempotent scheduler inside the existing Node process, and render the admin/competitor flows as same-origin HTML pages. Keep the Java client integration to one browser-launch button so tournament credentials never enter the game client.

**Tech Stack:** Node.js 24 built-in node:sqlite, node:http, node:crypto, and node:test; Java 8 Swing/AWT; existing TCP protocol and signed room tokens; SQLite WAL; PowerShell release/deployment scripts; plain assertion-based tests.

**Spec:** docs/superpowers/specs/2026-09-08-yimo-tournament-lifecycle-design.md

## Global Constraints

- Keep seeded single elimination as the initial format.
- Keep the existing ten-player room capacity and bounded room pool.
- Use UTC epoch seconds in the database and ISO-8601 timestamps in the UI.
- Use the existing organizer bearer token for admin actions.
- Use the existing short-lived participant session for competitor actions.
- Keep match codes as a fallback protocol; the competitor portal may join by authenticated assignment.
- Make check-in configurable and default it to disabled for compatibility with existing tournaments.
- Do not expose admin tokens, room HMAC secrets, raw participant codes, or raw room tokens in public pages or source releases.
- Do not replace the authoritative Java room result path.
- Keep Java source and bytecode compatible with Java 8.
- Use no external Node, Java, UI, or browser dependencies.
- Preserve legacy SEEDED tournaments and the existing API paths.
- Use TDD: each behavior starts with a failing test, then the minimum implementation, then the focused and full suites.
- Current clean baseline is commit 7c8b1ff on the single main branch.
- Staging server for the final gate is 172.86.74.172; its SSH key remains local and its secrets stay outside Git.

---

## File map and responsibilities

### Tournament control plane

- Create tournament/src/lifecycle.ts for status names, legal transitions, and due-event decisions with no database access.
- Create tournament/src/migrations.ts for idempotent SQLite column/table/index migration for existing databases.
- Modify tournament/src/service.ts for schedule fields, entries, lifecycle operations, automatic start, participant schedule, assigned joins, and projections.
- Modify tournament/src/server.ts for route parsing, auth routing, and JSON responses.
- Create tournament/src/pages.ts for functional /admin and /participant HTML/CSS/JavaScript pages.
- Modify tournament/src/main.ts to start and stop the scheduler interval.
- Modify tournament/src/demo.ts and tournament/src/demo-main.ts for a disposable scheduled tournament.

### Tests and documentation

- Create tournament/test/lifecycle.test.ts, migration.test.ts, and scheduler.test.ts.
- Extend tournament/test/tournament.test.ts, http.test.ts, and demo.test.ts.
- Create test/Graphwar/GlobalTournamentLinkTest.java.
- Modify tournament/README.md, docs/STAGES-5-6.md, README.md, docs/PROJECT-MAP.md, deploy/README.md, and docs/STAGE-8-RELEASE.md.

### Java client and deployment

- Modify src/Graphwar/GlobalScreen.java to add the competitor portal link.
- Use the existing Cloudzy service units and release scripts; do not commit generated artifacts or secrets.

---

### Task 1: Add pure lifecycle rules and SQLite migration

**Files:**
- Create: tournament/src/lifecycle.ts
- Create: tournament/src/migrations.ts
- Create: tournament/test/lifecycle.test.ts
- Create: tournament/test/migration.test.ts
- Modify: tournament/src/service.ts around the schema and constructor

**Interfaces produced by lifecycle.ts:**

~~~ts
export type TournamentStatus =
  | 'DRAFT' | 'REGISTRATION_OPEN' | 'CHECK_IN' | 'READY'
  | 'RUNNING' | 'START_BLOCKED' | 'COMPLETED' | 'SEEDED';

export interface ScheduleRow {
  status: TournamentStatus;
  requireCheckIn: boolean;
  autoStart: boolean;
  registrationOpenAt: number | null;
  registrationCloseAt: number | null;
  checkInOpenAt: number | null;
  checkInCloseAt: number | null;
  startAt: number | null;
}

export function assertTransition(from: TournamentStatus, to: TournamentStatus): void;
export function dueTransition(row: ScheduleRow, now: number): TournamentStatus | null;
export function canStart(status: TournamentStatus): boolean;
~~~

~~~ts
export function applyTournamentMigrations(db: any): void;
~~~

- [ ] Step 1: Write failing lifecycle tests. Assert scheduled transitions DRAFT to REGISTRATION_OPEN, REGISTRATION_OPEN to READY when check-in is disabled, and READY to RUNNING when autoStart is true. Assert invalid transitions such as RUNNING to DRAFT and COMPLETED to RUNNING throw a 409-compatible error.
- [ ] Step 2: Run the focused test and verify the expected missing-module failure.

~~~powershell
Push-Location tournament
npm test -- --test-name-pattern="lifecycle"
Pop-Location
~~~

- [ ] Step 3: Write failing migration tests. Build a legacy in-memory schema, apply the migration twice, then assert the schedule columns, tournament_entries table, and idx_tournament_entries_status index exist without duplicates.
- [ ] Step 4: Implement the pure lifecycle module. Use an explicit transition table. dueTransition returns at most one due transition per call, does not open registration without registrationOpenAt, and does not auto-start when autoStart is false.
- [ ] Step 5: Implement idempotent migrations. Query PRAGMA table_info(tournaments) before each missing ALTER TABLE; create tournament_entries and indexes with IF NOT EXISTS. Call the migration immediately after the current schema initialization in the service constructor.
- [ ] Step 6: Run npm test from tournament; all new and existing tests must pass.
- [ ] Step 7: Commit.

~~~powershell
git add tournament/src/lifecycle.ts tournament/src/migrations.ts tournament/src/service.ts tournament/test/lifecycle.test.ts tournament/test/migration.test.ts
git commit -m "Add tournament lifecycle rules and database migration"
~~~

---

### Task 2: Add schedule creation, entries, registration, and check-in

**Files:**
- Modify: tournament/src/service.ts
- Extend: tournament/test/tournament.test.ts

**Interfaces:**

~~~ts
createTournament(adminToken: string | undefined, input: TournamentInput & {
  registrationOpenAt?: number;
  registrationCloseAt?: number;
  checkInOpenAt?: number;
  checkInCloseAt?: number;
  startAt?: number;
  autoStart?: boolean;
  requireCheckIn?: boolean;
}): { tournamentId: string; status: string };

openRegistration(adminToken: string | undefined, tournamentId: string): Record<string, unknown>;
closeRegistration(adminToken: string | undefined, tournamentId: string): Record<string, unknown>;
openCheckIn(adminToken: string | undefined, tournamentId: string): Record<string, unknown>;
registerParticipant(sessionToken: string, tournamentId: string): Record<string, unknown>;
checkInParticipant(sessionToken: string, tournamentId: string): Record<string, unknown>;
adminTournament(adminToken: string | undefined, tournamentId: string): Record<string, unknown>;
playerTournament(sessionToken: string, tournamentId: string): Record<string, unknown>;
~~~

- [ ] Step 1: Write failing entry tests. Create a draft tournament and two participant sessions. Assert duplicate registration is idempotent, registration before REGISTRATION_OPEN and after READY is rejected, and check-in is rejected when requireCheckIn=false.
- [ ] Step 2: Run npm test -- --test-name-pattern="registration" and verify the new methods/schema are missing.
- [ ] Step 3: Extend createTournament. Persist schedule fields, auto_start, require_check_in, and DRAFT status. Reject non-integer timestamps, inverted windows, a start before registration closes, and autoStart=true without startAt.
- [ ] Step 4: Add lifecycle actions. Each action requires the admin bearer, validates the transition, updates status and updated_at inside transaction, and records an audit event. closeRegistration goes to CHECK_IN only when check-in is enabled; otherwise it goes to READY.
- [ ] Step 5: Add participant entries. Resolve the participant through the existing session helper. Use INSERT OR IGNORE for registration, update only REGISTERED rows for check-in, and reject expired windows, disabled check-in, inactive tournaments, and frozen rosters. Never return participant codes.
- [ ] Step 6: Add projections. adminTournament returns lifecycle, schedule, counts, roster, and public bracket. playerTournament returns the current participant's entry, schedule, next match, and public bracket without match codes or tokens.
- [ ] Step 7: Run the full Node suite and verify legacy bracket/security behavior remains green.
- [ ] Step 8: Commit.

~~~powershell
git add tournament/src/service.ts tournament/test/tournament.test.ts
git commit -m "Add tournament registration and check-in state"
~~~

---

### Task 3: Implement atomic manual start and automatic scheduling

**Files:**
- Modify: tournament/src/service.ts
- Modify: tournament/src/main.ts
- Extend: tournament/test/tournament.test.ts
- Create: tournament/test/scheduler.test.ts

**Interfaces:**

~~~ts
startTournament(adminToken: string | undefined, tournamentId: string): Record<string, unknown>;
processScheduledEvents(): number;
~~~

- [ ] Step 1: Write failing manual-start tests. Starting a READY tournament with four entries must return RUNNING, create exactly one bracket, freeze the roster, and reject later registrations. Fewer than two eligible entries must return START_BLOCKED; a repeated start must not add matches.
- [ ] Step 2: Write failing fake-clock tests. With registrationOpenAt=100, registrationCloseAt=200, startAt=300, and autoStart=true, call processScheduledEvents at each boundary twice and assert each transition/bracket occurs once. Reconstruct the service over the same SQLite file at time 300 and assert restart recovery does not duplicate the bracket.
- [ ] Step 3: Run npm test -- --test-name-pattern="start|schedule|scheduler" and verify the missing methods fail.
- [ ] Step 4: Extract the current bracket insertion body into a private transaction-safe helper. Keep the public legacy seedBracket response and validation; make startTournament call the helper without nested BEGIN IMMEDIATE.
- [ ] Step 5: Implement atomic start. In one transaction reload the row, return existing state for RUNNING/COMPLETED, set START_BLOCKED when eligible count is below two, select entries ordered by registered_at and participant_id, assign seeds, seed the bracket, set roster_frozen_at/started_at/started_by, set RUNNING, and audit TOURNAMENT_STARTED.
- [ ] Step 6: Implement due events. Apply one pure transition per transaction until no event is due. Route a due READY to RUNNING transition through startTournament. Keep autoStart=1 after START_BLOCKED so a later corrected roster can retry.
- [ ] Step 7: Add the main-process interval. After the HTTP server starts, run processScheduledEvents every 5 seconds, call unref(), log errors, and clear the interval on SIGINT/SIGTERM.
- [ ] Step 8: Run npm test; all scheduler, bracket, result-idempotency, 5,000-participant, and concurrency tests must pass.
- [ ] Step 9: Commit.

~~~powershell
git add tournament/src/service.ts tournament/src/main.ts tournament/test/tournament.test.ts tournament/test/scheduler.test.ts
git commit -m "Add atomic tournament start and automatic scheduling"
~~~

---
### Task 4: Add authenticated API routes and functional admin console

**Files:**
- Create: tournament/src/pages.ts
- Modify: tournament/src/server.ts
- Extend: tournament/test/http.test.ts

**Interfaces:**

pages.ts exports:

~~~ts
export const ADMIN_PAGE: string;
export const PARTICIPANT_PAGE: string;
~~~

server.ts adds these route patterns:

~~~text
GET  /api/v1/admin/tournaments/{id}
POST /api/v1/admin/tournaments/{id}/registration/open
POST /api/v1/admin/tournaments/{id}/registration/close
POST /api/v1/admin/tournaments/{id}/check-in/open
POST /api/v1/admin/tournaments/{id}/start
~~~

- [ ] Step 1: Write failing HTTP route tests. Create a draft tournament, call the admin lifecycle routes with Authorization: Bearer admin-test-token, and assert 200 responses and state changes. Assert missing/wrong bearer tokens return 401. Assert /admin contains Start tournament, Open registration, and schedule controls rather than only API documentation.
- [ ] Step 2: Run npm test -- --test-name-pattern="admin|lifecycle|routes" and verify new routes return 404 and the current admin page lacks controls.
- [ ] Step 3: Move the existing PAGE_STYLE and bracket renderer into pages.ts, preserving the current public bracket behavior. Keep HTML same-origin and use no CDN assets. Escape server-provided strings before inserting them into HTML or JavaScript.
- [ ] Step 4: Implement strict route parsing for /api/v1/admin/tournaments/{id}. Call bearer(request), route each action to the service, and return the existing JSON error shape for ServiceError. Keep existing create/participant/seed routes unchanged.
- [ ] Step 5: Build the admin console. Add token input, tournament creation fields, schedule fields using datetime-local, check-in toggle, lifecycle buttons, counts, status, schedule, roster, bracket, and visible server errors. Convert local date input to epoch seconds in browser JavaScript. Store the token only in sessionStorage. Refresh GET /api/v1/admin/tournaments/{id} after each action.
- [ ] Step 6: Run npm test. Expected: all API route tests, page smoke tests, bracket tests, and security tests pass.
- [ ] Step 7: Commit.

~~~powershell
git add tournament/src/pages.ts tournament/src/server.ts tournament/test/http.test.ts
git commit -m "Add tournament lifecycle API and admin console"
~~~

---

### Task 5: Add competitor registration portal and assigned-match joining

**Files:**
- Modify: tournament/src/service.ts
- Modify: tournament/src/server.ts
- Modify: tournament/src/pages.ts
- Extend: tournament/test/tournament.test.ts
- Extend: tournament/test/http.test.ts

**Interfaces:**

~~~ts
joinAssignedMatch(input: {
  sessionToken: string;
  matchId: string;
  buildId: string;
  protocolVersion: number;
  clientKey?: string;
}): { matchId: string; roomSlot: number; port: number; roomToken: string; expiresAt: number };
~~~

Add routes:

~~~text
POST /api/v1/tournaments/{id}/register
POST /api/v1/tournaments/{id}/check-in
GET  /api/v1/player/tournaments/{id}
POST /api/v1/matches/{matchId}/join-assigned
~~~

- [ ] Step 1: Write failing assigned-join tests. Create a running tournament, register two sessions, retrieve one participant schedule, and assert joinAssignedMatch returns the same room-slot/token shape as joinMatch. Assert an unrelated participant returns 403 PARTICIPANT_NOT_IN_MATCH, wrong build returns VERSION_MISMATCH, and a repeated join reuses the assigned room.
- [ ] Step 2: Run npm test -- --test-name-pattern="assigned|participant|join" and verify the new service method/routes do not exist.
- [ ] Step 3: Implement assigned joining by reusing the existing room transaction. Load the match by validated ID, resolve the session participant, require match status OPEN/ASSIGNED/IN_PROGRESS, require participant membership, and use the existing room allocation/token issuance helper. Never accept a client-supplied participant ID.
- [ ] Step 4: Implement participant routes with session bearer auth. Return entry status, schedule, next match, and public bracket. Keep room tokens out of GET /api/v1/player/tournaments/{id}; return one only from a successful join action.
- [ ] Step 5: Build the competitor portal. Add participant-code login, tournament ID input, register/check-in buttons, lifecycle/countdown status, next-match card, assigned-join button, and the existing bracket renderer. Store the session token only in sessionStorage and clear it on logout.
- [ ] Step 6: Run npm test. Expected: registration, check-in, lifecycle, assigned join, public bracket, and legacy API tests pass.
- [ ] Step 7: Commit.

~~~powershell
git add tournament/src/service.ts tournament/src/server.ts tournament/src/pages.ts tournament/test/tournament.test.ts tournament/test/http.test.ts
git commit -m "Add competitor registration and assigned match access"
~~~

---

### Task 6: Add the Java YIMO lobby tournament entry

**Files:**
- Modify: src/Graphwar/GlobalScreen.java around button fields, layout, and actionPerformed
- Create: test/Graphwar/GlobalTournamentLinkTest.java

**Interfaces:**

Add this package-private pure helper:

~~~java
static String tournamentPortalUrl(String baseUrl)
~~~

It trims one trailing slash and returns baseUrl + "/participant"; null/empty input throws IllegalArgumentException.

- [ ] Step 1: Write the failing Java test.

~~~java
check(GlobalScreen.tournamentPortalUrl("http://172.86.74.172").equals(
        "http://172.86.74.172/participant"), "portal URL");
check(GlobalScreen.tournamentPortalUrl("http://host/").equals(
        "http://host/participant"), "trailing slash");
check(GlobalScreen.menuLabels().contains("Tournament"), "lobby label");
~~~

- [ ] Step 2: Compile/run the focused test with Java 8 and verify the helper/label failures.
- [ ] Step 3: Add a Tournament button beside the existing Create Room action. On click, call Desktop.isDesktopSupported() and Desktop.getDesktop().browse(URI.create(GlobalScreen.tournamentPortalUrl(Constants.TOURNAMENT_API_BASE_URL))). If browsing is unavailable, show the portal URL in the existing lobby status text. Do not add admin credentials to Swing.
- [ ] Step 4: Run the new test and the complete Java suite; existing room/chat behavior must remain unchanged.
- [ ] Step 5: Commit.

~~~powershell
git add src/Graphwar/GlobalScreen.java test/Graphwar/GlobalTournamentLinkTest.java
git commit -m "Add tournament portal entry to the YIMO lobby"
~~~

---
### Task 7: Add scheduled local demo, end-to-end tests, and documentation

**Files:**
- Modify: tournament/src/demo.ts
- Modify: tournament/src/demo-main.ts
- Create: tournament/test/scheduled-demo.test.ts
- Modify: tournament/README.md
- Modify: docs/STAGES-5-6.md
- Modify: README.md
- Modify: docs/PROJECT-MAP.md

- [ ] Step 1: Write a failing scheduled-demo test using a fake clock. Assert the demo starts DRAFT, becomes REGISTRATION_OPEN, registers four sample participants, reaches READY, and becomes RUNNING after the scheduled start. Assert the public bracket has expected rounds and no match codes.
- [ ] Step 2: Run npm test -- --test-name-pattern="demo" and verify the current already-seeded demo does not satisfy the lifecycle.
- [ ] Step 3: Update the demo fixture to use scheduled service APIs with a deterministic clock. Print the local URL, demo admin token, participant codes, and tournament ID only in demo mode. Do not add a production bypass to main.ts.
- [ ] Step 4: Add an end-to-end HTTP assertion that registers competitors, advances the scheduler, loads /participant?tournament=..., calls assigned join, submits one signed test result, and verifies the next match.
- [ ] Step 5: Document lifecycle states, admin/competitor flows, schedule fields, demo command, public bracket behavior, migration backup, rollback, and the distinction between the Java public-room list and the tournament bracket.
- [ ] Step 6: Run npm test, git diff --check, and scan changed docs for unresolved template markers or credentials.
- [ ] Step 7: Commit.

~~~powershell
git add tournament/src/demo.ts tournament/src/demo-main.ts tournament/test/scheduled-demo.test.ts tournament/README.md docs/STAGES-5-6.md README.md docs/PROJECT-MAP.md
git commit -m "Document and test scheduled tournament flow"
~~~

---
### Task 8: Stage the migration and run the disposable VPS tournament test

**Files:**
- Modify: deploy/README.md
- Modify: docs/STAGE-8-RELEASE.md
- Use: deploy/cloudzy/install-release.sh, yimo-tournament.service, yimo-global.service, and yimo-public-rooms.service
- Output: ignored build/deployment directories only

- [ ] Step 1: Run the local release gate before touching the VPS.

~~~powershell
Push-Location tournament
npm test
Pop-Location
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\test-stage8.ps1
~~~

Compile/run the complete Java suite, including GlobalTournamentLinkTest, with zero failures.
- [ ] Step 2: Build a server release from the approved commit.

~~~powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\build-stage7-release.ps1 -OutputDir .\build\yimo-stage7-current-commit
~~~

Upload only that release directory and deployment scripts. Never upload .env.local, /root/yimo-admin-token.txt, tournament.sqlite, or private keys.
- [ ] Step 3: Back up the existing SQLite database over SSH. Stop only yimo-tournament.service, copy /var/lib/yimo/tournament.sqlite to a timestamped root-only backup, and restart it after release installation. Verify the backup before migration.
- [ ] Step 4: Install and verify the migrated service with install-release.sh --release-dir. Verify all YIMO services are active, GET /healthz succeeds, configured listeners are present, and firewall rules remain correct.
- [ ] Step 5: Create a disposable staging tournament through the real admin route. Use /root/yimo-admin-token.txt only inside a protected shell or local variable. Create four clearly named staging participants, create a short scheduled auto-start tournament, open registration, register four sessions, advance the scheduler, and assert the public bracket becomes visible. Do not print or commit tokens/codes.
- [ ] Step 6: Exercise one staging match through assigned-join, verify the Java room handshake, submit one authoritative result, and verify bracket advancement. Remove disposable records using an authenticated cleanup operation or restore the pre-test database backup after logs are collected.
- [ ] Step 7: Document rollback: stop the service, restore the SQLite backup and previous release directory, restart, and re-run healthz.
- [ ] Step 8: Commit.

~~~powershell
git add deploy/README.md docs/STAGE-8-RELEASE.md
git commit -m "Document scheduled tournament staging and rollback"
~~~

---
### Task 9: Build the online-configured Windows package and final release gate

**Files:**
- Use: installer/build-stage8-release.ps1
- Use: installer/test-stage8.ps1
- Use: installer/test-stage8-install.ps1
- Output: ignored build/yimo-graphwar-2.0.0-vps-172.86.74.172-20260908/

- [ ] Step 1: Build with the verified staging endpoint.

~~~powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\build-stage8-release.ps1 -GlobalHost '172.86.74.172' -TournamentApiBaseUrl 'http://172.86.74.172' -OutputDir .\build\yimo-graphwar-2.0.0-vps-172.86.74.172-20260908
~~~

Use a new output directory for every build because the script rejects an existing directory.
- [ ] Step 2: Extract YIMO-Graphwar-2.0.0-Portable.zip into a sibling portable-test directory and verify portable-test/YIMO-Graphwar.exe, yimo.properties, and all 20 campaign resources exist.
- [ ] Step 3: Run installer/test-stage8.ps1, then installer/test-stage8-install.ps1 with ExpectedGlobalHost 172.86.74.172 and ExpectedTournamentApiBaseUrl http://172.86.74.172. Verify the clean install includes the bundled runtime, YIMO icon, jars, resources, and online config.
- [ ] Step 4: Verify hashes and source state with Get-FileHash, git diff --check, and git status --short --branch. The repository must be clean and the release manifest must identify the exact approved commit.
- [ ] Step 5: Hand off the exact direct executable path first, then installer and portable ZIP paths. Include competitor portal URL, admin URL, secret location without revealing secrets, and staging health/bracket verification.
- [ ] Step 6: Commit only source/documentation; never commit generated artifacts. The single main branch remains the source of truth.

---

## Final acceptance checklist

- [ ] A legacy SEEDED tournament still loads its public bracket and accepts existing room results.
- [ ] A new tournament can be created from /admin without direct API calls.
- [ ] Registration opens/closes according to the configured schedule.
- [ ] Optional check-in is enforced only when enabled.
- [ ] Manual Start tournament creates exactly one frozen roster and bracket.
- [ ] Automatic start creates the same bracket without an admin request.
- [ ] Duplicate scheduler/admin starts are idempotent.
- [ ] Competitors can register, check in, view their schedule, and join an assigned match.
- [ ] Public bracket never exposes match codes, room tokens, participant codes, or admin secrets.
- [ ] Java lobby exposes the competitor portal link.
- [ ] All Node and Java tests pass.
- [ ] VPS migration, health check, disposable tournament, result advancement, and rollback are verified.
- [ ] The online-configured Windows installer passes clean-install checks.


