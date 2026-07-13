# Fullscreen, Trajectory Modes, and Custom Maps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Scale the existing 800×600 Graphwar UI uniformly to the largest available fullscreen size, add host-toggleable shooter-relative/global trajectories, and add a host-only pre-game editor for circle and rectangle maps.

**Architecture:** Preserve the current fixed logical canvas. `ScaleViewport` will transform one normal Swing child-painting pass and inverse-transform mouse input. The room server will own a trajectory-mode flag and validated transient map-shape list, while the existing `Function` and `Obstacle` paths will be extended for global paths and rasterized circles/rectangles.

**Tech Stack:** Java 8, Swing/AWT, existing line-based Graphwar room protocol, `javac`, plain Java assertion checks (no new dependencies).

## Global Constraints

- Keep the logical game surface at 800×600 and the logical map at 770×450.
- Use uniform scaling: `min(viewportWidth / 800, viewportHeight / 600)`.
- Preserve the current shooter-relative trajectory as the default.
- Global trajectories use fixed map coordinates and exclude only the firing soldier from collision.
- Custom maps are room-local and host-controlled; no disk persistence is added.
- Validate untrusted network payloads before storing or broadcasting them.
- Keep Java 8 compatibility and add no dependencies.
- Before renderer edits, preserve a timestamped backup of the current `src`, `rsc`, and `graphwar.jar`.
- Every new non-trivial behavior leaves a runnable assertion check or smoke test.

---

### Task 1: Preserve the current build and create the rollback point

**Files:**
- Create: `backups/<timestamp>/src/**`
- Create: `backups/<timestamp>/rsc/**`
- Create: `backups/<timestamp>/graphwar.jar`

**Interfaces:**
- Produces a complete source/resource/jar snapshot that can be copied back manually if a later task regresses the game.

- [ ] **Step 1: Copy the current working files before editing code**

Run from the repository root:

```powershell
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path 'backups' $stamp
New-Item -ItemType Directory -Force $backup | Out-Null
Copy-Item -Recurse -Force src (Join-Path $backup 'src')
Copy-Item -Recurse -Force rsc (Join-Path $backup 'rsc')
Copy-Item -Force graphwar.jar (Join-Path $backup 'graphwar.jar')
Write-Output $backup
```

Expected: a new timestamped directory containing `src`, `rsc`, and the currently working jar.

- [ ] **Step 2: Verify the backup is complete**

Run:

```powershell
Test-Path (Join-Path $backup 'src\Graphwar\Graphwar.java')
Test-Path (Join-Path $backup 'rsc\GameScreen.txt')
Test-Path (Join-Path $backup 'graphwar.jar')
```

Expected: three `True` values.

---

### Task 2: Implement and test uniform fullscreen scaling

**Files:**
- Modify: `src/Graphwar/ScaleViewport.java`
- Test: `test/Graphwar/FullscreenScaleTest.java`

**Interfaces:**
- `ScaleViewport.getScaleForSize(int width, int height)` returns the positive uniform scale for the logical 800×600 canvas.
- `ScaleViewport.getOffsetX(int width, int height)` and `getOffsetY(int width, int height)` return the centered physical offsets.
- `ScaleViewport` paints `GraphUI` once with a transform and maps pointer coordinates through the inverse transform.

- [ ] **Step 1: Write the failing scale/offset check**

Create `test/Graphwar/FullscreenScaleTest.java`:

```java
package Graphwar;

public class FullscreenScaleTest {
    public static void main(String[] args) {
        assertNear(1.0, ScaleViewport.getScaleForSize(800, 600));
        assertNear(1.8, ScaleViewport.getScaleForSize(1920, 1080));
        assertEquals(240, ScaleViewport.getOffsetX(1920, 1080));
        assertEquals(0, ScaleViewport.getOffsetY(1920, 1080));
        System.out.println("fullscreen-scale-check: PASS");
    }

    private static void assertNear(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(expected + " != " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(expected + " != " + actual);
        }
    }
}
```

