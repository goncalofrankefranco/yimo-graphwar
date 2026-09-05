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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import GraphServer.Constants;
import GraphServer.MapShape;

/** Responsive room setup screen with the existing network/game actions. */
public class PreGameScreen extends YimoScreen implements ActionListener {
    private final JButton normalFuncButton;
    private final JButton firstFuncButton;
    private final JButton secondFuncButton;
    private final JButton back;
    private final JButton addLocalPlayer;
    private final JButton addPCPlayer;
    private final JButton readyOn;
    private final JButton readyOff;
    private final JButton global;
    private final JTextField chatField;
    private final GraphTextBox chatBox;
    private final YimoPlayerRoster playerBoard;

    private JPanel roomSettingsPanel;
    private JCheckBox previewCheckBox;
    private JComboBox<Integer> turnTimeComboBox;
    private JComboBox<String> trajectoryModeComboBox;
    private JButton mapEditorButton;
    private JLabel mapStatusLabel;
    private JLabel invalidStatusLabel;
    private JLabel modeSummaryLabel;
    private boolean updatingRoomSettings;

    private final JTextField nameFieldAddLocal;
    private final JButton yesButtonAddLocal;
    private final JButton noButtonAddLocal;
    private final JTextField nameFieldAddPC;
    private final JTextField levelFieldAddPC;
    private final JButton yesButtonAddPC;
    private final JButton noButtonAddPC;
    private final JButton okButton;
    private final JLabel messageLabel;
    private final JLabel statusLabel;

    private final JPanel contentCards;
    private final java.awt.CardLayout contentLayout;
    private boolean addLocalVisible;
    private boolean addPCVisible;
    private boolean showMessageVisible;

