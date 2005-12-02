package e.scm;

import e.ptextarea.*;
import e.util.*;
import java.awt.*;

public class ScmUtilities {
    public static final Font CODE_FONT = new Font(GuiUtilities.getMonospacedFontName(), Font.PLAIN, 12);
    
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
