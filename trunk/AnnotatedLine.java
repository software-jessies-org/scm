import java.text.*;
import java.util.*;
import java.util.regex.*;

public class AnnotatedLine {
    private static final Pattern CVS_PATTERN = Pattern.compile("^([0-9.]+)\\s+\\((\\S+)\\s+(\\d\\d-\\S\\S\\S-\\d\\d)\\): (.*)$");
    private static final Pattern BK_PATTERN = Pattern.compile("^([^\t]+)\t([^\t]+)\t(.*)$");
    private static final SimpleDateFormat INPUT_DATE_FORMAT = new SimpleDateFormat("dd-MMM-yy");
    private static final SimpleDateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final long NOW = System.currentTimeMillis();

    /** The revision number this line belongs to. */
    public Revision revision;
    /** The username of the person who last changed this line. */
    public String author;
    /** The ISO date when this line was last changed. */
    public String date;
    /** The source code for this line. */
    public String source;

    /** The formatted form suitable for direct presentation to the user. */
    public String formattedLine;

    /** The number of days since this line was last changed. */
    public int age;

    private AnnotatedLine() {
    }

    public static AnnotatedLine fromCvsAnnotatedLine(RevisionListModel revisions, String line) {
        Matcher matcher = CVS_PATTERN.matcher(line);
        if (matcher.matches() == false) {
            return null;
        }

        AnnotatedLine result = new AnnotatedLine();
        result.revision = revisions.fromNumber(matcher.group(1));
        result.author = matcher.group(2);
        try {
            Date cvsDate = INPUT_DATE_FORMAT.parse(matcher.group(3));
            result.date = OUTPUT_DATE_FORMAT.format(cvsDate);
            result.age = (int) ((NOW - cvsDate.getTime()) / (1000L * 60 * 60 * 24));
        } catch (ParseException ex) {
            result.date = "xxxx-xx-xx";
        }
        result.source = matcher.group(4);
        result.prepareFormattedLine(revisions);
        return result;
    }

    public static AnnotatedLine fromBitKeeperAnnotatedLine(RevisionListModel revisions, String line) {
        Matcher matcher = BK_PATTERN.matcher(line);
        if (matcher.matches() == false) {
            return null;
        }

        AnnotatedLine result = new AnnotatedLine();
        result.revision = revisions.fromNumber(matcher.group(2));
        result.author = matcher.group(1);
        result.date = "xxxx-xx-xx";
        result.age = 0;
        result.source = matcher.group(3);
        result.prepareFormattedLine(revisions);
        return result;
    }

    private void prepareFormattedLine(RevisionListModel revisions) {
        formattedLine = justify(author, revisions.getMaxAuthorNameLength()) + " " +
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
        StringBuffer result = new StringBuffer(s);
        for (int i = length; i < width; ++i) {
            result.append(' ');
        }
        return result.toString();
    }

    public String toString() {
        return date + " " + author + "(" + revision + ")" + ":" + source;
    }
}
