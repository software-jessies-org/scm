package e.scm;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class WaitCursor {
    private static final Cursor DEFAULT_CURSOR = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    private static final Cursor WAIT_CURSOR = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
    
    private static final MouseAdapter MOUSE_EVENT_SWALLOWER = new MouseAdapter() {
    };
    
    private String message;
    private JPanel glassPane;
    private StatusReporter statusReporter;
    
    public WaitCursor(Object component, String message, StatusReporter statusReporter) {
        this.message = message;
        this.glassPane = (JPanel) findGlassPane(component);
        this.statusReporter = statusReporter;
    }
    
    public void start() {
        glassPane.setCursor(WAIT_CURSOR);
        glassPane.addMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.removeAll();
        glassPane.setVisible(true);
        statusReporter.startTask(message);
    }
    
    public void stop() {
        statusReporter.finishTask();
        glassPane.setCursor(DEFAULT_CURSOR);
        glassPane.removeMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(false);
    }
    
    private Component findGlassPane(Object component) {
        RootPaneContainer container = null;
        if (component instanceof RootPaneContainer) {
            container = (RootPaneContainer) component;
        } else if (component instanceof JComponent) {
            container = (RootPaneContainer) ((JComponent) component).getTopLevelAncestor();
        }
        return container.getGlassPane();
    }
}
