# YIMO UI, Mode Selection, and Tutorial Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the battlefield fill its available area, select room modes directly, add two-step campaign lessons, and replace the main menu with a YIMO Olympiad control-deck design.

**Architecture:** Keep the existing Swing screen graph and authoritative room protocol. Add a small pure viewport transform, an explicit `SET_MODE` room message, a two-step campaign data model layered over the existing lesson files, and a custom-painted responsive main menu using the existing `YimoTheme` and `Timer` primitives.

**Tech Stack:** Java 8, Swing/AWT, existing TCP protocol, Java Preferences, plain assertion-based tests, PowerShell build scripts.

**Spec:** `docs/superpowers/specs/2026-09-04-yimo-ui-tutorial-redesign-spec.md`

## Global Constraints

- No fullscreen APIs.
- Java source and bytecode remain compatible with Java 8.
- No external UI or animation dependencies.
- Existing anti-cheat, trajectory, map, network, and installer behavior must remain compatible.
- Tests must cover viewport math, mode selection, two-step lesson progress, and existing regression behavior.

---

### Task 1: Battlefield viewport scaling

**Files:**
- Create: `src/Graphwar/ScaleViewport.java`
- Modify: `src/Graphwar/GameScreen.java`, `src/Graphwar/GraphPlane.java`
- Test: `test/Graphwar/ScaleViewportTest.java`

**Interfaces:**
- `ScaleViewport.forSize(int width, int height)` returns a transform with `scale`, `offsetX`, `offsetY`, and inverse coordinate methods.
- `GraphPlane` continues to render in `Constants.PLANE_LENGTH × Constants.PLANE_HEIGHT` logical coordinates.

- [ ] **Step 1: Write the failing viewport test**

```java
ScaleViewport.Transform transform = ScaleViewport.forSize(1500, 800);
check(transform.scale == 1.0, "a 770x450 plane must fit by height without cropping");
check(transform.offsetX > 0, "the plane must be centered horizontally");
check(transform.toLogicalX(transform.toPhysicalX(123)) == 123,
        "physical/logical x conversion must be reversible");
```

Also assert that a 1920×1080 surface produces a scale greater than the old
fixed 770×450 rendering and that a 1280×1024 surface is centered without
distorting the aspect ratio.

- [ ] **Step 2: Run the focused test and verify it fails**

Run the existing Java 8 compile helper with `test/Graphwar/ScaleViewportTest.java`.
Expected: compilation fails because `ScaleViewport` does not exist.

- [ ] **Step 3: Implement the transform**

Use the logical plane dimensions and this calculation:

```java
double scale = Math.min((double) width / Constants.PLANE_LENGTH,
        (double) height / Constants.PLANE_HEIGHT);
int offsetX = (int) Math.round((width - Constants.PLANE_LENGTH * scale) / 2.0);
int offsetY = (int) Math.round((height - Constants.PLANE_HEIGHT * scale) / 2.0);
```

Return a scale of `1.0` and zero offsets for non-positive sizes. Add
`toLogicalX`, `toLogicalY`, `toPhysicalX`, and `toPhysicalY` using the same
transform so mouse coordinates do not drift after resizing.

- [ ] **Step 4: Make the game plane occupy the board surface**

Remove the fixed `GraphPlane` preferred-size constraint from `GameScreen`.
Give the board surface a `ScaleViewport`-style layout that passes its full
available size to `GraphPlane`; keep `GraphPlane`’s internal background and
collision buffers at logical dimensions and scale only the `Graphics2D` view.
Use the inverse transform for any plane mouse input. Do not change collision
coordinates or function calculations.

- [ ] **Step 5: Run tests and build**

Run `ScaleViewportTest`, `ResponsiveLayoutTest`, `GlobalTrajectoryTest`, and
the Java 8 production compile. Expected: all pass and the game plane no
longer remains a small fixed rectangle inside a large window.

- [ ] **Step 6: Commit**

```powershell
git add src/Graphwar/ScaleViewport.java src/Graphwar/GameScreen.java src/Graphwar/GraphPlane.java test/Graphwar/ScaleViewportTest.java
git commit -m "Scale battlefield to the available window"
```

### Task 2: Direct room-mode selection

**Files:**
- Modify: `src/Graphwar/PreGameScreen.java`, `src/Graphwar/GameData.java`, `src/GraphServer/GraphServer.java`
- Test: `test/Graphwar/ModeSelectionTest.java`, `test/GraphServer/GraphServerProtocolTest.java`

