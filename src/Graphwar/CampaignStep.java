//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import GraphServer.MapShape;

/** One guided or adaptation step inside a campaign lesson. */
public final class CampaignStep {
    private final int number;
    private final String instructions;
    private final String guide;
    private final String hint;
    private final String function;
    private final int targetX;
    private final int targetY;
    private final int targetRadius;
    private final MapShape[] shapes;

    public CampaignStep(int number, String instructions, String guide, String hint, String function,
            int targetX, int targetY, int targetRadius, MapShape[] shapes) {
        this.number = number;
        this.instructions = instructions;
        this.guide = guide;
        this.hint = hint;
        this.function = function;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetRadius = targetRadius;
        this.shapes = shapes == null ? new MapShape[0] : shapes.clone();
    }

    public int getNumber() {
        return number;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getGuide() {
        return guide;
    }

    public String getHint() {
        return hint;
    }

    public String getFunction() {
        return function;
    }

    public int getTargetX() {
        return targetX;
    }

    public int getTargetY() {
        return targetY;
    }

    public int getTargetRadius() {
        return targetRadius;
    }

    public MapShape[] getShapes() {
        return shapes.clone();
    }
}
