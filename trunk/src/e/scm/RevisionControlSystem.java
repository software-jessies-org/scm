package e.scm;

import java.io.File;
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
     * Returns a command that gets the annotated form of the given revision
     * of the given file.
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
     * as newer revision. You may be given null for both revisions, which is
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
    public abstract boolean isLocallyModified(File repositoryRoot, String filename);

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
    public abstract void showChangeSet(File repositoryRoot, String filename, Revision revision);
    
    //
    // Methods relating to checking in.
    //
    
    /**
     * Returns status information for all the added, deleted, modified or
     * unmanaged files and directories in the repository.
     */
    public abstract List getStatuses(File repositoryRoot);
    
    /**
     * Commits the files from the given list of FileStatus objects using the comment
     * supplied.
     */
    public abstract void commit(File repositoryRoot, String comment, List/*<FileStatus>*/ fileStatuses);
    
    /**
     * Executes a command in the given directory, and dumps all the output from it.
     * Useful until we do something funkier.
     */
    public static void execAndDump(File repositoryRoot, List command) {
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
    public static void scheduleNewFiles(File repositoryRoot, String commandName, List/*<FileStatus>*/ fileStatuses) {
        List newFiles = justNewFiles(fileStatuses);
        if (newFiles.isEmpty()) {
            return;
        }
        ArrayList command = new ArrayList();
        command.add(commandName);
        command.add("add");
        addFilenames(command, newFiles);
        execAndDump(repositoryRoot, command);
    }
}
