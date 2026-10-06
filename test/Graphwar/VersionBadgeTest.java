package Graphwar;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** The app paints its active build ID in a non-interactive bottom-left badge. */
public final class VersionBadgeTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check("YIMO Graphwar 2.2.0".equals(GraphUI.versionBadgeText()),
                "the badge must show the current product version, not protocol version");

        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            GraphUI.paintVersionBadge(graphics, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        check(image.getRGB(20, 580) != Color.WHITE.getRGB(),
                "the badge background must be painted at the lower-left edge");
        check(image.getRGB(500, 580) == Color.WHITE.getRGB(),
                "the version badge must not stretch across the window");
        System.out.println("version-badge-check: PASS");
    }
}
