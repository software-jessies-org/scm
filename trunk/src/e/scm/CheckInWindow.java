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

    private File repositoryRoot;

    private RevisionControlSystem backEnd;
    
    private JTable statusesTable;
    private StatusesTableModel statusesTableModel;
    private JTextArea checkInCommentArea;
    private PatchView patchView;
    private JLabel statusLine = new JLabel(" ");
    private JButton commitButton;
    
    public CheckInWindow() {
        this.repositoryRoot = RevisionWindow.getRepositoryRoot(System.getProperty("user.dir"));
        this.backEnd = RevisionWindow.guessWhichRevisionControlSystem(repositoryRoot);
        setTitle(repositoryRoot.toString());
        makeUserInterface();
        updateFileStatuses();
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
        commitButton.setEnabled(false);

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
                    File file = new File(repositoryRoot, status.getName());
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
                    patchView.showPatch(backEnd, repositoryRoot, null, null, status.getName());
                }
            }
        });
        
        // Try to make JTable look a little less weird.
        statusesTable.setIntercellSpacing(new Dimension());
        statusesTable.setShowGrid(false);
    }

    private void initStatusesTableContextMenu() {
        // FIXME: with Java 1.5, we can simplify this, I think.
        final PopupMenu contextMenu = new PopupMenu();
        
        MenuItem revertItem = new MenuItem("Revert");
        revertItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                revert();
            }
        });
        contextMenu.add(revertItem);
        
        MenuItem historyItem = new MenuItem("Show History...");
        historyItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showHistory();
            }
        });
        contextMenu.add(historyItem);
        
        statusesTable.add(contextMenu);
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
        patchView.setModel(new DefaultListModel());
        String comment = checkInCommentArea.getText();
        if (comment.endsWith("\n") == false) {
            comment += "\n";
        }
        List/*<FileStatus>*/ filenames = statusesTableModel.getIncludedFiles();
        backEnd.commit(repositoryRoot, comment, filenames);
        updateFileStatuses();
        checkInCommentArea.setText("");
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
            File file = FileUtilities.fileFromParentAndString(repositoryRoot.toString(), fileStatus.getName());
            boolean deleted = file.delete();
            System.err.println(deleted);
        } else {
            backEnd.revert(repositoryRoot, fileStatus.getName());
        }
        updateFileStatuses();
    }
    
    /**
     * Invoked when the user chooses "Show History..." from the pop-up menu.
     */
    private void showHistory() {
        FileStatus fileStatus = statusesTableModel.getFileStatus(statusesTable.getSelectedRow());
        if (fileStatus.getState() != FileStatus.NEW) {
            File file = FileUtilities.fileFromParentAndString(repositoryRoot.toString(), fileStatus.getName());
            new RevisionWindow(file.toString(), 1);
        }
    }
    
    private void initCheckInCommentArea() {
        checkInCommentArea = makeTextArea(8);
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
        setStatus("Getting file statuses...");
        Thread worker = new Thread() {
            public void run() {
                List statuses = null;
                try {
                    WaitCursor.start(statusesTable);
                    statuses = backEnd.getStatuses(repositoryRoot);
                } finally {
                    WaitCursor.stop(statusesTable);
                    clearStatus();
                }
                Collections.sort(statuses);
                statusesTableModel = new StatusesTableModel(statuses);
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        statusesTable.setModel(statusesTableModel);
                        statusesTableModel.initColumnWidths(statusesTable);
                        statusesTableModel.addTableModelListener(new StatusesTableModelListener());
                    }
                });
            }
        };
        worker.start();
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
                if (checkInComment.length() > 0 && !checkInComment.endsWith("\n")) {
                    name = "\n" + name;
                }
                checkInCommentArea.append(name);
            }
        }
    }
    
    private void showToolError(JList list, ArrayList lines) {
        list.setCellRenderer(new ToolErrorRenderer());
        list.setModel(new ToolErrorListModel(lines));
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
