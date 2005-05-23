package e.scm;

import e.util.*;
import java.util.*;

public class CheckInTool implements Launchable {
    private RevisionControlSystem backEnd;
    
    public void parseCommandLine(List<String> arguments) {
        backEnd = RevisionControlSystem.forPath(System.getProperty("user.dir"));
        if (backEnd == null) {
            System.err.println("Not in a directory that is under revision control.");
            System.exit(1);
        }
        
        for (String argument : arguments) {
            System.err.println("Unknown argument '" + argument + "'.");
        }
        if (arguments.size() > 0) {
            System.exit(1);
        }
    }
    
    public void startGui() {
        new CheckInWindow(backEnd);
    }
}
