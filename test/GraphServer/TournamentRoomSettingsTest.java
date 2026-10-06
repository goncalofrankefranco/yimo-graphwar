package GraphServer;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

/** Tournament rooms must ignore player-controlled setup and roster mutations. */
public final class TournamentRoomSettingsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String matchId = "settings-match";
        int port = 31000;
        long expiry = System.currentTimeMillis() + 60_000L;
        String token = RoomAccessToken.issue(new RoomAccessToken.Payload(Constants.PROTOCOL_VERSION,
                Constants.BUILD_ID, matchId, "candidate-1", "Official Candidate", port, expiry, "settings-nonce"), "settings-secret");
        RoomAccessPolicy policy = RoomAccessPolicy.required("settings-secret", matchId, port);
        GraphServer server = new GraphServer(0, policy);
        ServerSocket pair = new ServerSocket(0);
        Socket clientSocket = new Socket("127.0.0.1", pair.getLocalPort());
        Socket serverSocket = pair.accept();
        pair.close();
        PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
        output.println(NetworkProtocol.buildHello("candidate"));
        output.println(NetworkProtocol.TOURNAMENT_JOIN + "&" + token);
        ClientConnection client = new ClientConnection(server, serverSocket, policy);
        client.setLeader(true);
        server.clients.add(client);
        server.handleMessage(NetworkProtocol.ADD_PLAYER + "&spoofed-name", client);
        Player candidate = server.players.get(0);
        check("Official Candidate".equals(candidate.getName()), "room must replace a client-supplied name with the signed canonical name");

        server.handleMessage(NetworkProtocol.NEXT_MODE + "", client);
        server.handleMessage(NetworkProtocol.SET_MODE + "&" + Constants.FST_ODE, client);
        server.handleMessage(NetworkProtocol.SET_PREVIEW + "&0", client);
        server.handleMessage(NetworkProtocol.SET_TURN_TIME + "&20000", client);
        server.handleMessage(NetworkProtocol.SET_TRAJECTORY_MODE + "&" + Constants.GLOBAL_TRAJECTORY, client);
        server.handleMessage(NetworkProtocol.SET_MAP + "&1&0", client);
        server.handleMessage(NetworkProtocol.SET_TEAM + "&" + Constants.TEAM2 + "&" + candidate.getID(), client);
        server.handleMessage(NetworkProtocol.REMOVE_PLAYER + "&" + candidate.getID(), client);
        server.handleMessage(NetworkProtocol.ADD_SOLDIER + "&" + candidate.getID(), client);
        server.handleMessage(NetworkProtocol.REMOVE_SOLDIER + "&" + candidate.getID(), client);
        server.handleMessage(NetworkProtocol.ADD_PLAYER + "&extra-player", client);

        check(server.gameMode == Constants.NORMAL_FUNC, "tournament game mode must stay fixed");
        check(server.previewEnabled, "tournament aim preview must stay enabled");
        check(server.turnTime == Constants.DEFAULT_TURN_TIME, "tournament turn duration must stay fixed");
        check(server.trajectoryMode == Constants.SHOOTER_RELATIVE_TRAJECTORY, "tournament trajectory must stay fixed");
        check(!server.customMapEnabled, "tournament maps must not be player-editable");
        check(candidate.getTeam() == Constants.TEAM1 && candidate.getNumSoldiers() == 1,
                "candidate team and soldier count must stay fixed");
        check(server.players.size() == 1 && client.getPlayers().size() == 1,
                "tournament room must contain one player per candidate");
        client.disconnect();
        clientSocket.close();
        server.finalize();
        System.out.println("tournament-room-settings-check: PASS");
    }
}
