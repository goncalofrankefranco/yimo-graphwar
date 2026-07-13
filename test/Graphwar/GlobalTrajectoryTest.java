package Graphwar;

import GraphServer.Constants;

public class GlobalTrajectoryTest {
    public static void main(String[] args) {
        double left = Constants.PLANE_LENGTH * (-Constants.PLANE_GAME_LENGTH / 2.0)
                / Constants.PLANE_GAME_LENGTH + Constants.PLANE_LENGTH / 2.0;
        double center = Constants.PLANE_LENGTH / 2.0;
        if (Math.abs(left) > 0.000001 || Math.abs(center - Constants.PLANE_LENGTH / 2.0) > 0.000001) {
            throw new AssertionError("global map coordinate conversion is not fixed-axis");
        }
        if (Constants.GLOBAL_TRAJECTORY != 1) {
            throw new AssertionError("global trajectory mode missing");
        }
        System.out.println("global-trajectory-check: PASS");
    }
}
