package Graphwar;

import java.util.prefs.Preferences;

/** Checks that every campaign lesson teaches once and then adapts once. */
public final class CampaignStepsTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        CampaignLesson[] lessons = CampaignLesson.loadAll(CampaignScreen.class);
        Preferences node = Preferences.userRoot().node("yimo-campaign-steps-" + System.nanoTime());
        try {
            CampaignProgress progress = new CampaignProgress(node);
            for (CampaignLesson lesson : lessons) {
                check(lesson.getStepCount() == 2, "every lesson needs two steps: " + lesson.getId());
                CampaignStep guided = lesson.getStep(1);
                CampaignStep adapted = lesson.getStep(2);
                check(guided.getFunction().length() > 0 && adapted.getFunction().length() > 0,
                        "both steps need a valid model function: " + lesson.getId());
                check(CampaignScreen.simulateStep(lesson, 1, guided.getFunction()).getNumPlayersHit() > 0,
                        "guided function must reach its target: " + lesson.getId());
                check(CampaignScreen.simulateStep(lesson, 2, adapted.getFunction()).getNumPlayersHit() > 0,
                        "adapted function must reach its target: " + lesson.getId());
                check(guided.getTargetX() != adapted.getTargetX() || guided.getTargetY() != adapted.getTargetY(),
                        "step 2 must move the target: " + lesson.getId());
                check(!progress.isComplete(lesson.getId()), "fresh lesson must be incomplete");
                progress.markStepComplete(lesson.getId(), 1);
                check(!progress.isComplete(lesson.getId()), "step 1 alone must not complete the lesson");
                progress.markStepComplete(lesson.getId(), 2);
                check(progress.isComplete(lesson.getId()), "step 2 must complete the lesson");
                progress.reset();
            }
        } finally {
            node.removeNode();
        }
        System.out.println("campaign-steps-check: PASS");
    }
}
