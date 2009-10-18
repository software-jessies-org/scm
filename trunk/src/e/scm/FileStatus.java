package e.scm;

public class FileStatus implements Comparable<FileStatus> {
    public static final int NEW = 0;
    public static final int ADDED = 1;
    public static final int REMOVED = 2;
    public static final int MODIFIED = 3;
    public static final int CONTAINS_CONFLICTS = 4;
    public static final int MISSING = 5;
    public static final int WRONG_KIND = 6;
    
    /**
     * Used when a back-end doesn't understand what it's been
     * told when it was querying status. This *will* show up in the
     * UI, and that's what you want, because it's dangerous for the
     * user to continue if you haven't understood.
     * FIXME: should we refuse to continue if we have any of these
     * in the table?
     */
    public static final int NOT_RECOGNIZED_BY_BACK_END = 7;
    
    /**
     * Used internally by a back-end to remind itself that it shouldn't
     * be creating a FileStatus for the file it's looking at. It is an error
     * to create a FileStatus with this state.
     */
    public static final int IGNORED = 8;
    
    /**
     * Used internally by a back-end that can record file modifications and
     * file metadata modifications separately, to indicate that the file
     * contents are unchanged and only the metadata has changed.
     */
    public static final int CONTENTS_UNCHANGED = 9;
    
    private int state;
    private String name;
    
    public FileStatus(int state, String name) {
        this.state = state;
        this.name = name;
        assert(state != IGNORED);
        assert(name != null);
    }
    
    public String getName() {
        return name;
    }
    
    public int getState() {
        return state;
    }
    
    public String getStateString() {
        switch (state) {
            case NEW: return "?";
            case ADDED: return "+";
            case REMOVED: return "-";
            case MODIFIED: return "M";
            case CONTAINS_CONFLICTS: return "C";
            case MISSING: return "(missing)";
            case WRONG_KIND: return "(wrong kind)";
            case NOT_RECOGNIZED_BY_BACK_END: return "(unrecognized)";
            case CONTENTS_UNCHANGED: return " ";
        }
        return "(invalid state #" + state + ")";
    }

    /**
     * Allows FileStatus objects to be ordered by name.
     */
    public int compareTo(FileStatus other) {
        return name.compareToIgnoreCase(other.name);
    }
    
    public String toString() {
        return getStateString() + " " + name;
    }
}
