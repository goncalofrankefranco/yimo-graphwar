package Graphwar;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Ensures visual arena themes never alter the collision mask. */
public final class ObstacleAppearanceTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Obstacle obstacle = new Obstacle(1, new int[] { 250, 200, 40 });
        boolean[] pointResults = new boolean[] {
                obstacle.collidePoint(250, 200),
                obstacle.collidePoint(250, 150),
                obstacle.collidePoint(100, 100)
        };
        boolean[] soldierResults = new boolean[] {
                obstacle.soldierCollides(250, 200, 8),
                obstacle.soldierCollides(100, 100, 8),
                obstacle.soldierCollides(8, 100, 8)
        };

        BufferedImage classic = obstacle.getDisplayImage(AppearanceSettings.ArenaTheme.CLASSIC);
        BufferedImage garden = obstacle.getDisplayImage(AppearanceSettings.ArenaTheme.GARDEN);
        BufferedImage arcade = obstacle.getDisplayImage(AppearanceSettings.ArenaTheme.ARCADE);
        check(classic.getRGB(0, 0) != garden.getRGB(0, 0), "Garden must visibly recolor the arena");
        check(garden.getRGB(0, 0) != arcade.getRGB(0, 0), "Garden and Arcade must be distinct");
        check(classic.getRGB(250, 200) == Color.BLACK.getRGB(),
                "Classic must preserve the exact legacy terrain image");

        for (AppearanceSettings.ArenaTheme theme : AppearanceSettings.ArenaTheme.values()) {
            obstacle.getDisplayImage(theme);
            check(obstacle.collidePoint(250, 200) == pointResults[0]
                    && obstacle.collidePoint(250, 150) == pointResults[1]
                    && obstacle.collidePoint(100, 100) == pointResults[2],
                    "theme rendering must not change point collisions");
            check(obstacle.soldierCollides(250, 200, 8) == soldierResults[0]
                    && obstacle.soldierCollides(100, 100, 8) == soldierResults[1]
                    && obstacle.soldierCollides(8, 100, 8) == soldierResults[2],
                    "theme rendering must not change soldier collisions");
        }

        obstacle.setExplosion(250, 200, 10);
        obstacle.explodePoint();
        check(!obstacle.collidePoint(250, 200), "explosion must update the collision terrain as before");
        check(obstacle.getDisplayImage(AppearanceSettings.ArenaTheme.GARDEN).getRGB(250, 200)
                != garden.getRGB(250, 200), "an explosion must invalidate cached themed terrain");
        System.out.println("obstacle-appearance-check: PASS");
    }
}
