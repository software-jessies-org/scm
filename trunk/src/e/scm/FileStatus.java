package e.scm;

public class FileStatus {
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
    
    public String toString() {
        return state + " " + name;
    }
}
