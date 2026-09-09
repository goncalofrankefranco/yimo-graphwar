package Graphwar;

/** Regression checks for the live, non-firing tutorial aim preview. */
public final class CampaignAimPreviewTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        CampaignLesson lesson = CampaignLesson.loadAll(CampaignScreen.class)[0];
        String expression = lesson.getStep(1).getFunction();
        Function preview = CampaignScreen.previewStep(lesson, 1, expression);

        check(preview != null && preview.getNumSteps() > 0,
                "a valid tutorial function must produce a preview trajectory");
        check(CampaignScreen.visibleTrajectorySteps(preview, false) == preview.getNumSteps(),
                "the preview must show the complete predicted trajectory");
        check(CampaignScreen.visibleTrajectorySteps(preview, true) == 0,
                "a fired trajectory must start hidden for animation");
        check(CampaignScreen.aimPreviewText(false).equals("AIM PREVIEW"),
                "the canvas must label a non-firing trajectory as an aim preview");
        System.out.println("campaign-aim-preview-check: PASS");
    }
}
