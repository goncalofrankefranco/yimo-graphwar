# YIMO Graphwar 2.2.0 release notes

This release completes the tournament room path and fixes the second-player aim
preview race. It is built alongside the existing GPL-3.0-or-later notices and
the corresponding-source links.

## Changes

- The aim preview is bound to the current player's turn and ignores obsolete
  asynchronous calculations, so a delayed earlier-turn result cannot replace
  the second player's preview.
- The competitor portal discovers the one active tournament automatically.
  It uses candidate-code login plus display name, one stage-dependent action,
  and automatic bracket refresh; local player IDs and tournament-ID inputs are
  removed.
- The organizer console resumes the current tournament without a typed ID and
  keeps schedule fields inside responsive cards.
- A blocked tournament start can recover: organizers may reopen registration,
  which clears expired schedule deadlines, then close check-in manually before
  retrying the bracket start.
- The portal explicitly advances to the next bracket match and tells the player
  to click **Join Room**; it never attempts to launch the desktop game.
- The desktop room form uses the fixed YIMO game hostname, removes the address
  box, and reveals a masked candidate-code field only for tournament ports.
- Tournament-only room processes use `31000–31049`, separate from practice
  ports `30000–30049`. The 1 GB staging setup defaults to port `31000` and one
  active match process.
- Concurrent requests for the same newly assigned match now wait for the same
  Java room startup, so the second competitor is not sent to a port before it
  is listening.
- Bye entries no longer mask the competitor's next playable bracket match.
- Organizers can extend expired match access without discarding an already
  assigned room; the action is audited and requires a running tournament.
- The admin bracket exposes an explicit, confirmed forfeit action only after a
  match expires; the chosen competitor advances and any assigned room is
  stopped. The service never awards no-show wins automatically.
- For an expired match, the organizer can explicitly confirm a no-show, choose
  which assigned competitor advances, and release that room; the action is
  audited and never runs automatically.
- The Java room keeps retrying a signed result until the service acknowledges
  it, and stops accepting new players while that result is pending.
- Canonical competitor names are bound into the signed room-access token; the
  room ignores a client-supplied replacement name.
- Room admission uses bounded concurrent handshakes and a total deadline, so
  partial or slow-drip hellos cannot occupy the accept loop indefinitely.
- The staging smoke flow uses a room-server HMAC, refuses an already-active
  tournament, completes its two-player bracket, and explicitly releases its
  disposable room process.
- Tournament rooms are hidden, 1v1, one player per candidate, and locked to
  fixed teams, soldier count, game mode, trajectory, preview, timer, and map.
- The Java room server reports normal wins and live-match forfeits using a
  server-only HMAC. The service verifies the signature, advances the bracket,
  marks the final complete, and frees the room slot. Participant room tokens
  cannot submit results.
- Nginx restores real visitor IPs only from the official Cloudflare proxy
  ranges, so API rate limits distinguish competitors without trusting an
  arbitrary forwarded header.

## Ports and handoff

| Purpose | Ports | UI visibility |
| --- | --- | --- |
| YIMO global lobby | TCP 23762 | Fixed hostname |
| Practice rooms | TCP 30000–30049 | No candidate code |
| Tournament rooms | TCP 31000–31049 | Candidate code appears after a tournament port is entered |

On the staging VPS, new tournament schedules default to only port `31000`.
Increase the tournament range only after upgrading the VPS and measuring room
memory/CPU under concurrent matches. Do not expose tournament rooms through the
public lobby.

## Build and verification

Build the self-extracting Windows installer and portable ZIP with the Java 8
toolchain and the bundled runtime source:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File installer/build-stage8-release.ps1 `
  -OutputDir build/yimo-2.2.0-release
```

Run `npm test` from `tournament/`, compile all `src` and `test` Java files with
Java 8, and run each Java test main. `deploy/test-stage7.ps1` checks the VPS
bootstrap, port firewall, Cloudflare real-IP configuration, and bounded
service settings.

The installer contains the official YIMO logo, bundled Java runtime, `COPYING`,
`NOTICE.md`, and third-party notices. Keep runtime organizer passwords,
participant codes, HMAC keys, database files, SSH keys, and VPS API tokens out
of the release archive and source tree.

## Deployment status

The local release build and clean-install test passed, but this build is not
published to GitHub or deployed to the VPS. Do not restart the VPS or replace
its room services while an existing tournament match may still be live. Stage
the matching Java server and Node service together before allowing 2.2.0
clients into tournament rooms.
