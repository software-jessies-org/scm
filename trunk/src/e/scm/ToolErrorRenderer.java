package e.scm;

import java.awt.*;
import javax.swing.*;

public class ToolErrorRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean isFocused) {
        super.getListCellRendererComponent(list, value, index, isSelected, isFocused);
        setForeground(Color.RED);
        return this;
    }
}
