package e.scm;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import e.gui.*;
import e.util.*;

public class CheckInWindow extends JFrame {
    private static final Font FONT;
    static {
        // FIXME: this is nasty. Why is there no Font.parse or Font.fromString?
        if (System.getProperty("os.name").indexOf("Mac") != -1) {
            FONT = new Font("Monaco", Font.PLAIN, 10);
        } else {
            FONT = new Font("Monospaced", Font.PLAIN, 12);
        }
    }

    private RevisionControlSystem backEnd;
    
    private JTable statusesTable;
    private StatusesTableModel statusesTableModel;
    private JTextArea checkInCommentArea;
    private PatchView patchView;
    private JLabel statusLine = new JLabel(" ");
    private JButton commitButton;
    
    public CheckInWindow(final RevisionControlSystem backEnd) {
        this.backEnd = backEnd;
        setTitle(FileUtilities.getUserFriendlyName(backEnd.getRoot().toString()));
        makeUserInterface();
        updateFileStatuses();
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                updateSavedComment();
            }
        });
    }

    private void makeUserInterface() {
        initStatusesList();
        initCheckInCommentArea();
        
        // Give the statuses table a sensible amount of space.
        JScrollPane statusesScrollPane = new JScrollPane(statusesTable);
        Dimension preferredSize = statusesScrollPane.getPreferredSize();
        preferredSize.height = getFontMetrics(FONT).getHeight() * 10;
        statusesScrollPane.setPreferredSize(preferredSize);
        statusesScrollPane.getViewport().setBackground(statusesTable.getBackground());
        
        JSplitPane topUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            statusesScrollPane,
            new JScrollPane(checkInCommentArea,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        );
        topUi.setBorder(null);
        
        patchView = new PatchView();
        patchView.setFont(FONT);
        
        JSplitPane ui = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            topUi,
            new JScrollPane(patchView));
        ui.setBorder(null);
        
        commitButton = new JButton("Commit");
        commitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                commit();
            }
        });

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLine, BorderLayout.CENTER);
        statusPanel.add(commitButton, BorderLayout.EAST);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        contentPane.add(ui, BorderLayout.CENTER);
        contentPane.add(statusPanel, BorderLayout.SOUTH);
        setContentPane(contentPane);
        pack();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initStatusesList() {
        statusesTable = new ETable();
        statusesTable.setTableHeader(null);
        statusesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        statusesTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        statusesTable.setFont(FONT);
        initStatusesTableContextMenu();
        statusesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                
                final int selectedRow = statusesTable.getSelectedRow();
                if (selectedRow == -1) {
                    return;
                }
                
                FileStatus status = statusesTableModel.getFileStatus(selectedRow);
                if (status.getState() == FileStatus.NEW) {
                    DefaultListModel model = new DefaultListModel();
                    File file = new File(backEnd.getRoot(), status.getName());
                    if (file.isDirectory()) {
                        model.addElement("(" + status.getName() + " is a directory.)");
                    } else {
                        String[] lines = StringUtilities.readLinesFromFile(file.getAbsolutePath());
                        for (int i = 0; i < lines.length; ++i) {
                            model.addElement(lines[i]);
                        }
                        if (model.getSize() == 0) {
                            model.addElement("(" + status.getName() + " is empty.)");
                        }
                    }
                    patchView.setModel(model);
                } else {
                    patchView.showPatch(backEnd, null, null, status.getName());
                }
            }
        });
        
        // Try to make JTable look a little less weird.
        statusesTable.setIntercellSpacing(new Dimension());
        statusesTable.setShowGrid(false);
    }
    
    private class EditFileAction extends AbstractAction {
        EditFileAction() {
            super("Edit File");
        }
        public void actionPerformed(ActionEvent e) {
            editFile();
        }
    }
    
    private class RevertFileAction extends AbstractAction {
        RevertFileAction() {
            super("Revert File");
        }
        public void actionPerformed(ActionEvent e) {
            revert();
        }
    }
    
    private class ShowHistoryAction extends AbstractAction {
        ShowHistoryAction() {
            super("Show History...");
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
        final EPopupMenu contextMenu = new EPopupMenu();
        contextMenu.add(new EditFileAction());
        contextMenu.add(new RevertFileAction());
        contextMenu.addSeparator();
        contextMenu.add(new ShowHistoryAction());
        contextMenu.addSeparator();
        contextMenu.add(new RefreshListAction());
        statusesTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                maybeShowContextMenu(e);
            }
            public void mouseReleased(MouseEvent e) {
                maybeShowContextMenu(e);
            }
            private void maybeShowContextMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }
    
    private void commit() {
        commitButton.setEnabled(false);
        patchView.setModel(new DefaultListModel());
        new BlockingWorker(statusesTable, "Committing changes...") {
            public void work() {
                String comment = checkInCommentArea.getText();
                if (comment.endsWith("\n") == false) {
                    comment += "\n";
                }
                List/*<FileStatus>*/ filenames = statusesTableModel.getIncludedFiles();
                backEnd.commit(comment, filenames);
            }
            
            public void finish() {
                updateFileStatuses();
                clearCheckInCommentArea();
            }
        };
    }
    
    private void clearCheckInCommentArea() {
        checkInCommentArea.setText("");
        updateSavedComment();
    }

    public abstract class BlockingWorker extends Thread {
        private Component component;
        private String message;
        private Exception caughtException;
        
        public BlockingWorker(Component component, String message) {
            this.component = component;
            this.message = message;
            start();
        }
        
        public final void run() {
            try {
                WaitCursor.start(component, message);
                work();
            } catch (Exception ex) {
                caughtException = ex;
            } finally {
                WaitCursor.stop(component);
            }
            SwingUtilities.invokeLater(new Runnable() {
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
            ex.printStackTrace();
        }
    }
    
    /**
     * Invoked when the user chooses "Revert" from the pop-up menu.
     */
    private void revert() {
        patchView.setModel(new DefaultListModel());
        FileStatus fileStatus = statusesTableModel.getFileStatus(statusesTable.getSelectedRow());
        if (fileStatus.getState() == FileStatus.NEW) {
            // If the file isn't under version control, we'll have to remove
            // it ourselves...
            File file = FileUtilities.fileFromParentAndString(backEnd.getRoot().toString(), fileStatus.getName());
            boolean deleted = file.delete();
            System.err.println(deleted);
        } else {
            backEnd.revert(fileStatus.getName());
        }
        updateFileStatuses();
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
    
    private void editFile() {
        FileStatus fileStatus = statusesTableModel.getFileStatus(statusesTable.getSelectedRow());
        String command = getEditor() + " " + fileStatus.getName();
        ProcessUtilities.spawn(backEnd.getRoot(), new String[] { "bash", "-c", command });
    }
    
    private String getEditor() {
        String result = System.getenv("SCM_EDITOR");
        if (result == null) {
            result = System.getenv("EDITOR");
        }
        if (result == null) {
            result = "vi";
        }
        return result;
    }
    
    private void initCheckInCommentArea() {
        checkInCommentArea = makeTextArea(8);
        readSavedComment();
    }
    
    private void readSavedComment() {
        File savedCommentFile = getSavedCommentFile();
        if (savedCommentFile.exists()) {
            String savedComment = StringUtilities.readFile(savedCommentFile);
            checkInCommentArea.setText(savedComment);
        }
    }
    
    private void updateSavedComment() {
        String comment = checkInCommentArea.getText();
        File savedCommentFile = getSavedCommentFile();
        if (savedCommentFile.exists()) {
            savedCommentFile.delete();
        }
        if (comment.length() != 0) {
            StringUtilities.writeFile(savedCommentFile, comment);
        }
    }

    private File getSavedCommentFile() {
        return FileUtilities.fileFromParentAndString(backEnd.getRoot().toString(), ".e.scm.CheckInWindow.savedComment");
    }
    
    private JTextArea makeTextArea(final int rowCount) {
        JTextArea textArea = new JTextArea(rowCount, 80);
        textArea.setDragEnabled(false);
        textArea.setFont(FONT);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        e.gui.JTextComponentSpellingChecker spellingChecker = new e.gui.JTextComponentSpellingChecker(textArea);
        spellingChecker.setDocument(textArea.getDocument());
        return textArea;
    }

    private void updateFileStatuses() {
        new BlockingWorker(statusesTable, "Getting file statuses...") {
            List oldIncludedFiles;
            List statuses;
            
            public void work() {
                oldIncludedFiles = (statusesTableModel != null) ? statusesTableModel.getIncludedFiles() : new ArrayList();
                commitButton.setEnabled(false);
                statuses = backEnd.getStatuses();
                Collections.sort(statuses);
                statusesTableModel = new StatusesTableModel(statuses);
            }
            
            public void finish() {
                statusesTable.setModel(statusesTableModel);
                statusesTableModel.initColumnWidths(statusesTable);
                statusesTableModel.addTableModelListener(new StatusesTableModelListener());
                statusesTableModel.includeFiles(oldIncludedFiles);
                
                /* Give some feedback to demonstrate we're not broken if there's nothing to show. */
                boolean nothingModified = (statusesTableModel.getRowCount() == 0);
                if (nothingModified) {
                    DefaultListModel model = new DefaultListModel();
                    model.addElement("(Nothing to check in.)");
                    patchView.setModel(model);
                }
            }
        };
    }

    private class StatusesTableModelListener implements TableModelListener {
        public void tableChanged(TableModelEvent e) {
            commitButton.setEnabled(statusesTableModel.isAtLeastOneFileIncluded());
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) {
                checkBoxUpdated(e);
            }
        }

        /**
         * Attempt to be helpful by inserting a file's name in the check-in
         * comment when the file is marked for inclusion in this commit.
         */
        private void checkBoxUpdated(TableModelEvent e) {
            if (statusesTableModel.isIncluded(e.getFirstRow()) == false) {
                return;
            }
            
            FileStatus fileStatus = statusesTableModel.getFileStatus(e.getFirstRow());
            String name = fileStatus.getName() + ": ";
            String checkInComment = checkInCommentArea.getText();
            if (checkInComment.indexOf(name) == -1) {
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
                if (checkInComment.length() > 0) {
                    if (checkInComment.endsWith(": ")) {
                        name = "\n" + name;
                    } else {
                        if (checkInComment.endsWith("\n") == false) {
                            name = "\n" + name;
                        }
                        name = "\n" + name;
                    }
                }
                checkInCommentArea.append(name);
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        checkInCommentArea.requestFocus();
                    }
                });
            }
        }
    }
    
    public void clearStatus() {
        setStatus("");
    }

    public void setStatus(String message) {
        if (message.length() == 0) {
            message = " ";
        }
        statusLine.setText(message);
    }
}
