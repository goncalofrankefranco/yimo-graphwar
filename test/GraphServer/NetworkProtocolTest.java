package GraphServer;

import java.util.Properties;

/** Checks the YIMO-only configuration and pre-room compatibility handshake. */
public final class NetworkProtocolTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        String hello = NetworkProtocol.buildHello("A name");
        NetworkProtocol.Hello parsed = NetworkProtocol.parseHello(hello);
        check(parsed != null && "A name".equals(parsed.getPlayerName()),
                "handshake must preserve the encoded player name");
        check(NetworkProtocol.isHandshakeAccepted(NetworkProtocol.handshakeResponse(hello)),
                "the matching build must be accepted");
        check(NetworkProtocol.handshakeResponse("old-client-name").startsWith(NetworkProtocol.VERSION_MISMATCH + "&"),
                "an official pre-handshake client must be rejected");
        check(NetworkProtocol.handshakeResponse("HELLO&1&old-build&Ada").startsWith(NetworkProtocol.VERSION_MISMATCH + "&"),
                "an incompatible build must be rejected");
        check("TOURNAMENT_JOIN&opaque.token".equals(NetworkProtocol.buildTournamentJoin("opaque.token")),
                "tournament join messages must preserve the opaque token");
        check(NetworkProtocol.isTournamentAccepted("TOURNAMENT_ACCEPTED&match-1&p-1&30000"),
                "valid tournament acceptance messages must be recognized");

        Properties properties = new Properties();
        properties.setProperty("global.host", "yimo.example");
        properties.setProperty("global.port", "23762");
        properties.setProperty("room.port.start", "30000");
        properties.setProperty("room.port.end", "30049");
        properties.setProperty("tournament.room.port.start", "31000");
        properties.setProperty("tournament.room.port.end", "31049");
        properties.setProperty("tournament.api.baseUrl", "https://yimo.example/api");
        properties.setProperty("build.id", "YIMO-Graphwar-2.2.0");
        properties.setProperty("protocol.version", "2");
        NetworkConfig config = NetworkConfig.fromProperties(properties);
        check("yimo.example".equals(config.getGlobalHost()) && config.getGlobalPort() == 23762,
                "network configuration must load the YIMO endpoint");

        NetworkConfig defaults = NetworkConfig.defaults();
        check("graphwar-server.yimo-official.org".equals(defaults.getGlobalHost()),
                "the normal game endpoint must use the YIMO game domain");
        check("https://graphwar.yimo-official.org".equals(defaults.getTournamentApiBaseUrl()),
                "the normal tournament endpoint must use the HTTPS YIMO domain");
        check(defaults.getTournamentRoomPortStart() == 31000 && defaults.getTournamentRoomPortEnd() == 31049,
                "tournament ports must use the range reserved apart from practice rooms");
        check(config.getTournamentRoomPortStart() == 31000 && config.getTournamentRoomPortEnd() == 31049,
                "the dedicated tournament port range must be configurable independently");
        check("YIMO-Graphwar-2.2.0".equals(defaults.getBuildId()),
                "the next release must advertise the 2.2.0 build ID by default");

        NetworkConfig overridden = NetworkConfig.fromCommandLine(new String[] {
                "--global-host", "override.example", "--global-port", "23999",
                "--tournament-api", "https://override.example/tournament"
        });
        check("override.example".equals(overridden.getGlobalHost()) && overridden.getGlobalPort() == 23999
                && "https://override.example/tournament".equals(overridden.getTournamentApiBaseUrl()),
                "supported command-line overrides must apply");

        properties.setProperty("global.port", "70000");
        boolean rejected = false;
        try {
            NetworkConfig.fromProperties(properties);
        } catch (java.io.IOException expected) {
            rejected = true;
        }
        check(rejected, "invalid ports must be rejected");
        System.out.println("network-protocol-check: PASS");
    }
}
