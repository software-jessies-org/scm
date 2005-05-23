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
        setCellRenderer(new EListCellRenderer(false));
        this.defaultCellRenderer = getCellRenderer();
        
        setVisibleRowCount(34);
        
        // JList has half an implementation of copy.
        ActionMap actionMap = getActionMap();
        actionMap.put("copy", new AbstractAction("copy") {
            public void actionPerformed(ActionEvent e) {
                copySelectedItemsToClipboard();
            }
        });
    }
    
    private void copySelectedItemsToClipboard() {
        // Make a StringSelection corresponding to the selected lines.
        StringBuffer buffer = new StringBuffer();
        Object[] selectedLines = getSelectedValues();
        for (int i = 0; i < selectedLines.length; ++i) {
            String line = selectedLines[i].toString();
            buffer.append(line);
            buffer.append('\n');
        }
        StringSelection selection = new StringSelection(buffer.toString());
        
        // Set the clipboard (and X11's nasty hacky semi-duplicate).
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        toolkit.getSystemClipboard().setContents(selection, selection);
        if (toolkit.getSystemSelection() != null) {
            toolkit.getSystemSelection().setContents(selection, selection);
        }
    }
    
    public void showPatch(RevisionControlSystem backEnd, Revision olderRevision, Revision newerRevision, String filename) {
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = 0;
        WaitCursor waitCursor = new WaitCursor(this, "Getting patch...");
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
            return;
        }
        
        lines = annotatePatchUsingTags(backEnd, lines);
        
        DefaultListModel differences = new DefaultListModel();
        for (int i = 0; i < lines.size(); ++i) {
            differences.addElement((String) lines.get(i));
        }
        setCellRenderer(PatchListCellRenderer.INSTANCE);
        setModel(differences);
        
        // We can't easily retain the context when switching to differences.
        // As an extension, though, we could do this.
        ensureIndexIsVisible(0);
    }
    
    private ArrayList<String> annotatePatchUsingTags(RevisionControlSystem backEnd, ArrayList<String> lines) {
        ArrayList<String> newLines = new ArrayList<String>();
        ArrayList<String> newErrors = new ArrayList<String>();
        String patch = StringUtilities.join(lines, "\n") + "\n";
        String script = FileUtilities.getSalmaHayekFile("/bin/annotate-patch.rb").toString();
        String patchFilename = FileUtilities.createTemporaryFile("e.scm.PatchView-patch", "patch file", patch);
        String[] command = new String[] { script,  patchFilename };
        int status = ProcessUtilities.backQuote(backEnd.getRoot(), command, newLines, newErrors);
        if (status != 0) {
            return lines;
        }
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
            for (int i = 0; i < lines.length; ++i) {
                model.addElement(lines[i]);
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
    
    public void scroll(int direction) {
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
        int increment = scrollBar.getBlockIncrement(direction);
        int newValue = scrollBar.getValue() + direction * increment;
        newValue = Math.min(newValue, scrollBar.getMaximum());
        newValue = Math.max(newValue, scrollBar.getMinimum());
        scrollBar.setValue(newValue);
    }
}