- [ ] **Step 2: Run it and verify the expected failure**

Run:

```powershell
$jdk='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
& "$jdk\bin\javac.exe" -d bin-test -sourcepath 'src;test' test\Graphwar\FullscreenScaleTest.java
```

Expected: FAIL because the scale helper methods do not exist yet.

- [ ] **Step 3: Implement the minimum transformed viewport**

In `ScaleViewport.java`:

```java
static double getScaleForSize(int width, int height) {
    return Math.min(width / (double) DESIGN_WIDTH, height / (double) DESIGN_HEIGHT);
}

static int getOffsetX(int width, int height) {
    return (int) Math.round((width - DESIGN_WIDTH * getScaleForSize(width, height)) / 2.0);
}

static int getOffsetY(int width, int height) {
    return (int) Math.round((height - DESIGN_HEIGHT * getScaleForSize(width, height)) / 2.0);
}
```

Keep `graphUI` at `(0, 0, 800, 600)` and the transparent input layer at the full viewport size. Override `paintChildren` to create a graphics copy, translate by the offsets, scale by the helper result, clip to the logical canvas, and call `super.paintChildren` exactly once. The input layer’s paint method must remain visually empty. Convert pointer coordinates with `(physical - offset) / scale`, reject points outside the logical canvas, and dispatch the converted event to the existing target lookup.

- [ ] **Step 4: Run the scale check and compile the full source**

Run:

```powershell
& "$jdk\bin\javac.exe" -d bin-test -sourcepath 'src;test' test\Graphwar\FullscreenScaleTest.java
& "$jdk\bin\java.exe" -ea -cp bin-test Graphwar.FullscreenScaleTest
```

Expected: `fullscreen-scale-check: PASS`.

Then run the normal full build from the repository’s existing command and expect only the two pre-existing deprecation warnings.

---

### Task 3: Add the trajectory-mode protocol and global calculation path

**Files:**
- Modify: `src/GraphServer/Constants.java`
- Modify: `src/GraphServer/NetworkProtocol.java`
- Modify: `src/GraphServer/GraphServer.java`
- Modify: `src/Graphwar/GameData.java`
- Modify: `src/Graphwar/Function.java`
- Modify: `src/Graphwar/PreGameScreen.java`
- Modify: `src/Graphwar/GameScreen.java`
- Test: `test/Graphwar/GlobalTrajectoryTest.java`

**Interfaces:**
- `Constants.SHOOTER_RELATIVE_TRAJECTORY = 0` and `Constants.GLOBAL_TRAJECTORY = 1`.
- `NetworkProtocol.SET_TRAJECTORY_MODE = 46`.
- `Function.processGlobalRange(Obstacle, Player[], int currentTurn, int gameMode)` calculates a left-to-right fixed-axis trajectory and records hits.
- `GameData.isGlobalTrajectory()` exposes the selected room setting to preview/rendering code.
- `GameData.setTrajectoryMode(int mode)` sends a host setting change during pre-game.

- [ ] **Step 1: Write the failing coordinate behavior check**

Create `test/Graphwar/GlobalTrajectoryTest.java` with a package-level pure coordinate assertion:

```java
package Graphwar;

import GraphServer.Constants;

public class GlobalTrajectoryTest {
    public static void main(String[] args) {
        double left = Constants.PLANE_LENGTH * (-Constants.PLANE_GAME_LENGTH / 2.0)
                / Constants.PLANE_GAME_LENGTH + Constants.PLANE_LENGTH / 2.0;
        double center = Constants.PLANE_LENGTH / 2.0;
        if (Math.abs(left) > 0.000001 || Math.abs(center - Constants.PLANE_LENGTH / 2.0) > 0.000001) {
            throw new AssertionError("global map coordinate conversion is not fixed-axis");
        }
        if (Constants.GLOBAL_TRAJECTORY != 1) {
            throw new AssertionError("global trajectory mode missing");
        }
        System.out.println("global-trajectory-check: PASS");
    }
}
```

