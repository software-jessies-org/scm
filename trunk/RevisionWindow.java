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
        revisionsList.setFont(new Font("Monaco", Font.PLAIN, 10));
        revisionsList.addListSelectionListener(new RevisionListSelectionListener());

        revisionCommentArea = new JTextArea(8, 80);
        revisionCommentArea.setDragEnabled(false);
        revisionCommentArea.setEditable(false);
        revisionCommentArea.setFont(FONT);

        JComponent revisionDetail = new JScrollPane(revisionCommentArea);
        JComponent revisionsUi = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(revisionsList), revisionDetail);
        
        annotationView = new JList(new AnnotationModel());
        annotationView.setFont(FONT);
        annotationView.setVisibleRowCount(34);
        JComponent detailUi = new JScrollPane(annotationView);
        
        JSplitPane ui = new JSplitPane(JSplitPane.VERTICAL_SPLIT, revisionsUi, detailUi);

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
        String[] lines = ProcessUtilities.backQuote(command);
        history = new AnnotationModel();
        for (int i = 0; i < lines.length; ++i) {
            history.add(new AnnotatedLine(revisions, lines[i]));
        }
        annotationView.setModel(history);
        annotationView.setCellRenderer(new AnnotatedLineRenderer());
        clearStatus();
    }
    
    private void showDifferencesBetweenRevisions(Revision olderRevision, Revision newerRevision) {
        setStatus("Getting differences between revisions...");
        /* FIXME: do rest in separate thread. */
        String[] command = new String[] { "cvs", "diff", "-u", "-kk", "-r", olderRevision.number, "-r", newerRevision.number, filename };
        String[] lines = ProcessUtilities.backQuote(command);
        DefaultListModel differences = new DefaultListModel();
        for (int i = 0; i < lines.length; ++i) {
            differences.addElement(lines[i]);
        }
        annotationView.setModel(differences);
        annotationView.setCellRenderer(new DifferencesRenderer());
        clearStatus();
    }

    private void initRevisions(String filename) {
        setStatus("Getting list of revisions...");
        /* FIXME: do rest in separate thread. */
        String[] command = new String[] { "cvs", "log", filename };
        String[] lines = ProcessUtilities.backQuote(command);
        
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
        clearStatus();
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
                Revision revision = (Revision) values[0];
                revisionCommentArea.setText(revision.comment);
                revisionCommentArea.setCaretPosition(0);
                showAnnotationsForRevision(revision);
            } else if (values.length == 2) {
                // show diff.
                Revision newerRevision = (Revision) values[0];
                Revision olderRevision = (Revision) values[1];
                revisionCommentArea.setText("<differences between " + olderRevision.number + " and " + newerRevision.number + ">");
                showDifferencesBetweenRevisions(olderRevision, newerRevision);
            }
        }
    }
}
