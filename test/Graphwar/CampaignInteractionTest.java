package Graphwar;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import GraphServer.Constants;

/** Regression checks for campaign navigation, identity colors, and shot animation. */
public final class CampaignInteractionTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean hasField(String name) {
        for (Field field : CampaignScreen.class.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Color pointColor(int team) {
        try {
            Method method = CampaignScreen.class.getDeclaredMethod("pointColor", int.class);
            method.setAccessible(true);
            return (Color) method.invoke(null, team);
        } catch (Exception error) {
            return null;
        }
    }

    private static int animationSteps(int totalSteps, long elapsedMillis) {
        try {
            Method method = CampaignScreen.class.getDeclaredMethod("animationSteps", int.class, long.class);
            method.setAccessible(true);
            return ((Integer) method.invoke(null, totalSteps, elapsedMillis)).intValue();
        } catch (Exception error) {
            return -1;
        }
    }

    public static void main(String[] args) {
        check(hasField("previousButton"), "lesson screen needs a previous-lesson button");
        check(hasField("nextButton"), "lesson screen needs a next-lesson button");

        Color player = pointColor(Constants.TEAM1);
        Color opponent = pointColor(Constants.TEAM2);
        check(player != null && player.getBlue() > player.getRed(), "player point must be blue");
        check(opponent != null && opponent.getRed() > opponent.getBlue(), "opponent point must be red");

        check(animationSteps(100, 0) == 0, "the graph must start hidden");
        check(animationSteps(100, 66) > 0, "the graph must reveal progressively");
        check(animationSteps(100, 10000) == 100, "the graph must finish revealing");
        System.out.println("campaign-interaction-check: PASS");
    }
}
