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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import GraphServer.Constants;

/** YIMO landing screen. All sizing is delegated to Swing layout managers. */
public class MainMenuScreen extends YimoScreen implements ActionListener {
    private final JButton joinGlobal;
    private final JButton createGame;
    private final JButton joinGame;
    private final JButton campaign;
    private final JButton settings;

    private final JTextField nameFieldGlobal;
    private final JButton yesButtonGlobal;
    private final JButton noButtonGlobal;

    private final JTextField nameFieldCreate;
    private final JTextField portFieldCreate;
    private final JButton yesButtonCreate;
    private final JButton noButtonCreate;

    private final JTextField nameFieldJoin;
    private final JTextField portFieldJoin;
    private final JTextField ipFieldJoin;
    private final JButton yesButtonJoin;
    private final JButton noButtonJoin;

    private final JPanel formCards;
    private final java.awt.CardLayout formLayout;
    private final JLabel statusLabel;

    private boolean joinGlobalVisible;
    private boolean createVisible;
    private boolean joinVisible;

    public MainMenuScreen(Graphwar graphwar, String confFile) throws InterruptedException, IOException {
        super(graphwar);
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
        hero.add(kicker);
        hero.add(Box.createVerticalStrut(18));
        hero.add(title);
        hero.add(Box.createVerticalStrut(18));
        hero.add(tagline);
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
        settings = YimoTheme.menuButton("Settings", false);
        addMenuFiller(menu, 0);
        addMenuButton(menu, joinGlobal, 1);
        addMenuButton(menu, createGame, 2);
        addMenuButton(menu, joinGame, 3);
        addMenuButton(menu, campaign, 4);
        addMenuButton(menu, settings, 5);
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
        ipFieldJoin = YimoTheme.textField(18);
        yesButtonJoin = YimoTheme.accentButton("Join");
        noButtonJoin = YimoTheme.quietButton("Back");
        formCards.add(joinForm(), "join");

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
        return new String[] { "Join YIMO Lobby", "Create Practice Game", "Join Room", "Tutorial", "Settings" };
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

    private void row(JPanel grid, int y, String label, JTextField field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = y;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 10, 10);
        grid.add(YimoTheme.label(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = y;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(0, 0, 10, 0);
        grid.add(field, fieldConstraints);
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
        JPanel panel = formCard("DIRECT CONNECTION", "Join a known room");
        JPanel grid = formGrid();
        row(grid, 0, "Name", nameFieldJoin);
        row(grid, 1, "Address", ipFieldJoin);
        row(grid, 2, "Port", portFieldJoin);
        panel.add(grid, BorderLayout.CENTER);
        panel.add(formActions(yesButtonJoin, noButtonJoin), BorderLayout.SOUTH);
        return panel;
    }

    private void addActionListeners() {
        joinGlobal.addActionListener(this);
        createGame.addActionListener(this);
        joinGame.addActionListener(this);
        campaign.addActionListener(this);
        settings.addActionListener(this);
        nameFieldGlobal.addActionListener(this);
        yesButtonGlobal.addActionListener(this);
        noButtonGlobal.addActionListener(this);
        nameFieldCreate.addActionListener(this);
        portFieldCreate.addActionListener(this);
        yesButtonCreate.addActionListener(this);
        noButtonCreate.addActionListener(this);
        nameFieldJoin.addActionListener(this);
        portFieldJoin.addActionListener(this);
        ipFieldJoin.addActionListener(this);
        yesButtonJoin.addActionListener(this);
        noButtonJoin.addActionListener(this);
    }

    private void showMenu() {
        joinGlobalVisible = false;
        createVisible = false;
        joinVisible = false;
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
            ipFieldJoin.requestFocusInWindow();
        } else {
            showMenu();
        }
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
                } else if (source == yesButtonJoin || source == nameFieldJoin || source == ipFieldJoin || source == portFieldJoin) {
                    String name = playerName(nameFieldJoin);
                    String address = ipFieldJoin.getText() == null ? "" : ipFieldJoin.getText().trim();
                    if (name.length() == 0 || address.length() == 0) {
                        status("Enter a name and room address.", true);
                    } else {
                        status("Connecting to the room…", false);
						graphwar.joinGame(address, port(portFieldJoin), name);
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
            } else if (source == settings) {
                graphwar.getUI().setScreen(Constants.SETTINGS_SCREEN);
            }
        } catch (NumberFormatException error) {
            status("Port must be a number between 1 and 65535.", true);
        } catch (IOException error) {
            status("Connection failed. Check the address and try again.", true);
            error.printStackTrace();
        }
    }
}
