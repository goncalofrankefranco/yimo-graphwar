# Third-party licenses

This file records the third-party components intentionally included or
referenced by YIMO Graphwar 2.0.0. The release installer carries the relevant
runtime license files alongside the runtime where the vendor supplies them.

## Graphwar upstream

The Java game is derived from Graphwar by Lucas Catabriga Rocha and the
upstream contributors. It is distributed under GPL-3.0-or-later. The complete
license text is in [`COPYING`](COPYING), and the upstream source is available
at <https://github.com/catabriga/graphwar>.

## OpenJDK 8 runtime

The Windows installer is built with a separately obtained Java 8 runtime so a
player does not need to install Java. OpenJDK distributions are generally
licensed under GPLv2 with the Classpath Exception and include vendor-specific
notices. The build script copies the selected runtime's own `LICENSE`,
`NOTICE`, and other legal files into the installer payload when present.

The exact runtime directory is a build input, not source code, and must be
audited by the release builder before publishing a binary. The v2 build used
the Eclipse Temurin Java 8 runtime selected by the local `JavaHome` input.

## Node.js

The tournament service targets Node.js 24.x and uses only Node built-in
modules. Node.js is not bundled into the Windows client installer. A staging
or production server must install Node.js from an approved distribution and
retain its corresponding license and security-update records.

## Fonts, sounds, images, and other assets

The game resources under `rsc/` are included in the upstream source
distribution. Their exact licensing and provenance must be verified as part of
the release audit, especially for any replacement YIMO logo, font, sound, or
image. No asset is granted a broader license by this file. The build output
includes the source repository and this notice so the audit trail travels with
the release.
