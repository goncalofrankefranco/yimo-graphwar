//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Stable random identity for tournament self-registration; never fingerprints hardware. */
public final class PlayerIdentity {
    private static final String NODE_NAME = "yimo-player";
    private static final String KEY = "player.id";

    private PlayerIdentity() {
    }

    public static Preferences userNode() {
        return Preferences.userNodeForPackage(PlayerIdentity.class).node(NODE_NAME);
    }

    public static String loadOrCreate(Preferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("Player preferences are missing");
        String existing = preferences.get(KEY, "");
        if (isValid(existing)) return existing;
        String generated = "yimo-" + UUID.randomUUID().toString();
        preferences.put(KEY, generated);
        try {
            preferences.flush();
        } catch (BackingStoreException error) {
            throw new IllegalStateException("Could not save the local player ID", error);
        }
        return generated;
    }

    public static boolean isValid(String value) {
        return value != null && value.matches("yimo-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
