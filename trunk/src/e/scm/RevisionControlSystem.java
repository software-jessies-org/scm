package e.scm;

import java.io.File;
import java.util.List;

/**
 * The interface to various revision control systems.
 *
 * Filenames will be relative to the repository root, for the
 * benefit of systems as stupid as CVS.
 */
public interface RevisionControlSystem {
    /**
     * Returns a command that gets the annotated form of the given revision
     * of the given file.
     */
    public String[] getAnnotateCommand(Revision revision, String filename);

    /**
     * Parses one of the lines returned on standard output by the command from
     * getAnnotateCommand, building an AnnotatedLine.
     */
    public AnnotatedLine parseAnnotatedLine(RevisionListModel revisions, String line);

    /**
     * Returns a command that gets a patch for the given file from the older
     * revision to the newer revision. You may be given Revision.LOCAL_REVISION
     * as newer revision. You may be given null for both revisions, which is
     * used by CheckInTool to ask for the differences between a locally-modified
     * file and the repository.
     */
    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename);

    /** Returns a command that gets the revision log for the given file. */
    public String[] getLogCommand(String filename);

    /**
     * Parses the lines returned on standard output by the command from
     * getLogCommand, building a RevisionListModel.
     */
    public RevisionListModel parseLog(List lines);

    /**
     * Tests whether a given file has been locally modified.
     */
    public boolean isLocallyModified(File repositoryRoot, String filename);

    /**
     * Tests whether this revision control system supports the notion of
     * change sets, where a number of files are grouped together as a single
     * change.
     */
    public boolean supportsChangeSets();

    /**
     * Opens some kind of viewer for the change set corresponding to the
     * given revision of the given file.
     */
    public void showChangeSet(File repositoryRoot, String filename, Revision revision);
    
    //
    // Methods relating to checking in.
    //
    
    /**
     * Returns status information for all the added, deleted, modified or
     * unmanaged files and directories in the repository.
     */
    public List getStatuses(File repositoryRoot);
    
    /**
     * Commits the files from the given list of filenames using the comment
     * supplied.
     */
    public void commit(File repositoryRoot, String comment, List filenames);
}