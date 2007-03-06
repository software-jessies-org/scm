package e.scm;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.*;
import e.gui.*;
import e.ptextarea.*;
import e.util.*;

public class CheckInWindow extends JFrame {
    private RevisionControlSystem backEnd;
    
    private JTable statusesTable;
    private StatusesTableModel statusesTableModel;
    private PTextArea checkInCommentArea;
    private PatchView patchView;
    private StatusReporter statusReporter;
    private JButton commitButton;
    
    public CheckInWindow(final RevisionControlSystem backEnd) {
        this.backEnd = backEnd;
        setTitle(FileUtilities.getUserFriendlyName(backEnd.getRoot().toString()));
        JFrameUtilities.setFrameIcon(this);
        makeUserInterface();
        updateFileStatuses();
        initQuitMonitoring();
    }
    
    private void initQuitMonitoring() {
        // On the Mac, a user's likely to quit with C-Q, and not close our window at all.
        new com.apple.eawt.Application().addApplicationListener(new com.apple.eawt.ApplicationAdapter() {
            public void handleQuit(com.apple.eawt.ApplicationEvent e) {
                CheckInWindow.this.handleQuit();
                e.setHandled(true);
            }
        });
        // Elsewhere, it's window closing we need to watch for.
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                handleQuit();
            }
        });
    }
    
    private void handleQuit() {
        // If we got as far as being fully initialized, save our state.
        if (statusesTableModel != null) {
                updateSavedState();
        }
    }
    
    private void makeUserInterface() {
        initStatusesList();
        initCheckInCommentArea();
        
        // Give the statuses table a sensible amount of space.
        JScrollPane statusesScrollPane = ScmUtilities.makeScrollable(statusesTable);
        Dimension preferredSize = statusesScrollPane.getPreferredSize();
        preferredSize.height = getFontMetrics(statusesTable.getFont()).getHeight() * 10;
        statusesScrollPane.setPreferredSize(preferredSize);
        statusesScrollPane.getViewport().setBackground(statusesTable.getBackground());
        
        JSplitPane topUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, statusesScrollPane, ScmUtilities.makeScrollable(checkInCommentArea));
        topUi.setBorder(null);
        
        patchView = new PatchView();
        patchView.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                int index = patchView.locationToIndex(e.getPoint());
                int lineNumber = patchView.lineNumberInNewerRevisionAtIndex(index);
                editFileAtLine(lineNumber);
            }
        });
        ComponentUtilities.divertPageScrollingFromTo(statusesTable, patchView);
        
        // Whenever our window regains focus, make sure we're showing up-to-date information.
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                updatePatchView();
            }
        });
        
        JSplitPane ui = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topUi, ScmUtilities.makeScrollable(patchView));
        ui.setBorder(null);
        
        commitButton = new JButton("Commit");
        commitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                commit();
            }
        });
        if (GuiUtilities.isGtk()) {
            // Ubuntu's "Update Manager" uses this icon, which seems appropriate for "do the right -- yet not default -- thing".
            GnomeStockIcon.useStockIcon(commitButton, "gtk-yes");
        }
        commitButton.setEnabled(false);
        
        this.statusReporter = new StatusReporter(this);
        
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusReporter.addToPanel(statusPanel);
        statusPanel.add(commitButton, BorderLayout.EAST);
        if (GuiUtilities.isMacOs() == false) {
            // On Mac OS, there's already a big enough gap between the top of the "Commit" button and the bottom of the patch view.
            // On other systems, the layout is too tight.
            statusPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        }
        
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(ScmUtilities.getFrameBorder());
        contentPane.add(ui, BorderLayout.CENTER);
        contentPane.add(statusPanel, BorderLayout.SOUTH);
        setContentPane(contentPane);
        pack();
        ScmUtilities.sanitizeSplitPaneDivider(ui);
        ScmUtilities.sanitizeSplitPaneDivider(topUi);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        JFrameUtilities.constrainToScreen(this);
        setVisible(true);
        GuiUtilities.finishGnomeStartup();
    }

    private void initStatusesList() {
        statusesTable = new ETable();
        statusesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        statusesTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        initStatusesTableContextMenu();
        statusesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() == false) {
                    updatePatchView();
                }
            }
        });
    }
    
    private File lastPatchViewFile = null;
    private long lastPatchViewUpdateTime = 0;
    
    private void updatePatchView() {
        final int selectedRow = statusesTable.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }
        
        FileStatus status = statusesTableModel.getFileStatus(selectedRow);
        File file = new File(backEnd.getRoot(), status.getName());
        long updateTime = file.lastModified();
        if (file.equals(lastPatchViewFile) && updateTime <= lastPatchViewUpdateTime) {
            return;
        }
        
        lastPatchViewFile = file;
        lastPatchViewUpdateTime = updateTime;
        
        if (status.getState() == FileStatus.NEW) {
            patchView.showNewFile(backEnd, status);
        } else {
            patchView.showPatch(backEnd, null, null, status.getName(), statusReporter);
        }
    }
    
    private class EditFileAction extends AbstractAction {
        EditFileAction() {
            super("Edit File");
        }
        public boolean isEnabled() {
            return (statusesTable.getSelectedRow() != -1);
        }
        public void actionPerformed(ActionEvent e) {
            editFileAtLine(1);
        }
    }
    
    private class DiscardChangesAction extends AbstractAction {
        DiscardChangesAction(String name) {
            super(name);
        }
        public boolean isEnabled() {
            return (statusesTable.getSelectedRow() != -1);
        }
        public void actionPerformed(ActionEvent e) {
            discardChanges();
        }
    }
    
    private class ShowHistoryAction extends AbstractAction {
        ShowHistoryAction() {
            super("Show History...");
        }
        public boolean isEnabled() {
            return (statusesTable.getSelectedRow() != -1);
        }
        public void actionPerformed(ActionEvent e) {
            showHistory();
        }
    }
    
    private class RefreshListAction extends AbstractAction {
        RefreshListAction() {
            super("Refresh List");
        }
        public void actionPerformed(ActionEvent e) {
            updateFileStatuses();
        }
    }
    
    private void initStatusesTableContextMenu() {
        EPopupMenu menu = new EPopupMenu(statusesTable);
        menu.addMenuItemProvider(new MenuItemProvider() {
            public void provideMenuItems(MouseEvent e, Collection<Action> actions) {
                actions.add(new EditFileAction());
                actions.add(new DiscardChangesAction(chooseDiscardActionName()));
                actions.add(null);
                actions.add(new ShowHistoryAction());
                actions.add(null);
                actions.add(new RefreshListAction());
            }
            
            // It doesn't make sense to talk about discarding changes to an unmodified file.
            // FIXME: should we disable the action if the file's been added or removed?
            private String chooseDiscardActionName() {
                String result = "Discard";
                int row = statusesTable.getSelectedRow();
                if (row != -1) {
                    FileStatus fileStatus = statusesTableModel.getFileStatus(row);
                    String quotedFilename = "'" + fileStatus.getName() + "'";
                    result += ((fileStatus.getState() == FileStatus.MODIFIED) ? " Changes to " : " File ") + quotedFilename;
                }
                return result;
            }
        });
    }
    
    private String getCommentWithNewline() {
        String comment = checkInCommentArea.getText();
        if (comment.endsWith("\n") == false) {
            comment += "\n";
        }
        return comment;
    }
    
    private void setEntireUiEnabled(boolean newState) {
        checkInCommentArea.setEnabled(newState);
        commitButton.setEnabled(newState);
        patchView.setEnabled(newState);
        statusesTable.setEnabled(newState);
    }
    
    private void commit() {
        setEntireUiEnabled(false);
        
        final String comment = getCommentWithNewline();
        final List<FileStatus> filenames = statusesTableModel.getIncludedFiles();
        
        new Thread(new BlockingWorker(statusesTable, "Committing changes...", statusReporter) {
            public void work() {
                try {
                    backEnd.commit(comment, filenames);
                } catch (RuntimeException ex) {
                    // We know the button must have been enabled, or we couldn't
                    // have got here. If the commit failed, the user is likely
                    // to want to try again.
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            setEntireUiEnabled(true);
                        }
                    });
                    // Re-throw so that "finish()" isn't run, and the user can
                    // see that something went wrong. (Though obviously, the
                    // console isn't the best place to report that.)
                    throw ex;
                }
            }
            
            public void finish() {
                discardSavedState();
                updateFileStatuses();
            }
        }).start();
    }
    
    private void discardSavedState() {
        checkInCommentArea.setText("");
        updateSavedState();
    }
    
    /**
     * Invoked when the user chooses "Discard Changes" from the pop-up menu.
     */
    private void discardChanges() {
        setEntireUiEnabled(false);
        new Thread(new BlockingWorker(statusesTable, "Discarding changes...", statusReporter) {
            @Override
            public void work() {
                // Which file?
                FileStatus fileStatus = statusesTableModel.getFileStatus(statusesTable.getSelectedRow());
                String filename = fileStatus.getName();
                File file = FileUtilities.fileFromParentAndString(backEnd.getRoot().toString(), filename);
                
                // Keep a backup. The file may not exist if the change we're discarding
                // is a local "rm".
                // FIXME: Recognize that case ('!' status in svn), and change the UI to something like "restore"?
                if (file.exists() && file.isFile()) {
                    File backupFile = FileUtilities.fileFromParentAndString(System.getProperty("java.io.tmpdir"), StringUtilities.urlEncode(filename));
                    FileUtilities.copyFile(file, backupFile);
                }
                // FIXME: offer an "Undo Discard Changes" menu option?
                
                // Revert.
                if (fileStatus.getState() == FileStatus.NEW) {
                    // If the file isn't under version control, we'll have to remove it ourselves...
                    boolean deleted = file.delete();
                    if (deleted == false) {
                        // FIXME: try to find out why we failed, and tell the user rather than guessing.
                        SimpleDialog.showAlert(CheckInWindow.this, "Couldn't delete \"" + FileUtilities.getUserFriendlyName(file) + "\"", "Is it or its parent write-protected, or a non-empty directory?");
                    }
                } else {
                    backEnd.revert(filename);
                }
            }
            
            @Override
            public void finish() {
                updateFileStatuses();
            }
            
            @Override
            public void reportException(Exception ex) {
                super.reportException(ex);
                setEntireUiEnabled(true);
            }
        }).start();
    }
    
    /**
     * Invoked when the user chooses "Show History..." from the pop-up menu.
     */
    private void showHistory() {
        FileStatus fileStatus = statusesTableModel.getFileStatus(statusesTable.getSelectedRow());
        if (fileStatus.getState() != FileStatus.NEW) {
            File file = FileUtilities.fileFromParentAndString(backEnd.getRoot().toString(), fileStatus.getName());
            new RevisionWindow(file.toString(), 1);
        }
    }
    
    /**
     * Edits the currently selected file, at the given line number.
     */
    private void editFileAtLine(int lineNumber) {
        String editor = System.getenv("SCM_EDITOR");
        if (editor == null) {
            SimpleDialog.showAlert(this, "Couldn't start external editor", "Set the environment variable $SCM_EDITOR to the command you want to be invoked.");
            return;
        }
        
        FileStatus fileStatus = statusesTableModel.getFileStatus(statusesTable.getSelectedRow());
        String command = editor + " +" + lineNumber + " " + fileStatus.getName();
        ProcessUtilities.spawn(backEnd.getRoot(), new String[] { "bash", "-c", command });
    }
    
    private void initCheckInCommentArea() {
        checkInCommentArea = ScmUtilities.makeTextArea(8);
        checkInCommentArea.setEnabled(false);
        BugDatabaseHighlighter.highlightBugs(checkInCommentArea);
    }
    
    private void configureCheckInCommentArea(boolean initially) {
        boolean haveFiles = statusesTableModel.isAtLeastOneFileIncluded();
        if (haveFiles) {
            // Only read the saved comment if we're configuring the comment area at start-up.
            String text = (initially ? readSavedStateFile(getSavedCommentFile()) : "");
            checkInCommentArea.setText(text);
            checkInCommentArea.setEnabled(true);
        } else {
            checkInCommentArea.setEnabled(false);
            checkInCommentArea.setText(INSTRUCTIONS);
        }
    }
    
    private void readSavedFilenames() {
        String[] filenames = readSavedStateFile(getSavedFilenamesFile()).split("\n");
        statusesTableModel.includeFilenames(Arrays.asList(filenames));
    }
    
    private String readSavedStateFile(File file) {
        if (file.exists() == false) {
            return "";
        }
        return StringUtilities.readFile(file);
    }
    
    private void updateSavedState() {
        String comment = statusesTableModel.isAtLeastOneFileIncluded() ? checkInCommentArea.getText() : "";
        updateSavedStateFile(getSavedCommentFile(), comment);
        updateSavedStateFile(getSavedFilenamesFile(), StringUtilities.join(statusesTableModel.getIncludedFilenames(), "\n"));
    }

    private void updateSavedStateFile(File file, String newContent) {
        if (file.exists()) {
            file.delete();
        }
        if (newContent.length() != 0) {
            StringUtilities.writeFile(file, newContent);
        }
    }
    
    private File getSavedCommentFile() {
        return FileUtilities.fileFromParentAndString(backEnd.getRoot().toString(), ".e.scm.CheckInWindow.savedComment");
    }
    
    private File getSavedFilenamesFile() {
        return FileUtilities.fileFromParentAndString(backEnd.getRoot().toString(), ".e.scm.CheckInWindow.savedFilenames");
    }
    
    /**
     * Returns a copy of the given list with any entries corresponding to
     * SCM's own dot files removed. This saves the user from having to teach
     * their back-end to ignore our files.
     */
    private List<FileStatus> removeScmDotFiles(List<FileStatus> list) {
        ArrayList<FileStatus> result = new ArrayList<FileStatus>();
        for (FileStatus fileStatus : list) {
            if (fileStatus.getName().startsWith(".e.scm.") == false) {
                result.add(fileStatus);
            }
        }
        return result;
    }
    
    private void updateFileStatuses() {
        setEntireUiEnabled(false);
        new Thread(new BlockingWorker(statusesTable, "Getting file statuses...", statusReporter) {
            List<FileStatus> statuses;
            Exception failure;
            
            public void work() {
                if (statusesTableModel != null) {
                    updateSavedState();
                }
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        commitButton.setEnabled(false);
                    }
                });
                try {
                    statuses = backEnd.getStatuses(statusReporter);
                } catch (Exception ex) {
                    statuses = new ArrayList<FileStatus>();
                    failure = ex;
                }
                statuses = removeScmDotFiles(statuses);
                Collections.sort(statuses);
            }
            
            public void finish() {
                statusesTableModel = new StatusesTableModel(statuses);
                statusesTable.setModel(statusesTableModel);
                statusesTable.setEnabled(true);
                statusesTableModel.initColumnWidths(statusesTable);
                statusesTableModel.addTableModelListener(new StatusesTableModelListener());
                statusesTableModel.fireTableDataChanged();
                readSavedFilenames();
                configureCheckInCommentArea(true);
                
                /* Give some feedback to demonstrate we're not broken if there's nothing to show. */
                boolean nothingModified = (statusesTableModel.getRowCount() == 0);
                if (nothingModified) {
                    DefaultListModel model = new DefaultListModel();
                    model.addElement("(Nothing to check in.)");
                    patchView.setModel(model);
                }
                patchView.setEnabled(true);
                
                if (failure != null) {
                    SimpleDialog.showDetails(CheckInWindow.this, "Back-End Problem", failure);
                }
                
                /*
                 * If a user's working from the keyboard, they probably want
                 * to see the first patch and have the focus in the statuses
                 * table, so they can start reviewing changes.
                 */
                if (statusesTable.getRowCount() != 0) {
                    int chosenRow = statusesTableModel.chooseDefaultSelectedRow();
                    statusesTable.getSelectionModel().setSelectionInterval(chosenRow, chosenRow);
                    statusesTable.requestFocusInWindow();
                }
            }
        }).start();
    }
    
    private static final String INSTRUCTIONS = "\n 1. Check all those files in the list to the left that you wish to commit.\n\n 2. Edit the resulting comment in this area.\n\n 3. Click the \"Commit\" button when done.";
        
    private class StatusesTableModelListener implements TableModelListener {
        public void tableChanged(TableModelEvent e) {
            boolean haveFiles = statusesTableModel.isAtLeastOneFileIncluded();
            commitButton.setEnabled(haveFiles);
            
            updateStatusLine();

            if (checkInCommentArea.isEnabled() != haveFiles) {
                configureCheckInCommentArea(false);
            }
            
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) {
                checkBoxUpdated(e);
            }
        }

        /**
         * Attempt to be helpful by inserting a file's name in the check-in
         * comment when the file is marked for inclusion in this commit.
         */
        private void checkBoxUpdated(TableModelEvent e) {
            boolean addedFile = statusesTableModel.isIncluded(e.getFirstRow());
            
            FileStatus fileStatus = statusesTableModel.getFileStatus(e.getFirstRow());
            String name = fileStatus.getName();
            String checkInComment = checkInCommentArea.getText();
            boolean filenameAlreadyIncluded = ("\n" + checkInComment).contains("\n" + name + ":");
            if (addedFile && filenameAlreadyIncluded == false) {
                /**
                 * We want to encourage check-in comments like this:
                 * 
                 * src/dir/file.cpp:
                 * src/dir/file.h: comment here.
                 * 
                 * src/dir/header.h: a separate comment.
                 * 
                 * with just a newline between adjacent filenames, but a
                 * blank line if you've actually added a comment.
                 */
                if (checkInComment.length() > 0 && checkInComment.endsWith("\n\n") == false) {
                    Matcher lastLineMatcher = Pattern.compile("(.*): ?$").matcher(checkInComment);
                    boolean isLastLineLabel = lastLineMatcher.find() && isAnIncludedFilename(lastLineMatcher.group(1));
                    if (checkInComment.endsWith("\n") || isLastLineLabel) {
                        checkInCommentArea.append("\n");
                    } else {
                        checkInCommentArea.append("\n\n");
                    }
                }
                checkInCommentArea.append(name + ": ");
            } else if (filenameAlreadyIncluded && addedFile == false) {
                // Try to remove the existing filename (but not any
                // accompanying text). This works well enough for the typical
                // case where you accidentally include a file and immediately
                // exclude it without typing anything.
                String pattern = "(\n|^)" + StringUtilities.regularExpressionFromLiteral(name) + ": \n?";
                String newComment = checkInComment.replaceFirst(pattern, "");
                checkInCommentArea.setText(newComment);
            } else {
                return;
            }
            
            final int newOffset = checkInCommentArea.getTextBuffer().length();
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    checkInCommentArea.select(newOffset, newOffset);
                    checkInCommentArea.requestFocusInWindow();
                }
            });
        }
    }
    
    private boolean isAnIncludedFilename(String filename) {
        return statusesTableModel.getIncludedFilenames().contains(filename);
    }
    
    private void updateStatusLine() {
        String message = "";
        int rowCount = statusesTableModel.getRowCount();
        if (rowCount > 1) {
            message = statusesTableModel.getIncludedFileCount() + " of " + StringUtilities.pluralize(rowCount, "file", "files") + " will be committed.";
        }
        statusReporter.setMessage(message);
    }
}
