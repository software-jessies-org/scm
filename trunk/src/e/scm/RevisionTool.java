package e.scm;

import java.io.*;
import java.util.regex.*;

public class RevisionTool {
    private static final Pattern GREP_ADDRESS = Pattern.compile("(.*):([0-9]+):?");

    public static void main(String[] args) {
        final File repositoryRoot = RevisionWindow.getRepositoryRoot(System.getProperty("user.dir"));
        if (repositoryRoot == null) {
            System.err.println("Not in a directory that is under revision control.");
            System.exit(1);
        }
        
        if (args.length == 0) {
            System.err.println("No file specified.");
            System.exit(1);
        }
        
        for (int i = 0; i < args.length; ++i) {
            String filename = args[i];
            int lineNumber = 0;
            Matcher matcher = GREP_ADDRESS.matcher(filename);
            if (matcher.matches()) {
                // grep-style line number.
                filename = matcher.group(1);
                lineNumber = Integer.parseInt(matcher.group(2));
            }
            if (i < args.length - 1) {
                String nextArg = args[i + 1];
                if (nextArg.startsWith("-@")) {
                    // BitKeeper-style line number.
                    lineNumber = Integer.parseInt(nextArg.substring(2));
                    ++i;
                } else if (nextArg.startsWith("+")) {
                    // vim-style line number.
                    lineNumber = Integer.parseInt(nextArg.substring(1));
                    ++i;
                }
            }
            new RevisionWindow(filename, lineNumber);
        }
    }

    /**
     * Prevents instantiation. If you want a programmatic interface to
     * incorporate a revision history browser in your own Java program,
     * look at RevisionWindow instead. Or invoke RevisionTool.main
     * explicitly.
     */
    private RevisionTool() {
    }
}
