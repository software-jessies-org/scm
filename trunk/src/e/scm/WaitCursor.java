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
     * Makes sure that the sheet follows its parent around.
     */
    private static final ComponentListener SHEET_PARENT_LISTENER = new ComponentAdapter() {
        public void componentMoved(ComponentEvent e) {
            repositionSheet();
        }
        
        public void componentResized(ComponentEvent e) {
            repositionSheet();
        }
    };
    
    /**
     * If we're waiting long enough, we provide more feedback, via this window.
     */
    private static JDialog sheet;
    
    /**
     * This timer is used to decide when to show the informational sheet.
     */
    private static Timer sheetTimer;
    
    /**
     * To work around a Sun bug in Java 1.4.2, it's useful to keep a progress bar.
     */
    private static JProgressBar progressBar = new JProgressBar();
    
    public static void start(Object component, String message) {
        Component glassPane = getGlassPane(component);
        glassPane.setCursor(WAIT_CURSOR);
        glassPane.addMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(true);
        
        scheduleSheetAppearance(glassPane, message);
    }
    
    public static void stop(Object component) {
        if (sheetTimer != null) {
            sheetTimer.stop();
            sheetTimer = null;
        }
        
        Component glassPane = getGlassPane(component);
        glassPane.setCursor(DEFAULT_CURSOR);
        glassPane.removeMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(false);

        hideSheet();
    }

    private static synchronized void hideSheet() {
        // Work around Sun bug 4995929 for Java 1.4.2 users.
        progressBar.setIndeterminate(false);
        
        if (sheet != null) {
            sheet.getOwner().removeComponentListener(SHEET_PARENT_LISTENER);
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
    
    private static synchronized void showSheet(final Component glassPane, final String message) {
        hideSheet();
        createSheet(glassPane, message);
        sheet.setVisible(true);
    }
    
    private static synchronized void createSheet(final Component glassPane, final String message) {
        // Create the content.
        progressBar.setIndeterminate(true);
        JComponent content = new JPanel(new BorderLayout(20, 20));
        content.add(makeCenteredPanel(progressBar), BorderLayout.CENTER);
        content.add(makeCenteredPanel(new JLabel(message)), BorderLayout.SOUTH);
        content.setBorder(progressBar.getBorder());
        
        // Create the sheet itself.
        sheet = new JDialog(frameOf(glassPane));
        sheet.setContentPane(content);
        sheet.setSize(new Dimension(400, 120));
        sheet.setUndecorated(true);
        
        sheet.getOwner().addComponentListener(SHEET_PARENT_LISTENER);
        repositionSheet();
    }
    
    /** Lines the sheet up with the middle of the top of the parent window. */
    private static void repositionSheet() {
        Point location = sheet.getOwner().getLocationOnScreen();
        location.y += sheet.getOwner().getInsets().top;
        location.x += (sheet.getOwner().getWidth() - sheet.getWidth()) / 2;
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
