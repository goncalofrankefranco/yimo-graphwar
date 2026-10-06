package Graphwar;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JLabel;

/** Smoke checks for the redesigned YIMO Olympiad menu contract. */
public final class MainMenuDesignTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean containsText(Component component, String text) {
        if (component instanceof JLabel && text.equals(((JLabel) component).getText())) {
            return true;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (containsText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {
        String[] labels = MainMenuScreen.menuLabels();
        check(labels.length == 4, "menu must expose four primary actions without endpoint settings");
        check("Join YIMO Lobby".equals(labels[0]), "lobby action must stay YIMO-branded");
        check("Tutorial".equals(labels[3]), "tutorial action must remain visible");
        for (String label : labels) {
            check(!"Settings".equals(label), "network settings must not be in the player menu");
        }
        check(MainMenuScreen.isTournamentPort(31000) && MainMenuScreen.isTournamentPort(31049),
                "candidate-code entry must activate across the reserved tournament port range");
        check(!MainMenuScreen.isTournamentPort(30000),
                "practice room ports must not ask for a candidate code");
        try {
            MainMenuScreen screen = new MainMenuScreen(null, null);
            check(containsText(screen, "Powered by Cloudzy"), "the main menu must credit Cloudzy");
            check(containsText(screen, "Candidate code"), "the room form must provide candidate-code entry");
            check(!containsText(screen, "Address"), "the room host must not be user-editable");
        } catch (Exception error) {
            throw new AssertionError("main menu should construct without a running game", error);
        }
        check(YimoTheme.MENU_INK.getRed() < 30, "menu must use a dark Olympiad backdrop");
        check(YimoTheme.ORANGE.getRed() > YimoTheme.ORANGE.getBlue(), "menu accent must be warm orange");
        check(YimoTheme.MENU_WHITE.getRed() > 200, "menu text must keep strong paper contrast");
        System.out.println("main-menu-design-check: PASS");
    }
}
