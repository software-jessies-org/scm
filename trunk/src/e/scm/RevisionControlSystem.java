package e.scm;

import java.io.File;
import java.io.*;
import java.util.*;
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
        String[] siblings = directory.list();
        for (int i = 0; i < siblings.length; ++i) {
            if (siblings[i].equals("CVS")) {
                clue = "CVS";
            } else if (siblings[i].equals("SCCS")) {
                clue = "SCCS";
            } else if (siblings[i].equals(".svn")) {
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
        String[] siblings = repositoryRoot.list();
        for (int i = 0; i < siblings.length; ++i) {
            if (siblings[i].equals("CVS")) {
                return new Cvs();
            } else if (siblings[i].equals("SCCS")) {
                return new BitKeeper();
            } else if (siblings[i].equals(".svn")) {
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
    public abstract RevisionListModel parseLog(List lines);

    /**
     * Tests whether a given file has been locally modified.
     */
    public abstract boolean isLocallyModified(String filename);

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
    public abstract List getStatuses(WaitCursor waitCursor);
    
    /**
     * Commits the files from the given list of FileStatus objects using the comment
     * supplied.
     */
    public abstract void commit(String comment, List/*<FileStatus>*/ fileStatuses);
    
    // This only copes with spaces in commands, not other shell special punctuation and specifically not ".
    // Fix it when it bites you.
    private String quoteCommand(List command) {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < command.size(); ++i) {
            if (i > 0) {
                result.append(' ');
            }
            String argument = (String) command.get(i);
            if (argument.indexOf(' ') != -1) {
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
    public void execAndDump(List command) {
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        System.err.println("Running " + quoteCommand(command));
        int status = ProcessUtilities.backQuote(repositoryRoot, (String[]) command.toArray(new String[command.size()]), lines, errors);
        for (int i = 0; i < lines.size(); ++i) {
            System.out.println(lines.get(i));
        }
        for (int i = 0; i < errors.size(); ++i) {
            System.err.println(errors.get(i));
        }
        if (status != 0) {
            throw new RuntimeException("[Command '" + StringUtilities.join(command, " ") + "' returned status " + status + ".]");
        }
    }
    
    public static void addFilenames(Collection collection, List/*<FileStatus>*/ fileStatuses) {
        for (int i = 0; i < fileStatuses.size(); ++i) {
            FileStatus file = (FileStatus) fileStatuses.get(i);
            collection.add(file.getName());
        }
    }
    
    public static List justNewFiles(List/*<FileStatus>*/ fileStatuses) {
        ArrayList result = new ArrayList();
        for (int i = 0; i < fileStatuses.size(); ++i) {
            FileStatus file = (FileStatus) fileStatuses.get(i);
            if (file.getState() == FileStatus.NEW) {
                result.add(file);
            }
        }
        return result;
    }
    
    /**
     * Used by CVS and Subversion.
     */
    public void scheduleNewFiles(String commandName, List/*<FileStatus>*/ fileStatuses) {
        List newFiles = justNewFiles(fileStatuses);
        if (newFiles.isEmpty()) {
            return;
        }
        ArrayList command = new ArrayList();
        command.add(commandName);
        command.add("add");
        command.add("--non-recursive");
        addFilenames(command, newFiles);
        execAndDump(command);
    }
}
