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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import GraphServer.Constants;

/** Responsive YIMO lobby screen. */
public class GlobalScreen extends YimoScreen implements ActionListener, StartStopPanel {
    private final JButton createButton;
    private final JButton gameRoomButton;
    private final JButton backButton;
    private final JTextField chatField;
    private final GraphTextBox chatBox;
    private final GlobalPlayerBoard playerBoard;
    private final RoomBoard roomBoard;

    private final JTextField nameFieldCreate;
    private final JTextField portFieldCreate;
    private final JButton yesButtonCreate;
    private final JButton noButtonCreate;
    private final JButton okButton;
    private final JLabel statusLabel;

    private final JPanel contentCards;
    private final java.awt.CardLayout contentLayout;
    private boolean createVisible;
    private boolean showMessageVisible;

    public GlobalScreen(Graphwar graphwar, String confFile) throws InterruptedException, IOException {
        super(graphwar);
        setLayout(new BorderLayout(16, 16));

        createButton = YimoTheme.accentButton("Create Room");
        gameRoomButton = YimoTheme.button("Open Room");
        backButton = YimoTheme.quietButton("Back");
        chatField = YimoTheme.textField(24);
        chatBox = new GraphTextBox();
        playerBoard = new GlobalPlayerBoard(graphwar, 260, 240);
        roomBoard = new RoomBoard(graphwar, 560, 240);

        nameFieldCreate = YimoTheme.textField(18);
        portFieldCreate = YimoTheme.textField(8);
        yesButtonCreate = YimoTheme.accentButton("Create");
        noButtonCreate = YimoTheme.quietButton("Cancel");
        okButton = YimoTheme.button("OK");
        okButton.setVisible(false);
        statusLabel = YimoTheme.mutedLabel("");

        add(topBar("Lobby"), BorderLayout.NORTH);

        contentLayout = new java.awt.CardLayout();
        contentCards = new JPanel(contentLayout);
        contentCards.setOpaque(false);
        contentCards.add(buildLobby(), "lobby");
        contentCards.add(buildCreate(), "create");
        add(contentCards, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.add(statusLabel, BorderLayout.CENTER);
        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerActions.setOpaque(false);
        footerActions.add(okButton);
        footerActions.add(backButton);
        footer.add(footerActions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        createButton.addActionListener(this);
        gameRoomButton.addActionListener(this);
        backButton.addActionListener(this);
        chatField.addActionListener(this);
        yesButtonCreate.addActionListener(this);
        noButtonCreate.addActionListener(this);
        nameFieldCreate.addActionListener(this);
        portFieldCreate.addActionListener(this);
        okButton.addActionListener(this);
        gameRoomButton.setVisible(false);
    }

    private JPanel buildLobby() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setOpaque(false);

        JPanel columns = new JPanel(new GridBagLayout());
        columns.setOpaque(false);

        JPanel rooms = YimoTheme.card();
        rooms.setLayout(new BorderLayout(0, 10));
        rooms.add(YimoTheme.title("Created rooms"), BorderLayout.NORTH);
        JScrollPane roomScroll = YimoTheme.scroll(roomBoard);
        roomScroll.setPreferredSize(new Dimension(560, 260));
        rooms.add(roomScroll, BorderLayout.CENTER);

        JPanel players = YimoTheme.card();
        players.setLayout(new BorderLayout(0, 10));
        players.add(YimoTheme.title("Online players"), BorderLayout.NORTH);
        JScrollPane playerScroll = YimoTheme.scroll(playerBoard);
        playerScroll.setPreferredSize(new Dimension(260, 260));
        players.add(playerScroll, BorderLayout.CENTER);

        GridBagConstraints roomConstraints = new GridBagConstraints();
        roomConstraints.gridx = 0;
        roomConstraints.gridy = 0;
        roomConstraints.weightx = 0.68;
        roomConstraints.weighty = 1.0;
        roomConstraints.fill = GridBagConstraints.BOTH;
        roomConstraints.insets = new Insets(0, 0, 0, 7);
        columns.add(rooms, roomConstraints);

        GridBagConstraints playerConstraints = new GridBagConstraints();
        playerConstraints.gridx = 1;
        playerConstraints.gridy = 0;
        playerConstraints.weightx = 0.32;
        playerConstraints.weighty = 1.0;
        playerConstraints.fill = GridBagConstraints.BOTH;
        playerConstraints.insets = new Insets(0, 7, 0, 0);
        columns.add(players, playerConstraints);
        root.add(columns, BorderLayout.CENTER);

        JPanel chat = YimoTheme.card();
        chat.setLayout(new BorderLayout(0, 8));
        chat.add(YimoTheme.title("Lobby chat"), BorderLayout.NORTH);
        chatBox.setPreferredSize(new Dimension(500, 150));
        chat.add(chatBox, BorderLayout.CENTER);
        chat.add(chatField, BorderLayout.SOUTH);

        JPanel actions = YimoTheme.card();
        actions.setLayout(new GridBagLayout());
        JPanel actionButtons = new JPanel();
        actionButtons.setOpaque(false);
        actionButtons.setLayout(new BoxLayout(actionButtons, BoxLayout.Y_AXIS));
        actionButtons.add(Box.createVerticalGlue());
        createButton.setAlignmentX(LEFT_ALIGNMENT);
        createButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        actionButtons.add(createButton);
        actionButtons.add(Box.createVerticalStrut(8));
        gameRoomButton.setAlignmentX(LEFT_ALIGNMENT);
        gameRoomButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        actionButtons.add(gameRoomButton);
        actionButtons.add(Box.createVerticalGlue());
        GridBagConstraints actionGroupConstraints = new GridBagConstraints();
        actionGroupConstraints.gridx = 0;
        actionGroupConstraints.gridy = 0;
        actionGroupConstraints.weightx = 1.0;
        actionGroupConstraints.weighty = 1.0;
        actionGroupConstraints.fill = GridBagConstraints.BOTH;
        actions.add(actionButtons, actionGroupConstraints);

        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setOpaque(false);
        GridBagConstraints chatConstraints = new GridBagConstraints();
        chatConstraints.gridx = 0;
        chatConstraints.gridy = 0;
        chatConstraints.weightx = 0.72;
        chatConstraints.weighty = 1.0;
        chatConstraints.fill = GridBagConstraints.BOTH;
        chatConstraints.insets = new Insets(0, 0, 0, 7);
        bottom.add(chat, chatConstraints);
        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.gridx = 1;
        actionConstraints.gridy = 0;
        actionConstraints.weightx = 0.28;
        actionConstraints.weighty = 1.0;
        actionConstraints.fill = GridBagConstraints.BOTH;
        actionConstraints.insets = new Insets(0, 7, 0, 0);
        bottom.add(actions, actionConstraints);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildCreate() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
        JPanel card = YimoTheme.card();
        card.setLayout(new BorderLayout(0, 18));
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(YimoTheme.title("Create a room"));
        card.add(heading, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        fieldRow(fields, 0, "Room name", nameFieldCreate);
        fieldRow(fields, 1, "Port", portFieldCreate);
        card.add(fields, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(noButtonCreate);
        actions.add(yesButtonCreate);
        card.add(actions, BorderLayout.SOUTH);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 0, 0);
        root.add(card, constraints);
        return root;
    }

    private void fieldRow(JPanel fields, int y, String label, JTextField field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = y;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 12, 12);
        fields.add(YimoTheme.label(label), labelConstraints);
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = y;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(0, 0, 12, 0);
        fields.add(field, fieldConstraints);
    }

    private void showCreateGame(boolean show) {
        createVisible = show;
        if (show) {
            nameFieldCreate.setText(graphwar.getGlobalClient().getLocalPlayerName() + "'s Room");
            portFieldCreate.setText(Integer.toString(Constants.DEFAULT_PORT));
            contentLayout.show(contentCards, "create");
            nameFieldCreate.requestFocusInWindow();
        } else {
            contentLayout.show(contentCards, "lobby");
        }
    }

    private void status(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? YimoTheme.DANGER : YimoTheme.MINT);
    }

