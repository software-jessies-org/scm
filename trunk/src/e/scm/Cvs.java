package e.scm;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Cvs implements RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        return new String[] {
            "cvs", "annotate", "-r", revision.number, filename
        };
    }

    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename) {
        ArrayList result = new ArrayList();
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
        return (String[]) result.toArray(new String[result.size()]);
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "cvs", "log", filename };
    }

    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^([0-9.]+)\\s+\\((\\S+)\\s+(\\d\\d-\\S\\S\\S-\\d\\d)\\): (.*)$");

    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 1, 4);
    }
    
    //date: 2002/11/25 14:41:42;  author: ericb;  state: Exp;  lines: +12 -1
    private static final Pattern LOG_PATTERN = Pattern.compile("^date: (\\d\\d\\d\\d)/(\\d\\d)/(\\d\\d)[^;]*;\\s+author: ([^;]+);\\s+.*");

    public RevisionListModel parseLog(List linesList) {
        String[] lines = (String[]) linesList.toArray(new String[linesList.size()]);

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

    public boolean isLocallyModified(File repositoryRoot, String filename) {
        String[] command = new String[] { "cvs", "status", filename };
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        for (int i = 0; i < lines.size(); ++i) {
            String line = (String) lines.get(i);
            if (line.indexOf("Status: Locally Modified") != -1) {
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

    public void showChangeSet(File repositoryRoot, String filename, Revision revision) {
        throw new UnsupportedOperationException("Can't show a CVS change set for " + filename + " revision " + revision.number);
    }
    
    public List getStatuses(File repositoryRoot) {
        String[] command = new String[] { "cvs", "update", "-dP" };
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        
        ArrayList statuses = new ArrayList();
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
            } else {
                System.err.println("CVS back end didn't understand '" + line + "'.");
            }
        }
        return statuses;
    }
    
    public void commit(File repositoryRoot, String comment, List filenames) {
        ArrayList command = new ArrayList();
        command.add("cvs");
        command.add("commit");
        command.add("-m");
        command.add(comment);
        command.addAll(filenames);
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = ProcessUtilities.backQuote(repositoryRoot, (String[]) command.toArray(new String[command.size()]), lines, errors);
        for (int i = 0; i < lines.size(); ++i) {
            System.out.println(lines.get(i));
        }
        for (int i = 0; i < errors.size(); ++i) {
            System.err.println(errors.get(i));
        }
        System.err.println(status);
    }
}