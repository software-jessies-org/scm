package e.scm;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class WaitCursor {
    private static final Cursor DEFAULT_CURSOR =
        Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    private static final Cursor WAIT_CURSOR =
        Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
    
    private static final MouseAdapter MOUSE_EVENT_SWALLOWER = new MouseAdapter() {
    };
    
    /**
     * If we're waiting long enough, we provide more feedback, via this window.
     */
    private static JWindow sheet;
    
    /**
     * This timer is used to decide when to show the informational sheet.
     */
    private static Timer sheetTimer;
    
    public static void start(Object component, String message) {
        Component glassPane = getGlassPane(component);
        glassPane.setCursor(WAIT_CURSOR);
        glassPane.addMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(true);
        
        scheduleSheetAppearance(glassPane, message);
    }
    
    public static void stop(Object component) {
        sheetTimer.stop();
        sheetTimer = null;
        
        Component glassPane = getGlassPane(component);
        glassPane.setCursor(DEFAULT_CURSOR);
        glassPane.removeMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(false);
        
        if (sheet != null) {
            sheet.setVisible(false);
            sheet.dispose();
            sheet = null;
        }
    }
    
    /**
     * Brings up a sheet explaining the delay after we've been waiting a very short while.
     * We can't bring up the explanatory sheet immediately, because we don't know how long
     * we'll be waiting. If we're not waiting long enough, we'd just be causing annoying flashing.
     */
    private static void scheduleSheetAppearance(final Component glassPane, final String message) {
        sheetTimer = new Timer(500, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showSheet(glassPane, message);
            }
        });
        sheetTimer.setRepeats(false);
        sheetTimer.start();
    }
    
    private static void showSheet(final Component glassPane, final String message) {
        createSheet(glassPane, message);
        sheet.setVisible(true);
    }
    
    private static void createSheet(final Component glassPane, final String message) {
        // Create the content.
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        JComponent content = new JPanel(new BorderLayout(20, 20));
        content.add(makeCenteredPanel(progressBar), BorderLayout.CENTER);
        content.add(makeCenteredPanel(new JLabel(message)), BorderLayout.SOUTH);
        content.setBorder(progressBar.getBorder());
        
        // Create the sheet itself.
        sheet = new JWindow(frameOf(glassPane));
        sheet.setContentPane(content);
        sheet.setSize(new Dimension(400, 120));
        
        // Line it up with the middle of the top of the window.
        Point location = glassPane.getLocationOnScreen();
        location.x += (glassPane.getWidth() - sheet.getWidth()) / 2;
        sheet.setLocation(location);
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
    
    /**
     * Returns the Frame containing the given component.
     */
    private static Frame frameOf(Component c) {
        return (Frame) SwingUtilities.getAncestorOfClass(Frame.class, c);
    }
    
    /**
     * Returns a panel with the given component centered within it.
     */
    private static JPanel makeCenteredPanel(JComponent component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        panel.add(component);
        return panel;
    }

    private WaitCursor() {
        // Prevent instantiation.
    }
}
