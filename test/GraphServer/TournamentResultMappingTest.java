package GraphServer;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;

/** Maps the server's surviving team back to the two signed candidate identities. */
public final class TournamentResultMappingTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String secret = "mapping-secret";
        RoomAccessPolicy policy = RoomAccessPolicy.required(secret, "mapping-match", 31000,
                "candidate-blue", "candidate-red");
        GraphServer server = new GraphServer(0, policy);
        ClientConnection first = client(server, policy, secret, "candidate-blue", "nonce-blue");
        ClientConnection second = client(server, policy, secret, "candidate-red", "nonce-red");
        first.setLeader(true);
        server.clients.add(first);
        server.clients.add(second);
        Player blue = new Player("Blue Candidate");
        blue.setTeam(Constants.TEAM1);
        blue.setNumSoldiers(1);
        Player red = new Player("Red Candidate");
        red.setTeam(Constants.TEAM2);
        red.setNumSoldiers(1);
        first.addPlayer(blue);
        second.addPlayer(red);
        server.players.add(blue);
        server.players.add(red);

        AuthoritativeGame game = new AuthoritativeGame();
        game.start(new MapShape[0], new int[] {100, 225, 600, 225}, server.players, 0,
                Constants.NORMAL_FUNC, Constants.SHOOTER_RELATIVE_TRAJECTORY);
        game.acceptShot(blue.getID(), "0");
        Field field = GraphServer.class.getDeclaredField("authoritativeGame");
        field.setAccessible(true);
        field.set(server, game);

        String[] result = server.tournamentResult();
        check(result != null && "mapping-match".equals(result[0]), "match identity must come from the room policy");
        check("candidate-blue".equals(result[1]) && "candidate-red".equals(result[2]),
                "winner and loser must be derived from server-owned team elimination");
        Field state = GraphServer.class.getDeclaredField("gameState");
        state.setAccessible(true);
        state.setInt(server, Constants.GAME);
        String[] forfeit = server.tournamentForfeitResult(first);
        check(forfeit != null && "candidate-red".equals(forfeit[1]) && "candidate-blue".equals(forfeit[2]),
                "a disconnect during a live match must award the remaining candidate a forfeit win");
        first.disconnect();
        second.disconnect();
        server.finalize();
        System.out.println("tournament-result-mapping-check: PASS");
    }

    private static ClientConnection client(GraphServer server, RoomAccessPolicy policy, String secret,
            String participantId, String nonce) throws Exception {
        long expiry = System.currentTimeMillis() + 60_000L;
        String token = RoomAccessToken.issue(new RoomAccessToken.Payload(Constants.PROTOCOL_VERSION,
                Constants.BUILD_ID, "mapping-match", participantId, "Official " + participantId, 31000, expiry, nonce), secret);
        ServerSocket pair = new ServerSocket(0);
        Socket client = new Socket("127.0.0.1", pair.getLocalPort());
        Socket serverSocket = pair.accept();
        pair.close();
        PrintWriter output = new PrintWriter(client.getOutputStream(), true);
        output.println(NetworkProtocol.buildHello(participantId));
        output.println(NetworkProtocol.TOURNAMENT_JOIN + "&" + token);
        ClientConnection connection = new ClientConnection(server, serverSocket, policy);
        client.close();
        return connection;
    }
}
