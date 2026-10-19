# YIMO Graphwar 3.2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Ship a friendlier Graphwar 3.2.0 client with ten more combined-function tutorial lessons while preserving the existing 2.2.0 server contract and release.

**Architecture:** Separate the displayed product version from the wire build ID. Keep gameplay and appearance changes client-side, use server hit data for hit feedback, and extend the existing property-file campaign from 20 to 30 sequential two-step lessons.

**Tech Stack:** Java 8, Swing/Java2D, Java Preferences, existing Java main-based tests, PowerShell/IExpress packaging; no new dependencies.

**Spec:** docs/superpowers/specs/2026-10-19-yimo-graphwar-3.2.0-design.md

## Global Constraints

- Product version is 3.2.0; wire build ID is YIMO-Graphwar-2.2.0; protocol version is 2.
- Game host, tournament API, ports, server results, and the published 2.2.0 release stay unchanged.
- Collision mask pixels, map geometry, hit positions, and turn order are unchanged.
- New cosmetics are client-local; preserve blue/red team identification and player names.
- Keep Java 8 compatibility; add no dependencies or large raster assets.
- Campaign lessons 21–30 use normal, shooter-relative mode and preserve the guided-plus-adaptation two-step format.
- Preserve the existing user edits in tournament/src/home-page.ts and tournament/test/http.test.ts; do not stage, overwrite, or include them accidentally in the release build.
- Use temporary directories for tests and the clean release source export; keep only the installer, manifest, and checksum in persistent release output.

## Review Focus

- Flat, steep, vertical, and degenerate shot paths: FunctionAnimationTest checks slope-sensitive pacing, monotonic progress, and the 1,200 ms cap.
- Misses and teammate-only hits: ShotFeedbackTest checks that only server-confirmed opponent hits show “DIRECT HIT!”.
- Missing or invalid appearance preferences: AppearanceSettingsTest checks safe Classic defaults and persistence.
- Cosmetic terrain themes versus gameplay: ObstacleAppearanceTest checks identical collision outcomes across themes.
- New lesson formulas, target reachability, two-step progression, and old progress: CombinedFunctionCampaignTest and CampaignStepsTest check all ten new lessons and the lesson-20-to-21 unlock.

---

### Task 1: Separate product version from wire compatibility and update package metadata

**Files:**
- Modify: src/Graphwar/GraphUI.java
- Modify: test/Graphwar/VersionBadgeTest.java
- Modify: deploy/build-stage7-release.ps1
- Modify: installer/YimoLauncher.cs
- Modify: installer/launch-yimo.cmd
- Modify: installer/launch-practice-client.cmd
- Modify: installer/build-stage8-release.ps1
- Modify: installer/install.ps1
- Modify: installer/test-stage8.ps1
- Modify: installer/test-stage8-install.ps1
- Modify: README.md, NOTICE.md, installer/README.md
- Create: docs/Stage-8-RELEASE-3.2.0.md

**Interfaces:**
- Produces: GraphUI.APP_VERSION = "3.2.0"; configured Constants.BUILD_ID stays YIMO-Graphwar-2.2.0 and Constants.PROTOCOL_VERSION stays 2.
- The client JAR and installer are named 3.2.0. globalServer.jar, roomServer.jar, the tournament service, and production deployment remain on the 2.2.0 contract.
- Both release builders accept an optional SourceRevision string so a clean source export can write the reviewed commit into its manifest.

- [x] **Step 1: Write the failing version contract test and upgrade-cleanup assertion.** Update VersionBadgeTest to expect “YIMO Graphwar 3.2.0” while asserting NetworkConfig.defaults() still returns build ID YIMO-Graphwar-2.2.0 and protocol 2. Preseed a temporary install with an old versioned YIMO client JAR and assert the new install retains only YIMO-Graphwar-3.2.0.jar while preserving unversioned server JARs.
- [x] **Step 2: Compile to a fresh directory under %TEMP% and run Graphwar.VersionBadgeTest.** Expected: the product-version assertion fails, while compatibility assertions pass.
- [x] **Step 3: Add GraphUI.APP_VERSION and update the release metadata.** Rename only the client JAR to YIMO-Graphwar-3.2.0.jar; update launcher references, installer names, Start-menu package marker, notices, and release notes. Keep packaged build.id values at YIMO-Graphwar-2.2.0 and protocol.version at 2. Add SourceRevision support to both release builders. Keep the portable payload ZIP inside temporary staging rather than outputting a second ZIP. After successful extraction, remove only obsolete YIMO-Graphwar-*.jar client files, preserving the 3.2.0 client and unversioned server JARs.
- [x] **Step 4: Run VersionBadgeTest, installer/test-stage8.ps1, and installer/test-stage8-install.ps1.** Expected: all pass; product metadata is 3.2.0, wire metadata stays 2.2.0/protocol 2, and an upgrade leaves no stale versioned client JAR.

