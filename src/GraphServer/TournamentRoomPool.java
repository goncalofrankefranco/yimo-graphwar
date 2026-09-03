//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package GraphServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded in-process room allocation state for tournament room launchers. */
public final class TournamentRoomPool {
    public enum State {
        AVAILABLE, ASSIGNED, IN_PROGRESS, DRAINING, OFFLINE
    }

    private final Map<Integer, Slot> slots = new LinkedHashMap<Integer, Slot>();

    public TournamentRoomPool(int portStart, int portEnd, int prewarmed) {
        if (portStart <= 0 || portEnd < portStart || portEnd > 65535 || prewarmed < 0
                || prewarmed > portEnd - portStart + 1) {
            throw new IllegalArgumentException("Invalid tournament room pool bounds");
        }
        for (int port = portStart; port <= portEnd; port++) {
            boolean warm = port - portStart < prewarmed;
            slots.put(port, new Slot(port, warm ? State.AVAILABLE : State.OFFLINE));
        }
    }

    public synchronized Assignment assign(String matchId, long expiryMillis) {
        if (matchId == null || matchId.length() == 0 || expiryMillis <= System.currentTimeMillis()) {
            return null;
        }
        for (Slot slot : slots.values()) {
            if (slot.state == State.AVAILABLE) {
                slot.state = State.ASSIGNED;
                slot.matchId = matchId;
                slot.expiryMillis = expiryMillis;
                slot.lastHeartbeatMillis = System.currentTimeMillis();
                return new Assignment(slot.port, matchId, expiryMillis);
            }
        }
        return null;
    }

    public synchronized boolean expand(int count) {
        if (count < 0) {
            return false;
        }
        int changed = 0;
        for (Slot slot : slots.values()) {
            if (changed >= count) {
                break;
            }
            if (slot.state == State.OFFLINE) {
                slot.state = State.AVAILABLE;
                changed++;
            }
        }
        return changed == count;
    }

    public synchronized boolean markInProgress(int port, String matchId) {
        Slot slot = slots.get(port);
        if (slot == null || !sameMatch(slot, matchId) || slot.state != State.ASSIGNED) {
            return false;
        }
        slot.state = State.IN_PROGRESS;
        slot.lastHeartbeatMillis = System.currentTimeMillis();
        return true;
    }

    public synchronized boolean heartbeat(int port, String matchId, long nowMillis) {
        Slot slot = slots.get(port);
        if (slot == null || !sameMatch(slot, matchId)
                || (slot.state != State.ASSIGNED && slot.state != State.IN_PROGRESS)) {
            return false;
        }
        slot.lastHeartbeatMillis = nowMillis;
        return true;
    }

    public synchronized boolean release(int port, String matchId) {
        Slot slot = slots.get(port);
        if (slot == null || !sameMatch(slot, matchId)) {
            return false;
        }
        slot.state = State.AVAILABLE;
        slot.matchId = null;
        slot.expiryMillis = 0;
        slot.lastHeartbeatMillis = 0;
        return true;
    }

    public synchronized List<Integer> sweep(long nowMillis, long heartbeatTimeoutMillis) {
        List<Integer> drained = new ArrayList<Integer>();
        for (Slot slot : slots.values()) {
            if ((slot.state == State.ASSIGNED || slot.state == State.IN_PROGRESS)
                    && (slot.expiryMillis < nowMillis
                    || nowMillis - slot.lastHeartbeatMillis > heartbeatTimeoutMillis)) {
                slot.state = State.DRAINING;
                drained.add(slot.port);
            }
        }
        return drained;
    }

    public synchronized int count(State state) {
        int total = 0;
        for (Slot slot : slots.values()) {
            if (slot.state == state) {
                total++;
            }
        }
        return total;
    }

    public synchronized State state(int port) {
        Slot slot = slots.get(port);
        return slot == null ? null : slot.state;
    }

    private boolean sameMatch(Slot slot, String matchId) {
        return matchId != null && matchId.equals(slot.matchId);
    }

    private static final class Slot {
        private final int port;
        private State state;
        private String matchId;
        private long expiryMillis;
        private long lastHeartbeatMillis;

        private Slot(int port, State state) {
            this.port = port;
            this.state = state;
        }
    }

    public static final class Assignment {
        private final int roomSlot;
        private final String matchId;
        private final long expiryMillis;

        private Assignment(int roomSlot, String matchId, long expiryMillis) {
            this.roomSlot = roomSlot;
            this.matchId = matchId;
            this.expiryMillis = expiryMillis;
        }

        public int getRoomSlot() {
            return roomSlot;
        }

        public String getMatchId() {
            return matchId;
        }

        public long getExpiryMillis() {
            return expiryMillis;
        }
    }
}
