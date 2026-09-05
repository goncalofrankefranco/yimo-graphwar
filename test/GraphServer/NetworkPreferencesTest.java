package GraphServer;

import java.util.prefs.Preferences;

/** Verifies that client endpoint settings persist without changing server defaults. */
public final class NetworkPreferencesTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        Preferences node = Preferences.userRoot().node("yimo-test-" + System.nanoTime());
        try {
            NetworkConfig base = NetworkConfig.defaults();
            NetworkConfig saved = NetworkPreferences.fromFields(base, "yimo.example", "23999",
                    "https://yimo.example/tournament");
            NetworkPreferences.save(node, saved);

            NetworkConfig loaded = NetworkPreferences.load(node, base);
            check("yimo.example".equals(loaded.getGlobalHost()), "saved host must load");
            check(loaded.getGlobalPort() == 23999, "saved port must load");
            check("https://yimo.example/tournament".equals(loaded.getTournamentApiBaseUrl()),
                    "saved API URL must load");
            check(base.getGlobalPort() == NetworkConfig.DEFAULT_GLOBAL_PORT,
                    "server defaults must remain unchanged");

            boolean rejected = false;
            try {
                NetworkPreferences.fromFields(base, "bad host", "23762", "http://yimo.example");
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "invalid settings must be rejected");
            check(!NetworkPreferences.hasExplicitArguments(new String[] { "--config", "yimo.properties" }),
                    "the packaged config must remain a base for saved app settings");
            check(NetworkPreferences.hasExplicitArguments(new String[] { "--global-port", "23999" }),
                    "explicit command-line endpoint overrides must win over saved settings");

            NetworkPreferences.clear(node);
            check(NetworkPreferences.load(node, base).getGlobalHost().equals(base.getGlobalHost()),
                    "clearing settings must restore the base configuration");
        } finally {
            node.removeNode();
        }
        System.out.println("network-preferences-check: PASS");
    }
}
