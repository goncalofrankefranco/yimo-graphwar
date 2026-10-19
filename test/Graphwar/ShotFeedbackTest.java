package Graphwar;

import GraphServer.Constants;

public final class ShotFeedbackTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        check("DIRECT HIT!".equals(ShotFeedback.textFor(Constants.TEAM1,
                new int[] { Constants.TEAM2 })), "an opponent hit must show feedback");
        check("DIRECT HIT!".equals(ShotFeedback.textFor(Constants.TEAM2,
                new int[] { Constants.TEAM1 })), "feedback must work for either shooter team");
        check(ShotFeedback.textFor(Constants.TEAM1,
                new int[] { Constants.TEAM1 }) == null, "a teammate hit must not show feedback");
        check(ShotFeedback.textFor(Constants.TEAM1, new int[0]) == null,
                "a miss must not show feedback");
        System.out.println("shot-feedback-check: PASS");
    }
}
