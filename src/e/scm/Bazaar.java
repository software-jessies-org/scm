package e.scm;

import java.util.*;
import java.util.regex.*;
import e.util.*;

public class Bazaar extends RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        ArrayList<String> command = new ArrayList<>();
        command.add("bzr");
        command.add("annotate");
        command.add("--all");
        command.add("--long");
        if (revision != Revision.LOCAL_REVISION) {
            command.add("-r");
            command.add(revision.number);
        }
        command.add(filename);
        return command.toArray(new String[command.size()]);
    }
    
    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename, boolean ignoreWhiteSpace) {
        ArrayList<String> result = new ArrayList<>();
        result.add("bzr");
        result.add("diff");
        if (olderRevision != null) {
            result.add("-r");
            String revisionArgument = olderRevision.number;
            if (newerRevision != Revision.LOCAL_REVISION) {
                revisionArgument += ".." + newerRevision.number;
            }
            result.add(revisionArgument);
        }
        if (ignoreWhiteSpace) {
            result.add("--diff-options=-w");
        }
        result.add(filename);
        return result.toArray(new String[result.size()]);
    }
    
    public String[] getLogCommand(String filename) {
        return new String[] { "bzr", "log", filename };
    }
    
    //   10 enh@jessies.org 20060429 | monkeys!
    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^\\s*(\\d+\\??)\\s+(?:\\S+) (?:\\d{8}) \\| (.*)$");
    private static final Pattern LOCAL_REVISION_PATTERN = Pattern.compile("\\d+\\?");
    
    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 1, 2, LOCAL_REVISION_PATTERN);
    }
    
    //------------------------------------------------------------
    //revno: 10
    //committer: Elliott Hughes <enh@jessies.org>
    //branch nick: bzr-tutorial
    //timestamp: Sat 2006-04-29 10:44:51 -0700
    //message:
    //  log message on lines like this.
    //  and this.
    //------------------------------------------------------------
    
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^timestamp: \\S+ (\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2} [-+]\\d{4})$");
    
    private static void reportLogParsingFailure(String expected, String got) {
        throw new RuntimeException("expected " + expected + ", got \"" + got + "\"");
    }
    
    public RevisionListModel parseLog(List<String> linesList) {
        String[] lines = linesList.toArray(new String[linesList.size()]);
        String separator = "------------------------------------------------------------";
        
        int i = 0;
        if (lines[i++].equals(separator) == false) {
            reportLogParsingFailure("separator", lines[0]);
        }
        
        RevisionListModel result = new RevisionListModel();
        String number = null;
        String author = null;
        String date = null;
        String time = null;
        StringBuilder comment = null;
        for (; i < lines.length; ++i) {
            if (lines[i].startsWith("revno: ")) {
                number = lines[i].substring(7);
            } else if (lines[i].startsWith("committer: ")) {
                author = lines[i].substring(11);
            } else if (lines[i].startsWith("branch nick: ")) {
                // Ignore.
            } else if (lines[i].startsWith("timestamp: ")) {
                Matcher matcher = TIMESTAMP_PATTERN.matcher(lines[i]);
                if (matcher.matches()) {
                    date = matcher.group(1);
                    time = matcher.group(2);
                } else {
                    reportLogParsingFailure("separator", lines[i]);
                }
            } else if (lines[i].equals("message:")) {
                comment = new StringBuilder();
                ++i;
                while (i < lines.length) {
                    if (lines[i].equals(separator)) {
                        break;
                    }
                    comment.append(lines[i++]);
                    comment.append("\n");
                }
                if (number == null || author == null || date == null || time == null || comment == null) {
                    throw new RuntimeException("incomplete log record: number=" + number + ",author=" + author + ",date=" + date + ",time=" + time + ",comment" + comment);
                }
                Revision revision = new Revision(number, date, time, author, comment.toString());
                result.add(revision);
                number = author = date = time = null;
            } else {
                reportLogParsingFailure("known header", lines[i]);
            }
        }
        return result;
    }
    
    public boolean isLocallyModified(String filename) {
        String[] command = new String[] { "bzr", "status", filename };
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        for (String line : lines) {
            if (line.equals("modified:")) {
                return true;
            }
        }
        return false;
    }
    
    public boolean supportsChangeSets() {
        return true;
    }
    
    public List<ChangeSetItem> listTouchedFilesInRevision(String filename, Revision revision) {
        String[] command = new String[] { "bzr", "log", "-v", "-r", revision.number };
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        
        ArrayList<ChangeSetItem> result = new ArrayList<>();
        boolean inChangedPaths = false;
        for (String line : lines) {
            if (line.matches("^(added|modified|removed|renamed):$")) {
                inChangedPaths = true;
            } else if (inChangedPaths && line.startsWith("  ")) {
                String path = line.substring(2);
                // Subtracting one doesn't give the number of the previous revision of the file.
                // This means that we can't, in general, successfully look up the revision.oldRevision in a RevisionListModel.
                final String oldRevisionNumber = Integer.toString(Integer.parseInt(revision.number) - 1);
                result.add(new ChangeSetItem(path, oldRevisionNumber, revision.number));
            } else {
                inChangedPaths = false;
            }
        }
        return result;
    }
    
    public void revert(String filename) {
        ArrayList<String> command = new ArrayList<>();
        command.add("bzr");
        command.add("revert");
        command.add("--no-backup");
        command.add(filename);
        execAndDump(command);
    }
    
    public List<FileStatus> getStatuses(StatusReporter statusReporter) {
        String[] command = new String[] { "bzr", "status" };
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        
        HashMap<String, Integer> map = new HashMap<>();
        map.put("added", FileStatus.ADDED);
        map.put("removed", FileStatus.REMOVED);
        map.put("renamed", FileStatus.NOT_RECOGNIZED_BY_BACK_END); // FIXME
        map.put("modified", FileStatus.MODIFIED);
        map.put("unchanged", FileStatus.NOT_RECOGNIZED_BY_BACK_END); // Shouldn't happen.
        map.put("unknown", FileStatus.NEW);
        
        ArrayList<FileStatus> statuses = new ArrayList<>();
        Pattern pattern = Pattern.compile("^(added|removed|renamed|modified|unchanged|unknown):$");
        int canonicalState = FileStatus.NOT_RECOGNIZED_BY_BACK_END;
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                Integer state = map.get(matcher.group(1));
                canonicalState = (state != null) ? state.intValue() : FileStatus.NOT_RECOGNIZED_BY_BACK_END;
            } else if (line.startsWith("  ")) {
                String name = line.substring(2);
                statuses.add(new FileStatus(canonicalState, name));
            } else {
                Log.warn("Bazaar back end didn't understand '" + line + "'.");
            }
        }
        return statuses;
    }
    
    public void commit(String comment, List<FileStatus> fileStatuses, List<FileStatus> excluded) {
        scheduleNewFiles("bzr", "--no-recurse", fileStatuses);
        ArrayList<String> command = new ArrayList<>();
        command.add("bzr");
        command.add("commit");
        command.add("--file");
        command.add(createCommentFile(comment));
        addFilenames(command, fileStatuses);
        execAndDump(command);
    }
}
