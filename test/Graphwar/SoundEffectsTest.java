package Graphwar;

import java.lang.reflect.Method;

/** Verifies that the campaign exposes short, named shot and impact cues. */
public final class SoundEffectsTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int duration(String name) {
        try {
            Class<?> type = Class.forName("Graphwar.SoundEffects");
            Method method = type.getDeclaredMethod("durationMillis", String.class);
            method.setAccessible(true);
            return ((Integer) method.invoke(null, name)).intValue();
        } catch (Exception error) {
            return 0;
        }
    }

    public static void main(String[] args) {
        check(duration("shot") > 0, "shot cue must have a duration");
        check(duration("impact") > 0, "impact cue must have a duration");
        check(duration("unknown") == 0, "unknown cues must stay silent");
        System.out.println("sound-effects-check: PASS");
    }
}
