package GraphServer;

import java.net.ServerSocket;
import java.net.Socket;

import Graphwar.GameData;
import Graphwar.ServerConnection;

/** Verifies that the Java client sends a tournament token after the YIMO handshake. */
public final class TournamentClientAccessTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        final ServerSocket listener = new ServerSocket(0);
        final String[] messages = new String[2];
        final Throwable[] failure = new Throwable[1];
        Thread server = new Thread(new Runnable() {
            public void run() {
                try {
                    Socket socket = listener.accept();
                    Connection connection = new Connection(socket);
                    messages[0] = connection.readMessage();
                    connection.sendMessage(NetworkProtocol.HANDSHAKE_ACCEPTED + "&"
                            + Constants.PROTOCOL_VERSION + "&" + Constants.BUILD_ID);
                    messages[1] = connection.readMessage();
                    connection.sendMessage(NetworkProtocol.TOURNAMENT_ACCEPTED + "&match-1&participant-1&30000");
                    connection.close();
                } catch (Throwable error) {
                    failure[0] = error;
                }
            }
        }, "tournament-client-access-test-server");
        server.setDaemon(true);
        server.start();

        ServerConnection client = new ServerConnection(new GameData(null), "127.0.0.1", listener.getLocalPort(),
                "participant-1", "opaque.token");
        client.disconnect();
        server.join(1000L);
        listener.close();

        check(failure[0] == null, "test server must complete without an error");
        check(NetworkProtocol.parseHello(messages[0]) != null, "client must send the YIMO hello");
        check(NetworkProtocol.buildTournamentJoin("opaque.token").equals(messages[1]),
                "client must send the opaque tournament token");
        System.out.println("tournament-client-access-check: PASS");
    }
}
