# YIMO Graphwar 3.2.0 release

This is a client presentation and tutorial release. It does not require a
production-server or tournament-service update.

## Compatibility

- Product version shown in the app and installer: 3.2.0.
- Network build ID: YIMO-Graphwar-2.2.0.
- Network protocol: 2.
- Game and tournament endpoints, ports, server results, and tournament schema
  are unchanged.
- The published 2.2.0 release remains available and supported.

The distinct product version is intentional: the current Java room handshake
and tournament API accept the exact 2.2.0 build ID. Cosmetic settings, path
animation, hit callouts, and campaign data do not alter network messages.

## Changes

- Function traces are paced by rendered path distance so steep sections take
  longer, while the flat-path rate remains close to the existing animation.
- A short DIRECT HIT! callout uses the room server's confirmed opponent-hit
  list; it does not infer damage from the preview.
- New local marker styles and arena themes are saved with Java Preferences.
  The collision mask remains unchanged.
- Campaign lessons 21–30 add ten two-step lessons on line/wave, polynomial and
  exponential combinations, modulated cosine, bounded trends, and a capstone.

The existing parser does not support tanh. Lesson 29 uses supported arithmetic
and square-root operations for a similar bounded trend; the server grammar is
not changed.

## Build and verification

Build the single-file Windows installer from a clean `git archive` export of
the reviewed commit, passing that full SHA as `-SourceRevision`, a JDK with
`javac`, and a Java 8 runtime directory. The builders reject dirty Git
worktrees, even with an explicit source revision, because Stage 7 packages the
tournament website too. A newer JDK must support `--release 8`. Intermediate
payload files stay in a temporary directory; the release output contains only
the installer, manifest, and SHA256SUMS.txt.
The tournament portion excludes local `.env` files, SQLite databases, and
`data` or `backups` directories.

Run installer/test-stage8.ps1, installer/test-stage8-install.ps1 with the
release output directory, npm test from tournament/, and every Java test main.
The installer test uses a temporary LOCALAPPDATA/APPDATA root and does not
replace the user's existing installation.

The release output path is build/yimo-3.2.0-release-final. Keep the setup executable
out of Git, attach it and its checksum to the separate v3.2.0 GitHub release,
and do not replace the v2.2.0 release or restart the production servers.
