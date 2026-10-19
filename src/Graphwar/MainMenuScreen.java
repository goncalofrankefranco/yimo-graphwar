//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import GraphServer.Constants;

/** YIMO landing screen. All sizing is delegated to Swing layout managers. */
public class MainMenuScreen extends YimoScreen implements ActionListener {
    private final JButton joinGlobal;
    private final JButton createGame;
    private final JButton joinGame;
    private final JButton campaign;
    private final JButton appearance;

    private final JTextField nameFieldGlobal;
    private final JButton yesButtonGlobal;
    private final JButton noButtonGlobal;

    private final JTextField nameFieldCreate;
    private final JTextField portFieldCreate;
    private final JButton yesButtonCreate;
    private final JButton noButtonCreate;

    private final JTextField nameFieldJoin;
    private final JTextField portFieldJoin;
    private final JPasswordField candidateCodeField;
    private JLabel candidateCodeLabel;
    private final JButton yesButtonJoin;
    private final JButton noButtonJoin;

    private final JPanel formCards;
    private final java.awt.CardLayout formLayout;
    private final JLabel statusLabel;
    private final AppearanceSettings appearanceSettings;
    private final JComboBox<AppearanceSettings.MarkerShape> markerShapeChoice;
    private final JComboBox<AppearanceSettings.MarkerPalette> markerPaletteChoice;
    private final JComboBox<AppearanceSettings.MarkerPattern> markerPatternChoice;
    private final JComboBox<AppearanceSettings.ArenaTheme> arenaThemeChoice;
    private final JButton appearanceBack;
    private final JButton appearanceReset;
    private final AppearancePreview appearancePreview;

    private boolean joinGlobalVisible;
    private boolean createVisible;
    private boolean joinVisible;
    private boolean appearanceVisible;
    private boolean updatingAppearance;

