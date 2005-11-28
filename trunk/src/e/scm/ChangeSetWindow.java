package e.scm;

import e.util.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class ChangeSetWindow extends JFrame {
    private RevisionControlSystem backEnd;
    private JComboBox fileChooser;
    
    public ChangeSetWindow(final RevisionControlSystem backEnd, final String filePath, final Revision revision) {
        this.backEnd = backEnd;
        
        String title = FileUtilities.getUserFriendlyName(backEnd.getRoot().toString()) + " revision " + revision.number;
        setTitle(title);
        
        this.fileChooser = new JComboBox();
        
        setLayout(new BorderLayout());
        add(fileChooser, BorderLayout.NORTH);
        
        List<String> changedPaths = backEnd.listTouchedFilesInRevision(filePath, revision);
        for (String changedPath : changedPaths) {
            fileChooser.addItem(changedPath);
        }
    }
}
