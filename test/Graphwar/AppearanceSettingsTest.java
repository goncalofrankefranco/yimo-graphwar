package Graphwar;

import java.util.prefs.Preferences;

/** Checks safe defaults and Java Preferences round-trip for local cosmetics. */
public final class AppearanceSettingsTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        check("Classic Soldier".equals(AppearanceSettings.MarkerShape.LEGACY_SOLDIER.toString()),
                "marker choices must use friendly labels instead of enum identifiers");
        check("Team Colors".equals(AppearanceSettings.MarkerPalette.TEAM.toString()),
                "palette choices must use friendly labels instead of enum identifiers");
        Preferences parent = Preferences.userRoot();
        Preferences preferences = parent.node("YIMO-AppearanceSettingsTest-" + System.nanoTime());
        try {
            AppearanceSettings defaults = AppearanceSettings.load(preferences);
            check(defaults.getMarkerShape() == AppearanceSettings.MarkerShape.LEGACY_SOLDIER,
                    "missing marker shape must use the classic default");
            check(defaults.getMarkerPalette() == AppearanceSettings.MarkerPalette.TEAM,
                    "missing marker palette must preserve team colors");
            check(defaults.getMarkerPattern() == AppearanceSettings.MarkerPattern.SOLID,
                    "missing marker pattern must use a solid default");
            check(defaults.getArenaTheme() == AppearanceSettings.ArenaTheme.CLASSIC,
                    "missing arena theme must preserve the classic appearance");

            preferences.put("markerShape", "not-a-shape");
            preferences.put("arenaTheme", "not-a-theme");
            AppearanceSettings invalid = AppearanceSettings.load(preferences);
            check(invalid.getMarkerShape() == AppearanceSettings.MarkerShape.LEGACY_SOLDIER,
                    "invalid marker values must safely fall back");
            check(invalid.getArenaTheme() == AppearanceSettings.ArenaTheme.CLASSIC,
                    "invalid theme values must safely fall back");

            invalid.setMarkerShape(AppearanceSettings.MarkerShape.STAR);
            invalid.setMarkerPalette(AppearanceSettings.MarkerPalette.MINT);
            invalid.setMarkerPattern(AppearanceSettings.MarkerPattern.RINGS);
            invalid.setArenaTheme(AppearanceSettings.ArenaTheme.GARDEN);
            invalid.save(preferences);
            AppearanceSettings restored = AppearanceSettings.load(preferences);
            check(restored.getMarkerShape() == AppearanceSettings.MarkerShape.STAR,
                    "marker shape must survive a preference round-trip");
            check(restored.getMarkerPalette() == AppearanceSettings.MarkerPalette.MINT,
                    "marker palette must survive a preference round-trip");
            check(restored.getMarkerPattern() == AppearanceSettings.MarkerPattern.RINGS,
                    "marker pattern must survive a preference round-trip");
            check(restored.getArenaTheme() == AppearanceSettings.ArenaTheme.GARDEN,
                    "arena theme must survive a preference round-trip");
        } finally {
            preferences.removeNode();
            parent.flush();
        }
        System.out.println("appearance-settings-check: PASS");
    }
}
