//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package GraphServer;

import java.util.Properties;
import java.util.prefs.Preferences;

/** Persists client-only endpoint choices; Java servers continue using yimo.properties. */
public final class NetworkPreferences {
    private static final String NODE_NAME = "client-network";
    private static final String HOST = "global.host";
    private static final String PORT = "global.port";
    private static final String API = "tournament.api.baseUrl";

    private NetworkPreferences() {
    }

    public static Preferences userNode() {
        return Preferences.userNodeForPackage(NetworkPreferences.class).node(NODE_NAME);
    }

    public static NetworkConfig load(Preferences preferences, NetworkConfig base) {
        if (preferences == null || base == null) {
            throw new IllegalArgumentException("Network preferences require a base configuration");
        }
        Properties properties = properties(base);
        copyIfPresent(preferences, HOST, properties);
        copyIfPresent(preferences, PORT, properties);
        copyIfPresent(preferences, API, properties);
        try {
            return NetworkConfig.fromProperties(properties);
        } catch (java.io.IOException error) {
            // ponytail: a damaged local preference must not prevent the client from starting;
            // the Settings screen can replace it with a validated value.
            return base;
        }
    }

    public static void save(Preferences preferences, NetworkConfig config) {
        if (preferences == null || config == null) {
            throw new IllegalArgumentException("Network preferences require a configuration");
        }
        preferences.put(HOST, config.getGlobalHost());
        preferences.put(PORT, Integer.toString(config.getGlobalPort()));
        preferences.put(API, config.getTournamentApiBaseUrl());
        try {
            preferences.flush();
        } catch (java.util.prefs.BackingStoreException error) {
            throw new IllegalStateException("Could not save YIMO network settings", error);
        }
    }

    public static void clear(Preferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("Network preferences are missing");
        }
        try {
            preferences.clear();
            preferences.flush();
        } catch (java.util.prefs.BackingStoreException error) {
            throw new IllegalStateException("Could not clear YIMO network settings", error);
        }
    }

    public static NetworkConfig fromFields(NetworkConfig base, String host, String port, String api) {
        if (base == null) {
            throw new IllegalArgumentException("Network configuration is missing");
        }
        Properties properties = properties(base);
        properties.setProperty(HOST, host == null ? "" : host.trim());
        properties.setProperty(PORT, port == null ? "" : port.trim());
        properties.setProperty(API, api == null ? "" : api.trim());
        try {
            return NetworkConfig.fromProperties(properties);
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
    }

    public static boolean hasExplicitArguments(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if ("--global-host".equals(arg) || "--global-port".equals(arg)
                    || "--tournament-api".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void copyIfPresent(Preferences preferences, String key, Properties properties) {
        String value = preferences.get(key, null);
        if (value != null) {
            properties.setProperty(key, value);
        }
    }

    private static Properties properties(NetworkConfig config) {
        Properties properties = new Properties();
        properties.setProperty(HOST, config.getGlobalHost());
        properties.setProperty(PORT, Integer.toString(config.getGlobalPort()));
        properties.setProperty("room.port.start", Integer.toString(config.getRoomPortStart()));
        properties.setProperty("room.port.end", Integer.toString(config.getRoomPortEnd()));
        properties.setProperty(API, config.getTournamentApiBaseUrl());
        properties.setProperty("build.id", config.getBuildId());
        properties.setProperty("protocol.version", Integer.toString(config.getProtocolVersion()));
        return properties;
    }
}
