package GraphServer;

/** Ensures room servers bind inside the firewall's configured YIMO port range. */
public final class ConfiguredRoomPortTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        GraphServer server = new GraphServer();
        try {
            check(server.getPort() >= Constants.ROOM_PORT_START
                    && server.getPort() <= Constants.ROOM_PORT_END,
                    "room server must bind within the configured YIMO port range");
        } finally {
            server.finalize();
        }
        System.out.println("configured-room-port-check: PASS");
    }
}
