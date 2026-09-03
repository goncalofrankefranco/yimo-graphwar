//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Responsive screen shell. Child screens use normal Swing layout managers. */
public class YimoScreen extends JPanel {
    protected final Graphwar graphwar;

    public YimoScreen(Graphwar graphwar) {
        this.graphwar = graphwar;
        setOpaque(true);
        setBackground(YimoTheme.BACKGROUND_BOTTOM);
        setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
    }

    protected JPanel topBar(String title) {
        JPanel bar = new JPanel(new BorderLayout(14, 0));
        bar.setOpaque(false);

        JLabel brand = new JLabel("YIMO");
        brand.setFont(YimoTheme.BRAND);
        brand.setForeground(YimoTheme.GOLD);
        bar.add(brand, BorderLayout.WEST);

        if (title != null && title.length() > 0) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(YimoTheme.HEADING);
            titleLabel.setForeground(YimoTheme.TEXT);
            bar.add(titleLabel, BorderLayout.CENTER);
        }
        return bar;
    }

    protected JPanel statusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        return panel;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, YimoTheme.BACKGROUND_TOP,
                    getWidth(), getHeight(), YimoTheme.BACKGROUND_BOTTOM));
            g.fillRect(0, 0, getWidth(), getHeight());

            g.setColor(new Color(30, 58, 52, 22));
            g.setStroke(new BasicStroke(1.0f));
            for (int x = -getHeight(); x < getWidth(); x += 64) {
                g.drawLine(x, 0, x + getHeight(), getHeight());
            }
            int diameter = Math.max(180, Math.min(getWidth(), getHeight()) / 2);
            g.drawOval(getWidth() - diameter - 36, -diameter / 3, diameter, diameter);
            g.drawOval(-diameter / 2, getHeight() - diameter / 2, diameter, diameter);
        } finally {
            g.dispose();
        }
    }
}
