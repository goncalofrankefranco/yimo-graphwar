package Graphwar;

import java.util.prefs.Preferences;

import GraphServer.Constants;

/** Loader, sequential-unlock, and default-shot checks for the offline campaign. */
public final class CampaignTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        CampaignLesson[] lessons = CampaignLesson.loadAll(CampaignScreen.class);
        check(lessons.length == CampaignLesson.COUNT, "all campaign lessons must load");

        for (CampaignLesson lesson : lessons) {
            check(lesson.getTargetX() >= 0 && lesson.getTargetX() < Constants.PLANE_LENGTH,
                    "lesson target x must be inside the map");
            check(lesson.getTargetY() >= 0 && lesson.getTargetY() < Constants.PLANE_HEIGHT,
                    "lesson target y must be inside the map");
            check(lesson.getShapes().length <= 2, "lesson shape count must stay small and readable");
            for (GraphServer.MapShape shape : lesson.getShapes()) {
                check(shape.isWithinMap(), "lesson map shape must be inside the map");
            }
            Function trajectory = CampaignScreen.simulate(lesson, lesson.getFunction());
            check(trajectory.getNumPlayersHit() > 0,
                    "the supplied lesson function must reach its target: " + lesson.getId());
        }

        Preferences preferences = Preferences.userRoot().node("yimo-campaign-test-" + System.nanoTime());
        try {
            CampaignProgress progress = new CampaignProgress(preferences);
            check(progress.isUnlocked(lessons, 0), "the first lesson must be unlocked");
            check(!progress.isUnlocked(lessons, 1), "later lessons must start locked");
            progress.markComplete(lessons[0].getId());
            CampaignProgress reloaded = new CampaignProgress(preferences);
            check(reloaded.isComplete(lessons[0].getId()), "completion must persist");
            check(reloaded.isUnlocked(lessons, 1), "completion must unlock the next lesson");
            reloaded.reset();
            check(!reloaded.isComplete(lessons[0].getId()), "reset must clear completion");
        } finally {
            preferences.removeNode();
            preferences.flush();
        }
        System.out.println("campaign-check: PASS");
    }
}
