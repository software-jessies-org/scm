package e.scm;

public class Revision {
    /** A revision corresponding to changes not yet committed. */
    public static final Revision LOCAL_REVISION = new Revision("local", "xxxx-xx-xx", "unknown", "Changes not yet committed."); 

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

    public String toString() {
        return date + " " + author + " (" + number + ")";
    }
}
