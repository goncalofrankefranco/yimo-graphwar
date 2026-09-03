//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package GraphServer;

import java.util.HashSet;
import java.util.Set;

/** Optional room gate. Practice rooms stay open; tournament rooms require a signed token. */
public final class RoomAccessPolicy {
    private final boolean required;
    private final String secret;
    private final String matchId;
    private final int roomSlot;
    private final Set<String> usedNonces = new HashSet<String>();

    private RoomAccessPolicy(boolean required, String secret, String matchId, int roomSlot) {
        this.required = required;
        this.secret = secret;
        this.matchId = matchId;
        this.roomSlot = roomSlot;
    }

    public static RoomAccessPolicy open() {
        return new RoomAccessPolicy(false, "", "", -1);
    }

    public static RoomAccessPolicy required(String secret, String matchId, int roomSlot) {
        if (secret == null || secret.length() == 0 || matchId == null || matchId.length() == 0 || roomSlot <= 0) {
            throw new IllegalArgumentException("Tournament room access settings are incomplete");
        }
        return new RoomAccessPolicy(true, secret, matchId, roomSlot);
    }

    public boolean isRequired() {
        return required;
    }

    public synchronized RoomAccessToken.Payload accept(String token, long nowMillis) {
        if (!required) {
            return null;
        }
        RoomAccessToken.Payload payload = RoomAccessToken.verify(token, secret, nowMillis);
        if (payload == null || payload.getProtocolVersion() != Constants.PROTOCOL_VERSION
                || !Constants.BUILD_ID.equals(payload.getBuildId()) || !matchId.equals(payload.getMatchId())
                || payload.getRoomSlot() != roomSlot || usedNonces.contains(payload.getNonce())) {
            return null;
        }
        usedNonces.add(payload.getNonce());
        return payload;
    }
}
