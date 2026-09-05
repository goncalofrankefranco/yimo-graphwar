package Graphwar;

/** Smoke checks for the redesigned YIMO Olympiad menu contract. */
public final class MainMenuDesignTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        String[] labels = MainMenuScreen.menuLabels();
        check(labels.length == 5, "menu must expose five primary actions");
        check("Join YIMO Lobby".equals(labels[0]), "lobby action must stay YIMO-branded");
        check("Tutorial".equals(labels[3]), "tutorial action must remain visible");
        check(YimoTheme.MENU_INK.getRed() < 30, "menu must use a dark Olympiad backdrop");
        check(YimoTheme.ORANGE.getRed() > YimoTheme.ORANGE.getBlue(), "menu accent must be warm orange");
        check(YimoTheme.MENU_WHITE.getRed() > 200, "menu text must keep strong paper contrast");
        System.out.println("main-menu-design-check: PASS");
    }
}
