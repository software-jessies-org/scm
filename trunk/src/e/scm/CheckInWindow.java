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
                updateSavedState();
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
                if (e.getValueIsAdjusting() == false) {
                    updatePatchView();
                }
            }
        });
        
        // Try to make JTable look a little less weird.
        statusesTable.setIntercellSpacing(new Dimension());
        statusesTable.setShowGrid(false);
    }
    
    private void updatePatchView() {
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
    
    private class EditFileAction extends AbstractAction {
        EditFileAction() {
            super("Edit File");
        }
        public void actionPerformed(ActionEvent e) {
            editFileAtLine(1);
        }
    }
    
    private class DiscardChangesAction extends AbstractAction {
        DiscardChangesAction() {
            super("Discard Changes");
        }
        public void actionPerformed(ActionEvent e) {
            discardChanges();
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
        contextMenu.add(new DiscardChangesAction());
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
        updateSavedState();
    }
    
    /**
     * Invoked when the user chooses "Discard Changes" from the pop-up menu.
     */
    private void discardChanges() {
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
    
    /**
     * Edits the currently selected file, at the given line number.
     */
    private void editFileAtLine(int lineNumber) {
        FileStatus fileStatus = statusesTableModel.getFileStatus(statusesTable.getSelectedRow());
        String command = getEditor() + " " + fileStatus.getName() + ":" + lineNumber;
        ProcessUtilities.spawn(backEnd.getRoot(), new String[] { "bash", "-c", command });
    }
    
    private String getEditor() {
        String result = null;
        try {
            result = System.getenv("SCM_EDITOR");
            if (result == null) {
                result = System.getenv("EDITOR");
            }
        } catch (Error error) {
            // Sun were really stupid there. Ignore them and wait for 1.5!
            error = error;
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
        checkInCommentArea.setText(readSavedStateFile(getSavedCommentFile()));
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
        updateSavedStateFile(getSavedCommentFile(), checkInCommentArea.getText());
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

    /**
     * Returns a copy of the given list with any entries corresponding to
     * SCM's own dot files removed. This saves the user from having to teach
     * their back-end to ignore our files.
     */
    private List/*<FileStatuses>*/ removeScmDotFiles(List/*<FileStatus>*/ list) {
        ArrayList result = new ArrayList();
        for (int i = 0; i < list.size(); ++i) {
            FileStatus fileStatus = (FileStatus) list.get(i);
            if (fileStatus.getName().startsWith(".e.scm.") == false) {
                result.add(fileStatus);
            }
        }
        return result;
    }
    
    private void updateFileStatuses() {
        new BlockingWorker(statusesTable, "Getting file statuses...") {
            List oldIncludedFiles;
            List statuses;
            
            public void work() {
                oldIncludedFiles = (statusesTableModel != null) ? statusesTableModel.getIncludedFiles() : null;
                commitButton.setEnabled(false);
                statuses = backEnd.getStatuses(getWaitCursor());
                statuses = removeScmDotFiles(statuses);
                Collections.sort(statuses);
                statusesTableModel = new StatusesTableModel(statuses);
            }
            
            public void finish() {
                statusesTable.setModel(statusesTableModel);
                statusesTableModel.initColumnWidths(statusesTable);
                statusesTableModel.addTableModelListener(new StatusesTableModelListener());
                if (oldIncludedFiles != null) {
                    statusesTableModel.includeFiles(oldIncludedFiles);
                } else {
                    readSavedFilenames();
                }
                
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
