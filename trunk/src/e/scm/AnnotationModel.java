package e.scm;

import java.util.*;
import java.util.regex.*;
import javax.swing.*;

public class AnnotationModel extends AbstractListModel {
    private static final Pattern HUNK_PATTERN = Pattern.compile("^@@ -\\d+,\\d+ \\+(\\d+),\\d+ @@$");

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
     * Applies a patch to the annotation model. We throw out annotated lines
     * corresponding to lines removed in the patch, and insert new annotated
     * lines to correspond to lines added by the patch. This is useful because
     * no revision control system I know supports annotate/blame functionality
     * on a locally-modified file, so we have to get the annotations for the
     * head revision, and apply a patch like this. Note that unlike patch(1),
     * we work on line numbers rather than content. This makes sense because
     * we know the patch must be good, and because we know the lines won't
     * actually match (because of the annotations).
     */
    public void applyPatch(List/*<String>*/ patchLines, RevisionListModel revisions) {
        int lineNumber = -1;
        for (int i = 0; i < patchLines.size(); ++i) {
            String line = (String) patchLines.get(i);
            Matcher matcher = HUNK_PATTERN.matcher(line);
            if (matcher.find()) {
                String location = matcher.group(1);
                lineNumber = Integer.parseInt(location);
            } else if (lineNumber > 0) {
                if (line.startsWith("-")) {
                    removeLine(lineNumber);
                    --lineNumber;
                } else if (line.startsWith("+")) {
                    AnnotatedLine annotatedLine = AnnotatedLine.fromLocalRevision(revisions, line.substring(1));
                    insertLine(lineNumber, annotatedLine);
                }
                ++lineNumber;
            }
        }
    }
    
    /**
     * Removes a line when applying a patch.
     */
    private void removeLine(int lineNumber) {
        data.remove(lineNumber - 1);
    }
    
    /**
     * Inserts a line when applying a patch.
     */
    private void insertLine(int lineNumber, AnnotatedLine annotatedLine) {
        data.add(lineNumber - 1, annotatedLine);
    }
}
