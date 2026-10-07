package Graphwar;

import java.lang.reflect.Method;

/** Verifies room setup permissions independently of the room leader role. */
public final class TournamentRoomUiPolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean policy(String methodName, boolean... arguments) {
        try {
            Class<?>[] argumentTypes = new Class<?>[arguments.length];
            Object[] values = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                argumentTypes[i] = boolean.class;
                values[i] = Boolean.valueOf(arguments[i]);
            }
            Method method = PreGameScreen.class.getDeclaredMethod(methodName, argumentTypes);
            method.setAccessible(true);
            return ((Boolean) method.invoke(null, values)).booleanValue();
        } catch (Exception error) {
            throw new AssertionError("PreGameScreen must enforce " + methodName, error);
        }
    }

    public static void main(String[] args) {
        check(policy("canEditRoomSettings", false, true), "practice-room leader can edit settings");
        check(!policy("canEditRoomSettings", false, false), "practice-room non-leader cannot edit settings");
        check(!policy("canEditRoomSettings", true, true), "tournament-room leader cannot edit settings");
        check(!policy("canEditRoomSettings", true, false), "tournament-room non-leader cannot edit settings");
        check(policy("canChangeRoster", false), "practice rooms can edit their roster");
        check(!policy("canChangeRoster", true), "tournament rooms cannot add local or CPU players");
        check(policy("showRoomSetupOptions", false), "practice rooms show editable setup options");
        check(!policy("showRoomSetupOptions", true), "tournament rooms hide setup options from both players");
        System.out.println("tournament-room-ui-policy-check: PASS");
    }
}
