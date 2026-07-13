package Graphwar;

import GraphServer.Constants;
import GraphServer.MapShape;

public class GlobalTrajectorySlopeTest {
    public static void main(String[] args) throws Exception {
        Function steepLine = new Function("2*x");
        steepLine.processGlobalRange(new Obstacle(0, new MapShape[0]), new Player[0], 0, 0, Constants.NORMAL_FUNC);

        if (steepLine.getNumSteps() < 1000) {
            throw new AssertionError("global graph stopped before re-entering the map: " + steepLine.getNumSteps());
        }

        boolean visiblePoint = false;
        for (int i = 0; i < steepLine.getNumSteps(); i++) {
            double x = Constants.PLANE_LENGTH * steepLine.getX(i) / Constants.PLANE_GAME_LENGTH
                    + Constants.PLANE_LENGTH / 2.0;
            double y = -Constants.PLANE_LENGTH * steepLine.getY(i) / Constants.PLANE_GAME_LENGTH
                    + Constants.PLANE_HEIGHT / 2.0;
            if (x >= 0 && x < Constants.PLANE_LENGTH && y >= 0 && y < Constants.PLANE_HEIGHT) {
                visiblePoint = true;
                break;
            }
        }

        if (!visiblePoint) {
            throw new AssertionError("global graph did not produce an in-map point");
        }
        System.out.println("global-trajectory-slope-check: PASS");
    }
}
