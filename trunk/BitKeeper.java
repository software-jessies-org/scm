import java.util.*;

public class BitKeeper implements RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        return new String[] {
            "bk", "annotate", "-r" + revision.number, filename
        };
    }

    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename) {
        ArrayList result = new ArrayList();
        result.add("bk");
        result.add("diffs");
        result.add("-u");
        result.add("-r" + olderRevision.number);
        if (newerRevision != Revision.LOCAL_REVISION) {
            result.add("-r" + newerRevision.number);
        }
        result.add(filename);
        return (String[]) result.toArray(new String[result.size()]);
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "bk", "prs", filename };
    }

    public AnnotationModel parseAnnotations(RevisionListModel revisions, List lines) {
        AnnotationModel result = new AnnotationModel();
        for (int i = 0; i < lines.size(); ++i) {
            result.add(AnnotatedLine.fromBitKeeperAnnotatedLine(revisions, (String) lines.get(i)));
        }
        return result;
    }

    public RevisionListModel parseLog(List linesList) {
        String[] lines = (String[]) linesList.toArray(new String[linesList.size()]);

        String separator = "------------------------------------------------";
        RevisionListModel result = new RevisionListModel();
        String description = null;
        String comment = "";
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].startsWith("D ")) {
                description = lines[i];
            } else if (lines[i].startsWith("C ")) {
                comment += lines[i].substring(2) + '\n';
            }
            if (lines[i].equals(separator)) {
                result.add(new Revision(description, comment));
                description = null;
                comment = "";
            }
        }
        return result;
    }

    public boolean isLocallyModified(String filename) {
        String[] command = new String[] {
            "bk", "sfiles", "-v", "-ct", "-g", filename
        };
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = ProcessUtilities.backQuote(command, lines, errors);
        for (int i = 0; i < lines.size(); ++i) {
            String line = (String) lines.get(i);
            if (line.indexOf("lc") == 0) {
                return true;
            }
        }
        return false;
    }
}
