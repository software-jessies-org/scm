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

public class RevisionView extends JComponent {
    private static final String COMMENT_SEPARATOR = "---------------------------------------------------\n";
    
    private final AnnotatedLineRenderer annotatedLineRenderer = new AnnotatedLineRenderer(this);
    
    public List<Revision> getRevisionRange(Revision fromRevision, Revision toRevision) {
        ArrayList<Revision> range = new ArrayList<Revision>();
        Revision previousRevision = fromRevision;
        boolean withinRange = false;
        for (int i = 0; i < revisions.getSize(); ++i) {
            Revision revision = (Revision) revisions.getElementAt(i);
            if (withinRange) {
                range.add(revision);
            }
            if (revision == fromRevision) {
                withinRange = true;
            } else if (revision == toRevision) {
                withinRange = false;
            }
            previousRevision = revision;
        }
        if (withinRange) {
            throw new IllegalArgumentException("fromRevision must be newer than toRevision or this code needs fixing");
        }
        return range;
    }
            
    private int translateLineNumberInOneStep(Revision fromRevision, Revision toRevision, int fromLineNumber) {
        Patch patch = new Patch(backEnd, filePath, fromRevision, toRevision);
        int toLineNumber = patch.translateLineNumberInFromRevision(fromLineNumber);
        //System.err.println("(" + fromRevision + ") => (" + toRevision + ") maps (" + fromLineNumber + " => " + toLineNumber + ")");
        return toLineNumber;
    }
    
    public int translateLineNumberStepByStep(Revision fromRevision, Revision toRevision, int lineNumber) {
        if (backEnd.isMetaDataCheap() == false) {
            // FIXME: "return translateLineNumberInOneStep(fromRevision, toRevision, lineNumber);" would be better,
            // but it's still too slow across the Atlantic. Really, cheapness
            // of meta-data access isn't binary. I can imagine sites where you
            // can't afford to trace back step-by-step, but you could afford a
            // single step. Why isn't programming with timeouts easier?
            return lineNumber;
        }
        
        try {
            List<Revision> revisionRange = getRevisionRange(fromRevision, toRevision);
            Revision previousRevision = fromRevision;
            for (int i = 1 /* sic */; i < revisionRange.size(); ++i) {
                setStatus("Tracing line back to revision " + toRevision.number + " (currently at " + previousRevision.number + ")...");
                Revision revision = revisionRange.get(i);
                lineNumber = translateLineNumberInOneStep(previousRevision, revision, lineNumber);
                previousRevision = revision;
            }
            clearStatus();
        } catch (Exception ex) {
            // Jumping to the same line number in the target revision isn't ideal, but it's better than not jumping to
            // the right revision and it's better than jumping to the top.
            // The only known reason for there to be an exception is if we were asked to translate a line number
            // into a newer revision.
            // Although the back-end makes this difficult by insisting that diffs be asked for in old-to-new order,
            // the Patch class contains an isPatchReversed boolean to let us work around this.
            // Once we get round to using it.
            Log.warn("Couldn't translate line number", ex);
        }
        return lineNumber;
    }
    
    private final MouseListener annotationsDoubleClickListener = new MouseAdapter() {
        private int index;
        
        /**
         * Dispatches clicks and double-clicks on annotated lines.
         */
        public void mouseClicked(MouseEvent e) {
            AnnotatedLine annotatedLine = lineForEvent(e);
            if (e.getClickCount() == 2) {
                doubleClick(annotatedLine);
            } else if (e.getClickCount() == 1) {
                click(annotatedLine);
            }
        }
        
        private AnnotatedLine lineForEvent(MouseEvent e) {
            this.index = annotationView.locationToIndex(e.getPoint());
            return (AnnotatedLine) annotationView.getModel().getElementAt(index);
        }
        
        /**
         * Handles double-clicks on annotated lines by switching to the
         * revision of that line.
         */
        private void doubleClick(AnnotatedLine annotatedLine) {
            Revision fromRevision = getAnnotatedRevision();
            Revision toRevision = annotatedLine.revision;
            if (toRevision == fromRevision) {
                return;
            }
            int fromLineNumber = 1 + index;
            showAnnotationsForRevision(toRevision, fromRevision, fromLineNumber);
            revisionsList.setSelectedValue(toRevision, true);
        }
        
        /**
         * Handles single-clicks by showing the comment associated with
         * the line's revision.
         */
        private void click(AnnotatedLine annotatedLine) {
            showComment(annotatedLine.revision.comment);
        }
    };
    
