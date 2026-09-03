//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import GraphServer.Constants;

/** A layout-managed replacement for the fixed-width legacy player board. */
public final class YimoPlayerRoster extends JPanel {
    private final Graphwar graphwar;

    public YimoPlayerRoster(Graphwar graphwar) {
        this.graphwar = graphwar;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    }

    private void updateOnEdt(final Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    public void updateBoard() {
        updateOnEdt(new Runnable() {
            public void run() {
                removeAll();
                List<Player> players = graphwar.getGameData().getPlayers();
                if (players == null || players.isEmpty()) {
                    JLabel empty = YimoTheme.mutedLabel("Waiting for players…");
                    empty.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
                    add(empty);
                } else {
                    addTeam("TEAM 01  ·  MINT", Constants.TEAM1, players);
                    add(Box.createVerticalStrut(12));
                    addTeam("TEAM 02  ·  CORAL", Constants.TEAM2, players);
                }
                revalidate();
                repaint();
            }
        });
    }

    private void addTeam(String title, int team, List<Player> players) {
        JLabel teamTitle = YimoTheme.sectionTitle(title);
        teamTitle.setBorder(BorderFactory.createEmptyBorder(8, 4, 5, 4));
        add(teamTitle);
        boolean found = false;
        for (Player player : players) {
            if (player.getTeam() == team) {
                add(new PlayerRow(player));
                add(Box.createVerticalStrut(5));
                found = true;
            }
        }
        if (!found) {
            JLabel empty = YimoTheme.mutedLabel("No players yet");
            empty.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 4));
            add(empty);
        }
    }

    public void addPlayer(Player player) {
        updateBoard();
    }

    public void updatePlayer(Player player) {
        updateBoard();
    }

    public void removePlayer(Player player) {
        updateBoard();
    }

    public void restartPlayers() {
        updateOnEdt(new Runnable() {
            public void run() {
                removeAll();
                revalidate();
                repaint();
            }
        });
    }

    private final class PlayerRow extends JPanel {
        private final Player player;

        PlayerRow(Player player) {
            this.player = player;
            setLayout(new BorderLayout(8, 0));
            setOpaque(true);
            setBackground(YimoTheme.CARD);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(YimoTheme.CARD_BORDER),
                    BorderFactory.createEmptyBorder(7, 8, 7, 8)));
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));

            JPanel identity = new JPanel(new BorderLayout(8, 0));
            identity.setOpaque(false);
            JPanel dot = new JPanel();
            dot.setOpaque(true);
            dot.setBackground(player.getColor() == null ? YimoTheme.ORANGE : player.getColor());
            dot.setPreferredSize(new Dimension(7, 34));
            identity.add(dot, BorderLayout.WEST);

            JPanel copy = new JPanel(new GridLayout(2, 1));
            copy.setOpaque(false);
            JLabel name = YimoTheme.label(player.getName());
            name.setFont(new java.awt.Font("Sans", java.awt.Font.BOLD, 14));
            copy.add(name);
            copy.add(YimoTheme.mutedLabel((player.isLocalPlayer() ? "YOU" : "PLAYER")
                    + "  ·  " + player.getNumSoldiers() + " soldiers"
                    + (player.getReady() ? "  ·  READY" : "")));
            identity.add(copy, BorderLayout.CENTER);
            add(identity, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
            actions.setOpaque(false);
            JButton side = smallButton(player.getTeam() == Constants.TEAM1 ? "T1" : "T2");
            JButton minus = smallButton("−");
            JButton plus = smallButton("+");
            JButton remove = smallButton("×");

            boolean canManage = player.isLocalPlayer() || graphwar.getGameData().isLeader();
            side.setEnabled(canManage);
            remove.setEnabled(graphwar.getGameData().isLeader() || player.isLocalPlayer());
            minus.setEnabled(canManage && player.getNumSoldiers() > 1);
            plus.setEnabled(canManage && player.getNumSoldiers() < Constants.MAX_SOLDIERS_PER_PLAYER);

            side.addActionListener(event -> graphwar.getGameData().switchSide(player));
            minus.addActionListener(event -> graphwar.getGameData().removeSoldier(player));
            plus.addActionListener(event -> graphwar.getGameData().addSoldier(player));
            remove.addActionListener(event -> graphwar.getGameData().removePlayer(player));

            actions.add(side);
            actions.add(minus);
            actions.add(plus);
            actions.add(remove);
            add(actions, BorderLayout.EAST);
        }

        private JButton smallButton(String text) {
            JButton button = YimoTheme.quietButton(text);
            button.setFont(new java.awt.Font("Sans", java.awt.Font.BOLD, 12));
            button.setPreferredSize(new Dimension(30, 28));
            button.setMinimumSize(new Dimension(30, 28));
            return button;
        }
    }
}
