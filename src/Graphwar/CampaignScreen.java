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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;

import GraphServer.Constants;
import GraphServer.MapShape;

/** Offline YIMO campaign selector and lesson board. */
public final class CampaignScreen extends YimoScreen implements ActionListener {
    static final int SHOOTER_X = 140;
    static final int SHOOTER_Y = Constants.PLANE_HEIGHT / 2;

    private final CampaignLesson[] lessons;
    private final CampaignProgress progress;
    private final JPanel cards;
    private final java.awt.CardLayout cardLayout;
    private final JPanel lessonList = new JPanel();
    private final JButton[] lessonButtons;
    private final JButton startButton;
    private final JButton resetButton;
    private final JButton backButton;
    private final JButton fireButton;
    private final JButton retryButton;
    private final JButton lessonBackButton;
    private final JButton previousButton;
    private final JButton nextButton;
    private final JButton hintButton;
    private final JTextField functionField;
    private final JLabel lessonCountLabel;
    private final JLabel selectorTitleLabel;
    private final JLabel selectorObjectiveLabel;
    private final JLabel selectorModeLabel;
    private final JLabel lessonTitleLabel;
    private final JLabel lessonObjectiveLabel;
    private final JLabel lessonModeLabel;
    private final JLabel lessonStatusLabel;
    private final JLabel hintLabel;
    private final JTextArea instructionsArea;
    private final JTextArea lessonGuideArea;
    private final CampaignCanvas canvas;

    private CampaignLesson selectedLesson;
    private int selectedIndex = -1;

    public CampaignScreen(Graphwar graphwar) {
        super(graphwar);
        setLayout(new BorderLayout(16, 16));
        progress = new CampaignProgress();

        CampaignLesson[] loadedLessons;
        String loadError = null;
        try {
            loadedLessons = CampaignLesson.loadAll(CampaignScreen.class);
        } catch (Exception error) {
            loadedLessons = new CampaignLesson[0];
            loadError = error.getMessage() == null ? "Lesson files could not be loaded." : error.getMessage();
        }
        lessons = loadedLessons;
        lessonButtons = new JButton[lessons.length];

        startButton = YimoTheme.accentButton("Start Lesson");
        resetButton = YimoTheme.quietButton("Reset");
        backButton = YimoTheme.quietButton("Back");
        fireButton = YimoTheme.accentButton("Fire");
        retryButton = YimoTheme.button("Retry");
        lessonBackButton = YimoTheme.quietButton("Back to Lessons");
        previousButton = YimoTheme.quietButton("Previous Lesson");
        nextButton = YimoTheme.accentButton("Next Lesson");
        hintButton = YimoTheme.button("Hint");
        functionField = YimoTheme.textField(22);
        lessonCountLabel = YimoTheme.mutedLabel("");
        selectorTitleLabel = YimoTheme.title("");
        selectorObjectiveLabel = YimoTheme.mutedLabel("");
        selectorModeLabel = YimoTheme.mutedLabel("");
        lessonTitleLabel = YimoTheme.title("");
        lessonObjectiveLabel = YimoTheme.mutedLabel("");
        lessonModeLabel = YimoTheme.mutedLabel("");
        lessonStatusLabel = YimoTheme.mutedLabel("");
        hintLabel = YimoTheme.mutedLabel("");
        instructionsArea = textArea();
        lessonGuideArea = textArea();
        lessonGuideArea.setRows(5);
        lessonGuideArea.setColumns(20);
        lessonGuideArea.setPreferredSize(new Dimension(0, 112));
        lessonGuideArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        canvas = new CampaignCanvas();

        add(topBar("Tutorial"), BorderLayout.NORTH);
        cardLayout = new java.awt.CardLayout();
        cards = new JPanel(cardLayout);
        cards.setOpaque(false);
        cards.add(buildSelector(loadError), "selector");
        cards.add(buildLesson(), "lesson");
        add(cards, BorderLayout.CENTER);

        startButton.addActionListener(this);
        resetButton.addActionListener(this);
        backButton.addActionListener(this);
        fireButton.addActionListener(this);
        retryButton.addActionListener(this);
        lessonBackButton.addActionListener(this);
        previousButton.addActionListener(this);
        nextButton.addActionListener(this);
        hintButton.addActionListener(this);

        if (lessons.length > 0) {
            selectFirstUnlocked();
        }
    }

