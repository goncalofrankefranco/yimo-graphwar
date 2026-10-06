package Graphwar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Verifies the desktop's fixed-host tournament join request and opaque-token parsing. */
public final class TournamentClientTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        final String[] requestBody = new String[1];
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/game/join", (HttpExchange exchange) -> {
            requestBody[0] = read(exchange.getRequestBody());
            byte[] response = ("YIMO_ROOM&31000&opaque.room-token&"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString("Official Name".getBytes("UTF-8")))
                    .getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            TournamentClient.JoinDetails result = TournamentClient.join(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "CANDIDATE CODE", "Ada Example", 31000, "YIMO-Graphwar-2.2.0", 2);
            check(result.getPort() == 31000, "the service-assigned port must be returned to the client");
            check("opaque.room-token".equals(result.getRoomToken()), "the signed room token must stay opaque");
            check("Official Name".equals(result.getDisplayName()), "the game must use the bracket's canonical name");
            Map<String, String> fields = new HashMap<String, String>();
            for (String pair : requestBody[0].split("&")) {
                String[] item = pair.split("=", 2);
                fields.put(URLDecoder.decode(item[0], "UTF-8"), URLDecoder.decode(item[1], "UTF-8"));
            }
            check("CANDIDATE CODE".equals(fields.get("participantCode")), "candidate codes must be form-encoded");
            check("31000".equals(fields.get("roomPort")), "the entered tournament port must be sent for validation");
            check("Ada Example".equals(fields.get("displayName")), "the entered display name must be sent");
        } finally {
            server.stop(0);
        }
        System.out.println("tournament-client-check: PASS");
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return new String(output.toByteArray(), "UTF-8");
    }
}
