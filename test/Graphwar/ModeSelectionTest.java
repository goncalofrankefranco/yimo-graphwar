package Graphwar;

import GraphServer.Constants;
import GraphServer.GraphServer;

/** Ensures each room-mode button selects its own mode instead of cycling. */
public final class ModeSelectionTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        check(PreGameScreen.modeForButton(0) == Constants.NORMAL_FUNC, "normal mode selection");
        check(PreGameScreen.modeForButton(1) == Constants.FST_ODE, "first-order mode selection");
        check(PreGameScreen.modeForButton(2) == Constants.SND_ODE, "second-order mode selection");
        check(GraphServer.isValidGameMode(Constants.NORMAL_FUNC), "normal mode must be valid");
        check(GraphServer.isValidGameMode(Constants.FST_ODE), "first-order mode must be valid");
        check(GraphServer.isValidGameMode(Constants.SND_ODE), "second-order mode must be valid");
        check(!GraphServer.isValidGameMode(-1), "negative mode must be rejected");
        check(!GraphServer.isValidGameMode(3), "unknown mode must be rejected");
        System.out.println("mode-selection-check: PASS");
    }
}