**Interfaces:**
- `GameData.setMode(int mode)` validates `Constants.NORMAL_FUNC`, `Constants.FST_ODE`, and `Constants.SND_ODE`, then sends `SET_MODE&<mode>` only for the leader during pre-game.
- The server accepts `SET_MODE`, validates the same range, resets readiness, and broadcasts the authoritative mode.

- [ ] **Step 1: Write the failing selection test**

Test the pure mapping used by the screen:

```java
check(PreGameScreen.modeForButton(0) == Constants.NORMAL_FUNC, "normal button mapping");
check(PreGameScreen.modeForButton(1) == Constants.FST_ODE, "first-order button mapping");
check(PreGameScreen.modeForButton(2) == Constants.SND_ODE, "second-order button mapping");
```

Extend the server protocol test with valid mode messages and invalid values.

- [ ] **Step 2: Run the focused tests and verify failure**

Compile the production sources with the new tests. Expected: the explicit
mapping method and `GameData.setMode` are absent, so compilation fails.

- [ ] **Step 3: Implement explicit mode handling**

Change the three button branches in `PreGameScreen.actionPerformed` from
`nextMode()` to:

```java
graphwar.getGameData().setMode(modeForButton(
        source == normalFuncButton ? 0 : source == firstFuncButton ? 1 : 2));
```

Add the server case for `NetworkProtocol.SET_MODE` next to `NEXT_MODE`, allow
only the leader in `PRE_GAME`, and broadcast through the existing mode message.

- [ ] **Step 4: Run the mode tests and full protocol tests**

Expected: clicking any mode selects that exact mode, including clicking a
mode that is numerically before the current mode.

- [ ] **Step 5: Commit**

```powershell
git add src/Graphwar/PreGameScreen.java src/Graphwar/GameData.java src/GraphServer/GraphServer.java test/Graphwar/ModeSelectionTest.java test/GraphServer/GraphServerProtocolTest.java
git commit -m "Select room function modes directly"
```

### Task 3: Two-step campaign lessons

**Files:**
- Create: `src/Graphwar/CampaignStep.java`
- Modify: `src/Graphwar/CampaignLesson.java`, `src/Graphwar/CampaignProgress.java`, `src/Graphwar/CampaignScreen.java`
- Modify: `rsc/campaign/lesson-01.properties` through `lesson-10.properties`
- Test: `test/Graphwar/CampaignStepsTest.java`, `test/Graphwar/CampaignInteractionTest.java`

**Interfaces:**
- `CampaignStep` exposes `getNumber()`, `getInstructions()`, `getGuide()`, `getHint()`, `getFunction()`, `getTargetX()`, `getTargetY()`, `getTargetRadius()`, and `getShapes()`.
- `CampaignLesson.getStep(int stepNumber)` returns step 1 or step 2, and `getStepCount()` returns `2` for every valid lesson.
- `CampaignProgress.isStepComplete(String lessonId, int stepNumber)` and `markStepComplete(...)` persist step state; lesson completion is true only after step 2.

- [ ] **Step 1: Add failing step/data tests**

Assert that all lessons have two steps, step 1 contains a model function and
construction guide, step 2 has a different target position, and a progress
store does not unlock the next lesson after only step 1.

- [ ] **Step 2: Run the focused campaign tests and verify failure**

Expected: `CampaignStep` and the two-step accessors are missing.

- [ ] **Step 3: Extend the lesson properties**

Add these keys to every lesson file:

```properties
step.1.function=2*x
step.1.instructions=Use the slope to reach the marked target.
step.1.guide=Start with y=m*x; in shooter-relative mode, additive constants cancel.
step.1.hint=Compare the target's horizontal and vertical distance from the shooter.
step.1.target.x=610
step.1.target.y=210
step.1.target.radius=12
step.1.shapes=
step.2.function=2.2*x
step.2.instructions=Adapt the same idea to the shifted target.
step.2.guide=Explain exactly which slope, phase, curvature, or differential-equation initial condition changes.
step.2.hint=The target moved upward; change the slope rather than adding a constant.
step.2.target.x=610
step.2.target.y=180
step.2.target.radius=12
step.2.shapes=
```

