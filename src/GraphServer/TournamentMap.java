package GraphServer;

import java.util.ArrayList;
import java.util.List;

/** Authored left-side obstacles are reflected across the arena to keep each tournament fair. */
public final class TournamentMap {
    private static final int[][] OBSTACLES = {
        {110, 190, 48, 18}, {250, 95, 18, 66}, {175, 300, 56, 16}, {305, 235, 16, 70},
        {105, 65, 18, 56}, {220, 160, 54, 16}, {130, 365, 64, 15}, {285, 45, 52, 15},
        {85, 250, 18, 58}, {200, 30, 16, 72}, {315, 340, 42, 16}, {145, 120, 62, 15},
        {260, 285, 18, 60}, {90, 145, 52, 15}, {235, 380, 55, 15}, {325, 125, 15, 60},
        {165, 220, 18, 64}, {285, 175, 50, 15}, {110, 330, 50, 15}, {215, 250, 55, 15},
        {305, 295, 16, 56}, {140, 35, 52, 15}, {250, 105, 16, 58}, {95, 390, 60, 15},
        {195, 345, 48, 15},
    };

    private TournamentMap() {
    }

    public static MapShape[] shapesForRound(int round) {
        if (round < 1) throw new IllegalArgumentException("Tournament round must be positive");
        List<MapShape> shapes = new ArrayList<MapShape>();
        // ponytail: 25 authored tiers; add validated entries before raising this cap.
        int authoredRound = Math.min(round, OBSTACLES.length);
        for (int i = 0; i < authoredRound; i++) {
            int[] obstacle = OBSTACLES[i];
            int x = obstacle[0], y = obstacle[1], width = obstacle[2], height = obstacle[3];
            shapes.add(MapShape.rectangle(x, y, width, height));
            shapes.add(MapShape.rectangle(Constants.PLANE_LENGTH - x - width, y, width, height));
        }
        return shapes.toArray(new MapShape[shapes.size()]);
    }
}
