package Graphwar;

/** Regression check that the parabola lesson cannot be completed with a flat line. */
public final class CampaignDifficultyTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        CampaignLesson lesson = CampaignLesson.load(CampaignScreen.class, "/rsc/campaign/lesson-04.properties");
        check(CampaignScreen.simulate(lesson, "0").getNumPlayersHit() == 0,
                "parabola lesson must reject a flat y=0 trajectory");
        check(CampaignScreen.simulate(lesson, lesson.getFunction()).getNumPlayersHit() > 0,
                "parabola lesson answer must still reach the target");
        System.out.println("campaign-difficulty-check: PASS");
    }
}
