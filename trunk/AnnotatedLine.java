import java.text.*;
import java.util.*;
import java.util.regex.*;

public class AnnotatedLine {
    private static final Pattern PATTERN = Pattern.compile("^([0-9.]+)\\s+\\((\\S+)\\s+(\\d\\d-\\S\\S\\S-\\d\\d)\\):(.*)$");
    private static final SimpleDateFormat INPUT_DATE_FORMAT = new SimpleDateFormat("dd-MMM-yy");
    private static final SimpleDateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final long NOW = System.currentTimeMillis();

    /** The revision number this line belongs to. */
    public String revision;
    /** The username of the person who last changed this line. */
    public String author;
    /** The ISO date when this line was last changed. */
    public String date;
    /** The source code for this line. */
    public String source;
    /** The number of days since this line was last changed. */
    public int age;

    public AnnotatedLine(String line) {
        Matcher matcher = PATTERN.matcher(line);
        if (matcher.matches()) {
            revision = matcher.group(1);
            author = matcher.group(2);
            try {
                Date cvsDate = INPUT_DATE_FORMAT.parse(matcher.group(3));
                date = OUTPUT_DATE_FORMAT.format(cvsDate);
                age = (int) ((NOW - cvsDate.getTime()) / (1000L * 60 * 60 * 24));
            } catch (ParseException ex) {
                date = "xxxx-xx-xx";
            }
            source = expandTabs(matcher.group(4));
        }
    }

    public String expandTabs(String s) {
        return s.replaceAll("\t", "        ");
    }

    public String toString() {
        return date + " " + author + "(" + revision + ")" + ":" + source;
    }
}
