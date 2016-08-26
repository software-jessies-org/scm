package e.scm;

import java.util.*;
import java.util.regex.*;
import e.util.*;

public class Git extends RevisionControlSystem {
    public String followRenames(Revision revision, String filename) {
        if (revision == null) {
            return filename;
        }
        if (revision == Revision.LOCAL_REVISION) {
            return filename;
        }
        String[] command = new String[] { "git", "log", "--follow", "--name-status", revision.number.toString() + "..HEAD", "--", filename };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        if (lines.size() == 0) {
            return filename;
        }
        String lastLine = lines.get(lines.size() - 1);
        // R100\told name\tnew name
        // M\tname
        String[] fields = lastLine.split("\t");
        String oldName = fields[1];
        return oldName;
    }
    
    public String[] getAnnotateCommand(Revision revision, String filename) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("git");
        command.add("annotate");
        command.add("-l");
        if (revision != Revision.LOCAL_REVISION) {
            command.add(revision.number);
        }
        command.add("--");
        command.add(followRenames(revision, filename));
        return command.toArray(new String[command.size()]);
    }
    
    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename, boolean ignoreWhiteSpace) {
        ArrayList<String> result = new ArrayList<String>();
        result.add("git");
        result.add("diff");
        result.add("--find-renames");
        if (ignoreWhiteSpace) {
            result.add("-w");
        }
        if (olderRevision != null) {
            result.add(olderRevision.number);
            if (newerRevision != Revision.LOCAL_REVISION) {
                result.add(newerRevision.number);
            }
        }
        result.add("--");
        result.add(followRenames(olderRevision, filename));
        result.add(followRenames(newerRevision, filename));
        return result.toArray(new String[result.size()]);
    }
    
    private static final String LOG_SEPARATOR = "a string that must never appear in a check-in comment\f";

    public String[] getLogCommand(String filename) {
        return new String[] { "git", "log", "--follow", "--pretty=tformat:commit=%H%ncommitter=%ce%ndate=%ai%ncomment=%n%s%n%b" + LOG_SEPARATOR, "--", filename };
    }
    
    // The above git annotate produces the like of the following fields, separated by tabs:
    // 894ea547c603f080de46405d4ab678578c884d13
    // (andyc@diesel.dev.bluearc.com
    // 2007-02-28 12:52:09 +0000
    // 14)# already started

    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^([^ ]+)\t([^\t]+)\t([^ ]+) ([^ ]+) ([^\t]+)\t(\\d+)[)](.*)$");
    private static final Pattern LOCAL_REVISION_PATTERN = Pattern.compile("0000000000000000000000000000000000000000");
    
    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 1, 7, LOCAL_REVISION_PATTERN);
    }
    
    // commit=54e89b521ecaf392ad56a20f2034c838c2c966b9
    // committer=andyc@bluearc.com
    // date=2008-09-25 15:44:06 +0100
    // comment=
    // bin/bb:
    // bin/newclass: make them executable

    private static void reportLogParsingFailure(String expected, String got) {
        throw new RuntimeException("expected " + expected + ", got \"" + got + "\"");
    }
    
    private static final Pattern ISO_TIMESTAMP_PATTERN = Pattern.compile("date=(\\S+) (\\S+) (\\S+)");

    public RevisionListModel parseLog(List<String> linesList) {
        String[] lines = linesList.toArray(new String[linesList.size()]);
        
        RevisionListModel result = new RevisionListModel();
        String number = null;
        String author = null;
        String date = null;
        String time = null;
        StringBuilder comment = null;
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].startsWith("commit=")) {
                number = lines[i].substring(7);
            } else if (lines[i].startsWith("committer=")) {
                author = lines[i].substring(10);
            } else if (lines[i].startsWith("date=")) {
                Matcher matcher = ISO_TIMESTAMP_PATTERN.matcher(lines[i]);
                if (matcher.matches()) {
                    date = matcher.group(1);
                    String timeWithoutOffset = matcher.group(2);
                    String offset = matcher.group(3);
                    time = timeWithoutOffset + " " + offset;
                } else {
                    reportLogParsingFailure("date", lines[i]);
                }
            } else if (lines[i].equals("comment=")) {
                comment = new StringBuilder();
                while (++i < lines.length && lines[i].equals(LOG_SEPARATOR) == false) {
                    comment.append(lines[i]);
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
        String[] command = new String[] { "git", "diff", "--name-status", filename };
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
        String[] command = new String[] { "git", "diff", "--name-status", revision.number };
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        
        ArrayList<ChangeSetItem> result = new ArrayList<ChangeSetItem>();
        for (String line : lines) {
            String path = line.substring(2);
            result.add(new ChangeSetItem(path, revision.number, revision.number));
        }
        return result;
    }
    
    public void revert(String filename) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("git");
        command.add("checkout");
        command.add(filename);
        execAndDump(command);
    }
    
    public List<FileStatus> getStatuses(StatusReporter statusReporter) {
        String[] statusCommand = new String[] { "git", "status", "-z" };
        ArrayList<FileStatus> statuses = new ArrayList<FileStatus>();

        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(getRoot(), statusCommand, lines, errors);
        if (status != 0) {
            throwError(status, statusCommand, lines, errors);
        }
        
        String output = StringUtilities.join(lines, "\n");
        int size = output.length();
        int ii = 0;
        while (ii != size) {
            char x = output.charAt(ii);
            ++ ii;
            char y = output.charAt(ii);
            ++ ii;
            if (output.charAt(ii) != ' ') {
                throw new RuntimeException("lost synchronization with output of " + statusCommand + ": " + output);
            }
            ++ ii;
            StringBuilder name = new StringBuilder();
            while (output.charAt(ii) != 0) {
                name.append(output.charAt(ii));
                ++ ii;
            }
            ++ ii;
            if (x == 'R') {
                // The "to" name precedes the "from" name for renames.
                while (output.charAt(ii) != 0) {
                    ++ ii;
                }
                ++ ii;
            }
            int canonicalState = FileStatus.NOT_RECOGNIZED_BY_BACK_END;
            if (x == ' ' && y == 'M') {
                canonicalState = FileStatus.MODIFIED;
            } else if (x == 'D' && y == ' ') {
                // git rm already done.
                canonicalState = FileStatus.REMOVED;
            } else if (x == '?' && y == '?') {
                canonicalState = FileStatus.NEW;
            } else if (x == 'R' && y == ' ') {
                canonicalState = FileStatus.ADDED;
            }
            // FIXME: All the other combinations.
            if (canonicalState != FileStatus.IGNORED) {
                statuses.add(new FileStatus(canonicalState, name.toString()));
            }
        }

        return statuses;
    }
    
    public void commit(String comment, List<FileStatus> fileStatuses) {
        for (FileStatus file : fileStatuses) {
            if (file.getState() == FileStatus.REMOVED) {
                continue;
            }
            ArrayList<String> command = new ArrayList<String>();
            command.add("git");
            command.add("add");
            command.add(file.getName());
            execAndDump(command);
        }

        ArrayList<String> command = new ArrayList<String>();
        command.add("git");
        command.add("commit");
        command.add("--file=" + createCommentFile(comment));
        execAndDump(command);
    }
}
