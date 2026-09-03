//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.Border;

/** Small standard-library-only visual system for the YIMO client. */
public final class YimoTheme {
    // YIMO official site-inspired palette: paper, ink, one warm orange accent.
    public static final Color BACKGROUND_TOP = new Color(246, 242, 233);
    public static final Color BACKGROUND_BOTTOM = new Color(255, 253, 247);
    public static final Color CARD = new Color(255, 253, 247);
    public static final Color CARD_LIGHT = new Color(246, 242, 233);
    public static final Color CARD_BORDER = new Color(196, 190, 177);
    public static final Color TEXT = new Color(30, 58, 52);
    public static final Color MUTED = new Color(76, 99, 92);
    public static final Color ORANGE = new Color(242, 163, 91);
    public static final Color DARK_ORANGE = new Color(183, 105, 46);
    public static final Color CYAN = ORANGE;
    public static final Color MINT = new Color(30, 58, 52);
    public static final Color GOLD = new Color(183, 105, 46);
    public static final Color DANGER = new Color(176, 61, 47);
    public static final Color INPUT = new Color(255, 253, 247);

    public static final Font BRAND = new Font("Serif", Font.BOLD, 22);
    public static final Font DISPLAY = new Font("Serif", Font.PLAIN, 58);
    public static final Font HEADING = new Font("Sans", Font.BOLD, 20);
    public static final Font BODY = new Font("Sans", Font.PLAIN, 14);
    public static final Font SMALL = new Font("Sans", Font.PLAIN, 12);

    private YimoTheme() {
    }

    public static JPanel card() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        return panel;
    }

    public static Border inputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(64, 104, 132)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }

    public static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(BODY);
        return label;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(SMALL);
        return label;
    }

    public static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setForeground(CYAN);
        label.setFont(new Font("Sans", Font.BOLD, 12));
        return label;
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(HEADING);
        return label;
    }

    public static JButton button(String text) {
        return new AnimatedButton(text, CARD_LIGHT, new Color(238, 231, 217),
                new Color(221, 211, 193), TEXT);
    }

    public static JButton accentButton(String text) {
        return new AnimatedButton(text, ORANGE, new Color(248, 181, 116), DARK_ORANGE, TEXT);
    }

    public static JButton quietButton(String text) {
        return new AnimatedButton(text, CARD, new Color(248, 244, 235),
                new Color(231, 224, 211), MUTED);
    }

    public static void styleInput(JComponent component) {
        component.setFont(BODY);
        component.setForeground(TEXT);
        component.setBackground(INPUT);
        component.setBorder(inputBorder());
        if (component instanceof JTextField) {
            ((JTextField) component).setCaretColor(CYAN);
        }
    }

    public static JTextField textField(int columns) {
        JTextField field = new JTextField(columns);
        styleInput(field);
        field.setPreferredSize(new Dimension(180, 34));
        return field;
    }

    public static <T> JComboBox<T> combo(T[] values) {
        JComboBox<T> combo = new JComboBox<T>(values);
        styleInput(combo);
        combo.setPreferredSize(new Dimension(180, 34));
        return combo;
    }

    public static JCheckBox checkBox(String text) {
        JCheckBox check = new JCheckBox(text);
        check.setFont(BODY);
        check.setForeground(TEXT);
        check.setOpaque(false);
        check.setFocusPainted(false);
        return check;
    }

    public static JScrollPane scroll(JComponent component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        scroll.getViewport().setBackground(INPUT);
        scroll.setBackground(INPUT);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public static JLabel centered(String text) {
        JLabel label = label(text);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    /** Rounded controls share one animated renderer across every YIMO screen. */
    private static final class AnimatedButton extends JButton {
        private final Color normalColor;
        private final Color hoverColor;
        private final Color pressColor;
        private final Color textColor;
        private Timer animation;
        private float state;
        private float targetState;
        private boolean hovering;
        private boolean pressing;

        AnimatedButton(String text, Color normalColor, Color hoverColor,
                Color pressColor, Color textColor) {
            super(text);
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
            this.pressColor = pressColor;
            this.textColor = textColor;
            setFont(new Font("Sans", Font.BOLD, 14));
            setForeground(textColor);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
            setMargin(new Insets(0, 0, 0, 0));
            setFocusPainted(false);
            setRolloverEnabled(false);
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(180, 44));

            animation = new Timer(15, event -> {
                state += (targetState - state) * 0.28f;
                if (Math.abs(targetState - state) < 0.02f) {
                    state = targetState;
                    animation.stop();
                }
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovering = true;
                    updateAnimation();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovering = false;
                    pressing = false;
                    updateAnimation();
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON1 && isEnabled()) {
                        pressing = true;
                        updateAnimation();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    pressing = false;
                    updateAnimation();
                }
            });
        }

        private void updateAnimation() {
            targetState = !isEnabled() ? 0.0f : (pressing ? 1.0f : (hovering ? 0.55f : 0.0f));
            if (!animation.isRunning()) {
                animation.start();
            }
        }

        private Color mix(Color first, Color second, float amount) {
            float value = Math.max(0.0f, Math.min(1.0f, amount));
            int red = (int) (first.getRed() + (second.getRed() - first.getRed()) * value);
            int green = (int) (first.getGreen() + (second.getGreen() - first.getGreen()) * value);
            int blue = (int) (first.getBlue() + (second.getBlue() - first.getBlue()) * value);
            return new Color(red, green, blue);
        }

        private Color currentColor() {
            if (pressing) {
                return mix(normalColor, pressColor, state);
            }
            return mix(normalColor, hoverColor, state);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth();
                int height = getHeight();
                int pressOffset = pressing && isEnabled() ? 2 : 0;
                int arc = Math.min(22, Math.max(12, height - 4));

                if (isEnabled()) {
                    g.setColor(new Color(TEXT.getRed(), TEXT.getGreen(), TEXT.getBlue(), 34));
                    g.fillRoundRect(1, 3, Math.max(0, width - 2), Math.max(0, height - 4), arc, arc);
                }

                Color fill = isEnabled() ? currentColor() : new Color(224, 218, 207);
                Color bottom = mix(fill, isEnabled() ? DARK_ORANGE : CARD_BORDER, 0.10f);
                g.setPaint(new GradientPaint(0, pressOffset, mix(fill, Color.WHITE, 0.08f),
                        0, height, bottom));
                g.fillRoundRect(1, 1 + pressOffset, Math.max(0, width - 2),
                        Math.max(0, height - 4), arc, arc);

                if (isFocusOwner()) {
                    g.setColor(DARK_ORANGE);
                    g.setStroke(new java.awt.BasicStroke(2.0f));
                    g.drawRoundRect(3, 3 + pressOffset, Math.max(0, width - 6),
                            Math.max(0, height - 8), Math.max(10, arc - 4), Math.max(10, arc - 4));
                }

                g.setFont(getFont());
                g.setColor(isEnabled() ? textColor : MUTED);
                String text = getText() == null ? "" : getText();
                java.awt.FontMetrics metrics = g.getFontMetrics();
                int textX = (width - metrics.stringWidth(text)) / 2;
                int textY = (height - metrics.getHeight()) / 2 + metrics.getAscent() + pressOffset;
                g.drawString(text, textX, textY);
            } finally {
                g.dispose();
            }
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            // The border and focus ring are painted with the same rounded geometry as the fill.
        }
    }
}
