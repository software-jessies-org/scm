package e.scm;

import java.io.*;

public class CheckInTool {
    public static void main(String[] arguments) {
        final File repositoryRoot = RevisionWindow.getRepositoryRoot(System.getProperty("user.dir"));
        if (repositoryRoot == null) {
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
                new CheckInWindow(repositoryRoot);
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
