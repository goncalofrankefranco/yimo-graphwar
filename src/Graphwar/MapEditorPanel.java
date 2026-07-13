package Graphwar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import GraphServer.Constants;
import GraphServer.MapShape;

public class MapEditorPanel extends JPanel {

    public interface ApplyListener {
        void apply(MapShape[] shapes);
    }

    private static final int TOOL_CIRCLE = MapShape.CIRCLE;
    private static final int TOOL_RECTANGLE = MapShape.RECTANGLE;

    private final List<MapShape> shapes;
    private final ApplyListener applyListener;
    private final MapCanvas canvas;
    private int tool;
    private Runnable cancelAction;

    MapEditorPanel(MapShape[] initialShapes, ApplyListener applyListener) {
        this.applyListener = applyListener;
        this.shapes = new ArrayList<MapShape>();
        if(initialShapes != null) {
            for(int i=0; i<initialShapes.length; i++) {
                if(initialShapes[i] != null) {
                    this.shapes.add(initialShapes[i]);
                }
            }
        }
        this.tool = TOOL_CIRCLE;
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        canvas = new MapCanvas();
        add(canvas, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton circle = new JButton("Circle");
        JButton rectangle = new JButton("Rectangle");
        JButton undo = new JButton("Undo");
        JButton clear = new JButton("Clear");
        JButton apply = new JButton("Apply");
        JButton cancel = new JButton("Cancel");
        JLabel help = new JLabel("Click-drag to place a shape");

        circle.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                tool = TOOL_CIRCLE;
            }
        });
        rectangle.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                tool = TOOL_RECTANGLE;
            }
        });
        undo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if(!shapes.isEmpty()) {
                    shapes.remove(shapes.size()-1);
                    canvas.repaint();
                }
            }
        });
        clear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                shapes.clear();
                canvas.repaint();
            }
        });
        apply.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if(applyListener != null) {
                    applyListener.apply(shapes.toArray(new MapShape[shapes.size()]));
                }
            }
        });
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if(cancelAction != null) {
                    cancelAction.run();
                }
            }
        });

        toolbar.add(circle);
        toolbar.add(rectangle);
        toolbar.add(undo);
        toolbar.add(clear);
        toolbar.add(apply);
        toolbar.add(cancel);
        toolbar.add(help);
        add(toolbar, BorderLayout.SOUTH);
    }

    public int getShapeCount() {
        return shapes.size();
    }

    void setCancelAction(Runnable cancelAction) {
        this.cancelAction = cancelAction;
    }

    public static void showDialog(JFrame owner, MapShape[] initialShapes, final ApplyListener listener) {
        final JDialog dialog = new JDialog(owner, "Map Editor", true);
        MapEditorPanel panel = new MapEditorPanel(initialShapes, new ApplyListener() {
            public void apply(MapShape[] shapes) {
                if(listener != null) {
                    listener.apply(shapes);
                }
                dialog.dispose();
            }
        });
        panel.setCancelAction(new Runnable() {
            public void run() {
                dialog.dispose();
            }
        });
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private class MapCanvas extends JPanel {
        private Point dragStart;
        private Point dragEnd;

        MapCanvas() {
            setPreferredSize(new Dimension(Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT));
            setBackground(Color.WHITE);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    dragStart = clamp(event.getPoint());
                    dragEnd = dragStart;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    dragEnd = clamp(event.getPoint());
                    addShapeFromDrag();
                    dragStart = null;
                    dragEnd = null;
                    repaint();
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent event) {
                    dragEnd = clamp(event.getPoint());
                    repaint();
                }
            });
        }

        private Point clamp(Point point) {
            return new Point(Math.max(0, Math.min(Constants.PLANE_LENGTH-1, point.x)),
                    Math.max(0, Math.min(Constants.PLANE_HEIGHT-1, point.y)));
        }

        private void addShapeFromDrag() {
            if(dragStart == null || dragEnd == null) {
                return;
            }
            try {
                if(tool == TOOL_CIRCLE) {
                    int radius = (int) Math.round(dragStart.distance(dragEnd));
                    int maxRadius = Math.min(Math.min(dragStart.x, Constants.PLANE_LENGTH-1-dragStart.x),
                            Math.min(dragStart.y, Constants.PLANE_HEIGHT-1-dragStart.y));
                    radius = Math.min(radius, maxRadius);
                    if(radius >= 4) {
                        shapes.add(MapShape.circle(dragStart.x, dragStart.y, radius));
                    }
                }
                else {
                    int x = Math.min(dragStart.x, dragEnd.x);
                    int y = Math.min(dragStart.y, dragEnd.y);
                    int width = Math.abs(dragEnd.x-dragStart.x);
                    int height = Math.abs(dragEnd.y-dragStart.y);
                    if(width >= 4 && height >= 4) {
                        shapes.add(MapShape.rectangle(x, y, width, height));
                    }
                }
            }
            catch(IllegalArgumentException ignored) {
                // The drag is already bounded; invalid zero-size drags are ignored.
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(Color.BLACK);
                g.drawLine(0, Constants.PLANE_HEIGHT/2, Constants.PLANE_LENGTH, Constants.PLANE_HEIGHT/2);
                g.drawLine(Constants.PLANE_LENGTH/2, 0, Constants.PLANE_LENGTH/2, Constants.PLANE_HEIGHT);
                for(int i=0; i<shapes.size(); i++) {
                    drawShape(g, shapes.get(i));
                }
                if(dragStart != null && dragEnd != null) {
                    g.setColor(new Color(80, 120, 200, 120));
                    if(tool == TOOL_CIRCLE) {
                        int radius = (int) Math.round(dragStart.distance(dragEnd));
                        g.drawOval(dragStart.x-radius, dragStart.y-radius, radius*2, radius*2);
                    }
                    else {
                        int x = Math.min(dragStart.x, dragEnd.x);
                        int y = Math.min(dragStart.y, dragEnd.y);
                        g.drawRect(x, y, Math.abs(dragEnd.x-dragStart.x), Math.abs(dragEnd.y-dragStart.y));
                    }
                }
            }
            finally {
                g.dispose();
            }
        }

        private void drawShape(Graphics2D g, MapShape shape) {
            if(shape.getType() == MapShape.CIRCLE) {
                int radius = shape.getA();
                g.fillOval(shape.getX()-radius, shape.getY()-radius, radius*2, radius*2);
            }
            else {
                g.fillRect(shape.getX(), shape.getY(), shape.getA(), shape.getB());
            }
        }
    }
}
