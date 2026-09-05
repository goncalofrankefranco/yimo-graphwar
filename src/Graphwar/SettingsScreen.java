//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import GraphServer.Constants;
import GraphServer.NetworkConfig;
import GraphServer.NetworkPreferences;

/** In-app client endpoint settings; no command line is needed for normal play. */
public final class SettingsScreen extends YimoScreen implements ActionListener {
    private final JTextField hostField = YimoTheme.textField(24);
    private final JTextField portField = YimoTheme.textField(8);
    private final JTextField apiField = YimoTheme.textField(24);
    private final JButton saveButton = YimoTheme.accentButton("Save settings");
    private final JButton resetButton = YimoTheme.button("Reset defaults");
    private final JButton backButton = YimoTheme.quietButton("Back");
    private final JLabel statusLabel = YimoTheme.mutedLabel("");

    public SettingsScreen(Graphwar graphwar) {
        super(graphwar);
        setLayout(new BorderLayout(20, 20));
        add(topBar("Settings"), BorderLayout.NORTH);

        JPanel card = YimoTheme.card();
        card.setLayout(new BorderLayout(0, 18));
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints heading = new GridBagConstraints();
        heading.gridx = 0;
        heading.gridy = 0;
        heading.gridwidth = 2;
        heading.anchor = GridBagConstraints.WEST;
        heading.insets = new Insets(0, 0, 8, 0);
        content.add(YimoTheme.title("YIMO connection"), heading);

        GridBagConstraints note = new GridBagConstraints();
        note.gridx = 0;
        note.gridy = 1;
        note.gridwidth = 2;
        note.anchor = GridBagConstraints.WEST;
        note.insets = new Insets(0, 0, 18, 0);
        content.add(YimoTheme.mutedLabel("These values are saved on this computer and used the next time you connect."), note);

        addRow(content, 2, "Lobby host", hostField);
        addRow(content, 3, "Lobby port", portField);
        addRow(content, 4, "Tournament API", apiField);

        GridBagConstraints build = new GridBagConstraints();
        build.gridx = 0;
        build.gridy = 5;
        build.gridwidth = 2;
        build.anchor = GridBagConstraints.WEST;
        build.insets = new Insets(12, 0, 0, 0);
        content.add(YimoTheme.mutedLabel("Build " + Constants.BUILD_ID + "  •  Protocol " + Constants.PROTOCOL_VERSION), build);

        card.add(content, BorderLayout.NORTH);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(backButton);
        actions.add(resetButton);
        actions.add(saveButton);
        card.add(actions, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
        add(statusLabel, BorderLayout.SOUTH);
        saveButton.addActionListener(this);
        resetButton.addActionListener(this);
        backButton.addActionListener(this);
        refresh();
    }

    private void addRow(JPanel panel, int row, String label, JTextField field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, 0, 12, 18);
        panel.add(YimoTheme.label(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(0, 0, 12, 0);
        panel.add(field, fieldConstraints);
    }

    public void refresh() {
        hostField.setText(Constants.GLOBAL_IP);
        portField.setText(Integer.toString(Constants.GLOBAL_PORT));
        apiField.setText(Constants.TOURNAMENT_API_BASE_URL);
    }

    private void status(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? YimoTheme.DANGER : YimoTheme.MINT);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();
        if (source == backButton) {
            graphwar.getUI().setScreen(Constants.MAIN_MENU_SCREEN);
            return;
        }
        try {
            if (source == resetButton) {
                NetworkPreferences.clear(NetworkPreferences.userNode());
                Constants.applyNetworkConfig(NetworkConfig.load());
                refresh();
                status("Defaults restored.", false);
            } else if (source == saveButton) {
                NetworkConfig config = NetworkPreferences.fromFields(NetworkConfig.load(), hostField.getText(),
                        portField.getText(), apiField.getText());
                NetworkPreferences.save(NetworkPreferences.userNode(), config);
                Constants.applyNetworkConfig(config);
                refresh();
                status("Saved. New connections will use these settings.", false);
            }
        } catch (IllegalArgumentException error) {
            status(error.getMessage() == null ? "Enter valid connection settings." : error.getMessage(), true);
        } catch (IllegalStateException error) {
            status("Could not save settings on this computer.", true);
        }
    }
}
