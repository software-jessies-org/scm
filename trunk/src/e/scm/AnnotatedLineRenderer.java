package e.scm;

import java.awt.*;
import javax.swing.*;

public class AnnotatedLineRenderer extends DefaultListCellRenderer {
    /** Used to draw the dashed line between adjacent lines from different revisions. */
    private static final Stroke DASHED_STROKE = new BasicStroke(1.0f,
            BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[] { 2.0f, 3.0f }, 0.0f);

    /** The RevisionWindow we're rendering for. */
    private RevisionWindow revisionWindow;

    /** Whether or not we should draw a dashed line above this row. */
    private boolean shouldDrawLine;

    public AnnotatedLineRenderer(RevisionWindow parentRevisionWindow) {
        this.revisionWindow = parentRevisionWindow;
    }

    public Component getListCellRendererComponent(JList list, Object value, int row, boolean isSelected, boolean isFocused) {
        super.getListCellRendererComponent(list, value, row, isSelected, isFocused);

        AnnotatedLine line = (AnnotatedLine) value;

        // Find out if this line is from a different revision to the
        // previous line.
        shouldDrawLine = false;
        if (row > 0) {
            ListModel model = list.getModel();
            AnnotatedLine previousLine = (AnnotatedLine) model.getElementAt(row - 1);
            if (line.revision != previousLine.revision) {
                shouldDrawLine = true;
            }
        }

        setText(line.formattedLine);
        setToolTipText(toolTipForRevision(line.revision));
        setForeground((revisionWindow.getSelectedRevision() == line.revision) ? Color.blue : Color.black);

        return this;
    }

    public void paint(Graphics g) {
        super.paint(g);
        if (shouldDrawLine) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.LIGHT_GRAY);
            g2.setStroke(DASHED_STROKE);
            g2.drawLine(0, 0, getWidth(), 0);
        }
    }

    private String toolTipForRevision(Revision revision) {
        final String comment = revision.comment;
        if (comment.length() == 0) {
            return null;
        }
        return "<html>" +
               comment.replaceAll(" ", "&nbsp;").replaceAll("\n", "<p>") +
               "</html>";
    }
}
