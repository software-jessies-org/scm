import javax.swing.*;

/**
 * An equivalent model to the one used by the JList(Object[]) constructor,
 * which uses an anonymous inner class.
 */
public class ToolErrorListModel extends AbstractListModel {
    private String[] lines;

    public ToolErrorListModel(String[] lines) {
        this.lines = lines;
    }

    public int getSize() {
        return lines.length;
    }

    public Object getElementAt(int row) {
        return lines[row];
    }
}
