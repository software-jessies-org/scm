package e.scm;

import e.util.*;

public class CheckInTool {
    public static void main(String[] arguments) {
        Log.setApplicationName("CheckInTool");
        GuiUtilities.initLookAndFeel();

        final RevisionControlSystem backEnd = RevisionControlSystem.forPath(System.getProperty("user.dir"));
        if (backEnd == null) {
            System.err.println("Not in a directory that is under revision control.");
            System.exit(1);
        }
        
        for (int i = 0; i < arguments.length; ++i) {
            System.err.println("Unknown argument '" + arguments[i] + "'.");
        }
        if (arguments.length > 0) {
            System.exit(1);
        }
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new CheckInWindow(backEnd);
            }
        });
    }

    /**
     * Prevents instantiation. If you want a programmatic interface to
     * incorporate a check-in tool in your own Java program,
     * look at CheckInWindow instead. Or invoke CheckInTool.main
     * explicitly.
     */
    private CheckInTool() {
    }
}
