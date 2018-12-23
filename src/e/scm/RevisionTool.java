package e.scm;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import e.util.*;

public class RevisionTool implements Launchable {
    private static final Pattern GREP_ADDRESS = Pattern.compile("(.*):([1-9][0-9]*):.*");
    private ArrayList<Job> jobs = new ArrayList<>();
    
    public void parseCommandLine(List<String> arguments) {
        if (arguments.size() == 0) {
            ScmUtilities.panic("RevisionTool", "RevisionTool lets you browse the revision history of files. Please supply at least one filename.");
        }
        
        for (int i = 0; i < arguments.size(); ++i) {
            String filename = arguments.get(i);
            int lineNumber = 0;
            Matcher matcher = GREP_ADDRESS.matcher(filename);
            if (matcher.matches()) {
                File file = new File(filename);
                if (file.exists() == false) {
                    filename = matcher.group(1);
                    lineNumber = Integer.parseInt(matcher.group(2));
                }
            }
            if (i < arguments.size() - 1) {
                String nextArg = arguments.get(i + 1);
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
            final RevisionControlSystem backEnd = RevisionControlSystem.forPath(filename);
            if (backEnd == null) {
                ScmUtilities.panic("RevisionTool", "RevisionTool must be started in a directory that's under revision control, or beneath a directory that's under revision control.");
            }
            jobs.add(new Job(filename, lineNumber));
        }
    }
    
    public void startGui() {
        ScmUtilities.initAboutBox();
        for (Job job : jobs) {
            new RevisionWindow(job.filename, job.lineNumber);
        }
    }
    
    private static class Job {
        String filename;
        int lineNumber;
        Job(String filename, int lineNumber) {
            this.filename = filename;
            this.lineNumber = lineNumber;
        }
    }
}
