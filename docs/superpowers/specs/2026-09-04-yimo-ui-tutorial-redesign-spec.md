# YIMO Graphwar UI, Mode Selection, and Tutorial Redesign

## Goal

Make the battlefield fill its available area at every window size, make room
mode buttons select the clicked mode, turn every campaign lesson into a guided
example plus an adaptation challenge, and replace the main menu with a bold
YIMO Olympiad visual system.

## Approved direction

- Preserve the Java 8 Swing client and the logical game coordinate system.
- Scale the battlefield uniformly into its available panel; preserve readable
  controls and use letterboxing only when the aspect ratio requires it.
- Keep the YIMO black, white, and orange identity, but express it as an
  Olympiad control deck: dark mathematical grid, high-contrast typography,
  orange trajectory accents, and large centered actions.
- Each lesson has two deterministic steps. Step 1 presents a model function
  and construction instructions. Step 2 moves the target and requires the
  player to adapt the formula. Completion requires both steps.
- Mode selection sends an explicit validated mode to the authoritative room
  server instead of cycling from the current value.

## Constraints

- No fullscreen APIs.
- Java source and bytecode remain compatible with Java 8.
- No external UI or animation dependencies.
- Existing anti-cheat, trajectory, map, network, and installer behavior must
  remain compatible.
- Tests must cover viewport math, mode selection, two-step lesson progress,
  and existing regression behavior.
