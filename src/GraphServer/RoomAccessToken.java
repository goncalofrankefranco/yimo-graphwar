//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package GraphServer;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Cross-language HMAC token format used by the tournament room gate. */
public final class RoomAccessToken {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private RoomAccessToken() {
    }

    public static String issue(Payload payload, String secret) {
        if (payload == null || secret == null || secret.length() == 0) {
            throw new IllegalArgumentException("Token payload and secret are required");
        }
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.encode().getBytes(UTF8));
        return encodedPayload + "." + sign(payload.encode(), secret);
    }

    public static Payload verify(String token, String secret, long nowMillis) {
        if (token == null || secret == null || secret.length() == 0) {
            return null;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            String rawPayload = new String(Base64.getUrlDecoder().decode(parts[0]), UTF8);
            byte[] expected = Base64.getUrlDecoder().decode(sign(rawPayload, secret));
            byte[] actual = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return null;
            }
            Payload payload = Payload.parse(rawPayload);
            if (payload == null || payload.expiryMillis < nowMillis) {
                return null;
            }
            return payload;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(UTF8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", error);
        }
    }

    public static final class Payload {
        private final int protocolVersion;
        private final String buildId;
        private final String matchId;
        private final String participantId;
        private final int roomSlot;
        private final long expiryMillis;
        private final String nonce;

        public Payload(int protocolVersion, String buildId, String matchId, String participantId,
                int roomSlot, long expiryMillis, String nonce) {
            this.protocolVersion = protocolVersion;
            this.buildId = field(buildId, "buildId");
            this.matchId = field(matchId, "matchId");
            this.participantId = field(participantId, "participantId");
            this.roomSlot = roomSlot;
            this.expiryMillis = expiryMillis;
            this.nonce = field(nonce, "nonce");
        }

        private String encode() {
            return protocolVersion + "|" + buildId + "|" + matchId + "|" + participantId + "|"
                    + roomSlot + "|" + expiryMillis + "|" + nonce;
        }

        private static Payload parse(String value) {
            String[] fields = value.split("\\|", -1);
            if (fields.length != 7) {
                return null;
            }
            try {
                return new Payload(Integer.parseInt(fields[0]), fields[1], fields[2], fields[3],
                        Integer.parseInt(fields[4]), Long.parseLong(fields[5]), fields[6]);
            } catch (RuntimeException error) {
                return null;
            }
        }

        private static String field(String value, String name) {
            if (value == null || value.length() == 0 || value.indexOf('|') >= 0 || value.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public String getBuildId() {
            return buildId;
        }

        public String getMatchId() {
            return matchId;
        }

        public String getParticipantId() {
            return participantId;
        }

        public int getRoomSlot() {
            return roomSlot;
        }

        public long getExpiryMillis() {
            return expiryMillis;
        }

        public String getNonce() {
            return nonce;
        }
    }
}