    private final MouseListener differencesDoubleClickListener = new MouseAdapter() {
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
                    Matcher matcher = Patch.AT_AT_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        olderStartLine = Integer.parseInt(matcher.group(1));
                        newerStartLine = Integer.parseInt(matcher.group(3));
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
                
                selectRevision(desiredRevision, desiredLineNumber);
            }
        }
    };
    
    private String filePath;
    
    private RevisionControlSystem backEnd;
    
    // The revision we're annotating, if we're annotating, otherwise null.
    private Revision annotatedRevision;
    // Non-zero if we don't want to keep the currently selected line.
    private int desiredLineNumber = 0;
    
    private AnnotationModel annotationModel;
    private RevisionListModel revisions;
    
    private JSplitPane revisionsUi;
    private JList revisionsList;
    private PTextArea revisionCommentArea;
    
    private JList annotationView;
    
    private JAsynchronousProgressIndicator progressIndicator = new JAsynchronousProgressIndicator();
    private JLabel statusLine = new JLabel(" ");
    private JButton changeSetButton;
    private JButton showLogButton;
    
    private int expectedResultModCount;
    
    public RevisionView(String filename, int initialLineNumber) {
        this.backEnd = RevisionControlSystem.forPath(filename);
        setFilename(filename);
        makeUserInterface(initialLineNumber);

        readListOfRevisions(initialLineNumber);
    }

    private void makeUserInterface(int initialLineNumber) {
        initRevisionsList();
        initRevisionCommentArea();
        initAnnotationView();

        revisionsUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ScmUtilities.makeScrollable(revisionsList), ScmUtilities.makeScrollable(revisionCommentArea));
        revisionsUi.setBorder(null);
        
        JSplitPane ui = new JSplitPane(JSplitPane.VERTICAL_SPLIT, revisionsUi, ScmUtilities.makeScrollable(annotationView));
        ui.setBorder(null);
        ui.setDividerLocation(revisionsUi.getPreferredSize().height);

        changeSetButton = new JButton("Show Change Set");;
        changeSetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new ChangeSetWindow(backEnd, filePath, revisions, getAnnotatedRevision());
            }
        });
        changeSetButton.setEnabled(false);

        showLogButton = new JButton("Show Log");
        showLogButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showLog();
            }
        });
        showLogButton.setEnabled(false);

        final JTextField searchField = new SearchField();
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
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(changeSetButton);
        buttonPanel.add(showLogButton);
        buttonPanel.add(searchField);
        
        JPanel statusPanel = new JPanel(new BorderLayout(4, 0));
        statusPanel.setBorder(new javax.swing.border.EmptyBorder(10, 0, 10, 0));
        statusPanel.add(progressIndicator, BorderLayout.WEST);
        statusPanel.add(statusLine, BorderLayout.CENTER);
        statusPanel.add(buttonPanel, BorderLayout.EAST);
        
        setLayout(new BorderLayout());
        setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        add(ui, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void initRevisionsList() {
        revisionsList = ScmUtilities.makeList();
        revisionsList.setCellRenderer(new RevisionListCellRenderer());
        revisionsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        revisionsList.addListSelectionListener(new RevisionListSelectionListener());
    }

    private void initAnnotationView() {
        annotationView = ScmUtilities.makeList();
        annotationView.setModel(new AnnotationModel());
        annotationView.setVisibleRowCount(34);
    }

    private void initRevisionCommentArea() {
        revisionCommentArea = ScmUtilities.makeTextArea(8);
        revisionCommentArea.setEditable(false);
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
            filename = file.getCanonicalPath();
            this.filePath = filename.substring(backEnd.getRoot().toString().length() + File.separator.length());
        } catch (IOException ex) {
            Log.warn("Problem working out repository root", ex);
        }
    }
    
    /**
     * Similar to BlockingWorker. FIXME: replace both classes with SwingWorker for Java 6.
     */
    public abstract class BackEndWorker implements Runnable {
        // FIXME: This field appears to be used but not initialized.
        private String message;
        private Exception caughtException;
        
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        String[] command;
        int status = 0;
        
        private int thisWorkerModCount;
        
        public BackEndWorker(final String message) {
            setStatus(message);
            thisWorkerModCount = ++expectedResultModCount;
        }
        
        public final void run() {
            try {
                progressIndicator.startAnimation();
                work();
            } catch (Exception ex) {
                caughtException = ex;
            } finally {
                if (expectedResultModCount != thisWorkerModCount) {
                    // This result is outdated. Forget it.
                    // Note that startAnimation and stopAnimation don't nest, so it's correct that we don't call progressIndicator.stopAnimation in this case.
                    return;
                }
                progressIndicator.stopAnimation();
                clearStatus();
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
            SimpleDialog.showDetails(RevisionView.this, message, ex);
        }
    }

    private void showAnnotationsForRevision(final Revision revision, final int lineNumber) {
        new Thread(new BackEndWorker("Getting annotations for revision " + revision.number + "...") {
            public void work() {
                command = backEnd.getAnnotateCommand(revision, filePath);
                status = ProcessUtilities.backQuote(backEnd.getRoot(), command, lines, errors);
            }
            
            public void finish() {
                // CVS writes junk to standard error even on success.
                if (status != 0) {
                    ScmUtilities.showToolError(annotationView, errors, command, status);
                    return;
                }
                
                updateAnnotationModel(revision, lines);
                showSpecificLineInAnnotations(lineNumber);
                setAnnotatedRevision(revision);
            }
        }).start();
    }
    
    /**
     * Here, the line number corresponds to a line number in fromRevision.
     */
    private void showAnnotationsForRevision(final Revision toRevision, final Revision fromRevision, final int fromLineNumber) {
        new Thread(new BackEndWorker("Tracing line back to revision " + toRevision.number + "...") {
            private int toLineNumber;
            
            public void work() {
                toLineNumber = translateLineNumberStepByStep(fromRevision, toRevision, fromLineNumber);
            }
            
            public void finish() {
                showAnnotationsForRevision(toRevision, toLineNumber);
            }
        }).start();
    }

    private void updateAnnotationModel(Revision revision, List<String> lines) {
        annotationModel = parseAnnotations(lines);
        if (revision == Revision.LOCAL_REVISION) {
            // The annotations are wrong. They're actually for the head
            // revision, not the locally modified file. So we have to fake
            // it by getting a patch and 'applying' it to the annotations.
            
            ArrayList<String> patchLines = new ArrayList<String>();
            ArrayList<String> errors = new ArrayList<String>();
            int status = 0;
            /* FIXME: do this in separate thread. */
            WaitCursor waitCursor = new WaitCursor(this, "Getting local modifications...");
            try {
                waitCursor.start();
                String[] command = backEnd.getDifferencesCommand(null, Revision.LOCAL_REVISION, filePath);
                status = ProcessUtilities.backQuote(backEnd.getRoot(), command, patchLines, errors);
            } finally {
                waitCursor.stop();
            }
            
            // CVS returns a non-zero exit status if there were any differences.
            if (errors.size() > 0) {
                ScmUtilities.showToolError(revisionsList, errors);
                return;
            }
            
            annotationModel.applyPatch(patchLines, revisions);
        }
        switchAnnotationView(annotationModel, annotatedLineRenderer, annotationsDoubleClickListener);
    }
    
    public AnnotationModel parseAnnotations(List<String> lines) {
        AnnotationModel result = new AnnotationModel();
        for (String line : lines) {
            result.add(backEnd.parseAnnotatedLine(revisions, line));
        }
        return result;
    }

    private void showSpecificLineInAnnotations(int lineNumber) {
        // FIXME: this only works while the implementation is synchronous.
        final int index = lineNumber - 1;
        EventQueue.invokeLater(new Runnable() {
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

    private void showDifferencesBetweenRevisions(final Revision olderRevision, final Revision newerRevision) {
        new Thread(new BackEndWorker("Getting differences between revisions " + olderRevision.number + " and " + newerRevision.number + "...") {
            public void work() {
                command = backEnd.getDifferencesCommand(olderRevision, newerRevision, filePath);
                status = ProcessUtilities.backQuote(backEnd.getRoot(), command, lines, errors);
                if (wasSuccessful()) {
                    setStatus("Annotating patch...");
                    lines = PatchView.annotatePatchUsingTags(backEnd, lines);
                }
            }
            
            private boolean wasSuccessful() {
                // CVS returns a non-zero exit status if there were any differences, so we can't trust 'status'.
                return (errors.size() == 0);
            }
            
            public void finish() {
                if (wasSuccessful() == false) {
                    ScmUtilities.showToolError(revisionsList, errors);
                    return;
                }
                
                DefaultListModel differences = new DefaultListModel();
                for (String line : lines) {
                    differences.addElement(line);
                }
                switchAnnotationView(differences, PatchListCellRenderer.INSTANCE, differencesDoubleClickListener);
                
                // We can't easily retain the context when switching to differences.
                // As an extension, though, we could do this.
                annotationView.ensureIndexIsVisible(0);
                setAnnotatedRevision(null);
            }
        }).start();
    }

    private String summaryOfAllRevisions() {
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < revisions.getSize(); ++i) {
            Revision revision = (Revision) revisions.getElementAt(i);
            summary.append(revision.summary());
            summary.append(COMMENT_SEPARATOR);
        }
        return summary.toString();
    }

    private void showSummaryOfAllRevisions() {
        showComment(summaryOfAllRevisions());
        annotationView.setModel(EMPTY_LIST_MODEL);
        setAnnotatedRevision(null);
    }

    private void showLog() {
        JFrameUtilities.showTextWindow(this, "Revisions of " + filePath, summaryOfAllRevisions());
    }

    public Revision getAnnotatedRevision() {
        return annotatedRevision;
    }
    
    private void setAnnotatedRevision(Revision revision) {
        annotatedRevision = revision;
        // FIXME: this should only be enabled if the particular revision touched more than one file. I think Revision needs to contain this information, so our initial "svn log" (or whatever) should be "svn log -v", and we can skip making effectively the same request again if/when the user asks to see the change set.
        changeSetButton.setEnabled(backEnd.supportsChangeSets() && annotatedRevision != null);
    }
    
    public abstract class RevisionListWorker extends BackEndWorker {
        protected String filePath;
        private JList listForErrors;
        
        public RevisionListWorker(String filePath, JList listForErrors) {
            super("Getting list of revisions...");
            this.filePath = filePath;
            this.listForErrors = listForErrors;
        }
        
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
                ScmUtilities.showToolError(listForErrors, errors, command, status);
                return;
            }
            
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    reportFileRevisions(parseRevisions());
                }
            });
        }
        
        public abstract void reportFileRevisions(RevisionListModel fileRevisions);
    }
    
    private void readListOfRevisions(final int initialLineNumber) {
        new Thread(new RevisionListWorker(filePath, revisionsList) {
            public void reportFileRevisions(RevisionListModel fileRevisions) {
                revisionsList.setModel(fileRevisions);
                showLogButton.setEnabled(true);
                
                if (initialLineNumber != 0) {
                    Revision revision = revisions.getLatestInRepository();
                    selectRevision(revision, initialLineNumber);
                } else {
                    showSummaryOfAllRevisions();
                }
            }
        }).start();
    }

    public void selectRevision(Revision revision, int lineNumber) {
        revisionsList.clearSelection();
        desiredLineNumber = lineNumber;
        revisionsList.setSelectedValue(revision, true);
        desiredLineNumber = 0;
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

    /**
     * Shows the given text in the revision comment area.
     */
    private void showComment(String comment) {
        revisionCommentArea.setText(comment);
        revisionCommentArea.setCaretPosition(0);
    }

    private class RevisionListSelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
                // We're not interested in transitional states, particularly
                // given how expensive they are if you're talking to a CVS
                // repository that's hundreds of milliseconds away.
                return;
            }
            
            JList list = (JList) e.getSource();
            Object[] values = list.getSelectedValues();
            if (values.length == 0) {
                showSummaryOfAllRevisions();
            } else if (values.length == 1) {
                // Show annotated revision.
                Revision newRevision = (Revision) values[0];
                showComment(newRevision.summary());
                boolean alreadyAnnotating = getAnnotatedRevision() != null;
                boolean overrideCurrentLineNumber = desiredLineNumber != 0;
                if (alreadyAnnotating && overrideCurrentLineNumber == false) {
                    int previouslySelectedLineNumber = annotationView.getSelectedIndex() + 1; // indexes are zero-based
                    showAnnotationsForRevision(newRevision, getAnnotatedRevision(), previouslySelectedLineNumber);
                } else {
                    showAnnotationsForRevision(newRevision, desiredLineNumber);
                }
            } else {
                // Show differences between two revisions.
                Revision newerRevision = (Revision) values[0];
                Revision olderRevision = (Revision) values[values.length - 1];

                StringBuilder comment = new StringBuilder();
                comment.append("Differences between revisions ");
                comment.append(olderRevision.number);
                comment.append(" and ");
                comment.append(newerRevision.number);
                comment.append(":\n\n");
                for (int i = 0; i < values.length - 1; ++i) {
                    Revision revision = (Revision) values[i];
                    comment.append(revision.summary());
                    comment.append(COMMENT_SEPARATOR);
                }

                showComment(comment.toString());
                showDifferencesBetweenRevisions(olderRevision, newerRevision);
            }
        }
    }
    
    // Used to set the split pane divider locations, which are impossibly difficult to get right before the components are realized.
    public void postPack() {
        revisionsUi.setDividerLocation(revisionsUi.getWidth() - revisionCommentArea.getPreferredSize().width);
    }
}
