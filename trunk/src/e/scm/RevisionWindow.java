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

public class RevisionWindow extends JFrame {
    private static final Font FONT;
    static {
        // FIXME: this is nasty. Why is there no Font.parse or Font.fromString?
        if (System.getProperty("os.name").indexOf("Mac") != -1) {
            FONT = new Font("Monaco", Font.PLAIN, 10);
        } else {
            FONT = new Font("Monospaced", Font.PLAIN, 12);
        }
    }

    private final AnnotatedLineRenderer annotatedLineRenderer =
        new AnnotatedLineRenderer(this);
    private static final DifferencesRenderer DIFFERENCES_RENDERER =
        new DifferencesRenderer();

    private final MouseListener annotationsDoubleClickListener =
        new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    final int i = annotationView.locationToIndex(e.getPoint());
                    Object value = annotationView.getModel().getElementAt(i);
                    if (value instanceof AnnotatedLine) {
                        AnnotatedLine annotatedLine = (AnnotatedLine) value;
                        
                        Revision fromRevision = getSelectedRevision();
                        Revision toRevision = annotatedLine.revision;
                        Patch patch = new Patch(fromRevision, toRevision);
                        final int lineNumber = patch.translateLineNumberInFromRevision(1 + i);
                        
                        selectRevision(toRevision);
                        showAnnotationsForRevision(toRevision, lineNumber);
                    }
                }
            }
        };

    /** Matches the "@@ -111,41 +113,41 @@" lines at the start of a hunk. */
    private static final Pattern AT_AT_PATTERN =
        Pattern.compile("^@@ -(\\d+),\\d+ \\+(\\d+),\\d+ @@$");

    private final MouseListener differencesDoubleClickListener =
        new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Object[] values = revisionsList.getSelectedValues();
                    Revision newerRevision = (Revision) values[0];
                    Revision olderRevision = (Revision) values[values.length - 1];

                    ListModel model = annotationView.getModel();
                    final int index = annotationView.locationToIndex(e.getPoint());
                    String lineOfInterest = (String) model.getElementAt(index);

                    // Only lines removed or added can be jumped to.
                    if (lineOfInterest.matches("^[-+].*") == false) {
                        // FIXME: give some feedback?
                        return;
                    }

                    // What revision are we going to?
                    Revision desiredRevision = olderRevision;
                    if (lineOfInterest.startsWith("+")) {
                        desiredRevision = newerRevision;
                    }

                    // Search backwards for the previous @@ line to help find out where we are.
                    int newerStartLine = 0;
                    int olderStartLine = 0;
                    int linesIntoHunk = 0;
                    for (int i = index; i >= 0; --i) {
                        String line = (String) model.getElementAt(i);
                        Matcher matcher = AT_AT_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            olderStartLine = Integer.parseInt(matcher.group(1));
                            newerStartLine = Integer.parseInt(matcher.group(2));
                            break;
                        } else if (line.startsWith("+")) {
                            if (desiredRevision == newerRevision) {
                                ++linesIntoHunk;
                            }
                        } else if (line.startsWith("-")) {
                            if (desiredRevision == olderRevision) {
                                ++linesIntoHunk;
                            }
                        } else {
                            // Context lines count for both revisions.
                            ++linesIntoHunk;
                        }
                    }

                    // The line numbers in the @@ line actually refer to the
                    // line after.
                    --newerStartLine;
                    --olderStartLine;

                    int startLine = ((desiredRevision == olderRevision) ? olderStartLine : newerStartLine);
                    int desiredLineNumber = startLine + linesIntoHunk;

                    if (false) {
                        // Remove this when I'm confident this code is right.
                        System.err.println("going to " + ((desiredRevision == olderRevision) ? "older" : "newer") + " revision");
                        System.err.println("old=" + olderStartLine + " new=" + newerStartLine);
                        System.err.println("linesIntoHunk=" + linesIntoHunk);
                        System.err.println("startLine=" + startLine + "  desiredLineNumber=" + desiredLineNumber);
                    }

                    selectRevision(desiredRevision);
                    showAnnotationsForRevision(desiredRevision, desiredLineNumber);
                }
            }
        };

    private File repositoryRoot;
    private String filePath;

    private RevisionControlSystem backEnd;
    
    private AnnotationModel annotationModel;
    private RevisionListModel revisions;
    
    private JList revisionsList;
    private JTextArea revisionCommentArea;
    private JList annotationView;
    private JLabel statusLine = new JLabel(" ");
    
    public RevisionWindow(String filename, int initialLineNumber) {
        super(filename);
        setFilename(filename);
        this.backEnd = guessWhichRevisionControlSystem(repositoryRoot);
        makeUserInterface(initialLineNumber);

        readListOfRevisions();
        if (initialLineNumber != 0) {
            showAnnotationsForRevision(revisions.getLatestInRepository(), initialLineNumber);
        } else {
            showSummaryOfAllRevisions();
        }
    }

    private void makeUserInterface(int initialLineNumber) {
        initRevisionsList();
        initRevisionCommentArea();
        initAnnotationView();

        JComponent revisionsUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(revisionsList),
            new JScrollPane(revisionCommentArea,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        );
        revisionsUi.setBorder(null);
        
        JSplitPane ui = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            revisionsUi,
            new JScrollPane(annotationView));
        ui.setBorder(null);

        JButton changeSetButton = new JButton("Show Change Set");
        if (backEnd.supportsChangeSets()) {
            changeSetButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    backEnd.showChangeSet(repositoryRoot, filePath, getSelectedRevision());
                }
            });
        } else {
            changeSetButton.setEnabled(false);
        }

        JButton showLogButton = new JButton("Show Log");
        showLogButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showLog();
            }
        });

        JPanel buttonsPanel = new JPanel(new BorderLayout());
        buttonsPanel.add(changeSetButton, BorderLayout.EAST);
        buttonsPanel.add(showLogButton, BorderLayout.WEST);
        
        final JTextField searchField = new JTextField(10);
        searchField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String searchTerm = searchField.getText();
                if (searchTerm.matches("^:\\d+$")) {
                    goToLine(searchTerm.substring(1));
                } else {
                    findText(searchTerm);
                }
            }
            
            public void goToLine(String number) {
                int lineNumber = Integer.parseInt(number);
                showSpecificLineInAnnotations(lineNumber);
            }
            
            public void findText(String searchTerm) {
                Pattern pattern = Pattern.compile(searchTerm);
                int currentIndex = annotationView.getSelectedIndex();
                ListModel model = annotationView.getModel();
                for (int i = currentIndex + 1; i < model.getSize(); ++i) {
                    String line = model.getElementAt(i).toString();
                    if (pattern.matcher(line).find()) {
                        int lineNumber = i + 1; // Indexes are zero-based.
                        showSpecificLineInAnnotations(lineNumber);
                        return;
                    }
                }
            }
        });
        
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(new javax.swing.border.EmptyBorder(10, 0, 10, 0));
        statusPanel.add(statusLine, BorderLayout.CENTER);
        statusPanel.add(buttonsPanel, BorderLayout.WEST);
        statusPanel.add(searchField, BorderLayout.EAST);

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

    private void initRevisionsList() {
        revisionsList = new JList();
        revisionsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        revisionsList.setFont(FONT);
        revisionsList.addListSelectionListener(new RevisionListSelectionListener());
    }

    private void initAnnotationView() {
        annotationView = new JList(new AnnotationModel());
        annotationView.setFont(FONT);
        annotationView.setVisibleRowCount(34);
        ActionMap actionMap = annotationView.getActionMap();
        actionMap.put("copy", new AbstractAction("copy") {
            public void actionPerformed(ActionEvent e) {
                copyAnnotationViewSelectionToClipboard();
            }
        });
    }

    private void initRevisionCommentArea() {
        revisionCommentArea = makeTextArea(8);
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

    /**
     * Works out the repository root from the filename and keeps a path
     * to the file relative to the repository root.
     *
     * Follows symbolic links, so we get the revision history associated
     * with the target.
     */
    private void setFilename(String filename) {
        try {
            File file = new File(filename);
            String absoluteFilename = file.getAbsolutePath();
            String canonicalFilename = file.getCanonicalPath();
            if (canonicalFilename.equals(absoluteFilename) == false) {
                filename = canonicalFilename;
            } else {
                filename = absoluteFilename;
            }
            this.repositoryRoot = getRepositoryRoot(filename);
            this.filePath = filename.substring(repositoryRoot.toString().length() + File.separator.length());
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
    
    public static File getRepositoryRoot(String filename) {
        String clue = null;
        
        File file = new File(new File(filename).getAbsolutePath());
        File directory = new File(file.getParent());
        String[] siblings = directory.list();
        for (int i = 0; i < siblings.length; ++i) {
            if (siblings[i].equals("CVS")) {
                clue = "CVS";
            } else if (siblings[i].equals("SCCS")) {
                clue = "SCCS";
            } else if (siblings[i].equals(".svn")) {
                clue = ".svn";
            }
        }
        
        File root = directory;
        while (true) {
            File newRoot = new File(root.getParent());
            if (newRoot.exists() == false || newRoot.isDirectory() == false) {
                return root;
            }
            File scmDirectory = new File(newRoot, clue);
            if (scmDirectory.exists() == false || scmDirectory.isDirectory() == false) {
                return root;
            }
            root = newRoot;
        }
    }

    /**
     * Attempts to guess which revision control system the file we're
     * interested in is managed by. We do this simply on the basis of
     * whether there's a CVS, SCCS or .svn directory in the same directory
     * as the file.
     */
    public static RevisionControlSystem guessWhichRevisionControlSystem(File repositoryRoot) {
        String[] siblings = repositoryRoot.list();
        for (int i = 0; i < siblings.length; ++i) {
            if (siblings[i].equals("CVS")) {
                return new Cvs();
            } else if (siblings[i].equals("SCCS")) {
                return new BitKeeper();
            } else if (siblings[i].equals(".svn")) {
                return new Subversion();
            }
        }
        // We know this is wrong, but CVS is likely to be installed,
        // and will give a reasonably good error when invoked.
        return new Cvs();
    }
    
    private void showAnnotationsForRevision(Revision revision, int lineNumber) {
        setStatus("Getting annotations for revision...");
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = 0;
        try {
            WaitCursor.start(this);
            /* FIXME: do this in separate thread. */
            String[] command = backEnd.getAnnotateCommand(revision, filePath);
            status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        } finally {
            WaitCursor.stop(this);
            clearStatus();
        }

        // CVS writes junk to standard error even on success.
        if (status != 0) {
            showToolError(annotationView, errors);
            return;
        }

        updateAnnotationModel(lines);
        showSpecificLineInAnnotations(lineNumber);
    }
    
    private void updateAnnotationModel(List lines) {
        annotationModel = parseAnnotations(lines);
        switchAnnotationView(annotationModel, annotatedLineRenderer, annotationsDoubleClickListener);
    }
    
    public AnnotationModel parseAnnotations(List lines) {
        AnnotationModel result = new AnnotationModel();
        for (int i = 0; i < lines.size(); ++i) {
            result.add(backEnd.parseAnnotatedLine(revisions, (String) lines.get(i)));
        }
        return result;
    }

    private void showSpecificLineInAnnotations(int lineNumber) {
        // FIXME: this only works while the implementation is synchronous.
        final int index = lineNumber - 1;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // Select the appropriate index.
                annotationView.setSelectedIndex(index);
                
                // Center the newly selected item by making an item
                // near the bottom of the area we want visible and
                // then an item near the top visible in turn. We can't
                // just make the item itself visible, because Swing will
                // move the least distance necessary, and the item will
                // tend to be at the very top or very bottom of the visible
                // area, which isn't as useful as being in the middle.
                int visibleLineCount = annotationView.getLastVisibleIndex() -
                     annotationView.getFirstVisibleIndex();
                
                int desiredBottomIndex = index + (visibleLineCount / 2);
                desiredBottomIndex = Math.min(desiredBottomIndex,
                    annotationView.getModel().getSize() - 1);
                annotationView.ensureIndexIsVisible(desiredBottomIndex);
                
                int desiredTopIndex = index - (visibleLineCount / 2);
                desiredTopIndex = Math.max(0, desiredTopIndex);
                annotationView.ensureIndexIsVisible(desiredTopIndex);
            }
        });
    }

    private static final ListModel EMPTY_LIST_MODEL = new DefaultListModel();

    /**
     * Switches the annotation view's list model and cell renderer. This
     * is slightly more involved than it first seems, because the renderer
     * may make assumptions about its associated list model. So we put in
     * an empty list model, switch to a renderer suitable for the incoming
     * list model, and only then set the new model.
     */
    private void switchAnnotationView(ListModel listModel, ListCellRenderer renderer, MouseListener doubleClickListener) {
        annotationView.setModel(EMPTY_LIST_MODEL);
        annotationView.setCellRenderer(renderer);
        annotationView.setModel(listModel);
        //FIXME: maybe we should have two JLists on a CardLayout?
        annotationView.removeMouseListener(annotationsDoubleClickListener);
        annotationView.removeMouseListener(differencesDoubleClickListener);
        annotationView.addMouseListener(doubleClickListener);
    }

    private void copyAnnotationViewSelectionToClipboard() {
        // Make a StringSelection corresponding to the selected lines.
        StringBuffer buffer = new StringBuffer();
        Object[] selectedLines = annotationView.getSelectedValues();
        for (int i = 0; i < selectedLines.length; ++i) {
            String line = selectedLines[i].toString();
            buffer.append(line);
            buffer.append('\n');
        }
        StringSelection selection = new StringSelection(buffer.toString());

        // Set the clipboard (and X11's nasty hacky semi-duplicate).
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        toolkit.getSystemClipboard().setContents(selection, selection);
        if (toolkit.getSystemSelection() != null) {
            toolkit.getSystemSelection().setContents(selection, selection);
        }
    }
    
    private void showDifferencesBetweenRevisions(Revision olderRevision, Revision newerRevision) {
        setStatus("Getting differences between revisions...");
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = 0;
        /* FIXME: do this in separate thread. */
        try {
            WaitCursor.start(this);
            String[] command = backEnd.getDifferencesCommand(olderRevision, newerRevision, filePath);
            status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        } finally {
            WaitCursor.stop(this);
            clearStatus();
        }

        // CVS returns a non-zero exit status if there were any differences.
        if (errors.size() > 0) {
            showToolError(revisionsList, errors);
            return;
        }

        DefaultListModel differences = new DefaultListModel();
        for (int i = 0; i < lines.size(); ++i) {
            differences.addElement((String) lines.get(i));
        }
        switchAnnotationView(differences, DIFFERENCES_RENDERER, differencesDoubleClickListener);

        // We can't easily retain the context when switching to differences.
        // As an extension, though, we could do this.
        annotationView.ensureIndexIsVisible(0);
    }

    private String summaryOfAllRevisions() {
        StringBuffer summary = new StringBuffer();
        for (int i = 0; i < revisions.getSize(); ++i) {
            Revision revision = (Revision) revisions.getElementAt(i);

            summary.append(revision);
            summary.append("\n");

            summary.append(revision.comment);
            summary.append("\n");
        }
        return summary.toString();
    }

    private void showSummaryOfAllRevisions() {
        revisionCommentArea.setText(summaryOfAllRevisions());
        revisionCommentArea.setCaretPosition(0);
        annotationView.setModel(EMPTY_LIST_MODEL);
    }

    private void showLog() {
        JTextArea summary = makeTextArea(20);
        summary.setText(summaryOfAllRevisions());
        summary.setCaretPosition(0);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(summary), BorderLayout.CENTER);

        JFrame frame = new JFrame("Revisions of " + filePath);
        frame.setContentPane(panel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
    }

    public Revision getSelectedRevision() {
        return (Revision) revisionsList.getSelectedValue();
    }

    private void readListOfRevisions() {
        setStatus("Getting list of revisions...");
        /* FIXME: do rest in separate thread. */
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = 0;
        try {
            WaitCursor.start(this);
            String[] command = backEnd.getLogCommand(filePath);
            status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        } finally {
            WaitCursor.stop(this);
            clearStatus();
        }
        
        if (status != 0 || errors.size() > 0) {
            showToolError(revisionsList, errors);
            return;
        }

        revisions = backEnd.parseLog(lines);
        if (backEnd.isLocallyModified(repositoryRoot, filePath)) {
            revisions.addLocalRevision(Revision.LOCAL_REVISION);
        }
        revisionsList.setModel(revisions);
    }

    public void selectRevision(Revision revision) {
        revisionsList.clearSelection();
        revisionsList.setSelectedValue(revision, true);
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

    private class RevisionListSelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            JList list = (JList) e.getSource();
            Object[] values = list.getSelectedValues();
            if (values.length == 0) {
                showSummaryOfAllRevisions();
            } else if (values.length == 1) {
                // Show annotated revision.
                Revision revision = (Revision) values[0];
                revisionCommentArea.setText(revision.comment);
                revisionCommentArea.setCaretPosition(0);
                showAnnotationsForRevision(revision, 0);
            } else {
                // Show differences between two revisions.
                Revision newerRevision = (Revision) values[0];
                Revision olderRevision = (Revision) values[values.length - 1];

                StringBuffer comment = new StringBuffer();
                comment.append("Differences between revisions ");
                comment.append(olderRevision.number);
                comment.append(" and ");
                comment.append(newerRevision.number);
                comment.append("\n");
                for (int i = 0; i < values.length - 1; ++i) {
                    Revision revision = (Revision) values[i];
                    comment.append("\n(");
                    comment.append(revision.number);
                    comment.append(") ");
                    comment.append(revision.comment);
                }

                revisionCommentArea.setText(comment.toString());
                showDifferencesBetweenRevisions(olderRevision, newerRevision);
            }
        }
    }
}
