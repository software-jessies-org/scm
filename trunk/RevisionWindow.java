import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
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
                        selectRevision(annotatedLine.revision);
                        final int lineNumber = 1 + i; // FIXME: this needs to take the patch between the current and the target revisions into account; the line may have a different line number in the target revision.
                        showAnnotationsForRevision(annotatedLine.revision, lineNumber);
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

    private String filename;

    private RevisionControlSystem backEnd;
    
    private AnnotationModel history;
    private RevisionListModel revisions;
    
    private JList revisionsList;
    private JTextArea revisionCommentArea;
    private JList annotationView;
    private JLabel statusLine = new JLabel(" ");
    
    public RevisionWindow(String filename, int initialLineNumber) {
        super(filename);
        setFilename(filename);
        this.backEnd = guessWhichRevisionControlSystem();
        makeUserInterface(initialLineNumber);

        readListOfRevisions(filename);
        if (initialLineNumber != 0) {
            // revisions[0] is the local revision.
            // revisions[1] is the latest revision in the repository.
            showAnnotationsForRevision((Revision) revisions.getElementAt(1), initialLineNumber);
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

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        contentPane.add(ui, BorderLayout.CENTER);
        contentPane.add(statusLine, BorderLayout.SOUTH);
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
        revisionCommentArea = new JTextArea(8, 80);
        revisionCommentArea.setDragEnabled(false);
        revisionCommentArea.setEditable(false);
        revisionCommentArea.setFont(FONT);
        revisionCommentArea.setWrapStyleWord(true);
        revisionCommentArea.setLineWrap(true);
    }

    /**
     * Follows symbolic links, so we get the revision history associated
     * with the target.
     */
    private void setFilename(String filename) {
        this.filename = filename;
        try {
            File file = new File(filename);
            String absoluteFilename = file.getAbsolutePath();
            String canonicalFilename = file.getCanonicalPath();
            if (canonicalFilename.equals(absoluteFilename) == false) {
                this.filename = canonicalFilename;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Attempts to guess which revision control system the file we're
     * interested in is managed by. We do this simply on the basis of
     * whether there's a CVS or an SCCS directory in the same directory
     * as the file.
     */
    private RevisionControlSystem guessWhichRevisionControlSystem() {
        File file = new File(new File(filename).getAbsolutePath());
        File directory = new File(file.getParent());
        String[] siblings = directory.list();
        for (int i = 0; i < siblings.length; ++i) {
            if (siblings[i].equals("CVS")) {
                return new Cvs();
            } else if (siblings[i].equals("SCCS")) {
                return new BitKeeper();
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
            String[] command = backEnd.getAnnotateCommand(revision, filename);
            status = ProcessUtilities.backQuote(command, lines, errors);
        } finally {
            WaitCursor.stop(this);
            clearStatus();
        }

        // CVS writes junk to standard error even on success.
        if (status != 0) {
            showToolError(annotationView, errors);
            return;
        }

        history = backEnd.parseAnnotations(revisions, lines);
        switchAnnotationView(history, annotatedLineRenderer, annotationsDoubleClickListener);
        showSpecificLineInAnnotations(lineNumber);
    }

    private void showSpecificLineInAnnotations(int lineNumber) {
        // FIXME: this only works while the implementation is synchronous.
        final int index = lineNumber - 1;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                annotationView.setSelectedIndex(index);
                int desiredBottomIndex = index +
                    (annotationView.getLastVisibleIndex() -
                     annotationView.getFirstVisibleIndex()) / 2;
                annotationView.ensureIndexIsVisible(desiredBottomIndex);
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
            String[] command = backEnd.getDifferencesCommand(olderRevision, newerRevision, filename);
            status = ProcessUtilities.backQuote(command, lines, errors);
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

    private void showSummaryOfAllRevisions() {
        StringBuffer summary = new StringBuffer();
        for (int i = 0; i < revisions.getSize(); ++i) {
            Revision revision = (Revision) revisions.getElementAt(i);

            summary.append(revision);
            summary.append("\n");

            summary.append(revision.comment);
            summary.append("\n");
        }
        revisionCommentArea.setText(summary.toString());
        revisionCommentArea.setCaretPosition(0);
        annotationView.setModel(EMPTY_LIST_MODEL);
    }

    public Revision getSelectedRevision() {
        return (Revision) revisionsList.getSelectedValue();
    }

    private void readListOfRevisions(String filename) {
        setStatus("Getting list of revisions...");
        /* FIXME: do rest in separate thread. */
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = 0;
        try {
            WaitCursor.start(this);
            String[] command = backEnd.getLogCommand(filename);
            status = ProcessUtilities.backQuote(command, lines, errors);
        } finally {
            WaitCursor.stop(this);
            clearStatus();
        }
        
        if (status != 0 || errors.size() > 0) {
            showToolError(revisionsList, errors);
            return;
        }

        revisions = backEnd.parseLog(lines);
        if (backEnd.isLocallyModified(filename)) {
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
