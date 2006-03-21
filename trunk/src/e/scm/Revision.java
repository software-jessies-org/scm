package e.scm;

public class Revision {
    /** A revision corresponding to changes not yet committed. */
    public static final Revision LOCAL_REVISION = new Revision("local", "xxxx-xx-xx", "xx:xx:xx -xxxx", "unknown", "Changes not yet committed.");

    public String number;
    public String date;
    public String time;
    public String author;
    public String comment;

    public Revision(String number, String date, String time, String author, String comment) {
        this.number = number;
        this.date = date;
        this.time = time;
        this.author = author;
        this.comment = comment;
    }

    public String toString() {
        return date + " " + time + " " + author + " (" + number + ")";
    }
    
    public String summary() {
        return date + " " + time + " " + author + " (" + number + ")" + "\n\n" + comment + "\n";
    }
}
