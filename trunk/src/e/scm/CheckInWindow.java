package e.scm;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.*;
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
        statusesTable = new JTable();
        statusesTable.setTableHeader(null);
        statusesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        statusesTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        statusesTable.setFont(FONT);
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
    }
    
    private void commit() {
        String comment = checkInCommentArea.getText();
        if (comment.endsWith("\n") == false) {
            comment += "\n";
        }
        List filenames = statusesTableModel.getIncludedFilenames();
        backEnd.commit(repositoryRoot, comment, filenames);
        updateFileStatuses();
        checkInCommentArea.setText("");
        patchView.setModel(new DefaultListModel());
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
        List statuses = null;
        try {
            WaitCursor.start(this);
            statuses = backEnd.getStatuses(repositoryRoot);
        } finally {
            WaitCursor.stop(this);
            clearStatus();
        }
        
        statusesTableModel = new StatusesTableModel(statuses);
        statusesTable.setModel(statusesTableModel);
        statusesTableModel.initColumnWidths(statusesTable);
        statusesTableModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                commitButton.setEnabled(statusesTableModel.isAtLeastOneFileIncluded());
            }
        });
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