### Task 2: Add slope-aware path animation

**Files:**
- Create: src/Graphwar/FunctionAnimation.java
- Create: test/Graphwar/FunctionAnimationTest.java
- Modify: src/Graphwar/GameData.java

**Interfaces:**
- Produces: package-private FunctionAnimation(Function), int stepAt(long elapsedMillis), and long durationMillis().
- Precompute cumulative screen-space segment lengths once per received shot. Calibrate flat-path pacing to the current 1,500 samples/second rate and cap any trace at 1,200 ms.

- [x] **Step 1: Write FunctionAnimationTest.** Assert a flat path follows the current timing reference, the same elapsed time advances fewer samples on a steeper path, vertical/zero-length segments remain bounded, progress is monotonic, and elapsed time at/after duration reaches the final sample.
- [x] **Step 2: Compile and run Graphwar.FunctionAnimationTest from a fresh %TEMP% class directory.** Expected: compilation/test fails because FunctionAnimation is absent.
- [x] **Step 3: Implement cumulative rendered-distance progress and initialize it when GameData accepts the server shot. Replace only constant-step timing in getCurrentFunctionPosition; do not change trajectory calculation, hit positions, or wire messages.**
- [x] **Step 4: Run FunctionAnimationTest and the existing campaign preview, second-player preview, and authoritative-game tests.** Expected: slope pacing passes and existing path/result assertions remain unchanged.

### Task 3: Show server-confirmed Direct Hit feedback

**Files:**
- Create: src/Graphwar/ShotFeedback.java
- Create: test/Graphwar/ShotFeedbackTest.java
- Modify: src/Graphwar/GameData.java
- Modify: src/Graphwar/GraphPlane.java
- Modify: src/Graphwar/GameScreen.java

**Interfaces:**
- Produces: ShotFeedback.textFor(int shooterTeam, int[] hitTeams), returning “DIRECT HIT!” only if an opponent team appears; otherwise null.
- GraphPlane.showShotFeedback(String) draws the callout for 1,200 ms. GameData invokes it on the Swing event thread only when server hit metadata is present.

- [x] **Step 1: Write ShotFeedbackTest** for an opponent hit, teammate-only hit, and empty hit list.
- [x] **Step 2: Compile and run the focused test.** Expected: it fails because ShotFeedback is absent.
- [x] **Step 3: Implement the team-result mapping and lightweight Java2D callout. Do not infer hits from the aim preview or client-side collision.**
- [x] **Step 4: Run ShotFeedbackTest and the authoritative-result/network-protocol tests.** Expected: only a server-confirmed opponent hit displays the callout; wire payloads are unchanged.

### Task 4: Add local appearance settings and safe arena skins

**Files:**
- Create: src/Graphwar/AppearanceSettings.java
- Create: test/Graphwar/AppearanceSettingsTest.java
- Create: test/Graphwar/ObstacleAppearanceTest.java
- Modify: src/Graphwar/MainMenuScreen.java
- Modify: src/Graphwar/GraphPlane.java
- Modify: src/Graphwar/Obstacle.java
- Modify: src/Graphwar/YimoTheme.java

**Interfaces:**
- Produces: AppearanceSettings.load(Preferences) and save(Preferences), with validated marker-shape, marker-palette, marker-pattern, and Classic/Garden/Arcade theme values.
- Define MarkerShape { LEGACY_SOLDIER, ORB, STAR }, MarkerPalette { TEAM, CORAL, MINT, GOLD }, MarkerPattern { SOLID, DOTS, RINGS }, and ArenaTheme { CLASSIC, GARDEN, ARCADE } inside AppearanceSettings.
- Obstacle retains its existing collision image/API and exposes a separate cached display image for the selected theme. Explosions invalidate the display cache, not the collision rules.
- Produces: BufferedImage Obstacle.getDisplayImage(AppearanceSettings.ArenaTheme theme).

