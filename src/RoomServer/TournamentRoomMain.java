package RoomServer;

import GraphServer.Constants;
import GraphServer.RoomAccessPolicy;

/** One hidden, signed, single-match room process started by the tournament service. */
public final class TournamentRoomMain {
    private TournamentRoomMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = -1;
        String matchId = null;
        String participantA = null;
        String participantB = null;
        for (int i = 0; args != null && i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else if ("--match-id".equals(args[i]) && i + 1 < args.length) matchId = args[++i];
            else if ("--participant-a".equals(args[i]) && i + 1 < args.length) participantA = args[++i];
            else if ("--participant-b".equals(args[i]) && i + 1 < args.length) participantB = args[++i];
            else throw new IllegalArgumentException("Unknown or incomplete tournament room argument: " + args[i]);
        }
        String secret = System.getenv("YIMO_ROOM_HMAC_SECRET");
        if (port < Constants.TOURNAMENT_ROOM_PORT_START || port > Constants.TOURNAMENT_ROOM_PORT_END
                || matchId == null || !matchId.matches("[A-Za-z0-9_-]{1,100}")
                || participantA == null || participantB == null || participantA.equals(participantB)
                || secret == null || secret.length() == 0) {
            throw new IllegalArgumentException("Tournament room configuration is incomplete or invalid.");
        }
        RemoteGraphServer server = new RemoteGraphServer(null, port,
                RoomAccessPolicy.required(secret, matchId, port, participantA, participantB));
        Thread thread = new Thread(server, "yimo-tournament-room-" + port);
        thread.start();
        System.out.println("YIMO_TOURNAMENT_ROOM_READY|" + port + "|" + matchId);
    }
}
