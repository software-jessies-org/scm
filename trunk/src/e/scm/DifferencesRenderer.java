package e.scm;

import java.awt.*;
import javax.swing.*;

public class DifferencesRenderer extends e.gui.EListCellRenderer {
    /**
     * The sole instance of this class.
     */
    public static final DifferencesRenderer INSTANCE = new DifferencesRenderer();

    /**
     * Prevents the creation of useless instances.
     */
    private DifferencesRenderer() {
        super(false);
    }
    
    /**
     * Renders lines from a context diff patch in colors inspired by code2html.
     */
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
