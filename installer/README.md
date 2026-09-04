# YIMO Graphwar 2.0.0 Windows installer source

`install.cmd` and `install.ps1` are the complete installation logic used by
the self-extracting `YIMO-Graphwar-2.0.0-Setup.exe`. The installer expands a
bundled Java 8 runtime and the three game/server JARs into
`%LOCALAPPDATA%\YIMO Graphwar`, then creates a Start menu shortcut for the
native `YIMO-Graphwar.exe` launcher. The launcher starts the bundled
Java runtime, so users can search for YIMO Graphwar and click it without
PowerShell or a separate Java installation.

Build it from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\test-stage8.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\build-stage8-release.ps1 `
  -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot' `
  -GlobalHost '153.75.82.155' `
  -TournamentApiBaseUrl 'http://153.75.82.155'
```

The build uses the Windows-native IExpress tool and emits a portable ZIP,
the single-file installer, a release manifest, and SHA-256 checksums under
`build\yimo-stage8-release`. The generated artifacts are intentionally kept
out of Git because they contain the selected Java runtime; attach them to the
GitHub release instead.

The client executable and fallback `launch-yimo.cmd` use the configured YIMO
endpoint. The optional
`launch-practice-server.cmd` and `launch-practice-client.cmd` use loopback and
are useful for offline testing. No organizer tokens, room HMAC secrets,
participant codes, VPS credentials, or SSH keys are packaged.
