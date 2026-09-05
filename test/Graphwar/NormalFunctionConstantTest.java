package Graphwar;

import GraphServer.Constants;
import GraphServer.MapShape;

/** Proves that additive constants cancel in shooter-relative normal mode. */
public final class NormalFunctionConstantTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Function fire(String expression) throws MalformedFunction {
        Player shooter = new Player("You", 0, Constants.TEAM1, true, 1, true);
        shooter.startSoldier(0, 140, Constants.PLANE_HEIGHT / 2);
        Function function = new Function(expression);
        function.processFunctionRange(new Obstacle(0, new MapShape[0]),
                new Player[] { shooter }, 1, 0, false);
        return function;
    }

    public static void main(String[] args) throws Exception {
        Function base = fire("2*x");
        Function shifted = fire("2*x+37");
        check(base.getNumSteps() == shifted.getNumSteps(), "constant must not change step count");
        for (int i = 0; i < base.getNumSteps(); i++) {
            check(Math.abs(base.getX(i) - shifted.getX(i)) < 0.000001,
                    "constant must not change x at step " + i);
            check(Math.abs(base.getY(i) - shifted.getY(i)) < 0.000001,
                    "constant must not change y at step " + i);
        }
        System.out.println("normal-function-constant-check: PASS");
    }
}
