import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

public class RevisionWindow extends JFrame {
    private static final Font FONT = new Font("Monaco", Font.PLAIN, 10);

    private String filename;
    
    private AnnotationModel history;
    private RevisionListModel revisions;
    
    private JList revisionsList;
    private JTextArea revisionCommentArea;
    private JList annotationView;
    private JLabel statusLine = new JLabel(" ");
    
    public RevisionWindow(String filename, int initialLineNumber) {
        super(filename);
        this.filename = filename;
        
        revisionsList = new JList();
        revisionsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        revisionsList.setFont(new Font("Monaco", Font.PLAIN, 10));
        revisionsList.addListSelectionListener(new RevisionListSelectionListener());

        revisionCommentArea = new JTextArea(8, 80);
        revisionCommentArea.setDragEnabled(false);
        revisionCommentArea.setEditable(false);
        revisionCommentArea.setFont(FONT);

        JComponent revisionsUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(revisionsList),
            new JScrollPane(revisionCommentArea,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        );
        
        annotationView = new JList(new AnnotationModel());
        annotationView.setFont(FONT);
        annotationView.setVisibleRowCount(34);
        
        JSplitPane ui = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            revisionsUi,
            new JScrollPane(annotationView));

        getContentPane().add(ui, BorderLayout.CENTER);
        getContentPane().add(statusLine, BorderLayout.SOUTH);
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
                    annotationView.ensureIndexIsVisible(index);
                }
            });
        }
    }
    
    private void showAnnotationsForRevision(Revision revision) {
        setStatus("Getting annotations for revision...");
        /* FIXME: do rest in separate thread. */
        String[] command = new String[] { "cvs", "annotate", "-r", revision.number, filename };
        ArrayList errors = new ArrayList();
        String[] lines = ProcessUtilities.backQuote(command, errors);
        clearStatus();

        if (errors.size() != 2 || ((String) errors.get(0)).startsWith("Annotations for ") == false || ((String) errors.get(1)).startsWith("***************") == false) {
            System.err.println(errors.size());
            showToolError(annotationView, errors);
            return;
        }

        history = new AnnotationModel();
        for (int i = 0; i < lines.length; ++i) {
            history.add(new AnnotatedLine(revisions, lines[i]));
        }
        annotationView.setModel(history);
        annotationView.setCellRenderer(new AnnotatedLineRenderer());
    }
    
    private void showDifferencesBetweenRevisions(Revision olderRevision, Revision newerRevision) {
        setStatus("Getting differences between revisions...");
        /* FIXME: do rest in separate thread. */
        String[] command = new String[] { "cvs", "diff", "-u", "-kk", "-r", olderRevision.number, "-r", newerRevision.number, filename };
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
    }

    private void initRevisions(String filename) {
        setStatus("Getting list of revisions...");
        /* FIXME: do rest in separate thread. */
        String[] command = new String[] { "cvs", "log", filename };
        ArrayList errors = new ArrayList();
        String[] lines = ProcessUtilities.backQuote(command, errors);
        clearStatus();
        
        if (errors.size() > 0) {
            showToolError(revisionsList, errors);
            return;
        }

        String separator = "----------------------------";
        String endMarker = "=============================================================================";
        int i = 0;
        for (; i < lines.length && lines[i].equals(separator) == false; ++i) {
            // Skip header.
        }
        
        revisions = new RevisionListModel();
        while (i < lines.length && lines[i].equals(endMarker) == false) {
            if (lines[i].equals(separator)) {
                i++;
                continue;
            }
            String number = lines[i++];
            number = number.substring("revision ".length());
            String info = lines[i++];
            StringBuffer comment = new StringBuffer();
            while (i < lines.length && lines[i].equals(endMarker) == false && lines[i].equals(separator) == false) {
                comment.append(lines[i++]);
                comment.append("\n");
            }
            revisions.add(new Revision(number, info, comment.toString()));
        }
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
            if (values.length == 1) {
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
                revisionCommentArea.setText("<differences between " + olderRevision.number + " and " + newerRevision.number + ">");
                showDifferencesBetweenRevisions(olderRevision, newerRevision);
            }
        }
    }
}
