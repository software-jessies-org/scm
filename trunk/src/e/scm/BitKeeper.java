package e.scm;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import e.util.*;

public class BitKeeper extends RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        ArrayList command = new ArrayList();
        command.add("bk");
        command.add("annotate");
        if (revision != Revision.LOCAL_REVISION) {
            command.add("-r" + revision.number);
        }
        command.add(filename);
        return (String[]) command.toArray(new String[command.size()]);
    }

    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename) {
        ArrayList result = new ArrayList();
        result.add("bk");
        result.add("diffs");
        result.add("-u");
        if (olderRevision != null) {
            result.add("-r" + olderRevision.number);
            if (newerRevision != Revision.LOCAL_REVISION) {
                result.add("-r" + newerRevision.number);
            }
        }
        result.add(filename);
        return (String[]) result.toArray(new String[result.size()]);
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "bk", "prs", filename };
    }

    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^([^\t]+)\t([^\t]+)\t(.*)$");

    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 2, 3);
    }

    //D 1.2 04/02/15 13:02:22+00:00 elliotth@mercury.local 3 2 4/0/356
    //D 1.144 01/03/28 11:56:53-00:00 hughc 145 144 0/3/3528
    private static final Pattern LOG_PATTERN = Pattern.compile("^D ([0-9.]+) (\\d\\d)/(\\d\\d)/(\\d\\d) \\S+ ([^@ ]+).*");

    public RevisionListModel parseLog(List linesList) {
        String[] lines = (String[]) linesList.toArray(new String[linesList.size()]);

        String separator = "------------------------------------------------";
        RevisionListModel result = new RevisionListModel();
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].equals(separator)) {
                continue;
            }
            
            Matcher matcher = LOG_PATTERN.matcher(lines[i]);
            if (matcher.matches()) {
                String number = matcher.group(1);
                String author = matcher.group(5);
                String date = "20" + matcher.group(2) + "-" + matcher.group(3) + "-" + matcher.group(4);
                StringBuffer comment = new StringBuffer();
                while (++i < lines.length && lines[i].equals(separator) == false) {
                    comment.append(lines[i].substring(2));
                    comment.append("\n");
                }
                result.add(new Revision(number, date, author, comment.toString()));
            }
        }
        return result;
    }

    public boolean isLocallyModified(File repositoryRoot, String filename) {
        String[] command = new String[] {
            "bk", "sfiles", "-v", "-ct", "-g", filename
        };
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        for (int i = 0; i < lines.size(); ++i) {
            String line = (String) lines.get(i);
            if (line.indexOf("lc") == 0) {
                return true;
            }
        }
        return false;
    }

    public boolean supportsChangeSets() {
        return true;
    }

    public void showChangeSet(File repositoryRoot, String filename, Revision revision) {
        // bk csettool -r`bk r2c -r1.3 file.cpp` -ffile.cpp@1.3
        String command = "bk csettool -r`bk r2c -r" + revision.number + " " +
                         filename + "` -f" + filename + "@" + revision.number;
        ProcessUtilities.spawn(repositoryRoot, new String[] { "bash", "-c", command });
    }

    public void revert(File repositoryRoot, String filename) {
        ArrayList command = new ArrayList();
        command.add("bk");
        command.add("unedit");
        command.add(filename);
        execAndDump(repositoryRoot, command);
    }
    
    public List getStatuses(File repositoryRoot) {
        String[] command = new String[] { "bk", "sfiles", "-ct", "-g", "-x", "-v", "-p" };
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        
        ArrayList statuses = new ArrayList();
        Pattern pattern = Pattern.compile("^(.{4})\\s+(.+)(@[0-9.]+)?$");
        for (int i = 0; i < lines.size(); ++i) {
            int canonicalState = FileStatus.NOT_RECOGNIZED_BY_BACK_END;
            String name = null;
            String line = (String) lines.get(i);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String state = matcher.group(1);
                name = matcher.group(2);
                if (name.equals("ChangeSet")) {
                    // There seems to be a BitKeeper implementation detail that means
                    // that sometimes ChangeSet is included, though it shouldn't be.
                    canonicalState = FileStatus.IGNORED;
                } else if (state.equals("jjjj")) {
                    // Junk. Ignore.
                    canonicalState = FileStatus.IGNORED;
                } else if (state.equals("xxxx")) {
                    canonicalState = FileStatus.NEW;
                } else if (state.matches(".c..")) {
                    canonicalState = FileStatus.MODIFIED;
                } else if (state.matches("..p.")) {
                    if (name.startsWith("BitKeeper/deleted/.del-")) {
                        canonicalState = FileStatus.REMOVED;
                    } else {
                        canonicalState = FileStatus.ADDED;
                    }
                }
                //case 'C': canonicalState = FileStatus.CONTAINS_CONFLICTS; break;
                //case '!': canonicalState = FileStatus.MISSING; break;
                //case '~': canonicalState = FileStatus.WRONG_KIND; break;
            }
            if (canonicalState != FileStatus.IGNORED) {
                statuses.add(new FileStatus(canonicalState, name));
            }
        }
        return statuses;
    }
    
    public void commit(File repositoryRoot, String comment, List fileStatuses) {
        ArrayList command = new ArrayList();
        command.add("bk");
        command.add("delta");
        command.add("-a"); // Allow new files to be added without a separate 'bk new <file>'.
        command.add("-y" + comment);
        addFilenames(command, fileStatuses);
        execAndDump(repositoryRoot, command);
        
        command = new ArrayList();
        command.add("bk");
        command.add("commit");
        command.add("-y" + comment);
        addFilenames(command, fileStatuses);
        execAndDump(repositoryRoot, command);
    }
    
}
