//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import GraphServer.Constants;

/** Maps elapsed shot-animation time to visible path samples using screen distance. */
final class FunctionAnimation {
    static final long MAX_DURATION_MILLIS = 1200L;
    private static final double PIXELS_PER_GAME_UNIT =
            (double) Constants.PLANE_LENGTH / Constants.PLANE_GAME_LENGTH;

    private final int numSteps;
    private final double[] cumulativeDistance;
    private final double totalDistance;
    private final long durationMillis;

    FunctionAnimation(Function function) {
        if (function == null) {
            throw new IllegalArgumentException("function");
        }
        numSteps = function.getNumSteps();
        cumulativeDistance = new double[Math.max(0, numSteps)];

        double horizontalDistance = 0.0;
        int horizontalSegments = 0;
        double total = 0.0;
        for (int i = 1; i < numSteps; i++) {
            double dx = (function.getX(i) - function.getX(i - 1)) * PIXELS_PER_GAME_UNIT;
            double dy = (function.getY(i) - function.getY(i - 1)) * PIXELS_PER_GAME_UNIT;
            double segment = Math.hypot(dx, dy);
            if (Double.isNaN(segment) || Double.isInfinite(segment)) {
                segment = 0.0;
            }
            if (!Double.isNaN(dx) && !Double.isInfinite(dx) && Math.abs(dx) > 0.0) {
                horizontalDistance += Math.abs(dx);
                horizontalSegments++;
            }
            total += segment;
            if (Double.isNaN(total) || Double.isInfinite(total)) {
                total = Double.MAX_VALUE;
            }
            cumulativeDistance[i] = total;
        }

        totalDistance = total;
        double referenceSamplePixels = horizontalSegments > 0
                ? horizontalDistance / horizontalSegments : 1.0;
        double referenceSpeed = referenceSamplePixels * Constants.FUNCTION_VELOCITY;
        if (totalDistance <= 0.0 || referenceSpeed <= 0.0
                || Double.isNaN(referenceSpeed) || Double.isInfinite(referenceSpeed)) {
            durationMillis = 0L;
        } else {
            double naturalDuration = totalDistance / referenceSpeed * 1000.0;
            if (Double.isNaN(naturalDuration) || Double.isInfinite(naturalDuration)) {
                durationMillis = MAX_DURATION_MILLIS;
            } else {
                durationMillis = Math.min(MAX_DURATION_MILLIS,
                        Math.max(1L, Math.round(naturalDuration)));
            }
        }
    }

    int stepAt(long elapsedMillis) {
        if (numSteps <= 0 || elapsedMillis <= 0L) {
            return 0;
        }
        if (totalDistance <= 0.0 || durationMillis == 0L || elapsedMillis >= durationMillis) {
            return numSteps;
        }

        double targetDistance = totalDistance * ((double) elapsedMillis / durationMillis);
        for (int step = 1; step < numSteps; step++) {
            if (cumulativeDistance[step] >= targetDistance) {
                return step;
            }
        }
        return numSteps;
    }

    long durationMillis() {
        return durationMillis;
    }
}
