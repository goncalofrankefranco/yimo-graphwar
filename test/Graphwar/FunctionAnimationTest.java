package Graphwar;

import GraphServer.Constants;

/** Checks slope-aware display progress without changing the sampled path. */
public final class FunctionAnimationTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class SampleFunction extends Function {
        private final double[] x;
        private final double[] y;

        SampleFunction(double[] x, double[] y) throws MalformedFunction {
            super("0");
            if (x.length != y.length) {
                throw new IllegalArgumentException("sample arrays must have equal length");
            }
            this.x = x.clone();
            this.y = y.clone();
        }

        @Override
        public double getX(int index) {
            return x[index];
        }

        @Override
        public double getY(int index) {
            return y[index];
        }

        @Override
        public int getNumSteps() {
            return x.length;
        }
    }

    private static SampleFunction samples(int count, double yPerX) throws MalformedFunction {
        double[] x = new double[count];
        double[] y = new double[count];
        for (int i = 0; i < count; i++) {
            x[i] = i;
            y[i] = i * yPerX;
        }
        return new SampleFunction(x, y);
    }

    private static void assertMonotonic(FunctionAnimation animation) {
        int previous = animation.stepAt(0);
        for (long elapsed = 1; elapsed <= animation.durationMillis(); elapsed++) {
            int current = animation.stepAt(elapsed);
            check(current >= previous, "visible sample count must never move backwards");
            previous = current;
        }
    }

    public static void main(String[] args) throws Exception {
        Function flat = samples(101, 0.0);
        Function steep = samples(101, 3.0);
        FunctionAnimation flatAnimation = new FunctionAnimation(flat);
        FunctionAnimation steepAnimation = new FunctionAnimation(steep);

        check(flatAnimation.durationMillis() > 0, "flat path must have a positive animation duration");
        long expectedFlatDuration = Math.round((flat.getNumSteps() - 1) * 1000.0 / Constants.FUNCTION_VELOCITY);
        check(flatAnimation.durationMillis() == expectedFlatDuration,
                "flat path must preserve the existing 1,500-samples/second pace");
        check(flatAnimation.durationMillis() < steepAnimation.durationMillis(),
                "steep path must take longer than a flat path");
        check(steepAnimation.durationMillis() <= 1200, "steep paths must respect the duration cap");
        long sameElapsed = Math.max(1, flatAnimation.durationMillis() / 4);
        check(steepAnimation.stepAt(sameElapsed) < flatAnimation.stepAt(sameElapsed),
                "steep path must reveal fewer samples at the same elapsed time");
        check(flatAnimation.stepAt(-1) == 0, "negative elapsed time must start at the first sample");
        check(flatAnimation.stepAt(flatAnimation.durationMillis()) == flat.getNumSteps(),
                "flat path must finish on its last sample");
        check(steepAnimation.stepAt(steepAnimation.durationMillis()) == steep.getNumSteps(),
                "steep path must finish on its last sample");
        assertMonotonic(flatAnimation);
        assertMonotonic(steepAnimation);

        double[] verticalX = new double[101];
        double[] verticalY = new double[101];
        for (int i = 0; i < verticalY.length; i++) {
            verticalY[i] = i;
        }
        Function vertical = new SampleFunction(verticalX, verticalY);
        FunctionAnimation verticalAnimation = new FunctionAnimation(vertical);
        check(verticalAnimation.durationMillis() > 0 && verticalAnimation.durationMillis() <= 1200,
                "vertical segments must stay finite and respect the duration cap");
        check(verticalAnimation.stepAt(verticalAnimation.durationMillis()) == vertical.getNumSteps(),
                "vertical path must finish on its last sample");

        Function stationary = new SampleFunction(new double[] { 1, 1 }, new double[] { 1, 1 });
        FunctionAnimation stationaryAnimation = new FunctionAnimation(stationary);
        check(stationaryAnimation.stepAt(0) == 0, "stationary path starts without drawing a segment");
        check(stationaryAnimation.stepAt(1) == stationary.getNumSteps(),
                "stationary path must complete immediately without NaN progress");
        System.out.println("function-animation-check: PASS");
    }
}