- [x] **Step 1: Write AppearanceSettingsTest and ObstacleAppearanceTest.** Verify Classic fallback, round-trip persistence in a temporary Preferences node removed in finally, different theme pixels, and identical collidePoint/soldierCollides results under every theme.
- [x] **Step 2: Compile and run both focused tests.** Expected: they fail because the settings/display APIs are absent.
- [x] **Step 3: Implement the settings model, Appearance card with preview/reset, marker rendering, and Java2D arena textures. Use the built-in Preferences API. Keep team outlines/names and the original collision raster unchanged.**
- [x] **Step 4: Run the focused tests and existing MapEditorPanelTest, MainMenuDesignTest, ResponsiveLayoutTest, and YimoThemeTest.** Expected: all pass and Classic preserves the legacy appearance.

### Task 5: Extend the campaign with ten combined-function lessons

**Files:**
- Create: rsc/campaign/lesson-21.properties through rsc/campaign/lesson-30.properties
- Create: test/Graphwar/CombinedFunctionCampaignTest.java
- Modify: src/Graphwar/CampaignLesson.java
- Modify: test/Graphwar/ExtendedCampaignTest.java
- Modify: docs/CAMPAIGN.md

**Interfaces:**
- Produces: CampaignLesson.COUNT = 30; loadAll() continues to load sequential lesson-01 through lesson-30 files using the existing CampaignStep schema.
- Lessons 21–30 all use normal functions and shooter-relative trajectory. Every lesson has two distinct functions, two different targets, and term-by-term construction guidance.
- Lesson titles and families: 21 Backbone + Ripple (m*x+a*sin(x)); 22 Wave Strength (change coefficient); 23 Wave Spacing (change input scale); 24 Line + Cosine; 25 Quadratic + Wave; 26 Cubic + Wave; 27 Exponential + Line; 28 Modulated Cosine (x*cos(k*x) plus a linear correction); 29 Bounded Trend + Wave (a*x/sqrt(1+x^2)+b*sin(k*x)); 30 Three-Term Challenge.
- Step 2 leaves the function field blank, moves the target, and asks the learner to recreate the model and adapt one coefficient or scale. All formulas remain inside the current parser grammar; do not add tanh.

- [x] **Step 1: Write CombinedFunctionCampaignTest.** Assert 30 total sequential IDs; lessons 21–30 have the listed titles/families, normal mode, shooter-relative trajectory, two steps, changed target/function in step 2, and guidance that explains each term. Verify completing lesson-20 unlocks lesson-21 while lesson-20 remains complete.
- [x] **Step 2: Compile and run CombinedFunctionCampaignTest.** Expected: it fails because the campaign count is 20 and lessons 21–30 are missing.
- [x] **Step 3: Set CampaignLesson.COUNT to 30 and add the ten property files.** Use supported expressions and modest coefficients; calibrate each target with CampaignScreen.simulateStep so both functions hit. Keep the guided model, blank adaptation field, and one-parameter-at-a-time explanation. Update ExtendedCampaignTest's total-count assertion and the campaign topic table.
- [x] **Step 4: Run CombinedFunctionCampaignTest, CampaignTest, CampaignStepsTest, CampaignGuideTest, CampaignDifficultyTest, and ExtendedCampaignTest.** Expected: both steps in every new lesson reach their targets, all lessons retain two steps, and prior progress remains intact.

### Task 6: Integrate, verify, and publish v3.2.0

**Files:**
- Modify: README.md and docs/Stage-8-RELEASE-3.2.0.md with verified behavior.
- Release output: build/yimo-3.2.0-release/YIMO-Graphwar-3.2.0-Setup.exe, RELEASE-MANIFEST.txt, and SHA256SUMS.txt only.

- [ ] **Step 1: Compile every Java production/test source into a unique %TEMP% directory and run all Java test mains.** Expected: zero failures.
- [ ] **Step 2: Run npm test in tournament/ and installer/test-stage8.ps1.** Confirm the tournament service and the two pre-existing user-edited tournament files are unchanged and unstaged.
- [ ] **Step 3: Commit only feature-owned files, then create a temporary clean source export from that commit, excluding the existing uncommitted tournament edits.** Build from that export with SourceRevision set to the commit hash; remove the temporary export after packaging.
- [ ] **Step 4: Build the single 3.2.0 setup asset under the specified output directory.** Verify its config still uses build ID 2.2.0/protocol 2, its launcher points to the 3.2.0 client JAR, and no second portable ZIP is left in the release output.
- [ ] **Step 5: Run installer/test-stage8-install.ps1 with its temporary install root; open that temporary installation and verify the version badge, Appearance menu, slope-paced shot, Direct Hit callout, and campaign lessons 21–30.**
- [ ] **Step 6: Verify the canonical GitHub remote and publish a separate v3.2.0 tag/release with the installer and checksum.** Leave the published 2.2.0 release and production servers untouched; provide the local installer path.
