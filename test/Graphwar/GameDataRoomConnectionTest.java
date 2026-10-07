package Graphwar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A new room connection must discard stale room state before its reader starts. */
public final class GameDataRoomConnectionTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void reset(GameData data, boolean tournamentRoom) {
        try {
            Method method = GameData.class.getDeclaredMethod("resetRoomState", boolean.class);
            method.setAccessible(true);
            method.invoke(data, Boolean.valueOf(tournamentRoom));
        } catch (Exception error) {
            throw new AssertionError("GameData must reset room state before starting the receiver", error);
        }
    }

    private static boolean tournamentRoom(GameData data) {
        try {
            Method method = GameData.class.getDeclaredMethod("isTournamentRoom");
            method.setAccessible(true);
            return ((Boolean) method.invoke(data)).booleanValue();
        } catch (Exception error) {
            throw new AssertionError("GameData must expose the authenticated room type to its UI", error);
        }
    }

    public static void main(String[] args) throws Exception {
        GameData data = new GameData(null);
        data.getPlayers().add(null);
        Field leader = GameData.class.getDeclaredField("leader");
        leader.setAccessible(true);
        leader.setBoolean(data, true);

        reset(data, true);
        check(data.getPlayers().isEmpty(), "old players must be cleared before the new server reader runs");
        check(!data.isLeader(), "leader status must not leak across room connections");
        check(tournamentRoom(data), "a signed tournament connection must lock tournament-only UI actions");

        reset(data, false);
        check(!tournamentRoom(data), "practice connections must clear the previous tournament lock");
        System.out.println("game-data-room-connection-check: PASS");
    }
}
