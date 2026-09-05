# Fullscreen, Trajectory Modes, and Custom Maps Design

**Date:** 2026-07-13

## Goal

Make fullscreen use the maximum available display area without distorting the game, add a room-toggleable global graph trajectory alongside the existing shooter-relative trajectory, and give the host a pre-game editor for circle and rectangle obstacles.

## Existing constraints

- The game uses an 800×600 Swing UI with a 770×450 logical map.
- Rendering and controls are built from absolute-position Swing components.
- The current room protocol sends generated circles and soldier positions in the start-game message.
- Obstacle collision is already pixel-based, so new geometric shapes can reuse the same collision path after rasterization.
- The build must remain compatible with the existing Java 8 toolchain and avoid new dependencies.

## Design

### 1. Resolution-independent fullscreen

Keep the existing UI at its 800×600 logical size. `ScaleViewport` will calculate a uniform scale:

```text
scale = min(viewportWidth / 800, viewportHeight / 600)
```

It will center the transformed canvas, paint the `GraphUI` exactly once through the transformed Swing child-painting path, and leave equal letterbox bars where the display aspect ratio differs. A 1920×1080 display therefore renders the game at 1440×1080, preserving the original proportions while using the full available height.

Mouse coordinates received in the fullscreen viewport will be converted from display coordinates to the logical 800×600 canvas before dispatching to the existing Swing controls. Keyboard focus and text fields must continue to work. F11 and Alt+Enter remain the fullscreen toggles.

Before changing the renderer, the current source and runnable jar will be copied to a timestamped backup directory outside `bin` so the old behavior can be restored without losing the working preview/settings changes.

### 2. Room-toggleable trajectory modes

Add a host-only room setting with two values:

- **Shooter-relative:** preserve the current behavior, translating and orienting the function from the firing soldier.
- **Global graph:** evaluate the function in the map’s fixed coordinate system, starting at the left edge and drawing across the map independently of soldier positions.

Global mode uses deterministic initial conditions for every existing function type:

- Normal functions evaluate `y = f(x)` over the map domain.
- First-order ODEs start at the left edge with `y = 0`.
- Second-order ODEs start at the left edge with `y = 0` and derivative `0`.

Global mode does not mirror the trajectory based on the current team. Its collision scan includes every other living soldier from either team, including same-team soldiers; the firing soldier itself remains excluded to avoid an immediate self-hit. Preview and firing call the same calculation path so the displayed preview matches the shot.

The selected mode is stored by the room server, sent to every client with the existing room settings, and accepted only from the room leader while the room is in pre-game state. The default remains shooter-relative for compatibility with existing rooms.

### 3. Host-only custom map editor

Add a host-only **Map Editor** action to the pre-game room. The editor uses the logical 770×450 map area and provides:

- Circle tool: click-drag from a center to a radius.
- Rectangle tool: click-drag from one corner to the opposite corner.
- Undo, clear, apply, and cancel actions.

The editor maintains a bounded list of validated shape records. Applying sends the shape list to the room server. The server validates the host-only request, shape count, dimensions, and map bounds, then broadcasts the accepted map and includes it in the authoritative start-game message. Each client constructs the same rasterized `Obstacle` from those shapes, so rendering, projectile collision, terrain destruction, and soldier placement use identical geometry.

If no custom map is applied, the current random circle generation remains unchanged. Applying an empty custom map intentionally produces a blank map. Maps are room-local and are not persisted to disk in this iteration.

The server must reject or safely handle maps that leave no valid soldier spawn positions rather than retrying forever. Shape coordinates will be clamped or rejected at the network boundary, and the existing random map path remains the fallback when no custom map is selected.

## Data flow

```text
Host room controls
        │
        ├── trajectory mode ──► room server ──► all clients
        └── map editor shapes ─► validation ───► all clients/start message

Function input + trajectory mode + current soldiers
        └──► one calculation path ──► preview or authoritative animation

Display pixels ──► scaled logical canvas ──► existing Swing components
Mouse pixels ────► inverse scale ───────────► existing controls
```

## Error handling and limits

- Malformed or unauthorized room-setting messages are ignored by the server.
- Map shape payloads are validated before storage or broadcast.
- Shape count and dimensions are bounded to keep protocol messages and raster work predictable.
- The start-game spawn search has a finite attempt limit and must fail safely if a custom map is unusable.
- Invalid function input continues to be rejected before sending, as in the existing game.

## Verification

- Compile all Java sources with the installed Java 8 compiler.
- Run a small deterministic check for fullscreen scale/offset calculations at 800×600 and 1920×1080.
- Run a deterministic check for global graph coordinates and same-team collision eligibility.
- Run the game in fullscreen and verify the window is borderless, fills the display, and retains usable input.
- Start a room with both trajectory modes and verify the selected mode reaches every client.
- Create a circle/rectangle map as host, start the game, and verify every client renders and collides with the same geometry.
