package Graphwar;

public final class GlobalTournamentLinkTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(GlobalScreen.tournamentPortalUrl("http://172.86.74.172").equals(
                "http://172.86.74.172/participant"), "portal URL");
        check(GlobalScreen.tournamentPortalUrl("http://host/").equals(
                "http://host/participant"), "trailing slash");
        check(GlobalScreen.menuLabels().contains("Tournament"), "lobby label");
        boolean rejected = false;
        try {
            GlobalScreen.tournamentPortalUrl(" ");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "blank URL must be rejected");
        System.out.println("global-tournament-link-check: PASS");
    }
}
