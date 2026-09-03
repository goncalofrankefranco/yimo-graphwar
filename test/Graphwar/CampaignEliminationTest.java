package Graphwar;

import java.lang.reflect.Method;

/** Ensures a hit has a visible opponent elimination sequence. */
public final class CampaignEliminationTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int frame(long elapsedMillis) {
        try {
            Method method = CampaignScreen.class.getDeclaredMethod("eliminationFrame", long.class);
            method.setAccessible(true);
            return ((Integer) method.invoke(null, elapsedMillis)).intValue();
        } catch (Exception error) {
            return -1;
        }
    }

    public static void main(String[] args) {
        check(frame(0) == 0, "elimination must start at its first frame");
        check(frame(70) > 0, "the opponent must visibly animate after being hit");
        check(frame(10000) >= 5, "the elimination sequence must reach its final frame");
        System.out.println("campaign-elimination-check: PASS");
    }
}
