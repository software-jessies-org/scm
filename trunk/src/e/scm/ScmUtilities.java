package e.scm;

import e.gui.*;
import e.ptextarea.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

public class ScmUtilities {
    public static final Font CODE_FONT = new Font(GuiUtilities.getMonospacedFontName(), Font.PLAIN, 12);
    
    public static JList makeList() {
        JList list = new JList();
        list.setFont(ScmUtilities.CODE_FONT);
        JListCopyAction.fixCopyFor(list);
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
