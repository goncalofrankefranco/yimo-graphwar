package Graphwar;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JButton;

/** Paint smoke check for the shared rounded button renderer. */
public final class YimoThemeTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        JButton button = YimoTheme.accentButton("Fire");
        button.setSize(180, 44);
        BufferedImage image = new BufferedImage(180, 44, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        button.paint(graphics);
        graphics.dispose();

        check(!button.isContentAreaFilled(), "the custom renderer must own the button surface");
        check(image.getRGB(0, 0) == 0, "rounded corners must stay transparent");
        Color center = new Color(image.getRGB(90, 22), true);
        check(center.getAlpha() > 0, "the button center must be painted");
        System.out.println("yimo-theme-check: PASS");
    }
}
