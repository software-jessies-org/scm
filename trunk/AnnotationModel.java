import java.util.*;
import javax.swing.*;

public class AnnotationModel extends AbstractListModel {
    private ArrayList data = new ArrayList();

    public int getSize() {
        return data.size();
    }

    public Object getElementAt(int row) {
        return data.get(row);
    }

    public void add(AnnotatedLine line) {
        data.add(line);
    }
}
