import java.util.List;

public interface RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename);
    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename);
    public String[] getLogCommand(String filename);
    public AnnotationModel parseAnnotations(RevisionListModel revisions, List lines);
    public RevisionListModel parseLog(List lines);
}
