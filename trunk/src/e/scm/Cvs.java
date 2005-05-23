package e.scm;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import e.util.*;

public class Cvs extends RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("cvs");
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
        result.add("cvs");
        result.add("diff");
        result.add("-u");
        result.add("-kk");
        if (olderRevision != null) {
            result.add("-r");
            result.add(olderRevision.number);
            if (newerRevision != Revision.LOCAL_REVISION) {
                result.add("-r");
                result.add(newerRevision.number);
            }
        }
        result.add(filename);
        return result.toArray(new String[result.size()]);
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "cvs", "log", filename };
    }

    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^([0-9.]+)\\s+\\((\\S+)\\s+(\\d\\d-\\S\\S\\S-\\d\\d)\\): (.*)$");

    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 1, 4);
    }
    
    //date: 2002/11/25 14:41:42;  author: ericb;  state: Exp;  lines: +12 -1
    private static final Pattern LOG_PATTERN = Pattern.compile("^date: (\\d\\d\\d\\d)[-/](\\d\\d)[-/](\\d\\d)[^;]*;\\s+author: ([^;]+);\\s+.*");

    public RevisionListModel parseLog(List<String> linesList) {
        String[] lines = linesList.toArray(new String[linesList.size()]);

        String separator = "----------------------------";
        String endMarker = "=============================================================================";
        int i = 0;
        for (; i < lines.length && lines[i].equals(separator) == false; ++i) {
            // Skip header.
        }
        
        RevisionListModel result = new RevisionListModel();
        while (i < lines.length && lines[i].equals(endMarker) == false) {
            if (lines[i].equals(separator)) {
                i++;
                continue;
            }
            String number = lines[i++];
            number = number.substring("revision ".length());
            Matcher matcher = LOG_PATTERN.matcher(lines[i++]);
            if (matcher.matches()) {
                String author = matcher.group(4);
                String date = matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
                StringBuffer comment = new StringBuffer();
                while (i < lines.length && lines[i].equals(endMarker) == false && lines[i].equals(separator) == false) {
                    comment.append(lines[i++]);
                    comment.append("\n");
                }
                result.add(new Revision(number, date, author, comment.toString()));
            }
        }
        return result;
    }

    public boolean isLocallyModified(String filename) {
        String[] command = new String[] { "cvs", "status", filename };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        for (String line : lines) {
            if (line.contains("Status: Locally Modified")) {
                return true;
            }
        }
        return false;
    }

    /**
     * CVS has no notion of a change set.
     * FIXME: we could try to read filenames out of the revision comment,
     * which in many cases will include all the other changed files. I don't
     * know how we'd work out the appropriate revisions, though I guess the
     * time stamps would be enough.
     */
    public boolean supportsChangeSets() {
        return false;
    }

    public void showChangeSet(String filename, Revision revision) {
        throw new UnsupportedOperationException("Can't show a CVS change set for " + filename + " revision " + revision.number);
    }
    
    public void revert(String filename) {
        File file = FileUtilities.fileFromParentAndString(getRoot().toString(), filename);
        file.delete();
        ArrayList<String> command = new ArrayList<String>();
        command.add("cvs");
        command.add("update");
        // Although this might seem like the right thing to do, CVS will then
        // refuse to update the file until you "update -A".
        //command.add("-r");
        //command.add("BASE");
        command.add(filename);
        execAndDump(command);
    }

    public List<FileStatus> getStatuses(WaitCursor waitCursor) {
        String[] command = new String[] { "cvs", "-q", "update", "-dP" };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        
        ArrayList<FileStatus> statuses = new ArrayList<FileStatus>();
        Pattern pattern = Pattern.compile("^(.) (.+)$");
        for (int i = 0; i < lines.size(); ++i) {
            String line = (String) lines.get(i);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                char state = matcher.group(1).charAt(0);
                if (state == 'U') {
                    continue;
                }
                
                String name = matcher.group(2);
                int canonicalState = FileStatus.NOT_RECOGNIZED_BY_BACK_END;
                switch (state) {
                    case 'A': canonicalState = FileStatus.ADDED; break;
                    case 'C': canonicalState = FileStatus.CONTAINS_CONFLICTS; break;
                    case 'R': canonicalState = FileStatus.REMOVED; break;
                    case 'M': canonicalState = FileStatus.MODIFIED; break;
                    case '?': canonicalState = FileStatus.NEW; break;
                }
                statuses.add(new FileStatus(canonicalState, name));
            } else if (line.startsWith("retrieving revision ") || line.startsWith("RCS file: ") || line.startsWith("Merging differences between ")) {
                // Ignore.
            } else {
                System.err.println("CVS back end didn't understand '" + line + "'.");
            }
        }
        return statuses;
    }
    
    public void commit(String comment, List<FileStatus> fileStatuses) {
        scheduleNewFiles("cvs", false, fileStatuses);
        ArrayList<String> command = new ArrayList<String>();
        command.add("cvs");
        command.add("commit");
        command.add("-F");
        command.add(createCommentFile(comment));
        addFilenames(command, fileStatuses);
        execAndDump(command);
    }
}
