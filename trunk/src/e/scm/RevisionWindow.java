package e.scm;

import e.util.*;
import javax.swing.*;

public class RevisionWindow extends JFrame {
    private RevisionControlSystem backEnd;
    
    public RevisionWindow(String filename, int initialLineNumber) {
        this.backEnd = RevisionControlSystem.forPath(filename);
        setTitle(FileUtilities.getUserFriendlyName(filename));
        setContentPane(new RevisionView(filename, initialLineNumber));
        pack();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }
}
