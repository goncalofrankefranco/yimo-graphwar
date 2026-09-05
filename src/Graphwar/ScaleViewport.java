//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import GraphServer.Constants;

/** Uniform logical-to-physical mapping for the resizable battlefield. */
public final class ScaleViewport {
    private ScaleViewport() {
    }

    public static Transform forSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return new Transform(1.0, 0, 0);
        }
        double scale = Math.min((double) width / Constants.PLANE_LENGTH,
                (double) height / Constants.PLANE_HEIGHT);
        int offsetX = (int) Math.round((width - Constants.PLANE_LENGTH * scale) / 2.0);
        int offsetY = (int) Math.round((height - Constants.PLANE_HEIGHT * scale) / 2.0);
        return new Transform(scale, offsetX, offsetY);
    }

    public static final class Transform {
        public final double scale;
        public final int offsetX;
        public final int offsetY;

        private Transform(double scale, int offsetX, int offsetY) {
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public int toLogicalX(int physicalX) {
            return (int) Math.round((physicalX - offsetX) / scale);
        }

        public int toLogicalY(int physicalY) {
            return (int) Math.round((physicalY - offsetY) / scale);
        }

        public int toPhysicalX(int logicalX) {
            return offsetX + (int) Math.round(logicalX * scale);
        }

        public int toPhysicalY(int logicalY) {
            return offsetY + (int) Math.round(logicalY * scale);
        }
    }
}