    public PreGameScreen(Graphwar graphwar, String confFile) throws InterruptedException, IOException {
        super(graphwar);
        setLayout(new BorderLayout(16, 16));

        normalFuncButton = YimoTheme.button("y");
        firstFuncButton = YimoTheme.button("y′");
        secondFuncButton = YimoTheme.button("y″");
        back = YimoTheme.quietButton("Leave room");
        addLocalPlayer = YimoTheme.button("Add local player");
        addPCPlayer = YimoTheme.button("Add CPU");
        readyOn = YimoTheme.accentButton("Ready");
        readyOff = YimoTheme.button("Mark ready");
        global = YimoTheme.quietButton("YIMO Lobby");
        chatField = YimoTheme.textField(24);
        chatBox = new GraphTextBox();
        playerBoard = new YimoPlayerRoster(graphwar);

        nameFieldAddLocal = YimoTheme.textField(18);
        yesButtonAddLocal = YimoTheme.accentButton("Add player");
        noButtonAddLocal = YimoTheme.quietButton("Cancel");
        nameFieldAddPC = YimoTheme.textField(18);
        levelFieldAddPC = YimoTheme.textField(8);
        yesButtonAddPC = YimoTheme.accentButton("Add CPU");
        noButtonAddPC = YimoTheme.quietButton("Cancel");
        okButton = YimoTheme.accentButton("Continue");
        messageLabel = YimoTheme.centered("");
        messageLabel.setFont(YimoTheme.HEADING);
        messageLabel.setForeground(YimoTheme.GOLD);
        statusLabel = YimoTheme.mutedLabel("");

        add(topBar("Match Room"), BorderLayout.NORTH);

        contentLayout = new java.awt.CardLayout();
        contentCards = new JPanel(contentLayout);
        contentCards.setOpaque(false);
        contentCards.add(buildMain(), "main");
        contentCards.add(buildAddLocal(), "add-local");
        contentCards.add(buildAddPC(), "add-pc");
        contentCards.add(buildMessage(), "message");
        add(contentCards, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(okButton, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        addListeners();
        okButton.setVisible(false);
        global.setVisible(false);
        readyOn.setVisible(false);
        setMode(Constants.NORMAL_FUNC);
        setRoomPreviewEnabled(true);
        setRoomTurnTime(Constants.DEFAULT_TURN_TIME);
        setRoomTrajectoryMode(Constants.SHOOTER_RELATIVE_TRAJECTORY);
        setRoomSettingsEditable(false);
        setReadyButtonOn(false);
        playerBoard.updateBoard();
    }

    private JPanel buildMain() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setOpaque(false);

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        navigation.setOpaque(false);
        navigation.add(global);
        navigation.add(back);
        root.add(navigation, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridBagLayout());
        columns.setOpaque(false);

        JPanel rosterCard = YimoTheme.card();
        rosterCard.setLayout(new BorderLayout(0, 10));
        JPanel rosterHeading = new JPanel(new BorderLayout(8, 0));
        rosterHeading.setOpaque(false);
        rosterHeading.add(YimoTheme.title("Players"), BorderLayout.WEST);
        JPanel rosterActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rosterActions.setOpaque(false);
        rosterActions.add(addLocalPlayer);
        rosterActions.add(addPCPlayer);
        rosterHeading.add(rosterActions, BorderLayout.EAST);
        rosterCard.add(rosterHeading, BorderLayout.NORTH);
        JScrollPane rosterScroll = YimoTheme.scroll(playerBoard);
        rosterScroll.setPreferredSize(new Dimension(500, 270));
        rosterCard.add(rosterScroll, BorderLayout.CENTER);

        invalidStatusLabel = YimoTheme.mutedLabel("");
        invalidStatusLabel.setForeground(YimoTheme.DANGER);
        rosterCard.add(invalidStatusLabel, BorderLayout.SOUTH);

        GridBagConstraints rosterConstraints = new GridBagConstraints();
        rosterConstraints.gridx = 0;
        rosterConstraints.gridy = 0;
        rosterConstraints.weightx = 0.60;
        rosterConstraints.weighty = 1.0;
        rosterConstraints.fill = GridBagConstraints.BOTH;
        rosterConstraints.insets = new Insets(0, 0, 0, 7);
        columns.add(rosterCard, rosterConstraints);

        roomSettingsPanel = createRoomSettingsPanel();
        GridBagConstraints settingsConstraints = new GridBagConstraints();
        settingsConstraints.gridx = 1;
        settingsConstraints.gridy = 0;
        settingsConstraints.weightx = 0.40;
        settingsConstraints.weighty = 1.0;
        settingsConstraints.fill = GridBagConstraints.BOTH;
        settingsConstraints.insets = new Insets(0, 7, 0, 0);
        columns.add(roomSettingsPanel, settingsConstraints);
        root.add(columns, BorderLayout.CENTER);

        JPanel chat = YimoTheme.card();
        chat.setLayout(new BorderLayout(0, 8));
        chat.add(YimoTheme.title("Room chat"), BorderLayout.NORTH);
        chatBox.setPreferredSize(new Dimension(500, 110));
        chat.add(chatBox, BorderLayout.CENTER);
        chat.add(chatField, BorderLayout.SOUTH);
        root.add(chat, BorderLayout.SOUTH);
        return root;
    }

    private JPanel createRoomSettingsPanel() {
        JPanel panel = YimoTheme.card();
        panel.setLayout(new BorderLayout(0, 12));
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(YimoTheme.title("Room rules"));
        modeSummaryLabel = YimoTheme.mutedLabel("Normal functions");
        heading.add(modeSummaryLabel);
        panel.add(heading, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);

        previewCheckBox = YimoTheme.checkBox("Preview trajectory before firing");
        addControl(controls, 0, previewCheckBox, 2);

        turnTimeComboBox = YimoTheme.combo(new Integer[] { 10, 20, 30, 45, 60, 90, 120, 180 });
        addLabeledControl(controls, 1, "Time to play", turnTimeComboBox);

        trajectoryModeComboBox = YimoTheme.combo(new String[] { "Shooter-relative", "Global graph" });
        addLabeledControl(controls, 2, "Trajectory", trajectoryModeComboBox);

        mapEditorButton = YimoTheme.button("Open map creator");
        mapStatusLabel = YimoTheme.mutedLabel("Random map");
        JPanel mapRow = new JPanel(new BorderLayout(8, 0));
        mapRow.setOpaque(false);
        mapRow.add(mapEditorButton, BorderLayout.WEST);
        mapRow.add(mapStatusLabel, BorderLayout.CENTER);
        addControl(controls, 3, mapRow, 2);

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        modeRow.setOpaque(false);
        modeRow.add(normalFuncButton);
        modeRow.add(firstFuncButton);
        modeRow.add(secondFuncButton);
        addControl(controls, 4, modeRow, 2);

        JPanel readyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        readyRow.setOpaque(false);
        readyRow.add(readyOff);
        readyRow.add(readyOn);
        addControl(controls, 5, readyRow, 2);
        JPanel centeredControls = new JPanel(new GridBagLayout());
        centeredControls.setOpaque(false);
        GridBagConstraints centeredConstraints = new GridBagConstraints();
        centeredConstraints.gridx = 0;
        centeredConstraints.gridy = 0;
        centeredConstraints.weightx = 1.0;
        centeredConstraints.weighty = 1.0;
        centeredConstraints.fill = GridBagConstraints.HORIZONTAL;
        centeredControls.add(controls, centeredConstraints);
        panel.add(centeredControls, BorderLayout.CENTER);

        return panel;
    }

    private void addControl(JPanel panel, int row, java.awt.Component component, int width) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = width;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 10, 0);
        panel.add(component, constraints);
    }

    private void addLabeledControl(JPanel panel, int row, String label, java.awt.Component component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0.45;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 10, 8);
        panel.add(YimoTheme.label(label), labelConstraints);

        GridBagConstraints componentConstraints = new GridBagConstraints();
        componentConstraints.gridx = 1;
        componentConstraints.gridy = row;
        componentConstraints.weightx = 0.55;
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.insets = new Insets(0, 0, 10, 0);
        panel.add(component, componentConstraints);
    }

    private JPanel buildAddLocal() {
        JPanel root = formRoot("ADD LOCAL PLAYER", "Add another human player to this practice room");
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, "Player name", nameFieldAddLocal);
        root.add(fields, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(noButtonAddLocal);
        actions.add(yesButtonAddLocal);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildAddPC() {
        JPanel root = formRoot("ADD CPU PLAYER", "Practice against a generated opponent");
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, "Player name", nameFieldAddPC);
        addField(fields, 1, "Skill level", levelFieldAddPC);
        root.add(fields, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(noButtonAddPC);
        actions.add(yesButtonAddPC);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private JPanel formRoot(String eyebrow, String title) {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);
        JPanel card = YimoTheme.card();
        card.setLayout(new BorderLayout(0, 16));
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(YimoTheme.title(title));
        card.add(heading, BorderLayout.NORTH);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        outer.add(card, constraints);
        return card;
    }

    private void addField(JPanel panel, int row, String label, JTextField field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 12, 10);
        panel.add(YimoTheme.label(label), labelConstraints);
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(0, 0, 12, 0);
        panel.add(field, fieldConstraints);
    }

    private JPanel buildMessage() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
        JPanel card = YimoTheme.card();
        card.setLayout(new BorderLayout(0, 20));
        card.add(YimoTheme.sectionTitle("ROOM STATUS"), BorderLayout.NORTH);
        card.add(messageLabel, BorderLayout.CENTER);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        root.add(card, constraints);
        return root;
    }

    private void addListeners() {
        normalFuncButton.addActionListener(this);
        firstFuncButton.addActionListener(this);
        secondFuncButton.addActionListener(this);
        back.addActionListener(this);
        addLocalPlayer.addActionListener(this);
        addPCPlayer.addActionListener(this);
        readyOn.addActionListener(this);
        readyOff.addActionListener(this);
        global.addActionListener(this);
        chatField.addActionListener(this);
        previewCheckBox.addActionListener(this);
        turnTimeComboBox.addActionListener(this);
        trajectoryModeComboBox.addActionListener(this);
        mapEditorButton.addActionListener(this);
        nameFieldAddLocal.addActionListener(this);
        yesButtonAddLocal.addActionListener(this);
        noButtonAddLocal.addActionListener(this);
        nameFieldAddPC.addActionListener(this);
        levelFieldAddPC.addActionListener(this);
        yesButtonAddPC.addActionListener(this);
        noButtonAddPC.addActionListener(this);
        okButton.addActionListener(this);
    }

    public void setRoomSettingsEditable(boolean editable) {
        if (previewCheckBox != null) {
            previewCheckBox.setEnabled(editable);
            turnTimeComboBox.setEnabled(editable);
            trajectoryModeComboBox.setEnabled(editable);
            mapEditorButton.setEnabled(editable);
            normalFuncButton.setEnabled(editable);
            firstFuncButton.setEnabled(editable);
            secondFuncButton.setEnabled(editable);
        }
    }

    public void setRoomPreviewEnabled(boolean enabled) {
        if (previewCheckBox == null) {
            return;
        }
        updatingRoomSettings = true;
        previewCheckBox.setSelected(enabled);
        updatingRoomSettings = false;
    }

    public void setRoomTurnTime(int milliseconds) {
        if (turnTimeComboBox == null) {
            return;
        }
        int seconds = milliseconds / 1000;
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < turnTimeComboBox.getItemCount(); i++) {
            int distance = Math.abs(turnTimeComboBox.getItemAt(i) - seconds);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        updatingRoomSettings = true;
        turnTimeComboBox.setSelectedIndex(bestIndex);
        updatingRoomSettings = false;
    }

    public void setRoomTrajectoryMode(int mode) {
        if (trajectoryModeComboBox == null) {
            return;
        }
        if (mode != Constants.GLOBAL_TRAJECTORY) {
            mode = Constants.SHOOTER_RELATIVE_TRAJECTORY;
        }
        updatingRoomSettings = true;
        trajectoryModeComboBox.setSelectedIndex(mode);
        updatingRoomSettings = false;
    }

    public void setCustomMap(MapShape[] shapes, boolean enabled) {
        if (mapStatusLabel != null) {
            mapStatusLabel.setText(enabled ? "Custom map: " + (shapes == null ? 0 : shapes.length) + " shapes" : "Random map");
        }
    }

    public void setMode(int mode) {
        JButton selected = normalFuncButton;
        String summary = "Normal functions";
        if (mode == Constants.FST_ODE) {
            selected = firstFuncButton;
            summary = "First-order differential equations";
        } else if (mode == Constants.SND_ODE) {
            selected = secondFuncButton;
            summary = "Second-order differential equations";
        }
        final JButton selectedButton = selected;
        final String selectedSummary = summary;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                styleModeButton(normalFuncButton, normalFuncButton == selectedButton);
                styleModeButton(firstFuncButton, firstFuncButton == selectedButton);
                styleModeButton(secondFuncButton, secondFuncButton == selectedButton);
                modeSummaryLabel.setText(selectedSummary);
            }
        });
    }

    private void styleModeButton(JButton button, boolean selected) {
        button.setBackground(selected ? new Color(29, 104, 111) : YimoTheme.CARD_LIGHT);
        button.setForeground(selected ? Color.WHITE : YimoTheme.TEXT);
    }

    static int modeForButton(int buttonIndex) {
        if (buttonIndex == 1) {
            return Constants.FST_ODE;
        }
        if (buttonIndex == 2) {
            return Constants.SND_ODE;
        }
        return Constants.NORMAL_FUNC;
    }

    private void showCard(final String card, final boolean message, final boolean local, final boolean pc) {
        Runnable update = new Runnable() {
            public void run() {
                contentLayout.show(contentCards, card);
                showMessageVisible = message;
                addLocalVisible = local;
                addPCVisible = pc;
                okButton.setVisible(message);
                revalidate();
                repaint();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void showAddLocal(boolean show) {
        if (show) {
            nameFieldAddLocal.requestFocusInWindow();
            showCard("add-local", false, true, false);
        } else {
            showCard("main", false, false, false);
        }
    }

    private void showAddPC(boolean show) {
        if (show) {
            nameFieldAddPC.requestFocusInWindow();
            showCard("add-pc", false, false, true);
        } else {
            showCard("main", false, false, false);
        }
    }

    private void showShowMessage(boolean show) {
        if (show) {
            showCard("message", true, false, false);
        } else {
            showCard("main", false, false, false);
        }
    }

    public void refreshGlobalButton() {
        Runnable update = new Runnable() {
            public void run() {
                global.setVisible(graphwar.getGlobalClient().isRunning());
                revalidate();
                repaint();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    public void addPlayer(Player player) {
        playerBoard.addPlayer(player);
    }

    public void updatePlayer(Player player) {
        playerBoard.updatePlayer(player);
    }

    public void removePlayer(Player player) {
        playerBoard.removePlayer(player);
    }

    public void refreshBoard() {
        playerBoard.updateBoard();
    }

    public void restartScreen() {
        chatBox.emptyText();
        playerBoard.restartPlayers();
        showCard("main", false, false, false);
    }

    public void showMessage(String message) {
        messageLabel.setText(message == null ? "" : message);
        showShowMessage(true);
    }

    public void addChat(Player player, String chatMessage) {
        String name = player == null ? null : player.getName();
        Color color = player == null ? null : player.getColor();
        chatBox.addText(name, color, chatMessage);
    }

    public void setReadyButtonOn(final boolean on) {
        Runnable update = new Runnable() {
            public void run() {
                readyOn.setVisible(on);
                readyOff.setVisible(!on);
                revalidate();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void addPlayerFromField() {
        String name = nameFieldAddLocal.getText() == null ? "" : nameFieldAddLocal.getText().trim();
        if (name.length() == 0) {
            return;
        }
        if (name.length() > 20) {
            nameFieldAddLocal.setText(name.substring(0, 20));
            return;
        }
        graphwar.getGameData().addPlayer(name);
        showAddLocal(false);
    }

    private void addPCFromField() {
        String name = nameFieldAddPC.getText() == null ? "" : nameFieldAddPC.getText().trim();
        if (name.length() == 0) {
            return;
        }
        if (name.length() > 20) {
            nameFieldAddPC.setText(name.substring(0, 20));
            return;
        }
        int level;
        try {
            level = "Over 9000".equalsIgnoreCase(levelFieldAddPC.getText().trim())
                    ? 9001 : Integer.parseInt(levelFieldAddPC.getText().trim());
        } catch (NumberFormatException error) {
            levelFieldAddPC.setText("Over 9000");
            return;
        }
        if (level > 9000) {
            levelFieldAddPC.setText("Over 9000");
            return;
        }
        graphwar.getGameData().addPC(name, level);
        showAddPC(false);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();
        if (addLocalVisible) {
            if (source == noButtonAddLocal) {
                showAddLocal(false);
            } else if (source == yesButtonAddLocal || source == nameFieldAddLocal) {
                addPlayerFromField();
            }
            return;
        }
        if (addPCVisible) {
            if (source == noButtonAddPC) {
                showAddPC(false);
            } else if (source == yesButtonAddPC || source == nameFieldAddPC || source == levelFieldAddPC) {
                addPCFromField();
            }
            return;
        }
        if (showMessageVisible) {
            if (source == okButton) {
                if (graphwar.getGlobalClient().isRunning()) {
                    graphwar.getUI().setScreen(Constants.GLOBAL_ROOM_SCREEN);
                } else {
                    graphwar.getUI().setScreen(Constants.MAIN_MENU_SCREEN);
                }
                graphwar.finishGame();
                showShowMessage(false);
            }
            return;
        }

        if (source == previewCheckBox || source == turnTimeComboBox || source == trajectoryModeComboBox) {
            if (!updatingRoomSettings && graphwar.getGameData().isLeader()) {
                graphwar.getGameData().setPreviewEnabled(previewCheckBox.isSelected());
                graphwar.getGameData().setTurnTime(((Integer) turnTimeComboBox.getSelectedItem()).intValue() * 1000);
                graphwar.getGameData().setTrajectoryMode(trajectoryModeComboBox.getSelectedIndex());
            }
        } else if (source == mapEditorButton && graphwar.getGameData().isLeader()) {
            MapEditorPanel.showDialog(graphwar, graphwar.getGameData().getCustomMap(), new MapEditorPanel.ApplyListener() {
                public void apply(MapShape[] shapes) {
                    graphwar.getGameData().setCustomMap(shapes);
                }
            });
        } else if (source == addLocalPlayer) {
            showAddLocal(true);
        } else if (source == addPCPlayer) {
            nameFieldAddPC.setText(Constants.computerNames[GraphUtil.random.nextInt(Constants.computerNames.length)]);
            int level = (int) (Constants.COMPUTER_LEVEL_MEAN_VALUE + Constants.COMPUTER_LEVEL_STANDARD_DEVIATION * GraphUtil.random.nextGaussian());
            levelFieldAddPC.setText(Integer.toString(Math.max(Constants.COMPUTER_LEVEL_MIN_VALUE, level)));
            showAddPC(true);
        } else if (source == chatField) {
            String text = chatField.getText();
            if (text != null && text.trim().length() > 0) {
                graphwar.getGameData().sendChatMessage(text);
                chatField.setText("");
            }
        } else if (source == normalFuncButton || source == firstFuncButton || source == secondFuncButton) {
            int buttonIndex = source == normalFuncButton ? 0 : source == firstFuncButton ? 1 : 2;
            graphwar.getGameData().setMode(modeForButton(buttonIndex));
        } else if (source == back) {
            graphwar.getGameData().disconnect();
            graphwar.getGlobalClient().closeRoom();
        } else if (source == readyOff || source == readyOn) {
            List<Player> players = graphwar.getGameData().getPlayers();
            for (Player player : players) {
                if (player.isLocalPlayer()) {
                    graphwar.getGameData().setReady(player, source == readyOff);
                }
            }
        } else if (source == global) {
            graphwar.getUI().setScreen(Constants.GLOBAL_ROOM_SCREEN);
        }
    }
}
