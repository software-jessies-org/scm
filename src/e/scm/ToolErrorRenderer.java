package e.scm;

import java.awt.*;
import javax.swing.*;

public class ToolErrorRenderer extends e.gui.EListCellRenderer<String> {
    public ToolErrorRenderer() {
        super(false);
    }

    @Override public void doCustomization(JList<String> list, String value, int index, boolean isSelected, boolean isFocused) {
        setForeground(Color.RED);
    }
}
