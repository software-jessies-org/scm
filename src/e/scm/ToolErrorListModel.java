package e.scm;

import java.util.*;
import javax.swing.*;

/**
 * An equivalent model to the one used by the JList(Object[]) constructor,
 * which uses an anonymous inner class. Since updated to use a collection
 * class instead of an array. This is 2004, after all.
 */
public class ToolErrorListModel extends AbstractListModel<String> {
    private ArrayList<String> lines;

    public ToolErrorListModel(ArrayList<String> lines) {
        this.lines = lines;
    }

    public int getSize() {
        return lines.size();
    }

    public String getElementAt(int row) {
        return lines.get(row);
    }
}
