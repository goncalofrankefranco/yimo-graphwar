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
        RoomAccessPolicy policy = RoomAccessPolicy.required("settings-secret", matchId, port,
                "candidate-1", "candidate-2");
        GraphServer server = new GraphServer(0, policy);
        ServerSocket pair = new ServerSocket(0);
        Socket clientSocket = new Socket("127.0.0.1", pair.getLocalPort());
        Socket serverSocket = pair.accept();
        pair.close();
        PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
        output.println(NetworkProtocol.buildHello("candidate"));
        output.println(NetworkProtocol.TOURNAMENT_JOIN + "&" + token);
        ClientConnection client = new ClientConnection(server, serverSocket, policy);
        check("candidate-1".equals(client.getTournamentParticipantId()),
                "room access must bind the candidate code's server-issued participant identity");
        client.setLeader(true);
        server.clients.add(client);
        server.handleMessage(NetworkProtocol.ADD_PLAYER + "&spoofed-name", client);
        Player candidate = server.players.get(0);
        check("Official Candidate".equals(candidate.getName()), "room must replace a client-supplied name with the signed canonical name");

        String secondToken = RoomAccessToken.issue(new RoomAccessToken.Payload(Constants.PROTOCOL_VERSION,
                Constants.BUILD_ID, matchId, "candidate-2", "Second Candidate", port, expiry, "settings-nonce-2"), "settings-secret");
        ServerSocket secondPair = new ServerSocket(0);
        Socket secondClientSocket = new Socket("127.0.0.1", secondPair.getLocalPort());
        Socket secondServerSocket = secondPair.accept();
        secondPair.close();
        PrintWriter secondOutput = new PrintWriter(secondClientSocket.getOutputStream(), true);
        secondOutput.println(NetworkProtocol.buildHello("spoofed-second-name"));
        secondOutput.println(NetworkProtocol.TOURNAMENT_JOIN + "&" + secondToken);
        ClientConnection secondClient = new ClientConnection(server, secondServerSocket, policy);
        check("candidate-2".equals(secondClient.getTournamentParticipantId()),
                "the opponent's room token must bind its own assigned participant identity");
        secondClient.setLeader(false);
        server.clients.add(secondClient);
        server.handleMessage(NetworkProtocol.ADD_PLAYER + "&spoofed-second-name", secondClient);
        Player secondCandidate = server.players.get(1);
        check("Second Candidate".equals(secondCandidate.getName()), "second room player must use its signed name");

        for (ClientConnection roomClient : Arrays.asList(client, secondClient)) {
            Player roomPlayer = roomClient == client ? candidate : secondCandidate;
            server.handleMessage(NetworkProtocol.NEXT_MODE + "", roomClient);
            server.handleMessage(NetworkProtocol.SET_MODE + "&" + Constants.FST_ODE, roomClient);
            server.handleMessage(NetworkProtocol.SET_PREVIEW + "&0", roomClient);
            server.handleMessage(NetworkProtocol.SET_TURN_TIME + "&20000", roomClient);
            server.handleMessage(NetworkProtocol.SET_TRAJECTORY_MODE + "&" + Constants.GLOBAL_TRAJECTORY, roomClient);
            server.handleMessage(NetworkProtocol.SET_MAP + "&1&0", roomClient);
            server.handleMessage(NetworkProtocol.SET_TEAM + "&" + Constants.TEAM2 + "&" + roomPlayer.getID(), roomClient);
            server.handleMessage(NetworkProtocol.REMOVE_PLAYER + "&" + roomPlayer.getID(), roomClient);
            server.handleMessage(NetworkProtocol.ADD_SOLDIER + "&" + roomPlayer.getID(), roomClient);
            server.handleMessage(NetworkProtocol.REMOVE_SOLDIER + "&" + roomPlayer.getID(), roomClient);
            server.handleMessage(NetworkProtocol.ADD_PLAYER + "&extra-player", roomClient);
        }

        check(server.gameMode == Constants.NORMAL_FUNC, "tournament game mode must stay fixed");
        check(server.previewEnabled, "tournament aim preview must stay enabled");
        check(server.turnTime == Constants.DEFAULT_TURN_TIME, "tournament turn duration must stay fixed");
        check(server.trajectoryMode == Constants.SHOOTER_RELATIVE_TRAJECTORY, "tournament trajectory must stay fixed");
        check(!server.customMapEnabled, "tournament maps must not be player-editable");
        check(candidate.getTeam() == Constants.TEAM1 && candidate.getNumSoldiers() == 1
                        && secondCandidate.getTeam() == Constants.TEAM2 && secondCandidate.getNumSoldiers() == 1,
                "both candidates' teams and soldier counts must stay fixed");
        check(server.players.size() == 2 && client.getPlayers().size() == 1 && secondClient.getPlayers().size() == 1,
                "tournament room must contain one player per candidate");
        client.disconnect();
        secondClient.disconnect();
        clientSocket.close();
        secondClientSocket.close();
        server.finalize();
        System.out.println("tournament-room-settings-check: PASS");
    }
}
