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
    
    /**
     * Invoked after a model has been filled.
     */
    public void postProcess() {
        if (data.isEmpty()) {
            return;
        }
        
        // Use alternate background colors so we can see boundaries between revisions.
        AnnotatedLine previousLine = (AnnotatedLine) data.get(0);
        boolean useAlternateBackgroundColor = false;
        previousLine.useAlternateBackgroundColor = useAlternateBackgroundColor;
        for (int i = 1; i < data.size(); ++i) {
            AnnotatedLine currentLine = (AnnotatedLine) data.get(i);
            if (currentLine.revision != previousLine.revision) {
                useAlternateBackgroundColor = !useAlternateBackgroundColor;
            }
            currentLine.useAlternateBackgroundColor = useAlternateBackgroundColor;
            previousLine = currentLine;
        }
    }
}