    public MainMenuScreen(Graphwar graphwar, String confFile) throws InterruptedException, IOException {
        super(graphwar);
        appearanceSettings = AppearanceSettings.current();
        setLayout(new BorderLayout(20, 20));

        add(topBar(""), BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = 0;
        left.weightx = 0.58;
        left.weighty = 1.0;
        left.fill = GridBagConstraints.BOTH;
        left.insets = new Insets(0, 0, 0, 10);

        JPanel intro = new JPanel(new GridBagLayout());
        intro.setOpaque(false);
        JPanel hero = new JPanel();
        hero.setOpaque(false);
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        JLabel kicker = new JLabel("YIMO  //  OLYMPIAD EDITION");
        kicker.setFont(YimoTheme.SMALL);
        kicker.setForeground(YimoTheme.ORANGE);
        kicker.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        JLabel title = new JLabel("<html><div style='line-height:86%; text-align:center'>YIMO<br><span style='color:#F2A35B'>GRAPHWAR</span></div></html>");
        title.setFont(new java.awt.Font("Serif", java.awt.Font.BOLD, 72));
        title.setForeground(YimoTheme.MENU_WHITE);
        title.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        JLabel tagline = new JLabel("READ THE CURVE.  ADAPT THE SHOT.");
        tagline.setFont(new java.awt.Font("Sans", java.awt.Font.BOLD, 13));
        tagline.setForeground(YimoTheme.MENU_LINE);
        tagline.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        JLabel sponsor = new JLabel("Powered by Cloudzy");
        sponsor.setFont(YimoTheme.SMALL);
        sponsor.setForeground(YimoTheme.MENU_LINE);
        sponsor.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        hero.add(kicker);
        hero.add(Box.createVerticalStrut(18));
        hero.add(title);
        hero.add(Box.createVerticalStrut(18));
        hero.add(tagline);
        hero.add(Box.createVerticalStrut(12));
        hero.add(sponsor);
        GridBagConstraints introConstraints = new GridBagConstraints();
        introConstraints.gridx = 0;
        introConstraints.gridy = 0;
        introConstraints.weightx = 1.0;
        introConstraints.weighty = 1.0;
        introConstraints.anchor = GridBagConstraints.CENTER;
        intro.add(hero, introConstraints);
        content.add(intro, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = 0;
        right.weightx = 0.52;
        right.weighty = 1.0;
        right.fill = GridBagConstraints.BOTH;
        right.insets = new Insets(0, 10, 0, 0);

        JPanel actionCard = YimoTheme.card();
        actionCard.setLayout(new BorderLayout());
        actionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(YimoTheme.ORANGE, 2),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

        formLayout = new java.awt.CardLayout();
        formCards = new JPanel(formLayout);
        formCards.setOpaque(false);

        JPanel menu = new JPanel(new GridBagLayout());
        menu.setOpaque(false);
        joinGlobal = YimoTheme.menuButton("Join YIMO Lobby", true);
        createGame = YimoTheme.menuButton("Create Practice Game", false);
        joinGame = YimoTheme.menuButton("Join Room", false);
        campaign = YimoTheme.menuButton("Tutorial", false);
        appearance = YimoTheme.menuButton("Appearance", false);
        addMenuFiller(menu, 0);
        addMenuButton(menu, joinGlobal, 1);
        addMenuButton(menu, createGame, 2);
        addMenuButton(menu, joinGame, 3);
        addMenuButton(menu, campaign, 4);
        addMenuButton(menu, appearance, 5);
        addMenuFiller(menu, 6);
        formCards.add(menu, "menu");

        nameFieldGlobal = YimoTheme.textField(18);
        yesButtonGlobal = YimoTheme.accentButton("Connect");
        noButtonGlobal = YimoTheme.quietButton("Back");
        formCards.add(globalForm(), "global");

        nameFieldCreate = YimoTheme.textField(18);
        portFieldCreate = YimoTheme.textField(8);
        yesButtonCreate = YimoTheme.accentButton("Create");
        noButtonCreate = YimoTheme.quietButton("Back");
        formCards.add(createForm(), "create");

        nameFieldJoin = YimoTheme.textField(18);
        portFieldJoin = YimoTheme.textField(8);
        candidateCodeField = new JPasswordField(18);
        yesButtonJoin = YimoTheme.accentButton("Join");
        noButtonJoin = YimoTheme.quietButton("Back");
        formCards.add(joinForm(), "join");

        markerShapeChoice = YimoTheme.combo(AppearanceSettings.MarkerShape.values());
        markerPaletteChoice = YimoTheme.combo(AppearanceSettings.MarkerPalette.values());
        markerPatternChoice = YimoTheme.combo(AppearanceSettings.MarkerPattern.values());
        arenaThemeChoice = YimoTheme.combo(AppearanceSettings.ArenaTheme.values());
        markerShapeChoice.setSelectedItem(appearanceSettings.getMarkerShape());
        markerPaletteChoice.setSelectedItem(appearanceSettings.getMarkerPalette());
        markerPatternChoice.setSelectedItem(appearanceSettings.getMarkerPattern());
        arenaThemeChoice.setSelectedItem(appearanceSettings.getArenaTheme());
        appearanceBack = YimoTheme.quietButton("Back");
        appearanceReset = YimoTheme.button("Reset to Classic");
        appearancePreview = new AppearancePreview();
        formCards.add(appearanceForm(), "appearance");

        actionCard.add(formCards, BorderLayout.CENTER);
        content.add(actionCard, right);
        add(content, BorderLayout.CENTER);

        statusLabel = YimoTheme.mutedLabel("");
        statusLabel.setForeground(YimoTheme.MENU_LINE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
        add(statusLabel, BorderLayout.SOUTH);

        addActionListeners();
        showMenu();
    }

    private void addMenuButton(JPanel menu, JButton button, int row) {
        button.setPreferredSize(new Dimension(320, 58));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 12, 0);
        menu.add(button, constraints);
    }

    static String[] menuLabels() {
        return new String[] { "Join YIMO Lobby", "Create Practice Game", "Join Room", "Tutorial", "Appearance" };
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, YimoTheme.MENU_INK,
                    getWidth(), getHeight(), new Color(20, 31, 37)));
            g.fillRect(0, 0, getWidth(), getHeight());

            g.setColor(new Color(245, 242, 232, 18));
            g.setStroke(new BasicStroke(1.0f));
            for (int x = -getHeight(); x < getWidth(); x += 54) {
                g.drawLine(x, 0, x + getHeight(), getHeight());
            }
            for (int y = 36; y < getHeight(); y += 54) {
                g.drawLine(0, y, getWidth(), y);
            }

            int ring = Math.max(300, Math.min(getWidth(), getHeight()) + 80);
            g.setColor(new Color(YimoTheme.ORANGE.getRed(), YimoTheme.ORANGE.getGreen(),
                    YimoTheme.ORANGE.getBlue(), 34));
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(getWidth() / 2 - ring / 2, getHeight() / 2 - ring / 2, ring, ring);
            g.drawOval(getWidth() / 2 - ring / 2 + 22, getHeight() / 2 - ring / 2 + 22,
                    ring - 44, ring - 44);

            g.setColor(YimoTheme.ORANGE);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10.0f, new float[] { 10.0f, 8.0f }, 0.0f));
            java.awt.geom.Path2D trajectory = new java.awt.geom.Path2D.Double();
            trajectory.moveTo(0, getHeight() * 0.78);
            trajectory.curveTo(getWidth() * 0.28, getHeight() * 0.54,
                    getWidth() * 0.55, getHeight() * 0.65,
                    getWidth(), getHeight() * 0.22);
            g.draw(trajectory);

            g.setColor(new Color(YimoTheme.ORANGE.getRed(), YimoTheme.ORANGE.getGreen(),
                    YimoTheme.ORANGE.getBlue(), 150));
            g.fillOval(getWidth() - 28, (int) (getHeight() * 0.22) - 5, 10, 10);
        } finally {
            g.dispose();
        }
    }

    private void addMenuFiller(JPanel menu, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.VERTICAL;
        menu.add(Box.createVerticalGlue(), constraints);
    }

    private JPanel formCard(String eyebrow, String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setOpaque(false);
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(YimoTheme.title(title));
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    private JPanel formGrid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        return grid;
    }

    private JLabel row(JPanel grid, int y, String label, JTextField field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = y;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 10, 10);
        JLabel rowLabel = YimoTheme.label(label);
        grid.add(rowLabel, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = y;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(0, 0, 10, 0);
        grid.add(field, fieldConstraints);
        return rowLabel;
    }

    private JPanel formActions(JButton confirm, JButton cancel) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(confirm);
        return actions;
    }

    private JPanel globalForm() {
        JPanel panel = formCard("YIMO NETWORK", "Enter your player name");
        JPanel grid = formGrid();
        row(grid, 0, "Name", nameFieldGlobal);
        panel.add(grid, BorderLayout.CENTER);
        panel.add(formActions(yesButtonGlobal, noButtonGlobal), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createForm() {
        JPanel panel = formCard("LOCAL PRACTICE", "Open a room on this computer");
        JPanel grid = formGrid();
        row(grid, 0, "Room name", nameFieldCreate);
        row(grid, 1, "Port", portFieldCreate);
        panel.add(grid, BorderLayout.CENTER);
        panel.add(formActions(yesButtonCreate, noButtonCreate), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel joinForm() {
        JPanel panel = formCard("JOIN YIMO ROOM", "Practice rooms need only a port; tournament rooms also need your candidate code.");
        JPanel grid = formGrid();
        row(grid, 0, "Name", nameFieldJoin);
        row(grid, 1, "Port", portFieldJoin);
        candidateCodeLabel = row(grid, 2, "Candidate code", candidateCodeField);
        updateCandidateCodeVisibility();
        panel.add(grid, BorderLayout.CENTER);
        panel.add(formActions(yesButtonJoin, noButtonJoin), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel appearanceForm() {
        JPanel panel = formCard("LOCAL LOOK", "Make the battlefield yours");
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        JPanel choices = new JPanel(new GridBagLayout());
        choices.setOpaque(false);
        addAppearanceRow(choices, 0, "PLAYER MARKER", markerShapeChoice);
        addAppearanceRow(choices, 1, "MARKER COLOR", markerPaletteChoice);
        addAppearanceRow(choices, 2, "MARKER PATTERN", markerPatternChoice);
        addAppearanceRow(choices, 3, "ARENA THEME", arenaThemeChoice);
        GridBagConstraints choiceConstraints = new GridBagConstraints();
        choiceConstraints.gridx = 0;
        choiceConstraints.gridy = 0;
        choiceConstraints.weightx = 0.48;
        choiceConstraints.fill = GridBagConstraints.HORIZONTAL;
        choiceConstraints.insets = new Insets(0, 0, 0, 12);
        body.add(choices, choiceConstraints);

        appearancePreview.setPreferredSize(new Dimension(250, 190));
        appearancePreview.setBorder(BorderFactory.createLineBorder(YimoTheme.CARD_BORDER));
        GridBagConstraints previewConstraints = new GridBagConstraints();
        previewConstraints.gridx = 1;
        previewConstraints.gridy = 0;
        previewConstraints.weightx = 0.52;
        previewConstraints.fill = GridBagConstraints.BOTH;
        body.add(appearancePreview, previewConstraints);
        panel.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        footer.add(YimoTheme.mutedLabel("Saved on this device. Team colors and player names stay clear."), BorderLayout.CENTER);
        footer.add(formActions(appearanceReset, appearanceBack), BorderLayout.EAST);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void addAppearanceRow(JPanel grid, int row, String label, JComponent control) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 10, 10);
        JLabel title = YimoTheme.sectionTitle(label);
        grid.add(title, labelConstraints);

        GridBagConstraints controlConstraints = new GridBagConstraints();
        controlConstraints.gridx = 1;
        controlConstraints.gridy = row;
        controlConstraints.weightx = 1.0;
        controlConstraints.fill = GridBagConstraints.HORIZONTAL;
        controlConstraints.insets = new Insets(0, 0, 10, 0);
        grid.add(control, controlConstraints);
    }

    private final class AppearancePreview extends JPanel {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth();
                int height = getHeight();
                AppearanceSettings.ArenaTheme theme = appearanceSettings.getArenaTheme();
                Color background = theme == AppearanceSettings.ArenaTheme.GARDEN
                        ? YimoTheme.GARDEN_BACKGROUND : theme == AppearanceSettings.ArenaTheme.ARCADE
                                ? YimoTheme.ARCADE_BACKGROUND : Color.WHITE;
                Color obstacle = theme == AppearanceSettings.ArenaTheme.GARDEN
                        ? YimoTheme.GARDEN_OBSTACLE : theme == AppearanceSettings.ArenaTheme.ARCADE
                                ? YimoTheme.ARCADE_OBSTACLE : Color.BLACK;
                g.setColor(background);
                g.fillRect(0, 0, width, height);
                if (theme == AppearanceSettings.ArenaTheme.GARDEN) {
                    g.setColor(new Color(74, 125, 94, 40));
                    for (int y = 12; y < height; y += 24) {
                        for (int x = 12; x < width; x += 24) g.fillOval(x, y, 3, 3);
                    }
                } else if (theme == AppearanceSettings.ArenaTheme.ARCADE) {
                    g.setColor(new Color(89, 111, 173, 35));
                    for (int x = 0; x < width; x += 24) g.drawLine(x, 0, x, height);
                    for (int y = 0; y < height; y += 24) g.drawLine(0, y, width, y);
                }
                g.setColor(obstacle);
                g.fillOval(width / 2 - 25, height / 2 - 18, 50, 36);
                g.drawLine(10, height / 2, width - 10, height / 2);
                g.drawLine(width / 2, 12, width / 2, height - 12);
                g.setColor(YimoTheme.TEXT);
                g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10.0f, new float[] { 6.0f, 5.0f }, 0.0f));
                g.drawArc(24, 35, width / 2, height / 2, 205, 120);

                int markerX = Math.max(26, width / 4);
                int markerY = Math.max(36, height * 3 / 4);
                Color markerColor = YimoTheme.markerColor(appearanceSettings.getMarkerPalette(), YimoTheme.PLAYER_BLUE);
                g.setColor(new Color(markerColor.getRed(), markerColor.getGreen(), markerColor.getBlue(), 90));
                if (appearanceSettings.getMarkerShape() == AppearanceSettings.MarkerShape.STAR) {
                    java.awt.geom.Path2D star = new java.awt.geom.Path2D.Double();
                    for (int i = 0; i < 10; i++) {
                        double angle = -Math.PI / 2 + i * Math.PI / 5;
                        double radius = i % 2 == 0 ? 17 : 9;
                        double x = markerX + Math.cos(angle) * radius;
                        double y = markerY + Math.sin(angle) * radius;
                        if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
                    }
                    star.closePath();
                    g.fill(star);
                } else {
                    g.fillOval(markerX - 16, markerY - 16, 32, 32);
                }
                g.setColor(markerColor);
                g.setStroke(new BasicStroke(2.0f));
                if (appearanceSettings.getMarkerPattern() == AppearanceSettings.MarkerPattern.RINGS) {
                    g.drawOval(markerX - 12, markerY - 12, 24, 24);
                } else if (appearanceSettings.getMarkerPattern() == AppearanceSettings.MarkerPattern.DOTS) {
                    g.fillOval(markerX - 2, markerY - 2, 4, 4);
                    g.fillOval(markerX - 13, markerY - 2, 4, 4);
                    g.fillOval(markerX + 9, markerY - 2, 4, 4);
                }
                g.drawString("LIVE PREVIEW", 12, height - 10);
            } finally {
                g.dispose();
            }
        }
    }

    private void addActionListeners() {
        joinGlobal.addActionListener(this);
        createGame.addActionListener(this);
        joinGame.addActionListener(this);
        campaign.addActionListener(this);
        appearance.addActionListener(this);
        nameFieldGlobal.addActionListener(this);
        yesButtonGlobal.addActionListener(this);
        noButtonGlobal.addActionListener(this);
        nameFieldCreate.addActionListener(this);
        portFieldCreate.addActionListener(this);
        yesButtonCreate.addActionListener(this);
        noButtonCreate.addActionListener(this);
        nameFieldJoin.addActionListener(this);
        portFieldJoin.addActionListener(this);
        candidateCodeField.addActionListener(this);
        portFieldJoin.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { updateCandidateCodeVisibility(); }
            public void removeUpdate(DocumentEvent event) { updateCandidateCodeVisibility(); }
            public void changedUpdate(DocumentEvent event) { updateCandidateCodeVisibility(); }
        });
        yesButtonJoin.addActionListener(this);
        noButtonJoin.addActionListener(this);
        markerShapeChoice.addActionListener(event -> saveAppearance());
        markerPaletteChoice.addActionListener(event -> saveAppearance());
        markerPatternChoice.addActionListener(event -> saveAppearance());
        arenaThemeChoice.addActionListener(event -> saveAppearance());
        appearanceBack.addActionListener(this);
        appearanceReset.addActionListener(this);
    }

    private void showMenu() {
        joinGlobalVisible = false;
        createVisible = false;
        joinVisible = false;
        appearanceVisible = false;
        formLayout.show(formCards, "menu");
        statusLabel.setText("");
        statusLabel.setForeground(YimoTheme.MUTED);
    }

    private void showJoinGlobal(boolean show) {
        joinGlobalVisible = show;
        createVisible = false;
        joinVisible = false;
        if (show) {
            formLayout.show(formCards, "global");
            nameFieldGlobal.requestFocusInWindow();
        } else {
            showMenu();
        }
    }

    private void showCreateGame(boolean show) {
        joinGlobalVisible = false;
        createVisible = show;
        joinVisible = false;
        if (show) {
            formLayout.show(formCards, "create");
            nameFieldCreate.setText("Practice Room");
            portFieldCreate.setText(Integer.toString(Constants.DEFAULT_PORT));
            nameFieldCreate.requestFocusInWindow();
        } else {
            showMenu();
        }
    }

    private void showJoinGame(boolean show) {
        joinGlobalVisible = false;
        createVisible = false;
        joinVisible = show;
        if (show) {
            formLayout.show(formCards, "join");
            portFieldJoin.setText(Integer.toString(Constants.DEFAULT_PORT));
            candidateCodeField.setText("");
            updateCandidateCodeVisibility();
            nameFieldJoin.requestFocusInWindow();
        } else {
            showMenu();
        }
    }

    private void showAppearance(boolean show) {
        appearanceVisible = show;
        joinGlobalVisible = false;
        createVisible = false;
        joinVisible = false;
        if (show) {
            formLayout.show(formCards, "appearance");
            statusLabel.setText("Appearance settings are saved locally.");
            statusLabel.setForeground(YimoTheme.MINT);
        } else {
            showMenu();
        }
    }

    private void saveAppearance() {
        if (updatingAppearance) return;
        appearanceSettings.setMarkerShape((AppearanceSettings.MarkerShape) markerShapeChoice.getSelectedItem());
        appearanceSettings.setMarkerPalette((AppearanceSettings.MarkerPalette) markerPaletteChoice.getSelectedItem());
        appearanceSettings.setMarkerPattern((AppearanceSettings.MarkerPattern) markerPatternChoice.getSelectedItem());
        appearanceSettings.setArenaTheme((AppearanceSettings.ArenaTheme) arenaThemeChoice.getSelectedItem());
        AppearanceSettings.activate(appearanceSettings);
        appearancePreview.repaint();
        try {
            appearanceSettings.save(Preferences.userNodeForPackage(AppearanceSettings.class));
            statusLabel.setText("Appearance saved on this device.");
            statusLabel.setForeground(YimoTheme.MINT);
        } catch (BackingStoreException error) {
            statusLabel.setText("Could not save appearance preferences.");
            statusLabel.setForeground(YimoTheme.DANGER);
        }
    }

    private void resetAppearance() {
        updatingAppearance = true;
        markerShapeChoice.setSelectedItem(AppearanceSettings.MarkerShape.LEGACY_SOLDIER);
        markerPaletteChoice.setSelectedItem(AppearanceSettings.MarkerPalette.TEAM);
        markerPatternChoice.setSelectedItem(AppearanceSettings.MarkerPattern.SOLID);
        arenaThemeChoice.setSelectedItem(AppearanceSettings.ArenaTheme.CLASSIC);
        updatingAppearance = false;
        saveAppearance();
    }

    static boolean isTournamentPort(int value) {
        return value >= Constants.TOURNAMENT_ROOM_PORT_START && value <= Constants.TOURNAMENT_ROOM_PORT_END;
    }

    private void updateCandidateCodeVisibility() {
        boolean visible = false;
        try {
            visible = isTournamentPort(Integer.parseInt(portFieldJoin.getText().trim()));
        } catch (NumberFormatException ignored) {
            // Keep the candidate-code field hidden until a complete tournament port is entered.
        }
        if (candidateCodeLabel != null) candidateCodeLabel.setVisible(visible);
        candidateCodeField.setVisible(visible);
        if (!visible) candidateCodeField.setText("");
    }

    private void status(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? YimoTheme.DANGER : YimoTheme.MINT);
    }

    public void showMessage(String message) {
        status(message, message != null && (message.indexOf("Could") >= 0 || message.indexOf("Failed") >= 0 || message.indexOf("must") >= 0));
    }

    private String playerName(JTextField field) {
        String name = field.getText() == null ? "" : field.getText().trim();
        if (name.length() > 20) {
            name = name.substring(0, 20);
            field.setText(name);
        }
        return name;
    }

    private int port(JTextField field) throws NumberFormatException {
        int value = Integer.parseInt(field.getText().trim());
        if (value < 1 || value > 65535) {
            throw new NumberFormatException("port");
        }
        return value;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();
        try {
            if (appearanceVisible) {
                if (source == appearanceBack) {
                    showAppearance(false);
                } else if (source == appearanceReset) {
                    resetAppearance();
                }
                return;
            }
            if (joinGlobalVisible) {
                if (source == noButtonGlobal) {
                    showJoinGlobal(false);
                } else if (source == yesButtonGlobal || source == nameFieldGlobal) {
                    String name = playerName(nameFieldGlobal);
                    if (name.length() == 0) {
                        status("Enter a name first.", true);
                    } else {
                        status("Connecting to the YIMO lobby…", false);
                        graphwar.joinGlobal(name);
                        showMenu();
                    }
                }
                return;
            }
            if (createVisible) {
                if (source == noButtonCreate) {
                    showCreateGame(false);
                } else if (source == yesButtonCreate || source == nameFieldCreate || source == portFieldCreate) {
                    String name = playerName(nameFieldCreate);
                    if (name.length() == 0) {
                        status("Enter a room name first.", true);
                    } else {
                        status("Starting the practice room…", false);
						graphwar.createGame(port(portFieldCreate), name);
                        graphwar.getGameData().addPlayer(name);
                        showMenu();
                    }
                }
                return;
            }
            if (joinVisible) {
                if (source == noButtonJoin) {
                    showJoinGame(false);
                } else if (source == yesButtonJoin || source == nameFieldJoin || source == portFieldJoin || source == candidateCodeField) {
                    String name = playerName(nameFieldJoin);
                    int roomPort = port(portFieldJoin);
                    if (name.length() == 0) {
                        status("Enter your display name.", true);
                    } else if (isTournamentPort(roomPort)) {
                        char[] candidateCharacters = candidateCodeField.getPassword();
                        String candidateCode = new String(candidateCharacters).trim();
                        Arrays.fill(candidateCharacters, '\0');
                        if (candidateCode.length() == 0) {
                            status("Enter your candidate code for this tournament port.", true);
                            return;
                        }
                        status("Validating your tournament match…", false);
                        TournamentClient.JoinDetails assigned = TournamentClient.join(
                                Constants.TOURNAMENT_API_BASE_URL, candidateCode, name, roomPort,
                                Constants.BUILD_ID, Constants.PROTOCOL_VERSION);
                        String officialName = assigned.getDisplayName();
                        graphwar.joinGame(Constants.GLOBAL_IP, assigned.getPort(), officialName, assigned.getRoomToken());
                        graphwar.getGameData().addPlayer(officialName);
                        candidateCodeField.setText("");
                        showJoinGame(false);
                        graphwar.getUI().setScreen(Constants.PRE_GAME_SCREEN);
                    } else {
                        status("Connecting to the YIMO room…", false);
						graphwar.joinGame(Constants.GLOBAL_IP, roomPort, name);
                        graphwar.getGameData().addPlayer(name);
                        showJoinGame(false);
                        graphwar.getUI().setScreen(Constants.PRE_GAME_SCREEN);
                    }
                }
                return;
            }

            if (source == joinGlobal) {
                showJoinGlobal(true);
            } else if (source == createGame) {
                showCreateGame(true);
            } else if (source == joinGame) {
                showJoinGame(true);
            } else if (source == campaign) {
                graphwar.getUI().setScreen(Constants.CAMPAIGN_SCREEN);
            } else if (source == appearance) {
                showAppearance(true);
            }
        } catch (NumberFormatException error) {
            status("Port must be a number between 1 and 65535.", true);
        } catch (IOException error) {
            String message = error.getMessage();
            status(message == null || message.trim().length() == 0
                    ? "Connection failed. Check the port, candidate code, and internet connection." : message, true);
            error.printStackTrace();
        }
    }
}
