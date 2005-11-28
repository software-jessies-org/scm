package e.scm;

import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.jdesktop.swingworker.SwingWorker;

public class ChangeSetWindow extends JFrame {
    private RevisionControlSystem backEnd;
    private JComboBox fileChooser;
    private PatchView patchView;
    
    public ChangeSetWindow(final RevisionControlSystem backEnd, final String filePath, final Revision revision) {
        this.backEnd = backEnd;
        
        String title = FileUtilities.getUserFriendlyName(backEnd.getRoot().toString()) + " revision " + revision.number;
        setTitle(title);
        
        this.fileChooser = new JComboBox();
        this.patchView = new PatchView();
        
        fileChooser.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // FIXME: there must be a better way/more general way to get the patch from a particular revision. This will only work for Subversion.
                Revision previousRevision = new Revision(Integer.toString(Integer.parseInt(revision.number) - 1), null, null, null);
                // FIXME: we shouldn't do this on the EDT.
                patchView.showPatch(backEnd, previousRevision, revision, (String) fileChooser.getSelectedItem());
            }
        });
        
        // If you're going to have a scroll pane against the edge of the window on Mac OS, you need to always have the scroll bars visible or you risk having an inaccessible scroll bar arrow. You can't use a JScrollPane corner because, as the JavaDoc says, their size is completely determined by the size of the scroll bars. (This, I think, is a JScrollPane bug.) We could work around this problem in the way I did in Terminator, but that's a lot of work for very little gain.
        JScrollPane scrollablePatchView = new JScrollPane(patchView, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        // Remove the border so the scroll pane fits snugly against the window edge. This is right on Mac OS, but is it right on GNOME?
        scrollablePatchView.setBorder(null);
        
        setLayout(new BorderLayout());
        add(fileChooser, BorderLayout.NORTH);
        add(scrollablePatchView, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        
        // Fill the combo box without blocking the EDT; repository access may take some time.
        new ComboBoxFiller(filePath, revision).execute();
    }
    
    private class ComboBoxFiller extends SwingWorker<List<String>, Object> {
        private String filePath;
        private Revision revision;
        private List<String> changedPaths;
        
        public ComboBoxFiller(final String filePath, final Revision revision) {
            this.filePath = filePath;
            this.revision = revision;
            fileChooser.setEnabled(false);
        }
        
        @Override
        protected List<String> doInBackground() {
            changedPaths = backEnd.listTouchedFilesInRevision(filePath, revision);
            return changedPaths;
        }
        
        @Override
        public void done() {
            for (String changedPath : changedPaths) {
                fileChooser.addItem(changedPath);
                fileChooser.setEnabled(true);
            }
        }
    }
}
