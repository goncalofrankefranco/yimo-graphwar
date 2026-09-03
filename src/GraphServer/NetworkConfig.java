//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package GraphServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/** Validated YIMO endpoint settings shared by the client and Java servers. */
public final class NetworkConfig {
    public static final String FILE_NAME = "yimo.properties";
    public static final String DEFAULT_GLOBAL_HOST = "127.0.0.1";
    public static final int DEFAULT_GLOBAL_PORT = 23762;
    public static final int DEFAULT_ROOM_PORT_START = 30000;
    public static final int DEFAULT_ROOM_PORT_END = 30049;
    public static final String DEFAULT_TOURNAMENT_API = "http://127.0.0.1:8080";
    public static final String DEFAULT_BUILD_ID = "YIMO-Graphwar-2.0.0";
    public static final int DEFAULT_PROTOCOL_VERSION = 2;

    private final String globalHost;
    private final int globalPort;
    private final int roomPortStart;
    private final int roomPortEnd;
    private final String tournamentApiBaseUrl;
    private final String buildId;
    private final int protocolVersion;

    private NetworkConfig(String globalHost, int globalPort, int roomPortStart, int roomPortEnd,
            String tournamentApiBaseUrl, String buildId, int protocolVersion) {
        this.globalHost = globalHost;
        this.globalPort = globalPort;
        this.roomPortStart = roomPortStart;
        this.roomPortEnd = roomPortEnd;
        this.tournamentApiBaseUrl = tournamentApiBaseUrl;
        this.buildId = buildId;
        this.protocolVersion = protocolVersion;
    }

    public static NetworkConfig defaults() {
        return new NetworkConfig(DEFAULT_GLOBAL_HOST, DEFAULT_GLOBAL_PORT, DEFAULT_ROOM_PORT_START,
                DEFAULT_ROOM_PORT_END, DEFAULT_TOURNAMENT_API, DEFAULT_BUILD_ID, DEFAULT_PROTOCOL_VERSION);
    }

    /** Loads the external file, then the packaged local-development file, then safe defaults. */
    public static NetworkConfig load() {
        try {
            String configuredPath = System.getProperty("yimo.config");
            if (configuredPath != null && configuredPath.trim().length() > 0) {
                return load(new File(configuredPath));
            }

            File external = new File(FILE_NAME);
            if (external.isFile()) {
                return load(external);
            }

            InputStream packaged = NetworkConfig.class.getResourceAsStream("/rsc/yimo.properties");
            if (packaged != null) {
                try {
                    return fromProperties(readProperties(packaged));
                } finally {
                    packaged.close();
                }
            }
            return defaults();
        } catch (IOException error) {
            throw new IllegalStateException("Invalid YIMO network configuration", error);
        }
    }

    public static NetworkConfig load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Missing YIMO network configuration: " + file);
        }
        FileInputStream input = new FileInputStream(file);
        try {
            return fromProperties(readProperties(input));
        } finally {
            input.close();
        }
    }

    public static NetworkConfig fromProperties(Properties properties) throws IOException {
        if (properties == null) {
            throw new IOException("YIMO network properties are missing");
        }
        String host = value(properties, "global.host", DEFAULT_GLOBAL_HOST);
        int globalPort = port(properties, "global.port", DEFAULT_GLOBAL_PORT);
        int roomStart = port(properties, "room.port.start", DEFAULT_ROOM_PORT_START);
        int roomEnd = port(properties, "room.port.end", DEFAULT_ROOM_PORT_END);
        if (roomStart > roomEnd) {
            throw new IOException("room.port.start must not exceed room.port.end");
        }

        String tournamentApi = value(properties, "tournament.api.baseUrl", DEFAULT_TOURNAMENT_API);
        validateUrl(tournamentApi);
        String buildId = value(properties, "build.id", DEFAULT_BUILD_ID);
        if (buildId.length() > 64 || !buildId.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("build.id contains unsupported characters");
        }
        int protocolVersion = positive(properties, "protocol.version", DEFAULT_PROTOCOL_VERSION);
        return new NetworkConfig(host, globalPort, roomStart, roomEnd, tournamentApi, buildId, protocolVersion);
    }

    /** Applies only the supported deployment overrides; positional host arguments are not accepted. */
    public static NetworkConfig fromCommandLine(String[] args) {
        NetworkConfig base = load();
        if (args == null) {
            return base;
        }

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i])) {
                try {
                    base = load(new File(nextArgument(args, ++i, "--config")));
                } catch (IOException error) {
                    throw new IllegalArgumentException(error.getMessage(), error);
                }
            }
        }

        Properties properties = base.toProperties();
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if ("--global-host".equals(option)) {
                properties.setProperty("global.host", nextArgument(args, ++i, option));
            } else if ("--global-port".equals(option)) {
                properties.setProperty("global.port", nextArgument(args, ++i, option));
            } else if ("--tournament-api".equals(option)) {
                properties.setProperty("tournament.api.baseUrl", nextArgument(args, ++i, option));
            } else if ("--config".equals(option)) {
                i++;
            } else {
                throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        try {
            return fromProperties(properties);
        } catch (IOException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
    }

    private static String nextArgument(String[] args, int index, String option) {
        if (index >= args.length || args[index] == null || args[index].trim().length() == 0) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    private static Properties readProperties(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(input, "UTF-8"));
        return properties;
    }

    private static String value(Properties properties, String key, String fallback) throws IOException {
        String value = properties.getProperty(key, fallback);
        if (value == null || value.trim().length() == 0 || value.indexOf('&') >= 0 || containsControl(value)
                || containsWhitespace(value)) {
            throw new IOException(key + " is invalid");
        }
        return value.trim();
    }

    private static int port(Properties properties, String key, int fallback) throws IOException {
        int value = positive(properties, key, fallback);
        if (value > 65535) {
            throw new IOException(key + " must be between 1 and 65535");
        }
        return value;
    }

    private static int positive(Properties properties, String key, int fallback) throws IOException {
        String raw = properties.getProperty(key, Integer.toString(fallback));
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) {
                throw new IOException(key + " must be positive");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IOException(key + " must be a number", error);
        }
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void validateUrl(String value) throws IOException {
        try {
            URI uri = new URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IOException("tournament.api.baseUrl must be an HTTP(S) URL");
            }
        } catch (URISyntaxException error) {
            throw new IOException("tournament.api.baseUrl is invalid", error);
        }
    }

    private Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("global.host", globalHost);
        properties.setProperty("global.port", Integer.toString(globalPort));
        properties.setProperty("room.port.start", Integer.toString(roomPortStart));
        properties.setProperty("room.port.end", Integer.toString(roomPortEnd));
        properties.setProperty("tournament.api.baseUrl", tournamentApiBaseUrl);
        properties.setProperty("build.id", buildId);
        properties.setProperty("protocol.version", Integer.toString(protocolVersion));
        return properties;
    }

    public String getGlobalHost() {
        return globalHost;
    }

    public int getGlobalPort() {
        return globalPort;
    }

    public int getRoomPortStart() {
        return roomPortStart;
    }

    public int getRoomPortEnd() {
        return roomPortEnd;
    }

    public String getTournamentApiBaseUrl() {
        return tournamentApiBaseUrl;
    }

    public String getBuildId() {
        return buildId;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }
}
