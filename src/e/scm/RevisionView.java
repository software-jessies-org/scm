package e.scm;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.*;
import e.forms.*;
import e.gui.*;
import e.ptextarea.*;
import e.util.*;

public class RevisionView extends JComponent {
    private static final String COMMENT_SEPARATOR = "---------------------------------------------------\n";
    private static final ListModel EMPTY_LIST_MODEL = new DefaultListModel();
    
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
        Patch patch = new Patch(backEnd, filePath, fromRevision, toRevision, true, false);
        int toLineNumber = patch.translateLineNumberInFromRevision(fromLineNumber);
        //Log.warn("translateLineNumberInOneStep: (" + fromRevision + ") => (" + toRevision + ") maps (" + fromLineNumber + " => " + toLineNumber + ")");
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
                statusReporter.startTask("Tracing line back to revision " + toRevision.number + " (currently at " + previousRevision.number + ")...");
                Revision revision = revisionRange.get(i);
                lineNumber = translateLineNumberInOneStep(previousRevision, revision, lineNumber);
                previousRevision = revision;
            }
        } catch (Exception ex) {
            // Jumping to the same line number in the target revision isn't ideal, but it's better than not jumping to
            // the right revision and it's better than jumping to the top.
            // The only known reason for there to be an exception is if we were asked to translate a line number
            // into a newer revision.
            // Although the back-end makes this difficult by insisting that diffs be asked for in old-to-new order,
            // the Patch class contains an isPatchReversed boolean to let us work around this.
            // Once we get round to using it.
            Log.warn("Couldn't translate line number", ex);
        } finally {
            statusReporter.finishTask();
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
    
    private class DifferencesDoubleClickListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                Object[] values = revisionsList.getSelectedValues();
                Revision newerRevision = (Revision) values[0];
                Revision olderRevision = (Revision) values[values.length - 1];
                
                ListModel model = patchView.getModel();
                final int index = patchView.locationToIndex(e.getPoint());
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
                    Patch.HunkRange hunkRange = new Patch.HunkRange(line);
                    if (hunkRange.matches()) {
                        olderStartLine = hunkRange.fromBegin();
                        newerStartLine = hunkRange.toBegin();
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
    
    private JTabbedPane mainView;
    private JList annotationView;
    private PatchView patchView;
    
    private StatusReporter statusReporter;
    private JButton changeSetButton;
    private JButton showLogButton;
    
    public RevisionView(String filename, int initialLineNumber) {
        this.backEnd = RevisionControlSystem.forPath(filename);
        this.statusReporter = new StatusReporter(this);
        setFilename(filename);
        makeUserInterface(initialLineNumber);

        readListOfRevisions(initialLineNumber);
    }

    private void makeUserInterface(int initialLineNumber) {
        initRevisionsList();
        initRevisionCommentArea();
        initAnnotationView();
        initPatchView();
        
        mainView = new JTabbedPane();
        mainView.addTab("Annotations", ScmUtilities.makeScrollable(annotationView));
        mainView.addTab("Differences", ScmUtilities.makeScrollable(patchView));
        
        revisionsUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ScmUtilities.makeScrollable(revisionsList), ScmUtilities.makeScrollable(revisionCommentArea));
        revisionsUi.setBorder(null);
        ScmUtilities.sanitizeSplitPaneDivider(revisionsUi);
        
        JSplitPane ui = new JSplitPane(JSplitPane.VERTICAL_SPLIT, revisionsUi, mainView);
        ui.setBorder(null);
        ScmUtilities.sanitizeSplitPaneDivider(ui);

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
            
            private void goToLine(String number) {
                int lineNumber = Integer.parseInt(number);
                showSpecificLineInList(lineNumber, getVisibleList());
            }
            
            private void findText(String searchTerm) {
                JList list = getVisibleList();
                Pattern pattern = PatternUtilities.smartCaseCompile(searchTerm);
                int currentIndex = list.getSelectedIndex();
                ListModel model = list.getModel();
                for (int i = currentIndex + 1; i < model.getSize(); ++i) {
                    String line = model.getElementAt(i).toString();
                    if (pattern.matcher(line).find()) {
                        int lineNumber = i + 1; // Indexes are zero-based.
                        showSpecificLineInList(lineNumber, list);
                        return;
                    }
                }
            }
            
            private JList getVisibleList() {
                return (mainView.getSelectedIndex() == 0) ? annotationView : patchView;
            }
        });
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(changeSetButton);
        buttonPanel.add(Box.createHorizontalStrut(GuiUtilities.getComponentSpacing()));
        buttonPanel.add(showLogButton);
        buttonPanel.add(Box.createHorizontalStrut(GuiUtilities.getComponentSpacing()));
        buttonPanel.add(searchField);
        
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        statusReporter.addToPanel(statusPanel);
        statusPanel.add(buttonPanel, BorderLayout.EAST);
        
        setLayout(new BorderLayout());
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
        annotationView.setCellRenderer(new AnnotatedLineRenderer(this));
        annotationView.setModel(new AnnotationModel());
        annotationView.setVisibleRowCount(34);
    }
    
    private void initPatchView() {
        patchView = new PatchView();
        patchView.addMouseListener(new DifferencesDoubleClickListener());
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
            File file = FileUtilities.fileFromString(filename);
            filename = file.getCanonicalPath();
            this.filePath = filename.substring(backEnd.getRoot().toString().length() + File.separator.length());
            // Cygwin svn requires Unix slash sex.
            this.filePath = FileUtilities.translateFilenameForShellUse(this.filePath);
        } catch (IOException ex) {
            Log.warn("Problem working out repository root", ex);
        }
    }

    private void showAnnotationsForRevision(final Revision revision, final int lineNumber) {
        new Thread(new BackEndWorker(new BackEndTask("Getting annotations for revision " + revision.number + "...", statusReporter)) {
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
                showSpecificLineInList(lineNumber, annotationView);
            }
        }).start();
    }
    
    /**
     * Here, the line number corresponds to a line number in fromRevision.
     */
    private void showAnnotationsForRevision(final Revision toRevision, final Revision fromRevision, final int fromLineNumber) {
        new Thread(new BackEndWorker(new BackEndTask("Tracing line back to revision " + toRevision.number + "...", statusReporter)) {
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
            // The annotations are wrong.
            // They're actually for the head revision, not the locally modified file.
            // We have to fake it by getting a patch and 'applying' it to the annotations.
            Patch patch = new Patch(backEnd, filePath, null, Revision.LOCAL_REVISION, false, false);
            annotationModel.applyPatch(patch.getPatchLines(), revisions);
        }
        annotationView.setModel(annotationModel);
        annotationView.addMouseListener(annotationsDoubleClickListener);
        mainView.setSelectedIndex(0);
    }
    
    public AnnotationModel parseAnnotations(List<String> lines) {
        AnnotationModel result = new AnnotationModel();
        for (String line : lines) {
            result.add(backEnd.parseAnnotatedLine(revisions, line));
        }
        return result;
    }

    private void showSpecificLineInList(int lineNumber, final JList list) {
        // FIXME: this only works while the implementation is synchronous.
        final int index = lineNumber - 1;
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                // Select the appropriate index.
                list.setSelectedIndex(index);
                
                // Center the newly selected item by making an item
                // near the bottom of the area we want visible and
                // then an item near the top visible in turn. We can't
                // just make the item itself visible, because Swing will
                // move the least distance necessary, and the item will
                // tend to be at the very top or very bottom of the visible
                // area, which isn't as useful as being in the middle.
                int visibleLineCount = list.getLastVisibleIndex() - list.getFirstVisibleIndex();
                
                int desiredBottomIndex = index + (visibleLineCount / 2);
                desiredBottomIndex = Math.min(desiredBottomIndex, list.getModel().getSize() - 1);
                list.ensureIndexIsVisible(desiredBottomIndex);
                
                int desiredTopIndex = index - (visibleLineCount / 2);
                desiredTopIndex = Math.max(0, desiredTopIndex);
                list.ensureIndexIsVisible(desiredTopIndex);
            }
        });
    }

    private void showDifferencesBetweenRevisions(final Revision olderRevision, final Revision newerRevision) {
        patchView.showPatch(backEnd, olderRevision, newerRevision, filePath, statusReporter);
        mainView.setSelectedIndex(1);
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
    }

    private void showLog() {
        JFrameUtilities.showTextWindow(this, "Revisions of " + filePath, summaryOfAllRevisions());
    }

    public Revision getAnnotatedRevision() {
        return (Revision) revisionsList.getSelectedValue();
    }
    
    private void readListOfRevisions(final int initialLineNumber) {
        new Thread(new RevisionListWorker(backEnd, new BackEndTask("Getting list of revisions...", statusReporter), filePath, revisionsList) {
            public void reportFileRevisions(RevisionListModel fileRevisions) {
                RevisionView.this.revisions = fileRevisions;
                revisionsList.setModel(revisions);
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
            
            // FIXME: this should only be enabled if the particular revision touched more than one file. I think Revision needs to contain this information, so our initial "svn log" (or whatever) should be "svn log -v", and we can skip making effectively the same request again if/when the user asks to see the change set.
            changeSetButton.setEnabled(backEnd.supportsChangeSets() && values.length == 1);
            
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
}
