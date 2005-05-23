package e.scm;

import java.util.*;
import javax.swing.*;

/**
 * Collects Revision objects for each revision of a particular file.
 * Implements ListModel so that we can use the collection directly in
 * the user interface.
 */
public class RevisionListModel extends AbstractListModel {
    private ArrayList<Revision> data = new ArrayList<Revision>();
    private HashMap<String, Revision> numberToRevisionMap = new HashMap<String, Revision>();
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
        return numberToRevisionMap.get(number);
    }

    public int getMaxAuthorNameLength() {
        return maxAuthorNameLength;
    }

    public int getMaxRevisionNumberLength() {
        return maxRevisionNumberLength;
    }

    /**
     * Returns the latest revision that isn't the pseudo-revision
     * corresponding to local uncommitted changes.
     */
    public Revision getLatestInRepository() {
        Revision revision = data.get(0);
        if (revision != Revision.LOCAL_REVISION) {
            return revision;
        }
        revision = data.get(1);
        return revision;
    }
}
