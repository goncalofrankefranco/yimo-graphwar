package Graphwar;

import GraphServer.Constants;

/** Pure geometry checks for the logical battlefield viewport. */
public final class ScaleViewportTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void close(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.000001) {
            throw new AssertionError(message + " actual=" + actual + " expected=" + expected);
        }
    }

    public static void main(String[] args) {
        ScaleViewport.Transform wide = ScaleViewport.forSize(1920, 1080);
        close(wide.scale, 2.4, "1920x1080 must scale the 800-wide logical battlefield");
        check(wide.offsetX == 36 && wide.offsetY == 0, "wide viewport must be centered");

        ScaleViewport.Transform fourThree = ScaleViewport.forSize(1280, 1024);
        close(fourThree.scale, 1280.0 / Constants.PLANE_LENGTH,
                "four-three viewport must use the largest uniform scale");
        check(fourThree.offsetY > 0 && fourThree.offsetX == 0,
                "four-three viewport must letterbox vertically");

        ScaleViewport.Transform transform = ScaleViewport.forSize(1500, 800);
        int logicalX = 123;
        int logicalY = 321;
        check(transform.toLogicalX(transform.toPhysicalX(logicalX)) == logicalX,
                "x conversion must be reversible");
        check(transform.toLogicalY(transform.toPhysicalY(logicalY)) == logicalY,
                "y conversion must be reversible");
        check(ScaleViewport.forSize(0, 0).scale == 1.0, "empty sizes must stay safe");
        System.out.println("scale-viewport-check: PASS");
    }
}
