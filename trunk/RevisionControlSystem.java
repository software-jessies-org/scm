public interface RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename);
    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename);
    public String[] getLogCommand(String filename);
    public AnnotationModel parseAnnotations(RevisionListModel revisions, String[] lines);
    public RevisionListModel parseLog(String[] lines);
}
