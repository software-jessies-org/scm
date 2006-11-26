package e.scm;

import java.util.*;
import java.util.regex.*;
import e.util.*;

public class Subversion extends RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("svn");
        command.add("annotate");
        if (revision != Revision.LOCAL_REVISION) {
            command.add("-r");
            command.add(revision.number);
        }
        command.add(filename);
        return command.toArray(new String[command.size()]);
    }

    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename) {
        ArrayList<String> result = new ArrayList<String>();
        result.add("svn");
        result.add("diff");
        if (olderRevision != null) {
            result.add("-r");
            String revisionArgument = olderRevision.number;
            if (newerRevision != Revision.LOCAL_REVISION) {
                revisionArgument += ":" + newerRevision.number;
            }
            result.add(revisionArgument);
        }
        result.add(filename);
        return result.toArray(new String[result.size()]);
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "svn", "log", filename };
    }

    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^\\s+(\\d+)\\s+(?:\\S+) (.*)$");

    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 1, 2);
    }
    
    //r74 | elliotth | 2004-04-24 12:29:26 +0100 (Sat, 24 Apr 2004) | 3 lines
    private static final Pattern LOG_PATTERN = Pattern.compile("^r(\\d+) \\| ([^|]+) \\| (\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2} [-+]\\d{4}) \\(.*\\) \\| (\\d+) lines?$");

    public RevisionListModel parseLog(List<String> linesList) {
        String[] lines = linesList.toArray(new String[linesList.size()]);

        String separator = "------------------------------------------------------------------------";
        
        RevisionListModel result = new RevisionListModel();
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].equals(separator)) {
                continue;
            }
            
            Matcher matcher = LOG_PATTERN.matcher(lines[i++]);
            if (matcher.matches()) {
                String number = matcher.group(1);
                String author = matcher.group(2);
                String date = matcher.group(3);
                String time = matcher.group(4);
                
                int commentLineCount = Integer.parseInt(matcher.group(5));
                if (lines[i].equals("") == false) {
                    Log.warn("expected blank line when parsing log");
                }
                ++i; // Skip blank line.
                StringBuilder comment = new StringBuilder();
                for (; commentLineCount > 0; --commentLineCount) {
                    comment.append(lines[i++]);
                    comment.append("\n");
                }
                if (lines[i].equals(separator) == false) {
                    Log.warn("expected separator line when parsing log");
                }
                Revision revision = new Revision(number, date, time, author, comment.toString());
                result.add(revision);
            }
        }
        return result;
    }

    public boolean isLocallyModified(String filename) {
        String[] command = new String[] { "svn", "status", filename };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        for (String line : lines) {
            if (line.startsWith("M")) {
                return true;
            }
        }
        return false;
    }

    public boolean supportsChangeSets() {
        return true;
    }
    
    public List<ChangeSetItem> listTouchedFilesInRevision(String filename, Revision revision) {
        String[] command = new String[] { "svn", "log", "-v", "-r", revision.number };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        
        ArrayList<ChangeSetItem> result = new ArrayList<ChangeSetItem>();
        boolean inChangedPaths = false;
        Pattern changedPathPattern = Pattern.compile("^.... (.*)$");
        for (String line : lines) {
            if (line.equals("Changed paths:")) {
                inChangedPaths = true;
            } else if (inChangedPaths) {
                if (line.length() == 0) {
                    // The changed paths end with an empty line.
                    break;
                }
                
                Matcher matcher = changedPathPattern.matcher(line);
                if (matcher.matches()) {
                    // FIXME: how do we translate these into the paths that "svn diff" wants?
                    // We deliberately drop any leading / or trunk/, but this won't let us cope with arbitrary branches.
                    // Are we supposed to use the output of "svn info" to make URLs rather than working copy paths?
                    String path = matcher.group(1);
                    path = path.replaceAll("^/trunk/", "");
                    path = path.replaceAll("^/", "");
                    // Subtracting one doesn't give the number of the previous revision of the file.
                    // This means that we can't, in general, successfully look up the revision.oldRevision in a RevisionListModel.
                    final String oldRevisionNumber = Integer.toString(Integer.parseInt(revision.number) - 1);
                    result.add(new ChangeSetItem(path, oldRevisionNumber, revision.number));
                }
            }
        }
        return result;
    }
    
    public void revert(String filename) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("svn");
        command.add("revert");
        command.add(filename);
        execAndDump(command);
    }

    public List<FileStatus> getStatuses(StatusReporter statusReporter) {
        String[] command = new String[] { "svn", "status" };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        
        ArrayList<FileStatus> statuses = new ArrayList<FileStatus>();
        Pattern pattern = Pattern.compile("^(.)....\\s+(.+)$");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                char state = matcher.group(1).charAt(0);
                String name = matcher.group(2);
                int canonicalState = FileStatus.NOT_RECOGNIZED_BY_BACK_END;
                switch (state) {
                    case 'A': canonicalState = FileStatus.ADDED; break;
                    case 'C': canonicalState = FileStatus.CONTAINS_CONFLICTS; break;
                    case 'D': canonicalState = FileStatus.REMOVED; break;
                    case 'M': canonicalState = FileStatus.MODIFIED; break;
                    case '?': canonicalState = FileStatus.NEW; break;
                    case '!': canonicalState = FileStatus.MISSING; break;
                    case '~': canonicalState = FileStatus.WRONG_KIND; break;
                }
                statuses.add(new FileStatus(canonicalState, name));
            } else {
                System.err.println("Subversion back end didn't understand '" + line + "'.");
            }
        }
        return statuses;
    }
    
    public void commit(String comment, List<FileStatus> fileStatuses) {
        scheduleNewFiles("svn", "--non-recursive", fileStatuses);
        ArrayList<String> command = new ArrayList<String>();
        command.add("svn");
        command.add("commit");
        command.add("--non-interactive");
        command.add("--non-recursive");
        command.add("-F");
        command.add(createCommentFile(comment));
        command.add("--targets");
        command.add(createFileListFile(fileStatuses));
        execAndDump(command);
    }
}
