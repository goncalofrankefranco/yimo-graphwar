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
   endpoint configuration, and practice launchers.
4. Send an old-build handshake to a YIMO room and verify `VERSION_MISMATCH`.
5. Inspect the bundled runtime's legal files and review every `rsc/` asset.
6. Verify `SHA256SUMS.txt`, the Git tag, and the GitHub release assets.
7. Confirm no secret-like values are present in the source tree or artifacts.

The source release keeps `COPYING`, `NOTICE.md`, and
`THIRD-PARTY-LICENSES.md` next to the build scripts. This project remains
GPL-3.0-or-later; see the root `COPYING` file for redistribution obligations.
