package GraphServer;

import java.util.List;

/** Verifies bounded warm-room allocation, heartbeats, expansion, and release. */
public final class TournamentRoomPoolTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        TournamentRoomPool pool = new TournamentRoomPool(30000, 30049, 20);
        check(pool.count(TournamentRoomPool.State.AVAILABLE) == 20, "twenty rooms must start warm");
        TournamentRoomPool.Assignment first = pool.assign("match-1", System.currentTimeMillis() + 60_000L);
        check(first != null && first.getRoomSlot() == 30000, "first assignment must use the first warm port");
        check(pool.markInProgress(30000, "match-1"), "assigned room must enter progress");
        check(pool.heartbeat(30000, "match-1", System.currentTimeMillis()), "active room heartbeat must work");
        for (int i = 2; i <= 20; i++) {
            check(pool.assign("match-" + i, System.currentTimeMillis() + 60_000L) != null,
                    "all warm rooms must be assignable");
        }
        check(pool.assign("overflow", System.currentTimeMillis() + 60_000L) == null,
                "pool must reject assignments beyond its warm capacity");
        check(pool.expand(1), "one offline port must be expandable");
        check(pool.assign("match-21", System.currentTimeMillis() + 60_000L) != null,
                "expanded room must be assignable");
        List<Integer> drained = pool.sweep(System.currentTimeMillis() + 11_000L, 10_000L);
        check(drained.contains(30000) && pool.state(30000) == TournamentRoomPool.State.DRAINING,
                "expired heartbeat must drain the room for supervisor recovery");
        check(pool.release(30000, "match-1"), "completed room must release");
        check(pool.state(30000) == TournamentRoomPool.State.AVAILABLE, "released room must be reusable");
        System.out.println("tournament-room-pool-check: PASS");
    }
}
