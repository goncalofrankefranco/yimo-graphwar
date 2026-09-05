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
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import GraphServer.Constants;

/** Responsive game screen. The logical game plane remains 770×450 and scales uniformly. */
public class GameScreen extends YimoScreen implements ActionListener, StartStopPanel, KeyListener {
    private final JButton fire;
    private final JButton quit;
    private final JButton global;
    private final JTextField funcField;
    private final JTextField chatField;
    private final GraphTextBox chatBox;
    private final GraphPlane plane;
    private final GraphTimer timer;
    private final GraphAngleDisplay angleDisplay;
    private final JLabel modeLabel;

    private final JButton yesQuit;
    private final JButton noQuit;
    private final JButton okButton;
    private final JLabel messageLabel;
    private final JPanel contentCards;
    private final java.awt.CardLayout contentLayout;
    private boolean quitVisible;
    private boolean showMessageVisible;

    public GameScreen(Graphwar graphwar, String confFile) throws Exception {
        super(graphwar);
        setLayout(new BorderLayout(14, 14));

        fire = YimoTheme.accentButton("Fire");
        quit = YimoTheme.quietButton("Leave Match");
        global = YimoTheme.quietButton("Lobby");
        funcField = YimoTheme.textField(22);
        chatField = YimoTheme.textField(22);
        chatBox = new GraphTextBox();
        plane = new GraphPlane(graphwar);
        plane.setPreferredSize(new Dimension(Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT));
        plane.setBackground(Color.WHITE);
        timer = new GraphTimer(graphwar);
        timer.setPreferredSize(new Dimension(92, 34));
        angleDisplay = new GraphAngleDisplay(graphwar);
        angleDisplay.setPreferredSize(new Dimension(205, 104));
        modeLabel = YimoTheme.mutedLabel("Normal functions");

        yesQuit = YimoTheme.accentButton("Leave Match");
        noQuit = YimoTheme.quietButton("Keep Playing");
        okButton = YimoTheme.accentButton("Continue");
        messageLabel = YimoTheme.centered("");
        messageLabel.setFont(YimoTheme.HEADING);
        messageLabel.setForeground(YimoTheme.GOLD);

        add(topBar("Match"), BorderLayout.NORTH);

        contentLayout = new java.awt.CardLayout();
        contentCards = new JPanel(contentLayout);
        contentCards.setOpaque(false);
        contentCards.add(buildMain(), "main");
        contentCards.add(buildQuit(), "quit");
        contentCards.add(buildMessage(), "message");
        add(contentCards, BorderLayout.CENTER);

        addListeners();
        global.setVisible(false);
        contentLayout.show(contentCards, "main");
    }

    private JPanel buildMain() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        JPanel boardCard = YimoTheme.card();
        boardCard.setLayout(new BorderLayout(0, 8));
        JPanel boardHeader = new JPanel(new BorderLayout());
        boardHeader.setOpaque(false);
        boardHeader.add(YimoTheme.title("Battlefield"), BorderLayout.WEST);
        boardCard.add(boardHeader, BorderLayout.NORTH);
        JPanel boardSurface = new JPanel(new BorderLayout());
		boardSurface.setBackground(YimoTheme.TEXT);
		boardSurface.add(plane, BorderLayout.CENTER);
        boardCard.add(boardSurface, BorderLayout.CENTER);

        JPanel controls = YimoTheme.card();
        controls.setLayout(new BorderLayout(0, 12));
        controls.setPreferredSize(new Dimension(292, 0));

        JPanel turn = new JPanel(new BorderLayout(8, 0));
        turn.setOpaque(false);
        JPanel turnCopy = new JPanel();
        turnCopy.setOpaque(false);
        turnCopy.setLayout(new BoxLayout(turnCopy, BoxLayout.Y_AXIS));
        turnCopy.add(YimoTheme.sectionTitle("TURN STATUS"));
        turnCopy.add(modeLabel);
        turn.add(turnCopy, BorderLayout.CENTER);
        turn.add(timer, BorderLayout.EAST);
        controls.add(turn, BorderLayout.NORTH);

