package e.scm;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import e.util.*;

public class PatchView extends JList {
    public PatchView() {
        setCellRenderer(DifferencesRenderer.INSTANCE);
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
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = 0;
        try {
            WaitCursor.start(this);
            String[] command = backEnd.getDifferencesCommand(olderRevision, newerRevision, filename);
            status = ProcessUtilities.backQuote(backEnd.getRoot(), command, lines, errors);
        } finally {
            WaitCursor.stop(this);
        }
        
        // CVS returns a non-zero exit status if there were any differences.
        if (errors.size() > 0) {
            lines.addAll(errors);
            return;
        }
        
        DefaultListModel differences = new DefaultListModel();
        for (int i = 0; i < lines.size(); ++i) {
            differences.addElement((String) lines.get(i));
        }
        setModel(differences);
        
        // We can't easily retain the context when switching to differences.
        // As an extension, though, we could do this.
        ensureIndexIsVisible(0);
    }
}
