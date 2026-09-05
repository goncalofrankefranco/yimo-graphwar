# YIMO Graphwar project map

## Source of truth

- `src/` — Java 8 client, room server, global lobby, protocol, rendering, and
  campaign code.
- `rsc/` — runtime images, sounds, UI resources, and the ten campaign files.
- `test/` — runnable assertion-based Java regression checks.
- `tournament/` — Node.js 24 tournament control service and tests.

## Operations and packaging

- `deploy/` — Linux/Cloudzy bootstrap, service units, room deployment, and
  replacement-VPS templates.
- `installer/` — Windows installer, native launcher, official YIMO logo source,
  and clean-install tests.
- `docs/SCALING-PLAN.md` — hosting decision, capacity thresholds, and event
  runbook.
- `docs/STAGES-5-6.md` — tournament service and signed-room design.
- `docs/STAGE-8-RELEASE.md` — reproducible Windows release process.
- `docs/superpowers/` — approved implementation specs and plans.
- `docs/archive/superseded/` — historical plans replaced by the current
  resizable-window and YIMO redesign approach.

## Generated output

- `build/latest/` — the current locally built Windows package.
- `build/` — ignored build output; old release directories can be deleted and
  recreated with `installer/build-stage8-release.ps1`.
- `build/local/` — ignored source-build JARs from `compile.sh` or `makefile`.
- `bin-test/` — ignored temporary test classes; never source.

The repository does not keep generated JARs or installer EXEs at the root.
Use the build scripts when a fresh artifact is needed.