    public void showMessage(String message) {
        status(message, message != null && (message.indexOf("Could") >= 0 || message.indexOf("Failed") >= 0));
    }

    public void showDisconnectMessage(String message) {
        status(message, true);
        showMessageVisible = true;
        okButton.setVisible(true);
    }

    public void refreshGameButton() {
        boolean visible = graphwar.getGameData().getGameState() != Constants.NONE;
        gameRoomButton.setVisible(visible);
        revalidate();
        repaint();
    }

    public void refreshPlayers() {
        playerBoard.resize();
        playerBoard.repaint();
    }

    public void refreshRooms() {
        roomBoard.repaint();
    }

    public void addChat(String playerName, String chatMessage) {
        chatBox.addText(playerName, Color.WHITE, chatMessage);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();
        try {
            if (createVisible) {
                if (source == noButtonCreate) {
                    showCreateGame(false);
                } else if (source == yesButtonCreate || source == nameFieldCreate || source == portFieldCreate) {
                    String name = nameFieldCreate.getText() == null ? "" : nameFieldCreate.getText().trim();
                    int port = Integer.parseInt(portFieldCreate.getText().trim());
                    if (port < 1 || port > 65535) {
                        throw new NumberFormatException("port");
                    }
                    if (name.length() == 0) {
                        status("Enter a room name first.", true);
                    } else {
                        status("Starting the practice room…", false);
                        graphwar.createGame(port);
                        graphwar.getGlobalClient().createRoom(name, port);
                        graphwar.getGameData().addPlayer(graphwar.getGlobalClient().getLocalPlayerName());
                        showCreateGame(false);
                    }
                }
                return;
            }
            if (showMessageVisible) {
                if (source == okButton) {
                    if (graphwar.getGameData().getGameState() == Constants.NONE) {
                        graphwar.getUI().setScreen(Constants.MAIN_MENU_SCREEN);
                        graphwar.finishGame();
                    } else if (graphwar.getGameData().getGameState() == Constants.PRE_GAME) {
                        graphwar.getUI().setScreen(Constants.PRE_GAME_SCREEN);
                    } else {
                        graphwar.getUI().setScreen(Constants.GAME_SCREEN);
                    }
                    showMessageVisible = false;
                    okButton.setVisible(false);
                }
                return;
            }

            if (source == chatField) {
                String text = chatField.getText();
                if (text != null && text.trim().length() > 0) {
                    graphwar.getGlobalClient().sendChatMessage(text);
                    chatField.setText("");
                }
            } else if (source == createButton && graphwar.getGameData().getGameState() == Constants.NONE) {
                showCreateGame(true);
            } else if (source == backButton) {
                graphwar.getGlobalClient().stop();
                if (graphwar.getGameData().getGameState() != Constants.NONE) {
                    graphwar.getGameData().disconnect();
                    graphwar.finishGame();
                }
                graphwar.getUI().setScreen(Constants.MAIN_MENU_SCREEN);
            } else if (source == gameRoomButton) {
                if (graphwar.getGameData().getGameState() == Constants.PRE_GAME) {
                    graphwar.getUI().setScreen(Constants.PRE_GAME_SCREEN);
                } else if (graphwar.getGameData().getGameState() == Constants.GAME) {
                    graphwar.getUI().setScreen(Constants.GAME_SCREEN);
                }
            }
        } catch (NumberFormatException error) {
            status("Port must be a number between 1 and 65535.", true);
        } catch (IOException error) {
            status("Could not create the room.", true);
            error.printStackTrace();
        }
    }

    public void startPanel() {
        showMessageVisible = false;
        okButton.setVisible(false);
        contentLayout.show(contentCards, "lobby");
    }

    public void stopPanel() {
    }
}
