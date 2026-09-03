//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import GraphServer.Constants;
import GraphServer.MapShape;

/** Immutable, validated data for one offline campaign lesson. */
public final class CampaignLesson {
    private static final String RESOURCE_ROOT = "/rsc/campaign/";
    public static final int COUNT = 10;

    private final String id;
    private final String title;
    private final String instructions;
    private final String guide;
    private final String hint;
    private final int mode;
    private final int trajectory;
    private final String function;
    private final String objective;
    private final int targetX;
    private final int targetY;
    private final int targetRadius;
    private final MapShape[] shapes;

    private CampaignLesson(String id, String title, String instructions, String guide, String hint,
            int mode, int trajectory, String function, String objective,
            int targetX, int targetY, int targetRadius, MapShape[] shapes) {
        this.id = id;
        this.title = title;
        this.instructions = instructions;
        this.guide = guide;
        this.hint = hint;
        this.mode = mode;
        this.trajectory = trajectory;
        this.function = function;
        this.objective = objective;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetRadius = targetRadius;
        this.shapes = shapes.clone();
    }

    public static CampaignLesson[] loadAll(Class<?> resourceOwner) throws IOException {
        CampaignLesson[] lessons = new CampaignLesson[COUNT];
        for (int i = 0; i < COUNT; i++) {
            String number = i + 1 < 10 ? "0" + (i + 1) : Integer.toString(i + 1);
            lessons[i] = load(resourceOwner, RESOURCE_ROOT + "lesson-" + number + ".properties");
        }
        return lessons;
    }

    public static CampaignLesson load(Class<?> resourceOwner, String resource) throws IOException {
        InputStream stream = resourceOwner.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("Missing campaign lesson: " + resource);
        }

        Properties properties = new Properties();
        try {
            properties.load(new InputStreamReader(stream, "UTF-8"));
        } finally {
            stream.close();
        }

        String id = required(properties, "id", resource);
        String title = required(properties, "title", resource);
        String instructions = required(properties, "instructions", resource);
        String guide = required(properties, "guide", resource);
        String hint = required(properties, "hint", resource);
        String function = required(properties, "function", resource);
        String objective = required(properties, "objective", resource);
        if (function.length() > Constants.MAX_FUNCTION_LENGTH) {
            throw new IOException("Campaign function is too long: " + resource);
        }
        try {
            new Function(function);
        } catch (MalformedFunction error) {
            throw new IOException("Invalid campaign function: " + resource, error);
        }

        int mode = parseMode(required(properties, "mode", resource), resource);
        int trajectory = parseTrajectory(required(properties, "trajectory", resource), resource);
        int targetX = integer(properties, "target.x", resource);
        int targetY = integer(properties, "target.y", resource);
        int targetRadius = integer(properties, "target.radius", resource);
        if (targetRadius <= 0 || targetX - targetRadius < 0 || targetX + targetRadius >= Constants.PLANE_LENGTH
                || targetY - targetRadius < 0 || targetY + targetRadius >= Constants.PLANE_HEIGHT) {
            throw new IOException("Campaign target is outside the map: " + resource);
        }

        MapShape[] shapes = parseShapes(properties.getProperty("shapes", ""), resource);
        return new CampaignLesson(id, title, instructions, guide, hint, mode, trajectory, function,
                objective, targetX, targetY, targetRadius, shapes);
    }

    private static String required(Properties properties, String key, String resource) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().length() == 0) {
            throw new IOException("Missing campaign property '" + key + "': " + resource);
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key, String resource) throws IOException {
        String value = required(properties, key, resource);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IOException("Invalid campaign integer '" + key + "': " + resource, error);
        }
    }

    private static int parseMode(String value, String resource) throws IOException {
        String normalized = value.toLowerCase(Locale.ENGLISH);
        if ("normal".equals(normalized) || "function".equals(normalized)) {
            return Constants.NORMAL_FUNC;
        }
        if ("first-ode".equals(normalized) || "fst-ode".equals(normalized)
                || "first".equals(normalized)) {
            return Constants.FST_ODE;
        }
        if ("second-ode".equals(normalized) || "snd-ode".equals(normalized)
                || "second".equals(normalized)) {
            return Constants.SND_ODE;
        }
        throw new IOException("Unknown campaign mode '" + value + "': " + resource);
    }

    private static int parseTrajectory(String value, String resource) throws IOException {
        String normalized = value.toLowerCase(Locale.ENGLISH);
        if ("shooter-relative".equals(normalized) || "relative".equals(normalized)) {
            return Constants.SHOOTER_RELATIVE_TRAJECTORY;
        }
        if ("global".equals(normalized) || "global-graph".equals(normalized)) {
            return Constants.GLOBAL_TRAJECTORY;
        }
        throw new IOException("Unknown campaign trajectory '" + value + "': " + resource);
    }

    private static MapShape[] parseShapes(String value, String resource) throws IOException {
        if (value == null || value.trim().length() == 0) {
            return new MapShape[0];
        }

        String[] definitions = value.split(";");
        if (definitions.length > MapShape.MAX_SHAPES) {
            throw new IOException("Too many campaign shapes: " + resource);
        }
        List<MapShape> shapes = new ArrayList<MapShape>(definitions.length);
        for (String definition : definitions) {
            String[] parts = definition.trim().split(":");
            if (parts.length != 4 && parts.length != 5) {
                throw new IOException("Invalid campaign shape: " + resource);
            }
            try {
                String type = parts[0].toLowerCase(Locale.ENGLISH);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int a = Integer.parseInt(parts[3]);
                MapShape shape;
                if ("circle".equals(type) && parts.length == 4) {
                    shape = MapShape.circle(x, y, a);
                } else if ("rectangle".equals(type) && parts.length == 5) {
                    shape = MapShape.rectangle(x, y, a, Integer.parseInt(parts[4]));
                } else {
                    throw new IOException("Invalid campaign shape type: " + resource);
                }
                shapes.add(shape);
            } catch (NumberFormatException error) {
                throw new IOException("Invalid campaign shape number: " + resource, error);
            } catch (IllegalArgumentException error) {
                throw new IOException("Campaign shape is outside the map: " + resource, error);
            }
        }
        return shapes.toArray(new MapShape[shapes.size()]);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
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

    public int getMode() {
        return mode;
    }

    public int getTrajectory() {
        return trajectory;
    }

    public String getFunction() {
        return function;
    }

    public String getObjective() {
        return objective;
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
