package Graphwar;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import GraphServer.NetworkConfig;

/** The app paints its product version separately from the wire build ID. */
public final class VersionBadgeTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check("YIMO Graphwar 3.2.0".equals(GraphUI.versionBadgeText()),
                "the badge must show the product version independently of wire compatibility");
        check("YIMO-Graphwar-2.2.0".equals(NetworkConfig.defaults().getBuildId()),
                "the new client must retain the build ID accepted by the existing servers");
        check(NetworkConfig.defaults().getProtocolVersion() == 2,
                "the new client must retain the protocol accepted by the existing servers");

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
