package e.scm;

import java.awt.*;
import javax.swing.*;

public class DifferencesRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean isFocused) {
        super.getListCellRendererComponent(list, value, index, isSelected, isFocused);
        String line = (String) value;
        if (line.startsWith("+")) {
            setForeground(Color.BLUE);
        } else if (line.startsWith("-")) {
            setForeground(Color.RED);
        } else if (line.startsWith("@@ ")) {
            setForeground(Color.GRAY);
        }
        return this;
    }
}
