package Graphwar;

import GraphServer.Constants;

/** Checks the additional normal-function curriculum and both-step shots. */
public final class ExtendedCampaignTest {
    private static final String[] NORMAL_FUNCTIONS = {
        "-0.1*x",
        "0.3*x",
        "x^3/1000",
        "abs(x)/3",
        "sqrt(abs(x))",
        "1/(x+20)",
        "exp(x/10)",
        "ln(abs(x))",
        "cos(x)",
        "tan(x/20)"
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
        return String.format("nearest %.1f px at (%.1f,%.1f), target (%d,%d)",
                nearest, nearestX, nearestY, step.getTargetX(), step.getTargetY());
    }

    public static void main(String[] args) throws Exception {
        CampaignLesson[] lessons = CampaignLesson.loadAll(CampaignScreen.class);
        check(lessons.length == 20, "the campaign must contain twenty lessons");

        for (int index = 0; index < NORMAL_FUNCTIONS.length; index++) {
            CampaignLesson lesson = lessons[index + 10];
            check(lesson.getId().equals(String.format("lesson-%02d", index + 11)),
                    "extended lesson id must be sequential");
            check(lesson.getMode() == Constants.NORMAL_FUNC,
                    "extended lessons must use normal functions");
            check(lesson.getTrajectory() == Constants.SHOOTER_RELATIVE_TRAJECTORY,
                    "extended lessons must teach shooter-relative paths");
            check(lesson.getStepCount() == 2, "each extended lesson must have two steps");
            check(lesson.getStep(1).getFunction().equals(NORMAL_FUNCTIONS[index]),
                    "extended lesson must cover the planned function family");
            check(!lesson.getStep(1).getFunction().equals(lesson.getStep(2).getFunction()),
                    "the adaptation step must change the function");
            for (int step = 1; step <= lesson.getStepCount(); step++) {
                Function trajectory = CampaignScreen.simulateStep(lesson, step,
                        lesson.getStep(step).getFunction());
                check(trajectory.getNumPlayersHit() > 0,
                        "the supplied extended lesson function must hit its target: "
                                + lesson.getId() + " step " + step + " ("
                                + nearestPoint(lesson.getStep(step), trajectory) + ")");
            }
        }
        System.out.println("extended-campaign-check: PASS");
    }
}
