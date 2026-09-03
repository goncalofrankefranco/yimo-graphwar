package GraphServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/** Verifies the required-token room gate is checked before room state is exposed. */
public final class TournamentRoomAccessTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        long expiry = System.currentTimeMillis() + 60_000L;
        String secret = "test-secret";
        RoomAccessPolicy policy = RoomAccessPolicy.required(secret, "match-1", 30000);
        RoomAccessToken.Payload payload = new RoomAccessToken.Payload(Constants.PROTOCOL_VERSION, Constants.BUILD_ID,
                "match-1", "participant-1", 30000, expiry, "nonce-access");
        String token = RoomAccessToken.issue(payload, secret);
        GraphServer server = new GraphServer(0, policy);
        ServerSocket pair = new ServerSocket(0);
        Socket clientSocket = new Socket("127.0.0.1", pair.getLocalPort());
        Socket serverSocket = pair.accept();
        pair.close();
        PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
        output.println(NetworkProtocol.buildHello("participant-1"));
        output.println(NetworkProtocol.TOURNAMENT_JOIN + "&" + token);
        ClientConnection client = new ClientConnection(server, serverSocket, policy);
        BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        check(NetworkProtocol.isHandshakeAccepted(input.readLine()), "matching client build must be accepted");
        String access = input.readLine();
        check(access != null && access.startsWith(NetworkProtocol.TOURNAMENT_ACCEPTED + "&"),
                "valid tournament token must be accepted");
        client.disconnect();
        clientSocket.close();
        server.finalize();
        System.out.println("tournament-room-access-check: PASS");
    }
}
