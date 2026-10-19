# YIMO Graphwar 3.2.0 Design

**Status:** Proposed for review
**Purpose:** A friendlier, clearer presentation release that remains compatible with the existing 2.2.0 game and tournament servers.

## Research

The Math Wars Reels page showed 15 videos; all 15 were watched directly in the signed-in browser. No reel was downloaded. Common patterns were colorful graph arenas, a readable coordinate grid, distinct round obstacle silhouettes, visible equations, and short “DIRECT HIT!” feedback. Comments also repeatedly asked how to learn the game and whether formulas must be memorized. The Roblox project’s assets and branding are not to be copied.

The current app is a Java 8 Swing game. The shot trace currently advances at a fixed 1,500 samples per second in GameData, while the server independently calculates the authoritative hit list. Obstacle uses a black/white raster both for rendering and collision checks, so visual changes must not mutate that collision mask. Both the Java room handshake and tournament HTTP service require the exact build ID YIMO-Graphwar-2.2.0 and protocol 2.

## Product direction

Keep the YIMO ink/paper/orange identity, but make the battlefield warmer and more welcoming: soft teal, coral, mint, and gold surfaces; gentle patterns; clear axes and grid; and stronger separation between the equation, aim preview, players, and obstacles. Use lightweight Java2D drawing and the existing art system, not copied Roblox assets or a new graphics dependency.

## Proposed behavior

1. **Slope-aware trace.** Draw the same server-approved function path, but pace it by cumulative screen-space distance. Flat sections retain the current 1,500-samples-per-second reference; steep sections take longer because they cover more screen distance per sample. Cap a trace at 1,200 ms. The endpoint, collision calculation, kill positions, turn order, and wire message remain unchanged.

2. **Authoritative hit callout.** Show “DIRECT HIT!” briefly when the room server’s hit list includes an opponent. Do not show it for misses or teammate-only hits. The callout is feedback only; the game server continues to determine damage and winners.

3. **Friendly arena appearance.** Offer Classic, Garden, and Arcade palettes with subtle dot/ring/stripe patterns, while leaving each map’s collision geometry and collision pixels untouched. Keep the coordinate grid, axes, names, and team contrast legible.

4. **Separate Appearance menu.** Add a main-menu entry with a live preview for local soldier-marker shape, accent color, and pattern, plus the arena palette and a reset-to-Classic action. Preserve blue/red team outlines and player names. Save these cosmetic preferences with Java’s built-in Preferences API; they are local-only and are not sent to other players.

5. **Combined-functions curriculum.** Remove the proposed match-screen hint. Add ten offline campaign lessons (21–30), keeping the existing guided-example then adaptation step in every lesson. Each first step explains the role of each term and gives a model; its second step moves the target, leaves the field blank, and asks the learner to recreate the expression and adapt one relevant coefficient or scale. Teach a repeatable intuition: identify the baseline trend, add the shape needed, predict the combined curve, then adjust one parameter at a time while checking the aim preview.

   - 21: line plus sine, m*x + a*sin(x), for backbone and ripple;
   - 22: coefficient strength, separating the trend's slope from the wave's amplitude;
   - 23: input scale, showing how sin(k*x) changes wave spacing;
   - 24: line plus cosine, comparing the two wave shapes and their starting phase;
   - 25: quadratic power plus line and sine, for broad even curvature;
   - 26: cubic power plus line and sine, for odd growth and sign behavior;
   - 27: exponential plus line, inspired by the reels' exponential/linear combinations;
   - 28: x*cos(k*x) plus a linear correction, inspired by the reels' modulated cosine;
   - 29: a bounded trend plus a wave, using the supported tanh-like form a*x/sqrt(1+x^2) + b*sin(k*x);
   - 30: a three-term capstone where the learner explains and adapts each term.

   “Power” is interpreted as both coefficient strength and mathematical exponents. All ten lessons use normal, shooter-relative mode and parser-supported expressions; they do not teach an outside additive constant as a vertical shift. Lesson 10 already teaches a global line-plus-wave, so this sequence teaches richer composition in normal mode. Do not use tanh itself: the current parser does not support it, and adding it would require server changes. Use existing supported operations for the bounded-trend example. Preserve existing lesson progress and sequential unlocking.

## Compatibility and release constraints

- Product version shown in the app, installer, and release is **3.2.0**.
- Wire build ID remains **YIMO-Graphwar-2.2.0** and protocol remains **2**. This is required because the live 2.2.0 servers reject another build ID.
- All new behavior is client-side presentation/cosmetics. No tournament-service, server, endpoint, port, or protocol change is required.
- Existing 2.2.0 clients and the published 2.2.0 GitHub release remain supported and unmodified.
- Keep Java 8 compatibility and add no dependency.
- Point customization is interpreted as local soldier/player-marker cosmetics, not score or graph coordinates.

## Acceptance

- The app badge and Windows installer identify as 3.2.0; network handshakes and tournament requests still identify as build 2.2.0/protocol 2.
- Flat and steep functions visibly trace at different horizontal rates, finish within the cap, and preserve their exact server-authoritative result.
- “DIRECT HIT!” appears only for an opponent hit present in the server hit list.
- Appearance preferences survive restart; Classic remains the safe default; team identity remains obvious.
- The campaign contains 30 sequential lessons; lessons 21–30 teach varied combined-function intuition in two calibrated steps each, without resetting earlier progress.
- Changing an arena theme changes rendered pixels but never changes collision results.
- The release is built and smoke-tested without replacing or deleting the 2.2.0 GitHub release or touching the production servers.

## Out of scope

No new game physics, collision shapes, map generator, tournament rules, network protocol, multiplayer-visible cosmetics, Roblox assets, or production-server deployment.
