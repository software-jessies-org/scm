package e.scm;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class WaitCursor {
    private static final Cursor DEFAULT_CURSOR =
        Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    private static final Cursor WAIT_CURSOR =
        Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
    private static final MouseAdapter MOUSE_EVENT_SWALLOWER =
        new MouseAdapter() {
        };

    public static void start(Object component) {
        Component glassPane = getGlassPane(component);
        glassPane.setCursor(WAIT_CURSOR);
        glassPane.addMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(true);
    }

    public static void stop(Object component) {
        Component glassPane = getGlassPane(component);
        glassPane.setCursor(DEFAULT_CURSOR);
        glassPane.removeMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(false);
    }

    private static Component getGlassPane(Object component) {
        RootPaneContainer container = null;
        if (component instanceof RootPaneContainer) {
            container = (RootPaneContainer) component;
        } else if (component instanceof JComponent) {
            container = (RootPaneContainer) ((JComponent) component).getTopLevelAncestor();
        }
        return container.getGlassPane();
    }

    private WaitCursor() {
        // Prevent instantiation.
    }
}
