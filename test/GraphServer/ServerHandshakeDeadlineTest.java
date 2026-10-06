package GraphServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps slow client handshakes from blocking room admission or living indefinitely. */
public final class ServerHandshakeDeadlineTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int freePort() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    public static void main(String[] args) throws Exception {
        concurrentValidClientIsNotBlockedByPartialHello();
        slowDripHandshakeHasAnAbsoluteDeadline();
        System.out.println("server-handshake-deadline-check: PASS");
    }

    private static void concurrentValidClientIsNotBlockedByPartialHello() throws Exception {
        GraphServer server = new GraphServer(freePort());
        Thread acceptLoop = new Thread(server, "handshake-test-server");
        acceptLoop.start();
        Socket slow = new Socket("127.0.0.1", server.getPort());
        PrintWriter slowOutput = new PrintWriter(slow.getOutputStream(), true);
        slowOutput.print("HELLO&partial");
        slowOutput.flush();

        Socket valid = new Socket("127.0.0.1", server.getPort());
        valid.setSoTimeout(2000);
        PrintWriter validOutput = new PrintWriter(valid.getOutputStream(), true);
        validOutput.println(NetworkProtocol.buildHello("valid-client"));
        BufferedReader validInput = new BufferedReader(new InputStreamReader(valid.getInputStream()));
        check(NetworkProtocol.isHandshakeAccepted(validInput.readLine()),
                "a partial hello must not block a concurrent valid connection");

        valid.close();
        slow.close();
        server.finalize();
        acceptLoop.join(2000);
        check(!acceptLoop.isAlive(), "the accept loop must stop cleanly");
    }

    private static void slowDripHandshakeHasAnAbsoluteDeadline() throws Exception {
        GraphServer server = new GraphServer(0);
        ServerSocket pair = new ServerSocket(0);
        Socket clientSocket = new Socket("127.0.0.1", pair.getLocalPort());
        Socket serverSocket = pair.accept();
        pair.close();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        Thread handshake = new Thread(() -> {
            try {
                new ClientConnection(server, serverSocket, RoomAccessPolicy.open(), 500L);
            } catch (Exception expected) {
                timedOut.set(true);
            }
        }, "slow-handshake-test");
        handshake.start();
        PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
        for (int i = 0; i < 20 && handshake.isAlive(); i++) {
            output.print("x");
            output.flush();
            Thread.sleep(100L);
        }
        handshake.join(1500L);
        check(!handshake.isAlive() && timedOut.get(),
                "a client that drips bytes must still be closed at the total handshake deadline");
        clientSocket.close();
        server.finalize();
    }
}
