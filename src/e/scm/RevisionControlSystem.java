package e.scm;

import java.io.File;
import java.io.*;
import java.util.*;
import e.gui.*;
import e.util.*;

/**
 * The interface to various revision control systems.
 *
 * Filenames will be relative to the repository root, for the
 * benefit of systems as stupid as CVS.
 */
public abstract class RevisionControlSystem {
    /**
     * Returns an instance of a subclass of RevisionControlSystem suitable for
     * use with the given file or directory.
     */
    public static RevisionControlSystem forPath(String path) {
        File root = findRepositoryRoot(path);
        if (root == null) {
            return null;
        }
        RevisionControlSystem backEnd = selectRevisionControlSystem(root);
        backEnd.setRoot(root);
        return backEnd;
    }
    
    /**
     * Searches up the directory hierarchy looking for the highest level that
     * still contains a revision control system directory of the kind found
     * at the given point.
     */
    private static File findRepositoryRoot(final String path) {
        String canonicalPath = path;
        try {
            canonicalPath = FileUtilities.fileFromString(path).getCanonicalPath();
        } catch (IOException ex) {
            Log.warn("Couldn't canonicalize path.", ex);
        }
        File file = new File(canonicalPath);
        if (file.exists() == false) {
            Log.warn("\"" + file + "\" does not exist.");
            // We can still usefully run when the target file doesn't exist.
            // BitKeeper often doesn't check-out files in BitKeeper/deleted/ but will happily allow you to look at the history.
            // I've seen similar behavior in other systems too.
        }
        
        // Is there evidence of revision control in this directory?
        // This is carefully crafted such that, if "file" doesn't exist, we look at its parent directory.
        File directory = file.isDirectory() ? file : new File(file.getParent());
        for (String sibling : directory.list()) {
            if (sibling.equals(".bzr")) {
                return ascendUntilSubdirectoryDisappears(directory, ".bzr");
            } else if (sibling.equals("CVS")) {
                return ascendUntilSubdirectoryDisappears(directory, "CVS");
            } else if (sibling.equals(".hg")) {
                return ascendUntilSubdirectoryDisappears(directory, ".hg");
            } else if (sibling.equals("SCCS") || sibling.equals(".bk")) {
                return ascendUntilSubdirectoryAppears(directory, "BitKeeper");
            } else if (sibling.equals(".svn")) {
                return ascendUntilSubdirectoryDisappears(directory, ".svn");
            } else if (sibling.equals(".git")) {
                return ascendUntilSubdirectoryDisappears(directory, ".git");
            }
        }
        
        // We've found nothing yet, so try again in the parent.
        String parent = file.getParent();
        if (parent != null) {
            return findRepositoryRoot(parent);
        }
        
        // We're in "/", so admit defeat.
        return null;
    }
    
    private static boolean isScmDirectoryAbsent(final File directory, final String subdirectoryName) {
        File scmDirectory = new File(directory, subdirectoryName);
        return scmDirectory.exists() == false || scmDirectory.isDirectory() == false;
    }
    private static File ascendUntilSubdirectoryAppearsOrDisappears(final File directory, final String subdirectoryName, final boolean stopWhenAbsent) {
        File previousRoot = null;
        File root = directory;
        while (isScmDirectoryAbsent(root, subdirectoryName) != stopWhenAbsent) {
            String parentName = root.getParent();
            if (parentName == null) {
                return null;
            }
            previousRoot = root;
            root = new File(parentName);
            if (root.exists() == false || root.isDirectory() == false) {
                return null;
            }
        }
        // Always return the highest directory which did contain the scmDirectory,
        // even if we only stop when we've higher.
        return stopWhenAbsent ? previousRoot : root;
    }
    
    private static File ascendUntilSubdirectoryDisappears(final File directory, final String subdirectoryName) {
        return ascendUntilSubdirectoryAppearsOrDisappears(directory, subdirectoryName, true);
    }
    
    private static File ascendUntilSubdirectoryAppears(final File directory, final String subdirectoryName) {
        return ascendUntilSubdirectoryAppearsOrDisappears(directory, subdirectoryName, false);
    }
    
    /**
     * Attempts to guess which revision control system the file we're
     * interested in is managed by. We do this simply on the basis of
     * whether there's a .bk, .bzr, CVS, .hg, SCCS, .git or .svn directory
     * in the same directory as the file.
     */
    private static RevisionControlSystem selectRevisionControlSystem(File repositoryRoot) {
        for (String sibling : repositoryRoot.list()) {
            if (sibling.equals(".bzr")) {
                return new Bazaar();
            } else if (sibling.equals("CVS")) {
                return new Cvs();
            } else if (sibling.equals(".hg")) {
                return new Mercurial();
            } else if (sibling.equals("SCCS") || sibling.equals(".bk")) {
                return new BitKeeper();
            } else if (sibling.equals(".git")) {
                return new Git();
            } else if (sibling.equals(".svn")) {
                return new Subversion();
            }
        }
        // We know this is wrong, but CVS is likely to be installed,
        // and will give a reasonably good error when invoked.
        return new Cvs();
    }
    
    private File repositoryRoot;
    
    private void setRoot(File root) {
        this.repositoryRoot = root;
    }
    
    public File getRoot() {
        return repositoryRoot;
    }

    /**
     * Returns a command that gets the annotated form of the given revision
     * of the given file. You may be given Revision.LOCAL_REVISION as revision.
     */
    public abstract String[] getAnnotateCommand(Revision revision, String filename);

