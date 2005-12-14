package e.scm;

/**
 * A "change set" (a BitKeeper term) or a "revision" in Subversion, consists of changes to one or more files. A "change set item" is a filename, and that file's old and new version numbers. For Subversion, with its global revisions, these numbers will be the same for each file in a "change set", but for BitKeeper that isn't necessarily true, since each file has its own version namespace.
 *
 * (At some point we should go through SCM and try to come up with consistent unambiguous non-confusing nomenclature, though I'm not confident that the problem is solvable.)
 */
public class ChangeSetItem implements Comparable<ChangeSetItem> {
    public String filename;
    public String oldRevision;
    public String newRevision;
    
    public ChangeSetItem(final String filename, final String oldRevision, final String newRevision) {
        this.filename = filename;
        this.oldRevision = oldRevision;
        this.newRevision = newRevision;
    }
    
    public String toString() {
        // FIXME
        return filename;
    }
    
    public final int compareTo(ChangeSetItem other) {
        return toString().compareTo(other.toString());
    }
}
