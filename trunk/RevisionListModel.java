import java.util.*;
import javax.swing.*;

/**
 * Collects Revision objects for each revision of a particular file.
 * Implements ListModel so that we can use the collection directly in
 * the user interface.
 */
public class RevisionListModel extends AbstractListModel {
    private ArrayList data = new ArrayList();
    private HashMap numberToRevisionMap = new HashMap();
    private int maxAuthorNameLength = 0;
    private int maxRevisionNumberLength = 0;
    
    /** Implements ListModel. */
    public int getSize() {
        return data.size();
    }
    
    /** Implements ListModel. */
    public Object getElementAt(int row) {
        return data.get(row);
    }
    
    /**
     * Adds the unique local revision, corresponding to changes not yet
     * committed.
     */
    public void addLocalRevision(Revision localRevision) {
        data.add(0, localRevision);
        updateCaches(localRevision);
    }

    /** Adds a revision. */
    public void add(Revision revision) {
        data.add(revision);
        updateCaches(revision);
    }

    private void updateCaches(Revision revision) {
        numberToRevisionMap.put(revision.number, revision);
        maxAuthorNameLength = Math.max(maxAuthorNameLength, revision.author.length());
        maxRevisionNumberLength = Math.max(maxRevisionNumberLength, revision.number.length());
    }

    /**
     * Returns the revision object corresponding to the given
     * revision number (such as "1.23"). Returns null if the
     * revision isn't known.
     */
    public Revision fromNumber(String number) {
        return (Revision) numberToRevisionMap.get(number);
    }

    public int getMaxAuthorNameLength() {
        return maxAuthorNameLength;
    }

    public int getMaxRevisionNumberLength() {
        return maxRevisionNumberLength;
    }
}