        JPanel controlStack = new JPanel();
        controlStack.setOpaque(false);
        controlStack.setLayout(new BoxLayout(controlStack, BoxLayout.Y_AXIS));
        controlStack.add(YimoTheme.sectionTitle("FUNCTION"));
        controlStack.add(Box.createVerticalStrut(6));
        controlStack.add(funcField);
        controlStack.add(Box.createVerticalStrut(8));
        controlStack.add(fire);
        controlStack.add(Box.createVerticalStrut(12));
        controlStack.add(YimoTheme.sectionTitle("ANGLE"));
        controlStack.add(Box.createVerticalStrut(4));
        JPanel angleWrap = new JPanel(new GridBagLayout());
        angleWrap.setOpaque(false);
        angleWrap.add(angleDisplay);
        controlStack.add(angleWrap);
        controlStack.add(Box.createVerticalStrut(12));
        controlStack.add(YimoTheme.sectionTitle("CHAT"));
        controlStack.add(Box.createVerticalStrut(4));
        chatBox.setPreferredSize(new Dimension(260, 86));
        controlStack.add(chatBox);
        controlStack.add(Box.createVerticalStrut(6));
        controlStack.add(chatField);
        JScrollPane controlScroll = YimoTheme.scroll(controlStack);
        controlScroll.setBorder(BorderFactory.createEmptyBorder());
        controls.add(controlScroll, BorderLayout.CENTER);

        JPanel controlActions = new JPanel(new GridBagLayout());
        controlActions.setOpaque(false);
        GridBagConstraints globalConstraints = new GridBagConstraints();
        globalConstraints.gridx = 0;
        globalConstraints.gridy = 0;
        globalConstraints.weightx = 1.0;
        globalConstraints.fill = GridBagConstraints.HORIZONTAL;
        globalConstraints.insets = new Insets(0, 0, 6, 0);
        controlActions.add(global, globalConstraints);
        GridBagConstraints quitConstraints = new GridBagConstraints();
        quitConstraints.gridx = 0;
        quitConstraints.gridy = 1;
        quitConstraints.weightx = 1.0;
        quitConstraints.fill = GridBagConstraints.HORIZONTAL;
        controlActions.add(quit, quitConstraints);
        controls.add(controlActions, BorderLayout.SOUTH);