Keep the original lesson-level values as compatibility fallbacks while all ten
files receive explicit step data. Step 2 functions remain internal answers;
the UI shows instructions and hints, not the solution.

- [ ] **Step 4: Implement step-aware simulation and progress**

Pass the selected `CampaignStep` to the existing collision simulation. Mark
only the active step complete when its target is hit, and unlock the next
lesson only after `isComplete(lessonId)` observes both step flags.

- [ ] **Step 5: Update the lesson UI**

Add a visible `Step 1 of 2` / `Step 2 of 2` indicator, a `Guided example` /
`Adapt the formula` label, a `Use model function` action only for step 1, and
an empty input for step 2. `Retry`, `Previous Lesson`, `Next Lesson`, and
`Back to Lessons` must preserve the selected step correctly.

- [ ] **Step 6: Run campaign tests and all existing campaign regressions**

Run `CampaignTest`, `CampaignGuideTest`, `CampaignDifficultyTest`,
`CampaignEliminationTest`, `CampaignInteractionTest`, and
`CampaignStepsTest`. Expected: all 20 steps load and step 2 is required for
completion.

- [ ] **Step 7: Commit**

```powershell
git add src/Graphwar/CampaignStep.java src/Graphwar/CampaignLesson.java src/Graphwar/CampaignProgress.java src/Graphwar/CampaignScreen.java rsc/campaign test/Graphwar
git commit -m "Turn campaign lessons into guided two-step challenges"
```

### Task 4: YIMO Olympiad main menu

**Files:**
- Modify: `src/Graphwar/MainMenuScreen.java`, `src/Graphwar/YimoTheme.java`, `src/Graphwar/YimoScreen.java`
- Test: `test/Graphwar/YimoThemeTest.java`, `test/Graphwar/MainMenuDesignTest.java`

**Interfaces:**
- Preserve the existing menu actions and settings screen IDs.
- Add a menu-only painted backdrop and action-card components using Swing/AWT
  and `javax.swing.Timer`; no new dependency or image asset is required.

- [ ] **Step 1: Write the failing design smoke test**

Assert that the menu exposes the required action labels, the dark Olympiad
palette constants exist, and the menu no longer uses the old oversized title
as its only visual anchor.

- [ ] **Step 2: Implement the visual system**

Use near-black ink, paper-white surfaces, one orange trajectory accent, a fine
mathematical grid, orange orbit/trajectory strokes, compact YIMO wordmark,
and large centered action cards. Use `Timer`-driven opacity/offset state for
entry, hover, and press. Keep keyboard focus rings and accessible button
labels.

- [ ] **Step 3: Make the layout responsive**

Use a central hero/action composition at wide sizes and a single-column stack
below the narrow breakpoint. Ensure the menu actions remain centered and do
not clip at 800×600, 1280×1024, or 1920×1080.

- [ ] **Step 4: Run visual/layout tests and compile**

Run `YimoThemeTest`, `MainMenuDesignTest`, `ResponsiveLayoutTest`, and the
full Java compile. Verify that all existing actions still route to the same
screens.

- [ ] **Step 5: Commit**

```powershell
git add src/Graphwar/MainMenuScreen.java src/Graphwar/YimoTheme.java src/Graphwar/YimoScreen.java test/Graphwar/YimoThemeTest.java test/Graphwar/MainMenuDesignTest.java
git commit -m "Redesign the YIMO Olympiad main menu"
```

### Task 5: Release verification and documentation

**Files:**
- Modify: `README.md`, `docs/STAGE-8-RELEASE.md`
- Test: all existing Java entry-point tests, `deploy/test-stage7.ps1`, `installer/test-stage8.ps1`

- [ ] **Step 1: Run the complete Java regression suite**

Compile every file under `src` and `test` with Java 8 and run each test main
class. Expected: zero failures.

- [ ] **Step 2: Build and clean-install the Windows package**

Run `installer/build-stage8-release.ps1` with the Java 8 toolchain, then run
`installer/test-stage8-install.ps1` against the new output directory. Confirm
the installer contains the updated client and clickable launcher.

- [ ] **Step 3: Document the behavior**

Document the responsive battlefield, explicit room mode selection, two-step
tutorial structure, and the new menu in the root README and Stage 8 release
notes.

- [ ] **Step 4: Commit the release documentation**

```powershell
git add README.md docs/STAGE-8-RELEASE.md
git commit -m "Document the YIMO UI and tutorial redesign"
```
