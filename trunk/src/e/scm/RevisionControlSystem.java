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
        String clue = null;
        
        String canonicalPath = path;
        try {
            canonicalPath = new File(path).getCanonicalPath();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        File file = new File(canonicalPath);
        File directory = file.isDirectory() ? file : new File(file.getParent());
        for (String sibling : directory.list()) {
            if (sibling.equals("CVS")) {
                clue = "CVS";
            } else if (sibling.equals("SCCS")) {
                clue = "SCCS";
            } else if (sibling.equals(".svn")) {
                clue = ".svn";
            }
        }
        
        if (clue == null) {
            return null;
        }
        
        File root = directory;
        while (true) {
            File newRoot = new File(root.getParent());
            if (newRoot.exists() == false || newRoot.isDirectory() == false) {
                return root;
            }
            File scmDirectory = new File(newRoot, clue);
            if (scmDirectory.exists() == false || scmDirectory.isDirectory() == false) {
                return root;
            }
            root = newRoot;
        }
    }
    
    /**
     * Attempts to guess which revision control system the file we're
     * interested in is managed by. We do this simply on the basis of
     * whether there's a CVS, SCCS or .svn directory in the same directory
     * as the file.
     */
    private static RevisionControlSystem selectRevisionControlSystem(File repositoryRoot) {
        for (String sibling : repositoryRoot.list()) {
            if (sibling.equals("CVS")) {
                return new Cvs();
            } else if (sibling.equals("SCCS")) {
                return new BitKeeper();
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
    public abstract String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename);

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
     * Opens some kind of viewer for the change set corresponding to the
     * given revision of the given file.
     */
    public abstract void showChangeSet(String filename, Revision revision);
    
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
    public abstract List<FileStatus> getStatuses(WaitCursor waitCursor);
    
    /**
     * Commits the files from the given list of FileStatus objects using the comment
     * supplied.
     */
    public abstract void commit(String comment, List<FileStatus> fileStatuses);
    
    // This only copes with spaces in commands, not other shell special punctuation and specifically not ".
    // Fix it when it bites you.
    private static String quoteCommand(List command) {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < command.size(); ++i) {
            if (i > 0) {
                result.append(' ');
            }
            String argument = (String) command.get(i);
            if (argument.contains(" ")) {
                result.append('"');
                result.append(argument);
                result.append('"');
            } else {
                result.append(argument);
            }
        }
        return result.toString();
    }

    /**
     * Executes a command in the given directory, and dumps all the output from it.
     * Useful until we do something funkier.
     */
    public void execAndDump(List<String> commandAsList) {
        String[] command = commandAsList.toArray(new String[commandAsList.size()]);
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        System.err.println("Running " + quoteCommand(commandAsList));
        int status = ProcessUtilities.backQuote(repositoryRoot, command, lines, errors);
        for (int i = 0; i < lines.size(); ++i) {
            System.out.println(lines.get(i));
        }
        for (int i = 0; i < errors.size(); ++i) {
            System.err.println(errors.get(i));
        }
        if (status != 0) {
            throwError(status, command, lines, errors);
        }
    }
    
    /**
     * Reports an error in a dialog, and then throws a RuntimeException.
     */
    public static void throwError(int status, String[] command, List output, List errors) {
        String message = "Command '" + quoteCommand(Arrays.asList(command)) + "' returned status " + status + ".";
        message += "\n\nErrors were [\n";
        if (errors.size() > 0) {
            message += StringUtilities.join(errors, "\n") + "\n";
        }
        message += "].  Output was [\n";
        if (output.size() > 0) {
            message += StringUtilities.join(output, "\n") + "\n";
        }
        message += "]";
        SimpleDialog.showDetails(null, "Back-end Error", message);
        throw new RuntimeException(message);
    }
    
    public static void addFilenames(Collection<String> collection, List<FileStatus> fileStatuses) {
        for (FileStatus file : fileStatuses) {
            collection.add(file.getName());
        }
    }
    
    protected static String createCommentFile(String comment) {
        return FileUtilities.createTemporaryFile("e.scm.RevisionControlSystem-comment", "comment file", comment);
    }
    
    protected static String createFileListFile(List<FileStatus> fileStatuses) {
        StringBuffer content = new StringBuffer();
        for (int i = 0; i < fileStatuses.size(); ++i) {
            content.append(fileStatuses.get(i).getName());
            content.append("\n");
        }
        return FileUtilities.createTemporaryFile("e.scm.RevisionControlSystem-file-list", "list of files", content.toString());
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
    public void scheduleNewFiles(String commandName, boolean useNonRecursiveSwitch, List<FileStatus> fileStatuses) {
        List<FileStatus> newFiles = justNewFiles(fileStatuses);
        if (newFiles.isEmpty()) {
            return;
        }
        ArrayList<String> command = new ArrayList<String>();
        command.add(commandName);
        command.add("add");
        if (useNonRecursiveSwitch) {
            command.add("--non-recursive");
        }
        addFilenames(command, newFiles);
        execAndDump(command);
    }
}
