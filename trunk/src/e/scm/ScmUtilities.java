package e.scm;

import e.ptextarea.*;
import e.util.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

public class ScmUtilities {
    public static final Font CODE_FONT = new Font(GuiUtilities.getMonospacedFontName(), Font.PLAIN, 12);
    
    private static class JListCopyAction extends AbstractAction {
        public JListCopyAction() {
            super("copy");
        }
        
        public void actionPerformed(ActionEvent e) {
            copyListSelectionToClipboard((JList) e.getSource());
        }
        
        private void copyListSelectionToClipboard(JList list) {
            // Make a StringSelection corresponding to the selected lines.
            StringBuilder buffer = new StringBuilder();
            Object[] selectedLines = list.getSelectedValues();
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
    }
    
    public static JList makeList() {
        JList list = new JList();
        list.getActionMap().put("copy", new JListCopyAction());
        list.setFont(ScmUtilities.CODE_FONT);
        return list;
    }
    
    public static PTextArea makeTextArea(final int rowCount) {
        PTextArea textArea = new PTextArea(rowCount, 80);
        textArea.setFont(ScmUtilities.CODE_FONT);
        textArea.setWrapStyleWord(true);
        textArea.addStyleApplicator(new BugDatabaseHighlighter(textArea));
        return textArea;
    }
    
    private ScmUtilities() {
    }
}
