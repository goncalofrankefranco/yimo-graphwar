package Graphwar;

import java.util.Locale;
import java.util.prefs.Preferences;

import GraphServer.Constants;

/** Checks the ten combined-function lessons, both calibrated steps, and sequential unlock. */
public final class CombinedFunctionCampaignTest {
    private static final String[] TITLES = {
        "Backbone + Ripple", "Wave Strength", "Wave Spacing", "Line + Cosine",
        "Quadratic + Wave", "Cubic + Wave", "Exponential + Line", "Modulated Cosine",
        "Bounded Trend + Wave", "Three-Term Challenge"
    };
    private static final String[] FUNCTIONS = {
        "0.25*x+2*sin(x)", "0.15*x+2*sin(x/9)", "0.15*x+3*sin(x/7)",
        "0.2*x+2*cos(x/8)", "0.04*x^2+0.15*x+2*sin(x/7)",
        "0.001*x^3+0.15*x+2*sin(x/8)", "exp(x/14)+0.15*x",
        "x*cos(x/10)/5+0.12*x", "0.8*x/sqrt(1+x^2)+2*sin(x/7)",
        "0.12*x+2*sin(x/8)+0.02*x^2"
    };
    private static final String[][] TERMS = {
        { "line", "sine" }, { "line", "sine", "amplitude" }, { "sine", "scale" },
        { "line", "cosine" }, { "quadratic", "sine" }, { "cubic", "sine" },
        { "exponential", "line" }, { "cosine", "line" }, { "bounded", "sine" },
        { "line", "sine", "quadratic" }
    };

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String nearestPoint(CampaignStep step, Function trajectory) {
        double nearest = Double.MAX_VALUE;
        double nearestX = 0;
        double nearestY = 0;
        for (int i = 0; i < trajectory.getNumSteps(); i++) {
            double x = Constants.PLANE_LENGTH * trajectory.getX(i) / Constants.PLANE_GAME_LENGTH
                    + Constants.PLANE_LENGTH / 2.0;
            double y = -Constants.PLANE_LENGTH * trajectory.getY(i) / Constants.PLANE_GAME_LENGTH
                    + Constants.PLANE_HEIGHT / 2.0;
            double distance = Math.hypot(x - step.getTargetX(), y - step.getTargetY());
            if (distance < nearest) {
                nearest = distance;
                nearestX = x;
                nearestY = y;
            }
        }
        return String.format(Locale.ENGLISH, "nearest %.1f px at (%.1f,%.1f), target (%d,%d)",
                nearest, nearestX, nearestY, step.getTargetX(), step.getTargetY());
    }

    public static void main(String[] args) throws Exception {
        CampaignLesson[] lessons = CampaignLesson.loadAll(CampaignScreen.class);
        check(lessons.length == 30, "campaign must contain thirty sequential lessons");
        for (int index = 0; index < lessons.length; index++) {
            check(lessons[index].getId().equals(String.format(Locale.ENGLISH, "lesson-%02d", index + 1)),
                    "lesson IDs must remain sequential");
        }

        for (int index = 0; index < TITLES.length; index++) {
            CampaignLesson lesson = lessons[index + 20];
            check(lesson.getTitle().equals(TITLES[index]), "combined lesson title must match its topic");
            check(lesson.getMode() == Constants.NORMAL_FUNC, "new lessons must use normal functions");
            check(lesson.getTrajectory() == Constants.SHOOTER_RELATIVE_TRAJECTORY,
                    "new lessons must use shooter-relative trajectories");
            check(lesson.getStepCount() == 2, "each new lesson must have two steps");
            CampaignStep guided = lesson.getStep(1);
            CampaignStep adapted = lesson.getStep(2);
            check(guided.getFunction().equals(FUNCTIONS[index]),
                    "guided function must match the planned combined family");
            check(!guided.getFunction().equals(adapted.getFunction()),
                    "adaptation must change the formula or one of its parameters");
            check(guided.getTargetX() != adapted.getTargetX() || guided.getTargetY() != adapted.getTargetY(),
                    "adaptation must move the target");
            for (String term : TERMS[index]) {
                check(guided.getGuide().toLowerCase(Locale.ENGLISH).contains(term),
                        "guided explanation must explain the " + term + " term in " + lesson.getId());
            }
            check(adapted.getGuide().trim().length() > 0 && adapted.getInstructions().trim().length() > 0,
                    "adaptation must include instructions and an explanation");
            for (int step = 1; step <= 2; step++) {
                CampaignStep challenge = lesson.getStep(step);
                Function trajectory = CampaignScreen.simulateStep(lesson, step, challenge.getFunction());
                check(trajectory.getNumPlayersHit() > 0,
                        "supplied answer must reach " + lesson.getId() + " step " + step + " ("
                                + nearestPoint(challenge, trajectory) + ")");
            }
        }

        Preferences root = Preferences.userRoot();
        Preferences node = root.node("YIMO-CombinedCampaignTest-" + System.nanoTime());
        try {
            CampaignProgress progress = new CampaignProgress(node);
            progress.markComplete("lesson-20");
            check(progress.isComplete("lesson-20"), "existing lesson-20 completion must be retained");
            check(progress.isUnlocked(lessons, 20), "completing lesson 20 must unlock lesson 21");
        } finally {
            node.removeNode();
            root.flush();
        }
        System.out.println("combined-function-campaign-check: PASS");
    }
}