- [ ] **Step 2: Run it and verify the expected failure**

Run:

```powershell
& "$jdk\bin\javac.exe" -d bin-test -sourcepath 'src;test' test\Graphwar\GlobalTrajectoryTest.java
```

Expected: FAIL because `GLOBAL_TRAJECTORY` is not defined yet.

- [ ] **Step 3: Add the shared mode and room protocol**

Add the constants and protocol ID above. Add `trajectoryMode` to `GraphServer`, default it to shooter-relative, include it in the existing room-settings broadcast, and accept `SET_TRAJECTORY_MODE` only when the sender is leader and the room is pre-game. Clamp invalid values to shooter-relative.

In `GameData`, store the mode, parse the broadcast, update the pre-game control, and send only validated host changes.

- [ ] **Step 4: Implement the global trajectory calculation**

Add `Function.processGlobalRange(...)` using the existing step, collision, hit-recording, and terrain-stop logic:

- Normal mode evaluates the parsed function over `x = -PLANE_GAME_LENGTH / 2` through `+PLANE_GAME_LENGTH / 2` without translating from a soldier.
- First-order mode integrates from the left edge with `y = 0`.
- Second-order mode integrates from the left edge with `y = 0` and derivative `0`.
- Do not mirror the result for team 2.
- Record every living soldier hit except the current firing soldier; do not filter by team.

Update `GameData.calculateFunction` so global mode calls this method and shooter-relative mode keeps the existing three branches. `buildPreviewFunction` must use the same selection.

- [ ] **Step 5: Add and wire the host UI toggle**

Add a `JComboBox<String>` to the existing room settings panel with exactly:

```text
Shooter-relative
Global graph (both teams)
```

Keep it disabled for non-hosts, synchronize it on room-setting messages, and send the selected value on change. Preserve the existing preview checkbox and turn-time control.

- [ ] **Step 6: Run the trajectory check and compile**

Run:

```powershell
& "$jdk\bin\javac.exe" -d bin-test -sourcepath 'src;test' test\Graphwar\GlobalTrajectoryTest.java
& "$jdk\bin\java.exe" -ea -cp bin-test Graphwar.GlobalTrajectoryTest
```

Expected: `global-trajectory-check: PASS`, followed by a successful full compile.

---

### Task 4: Add shared map shapes, server synchronization, and collision support

**Files:**
- Create: `src/GraphServer/MapShape.java`
- Modify: `src/GraphServer/NetworkProtocol.java`
- Modify: `src/GraphServer/GraphServer.java`
- Modify: `src/Graphwar/Obstacle.java`
- Modify: `src/Graphwar/GameData.java`
- Test: `test/GraphServer/MapShapeTest.java`

**Interfaces:**
- `MapShape.circle(int x, int y, int radius)` and `MapShape.rectangle(int x, int y, int width, int height)` create validated primitives.
- `MapShape.encode()` returns four numeric payload fields after the type: `type,x,y,a,b`.
- `MapShape.intersects(int x, int y, int radius)` checks spawn overlap.
- `Obstacle(int shapeCount, MapShape[] shapes)` rasterizes both primitive types into the existing terrain image.

- [ ] **Step 1: Write the failing shape/collision check**

Create `test/GraphServer/MapShapeTest.java`:

```java
package GraphServer;

public class MapShapeTest {
    public static void main(String[] args) {
        MapShape circle = MapShape.circle(100, 100, 20);
        MapShape rectangle = MapShape.rectangle(200, 80, 40, 30);
        if (!circle.intersects(100, 100, 7) || !rectangle.intersects(220, 95, 7)) {
            throw new AssertionError("shape intersection failed");
        }
        if (circle.intersects(150, 100, 7) || rectangle.intersects(100, 100, 7)) {
            throw new AssertionError("shape intersection false positive");
        }
        if (circle.encode().split(",").length != 5 || rectangle.encode().split(",").length != 5) {
            throw new AssertionError("shape encoding is not fixed-width");
        }
        System.out.println("map-shape-check: PASS");
    }
}
```

