package e.scm;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import e.util.*;

public class WaitCursor {
    private static final Cursor DEFAULT_CURSOR = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    private static final Cursor WAIT_CURSOR = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
    
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
    
    private JProgressBar progressBar;
    private JComponent content;
    
    private Object component;
    private String message;
    private JPanel glassPane;
    
    public WaitCursor(Object component, String message) {
        this.component = component;
        this.message = message;
        this.glassPane = (JPanel) getGlassPane();
        this.progressBar = new JProgressBar();
    }
    
    public void start() {
        glassPane.setCursor(WAIT_CURSOR);
        glassPane.addMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.removeAll();
        glassPane.setVisible(true);
        
        scheduleSheetAppearance();
    }
    
    public void stop() {
        if (sheetTimer != null) {
            sheetTimer.stop();
            sheetTimer = null;
        }
        
        glassPane.setCursor(DEFAULT_CURSOR);
        glassPane.removeMouseListener(MOUSE_EVENT_SWALLOWER);
        glassPane.setVisible(false);

        hideSheet();
    }
    
    public void setProgressValue(final int value, final int maximum) {
        progressBar.setValue(value);
        progressBar.setMaximum(maximum);
        progressBar.setIndeterminate(false);
    }
    
    private void hideSheet() {
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
    
    private void showSheet() {
        hideSheet();
        createSheet();
    }
    
    private void createSheet() {
        // Create the content.
        content = new JPanel(new BorderLayout());
        content.add(makeCenteredPanel(progressBar), BorderLayout.CENTER);
        content.add(makeCenteredPanel(new JLabel(message)), BorderLayout.SOUTH);
        
        progressBar.setIndeterminate(true);
        
        final Frame frame = frameOf(glassPane);
        
        // Create the sheet itself.
        sheet = new JDialog(frame);
        sheet.setContentPane(content);
        
        // Get rid of the title bar, but put back some kind of hard edge.
        sheet.setUndecorated(true);
        content.setBorder(new javax.swing.border.LineBorder(SystemColor.windowBorder));
        
        // Give it a decent size.
        sheet.pack();
        Dimension size = sheet.getSize();
        if (size.width < 400) {
            size.width = 400;
        }
        if (size.height < 120) {
            size.height = 120;
        }
        sheet.setSize(size);
        sheet.setLocationRelativeTo(frame);
        
        if (GuiUtilities.isMacOs()) {
            // Ensure we defer focus to our parent.
            content.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    frame.requestFocus();
                }
            });
            
            // Ensure we follow our parent around, as if attached.
            repositionSheet();
            frame.addComponentListener(sheetParentListener);
        }
        sheet.setVisible(true);
    }
    
    /** Keeps the sheet dialog lined up with the middle of the top of the parent window. */
    private void repositionSheet() {
        Frame owner = frameOf(glassPane);
        Point location = owner.getLocationOnScreen();
        location.x += (owner.getWidth() - content.getWidth()) / 2;
        location.y += owner.getInsets().top;
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
        return Frame.class.cast(SwingUtilities.getAncestorOfClass(Frame.class, c));
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
