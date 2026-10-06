package GraphServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Checks the room-server callback payload and its cross-language HMAC signature. */
public final class TournamentResultReporterTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        final String[] requestPath = new String[1];
        final String[] requestBody = new String[1];
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/matches/match-42/result", (HttpExchange exchange) -> {
            requestPath[0] = exchange.getRequestURI().getPath();
            requestBody[0] = read(exchange.getRequestBody());
            byte[] response = "{}".getBytes("UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            boolean reported = TournamentResultReporter.report(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "match-42", "winner-1", "loser-2", "NORMAL", "test-room-secret");
            check(reported, "room result must be reported successfully");
            check("/api/v1/matches/match-42/result".equals(requestPath[0]), "match result endpoint");
            String nonce = field("serverNonce", requestBody[0]);
            String signature = field("serverSignature", requestBody[0]);
            String expected = sign("match-42|winner-1|loser-2|NORMAL|" + nonce, "test-room-secret");
            check(expected.equals(signature), "result must carry the room-server HMAC, not a player token");
            check(requestBody[0].contains("\"winnerParticipantId\":\"winner-1\""), "winner identity");
            check(requestBody[0].contains("\"loserParticipantId\":\"loser-2\""), "loser identity");
        } finally {
            server.stop(0);
        }

        final AtomicInteger attempts = new AtomicInteger();
        HttpServer retryServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        retryServer.createContext("/api/v1/matches/match-retry/result", (HttpExchange exchange) -> {
            int attempt = attempts.incrementAndGet();
            exchange.sendResponseHeaders(attempt <= 3 ? 503 : 200, -1);
            exchange.close();
        });
        retryServer.start();
        try {
            TournamentResultReporter.reportUntilAccepted(
                    "http://127.0.0.1:" + retryServer.getAddress().getPort(),
                    "match-retry", "winner-1", "loser-2", "NORMAL", "test-room-secret");
            check(attempts.get() == 4, "result reporter must keep retrying after transient service errors");
        } finally {
            retryServer.stop(0);
        }
        System.out.println("tournament-result-reporter-check: PASS");
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int size;
        while ((size = input.read(buffer)) >= 0) output.write(buffer, 0, size);
        return new String(output.toByteArray(), UTF8);
    }

    private static String field(String name, String body) {
        Matcher matcher = Pattern.compile("\\\"" + name + "\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        if (!matcher.find()) throw new AssertionError("missing JSON field " + name + " in " + body);
        return matcher.group(1);
    }

    private static String sign(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(UTF8), "HmacSHA256"));
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(UTF8)));
    }
}