        GridBagConstraints boardConstraints = new GridBagConstraints();
        boardConstraints.gridx = 0;
        boardConstraints.gridy = 0;
        boardConstraints.weightx = 1.0;
        boardConstraints.weighty = 1.0;
        boardConstraints.fill = GridBagConstraints.BOTH;
        boardConstraints.insets = new Insets(0, 0, 0, 7);
        root.add(boardCard, boardConstraints);
        GridBagConstraints controlConstraints = new GridBagConstraints();
        controlConstraints.gridx = 1;
        controlConstraints.gridy = 0;
        controlConstraints.weightx = 0.0;
        controlConstraints.weighty = 1.0;
        controlConstraints.fill = GridBagConstraints.VERTICAL;
        controlConstraints.anchor = GridBagConstraints.NORTH;
        controlConstraints.insets = new Insets(0, 7, 0, 0);
        root.add(controls, controlConstraints);
        return root;
    }

    private JPanel buildQuit() {
        JPanel root = centeredCard("PAUSE", "Leave this match?", "");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actions.setOpaque(false);
        actions.add(noQuit);
        actions.add(yesQuit);
        ((JPanel) root.getComponent(0)).add(actions, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildMessage() {
        JPanel root = centeredCard("MATCH STATUS", "", "");
        JPanel card = (JPanel) root.getComponent(0);
        card.add(messageLabel, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actions.setOpaque(false);
        actions.add(okButton);
        card.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private JPanel centeredCard(String eyebrow, String title, String subtitle) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
        JPanel card = YimoTheme.card();
        card.setLayout(new BorderLayout(0, 14));
        card.add(YimoTheme.sectionTitle(eyebrow), BorderLayout.NORTH);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        if (title.length() > 0) {
            copy.add(YimoTheme.centered(title));
        }
        if (subtitle.length() > 0) {
            copy.add(Box.createVerticalStrut(7));
            copy.add(YimoTheme.centered(subtitle));
        }
        card.add(copy, BorderLayout.CENTER);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        root.add(card, constraints);
        return root;
    }

    private void addListeners() {
        fire.addActionListener(this);
        quit.addActionListener(this);
        global.addActionListener(this);
        funcField.addActionListener(this);
        chatField.addActionListener(this);
        yesQuit.addActionListener(this);
        noQuit.addActionListener(this);
        okButton.addActionListener(this);
        funcField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) {
                requestPreview();
            }

            public void removeUpdate(DocumentEvent event) {
                requestPreview();
            }

            public void changedUpdate(DocumentEvent event) {
                requestPreview();
            }
        });
        funcField.addKeyListener(this);
        chatField.addKeyListener(this);
        addKeyListener(this);
        setFocusable(true);
        plane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                requestFocusInWindow();
            }
        });
    }

    public void setNextMarker(boolean marker) {
        plane.setNextMarker(marker);
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

    public void addChat(Player player, String chatMessage) {
        chatBox.addText(player == null ? null : player.getName(), player == null ? null : player.getColor(), chatMessage);
    }

    public void showMessage(final String message) {
        Runnable update = new Runnable() {
            public void run() {
                messageLabel.setText(message == null ? "" : message);
                showShowMessage(true);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    public void refreshBack() {
        plane.refreshBackground();
    }

    public void refreshSoldiers() {
        plane.refreshSoldiers();
    }

    public void refreshFunction() {
        final Player player = graphwar.getGameData().getCurrentTurnPlayer();
        if (player != null && player.isLocalPlayer() && !(player instanceof ComputerPlayer)) {
            final String function = player.getCurrentTurnSoldier().getFunction();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    funcField.setEnabled(true);
                    funcField.setText(function);
                    requestPreview();
                }
            });
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    funcField.setEnabled(false);
                    plane.setPreviewFunction(null);
                }
            });
        }
    }

    public boolean isShowMessageVisible() {
        return showMessageVisible;
    }

    public boolean isQuitVisible() {
        return quitVisible;
    }

    private void showQuit(boolean show) {
        quitVisible = show;
        contentLayout.show(contentCards, show ? "quit" : "main");
        revalidate();
        repaint();
    }

    private void showShowMessage(boolean show) {
        showMessageVisible = show;
        contentLayout.show(contentCards, show ? "message" : "main");
        okButton.setVisible(show);
        revalidate();
        repaint();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();
        if (quitVisible) {
            if (source == noQuit) {
                showQuit(false);
                plane.refreshBackground();
            } else if (source == yesQuit) {
                graphwar.getGameData().disconnect();
                graphwar.getGlobalClient().closeRoom();
                showQuit(false);
            }
            return;
        }
        if (showMessageVisible) {
            if (source == okButton) {
                ((GlobalScreen) graphwar.getUI().getScreen(Constants.GLOBAL_ROOM_SCREEN)).refreshGameButton();
                ((PreGameScreen) graphwar.getUI().getScreen(Constants.PRE_GAME_SCREEN)).restartScreen();
                graphwar.getUI().setScreen(graphwar.getGlobalClient().isRunning()
                        ? Constants.GLOBAL_ROOM_SCREEN : Constants.MAIN_MENU_SCREEN);
                graphwar.finishGame();
                showShowMessage(false);
            }
            return;
        }

        if (source == quit) {
            showQuit(true);
        } else if (source == chatField) {
            String text = chatField.getText();
            if (text != null && text.trim().length() > 0) {
                graphwar.getGameData().sendChatMessage(text);
                chatField.setText("");
            }
        } else if (source == fire || source == funcField) {
            Player current = graphwar.getGameData().getCurrentTurnPlayer();
            if (current != null && !(current instanceof ComputerPlayer)) {
                String function = funcField.getText();
                if (function != null && function.trim().length() > 0) {
                    plane.setPreviewFunction(null);
                    graphwar.getGameData().sendFunction(function);
                }
            }
        } else if (source == global) {
            graphwar.getUI().setScreen(Constants.GLOBAL_ROOM_SCREEN);
        }
    }

    public void startDrawingFunction() {
        plane.setPreviewFunction(null);
        plane.startDrawingFunction();
    }

    private void requestPreview() {
        if (funcField == null || graphwar.getGameData() == null) {
            return;
        }
        final String text = funcField.getText();
        Thread previewThread = new Thread(new Runnable() {
            public void run() {
                final Function preview = graphwar.getGameData().buildPreviewFunction(text);
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        plane.setPreviewFunction(preview);
                    }
                });
            }
        }, "YIMO-preview");
        previewThread.setDaemon(true);
        previewThread.start();
    }

    public void repaintAngle() {
        angleDisplay.repaint();
    }

    private void showFuncType() {
        String text = "Normal functions";
        if (graphwar.getGameData().getGameMode() == Constants.FST_ODE) {
            text = "First-order differential equations";
        } else if (graphwar.getGameData().getGameMode() == Constants.SND_ODE) {
            text = "Second-order differential equations";
        }
        modeLabel.setText(text);
    }

    public void startPanel() {
        showFuncType();
        plane.startAnimating();
        timer.startRunning();
    }

    public void stopPanel() {
        plane.stopAnimating();
        timer.stopRunning();
    }

    public void keyPressed(KeyEvent event) {
        if (graphwar.getGameData().getGameMode() == Constants.SND_ODE) {
            if (event.getKeyCode() == KeyEvent.VK_UP) {
                graphwar.getGameData().angleUp();
            } else if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                graphwar.getGameData().angleDown();
            }
        }
    }

    public void keyReleased(KeyEvent event) {
        if (graphwar.getGameData().getGameMode() == Constants.SND_ODE
                && (event.getKeyCode() == KeyEvent.VK_UP || event.getKeyCode() == KeyEvent.VK_DOWN)) {
            graphwar.getGameData().stopAngle();
        }
    }

    public void keyTyped(KeyEvent event) {
    }
}
