package e.scm;

import java.text.*;
import java.util.regex.*;

public class AnnotatedLine {
    private static final SimpleDateFormat INPUT_DATE_FORMAT = new SimpleDateFormat("dd-MMM-yy");
    private static final SimpleDateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    /** The revision number this line belongs to. */
    public Revision revision;

    /** The source code for this line. */
    public String source;

    /** The formatted form suitable for direct presentation to the user. */
    public String formattedLine;

    private AnnotatedLine() {
    }

    public static AnnotatedLine fromLine(RevisionListModel revisions, String line, Pattern pattern, int revisionGroup, int sourceGroup) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches() == false) {
            throw new IllegalArgumentException("Line \"" + line + "\" doesn't match annotation pattern (" + pattern + ").");
        }
        
        String revisionId = matcher.group(revisionGroup);
        AnnotatedLine result = new AnnotatedLine();
        result.revision = revisions.fromNumber(revisionId);
        if (result.revision == null) {
            throw new IllegalArgumentException("No revision '" + revisionId + "'");
        }
        result.source = matcher.group(sourceGroup);
        result.prepareFormattedLine(revisions);
        return result;
    }
    
    public static AnnotatedLine fromLocalRevision(RevisionListModel revisions, String line) {
        AnnotatedLine result = new AnnotatedLine();
        result.revision = Revision.LOCAL_REVISION;
        result.source = line;
        result.prepareFormattedLine(revisions);
        return result;
    }
    
    private void prepareFormattedLine(RevisionListModel revisions) {
        formattedLine = justify(revision.author, revisions.getMaxAuthorNameLength()) + " " +
                        justify(revision.number, revisions.getMaxRevisionNumberLength()) + ":" +
                        expandTabs(source);
    }

    public String expandTabs(String s) {
        return s.replaceAll("\t", "        ");
    }

    public String justify(String s, int width) {
        final int length = s.length();
        if (length > width) {
            // Don't return more than 'width' characters.
            return s.substring(0, width - 1);
        } else if (length == width) {
            // Don't create a new string unless we need to.
            return s;
        }

        // Return a string of 's' followed by a suitable number of spaces.
        StringBuilder result = new StringBuilder(s);
        for (int i = length; i < width; ++i) {
            result.append(' ');
        }
        return result.toString();
    }

    public String toString() {
        return formattedLine;
    }
}