    private JPanel buildSelector(String loadError) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        JPanel listCard = YimoTheme.card();
        listCard.setLayout(new BorderLayout(0, 12));
        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.setOpaque(false);
        listHeader.add(YimoTheme.title("Lessons"), BorderLayout.WEST);
        listHeader.add(lessonCountLabel, BorderLayout.EAST);
        listCard.add(listHeader, BorderLayout.NORTH);

        lessonList.setOpaque(false);
        lessonList.setLayout(new BoxLayout(lessonList, BoxLayout.Y_AXIS));
        if (loadError != null) {
            JLabel error = YimoTheme.mutedLabel("Campaign unavailable");
            error.setForeground(YimoTheme.DANGER);
            lessonList.add(error);
            JLabel detail = YimoTheme.mutedLabel(loadError);
            detail.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
            lessonList.add(detail);
        } else {
            for (int i = 0; i < lessons.length; i++) {
                final int index = i;
                JButton lessonButton = YimoTheme.button(lessonLabel(i));
                lessonButton.setAlignmentX(LEFT_ALIGNMENT);
                lessonButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
                lessonButton.addActionListener(event -> selectLesson(index));
                lessonButtons[i] = lessonButton;
                lessonList.add(lessonButton);
                if (i + 1 < lessons.length) {
                    lessonList.add(Box.createVerticalStrut(7));
                }
            }
        }
        JScrollPane lessonScroll = YimoTheme.scroll(lessonList);
        lessonScroll.setBorder(BorderFactory.createEmptyBorder());
        listCard.add(lessonScroll, BorderLayout.CENTER);

        JPanel listActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        listActions.setOpaque(false);
        listActions.add(resetButton);
        listActions.add(backButton);
        listCard.add(listActions, BorderLayout.SOUTH);

        JPanel detailCard = YimoTheme.card();
        detailCard.setLayout(new BorderLayout(0, 16));
        detailCard.add(selectorTitleLabel, BorderLayout.NORTH);
        JPanel detailCopy = new JPanel();
        detailCopy.setOpaque(false);
        detailCopy.setLayout(new BoxLayout(detailCopy, BoxLayout.Y_AXIS));
        detailCopy.add(YimoTheme.sectionTitle("Instructions"));
        detailCopy.add(Box.createVerticalStrut(8));
        detailCopy.add(instructionsArea);
        detailCopy.add(Box.createVerticalStrut(18));
        detailCopy.add(YimoTheme.sectionTitle("Objective"));
        detailCopy.add(Box.createVerticalStrut(8));
        detailCopy.add(selectorObjectiveLabel);
        detailCopy.add(Box.createVerticalStrut(18));
        detailCopy.add(selectorModeLabel);
        detailCard.add(detailCopy, BorderLayout.CENTER);
        JPanel detailActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        detailActions.setOpaque(false);
        detailActions.add(startButton);
        detailCard.add(detailActions, BorderLayout.SOUTH);

        GridBagConstraints listConstraints = new GridBagConstraints();
        listConstraints.gridx = 0;
        listConstraints.gridy = 0;
        listConstraints.weightx = 0.42;
        listConstraints.weighty = 1.0;
        listConstraints.fill = GridBagConstraints.BOTH;
        listConstraints.insets = new Insets(0, 0, 0, 8);
        root.add(listCard, listConstraints);

