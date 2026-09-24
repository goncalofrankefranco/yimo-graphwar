package Graphwar;

import java.util.UUID;
import java.util.prefs.Preferences;

public final class PlayerIdentityTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Preferences preferences = Preferences.userRoot().node("yimo-player-test-" + UUID.randomUUID());
        try {
            String first = PlayerIdentity.loadOrCreate(preferences);
            String second = PlayerIdentity.loadOrCreate(preferences);
            check(first.equals(second), "player ID must persist");
            check(PlayerIdentity.isValid(first), "player ID format");
            check(first.startsWith("yimo-"), "player ID prefix");
            System.out.println("player-identity-check: PASS");
        } finally {
            preferences.removeNode();
        }
    }
}
