package GraphServer;

/** Verifies signed token integrity, expiry, and one-time nonce use. */
public final class RoomAccessTokenTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        long now = 1_700_000_000_000L;
        RoomAccessToken.Payload payload = new RoomAccessToken.Payload(2, Constants.BUILD_ID,
                "match-1", "participant-1", "Official Candidate", 30000, now + 60_000L, "nonce-1");
        String token = RoomAccessToken.issue(payload, "test-secret");
        RoomAccessToken.Payload verified = RoomAccessToken.verify(token, "test-secret", now);
        check(verified != null && "match-1".equals(verified.getMatchId()), "valid room token must verify");
        check("Official Candidate".equals(verified.getDisplayName()), "signed room token must carry its canonical display name");
        check(RoomAccessToken.verify(token, "wrong-secret", now) == null, "wrong secret must fail");
        check(RoomAccessToken.verify(token, "test-secret", now + 60_001L) == null, "expired token must fail");

        RoomAccessPolicy policy = RoomAccessPolicy.required("test-secret", "match-1", 30000,
                "participant-1", "participant-2");
        check(policy.accept(token, now) != null, "first token use must be accepted");
        check(policy.accept(token, now) == null, "replayed token nonce must be rejected");
        String duplicateParticipantToken = RoomAccessToken.issue(new RoomAccessToken.Payload(2, Constants.BUILD_ID,
                "match-1", "participant-1", "Official Candidate", 30000, now + 60_000L, "nonce-2"), "test-secret");
        check(policy.accept(duplicateParticipantToken, now) == null,
                "a second concurrent token for the same candidate must be rejected");

        String nodeIssuedToken = "MnxZSU1PLUdyYXBod2FyLTIuMC4wfGNyb3NzLW1hdGNofGNyb3NzLXBhcnRpY2lwYW50fENyb3NzIENhbmRpZGF0ZXwzMDAwMHwxNzAwMDAwMDYwMDAwfGNyb3NzLW5vbmNl.tMfUjR1qRGRfrWg6o4CSuLwe_2ZWNS5wCfRFfF6jJMU";
        RoomAccessToken.Payload nodePayload = RoomAccessToken.verify(nodeIssuedToken, "cross-secret", now);
        check(nodePayload != null && "cross-match".equals(nodePayload.getMatchId())
                && "cross-participant".equals(nodePayload.getParticipantId())
                && "Cross Candidate".equals(nodePayload.getDisplayName()),
                "Java must verify the Node tournament token format");
        System.out.println("room-access-token-check: PASS");
    }
}
