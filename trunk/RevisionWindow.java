import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
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
        this.filename = filename;
        this.backEnd = guessWhichRevisionControlSystem();
        
        revisionsList = new JList();
        revisionsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        revisionsList.setFont(FONT);
        revisionsList.addListSelectionListener(new RevisionListSelectionListener());

        revisionCommentArea = new JTextArea(8, 80);
        revisionCommentArea.setDragEnabled(false);
        revisionCommentArea.setEditable(false);
        revisionCommentArea.setFont(FONT);
        revisionCommentArea.setWrapStyleWord(true);
        revisionCommentArea.setLineWrap(true);

        JComponent revisionsUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(revisionsList),
            new JScrollPane(revisionCommentArea,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        );
        revisionsUi.setBorder(null);
        
        annotationView = new JList(new AnnotationModel());
        annotationView.setFont(FONT);
        annotationView.setVisibleRowCount(34);
        
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

        initRevisions(filename);

        if (initialLineNumber != 0) {
            // FIXME: this only works while the implementation is synchronous.
            showAnnotationsForRevision((Revision) revisions.getElementAt(0));
            final int index = initialLineNumber - 1;
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    annotationView.setSelectedIndex(index);
                    int desiredBottomIndex = index +
                        (annotationView.getLastVisibleIndex() -
                         annotationView.getFirstVisibleIndex()) / 2;
                    annotationView.ensureIndexIsVisible(desiredBottomIndex);
                }
            });
        } else {
            showSummaryOfAllRevisions();
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
    
    private void showAnnotationsForRevision(Revision revision) {
        setStatus("Getting annotations for revision...");
        /* FIXME: do rest in separate thread. */
        String[] command = backEnd.getAnnotateCommand(revision, filename);
        ArrayList errors = new ArrayList();
        String[] lines = ProcessUtilities.backQuote(command, errors);
        clearStatus();

        if (backEnd instanceof Cvs && (errors.size() > 3 || ((String) errors.get(errors.size() - 2)).startsWith("Annotations for ") == false || ((String) errors.get(errors.size() - 1)).startsWith("***************") == false)) {
            showToolError(annotationView, errors);
            return;
        }

        history = backEnd.parseAnnotations(revisions, lines);
        annotationView.setModel(history);
        annotationView.setCellRenderer(new AnnotatedLineRenderer());
    }
    
    private void showDifferencesBetweenRevisions(Revision olderRevision, Revision newerRevision) {
        setStatus("Getting differences between revisions...");
        /* FIXME: do rest in separate thread. */
        String[] command = backEnd.getDifferencesCommand(olderRevision, newerRevision, filename);
        ArrayList errors = new ArrayList();
        String[] lines = ProcessUtilities.backQuote(command, errors);
        clearStatus();

        if (errors.size() > 0) {
            showToolError(revisionsList, errors);
            return;
        }

        DefaultListModel differences = new DefaultListModel();
        for (int i = 0; i < lines.length; ++i) {
            differences.addElement(lines[i]);
        }
        annotationView.setModel(differences);
        annotationView.setCellRenderer(new DifferencesRenderer());

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
        annotationView.setModel(new DefaultListModel());
    }

    private void initRevisions(String filename) {
        setStatus("Getting list of revisions...");
        /* FIXME: do rest in separate thread. */
        String[] command = backEnd.getLogCommand(filename);
        ArrayList errors = new ArrayList();
        String[] lines = ProcessUtilities.backQuote(command, errors);
        clearStatus();
        
        if (errors.size() > 0) {
            showToolError(revisionsList, errors);
            return;
        }

        revisions = backEnd.parseLog(lines);
        revisionsList.setModel(revisions);
    }

    private void showToolError(JList list, ArrayList lines) {
        list.setModel(new ToolErrorListModel(lines));
        list.setCellRenderer(new ToolErrorRenderer());
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
                showAnnotationsForRevision(revision);
            } else {
                // Show differences between two revisions.
                Revision newerRevision = (Revision) values[0];
                Revision olderRevision = (Revision) values[values.length - 1];
                // FIXME: should we join all the revision comments and show that?
                revisionCommentArea.setText("Differences between revisions " + olderRevision.number + " and " + newerRevision.number + "\n");
                showDifferencesBetweenRevisions(olderRevision, newerRevision);
            }
        }
    }
}
