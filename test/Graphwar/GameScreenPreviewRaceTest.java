package Graphwar;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import GraphServer.Constants;

/** Guards the second player's preview from a delayed first-player calculation. */
public final class GameScreenPreviewRaceTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(new GameData(null).getCurrentTurnPlayerID() == -1,
                "preview requests during reset must handle the no-current-player state");
        check(GameScreen.isCurrentPreviewRequest(2, 2, 22, 22),
                "the newest request for the active second player must be applied");
        check(!GameScreen.isCurrentPreviewRequest(1, 2, 11, 22),
                "a delayed first-player preview must not replace the second player's preview");
        check(!GameScreen.isCurrentPreviewRequest(2, 2, 11, 22),
                "a preview calculated for a different turn must be discarded");
        check(!GraphPlane.shouldReverseFunctionPath(true, true),
                "team-two preview and fired curves must share the unmirrored local-view transform");
        check(GraphPlane.shouldReverseFunctionPath(false, true),
                "a global curve must mirror with team-two terrain");
        check(!GraphPlane.shouldReverseFunctionPath(false, false),
                "team-one curves must remain unmirrored in the normal view");
        checkSecondTeamPreviewStartsFromItsOwnSide();
        System.out.println("game-screen-preview-race-check: PASS");
    }

    private static void checkSecondTeamPreviewStartsFromItsOwnSide() {
        try {
            GameData data = new GameData(null);
            Player first = new Player("first", 11, Constants.TEAM1, false, 1, false);
            Player second = new Player("second", 22, Constants.TEAM2, true, 1, false);
            first.startSoldier(0, 100, 225);
            second.startSoldier(0, 600, 225);
            List<Player> players = new ArrayList<Player>();
            players.add(first);
            players.add(second);
            set(data, "players", players);
            set(data, "currentTurn", Integer.valueOf(1));
            set(data, "gameState", Integer.valueOf(Constants.GAME));
            set(data, "obstacle", new Obstacle(0, new int[0]));

            Function preview = data.buildPreviewFunction("0", second.getID());
            check(preview != null && preview.getNumSteps() > 0,
                    "the second local player must receive a computed aim preview");
            check(Constants.PLANE_LENGTH - preview.getX(0) > 500,
                    "the second-team preview must render beside the second player's point");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void set(GameData data, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = GameData.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(data, value);
    }
}
