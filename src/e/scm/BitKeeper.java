package e.scm;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import e.util.*;

public class BitKeeper extends RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        ArrayList<String> command = new ArrayList<>();
        command.add("bk");
        command.add("annotate");
        if (revision != Revision.LOCAL_REVISION) {
            command.add("-r" + revision.number);
        }
        command.add(filename);
        return command.toArray(new String[command.size()]);
    }

    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename, boolean ignoreWhiteSpace) {
        ArrayList<String> result = new ArrayList<>();
        result.add("bk");
        result.add("diffs");
        result.add("-u");
        if (ignoreWhiteSpace) {
            result.add("-w");
        }
        if (olderRevision != null) {
            result.add("-r" + olderRevision.number);
            if (newerRevision != Revision.LOCAL_REVISION) {
                result.add("-r" + newerRevision.number);
            }
        }
        result.add(filename);
        return result.toArray(new String[result.size()]);
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "bk", "prs", "-n", "-d:DSUMMARY:\n:COMMENTS:" + LOG_SEPARATOR, filename };
    }

    private static final Pattern ANNOTATED_LINE_PATTERN = Pattern.compile("^([^\t]+)\t([^\t]+)\t(.*)$");
    // bk doesn't annotate local changes.
    private static final Pattern LOCAL_REVISION_PATTERN = Pattern.compile("\t");

    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line) {
        return AnnotatedLine.fromLine(revisions, line, ANNOTATED_LINE_PATTERN, 2, 3, LOCAL_REVISION_PATTERN);
    }

    //D 1.2 04/02/15 13:02:22+00:00 elliotth@mercury.local 3 2 4/0/356
    //D 1.144 01/03/28 11:56:53-00:00 hughc 145 144 0/3/3528
    //D 1.1 2002/11/11 11:11:11+11:11 11@eleven.com 1 1 1/1/1
    // From backport-new-file.rb:
    //D 1.1 2007/07/09 12:36:34 martind@duezer.us.dev.bluearc.com 2 1 9/0/0
    private static final Pattern LOG_PATTERN = Pattern.compile("^D ([0-9.]+) (\\d\\d|\\d{4})/(\\d\\d)/(\\d\\d) (\\d{2}:\\d{2}:\\d{2}(?:[-+]\\d{2}:\\d{2})?) ([^@ ]+).*");
    private static final String LOG_SEPARATOR = "a string that must never appear in a check-in comment\f";
    
    public RevisionListModel parseLog(List<String> linesList) {
        String[] lines = linesList.toArray(new String[linesList.size()]);

        RevisionListModel result = new RevisionListModel();
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].equals(LOG_SEPARATOR)) {
                continue;
            }
            
            Matcher matcher = LOG_PATTERN.matcher(lines[i]);
            if (matcher.matches()) {
                String number = matcher.group(1);
                String time = matcher.group(5);
                String author = matcher.group(6);
                String year = matcher.group(2).length() == 2 ? "20" + matcher.group(2) : matcher.group(2);
                String date = year + "-" + matcher.group(3) + "-" + matcher.group(4);
                StringBuilder comment = new StringBuilder();
                while (++i < lines.length && lines[i].equals(LOG_SEPARATOR) == false) {
                    comment.append(lines[i].substring(2));
                    comment.append("\n");
                }
                result.add(new Revision(number, date, time, author, comment.toString()));
            }
        }
        return result;
    }

    public boolean isLocallyModified(String filename) {
        String[] command = new String[] {
            "bk", "sfiles", "-v", "-c", "-g", filename
        };
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        for (String line : lines) {
            if (line.startsWith("lc") || line.startsWith("slc")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns true, because BitKeeper has all the meta-data locally.
     */
    public boolean isMetaDataCheap() {
        return true;
    }
    
    public boolean supportsChangeSets() {
        return true;
    }
    
    public List<ChangeSetItem> listTouchedFilesInRevision(String filename, Revision revision) {
        String changeSetNumber = getChangeSetNumberForRevision(filename, revision);
        List<String> changeSet = getChangeSetByNumber(changeSetNumber);
        return extractChangeSetItems(changeSet);
    }
    
    /**
     * Given a filename and a revision of that file, find the change set that change belongs to.
     */
    private String getChangeSetNumberForRevision(String filename, Revision revision) {
        String[] command = new String[] { "bk", "r2c", "-r" + revision.number, filename };
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        return lines.get(0);
    }
    
    /**
     * Returns the contents of a change set given the change set number.
     */
    private List<String> getChangeSetByNumber(String changeSetNumber) {
        // bk-4.6b's rset.
        String[] command = new String[] { "bk", "changes", "-r" + changeSetNumber, "-v", "-n", "-d$if(:PARENT:){:GFILE:|:PARENT:..:REV:}" };
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        return lines;
    }
    
    private List<ChangeSetItem> extractChangeSetItems(List<String> changeSet) {
        // The output consists of changes one per line, looking like this: "ChangeSet|1.4818..1.4818.1.1"
        // or like this for merge change sets "ChangeSet|1.4887.295.1+1.4887.281.1..1.4887.304.1".
        // bk 5.3 sometimes gives us md5keys in place of revision numbers in the per-file output of bk rset
        // but still uses revision numbers for the ChangeSet.
        String revisionSpecifier = "(?:1(?:\\.\\d+)+|[-_A-Za-z0-9]{30})";
        Pattern pattern = Pattern.compile("^(.*)\\|(" + revisionSpecifier + "(?:\\+" + revisionSpecifier + ")?)\\.\\.(" + revisionSpecifier + ")$");
        ArrayList<ChangeSetItem> result = new ArrayList<>();
        for (String change : changeSet) {
            Matcher matcher = pattern.matcher(change);
            if (matcher.matches()) {
                String filename = matcher.group(1);
                if (filename.equals("ChangeSet")) {
                    // The file "ChangeSet" is a BitKeeper implementation detail which we shouldn't expose to the user.
                    continue;
                }
                String oldRevision = matcher.group(2);
                String newRevision = matcher.group(3);
                result.add(new ChangeSetItem(filename, oldRevision, newRevision));
            } else {
                throw new RuntimeException("unintelligible line '" + change + "' from BitKeeper");
            }
        }
        return result;
    }
    
    public void revert(String filename) {
        ArrayList<String> command = new ArrayList<>();
        command.add("bk");
        command.add("unedit");
        command.add(filename);
        execAndDump(command);
    }
    
    /**
     * Returns the number of files in the repository, so we can give determinate progress.
     */
    private int getRepositoryFileCount() {
        String[] command = new String[] { "bk", "log", "-hr+", "-d:HASHCOUNT:" };
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0 || lines.size() != 1) {
            return -1;
        }
        String line = lines.get(0);
        return Integer.parseInt(line);
    }
    
    public List<FileStatus> getStatuses(final StatusReporter statusReporter) {
        // Switch to a determinate progress bar as soon as possible...
        final int fileCount = getRepositoryFileCount();
        
        // Create a temporary file for the interesting output; we'll get progress information on standard out.
        File temporaryFile = null;
        try {
            temporaryFile = File.createTempFile("scm-BitKeeper-", null);
            temporaryFile.deleteOnExit();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
        String[] command = new String[] { "bk", "sfiles", "-c", "-g", "-x", "-v", "-p", "-o" + temporaryFile.toString() };
        ProcessUtilities.LineListener outputListener = new ProcessUtilities.LineListener() {
            public void processLine(String line) {
                // Update the progress bar based on BitKeeper's progress feedback.
                int value = Integer.parseInt(line.substring(0, line.indexOf(' ')));
                statusReporter.setProgressValue(value, fileCount);
            }
        };
        ArrayList<String> notReallyTheOutput = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        ProcessUtilities.LineListener errorsListener = new ProcessUtilities.ArrayListLineListener(errors);
        int status = ProcessUtilities.backQuote(getRoot(), command, "", outputListener, errorsListener);
        if (status != 0) {
            throwError(status, command, notReallyTheOutput, errors);
        }
        
        // Read in BitKeeper's real output.
        List<String> lines = Arrays.asList(StringUtilities.readLinesFromFile(temporaryFile.toString()));
        
        ArrayList<FileStatus> statuses = new ArrayList<>();
        Pattern pattern = Pattern.compile("^(.{4}|.{7})\\s+(.+)(@[0-9.]+)?$");
        for (String line : lines) {
            int canonicalState = FileStatus.NOT_RECOGNIZED_BY_BACK_END;
            String name = null;
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String state = matcher.group(1);
                name = matcher.group(2);
                if (name.equals("ChangeSet")) {
                    // There seems to be a BitKeeper implementation detail that means
                    // that sometimes ChangeSet is included, though it shouldn't be.
                    canonicalState = FileStatus.IGNORED;
                } else if (state.equals("jjjj") || state.matches("j......")) {
                    // Junk. Ignore.
                    canonicalState = FileStatus.IGNORED;
                } else if (state.equals("xxxx") || state.matches("x......")) {
                    canonicalState = FileStatus.NEW;
                } else if (state.matches(".c..") || state.matches("s.c....")) {
                    canonicalState = FileStatus.MODIFIED;
                } else if (state.matches("..p.") || state.matches("s..p...")) {
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
        temporaryFile.delete();
        return statuses;
    }
    
    private String getPendingDelta(String filename) {
        String[] command = new String[] {
            "bk", "sfiles", "-pC", filename
        };
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        int status = ProcessUtilities.backQuote(getRoot(), command, lines, errors);
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
        return lines.get(0);
    }
    
    public void commit(String comment, List<FileStatus> fileStatuses, List<FileStatus> excluded) {
        Path resync = Paths.get(getRoot().toString(), "RESYNC");
        if (Files.isDirectory(resync) || getRoot().getFileName().toString().equals("RESYNC")) {
            throw new RuntimeException("This repository has a RESYNC directory. SCM's BitKeeper back-end doesn't support resolving merge conflicts, nor committing while there are merge conflicts to be resolved. Please use \"bk resolve\" to fix the problem.");
        }
        
        // We write the comment to a file once, because it doesn't change.
        String commentFilename = createCommentFile(comment);
        
        StringBuilder input = new StringBuilder();
        // We run "bk delta" once per file, to avoid OS command-line limits.
        for (FileStatus file : fileStatuses) {
            // The delta will fail if there's no modification, which is untidy.
            if (file.getState() != FileStatus.ADDED && file.getState() != FileStatus.REMOVED) {
                ArrayList<String> command = new ArrayList<>();
                command.add("bk");
                command.add("delta");
                command.add("-a"); // Allow new files to be added without a separate 'bk new <file>'.
                command.add("-Y" + commentFilename);
                command.add(file.getName());
                execAndDump(command);
            } else {
                // If a pre-commit trigger fails, then the comment might have changed.
                // This overwrites the default file comments on eg bk cp.
                ArrayList<String> command = new ArrayList<>();
                command.add("bk");
                command.add("comments");
                command.add("-Y" + commentFilename);
                command.add(file.getName());
                execAndDump(command);
            }
            input.append(getPendingDelta(file.getName()));
            input.append("\n");
        }
        
        ArrayList<String> command = new ArrayList<>();
        command.add("bk");
        command.add("commit");
        command.add("--standalone");
        command.add("-Y" + commentFilename);
        command.add("-");
        execAndDumpWithInput(command, input.toString());
    }
}
