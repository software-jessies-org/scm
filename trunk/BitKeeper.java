public class BitKeeper implements RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        return new String[] {
            "bk", "annotate", "-r" + revision.number, filename
        };
    }

    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename) {
        return new String[] {
            "bk", "diffs",
                "-u",
                "-r" + olderRevision.number,
                "-r" + newerRevision.number,
                filename
        };
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "bk", "prs", filename };
    }

    public AnnotationModel parseAnnotations(RevisionListModel revisions, String[] lines) {
        AnnotationModel result = new AnnotationModel();
        for (int i = 0; i < lines.length; ++i) {
            result.add(AnnotatedLine.fromBitKeeperAnnotatedLine(revisions, lines[i]));
        }
        return result;
    }

    public RevisionListModel parseLog(String[] lines) {
        String separator = "------------------------------------------------";
        RevisionListModel result = new RevisionListModel();
        String description = null;
        String comment = "";
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].startsWith("D ")) {
                description = lines[i];
            } else if (lines[i].startsWith("C ")) {
                comment += lines[i].substring(2);
            }
            if (lines[i].equals(separator)) {
                result.add(new Revision(description, comment));
                description = null;
                comment = "";
            }
        }
        return result;
    }
}
