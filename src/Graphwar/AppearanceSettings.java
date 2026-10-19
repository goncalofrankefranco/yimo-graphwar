package Graphwar;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Local-only visual choices; the game protocol and collision data never see these values. */
public final class AppearanceSettings {
    public enum MarkerShape {
        LEGACY_SOLDIER("Classic Soldier"), ORB("Orb"), STAR("Star");
        private final String label;
        MarkerShape(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public enum MarkerPalette {
        TEAM("Team Colors"), CORAL("Coral"), MINT("Mint"), GOLD("Gold");
        private final String label;
        MarkerPalette(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public enum MarkerPattern {
        SOLID("Solid"), DOTS("Dots"), RINGS("Rings");
        private final String label;
        MarkerPattern(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public enum ArenaTheme {
        CLASSIC("Classic"), GARDEN("Garden"), ARCADE("Arcade");
        private final String label;
        ArenaTheme(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static volatile AppearanceSettings active;

    private MarkerShape markerShape;
    private MarkerPalette markerPalette;
    private MarkerPattern markerPattern;
    private ArenaTheme arenaTheme;

    public AppearanceSettings() {
        this(MarkerShape.LEGACY_SOLDIER, MarkerPalette.TEAM,
                MarkerPattern.SOLID, ArenaTheme.CLASSIC);
    }

    private AppearanceSettings(MarkerShape markerShape, MarkerPalette markerPalette,
            MarkerPattern markerPattern, ArenaTheme arenaTheme) {
        this.markerShape = markerShape;
        this.markerPalette = markerPalette;
        this.markerPattern = markerPattern;
        this.arenaTheme = arenaTheme;
    }

    public static AppearanceSettings load(Preferences preferences) {
        return new AppearanceSettings(
                read(preferences, "markerShape", MarkerShape.class, MarkerShape.LEGACY_SOLDIER),
                read(preferences, "markerPalette", MarkerPalette.class, MarkerPalette.TEAM),
                read(preferences, "markerPattern", MarkerPattern.class, MarkerPattern.SOLID),
                read(preferences, "arenaTheme", ArenaTheme.class, ArenaTheme.CLASSIC));
    }

    private static <T extends Enum<T>> T read(Preferences preferences, String key,
            Class<T> type, T fallback) {
        String value = preferences.get(key, fallback.name());
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException invalidValue) {
            return fallback;
        }
    }

    public void save(Preferences preferences) throws BackingStoreException {
        preferences.put("markerShape", markerShape.name());
        preferences.put("markerPalette", markerPalette.name());
        preferences.put("markerPattern", markerPattern.name());
        preferences.put("arenaTheme", arenaTheme.name());
        preferences.flush();
    }

    static AppearanceSettings current() {
        AppearanceSettings settings = active;
        if (settings == null) {
            synchronized (AppearanceSettings.class) {
                settings = active;
                if (settings == null) {
                    settings = load(Preferences.userNodeForPackage(AppearanceSettings.class));
                    active = settings;
                }
            }
        }
        return settings;
    }

    static void activate(AppearanceSettings settings) {
        active = settings == null ? new AppearanceSettings() : settings;
    }

    public MarkerShape getMarkerShape() {
        return markerShape;
    }

    public void setMarkerShape(MarkerShape markerShape) {
        this.markerShape = markerShape == null ? MarkerShape.LEGACY_SOLDIER : markerShape;
    }

    public MarkerPalette getMarkerPalette() {
        return markerPalette;
    }

    public void setMarkerPalette(MarkerPalette markerPalette) {
        this.markerPalette = markerPalette == null ? MarkerPalette.TEAM : markerPalette;
    }

    public MarkerPattern getMarkerPattern() {
        return markerPattern;
    }

    public void setMarkerPattern(MarkerPattern markerPattern) {
        this.markerPattern = markerPattern == null ? MarkerPattern.SOLID : markerPattern;
    }

    public ArenaTheme getArenaTheme() {
        return arenaTheme;
    }

    public void setArenaTheme(ArenaTheme arenaTheme) {
        this.arenaTheme = arenaTheme == null ? ArenaTheme.CLASSIC : arenaTheme;
    }
}