- [ ] **Step 2: Run it and verify the expected failure**

Run:

```powershell
& "$jdk\bin\javac.exe" -d bin-test -sourcepath 'src;test' test\GraphServer\MapShapeTest.java
```

Expected: FAIL because `MapShape` does not exist yet.

- [ ] **Step 3: Implement bounded circle/rectangle shapes**

Implement immutable `MapShape` records with type, origin, and two size values. Reject unknown types, non-positive sizes, coordinates outside the logical map, and dimensions larger than the map. Use a maximum of 128 shapes at the server boundary.

- [ ] **Step 4: Extend obstacle rasterization**

Add the shape-array constructor to `Obstacle`. Draw circles with `fillOval` and rectangles with `fillRect`; keep the existing white background, pixel collision methods, and explosion behavior. Keep the existing circle constructor as a compatibility overload for generated random maps.

- [ ] **Step 5: Store, validate, and broadcast custom maps on the server**

Add `customMapEnabled` and `List<MapShape> customMap` to `GraphServer`, plus `SET_MAP = 47`. The host-only pre-game handler must parse exactly 5 numeric fields per shape, validate every shape, reject malformed payloads without changing the current map, and broadcast the accepted list. Include custom shapes in `START_GAME`; if no custom map is enabled, retain the existing random circles.

Update server soldier placement to use `MapShape.intersects`, preserve team-side placement, and stop retrying after a finite number of attempts. Reject a custom map that cannot produce valid placements for all soldiers and keep the previous valid map instead.

- [ ] **Step 6: Parse the authoritative shape list on clients**

Update `GameData.startGameMessage` to parse shape records before soldier coordinates and construct `new Obstacle(shapeCount, shapes)`. Add custom-map state and message parsing for pre-game synchronization. Keep the old generated-circle path working when the server sends no custom map.

- [ ] **Step 7: Run the shape check and compile**

Run:

```powershell
& "$jdk\bin\javac.exe" -d bin-test -sourcepath 'src;test' test\GraphServer\MapShapeTest.java
& "$jdk\bin\java.exe" -ea -cp bin-test GraphServer.MapShapeTest
```

Expected: `map-shape-check: PASS`, followed by a successful full compile.

---

### Task 5: Build the host-only pre-game map editor

**Files:**
- Create: `src/Graphwar/MapEditorPanel.java`
- Modify: `src/Graphwar/PreGameScreen.java`
- Modify: `src/Graphwar/GameData.java`
- Test: `test/Graphwar/MapEditorPanelTest.java`

**Interfaces:**
- `MapEditorPanel.ApplyListener.apply(MapShape[] shapes)` receives an applied shape list.
- `MapEditorPanel(MapShape[] initialShapes, ApplyListener listener)` creates the editor and `getShapeCount()` reports the current list size.
- `PreGameScreen.setCustomMap(MapShape[] shapes, boolean enabled)` updates the host status/control.
- `GameData.setCustomMap(MapShape[] shapes)` sends the validated list to the room server.

- [ ] **Step 1: Write the failing editor check**

Create `test/Graphwar/MapEditorPanelTest.java`:

```java
package Graphwar;

import GraphServer.MapShape;
import javax.swing.SwingUtilities;

public class MapEditorPanelTest {
    public static void main(String[] args) throws Exception {
        final int[] count = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                MapEditorPanel panel = new MapEditorPanel(new MapShape[0], null);
                count[0] = panel.getShapeCount();
            }
        });
        if (count[0] != 0) {
            throw new AssertionError("editor should start empty");
        }
        System.out.println("map-editor-check: PASS");
    }
}
```

