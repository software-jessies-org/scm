package e.scm;

import java.util.regex.*;

public class RevisionTool {
    private static final Pattern GREP_ADDRESS = Pattern.compile("(.*):([0-9]+):?");

    public static void main(String[] args) {
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
            final RevisionControlSystem backEnd = RevisionControlSystem.forPath(System.getProperty("user.dir"));
            if (backEnd == null) {
                System.err.println(filename + " is not in a directory that is under revision control.");
            } else {
                new RevisionWindow(filename, lineNumber);
            }
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