        GridBagConstraints detailConstraints = new GridBagConstraints();
        detailConstraints.gridx = 1;
        detailConstraints.gridy = 0;
        detailConstraints.weightx = 0.58;
        detailConstraints.weighty = 1.0;
        detailConstraints.fill = GridBagConstraints.BOTH;
        detailConstraints.insets = new Insets(0, 8, 0, 0);
        root.add(detailCard, detailConstraints);
        return root;
    }

    private JPanel buildLesson() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        JPanel boardCard = YimoTheme.card();
        boardCard.setLayout(new BorderLayout(0, 8));
        boardCard.add(canvas, BorderLayout.CENTER);
        boardCard.setMinimumSize(new Dimension(0, 0));

        JPanel controls = YimoTheme.card();
        controls.setLayout(new BorderLayout(0, 12));
        controls.setMinimumSize(new Dimension(0, 0));
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(lessonTitleLabel);
        heading.add(lessonModeLabel);
        controls.add(heading, BorderLayout.NORTH);

        JPanel controlStack = new JPanel();
        controlStack.setOpaque(false);
        controlStack.setLayout(new BoxLayout(controlStack, BoxLayout.Y_AXIS));
        controlStack.add(YimoTheme.sectionTitle("Function"));
        controlStack.add(Box.createVerticalStrut(7));
        controlStack.add(functionField);
        controlStack.add(Box.createVerticalStrut(9));
        controlStack.add(fireButton);
        controlStack.add(Box.createVerticalStrut(18));
        controlStack.add(YimoTheme.sectionTitle("How to build it"));
        controlStack.add(Box.createVerticalStrut(7));
        controlStack.add(lessonGuideArea);
        controlStack.add(Box.createVerticalStrut(18));
        controlStack.add(YimoTheme.sectionTitle("Objective"));
        controlStack.add(Box.createVerticalStrut(7));
        controlStack.add(lessonObjectiveLabel);
        controlStack.add(Box.createVerticalStrut(18));
        controlStack.add(hintButton);
        hintLabel.setVisible(false);
        controlStack.add(Box.createVerticalStrut(7));
        controlStack.add(hintLabel);
        controlStack.add(Box.createVerticalGlue());
        lessonStatusLabel.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        controlStack.add(lessonStatusLabel);
        JScrollPane controlScroll = YimoTheme.scroll(controlStack);
        controlScroll.setBorder(BorderFactory.createEmptyBorder());
        controls.add(controlScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 1, 8, 8));
        actions.setOpaque(false);
        actions.add(lessonBackButton);
        actions.add(previousButton);
        actions.add(retryButton);
        actions.add(nextButton);
        controls.add(actions, BorderLayout.SOUTH);

        GridBagConstraints boardConstraints = new GridBagConstraints();
        boardConstraints.gridx = 0;
        boardConstraints.gridy = 0;
        boardConstraints.weightx = 0.72;
        boardConstraints.weighty = 1.0;
        boardConstraints.fill = GridBagConstraints.BOTH;
        boardConstraints.insets = new Insets(0, 0, 0, 8);
        root.add(boardCard, boardConstraints);

        GridBagConstraints controlConstraints = new GridBagConstraints();
        controlConstraints.gridx = 1;
        controlConstraints.gridy = 0;
        controlConstraints.weightx = 0.28;
        controlConstraints.weighty = 1.0;
        controlConstraints.fill = GridBagConstraints.BOTH;
        controlConstraints.insets = new Insets(0, 8, 0, 0);
        root.add(controls, controlConstraints);
        return root;
    }

    private JTextArea textArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setFont(YimoTheme.BODY);
        area.setForeground(YimoTheme.TEXT);
        return area;
    }

    private String lessonLabel(int index) {
        return String.format("%02d  %s", index + 1, lessons[index].getTitle());
    }

    static Color pointColor(int team) {
        return team == Constants.TEAM1 ? YimoTheme.PLAYER_BLUE : YimoTheme.OPPONENT_RED;
    }

    static int animationSteps(int totalSteps, long elapsedMillis) {
        if (totalSteps <= 0 || elapsedMillis <= 0) {
            return 0;
        }
        long steps = elapsedMillis * Constants.FUNCTION_VELOCITY / 1000L;
        return (int) Math.min(totalSteps, Math.max(1L, steps));
    }

    static int eliminationFrame(long elapsedMillis) {
        if (elapsedMillis <= 0) {
            return 0;
        }
        return Math.min(5, (int) (elapsedMillis / 60L));
    }

    static String startingExpression(CampaignLesson lesson) {
        return "";
    }

    private void selectFirstUnlocked() {
        for (int i = 0; i < lessons.length; i++) {
            if (progress.isUnlocked(lessons, i)) {
                selectLesson(i);
                return;
            }
        }
        selectLesson(0);
    }

    private void selectLesson(int index) {
        if (index < 0 || index >= lessons.length || !progress.isUnlocked(lessons, index)) {
            return;
        }
        selectedIndex = index;
        selectedLesson = lessons[index];
        String displayTitle = String.format("%02d  %s", index + 1, selectedLesson.getTitle());
        selectorTitleLabel.setText(displayTitle);
        lessonTitleLabel.setText(displayTitle);
        instructionsArea.setText(selectedLesson.getInstructions());
        lessonGuideArea.setText(selectedLesson.getGuide());
        selectorObjectiveLabel.setText(selectedLesson.getObjective());
        lessonObjectiveLabel.setText(selectedLesson.getObjective());
        String displayMode = modeText(selectedLesson);
        selectorModeLabel.setText(displayMode);
        lessonModeLabel.setText(displayMode);
        lessonCountLabel.setText(progress.completedCount(lessons) + " / " + lessons.length);
        startButton.setEnabled(true);
        refreshLessonButtons();
        updateNavigationButtons();
    }

    private String modeText(CampaignLesson lesson) {
        String mode = "Function";
        if (lesson.getMode() == Constants.FST_ODE) {
            mode = "First-order ODE";
        } else if (lesson.getMode() == Constants.SND_ODE) {
            mode = "Second-order ODE";
        }
        return mode + (lesson.getTrajectory() == Constants.GLOBAL_TRAJECTORY ? "  ·  Global graph" : "  ·  Shooter-relative");
    }

    private void refreshLessonButtons() {
        lessonCountLabel.setText(progress.completedCount(lessons) + " / " + lessons.length);
        for (int i = 0; i < lessonButtons.length; i++) {
            if (lessonButtons[i] == null) {
                continue;
            }
            lessonButtons[i].setEnabled(progress.isUnlocked(lessons, i));
            lessonButtons[i].setText((progress.isComplete(lessons[i].getId()) ? "✓  " : "") + lessonLabel(i));
        }
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        previousButton.setEnabled(selectedIndex > 0 && progress.isUnlocked(lessons, selectedIndex - 1));
        nextButton.setEnabled(selectedIndex >= 0 && selectedIndex + 1 < lessons.length
                && progress.isUnlocked(lessons, selectedIndex + 1));
    }

    private void startLesson() {
        if (selectedLesson == null) {
            return;
        }
        functionField.setText(startingExpression(selectedLesson));
        functionField.setToolTipText("Build your function using the guide, then fire.");
        hintLabel.setText(selectedLesson.getHint());
        hintLabel.setVisible(false);
        hintButton.setText("Hint");
        lessonStatusLabel.setText("");
        lessonStatusLabel.setForeground(YimoTheme.MUTED);
        canvas.setLesson(selectedLesson);
        updateTrajectory(false);
        cardLayout.show(cards, "lesson");
        revalidate();
        repaint();
    }

    private void updateTrajectory(boolean fired) {
        if (selectedLesson == null) {
            return;
        }
        String expression = functionField.getText();
        if (expression == null || expression.trim().length() == 0) {
            canvas.setFunction(null, false);
            if (!fired) {
                lessonStatusLabel.setText("Build your function, then Fire");
                lessonStatusLabel.setForeground(YimoTheme.MUTED);
            } else {
                lessonStatusLabel.setText("Enter a function first");
                lessonStatusLabel.setForeground(YimoTheme.DANGER);
            }
            return;
        }
        try {
            Function trajectory = simulate(selectedLesson, expression);
            canvas.setFunction(trajectory, fired);
            if (fired) {
                if (trajectory.getNumPlayersHit() > 0) {
                    progress.markComplete(selectedLesson.getId());
                    lessonStatusLabel.setText("Lesson complete");
                    lessonStatusLabel.setForeground(YimoTheme.MINT);
                    refreshLessonButtons();
                } else {
                    lessonStatusLabel.setText("Target not reached");
                    lessonStatusLabel.setForeground(YimoTheme.DANGER);
                }
            }
        } catch (MalformedFunction error) {
            canvas.setFunction(null, fired);
            lessonStatusLabel.setText("Enter a valid function");
            lessonStatusLabel.setForeground(YimoTheme.DANGER);
        }
    }

    /** Uses the same trajectory and collision implementation as a normal shot. */
    static Function simulate(CampaignLesson lesson, String expression) throws MalformedFunction {
        if (lesson == null) {
            throw new MalformedFunction();
        }
        MapShape[] shapes = lesson.getShapes();
        Obstacle obstacle = new Obstacle(shapes.length, shapes);
        Player shooter = new Player("You", 0, Constants.TEAM1, true, 1, true);
        shooter.startSoldier(0, SHOOTER_X, SHOOTER_Y);
        Player target = new Player("Target", 1, Constants.TEAM2, false, 1, false);
        target.startSoldier(0, lesson.getTargetX(), lesson.getTargetY());
        Player[] players = new Player[] { shooter, target };
        Function trajectory = new Function(expression);

        if (lesson.getTrajectory() == Constants.GLOBAL_TRAJECTORY) {
            trajectory.processGlobalRange(obstacle, players, players.length, 0, lesson.getMode());
        } else if (lesson.getMode() == Constants.FST_ODE) {
            trajectory.processRK4Range(obstacle, players, players.length, 0, false);
        } else if (lesson.getMode() == Constants.SND_ODE) {
            trajectory.processRK42Range(obstacle, players, players.length, 0, 0.0, false);
        } else {
            trajectory.processFunctionRange(obstacle, players, players.length, 0, false);
        }
        return trajectory;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();
        if (source == startButton) {
            startLesson();
        } else if (source == backButton || source == lessonBackButton) {
            if (source == lessonBackButton) {
                cardLayout.show(cards, "selector");
            } else {
                graphwar.getUI().setScreen(Constants.MAIN_MENU_SCREEN);
            }
        } else if (source == previousButton) {
            selectLesson(selectedIndex - 1);
            startLesson();
        } else if (source == nextButton) {
            selectLesson(selectedIndex + 1);
            startLesson();
        } else if (source == resetButton) {
            progress.reset();
            refreshLessonButtons();
            if (lessons.length > 0) {
                selectLesson(0);
            }
        } else if (source == fireButton) {
            updateTrajectory(true);
        } else if (source == retryButton) {
            startLesson();
        } else if (source == hintButton) {
            boolean visible = !hintLabel.isVisible();
            hintLabel.setVisible(visible);
            hintButton.setText(visible ? "Hide Hint" : "Hint");
            revalidate();
        }
    }

    public void startPanel() {
        refreshLessonButtons();
        cardLayout.show(cards, "selector");
    }

    public void stopPanel() {
    }

    private static final class CampaignCanvas extends JPanel {
        private CampaignLesson lesson;
        private Function function;
        private boolean fired;
        private int visibleSteps;
        private long animationStartedAt;
        private long impactStartedAt;
        private boolean impact;
        private boolean targetEliminated;
        private long targetEliminatedAt;
        private final Timer animationTimer;
        private final Image[] explosionImages;
        private final Image[] deathImages;
        private final int[] explosionDurations;

        CampaignCanvas() {
            setOpaque(true);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT));
            setMinimumSize(new Dimension(0, 0));
            setBorder(BorderFactory.createLineBorder(YimoTheme.CARD_BORDER));
            explosionImages = loadExplosionImages();
            deathImages = loadDeathImages();
            explosionDurations = new int[] { 15, 15, 15, 15, 15, 15 };
            animationTimer = new Timer(30, event -> advanceAnimation());
            animationTimer.setCoalesce(true);
        }

        void setLesson(CampaignLesson lesson) {
            animationTimer.stop();
            this.lesson = lesson;
            this.function = null;
            this.fired = false;
            this.visibleSteps = 0;
            this.impact = false;
            this.targetEliminated = false;
            repaint();
        }

        void setFunction(Function function, boolean fired) {
            animationTimer.stop();
            this.function = function;
            this.fired = fired;
            this.impact = false;
            this.targetEliminated = false;
            this.visibleSteps = function == null ? 0 : (fired ? 0 : function.getNumSteps());
            if (fired && function != null && function.getNumSteps() > 0) {
                animationStartedAt = System.currentTimeMillis();
                SoundEffects.playShot();
                animationTimer.start();
            }
            repaint();
        }

        private void advanceAnimation() {
            if (function == null || !fired) {
                animationTimer.stop();
                return;
            }
            long now = System.currentTimeMillis();
            visibleSteps = animationSteps(function.getNumSteps(), now - animationStartedAt);
            if (visibleSteps >= function.getNumSteps()) {
                if (!impact) {
                    impact = true;
                    impactStartedAt = now;
                    if (function.getNumPlayersHit() > 0) {
                        targetEliminated = true;
                        targetEliminatedAt = now;
                    }
                    SoundEffects.playImpact();
                }
                if (now - impactStartedAt >= explosionDuration()) {
                    animationTimer.stop();
                }
            }
            repaint();
        }

        private int explosionDuration() {
            int total = 0;
            for (int duration : explosionDurations) {
                total += duration;
            }
            return total;
        }

        private Image[] loadExplosionImages() {
            Image[] images = new Image[6];
            for (int i = 0; i < images.length; i++) {
                try {
                    java.net.URL resource = CampaignScreen.class.getResource(
                            "/rsc/explosions/explosion" + i + ".png");
                    if (resource != null) {
                        images[i] = ImageIO.read(resource);
                    }
                } catch (Exception ignored) {
                    images[i] = null;
                }
            }
            return images;
        }

        private Image[] loadDeathImages() {
            Image[] images = new Image[5];
            for (int i = 0; i < images.length; i++) {
                try {
                    java.net.URL resource = CampaignScreen.class.getResource(
                            "/rsc/soldiers/soldierExplosion" + (i + 1) + "Small.png");
                    if (resource != null) {
                        images[i] = ImageIO.read(resource);
                    }
                } catch (Exception ignored) {
                    images[i] = null;
                }
            }
            return images;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (lesson == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.min((double) getWidth() / Constants.PLANE_LENGTH,
                        (double) getHeight() / Constants.PLANE_HEIGHT);
                int offsetX = (int) Math.round((getWidth() - Constants.PLANE_LENGTH * scale) / 2.0);
                int offsetY = (int) Math.round((getHeight() - Constants.PLANE_HEIGHT * scale) / 2.0);
                g.translate(offsetX, offsetY);
                g.scale(scale, scale);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT);

                g.setColor(new Color(30, 58, 52, 25));
                g.setStroke(new BasicStroke(1.0f));
                for (int x = 0; x <= Constants.PLANE_LENGTH; x += 77) {
                    g.drawLine(x, 0, x, Constants.PLANE_HEIGHT);
                }
                for (int y = 0; y <= Constants.PLANE_HEIGHT; y += 75) {
                    g.drawLine(0, y, Constants.PLANE_LENGTH, y);
                }
                g.setColor(YimoTheme.TEXT);
                g.drawLine(0, Constants.PLANE_HEIGHT / 2, Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT / 2);
                g.drawLine(Constants.PLANE_LENGTH / 2, 0, Constants.PLANE_LENGTH / 2, Constants.PLANE_HEIGHT);

                g.setColor(YimoTheme.TEXT);
                for (MapShape shape : lesson.getShapes()) {
                    if (shape.getType() == MapShape.CIRCLE) {
                        int radius = shape.getA();
                        g.fillOval(shape.getX() - radius, shape.getY() - radius, radius * 2, radius * 2);
                    } else {
                        g.fillRect(shape.getX(), shape.getY(), shape.getA(), shape.getB());
                    }
                }

                drawLegend(g);
                drawFunction(g);
                boolean complete = function != null && function.getNumPlayersHit() > 0;
                int targetRadius = lesson.getTargetRadius();
                if (targetEliminated) {
                    drawEliminatedTarget(g);
                } else {
                    drawPoint(g, lesson.getTargetX(), lesson.getTargetY(), targetRadius,
                            pointColor(Constants.TEAM2), "OPPONENT");
                    if (complete) {
                        g.setColor(YimoTheme.MINT);
                        g.setStroke(new BasicStroke(2.0f));
                        g.drawOval(lesson.getTargetX() - targetRadius - 4, lesson.getTargetY() - targetRadius - 4,
                                targetRadius * 2 + 8, targetRadius * 2 + 8);
                    }
                }

                int shooterRadius = Constants.SOLDIER_RADIUS;
                drawPoint(g, SHOOTER_X, SHOOTER_Y, shooterRadius,
                        pointColor(Constants.TEAM1), "YOU");
                drawExplosion(g);
            } finally {
                g.dispose();
            }
        }

        private void drawLegend(Graphics2D g) {
            g.setFont(YimoTheme.SMALL);
            drawLegendItem(g, 18, 20, pointColor(Constants.TEAM1), "YOU");
            drawLegendItem(g, 78, 20, pointColor(Constants.TEAM2), "OPPONENT");
        }

        private void drawLegendItem(Graphics2D g, int x, int y, Color color, String label) {
            g.setColor(color);
            g.fillOval(x, y - 9, 10, 10);
            g.setColor(YimoTheme.TEXT);
            g.drawString(label, x + 15, y);
        }

        private void drawPoint(Graphics2D g, int x, int y, int radius, Color color, String label) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 46));
            g.fillOval(x - radius - 4, y - radius - 4, radius * 2 + 8, radius * 2 + 8);
            g.setColor(Color.WHITE);
            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            g.setColor(color);
            g.fillOval(x - radius + 2, y - radius + 2, Math.max(2, radius * 2 - 4),
                    Math.max(2, radius * 2 - 4));
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            g.setFont(YimoTheme.SMALL);
            g.setColor(color);
            g.drawString(label, x - radius, y - radius - 8);
        }

        private void drawEliminatedTarget(Graphics2D g) {
            int x = lesson.getTargetX();
            int y = lesson.getTargetY();
            int frame = eliminationFrame(System.currentTimeMillis() - targetEliminatedAt);
            if (frame < deathImages.length && deathImages[frame] != null) {
                Image image = deathImages[frame];
                g.drawImage(image, x - image.getWidth(null) / 2, y - image.getHeight(null) / 2, null);
            } else {
                int radius = lesson.getTargetRadius();
                g.setColor(pointColor(Constants.TEAM2));
                g.setStroke(new BasicStroke(2.0f));
                g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
                g.drawLine(x - radius + 2, y - radius + 2, x + radius - 2, y + radius - 2);
                g.drawLine(x + radius - 2, y - radius + 2, x - radius + 2, y + radius - 2);
            }
            g.setFont(YimoTheme.SMALL);
            g.setColor(pointColor(Constants.TEAM2));
            g.drawString("ELIMINATED", x - 28, y + lesson.getTargetRadius() + 17);
        }

        private void drawFunction(Graphics2D g) {
            if (function == null || function.getNumSteps() <= 0) {
                return;
            }
            g.setColor(YimoTheme.ORANGE);
            g.setStroke(fired
                    ? new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    : new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            10.0f, new float[] { 8.0f, 6.0f }, 0.0f));
            Path2D path = new Path2D.Double();
            boolean started = false;
            int steps = fired ? visibleSteps : function.getNumSteps();
            for (int i = 0; i < steps; i++) {
                double x = Constants.PLANE_LENGTH * function.getX(i) / Constants.PLANE_GAME_LENGTH
                        + Constants.PLANE_LENGTH / 2.0;
                double y = -Constants.PLANE_LENGTH * function.getY(i) / Constants.PLANE_GAME_LENGTH
                        + Constants.PLANE_HEIGHT / 2.0;
                if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y)) {
                    started = false;
                    continue;
                }
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
            }
            g.draw(path);
        }

        private void drawExplosion(Graphics2D g) {
            if (!impact || function == null) {
                return;
            }
            long elapsed = System.currentTimeMillis() - impactStartedAt;
            int frame = (int) (elapsed / 15L);
            if (frame < 0 || frame >= explosionImages.length || explosionImages[frame] == null) {
                return;
            }
            Image image = explosionImages[frame];
            int x = (int) Math.round(function.getLastX());
            int y = (int) Math.round(function.getLastY());
            g.drawImage(image, x - image.getWidth(null) / 2, y - image.getHeight(null) / 2, null);
        }
    }
}
