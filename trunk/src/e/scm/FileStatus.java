package e.scm;

public class FileStatus implements Comparable {
    public static final int NEW = 0;
    public static final int ADDED = 1;
    public static final int REMOVED = 2;
    public static final int MODIFIED = 3;
    public static final int CONTAINS_CONFLICTS = 4;
    public static final int MISSING = 5;
    public static final int WRONG_KIND = 6;
    public static final int NOT_RECOGNIZED_BY_BACK_END = 7;
    
    private int state;
    private String name;
    
    public FileStatus(int state, String name) {
        this.state = state;
        this.name = name;
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
        }
        return "(invalid state #" + state + ")";
    }

    /**
     * Allows FileStatus objects to be ordered by name.
     */
    public int compareTo(Object o) {
        FileStatus other = (FileStatus) o;
        return name.compareToIgnoreCase(other.name);
    }
    
    public String toString() {
        return getStateString() + " " + name;
    }
}
