package Graphwar;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JComboBox;
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

    private static Component findNamed(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                Component match = findNamed(child, name);
                if (match != null) return match;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        String[] labels = MainMenuScreen.menuLabels();
        check(labels.length == 5, "menu must expose the game and appearance actions without endpoint settings");
        check("Join YIMO Lobby".equals(labels[0]), "lobby action must stay YIMO-branded");
        check("Tutorial".equals(labels[3]), "tutorial action must remain visible");
        check("Appearance".equals(labels[4]), "appearance customization must have a separate menu action");
        for (String label : labels) {
            check(!"Settings".equals(label), "network settings must not be in the player menu");
        }
        check(MainMenuScreen.isTournamentPort(31000) && MainMenuScreen.isTournamentPort(31049),
                "candidate-code entry must activate across the reserved tournament port range");
        check(!MainMenuScreen.isTournamentPort(30000),
                "practice room ports must not ask for a candidate code");
        try {
            AppearanceSettings.activate(new AppearanceSettings());
            MainMenuScreen screen = new MainMenuScreen(null, null);
            check(containsText(screen, "Powered by Cloudzy"), "the main menu must credit Cloudzy");
            check(containsText(screen, "Candidate code"), "the room form must provide candidate-code entry");
            check(containsText(screen, "Make the battlefield yours"), "appearance controls must be in their own card");
            check(!containsText(screen, "Address"), "the room host must not be user-editable");
            Component palette = findNamed(screen, "appearance-marker-palette");
            Component pattern = findNamed(screen, "appearance-marker-pattern");
            check(palette instanceof JComboBox && !palette.isEnabled(),
                    "classic soldier style must disable custom marker colors");
            check(pattern instanceof JComboBox && !pattern.isEnabled(),
                    "classic soldier style must disable custom marker patterns");
        } catch (Exception error) {
            throw new AssertionError("main menu should construct without a running game", error);
        } finally {
            AppearanceSettings.activate(null);
        }
        check(YimoTheme.MENU_INK.getRed() < 30, "menu must use a dark Olympiad backdrop");
        check(YimoTheme.ORANGE.getRed() > YimoTheme.ORANGE.getBlue(), "menu accent must be warm orange");
        check(YimoTheme.MENU_WHITE.getRed() > 200, "menu text must keep strong paper contrast");
        System.out.println("main-menu-design-check: PASS");
    }
}
