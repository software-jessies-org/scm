import java.util.regex.*;

public class Patch {
    public Patch(Revision fromRevision, Revision toRevision) {
    }

    public int translateLineNumberInFromRevision(int fromLineNumber) {
        return fromLineNumber;
    }
}
