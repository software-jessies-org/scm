import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Subversion implements RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        return new String[] {
            "svn", "annotate", "-r", revision.number, filename
        };
    }

    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename) {
        ArrayList result = new ArrayList();
        result.add("svn");
        result.add("diff");
        result.add("-r");
        String revisionArgument = olderRevision.number;
        if (newerRevision != Revision.LOCAL_REVISION) {
            revisionArgument += ":" + newerRevision.number;
        }
        result.add(revisionArgument);
        result.add(filename);
        return (String[]) result.toArray(new String[result.size()]);
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "svn", "log", filename };
    }

    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^\\s+(\\d+)\\s+(?:\\S+) (.*)$");

    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 1, 2);
    }
    
    private static final Pattern LOG_PATTERN = Pattern.compile("^r(\\d+) \\| (\\S+) \\| (\\d{4}-\\d{2}-\\d{2}) .* lines$");

    public RevisionListModel parseLog(List linesList) {
        String[] lines = (String[]) linesList.toArray(new String[linesList.size()]);

        String separator = "------------------------------------------------------------------------";
        
        RevisionListModel result = new RevisionListModel();
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].equals(separator)) {
                continue;
            }
            
            Matcher matcher = LOG_PATTERN.matcher(lines[i]);
            if (matcher.matches()) {
                String number = matcher.group(1);
                String author = matcher.group(2);
                String date = matcher.group(3);
                StringBuffer comment = new StringBuffer();
                while (i < lines.length && lines[i].equals(separator) == false) {
                    comment.append(lines[i++]);
                    comment.append("\n");
                }
                result.add(new Revision(number, date, author, comment.toString()));
            }
        }
        return result;
    }

    public boolean isLocallyModified(File repositoryRoot, String filename) {
        String[] command = new String[] { "svn", "status", filename };
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        for (int i = 0; i < lines.size(); ++i) {
            String line = (String) lines.get(i);
            if (line.startsWith("M")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Subversion has some notion of a change set, but it's a bit odd.
     * Global revision numbers ought to give us all we need, if we can
     * get a list of files modified in that revision.
     */
    public boolean supportsChangeSets() {
        return false; // FIXME: learn about svn global revisions.
    }

    public void showChangeSet(File repositoryRoot, String filename, Revision revision) {
        throw new UnsupportedOperationException("Can't show a Subversion change set for " + filename + " revision " + revision.number);
    }
}
