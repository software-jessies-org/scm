package e.scm;

import e.gui.*;
import e.util.*;
import javax.swing.*;

public class RevisionWindow extends JFrame {
    private RevisionControlSystem backEnd;
    
    // FIXME: we should have an interface that lets us ask to start with a given revision.
    public RevisionWindow(String filename, int initialLineNumber) {
        this.backEnd = RevisionControlSystem.forPath(filename);
        setTitle(FileUtilities.getUserFriendlyName(filename));
        RevisionView revisionView = new RevisionView(filename, initialLineNumber);
        setContentPane(revisionView);
        pack();
        revisionView.postPack();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        JFrameUtilities.setFrameIcon(this);
        JFrameUtilities.constrainToScreen(this);
        setVisible(true);
        GuiUtilities.finishGnomeStartup();
    }
}
