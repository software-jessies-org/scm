package e.scm;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import e.util.*;

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
    private final ComponentListener sheetParentListener = new ComponentAdapter() {
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
    private JDialog sheet;
    
    /**
     * This timer is used to decide when to show the informational sheet.
     */
    private Timer sheetTimer;
    
    /**
     * To work around a Sun bug in Java 1.4.2, it's useful to keep a progress bar.
     */
    private JProgressBar progressBar = new JProgressBar();
    
    private Object component;
    private String message;
    private Component glassPane;
    
    public WaitCursor(Object component, String message) {
        this.component = component;
        this.message = message;
        this.glassPane = getGlassPane();
    }
    
    public synchronized void start() {
        glassPane.setCursor(WAIT_CURSOR);
        glassPane.addMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(true);
        
        scheduleSheetAppearance();
    }
    
    public synchronized void stop() {
        if (sheetTimer != null) {
            sheetTimer.stop();
            sheetTimer = null;
        }
        
        glassPane.setCursor(DEFAULT_CURSOR);
        glassPane.removeMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(false);

        hideSheet();
    }
    
    public void setProgressValue(final int value) {
        progressBar.setValue(value);
    }
    
    public void setProgressMaximum(final int value) {
        progressBar.setMaximum(value);
        progressBar.setIndeterminate(false);
    }
    
    private synchronized void hideSheet() {
        // Work around Sun bug 4995929 for Java 1.4.2 users.
        progressBar.setIndeterminate(false);
        
        if (sheet != null) {
            sheet.getOwner().removeComponentListener(sheetParentListener);
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
    private void scheduleSheetAppearance() {
        sheetTimer = new Timer(500, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showSheet();
            }
        });
        sheetTimer.setRepeats(false);
        sheetTimer.start();
    }
    
    private synchronized void showSheet() {
        hideSheet();
        createSheet();
        sheet.setVisible(true);
    }
    
    private synchronized void createSheet() {
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
        
        sheet.getOwner().addComponentListener(sheetParentListener);
        repositionSheet();
    }
    
    /** Lines the sheet up with the middle of the top of the parent window. */
    private void repositionSheet() {
        Window owner = sheet.getOwner();
        Point location = owner.getLocationOnScreen();
        if (GuiUtilities.isMacOs()) {
            location.y += owner.getInsets().top;
        } else {
            location.y += (owner.getHeight() - sheet.getHeight()) / 2;
        }
        location.x += (owner.getWidth() - sheet.getWidth()) / 2;
        sheet.setLocation(location);
    }

    private Component getGlassPane() {
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
}
