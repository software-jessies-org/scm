import java.util.List;

public interface RevisionControlSystem {
    /**
     * Returns a command that gets the annotated form of the given revision
     * of the given file.
     */
    public String[] getAnnotateCommand(Revision revision, String filename);

    /**
     * Parses the lines returned on standard output by the command from
     * getAnnotateCommand, building an AnnotationModel.
     */
    public AnnotationModel parseAnnotations(RevisionListModel revisions, List lines);

    /**
     * Returns a command that gets a patch for the given file from the older
     * revision to the newer revision.
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
    public boolean isLocallyModified(String filename);
}
