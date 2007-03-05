package e.scm;

import java.awt.*;
import javax.swing.*;

public class AnnotatedLineRenderer extends e.gui.EListCellRenderer {
    /** Used to draw the dashed line between adjacent lines from different revisions. */
    private static final Stroke DASHED_STROKE = new BasicStroke(1.0f,
            BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[] { 2.0f, 3.0f }, 0.0f);

    /** The RevisionView we're rendering for. */
    private RevisionView revisionView;

    /** Whether or not we should draw a dashed line above this row. */
    private boolean shouldDrawLine;

    public AnnotatedLineRenderer(RevisionView parentRevisionView) {
        super(false);
        this.revisionView = parentRevisionView;
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
        if (isSelected == false) {
            setForeground((revisionView.getAnnotatedRevision() == line.revision) ? Color.BLUE : Color.BLACK);
        }
        return this;
    }

    public void paint(Graphics oldGraphics) {
        Graphics2D g = (Graphics2D) oldGraphics;
        super.paint(g);
        if (shouldDrawLine) {
            g.setColor(Color.LIGHT_GRAY);
            g.setStroke(DASHED_STROKE);
            g.drawLine(0, 0, getWidth(), 0);
        }
    }
}
