import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

public class AnnotatedLineRenderer extends DefaultListCellRenderer {
    private static final float[] NEW_COLOR = Color.RGBtoHSB(0, 0, 255, null);
    private static final int DAYS_TO_BLACK = 31;

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean isFocused) {
        super.getListCellRendererComponent(list, value, index, isSelected, isFocused);
        AnnotatedLine line = (AnnotatedLine) value;
        setText(line.date + " " + line.source);
        setForeground(colorForAge(line.age));
        return this;
    }

    /**
     * Chooses a color from NEW_COLOR to black, depending on the given age in
     * days. A very young age will result in a color close to NEW_COLOR, a
     * very old age will result in black. The space in between is linear.
     *
     * One alternative would be a logarithmic aging process.
     *
     * Another alternative would be to use discrete steps, such as "today",
     * "yesterday", "this week", "this month", et cetera. This could
     * possibly even use a collection of very different colors, rather than
     * a range.
     */
    private Color colorForAge(int ageInDays) {
        float b = 1.0f - Math.min((float) ageInDays / DAYS_TO_BLACK, 1.0f);
        // FIXME: stop creating all these Color instances at render-time!
        // FIXME: especially because there are so few unique ones!
        return new Color(Color.HSBtoRGB(NEW_COLOR[0], NEW_COLOR[1], b));
    }
}
