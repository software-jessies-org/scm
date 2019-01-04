package e.scm;

import e.gui.*;
import e.util.*;

public class RevisionWindow extends MainFrame {
    private RevisionControlSystem backEnd;
    
    // FIXME: we should have an interface that lets us ask to start with a given revision.
    public RevisionWindow(String filename, int initialLineNumber) {
        this.backEnd = RevisionControlSystem.forPath(filename);
        setTitle(FileUtilities.getUserFriendlyName(filename));
        RevisionView revisionView = new RevisionView(filename, initialLineNumber);
        revisionView.setBorder(ScmUtilities.getFrameBorder());
        setContentPane(revisionView);
        pack();
        setVisible(true);
    }
}
