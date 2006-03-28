package e.scm;

import e.gui.*;
import e.ptextarea.*;
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
    private Map<String, RevisionListModel> filePathToRevisionsMap;
    
    private JList fileList;
    private PatchView patchView;
    private JButton showPatchButton;
    
    public ChangeSetWindow(final RevisionControlSystem backEnd, final String initialFilePath, final RevisionListModel initialFileRevisions, final Revision initialFileRevision) {
        this.backEnd = backEnd;
        this.filePathToRevisionsMap = new HashMap<String, RevisionListModel>();
        filePathToRevisionsMap.put(initialFilePath, initialFileRevisions);
        
        // FIXME: Specifying a revision that, with BitKeeper, is file-specific without specifying the file doesn't make sense.
        String title = FileUtilities.getUserFriendlyName(backEnd.getRoot().toString()) + " revision " + initialFileRevision.number;
        setTitle(title);
        
        this.fileList = ScmUtilities.makeList();
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setVisibleRowCount(8);
        JScrollPane scrollableFileList = ScmUtilities.makeScrollable(fileList);
        
        this.patchView = new PatchView();
        JScrollPane scrollablePatchView = ScmUtilities.makeScrollable(patchView);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollableFileList, scrollablePatchView);
        splitPane.setBorder(null);
        
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(ScmUtilities.getFrameBorder());
        contentPane.add(makeButtonPanel(), BorderLayout.NORTH);
        contentPane.add(splitPane, BorderLayout.CENTER);
        setContentPane(contentPane);
        pack();
        
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        JFrameUtilities.setFrameIcon(this);
        JFrameUtilities.constrainToScreen(this);
        
        setVisible(true);
        splitPane.setDividerLocation(fileList.getPreferredScrollableViewportSize().height);
        
        // Fill the combo box without blocking the EDT; repository access may take some time.
        new ComboBoxFiller(initialFilePath, initialFileRevision).execute();
    }
    
    private JComponent makeButtonPanel() {
        this.showPatchButton = new JButton("Show Patch");
        showPatchButton.setEnabled(false);
        showPatchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showPatch();
            }
        });
        
        // FIXME: the UI should offer a way to get to the revision history.
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(showPatchButton);
        return buttonPanel;
    }
    
    // FIXME: This is a verbatim copy of code from RevisionView.
    private void showToolError(JList list, ArrayList<String> lines) {
        list.setCellRenderer(new ToolErrorRenderer());
        list.setModel(new ToolErrorListModel(lines));
    }
    
    // FIXME: This is a verbatim copy of code from RevisionView.
    private void showToolError(JList list, ArrayList<String> lines, String[] command, int status) {
        if (status != 0) {
            lines.add("[Command '" + StringUtilities.join(command, " ") + "' returned status " + status + ".]");
        }
        showToolError(list, lines);
    }
    
    /**
     * Similar to BlockingWorker. FIXME: replace both classes with SwingWorker for Java 6.
     */
    public abstract class BackEndWorker implements Runnable {
        private String message;
        private Exception caughtException;
        
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        String[] command;
        int status = 0;
        
        private int thisWorkerModCount;
        
        public BackEndWorker(final String message) {
            this.message = message;
        }
        
        public final void run() {
            try {
                work();
            } catch (Exception ex) {
                caughtException = ex;
            }
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    if (caughtException != null) {
                        reportException(caughtException);
                    } else {
                        finish();
                    }
                }
            });
        }
        
        /**
         * Override this to do the potentially time-consuming work.
         */
        public abstract void work();
        
        /**
         * Override this to do the finishing up that needs to be done back
         * on the event dispatch thread. This will only be invoked if your
         * 'work' method didn't throw an exception (if an exception was
         * thrown, 'reportException' will be invoked instead).
         */
        public abstract void finish();
        
        /**
         * Invoked if 'work' threw an exception. The default implementation
         * simply prints the stack trace. This method is run on the event
         * dispatch thread, so you can safely modify the UI here.
         */
        public void reportException(Exception ex) {
            SimpleDialog.showDetails(ChangeSetWindow.this, message, ex);
        }
    }
    
    void reassessShowPatchAvailability() {
        showPatchButton.setEnabled(filePathToRevisionsMap.size() == fileList.getModel().getSize());
    }
    
    private void readListOfRevisions(final String filePath) {
        if (filePathToRevisionsMap.containsKey(filePath)) {
            return;
        }
        new Thread(new BackEndWorker("Getting list of revisions...") {
            public void work() {
                command = backEnd.getLogCommand(filePath);
                status = ProcessUtilities.backQuote(backEnd.getRoot(), command, lines, errors);
            }
            
            private RevisionListModel parseRevisions() {
                RevisionListModel revisions = backEnd.parseLog(lines);
                if (backEnd.isLocallyModified(filePath)) {
                    revisions.addLocalRevision(Revision.LOCAL_REVISION);
                }
                return revisions;
            }
            
            public void finish() {
                if (status != 0 || errors.size() > 0) {
                    showToolError(fileList, errors, command, status);
                    return;
                }
                
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        reportFileRevisions(filePath, parseRevisions());
                    }
                });
            }
            
            public void reportFileRevisions(String filePath, RevisionListModel fileRevisions) {
                filePathToRevisionsMap.put(filePath, fileRevisions);
                reassessShowPatchAvailability();
            }
        }).start();
    }
    
    private void showPatch() {
        StringBuilder patch = new StringBuilder();
        patch.append("# " + getTitle() + "\n");
        ListModel model = fileList.getModel();
        for (int i = 0; i < model.getSize(); ++i) {
            ChangeSetItem changeSetItem = (ChangeSetItem) model.getElementAt(i);
            
            RevisionListModel revisions = filePathToRevisionsMap.get(changeSetItem.filename);
            // The Subversion back-end gives us old revision numbers which aren't necessarily in the file's history.
            Revision oldRevision = new Revision(changeSetItem.oldRevision, null, null, null, null);            
            Revision newRevision = revisions.fromNumber(changeSetItem.newRevision);
            
            // FIXME: don't repeat the comments for systems where every file in a revision has the same comment.
            // FIXME: we should display the comments for all of the revisions between oldRevision and newRevision.
            for (String commentLine : newRevision.comment.split("\n")) {
                patch.append("# " + commentLine + "\n");
            }
            
            // FIXME: we shouldn't do this on the EDT.
            ArrayList<String> lines = patchView.getPatchLines(backEnd, oldRevision, newRevision, changeSetItem.filename);
            for (String line : lines) {
                patch.append(line + "\n");
            }
        }
        
        PTextArea textArea = JFrameUtilities.makeTextArea(patch.toString());
        textArea.setTextStyler(new PPatchTextStyler(textArea));
        JFrame frame = JFrameUtilities.makeScrollableContentWindow("Patch for " + getTitle(), textArea);
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
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
            // FIXME: Specifying a revision that, with BitKeeper, is file-specific without specifying the file doesn't make sense.
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
                    Revision oldRevision = new Revision(changeSetItem.oldRevision, null, null, null, null);
                    Revision newRevision = new Revision(changeSetItem.newRevision, null, null, null, null);
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
                readListOfRevisions(changeSetItem.filename);
            }
            
            reassessShowPatchAvailability();
            // Guess the user's most likely initial action.
            fileList.setSelectedIndex(0);
            fileList.requestFocus();
        }
    }
}
