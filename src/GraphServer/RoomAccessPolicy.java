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
    private final Set<String> assignedParticipants;
    private final Set<String> usedNonces = new HashSet<String>();
    private final Set<String> activeParticipants = new HashSet<String>();

    private RoomAccessPolicy(boolean required, String secret, String matchId, int roomSlot,
            Set<String> assignedParticipants) {
        this.required = required;
        this.secret = secret;
        this.matchId = matchId;
        this.roomSlot = roomSlot;
        this.assignedParticipants = assignedParticipants;
    }

    public static RoomAccessPolicy open() {
        return new RoomAccessPolicy(false, "", "", -1, new HashSet<String>());
    }

    public static RoomAccessPolicy required(String secret, String matchId, int roomSlot,
            String participantA, String participantB) {
        if (secret == null || secret.length() == 0 || matchId == null || matchId.length() == 0 || roomSlot <= 0) {
            throw new IllegalArgumentException("Tournament room access settings are incomplete");
        }
        if (!isParticipantId(participantA) || !isParticipantId(participantB) || participantA.equals(participantB)) {
            throw new IllegalArgumentException("Tournament room participant assignments are invalid");
        }
        Set<String> assigned = new HashSet<String>();
        assigned.add(participantA);
        assigned.add(participantB);
        return new RoomAccessPolicy(true, secret, matchId, roomSlot, assigned);
    }

    private static boolean isParticipantId(String participantId) {
        return participantId != null && participantId.matches("[A-Za-z0-9_-]{1,100}");
    }

    public boolean isRequired() {
        return required;
    }

    public String getMatchId() {
        return matchId;
    }

    public int getRoomSlot() {
        return roomSlot;
    }

    public synchronized RoomAccessToken.Payload accept(String token, long nowMillis) {
        if (!required) {
            return null;
        }
        RoomAccessToken.Payload payload = RoomAccessToken.verify(token, secret, nowMillis);
        if (payload == null || payload.getProtocolVersion() != Constants.PROTOCOL_VERSION
                || !Constants.BUILD_ID.equals(payload.getBuildId()) || !matchId.equals(payload.getMatchId())
                || payload.getRoomSlot() != roomSlot || usedNonces.contains(payload.getNonce())
                || !assignedParticipants.contains(payload.getParticipantId())
                || activeParticipants.contains(payload.getParticipantId())) {
            return null;
        }
        usedNonces.add(payload.getNonce());
        activeParticipants.add(payload.getParticipantId());
        return payload;
    }

    public synchronized void release(String participantId) {
        if (participantId != null) activeParticipants.remove(participantId);
    }
}
