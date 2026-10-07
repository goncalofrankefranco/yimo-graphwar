package GraphServer;

import java.lang.reflect.Method;

/** The Java room must reject signed tokens for candidates outside its assigned match. */
public final class TournamentRoomParticipantBindingTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static RoomAccessPolicy assignedRoom(String secret, String matchId, int port,
            String participantA, String participantB) {
        try {
            Method factory = RoomAccessPolicy.class.getDeclaredMethod("required", String.class,
                    String.class, int.class, String.class, String.class);
            return (RoomAccessPolicy) factory.invoke(null, secret, matchId, Integer.valueOf(port),
                    participantA, participantB);
        } catch (Exception error) {
            throw new AssertionError("tournament rooms must receive their assigned participant IDs", error);
        }
    }

    public static void main(String[] args) {
        String secret = "assigned-room-secret";
        String matchId = "assigned-match";
        int roomPort = 31002;
        long expiry = System.currentTimeMillis() + 60_000L;
        RoomAccessPolicy policy = assignedRoom(secret, matchId, roomPort, "candidate-a", "candidate-b");

        RoomAccessToken.Payload valid = new RoomAccessToken.Payload(Constants.PROTOCOL_VERSION,
                Constants.BUILD_ID, matchId, "candidate-a", "Candidate A", roomPort, expiry, "nonce-valid");
        RoomAccessToken.Payload unassigned = new RoomAccessToken.Payload(Constants.PROTOCOL_VERSION,
                Constants.BUILD_ID, matchId, "candidate-c", "Candidate C", roomPort, expiry, "nonce-unassigned");
        check(policy.accept(RoomAccessToken.issue(unassigned, secret), System.currentTimeMillis()) == null,
                "a correctly signed but unassigned candidate must be rejected by the room server");
        check("candidate-a".equals(policy.accept(RoomAccessToken.issue(valid, secret),
                System.currentTimeMillis()).getParticipantId()),
                "an assigned candidate must be accepted with its signed participant ID");
        System.out.println("tournament-room-participant-binding-check: PASS");
    }
}
