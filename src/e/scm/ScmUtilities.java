package e.scm;

import e.gui.*;
import e.ptextarea.*;
import e.util.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;

public class ScmUtilities {
    public static final Font CODE_FONT = new Font(GuiUtilities.getMonospacedFontName(), Font.PLAIN, 14);
    
    public static void initAboutBox() {
        AboutBox aboutBox = AboutBox.getSharedInstance();
        Log.setApplicationName("SCM");
        aboutBox.setWebSiteAddress("https://github.com/software-jessies-org/jessies/wiki/SCM");
        aboutBox.addCopyright("Copyright (C) 2003-2018 Free Software Foundation, Inc.");
        aboutBox.addCopyright("All Rights Reserved.");
    }
    
    public static <T> JList<T> makeList() {
        JList<T> list = new JList<>();
        list.setFont(ScmUtilities.CODE_FONT);
        JListCopyAction.fixCopyFor(list);
        return list;
    }
    
    public static PTextArea makeTextArea(final int rowCount) {
        PTextArea textArea = new PTextArea(rowCount, 80);
        textArea.setFont(ScmUtilities.CODE_FONT);
        textArea.setWrapStyleWord(true);
        BugDatabaseHighlighter.highlightBugs(textArea);
        return textArea;
    }
    
    public static JScrollPane makeScrollable(JComponent c) {
        return new JScrollPane(c, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }
    
    public static javax.swing.border.Border getFrameBorder() {
        return BorderFactory.createEmptyBorder(10, 10, 10, 10);
    }
    
    public static void showToolError(JList<?> list, ArrayList<String> lines) {
        // We're going to replace whatever model the JList has with a String model...
        @SuppressWarnings("unchecked") JList<String> stringList = (JList<String>) list;
        stringList.setCellRenderer(new ToolErrorRenderer());
        stringList.setModel(new ToolErrorListModel(lines));
    }
    
    public static void showToolError(JList<?> list, ArrayList<String> lines, String[] command, int status) {
        if (status != 0) {
            lines.add("[Command '" + StringUtilities.join(command, " ") + "' returned status " + status + ".]");
        }
        showToolError(list, lines);
    }
    
    public static void panic(String toolName, String reason) {
        SimpleDialog.showAlert(null, toolName + " couldn't start", reason);
        System.exit(1);
    }
    
    public static void sanitizeSplitPaneDivider(JSplitPane splitPane) {
        // I think that, if JSplitPane can't fit both components' preferred sizes, it persecutes the left/top component,
        // depriving it of any space, even if that means granting the bottom/right component more than its preferred size.
        // This is the, somewhat poorly, documented behavior of the default resizeWeight, which is zero.
        // Getting and setting the divider location doesn't work reliably until long after the UI has been constructed.
        // BasicSplitPaneUI.java suggests this is true until we've been painted.
        Component firstComponent = splitPane.getLeftComponent();
        Component secondComponent = splitPane.getRightComponent();
        double firstLength = getSplitPaneComponentLength(splitPane, firstComponent);
        double secondLength = getSplitPaneComponentLength(splitPane, secondComponent);
        // If both components were equally sized, we'd want to pass 0.5.
        double preferredWeight = firstLength / (firstLength + secondLength);
        splitPane.setResizeWeight(preferredWeight);
    }
    
    // Helper method for sanitizeSplitPaneDivider.
    private static double getSplitPaneComponentLength(JSplitPane splitPane, Component component) {
        // The actual size may already have been messed up, so we use the preferred size.
        Dimension size = component.getPreferredSize();
        return (splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT) ? size.getWidth() : size.getHeight();
    }
    
    private ScmUtilities() {
    }
}
