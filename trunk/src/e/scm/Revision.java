package e.scm;

import java.util.regex.*;

public class Revision {
    private static final Pattern AUTHOR_PATTERN = Pattern.compile(".* <(.+)@.*>$");
    
    /** A revision corresponding to changes not yet committed. */
    public static final Revision LOCAL_REVISION = new Revision("local", "xxxx-xx-xx", "xx:xx:xx -xxxx", "unknown", "Changes not yet committed.");

    public String number;
    public String date;
    public String time;
    public String author; // "John Doe <johnd@jessies.org>"
    public String shortAuthor; // "johnd"
    public String comment;
    
    public Revision(String number, String date, String time, String author, String comment) {
        this.number = number;
        this.date = date;
        this.time = time;
        this.author = author;
        this.shortAuthor = makeShortAuthor(author);
        this.comment = comment;
    }
    
    private static String makeShortAuthor(String author) {
        Matcher matcher = AUTHOR_PATTERN.matcher(author);
        return (matcher.matches() ? matcher.group(1) : author);
    }
    
    public String toString() {
        return date + " " + time + " " + author + " (" + number + ")";
    }
    
    public String summary() {
        return date + " " + time + " " + author + " (" + number + ")" + "\n\n" + comment + "\n";
    }
}
