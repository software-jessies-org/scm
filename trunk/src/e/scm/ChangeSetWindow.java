package e.scm;

import e.gui.*;
import e.util.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import org.jdesktop.swingworker.SwingWorker;

public class ChangeSetWindow extends JFrame {
    private RevisionControlSystem backEnd;
    private JList fileList;
    private PatchView patchView;
    
    public ChangeSetWindow(final RevisionControlSystem backEnd, final String filePath, final Revision revision) {
        this.backEnd = backEnd;
        
        String title = FileUtilities.getUserFriendlyName(backEnd.getRoot().toString()) + " revision " + revision.number;
        setTitle(title);
        
        this.fileList = ScmUtilities.makeList();
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setVisibleRowCount(8);
        JScrollPane scrollableFileList = new JScrollPane(fileList, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        this.patchView = new PatchView();
        
        // If you're going to have a scroll pane against the edge of the window on Mac OS, you need to always have the scroll bars visible or you risk having an inaccessible scroll bar arrow. You can't use a JScrollPane corner because, as the JavaDoc says, their size is completely determined by the size of the scroll bars. (This, I think, is a JScrollPane bug.) We could work around this problem in the way I did in Terminator, but that's a lot of work for very little gain.
        JScrollPane scrollablePatchView = new JScrollPane(patchView, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        
        // Remove the border so the scroll pane fits snugly against the window edge. This is right on Mac OS, but is it right on GNOME?
        // FIXME: switch to a split pane instead.
        scrollablePatchView.setBorder(null);
        
        // FIXME: the UI should offer a way to get to the revision history.
        
        // FIXME: the UI should offer a way to get a patch corresponding to the change set, including the comments, and a comment generated by us saying what change set the patch corresponds to.
        // Per patch(1): "lines beginning with # ... are considered to be comments".
        
        setLayout(new BorderLayout());
        add(scrollableFileList, BorderLayout.NORTH);
        add(scrollablePatchView, BorderLayout.CENTER);
        pack();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        JFrameUtilities.setFrameIcon(this);
        JFrameUtilities.constrainToScreen(this);
        
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
            fileList.setEnabled(false);
            
            DefaultListModel model = new DefaultListModel();
            model.addElement("Getting list of files in revision " + revision.number);
            fileList.setModel(model);
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
            
            // Get the GUI component ready for real content.
            DefaultListModel model = new DefaultListModel();
            fileList.setModel(model);
            fileList.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    ChangeSetItem changeSetItem = (ChangeSetItem) fileList.getSelectedValue();
                    // For systems like BitKeeper, the newRevision of this file isn't necessarily the same as the Revision of the file we're showing the change set for.
                    Revision oldRevision = new Revision(changeSetItem.oldRevision, null, null, null);
                    Revision newRevision = new Revision(changeSetItem.newRevision, null, null, null);
                    // FIXME: we shouldn't do this on the EDT.
                    patchView.showPatch(backEnd, oldRevision, newRevision, changeSetItem.filename);
                    // FIXME: PatchView.showPatch produces an empty patch for a new file. (And for a deleted file?) If we got more information from the back-end we'd at least know that we were dealing with an 'A' (or 'D') file rather than an 'M' file.
                }
            });
            
            // Put the change set items into the GUI component's model.
            Collections.sort(changeSet);
            for (ChangeSetItem changeSetItem : changeSet) {
                model.addElement(changeSetItem);
                fileList.setEnabled(true);
            }
        }
    }
}
