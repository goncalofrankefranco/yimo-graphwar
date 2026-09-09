# Stage 8 release and installer

YIMO Graphwar 2.0.0 is released from the Git tag `v2.0.0`. The Java client and
Java room/global servers target Java 8. The tournament service remains a
separate Node.js 24.x service and is not started by the Windows client.

## Build from a clean checkout

From a Windows PowerShell prompt with Java 8 installed:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\test-stage8.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\build-stage8-release.ps1 `
  -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot' `
  -GlobalHost '153.75.82.155' `
  -TournamentApiBaseUrl 'http://153.75.82.155'
```

The builder compiles all Java artifacts through the Stage 7 reproducible
build, copies the selected Java 8 runtime, writes the YIMO endpoint settings,
creates a portable ZIP, and creates the single-file
`YIMO-Graphwar-2.0.0-Setup.exe` with Windows IExpress. The output directory
contains SHA-256 checksums for the distributable files.

The installed package includes `YIMO-Graphwar.exe`, a native clickable
launcher for the bundled Java runtime. The client’s Settings screen stores
the lobby host, lobby port, and tournament API per Windows user; command-line
flags remain available for deployment and testing.

The package also includes the responsive battlefield viewport, explicit
room-mode selection, two-step guided/adaptation campaign lessons, the
YIMO Olympiad main-menu redesign, and the Java lobby link to the competitor
portal. These changes preserve Java 8 compatibility and the existing logical
game coordinates.

The default IP is a deployment input. Change `-GlobalHost` and
`-TournamentApiBaseUrl` for a restored snapshot with a different public IP;
never put server secrets in the client package.

## Installer behavior

The installer expands to `%LOCALAPPDATA%\YIMO Graphwar`, creates a Start menu
shortcut, and can launch the client immediately. It contains the Java runtime,
client/server JARs, resources, default YIMO configuration, legal notices, and
optional local practice launchers. It does not contain the tournament admin
token, participant codes, room HMAC secret, database, SSH key, or VPS token.

## Release checks

Before publishing a new binary:

1. Build from a clean checkout and record the source commit in the manifest.
2. Run the Java regression suite, tournament tests, deployment checks, and
   Stage 8 installer smoke check.
3. Install on a clean Windows account and verify the offline campaign, YIMO
   endpoint configuration, competitor-portal link, and practice launchers.
4. Send an old-build handshake to a YIMO room and verify `VERSION_MISMATCH`.
5. Run the local scheduled tournament demo and the VPS disposable tournament
   check before publishing the online endpoint.
6. Inspect the bundled runtime's legal files and review every `rsc/` asset.
7. Verify `SHA256SUMS.txt`, the Git tag, and the GitHub release assets.
8. Confirm no secret-like values are present in the source tree or artifacts.

The source release keeps `COPYING`, `NOTICE.md`, and
`THIRD-PARTY-LICENSES.md` next to the build scripts. This project remains
GPL-3.0-or-later; see the root `COPYING` file for redistribution obligations.

## Staging evidence

The disposable Stage 8 check was run on the replacement 1 GB VPS at
`172.86.81.18` from source revision `3fea3ab`. It created four temporary
participants, exercised scheduled registration/start, assigned a room,
submitted one signed result, and verified public-bracket advancement. The
pre-test SQLite backup was restored afterward; the service returned healthy
on `/healthz` and all four YIMO services remained active. This is a wiring
check, not a 100-player capacity result.

When uploading deployment scripts directly from a Windows checkout, normalize
shell line endings before executing them on Linux:

```bash
find /root/yimo-source/deploy/cloudzy -type f \
  \( -name '*.sh' -o -name '*.service' \) -exec sed -i 's/\r$//' {} +
```

## Tournament release boundary

The Windows client does not start the Node tournament service and does not
contain organizer credentials, participant codes, room HMAC secrets, database
files, or VPS keys. The server release includes `tournament/src`, its tests,
the migration, and the same-origin `/admin` and `/participant` pages. Deploy
that service separately behind the configured HTTP(S) endpoint. A public
bracket is read-only; assigned room tokens are issued only after an
authenticated competitor request.
