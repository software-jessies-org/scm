package e.scm;

import java.util.regex.*;

public class Revision {
    /** A revision corresponding to changes not yet committed. */
    public static final Revision LOCAL_REVISION = new Revision("local", "xxxx-xx-xx", "unknown", "Changes not yet committed."); 

    //date: 2002/11/25 14:41:42;  author: ericb;  state: Exp;  lines: +12 -1
    private static final Pattern CVS_PATTERN = Pattern.compile("^date: (\\d\\d\\d\\d)/(\\d\\d)/(\\d\\d)[^;]*;\\s+author: ([^;]+);\\s+.*");

    public String number;
    public String date;
    public String author;
    public String comment;

    public Revision(String number, String date, String author, String comment) {
        this.number = number;
        this.date = date;
        this.author = author;
        this.comment = comment;
    }

    public Revision(String number, String info, String comment) {
        this.number = number;
        this.comment = comment;
        Matcher matcher = CVS_PATTERN.matcher(info);
        if (matcher.matches()) {
            date = matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
            author = matcher.group(4);
        } else {
            date = "xxxx-xx-xx";
            author = "?";
        }
    }

    public String toString() {
        return date + " " + author + " (" + number + ")";
    }
}
