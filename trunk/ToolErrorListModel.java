import java.util.*;
import javax.swing.*;

/**
 * An equivalent model to the one used by the JList(Object[]) constructor,
 * which uses an anonymous inner class. Since updated to use a collection
 * class instead of an array. This is 2004, after all.
 */
public class ToolErrorListModel extends AbstractListModel {
    private ArrayList lines;

    public ToolErrorListModel(ArrayList lines) {
        this.lines = lines;
    }

    public int getSize() {
        return lines.size();
    }

    public Object getElementAt(int row) {
        return lines.get(row);
    }
}
