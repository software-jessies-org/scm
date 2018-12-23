package e.scm;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;
import e.gui.*;
import e.util.*;

public class PatchView extends JList<String> {
    private ListCellRenderer<? super String> defaultCellRenderer;
    private boolean ignoreWhiteSpace;
    
    private RevisionControlSystem backEnd;
    private Revision olderRevision;
    private Revision newerRevision;
    private String filename;
    private StatusReporter statusReporter;
    
    public PatchView() {
        // JList calculates its preferred size based on the first row of its model, so provide a fake model so that our initial preferred size is 80 columns.
        // We don't use setPrototypeCellValue because that would also affect our maximum size.
        super(new AbstractListModel<String>() {
            public int getSize() {
                return 1;
            }
            
            public String getElementAt(int i) {
                return "                                                                                ";
            }
        });
        setFont(ScmUtilities.CODE_FONT);
        
        EListCellRenderer<String> renderer = new EListCellRenderer<>(false);
        // Disable HTML so we can render HTML files as source!
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        setCellRenderer(renderer);
        this.defaultCellRenderer = getCellRenderer();
        
        JListCopyAction.fixCopyFor(this);
        
        setVisibleRowCount(34);
        
        EPopupMenu menu = new EPopupMenu(this);
        menu.addMenuItemProvider(new MenuItemProvider() {
            public void provideMenuItems(MouseEvent e, Collection<Action> actions) {
                actions.add(new IgnoreWhiteSpaceAction());
            }
        });
        
        ignoreWhiteSpace = false;
    }
    
    private class IgnoreWhiteSpaceAction extends AbstractAction {
        IgnoreWhiteSpaceAction() {
            super(ignoreWhiteSpace ? "Stop Ignoring White Space" : "Ignore White Space");
        }
        
        public void actionPerformed(ActionEvent e) {
            ignoreWhiteSpace = !ignoreWhiteSpace;
            updatePatch();
        }
    }
    
    private void updatePatch() {
        ArrayList<String> lines = getPatchLines(backEnd, olderRevision, newerRevision, filename, statusReporter);
        showPatch(lines);
    }
    
    public void showPatch(RevisionControlSystem backEnd, Revision olderRevision, Revision newerRevision, String filename, StatusReporter statusReporter) {
        this.backEnd = backEnd;
        this.olderRevision = olderRevision;
        this.newerRevision = newerRevision;
        this.filename = filename;
        this.statusReporter = statusReporter;
        updatePatch();
    }
    
    public ArrayList<String> getPatchLines(RevisionControlSystem backEnd, Revision olderRevision, Revision newerRevision, String filename, StatusReporter statusReporter) {
        WaitCursor waitCursor = new WaitCursor(this, "Getting patch...", statusReporter);
        waitCursor.start();
        try {
            Patch patch = new Patch(backEnd, filename, olderRevision, newerRevision, false, ignoreWhiteSpace);
            return annotatePatchUsingTags(backEnd, patch.getPatchLines());
        } finally {
            waitCursor.stop();
        }
    }
    
    private void showPatch(ArrayList<String> lines) {
        DefaultListModel<String> differences = new DefaultListModel<>();
        for (String line : lines) {
            differences.addElement(line);
        }
        setCellRenderer(PatchListCellRenderer.INSTANCE);
        setModel(differences);
        
        // We can't easily retain the context when switching to differences.
        // As an extension, though, we could do this.
        ensureIndexIsVisible(0);
    }
    
    public static ArrayList<String> annotatePatchUsingTags(RevisionControlSystem backEnd, ArrayList<String> lines) {
        ArrayList<String> newLines = new ArrayList<>();
        ArrayList<String> newErrors = new ArrayList<>();
        String patch = StringUtilities.join(lines, "\n") + "\n";
        String patchAnnotationTool = FileUtilities.findSupportScript("annotate-patch.rb");
        
        File patchFile = FileUtilities.createTemporaryFile("e.scm.PatchView-patch", ".tmp", "patch file", patch);
        String[] command = new String[] { patchAnnotationTool.toString(), patchFile.toString() };
        int status = ProcessUtilities.backQuote(backEnd.getRoot(), command, newLines, newErrors);
        if (status != 0) {
            return lines;
        }
        // Remove the file right away, since a CheckInTool user can cause an
        // arbitrary number to be created during a session; one for each time
        // they change the selection in the file list. We only remove the
        // file if the script executed successfully; if it didn't, we might
        // want to debug the problem.
        patchFile.delete();
        return newLines;
    }
}