The package-visible constructor accepts the initial `MapShape[]` and an apply callback; the first check must fail because `MapEditorPanel` does not exist yet.

- [ ] **Step 2: Run the editor check and verify the expected failure**

Run the small Swing assertion from the repository root:

```powershell
& "$jdk\bin\javac.exe" -d bin-test -sourcepath 'src;test' test\Graphwar\MapEditorPanelTest.java
```

Expected: FAIL because `MapEditorPanel` does not exist yet.

- [ ] **Step 3: Implement the minimal editor canvas and controls**

Create a modal pre-game panel with a 770×450 canvas and toolbar buttons:

```text
Circle | Rectangle | Undo | Clear | Apply | Cancel
```

Use mouse press/drag/release to create one normalized shape at a time, draw the white map, axes, and black shapes, and keep all geometry within the map bounds. Apply returns a copy of the list; cancel returns no changes.

- [ ] **Step 4: Add the host-only entry point**

Add a `Map Editor` button to `PreGameScreen`. Enable it only when `GameData.isLeader()` and the room is pre-game. Opening the editor uses the current custom shapes, and applying calls `GameData.setCustomMap`. Non-host clients see the synchronized custom-map state but cannot edit it.

- [ ] **Step 5: Wire map broadcasts into the pre-game screen**

On `SET_MAP`, update `GameData` and the pre-game status label. Do not start a game or mutate the current generated-map fallback until the host applies the editor. Disable map editing once the room leaves pre-game.

- [ ] **Step 6: Run the editor check and compile**

Run `& "$jdk\bin\java.exe" -ea -cp bin-test Graphwar.MapEditorPanelTest` and then the full build. Expected output is `map-editor-check: PASS` and only the existing deprecation warnings.

---

### Task 6: Full build and runtime verification

**Files:**
- Modify: `graphwar.jar`

**Interfaces:**
- Produces the rebuilt runnable jar with all source and resource changes.

- [ ] **Step 1: Compile all Java sources**

Run:

```powershell
if(Test-Path bin){Remove-Item -Recurse -Force bin}
New-Item -ItemType Directory -Force bin | Out-Null
$files = Get-ChildItem src\Graphwar,src\GraphServer,src\GlobalServer,src\RoomServer -Filter *.java | ForEach-Object FullName
& "$jdk\bin\javac.exe" -Xlint:deprecation -d bin -sourcepath src -classpath bin $files
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
```

Expected: exit code 0 with only the two known `Toolkit.getFontMetrics` deprecation warnings.

- [ ] **Step 2: Package the runnable jar**

Run:

```powershell
& "$jdk\bin\jar.exe" cfe graphwar.jar Graphwar.Graphwar -C bin GraphServer -C bin Graphwar -C . rsc
```

Expected: exit code 0 and a newer `graphwar.jar` timestamp.

- [ ] **Step 3: Smoke-test borderless scaling**

Launch with `--fullscreen`, inspect the window bounds and style with `user32.dll`, and verify it is responsive, borderless, and equal to the active display bounds. Confirm the logical canvas scale at 1920×1080 is 1.8 and the side bars are 240 pixels each.

- [ ] **Step 4: Smoke-test both room trajectory settings**

Create a local room, verify the host can switch between `Shooter-relative` and `Global graph (both teams)`, verify non-host controls are disabled, and fire a simple function in each mode. Confirm global preview and fired path use fixed map axes.

- [ ] **Step 5: Smoke-test the custom map flow**

In the host pre-game editor, draw one circle and one rectangle, apply them, add at least one player per team, and start the game. Verify the map is rendered identically for clients, projectiles stop on both shapes, terrain explosions still work, and soldier placement does not hang.

- [ ] **Step 6: Report the exact backup, jar, and verification outputs**

Record the backup directory, rebuilt jar path, compile exit code, assertion-check results, fullscreen bounds/style, and any remaining limitations. Do not claim completion without these outputs.
