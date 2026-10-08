package GraphServer;

public final class TournamentMapTest {
    public static void main(String[] args) throws Exception {
        MapShape[] first = TournamentMap.shapesForRound(1);
        check(first.length == 2, "round one should use the authored opening map");
        check(encoded(first).equals(encoded(TournamentMap.shapesForRound(1))), "map selection must be deterministic");
        check(encoded(TournamentMap.shapesForRound(26)).equals(encoded(TournamentMap.shapesForRound(25))),
                "rounds after 25 must use map 25");

        int previousCount = 0;
        for (int round = 1; round <= 25; round++) {
            MapShape[] shapes = TournamentMap.shapesForRound(round);
            check(shapes.length > previousCount, "each bracket round must add authored obstacles");
            previousCount = shapes.length;
            for (MapShape shape : shapes) {
                check(shape.isWithinMap(), "every authored shape must fit the 770x450 plane");
                check(containsMirror(shapes, shape), "every authored shape must have a horizontal mirror");
            }
            for (int y = Constants.SOLDIER_RADIUS; y <= Constants.PLANE_HEIGHT - Constants.SOLDIER_RADIUS; y += 10) {
                for (int x = 25; x <= 55; x += 5) {
                    check(clear(shapes, x, y) && clear(shapes, Constants.PLANE_LENGTH - x, y),
                            "the full edge spawn corridors must stay clear");
                }
            }
            check(shotRouteAcrossArena(shapes), "every map must keep a selection-radius-clear rightward shot route");
        }
        RoomAccessPolicy required = RoomAccessPolicy.required("map-test-secret", "map-match", 31000, "a", "b");
        try {
            GraphServer missingRound = new GraphServer(0, required);
            missingRound.finalize();
            throw new AssertionError("a required tournament room must reject a missing round");
        } catch (IllegalArgumentException expected) {
            // A required access policy without its authoritative round must fail closed.
        }
    }

    private static boolean shotRouteAcrossArena(MapShape[] shapes) {
        boolean[][] visited = new boolean[42][74];
        int[] queueX = new int[42 * 74], queueY = new int[42 * 74];
        int head = 0, tail = 0;
        queueX[tail] = 2;
        queueY[tail++] = 20;
        visited[20][2] = true;
        while (head < tail) {
            int x = queueX[head], y = queueY[head++];
            if (x == 71) return true;
            int[][] moves = {{1, 0}, {0, 1}, {0, -1}};
            for (int[] move : moves) {
                int nextX = x + move[0], nextY = y + move[1];
                if (nextX < 0 || nextX >= 74 || nextY < 0 || nextY >= 42 || visited[nextY][nextX]) continue;
                int px = 20 + nextX * 10, py = 20 + nextY * 10;
                if (!clear(shapes, px, py)) continue;
                visited[nextY][nextX] = true;
                queueX[tail] = nextX;
                queueY[tail++] = nextY;
            }
        }
        return false;
    }

    private static boolean clear(MapShape[] shapes, int x, int y) {
        for (MapShape shape : shapes) if (shape.intersects(x, y, Constants.SOLDIER_SELECTION_RADIUS)) return false;
        return true;
    }

    private static boolean containsMirror(MapShape[] shapes, MapShape shape) {
        int x = shape.getType() == MapShape.CIRCLE
                ? Constants.PLANE_LENGTH - 1 - shape.getX()
                : Constants.PLANE_LENGTH - shape.getX() - shape.getA();
        for (MapShape other : shapes) {
            if (other.getType() == shape.getType() && other.getX() == x && other.getY() == shape.getY()
                    && other.getA() == shape.getA() && other.getB() == shape.getB()) return true;
        }
        return false;
    }

    private static String encoded(MapShape[] shapes) {
        StringBuilder result = new StringBuilder();
        for (MapShape shape : shapes) result.append(shape.encode()).append(';');
        return result.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
