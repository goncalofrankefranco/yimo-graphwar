# YIMO Graphwar 3.2.0 Windows installer source

`install.cmd` and `install.ps1` are the complete installation logic used by
the self-extracting `YIMO-Graphwar-3.2.0-Setup.exe`. The installer expands a
bundled Java 8 runtime and the three game/server JARs into
`%LOCALAPPDATA%\YIMO Graphwar`, then creates a Start menu shortcut for the
native `YIMO-Graphwar.exe` launcher. The launcher starts the bundled
Java runtime, so users can search for YIMO Graphwar and click it without
PowerShell or a separate Java installation.

The setup wrapper records a fingerprint of the embedded package.
Re-running the same installer reuses the existing installation instead of
expanding `payload.zip` again, while any package-content change gets a new
fingerprint and updates the installation. Use the Start menu shortcut or
`YIMO-Graphwar.exe` to launch the game.

Build it from a clean source tree. Release builders reject dirty Git
worktrees, even when `-SourceRevision` is supplied, because Stage 7 also
packages the tournament website. For a release, export the reviewed commit
with `git archive`, extract it to a temporary directory, and pass that
commit's full SHA using `-SourceRevision`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\test-stage8.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\build-stage8-release.ps1 `
  -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot' `
  -SourceRevision '<reviewed-commit-sha>' `
  -GlobalHost 'graphwar-server.yimo-official.org' `
  -TournamentApiBaseUrl 'https://graphwar-server.yimo-official.org'
```

The build targets the Java 8 API (using `--release 8` with a newer JDK)
and uses the selected Java 8 runtime. It emits the single-file installer, a
release manifest, and SHA-256 checksums under
`build\yimo-3.2.0-release-final`. The portable staging ZIP stays in a
temporary folder and is not retained as a second local artifact. Keep the
generated installer out of Git because it contains the selected Java runtime;
attach it to the GitHub release instead.

The client displays product version 3.2.0 while retaining wire build ID
`YIMO-Graphwar-2.2.0` and protocol 2 for compatibility with the existing
servers. The production server JARs and tournament service remain on their
2.2.0 contract.

The client executable and fallback `launch-yimo.cmd` use the configured YIMO
endpoint. The optional
`launch-practice-server.cmd` and `launch-practice-client.cmd` use loopback and
are useful for offline testing. No organizer tokens, room HMAC secrets,
participant codes, VPS credentials, or SSH keys are packaged.
