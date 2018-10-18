package e.scm;

import java.util.*;
import java.util.regex.*;
import e.util.*;

// FIXME: sometimes we see 123:deadbeef, sometimes just 123, sometimes just deadbeef. "123" is the "revision number", "deadbeef" the "changeset number". We should learn about these and decide what to do. For the moment, we just use the revision number.
// FIXME: Mercurial seems to include empty merges as changesets. Perhaps we want to elide these, or somehow make them less in your face.
public class Mercurial extends RevisionControlSystem {
    public String followRenames(Revision revision, String filename) {
        if (revision == null) {
            return filename;
        }
        if (revision == Revision.LOCAL_REVISION) {
            return filename;
        }
        String[] command = new String[] { "hg", "diff", "--git", "-r", revision.number };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        Pattern pattern = Pattern.compile("^(?:copy|rename) (to|from) (.+)$");
        String to = "";
        Collections.reverse(lines);
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches() == false) {
                continue;
            }
            String direction = matcher.group(1);
            String candidate = matcher.group(2);
            if (direction.equals("to")) {
                to = candidate;
            } else if (to.equals(filename)) {
                filename = candidate;
            }
        }
        return filename;
    }
    
    public String[] getAnnotateCommand(Revision revision, String filename) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("hg");
        command.add("annotate");
        command.add("--number");
        command.add("--file");
        if (revision != Revision.LOCAL_REVISION) {
            command.add("-r");
            command.add(revision.number);
        }
        command.add(followRenames(revision, filename));
        return command.toArray(new String[command.size()]);
    }
    
    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename, boolean ignoreWhiteSpace) {
        ArrayList<String> result = new ArrayList<String>();
        result.add("hg");
        result.add("diff");
        if (ignoreWhiteSpace) {
            result.add("-w");
        }
        if (olderRevision != null) {
            result.add("-r");
            result.add(olderRevision.number);
            if (newerRevision != Revision.LOCAL_REVISION) {
                result.add("-r");
                result.add(newerRevision.number);
            }
        }
        result.add(followRenames(newerRevision, filename));
        return result.toArray(new String[result.size()]);
    }
    
    public String[] getLogCommand(String filename) {
        return new String[] { "hg", "log", "-v", "--follow", filename };
    }
    
    // 544: #!/bin/sh
    // 186: #
    //1599: # This is an example of using HGEDITOR to create of diff to review the
    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^\\s*(\\d+\\+?)\\s+([^:]+): (.*)$");
    private static final Pattern LOCAL_REVISION_PATTERN = Pattern.compile("\\d+\\+");
    
    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 1, 3, LOCAL_REVISION_PATTERN);
    }
    
    //changeset:   2242:12e36dedf668c483df6fe0fbfb3f0efdaded8b4c
    //user:        Vadim Gelfer <vadim.gelfer@gmail.com>
    //date:        Thu May  4 21:44:09 2006 -0700
    //files:       README
    //description:
    //update README.
    //fix issue 225.
    //do not use deprecated commands in examples.
    //
    //
    
    // Thu May  4 21:44:09 2006 -0700
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^date:        ... (\\S+) +(\\d+) (\\d{2}:\\d{2}:\\d{2}) (\\d{4}) ([-+]\\d{4})$");
    
    private static final HashMap<String, Integer> monthMap = new HashMap<String, Integer>();
    static {
        monthMap.put("Jan", 1);
        monthMap.put("Feb", 2);
        monthMap.put("Mar", 3);
        monthMap.put("Apr", 4);
        monthMap.put("May", 5);
        monthMap.put("Jun", 6);
        monthMap.put("Jul", 7);
        monthMap.put("Aug", 8);
        monthMap.put("Sep", 9);
        monthMap.put("Oct", 10);
        monthMap.put("Nov", 11);
        monthMap.put("Dec", 12);
    }
    
    private static void reportLogParsingFailure(String expected, String got) {
        throw new RuntimeException("expected " + expected + ", got \"" + got + "\"");
    }
    
    public RevisionListModel parseLog(List<String> linesList) {
        String[] lines = linesList.toArray(new String[linesList.size()]);
        
        RevisionListModel result = new RevisionListModel();
        String number = null;
        String author = null;
        String date = null;
        String time = null;
        StringBuilder comment = null;
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].startsWith("changeset:   ")) {
                number = lines[i].substring(13);
                // FIXME: I'm sure this isn't right, but "hg" doesn't seem to accept the long form as a revision argument.
                number = number.replaceAll("^(\\d+):.*$", "$1");
            } else if (lines[i].startsWith("user:        ")) {
                author = lines[i].substring(13);
            } else if (lines[i].startsWith("files:       ")) {
                // Ignore.
            } else if (lines[i].startsWith("parent:      ")) {
                // Ignore.
                // FIXME: this is potentially useful for the "bzrk"-style view.
            } else if (lines[i].startsWith("tag:         ")) {
                // Ignore.
            } else if (lines[i].startsWith("branch:      ")) {
                // Ignore.
            } else if (lines[i].startsWith("date:        ")) {
                Matcher matcher = TIMESTAMP_PATTERN.matcher(lines[i]);
                if (matcher.matches()) {
                    String englishMonth = matcher.group(1);
                    int dayOfMonth = Integer.parseInt(matcher.group(2));
                    String timeWithoutOffset = matcher.group(3);
                    String year = matcher.group(4);
                    String offset = matcher.group(5);
                    date = String.format("%s-%02d-%02d", year, monthMap.get(englishMonth), dayOfMonth);
                    time = timeWithoutOffset + " " + offset;
                } else {
                    reportLogParsingFailure("date", lines[i]);
                }
            } else if (lines[i].equals("description:")) {
                // FIXME: how do they represent a "description" that contains two blank lines? Experiment suggests "they don't".
                comment = new StringBuilder();
                ++i;
                while (i < lines.length) {
                    if (lines[i].length() + lines[i+1].length() == 0 && (i+2 == lines.length || lines[i+2].startsWith("changeset:   "))) {
                        ++i;
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
        String[] command = new String[] { "hg", "status", filename };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        for (String line : lines) {
            if (line.startsWith("M ")) {
                return true;
            }
        }
        return false;
    }
    
    public boolean supportsChangeSets() {
        return true;
    }
    
    public List<ChangeSetItem> listTouchedFilesInRevision(String filename, Revision revision) {
        String[] command = new String[] { "hg", "log", "-v", "-r", revision.number };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        
        ArrayList<ChangeSetItem> result = new ArrayList<ChangeSetItem>();
        for (String line : lines) {
            if (line.startsWith("files:       ")) {
                // FIXME: how do they cope with filenames containing spaces? Experiment suggests "they don't".
                String[] paths = line.substring(13).split(" ");
                for (String path : paths) {
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
        command.add("hg");
        command.add("revert");
        // We keep our own backups (though we should should make this clear in the UI).
        command.add("--no-backup");
        command.add(filename);
        execAndDump(command);
    }
    
    public List<FileStatus> getStatuses(StatusReporter statusReporter) {
        String[] command = new String[] { "hg", "status" };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        map.put("A", FileStatus.ADDED);
        map.put("R", FileStatus.REMOVED);
        map.put("M", FileStatus.MODIFIED);
        map.put("?", FileStatus.NEW);
        
        ArrayList<FileStatus> statuses = new ArrayList<FileStatus>();
        Pattern pattern = Pattern.compile("^([MAR?]) (.*)$");
        int canonicalState = FileStatus.NOT_RECOGNIZED_BY_BACK_END;
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String path = matcher.group(2);
                Integer state = map.get(matcher.group(1));
                canonicalState = (state != null) ? state.intValue() : FileStatus.NOT_RECOGNIZED_BY_BACK_END;
                statuses.add(new FileStatus(canonicalState, path));
            } else {
                Log.warn("Mercurial back end didn't understand '" + line + "'.");
            }
        }
        return statuses;
    }
    
    public boolean isMerge() {
        String[] command = new String[] { "hg", "heads", "." };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        // "Returns 0 if matching heads are found, 1 if not."
        // With "no open branch heads found on branches default" on stderr.
        // There are no heads in a repository that's just been through "hg init".
        if (status == 1 && lines.isEmpty()) {
            return false;
        }
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        int heads = 0;
        for (String line : lines) {
            if (line.startsWith("changeset:")) {
                ++ heads;
            }
        }
        return heads > 1;
    }
    
    public void commit(String comment, List<FileStatus> fileStatuses) {
        scheduleNewFiles("hg", null, fileStatuses);
        ArrayList<String> command = new ArrayList<String>();
        command.add("hg");
        command.add("commit");
        command.add("--logfile");
        command.add(createCommentFile(comment));
        if (isMerge() == false) {
            addFilenames(command, fileStatuses);
        }
        execAndDump(command);
    }
    
    public void approvePartialCommit() {
        if (isMerge()) {
            throw new RuntimeException("Mercurial won't let you commit a merge without including everything");
        }
    }
}
