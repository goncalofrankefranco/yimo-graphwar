package GraphServer;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/** Tournament player readers must keep the room JVM alive after its accept socket closes at game start. */
public final class TournamentRoomThreadLifecycleTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Socket connectCandidate(GraphServer server, String participantId, String name,
            String secret, int roomSlot, String nonce) throws Exception {
        Socket socket = new Socket("127.0.0.1", server.getPort());
        socket.setSoTimeout(5000);
        BufferedReader input = new BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "UTF-8"));
        PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
        output.println(NetworkProtocol.buildHello(name));
        check(NetworkProtocol.isHandshakeAccepted(input.readLine()), "room must accept the supported client build");
        String token = RoomAccessToken.issue(new RoomAccessToken.Payload(Constants.PROTOCOL_VERSION,
                Constants.BUILD_ID, "lifecycle-match", participantId, name, roomSlot,
                System.currentTimeMillis() + 60_000L, nonce), secret);
        output.println(NetworkProtocol.buildTournamentJoin(token));
        check(NetworkProtocol.isTournamentAccepted(input.readLine()), "room must accept its assigned candidate");
        output.println(NetworkProtocol.ADD_PLAYER + "&" + name);
        return socket;
    }

    private static List<Thread> clientReaderThreads() {
        List<Thread> readers = new ArrayList<Thread>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            for (StackTraceElement frame : thread.getStackTrace()) {
                if ("GraphServer.ClientConnection".equals(frame.getClassName())
                        && "run".equals(frame.getMethodName())) {
                    readers.add(thread);
                    break;
                }
            }
        }
        return readers;
    }

    private static void waitForPlayers(GraphServer server, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (server.players.size() != expected && System.currentTimeMillis() < deadline) Thread.sleep(10L);
        check(server.players.size() == expected, "both tournament candidates must join before the game starts");
    }

    private static int freePort() throws Exception {
        ServerSocket reservation = new ServerSocket(0);
        try {
            return reservation.getLocalPort();
        } finally {
            reservation.close();
        }
    }

    public static void main(String[] args) throws Exception {
        String secret = "room-thread-lifecycle-secret";
        int roomSlot = freePort();
        GraphServer server = new GraphServer(roomSlot, RoomAccessPolicy.required(secret, "lifecycle-match", roomSlot,
                "candidate-a", "candidate-b"));
        Thread acceptor = new Thread(server, "test-room-acceptor");
        Socket first = null;
        Socket second = null;
        acceptor.start();
        try {
            first = connectCandidate(server, "candidate-a", "Candidate A", secret, roomSlot, "nonce-a");
            second = connectCandidate(server, "candidate-b", "Candidate B", secret, roomSlot, "nonce-b");
            waitForPlayers(server, 2);

            server.startGame();
            acceptor.join(3000L);
            check(!acceptor.isAlive(), "starting the game must close the room accept loop");

            List<Thread> readers = clientReaderThreads();
            check(readers.size() == 2, "both player connections must remain active after the game starts");
            for (Thread reader : readers) {
                check(!reader.isDaemon(), "player connection readers must keep the tournament room JVM alive");
            }
        } finally {
            server.finalize();
            if (first != null) first.close();
            if (second != null) second.close();
            acceptor.join(3000L);
        }
        System.out.println("tournament-room-thread-lifecycle-check: PASS");
    }
}
