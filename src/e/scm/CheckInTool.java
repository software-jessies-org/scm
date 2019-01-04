package e.scm;

import e.util.*;
import java.util.*;

public class CheckInTool implements Launchable {
    private RevisionControlSystem backEnd;
    
    public void parseCommandLine(List<String> arguments) {
        if (arguments.size() > 0) {
            ScmUtilities.panic("CheckInTool", "CheckInTool does not take any arguments.");
        }
        
        backEnd = RevisionControlSystem.forPath(System.getProperty("user.dir"));
        if (backEnd == null) {
            ScmUtilities.panic("CheckInTool", "CheckInTool must be started in a directory that's under revision control, or beneath a directory that's under revision control.");
        }
    }
    
    public void startGui() {
        ScmUtilities.initAboutBox();
        new CheckInWindow(backEnd);
    }
}
