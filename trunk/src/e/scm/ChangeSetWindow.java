package e.scm;

import e.gui.*;
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
                ChangeSetItem changeSetItem = (ChangeSetItem) fileChooser.getSelectedItem();
                // For systems like BitKeeper, the newRevision of this file isn't necessarily the same as the Revision of the file we're showing the change set for.
                Revision oldRevision = new Revision(changeSetItem.oldRevision, null, null, null);
                Revision newRevision = new Revision(changeSetItem.newRevision, null, null, null);
                // FIXME: we shouldn't do this on the EDT.
                patchView.showPatch(backEnd, oldRevision, newRevision, changeSetItem.filename);
                // FIXME: PatchView.showPatch produces an empty patch for a new file. (And for a deleted file?) If we got more information from the back-end we'd at least know that we were dealing with an 'A' (or 'D') file rather than an 'M' file.
            }
        });
        
        // If you're going to have a scroll pane against the edge of the window on Mac OS, you need to always have the scroll bars visible or you risk having an inaccessible scroll bar arrow. You can't use a JScrollPane corner because, as the JavaDoc says, their size is completely determined by the size of the scroll bars. (This, I think, is a JScrollPane bug.) We could work around this problem in the way I did in Terminator, but that's a lot of work for very little gain.
        JScrollPane scrollablePatchView = new JScrollPane(patchView, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        // Remove the border so the scroll pane fits snugly against the window edge. This is right on Mac OS, but is it right on GNOME?
        scrollablePatchView.setBorder(null);
        
        setLayout(new BorderLayout());
        add(fileChooser, BorderLayout.NORTH);
        add(scrollablePatchView, BorderLayout.CENTER);
        // FIXME: the UI should offer a way to get to the revision history.
        pack();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Fill the combo box without blocking the EDT; repository access may take some time.
        new ComboBoxFiller(filePath, revision).execute();
    }
    
    private class ComboBoxFiller extends SwingWorker<List<ChangeSetItem>, Object> {
        private String filePath;
        private Revision revision;
        private List<ChangeSetItem> changeSet;
        private Throwable failure;
        
        public ComboBoxFiller(final String filePath, final Revision revision) {
            this.filePath = filePath;
            this.revision = revision;
            fileChooser.setEnabled(false);
        }
        
        @Override
        protected List<ChangeSetItem> doInBackground() {
            try {
                changeSet = backEnd.listTouchedFilesInRevision(filePath, revision);
            } catch (Throwable th) {
                failure = th;
            }
            return changeSet;
        }
        
        @Override
        public void done() {
            if (failure != null) {
                SimpleDialog.showDetails(ChangeSetWindow.this, "Change Set View", failure);
                return;
            }
            
            Collections.sort(changeSet);
            for (ChangeSetItem changeSetItem : changeSet) {
                fileChooser.addItem(changeSetItem);
                fileChooser.setEnabled(true);
            }
        }
    }
}