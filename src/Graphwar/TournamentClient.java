package Graphwar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;

/** Small Java 8 HTTP bridge used only when joining a reserved tournament room. */
public final class TournamentClient {
    private TournamentClient() {
    }

    public static final class JoinDetails {
        private final int port;
        private final String roomToken;
        private final String displayName;

        private JoinDetails(int port, String roomToken, String displayName) {
            this.port = port;
            this.roomToken = roomToken;
            this.displayName = displayName;
        }

        public int getPort() {
            return port;
        }

        public String getRoomToken() {
            return roomToken;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static JoinDetails join(String apiBase, String candidateCode, String displayName, int roomPort,
            String buildId, int protocolVersion) throws IOException {
        if (candidateCode == null || candidateCode.trim().length() == 0
                || displayName == null || displayName.trim().length() == 0) {
            throw new IOException("Candidate code and display name are required.");
        }
        if (roomPort < 1 || roomPort > 65535) {
            throw new IOException("Tournament room port is invalid.");
        }
        URI base;
        try {
            base = new URI(apiBase);
        } catch (Exception error) {
            throw new IOException("Tournament service URL is invalid.", error);
        }
        if (base.getHost() == null || !("http".equalsIgnoreCase(base.getScheme())
                || "https".equalsIgnoreCase(base.getScheme()))) {
            throw new IOException("Tournament service URL must use HTTP or HTTPS.");
        }
        String baseUrl = apiBase;
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        URL url = new URL(baseUrl + "/api/v1/game/join");
        String body = "participantCode=" + encode(candidateCode.trim())
                + "&displayName=" + encode(displayName.trim())
                + "&roomPort=" + roomPort
                + "&buildId=" + encode(buildId)
                + "&protocolVersion=" + protocolVersion;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setDoOutput(true);
        try {
            OutputStream output = connection.getOutputStream();
            try {
                output.write(body.getBytes("UTF-8"));
            } finally {
                output.close();
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = read(input);
            if (status < 200 || status >= 300) {
                throw new IOException(errorMessage(response));
            }
            String[] fields = response.split("&", -1);
            if (fields.length != 4 || !"YIMO_ROOM".equals(fields[0])) {
                throw new IOException("Tournament service returned an invalid room assignment.");
            }
            int assignedPort;
            try {
                assignedPort = Integer.parseInt(fields[1]);
            } catch (NumberFormatException error) {
                throw new IOException("Tournament service returned an invalid room port.", error);
            }
            if (assignedPort != roomPort) {
                throw new IOException("This match is assigned to port " + assignedPort + ".");
            }
            if (fields[2].length() == 0) {
                throw new IOException("Tournament service returned an empty room token.");
            }
            String officialName;
            try {
                officialName = new String(java.util.Base64.getUrlDecoder().decode(fields[3]), "UTF-8");
            } catch (IllegalArgumentException error) {
                throw new IOException("Tournament service returned an invalid display name.", error);
            }
            if (officialName.trim().length() == 0) {
                throw new IOException("Tournament service returned an empty display name.");
            }
            return new JoinDetails(assignedPort, fields[2], officialName);
        } finally {
            connection.disconnect();
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder text = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) text.append(line);
        } finally {
            reader.close();
        }
        return text.toString();
    }

    private static String errorMessage(String response) {
        String[] fields = response.split("&", 3);
        if (fields.length == 3 && "ERROR".equals(fields[0])) {
            try {
                return java.net.URLDecoder.decode(fields[2], "UTF-8");
            } catch (IOException ignored) {
                return "Tournament match authorization failed.";
            }
        }
        return "Tournament match authorization failed.";
    }
}
