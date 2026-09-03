package GraphServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/** Verifies that a room server rejects pre-YIMO clients before room state is sent. */
public final class ServerHandshakeTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String exchange(String firstMessage, boolean accepted) throws Exception {
        GraphServer server = new GraphServer();
        ServerSocket pair = new ServerSocket(0);
        Socket clientSocket = new Socket("127.0.0.1", pair.getLocalPort());
        Socket serverSocket = pair.accept();
        pair.close();
        PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
        output.println(firstMessage);
        ClientConnection client = null;
        try {
            client = new ClientConnection(server, serverSocket);
        } catch (java.io.IOException expected) {
            check(!accepted, "only an incompatible handshake may be rejected");
        }
        BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String response = input.readLine();
        if (client != null) {
            client.disconnect();
        }
        clientSocket.close();
        server.finalize();
        return response;
    }

    public static void main(String[] args) throws Exception {
        String accepted = exchange(NetworkProtocol.buildHello("smoke"), true);
        check(NetworkProtocol.isHandshakeAccepted(accepted), "matching YIMO client must be accepted");
        String rejected = exchange("old-client-name", false);
        check(rejected != null && rejected.startsWith(NetworkProtocol.VERSION_MISMATCH + "&"),
                "old client must receive a version mismatch before disconnect");
        System.out.println("server-handshake-check: PASS");
    }
}
