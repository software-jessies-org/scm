import java.util.regex.*;

public class Revision {
    /** A revision corresponding to changes not yet committed. */
    public static final Revision LOCAL_REVISION = new Revision("local", "xxxx-xx-xx", "me", "Changes not yet committed."); 

    //date: 2002/11/25 14:41:42;  author: ericb;  state: Exp;  lines: +12 -1
    private static final Pattern CVS_PATTERN = Pattern.compile("^date: (\\d\\d\\d\\d)/(\\d\\d)/(\\d\\d)[^;]*;\\s+author: ([^;]+);\\s+.*");

    //D 1.2 04/02/15 13:02:22+00:00 elliotth@mercury.local 3 2 4/0/356
    //D 1.144 01/03/28 11:56:53-00:00 hughc 145 144 0/3/3528
    private static final Pattern BIT_KEEPER_PATTERN = Pattern.compile("^D ([0-9.]+) (\\d\\d)/(\\d\\d)/(\\d\\d) \\S+ ([^@ ]+).*");

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

    public Revision(String description, String comment) {
        this.comment = comment;
        Matcher matcher = BIT_KEEPER_PATTERN.matcher(description);
        if (matcher.matches()) {
            this.number = matcher.group(1);
            this.date = "20" + matcher.group(2) + "-" + matcher.group(3) + "-" + matcher.group(4);
            this.author = matcher.group(5);
        } else {
            throw new RuntimeException("dodgy bk prs description line: " + description);
        }
    }
    
    public String toString() {
        return date + " " + author + " (" + number + ")";
    }
}
