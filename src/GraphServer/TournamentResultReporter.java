package GraphServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Sends the authoritative room outcome to the tournament service using the shared server HMAC. */
public final class TournamentResultReporter {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private TournamentResultReporter() {
    }

    public static boolean report(String apiBase, String matchId, String winnerId, String loserId,
            String reason, String secret) throws IOException {
        if (apiBase == null || apiBase.trim().length() == 0 || secret == null || secret.length() == 0
                || !valid(matchId) || !valid(winnerId) || !valid(loserId)
                || !("NORMAL".equals(reason) || "FORFEIT".equals(reason))) {
            throw new IllegalArgumentException("Tournament result fields are invalid");
        }
        String nonce = UUID.randomUUID().toString();
        String body = matchId + "|" + winnerId + "|" + loserId + "|" + reason + "|" + nonce;
        String signature = sign(body, secret);
        String json = "{\"winnerParticipantId\":" + quoted(winnerId)
                + ",\"loserParticipantId\":" + quoted(loserId)
                + ",\"reason\":" + quoted(reason)
                + ",\"serverNonce\":" + quoted(nonce)
                + ",\"serverSignature\":" + quoted(signature) + "}";
        String base = apiBase.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        URL url = new URL(base + "/api/v1/matches/" + matchId + "/result");
        IOException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);
                try {
                    OutputStream output = connection.getOutputStream();
                    try {
                        output.write(json.getBytes(UTF8));
                    } finally {
                        output.close();
                    }
                    int status = connection.getResponseCode();
                    if (status >= 200 && status < 300) return true;
                    throw new IOException("Tournament service rejected the room result (HTTP " + status + ").");
                } finally {
                    connection.disconnect();
                }
            } catch (IOException error) {
                lastError = error;
                if (attempt < 2) {
                    try {
                        Thread.sleep(200L * (attempt + 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Tournament result reporting was interrupted.", interrupted);
                    }
                }
            }
        }
        throw lastError == null ? new IOException("Tournament result could not be reported.") : lastError;
    }

    /** Keep the signed outcome live in memory and retry until the API acknowledges it. */
    public static void reportUntilAccepted(String apiBase, String matchId, String winnerId, String loserId,
            String reason, String secret) throws IOException {
        long delayMillis = 1000L;
        while (true) {
            try {
                report(apiBase, matchId, winnerId, loserId, reason, secret);
                return;
            } catch (IOException error) {
                System.err.println("Tournament result delivery failed; retrying: " + error.getMessage());
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Tournament result reporting was interrupted.", interrupted);
                }
                delayMillis = Math.min(delayMillis * 2L, 30000L);
            }
        }
    }

    private static boolean valid(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,100}");
    }

    private static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF8), "HmacSHA256"));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(UTF8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", error);
        }
    }

    private static String quoted(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"' || character == '\\') result.append('\\');
            if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.append('"').toString();
    }
}