    /**
     * Parses one of the lines returned on standard output by the command from
     * getAnnotateCommand, building an AnnotatedLine.
     */
    public abstract AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line);

    /**
     * Returns a command that gets a patch for the given file from the older
     * revision to the newer revision. You may be given Revision.LOCAL_REVISION
     * as newerRevision. You may be given null for both revisions, which is
     * used by CheckInTool to ask for the differences between a locally-modified
     * file and the repository.
     */
    public abstract String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename, boolean ignoreWhiteSpace);

    /** Returns a command that gets the revision log for the given file. */
    public abstract String[] getLogCommand(String filename);

    /**
     * Parses the lines returned on standard output by the command from
     * getLogCommand, building a RevisionListModel.
     */
    public abstract RevisionListModel parseLog(List<String> lines);

    /**
     * Tests whether a given file has been locally modified.
     */
    public abstract boolean isLocallyModified(String filename);
    
    /**
     * Tests whether meta-data is cheap to access, which generally means it's
     * stored locally, as with BitKeeper. This can be used to enable/disable
     * advanced features that work okay for (say) BitKeeper, but are too slow
     * to use with broken designs like Subversion's.
     */
    public boolean isMetaDataCheap() {
        return false;
    }
    
    /**
     * Tests whether this revision control system supports the notion of
     * change sets, where a number of files are grouped together as a single
     * change.
     */
    public abstract boolean supportsChangeSets();
    
    /**
     * Returns a list of the files touched (created or modified) in a given revision.
     */
    public abstract List<ChangeSetItem> listTouchedFilesInRevision(String filename, Revision revision);
    
    //
    // Methods relating to reverting.
    //

    /**
     * Discards local modifications and reverts to the most recent revision
     * in the repository.
     */
    public abstract void revert(String filename);

    //
    // Methods relating to checking in.
    //
    
    /**
     * Returns status information for all the added, deleted, modified or
     * unmanaged files and directories in the repository.
     */
    public abstract List<FileStatus> getStatuses(StatusReporter statusReporter);
    
    /**
     * Commits the files from the given list of FileStatus objects using the comment
     * supplied.
     */
    public abstract void commit(String comment, List<FileStatus> fileStatuses);
    
    public void execAndDump(List<String> commandAsList) {
        execAndDumpWithInput(commandAsList, "");
    }
    
    /**
     * Executes a command in the given directory, and dumps all the output from it.
     */
    public void execAndDumpWithInput(List<String> commandAsList, String input) {
        String[] command = commandAsList.toArray(new String[commandAsList.size()]);
        String tool = command[0];
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        Log.warn(tool + ": running echo '" + input +"' | \"" + ProcessUtilities.shellQuotedFormOf(commandAsList) + "\"...");
        int status = ProcessUtilities.backQuote(repositoryRoot, command, input, lines, errors);
        for (int i = 0; i < lines.size(); ++i) {
            Log.warn(tool + ": stdout: " + lines.get(i));
        }
        for (int i = 0; i < errors.size(); ++i) {
            Log.warn(tool + ": stderr: " + errors.get(i));
        }
        if (status != 0) {
            Log.warn(tool + ": exited with status " + status + ".");
            throwError(status, command, lines, errors);
        }
    }
    
    /**
     * Reports an error in a dialog, and then throws a RuntimeException.
     */
    public static void throwError(int status, String[] command, List<String> output, List<String> errors) {
        String message = "Command '" + ProcessUtilities.shellQuotedFormOf(Arrays.asList(command)) + "' returned status " + status + ".";
        message += "\n\nErrors were [\n";
        if (errors.size() > 0) {
            message += StringUtilities.join(errors, "\n") + "\n";
        }
        message += "].  Output was [\n";
        if (output.size() > 0) {
            message += StringUtilities.join(output, "\n") + "\n";
        }
        message += "]";
        throw new RuntimeException(message);
    }
    
    public static void addFilenames(Collection<String> collection, List<FileStatus> fileStatuses) {
        for (FileStatus file : fileStatuses) {
            collection.add(file.getName());
        }
    }
    
    protected static String createCommentFile(String comment) {
        return FileUtilities.createTemporaryFile("e.scm.RevisionControlSystem-comment", ".txt", "comment file", comment).toString();
    }
    
    protected static String createFileListFile(List<FileStatus> fileStatuses) {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < fileStatuses.size(); ++i) {
            content.append(fileStatuses.get(i).getName());
            content.append("\n");
        }
        return FileUtilities.createTemporaryFile("e.scm.RevisionControlSystem-file-list", ".tmp", "list of files", content.toString()).toString();
    }
    
    public static List<FileStatus> justNewFiles(List<FileStatus> fileStatuses) {
        ArrayList<FileStatus> result = new ArrayList<FileStatus>();
        for (FileStatus file : fileStatuses) {
            if (file.getState() == FileStatus.NEW) {
                result.add(file);
            }
        }
        return result;
    }
    
    /**
     * Used by CVS and Subversion.
     */
    public void scheduleNewFiles(String commandName, String nonRecursiveSwitch, List<FileStatus> fileStatuses) {
        List<FileStatus> newFiles = justNewFiles(fileStatuses);
        if (newFiles.isEmpty()) {
            return;
        }
        ArrayList<String> command = new ArrayList<String>();
        command.add(commandName);
        command.add("add");
        if (nonRecursiveSwitch != null) {
            command.add(nonRecursiveSwitch);
        }
        addFilenames(command, newFiles);
        execAndDump(command);
    }
}
