package Graphwar;

import GraphServer.MapShape;
import javax.swing.SwingUtilities;

public class MapEditorPanelTest {
    public static void main(String[] args) throws Exception {
        final int[] count = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                MapEditorPanel panel = new MapEditorPanel(new MapShape[0], null);
                count[0] = panel.getShapeCount();
            }
        });
        if (count[0] != 0) {
            throw new AssertionError("editor should start empty");
        }
        System.out.println("map-editor-check: PASS");
    }
}
