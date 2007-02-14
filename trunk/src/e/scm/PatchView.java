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

public class PatchView extends JList {
    private ListCellRenderer defaultCellRenderer;
    
    public PatchView() {
        // JList calculates its preferred size based on the first row of its model, so provide a fake model so that our initial preferred size is 80 columns.
        // We don't use setPrototypeCellValue because that would also affect our maximum size.
        super(new AbstractListModel() {
            public int getSize() {
                return 1;
            }
            
            public Object getElementAt(int i) {
                return "                                                                                ";
            }
        });
        setFont(ScmUtilities.CODE_FONT);
        
        EListCellRenderer renderer = new EListCellRenderer(false);
        // Disable HTML so we can render HTML files as source!
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        setCellRenderer(renderer);
        this.defaultCellRenderer = getCellRenderer();
        
        JListCopyAction.fixCopyFor(this);
        
        setVisibleRowCount(34);
    }
    
    public void showPatch(RevisionControlSystem backEnd, Revision olderRevision, Revision newerRevision, String filename, StatusReporter statusReporter) {
        ArrayList<String> lines = getPatchLines(backEnd, olderRevision, newerRevision, filename, statusReporter);
        showPatch(lines);
    }
    
    public ArrayList<String> getPatchLines(RevisionControlSystem backEnd, Revision olderRevision, Revision newerRevision, String filename, StatusReporter statusReporter) {
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = 0;
        WaitCursor waitCursor = new WaitCursor(this, "Getting patch...", statusReporter);
        try {
            waitCursor.start();
            String[] command = backEnd.getDifferencesCommand(olderRevision, newerRevision, filename);
            status = ProcessUtilities.backQuote(backEnd.getRoot(), command, lines, errors);
        } finally {
            waitCursor.stop();
        }
        
        // CVS returns a non-zero exit status if there were any differences.
        if (errors.size() > 0) {
            lines.addAll(errors);
        }
        
        lines = annotatePatchUsingTags(backEnd, lines);
        return lines;
    }
    
    public void showPatch(ArrayList<String> lines) {
        DefaultListModel differences = new DefaultListModel();
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
        ArrayList<String> newLines = new ArrayList<String>();
        ArrayList<String> newErrors = new ArrayList<String>();
        String patch = StringUtilities.join(lines, "\n") + "\n";
        File patchAnnotationTool = FileUtilities.findOnPath("annotate-patch.rb");
        if (patchAnnotationTool == null) {
            return lines;
        }
        
        String patchFilename = FileUtilities.createTemporaryFile("e.scm.PatchView-patch", "patch file", patch);
        String[] command = new String[] { "ruby", patchAnnotationTool.toString(), patchFilename };
        int status = ProcessUtilities.backQuote(backEnd.getRoot(), command, newLines, newErrors);
        if (status != 0) {
            return lines;
        }
        // Remove the file right away, since a CheckInTool user can cause an
        // arbitrary number to be created during a session; one for each time
        // they change the selection in the file list. We only remove the
        // file if the script executed successfully; if it didn't, we might
        // want to debug the problem.
        FileUtilities.fileFromString(patchFilename).delete();
        return newLines;
    }
    
    void showNewFile(RevisionControlSystem backEnd, final FileStatus status) {
        DefaultListModel model = new DefaultListModel();
        File file = new File(backEnd.getRoot(), status.getName());
        if (file.isDirectory()) {
            model.addElement("(" + status.getName() + " is a directory.)");
        } else if (FileUtilities.isTextFile(file) == false) {
            model.addElement("(" + status.getName() + " is a binary file.)");
        } else {
            String[] lines = StringUtilities.readLinesFromFile(file.getAbsolutePath());
            for (String line : lines) {
                model.addElement(line);
            }
            if (model.getSize() == 0) {
                model.addElement("(" + status.getName() + " is empty.)");
            }
        }

        setCellRenderer(defaultCellRenderer);
        setModel(model);
        ensureIndexIsVisible(0);
    }
    
    /**
     * Given an index (that is, a line number in the patch), returns the
     * line number in the newer revision. Used to implement double-clicking
     * on a patch in CheckInWindow, which shows you that line in your
     * editor.
     */
    public int lineNumberInNewerRevisionAtIndex(int index) {
        int lineNumber = 0;
        for (int i = 0; i <= index; ++i) {
            String line = (String) getModel().getElementAt(i);
            Matcher matcher = Patch.AT_AT_PATTERN.matcher(line);
            if (matcher.matches()) {
                lineNumber = Integer.parseInt(matcher.group(3)) - 1;
            } else if (line.startsWith("-") == false) {
                ++lineNumber;
            }
        }
        return lineNumber;
    }
}
