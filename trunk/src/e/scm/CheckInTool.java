package e.scm;

import e.gui.*;
import e.util.*;
import java.util.*;

public class CheckInTool implements Launchable {
    private RevisionControlSystem backEnd;
    
    public void parseCommandLine(List<String> arguments) {
        if (arguments.size() > 0) {
            fail("CheckInTool does not take any arguments.");
        }
        
        backEnd = RevisionControlSystem.forPath(System.getProperty("user.dir"));
        if (backEnd == null) {
            fail("CheckInTool must be started in a directory that's under revision control, or beneath a directory that's under revision control.");
        }
    }
    
    private void fail(String reason) {
        SimpleDialog.showAlert(null, "CheckInTool couldn't start", reason);
        System.exit(1);
    }
    
    public void startGui() {
        new CheckInWindow(backEnd);
    }
}
