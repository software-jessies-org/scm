package e.scm;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.*;

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
    
    private JList statusesList;
    private JTextArea checkInCommentArea;
    private JLabel statusLine = new JLabel(" ");
    
    public CheckInWindow() {
        this.repositoryRoot = RevisionWindow.getRepositoryRoot(System.getProperty("user.dir"));
        this.backEnd = RevisionWindow.guessWhichRevisionControlSystem(repositoryRoot);
        setTitle(repositoryRoot.toString());
        makeUserInterface();
    }

    private void makeUserInterface() {
        initStatusesList();
        initCheckInCommentArea();
        initAnnotationView();

        JComponent topUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(statusesList),
            new JScrollPane(checkInCommentArea,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        );
        topUi.setBorder(null);
        
        /*
        JSplitPane ui = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            topUi,
            new JScrollPane(annotationView));
        ui.setBorder(null);
        */
        JComponent ui = topUi;
        
        JButton commitButton = new JButton("Commit");
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
        statusesList = new JList();
        statusesList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        statusesList.setFont(FONT);
        //statusesList.addListSelectionListener(new RevisionListSelectionListener());
    }

    private void initAnnotationView() {
        /*
        annotationView = new JList(new AnnotationModel());
        annotationView.setFont(FONT);
        annotationView.setVisibleRowCount(34);
        ActionMap actionMap = annotationView.getActionMap();
        actionMap.put("copy", new AbstractAction("copy") {
            public void actionPerformed(ActionEvent e) {
                copyAnnotationViewSelectionToClipboard();
            }
        });
        */
    }

    private void commit() {
        // FIXME!
    }
    
    private void initCheckInCommentArea() {
        checkInCommentArea = makeTextArea(8);
    }

    private JTextArea makeTextArea(final int rowCount) {
        JTextArea textArea = new JTextArea(rowCount, 80);
        textArea.setDragEnabled(false);
        textArea.setEditable(false);
        textArea.setFont(FONT);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        return textArea;
    }

    private void readListOfRevisions() {
        setStatus("Getting file statuses...");
        // FIXME: i want to avoid this style, and have the back-end return a List<Status>.
        /*
        // FIXME: do rest in separate thread.
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = 0;
        try {
            WaitCursor.start(this);
            String[] command = backEnd.getStatusCommand(filePath);
            status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        } finally {
            WaitCursor.stop(this);
            clearStatus();
        }
        
        if (status != 0 || errors.size() > 0) {
            showToolError(statusesList, errors);
            return;
        }

        statuses = backEnd.parseLog(lines);
        if (backEnd.isLocallyModified(repositoryRoot, filePath)) {
            revisions.addLocalRevision(Revision.LOCAL_REVISION);
        }
        statusesList.setModel(statuses);
        */
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
