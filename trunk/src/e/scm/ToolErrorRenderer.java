package e.scm;

import java.awt.*;
import javax.swing.*;

public class ToolErrorRenderer extends e.gui.EListCellRenderer {
    public ToolErrorRenderer() {
        super(false);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean isFocused) {
        super.getListCellRendererComponent(list, value, index, isSelected, isFocused);
        setForeground(Color.RED);
        return this;
    }
}
