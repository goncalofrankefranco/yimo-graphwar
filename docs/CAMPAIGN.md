# YIMO Graphwar offline campaign

The campaign is an offline, sequential curriculum. Each lesson has two steps:

1. a guided example with a supplied function and construction explanation;
2. an adaptation challenge with a moved target and a changed function.

The target coordinates are calibrated against the same shooter-relative
trajectory and collision engine used by a normal game shot. Lesson files live
in `rsc/campaign/lesson-01.properties` through
`rsc/campaign/lesson-20.properties` and are loaded by
`Graphwar.CampaignLesson`.

While a lesson is open, the function field updates the board immediately. A
valid expression is rendered as an orange dashed `AIM PREVIEW / NOT FIRED`
path; pressing Fire switches to the existing animated shot and sound effects.
Previewing never advances lesson progress.

## Curriculum

| Lessons | Topic | Function families |
| --- | --- | --- |
| 01 | Axes | Constant and slope basics |
| 02 | Straight lines | Positive slope |
| 03 | Slope control | Comparing line coefficients |
| 04 | Parabolas | Quadratic curvature and width |
| 05 | Trigonometry | Sine amplitude and phase |
| 06 | First-order ODE | Height-dependent decay |
| 07 | Second-order ODE | Initial angle and acceleration |
| 08 | Global graph | Fixed-coordinate graphing |
| 09 | Custom maps | Slope through terrain corridors |
| 10 | Final challenge | Combined line and sine |
| 11 | Negative slopes | Descending lines |
| 12 | Steep lines | Scaling a large positive slope |
| 13 | Cubic curves | Scaled odd-power polynomial |
| 14 | Absolute value | V-shaped curves and cusps |
| 15 | Square roots | Domain-safe `sqrt(abs(x))` |
| 16 | Reciprocal curves | Asymptotes and continuous branches |
| 17 | Exponential growth | Scaled `exp` growth |
| 18 | Logarithms | Valid `ln` domains and input shifts |
| 19 | Cosine waves | Amplitude and wavelength |
| 20 | Tangent curves | Safe intervals around asymptotes |

## Normal-function rule

Normal mode is shooter-relative. The game anchors the curve at the firing
soldier, so adding a constant to the outside of a function does not translate
the shot. For example, `2*x`, `2*x+3`, and `2*x-8` produce the same path in
this mode. The lessons therefore change slope, curvature, amplitude, phase,
scale, or the function's input instead of teaching constants as a vertical
offset. Constants remain meaningful in global graphs and differential-equation
modes, which use different calculations.

## Adding a lesson

Copy the two-step shape of an existing `.properties` file and provide:

- a unique sequential `id`;
- `title`, `instructions`, `guide`, `hint`, `mode`, `trajectory`, and `objective`;
- top-level step-1 compatibility fields (`function`, `target.*`, `shapes`);
- `step.1.*` and `step.2.*` values;
- a target inside the 770×450 logical map.

For normal shooter-relative lessons, validate both supplied functions with
`CampaignScreen.simulateStep`. The `ExtendedCampaignTest` regression check is
the reference for the additional normal-function curriculum.
