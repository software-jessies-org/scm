import java.util.*;

public class Cvs implements RevisionControlSystem {
    public String[] getAnnotateCommand(Revision revision, String filename) {
        return new String[] {
            "cvs", "annotate", "-r", revision.number, filename
        };
    }

    public String[] getDifferencesCommand(Revision olderRevision, Revision newerRevision, String filename) {
        ArrayList result = new ArrayList();
        result.add("cvs");
        result.add("diff");
        result.add("-u");
        result.add("-kk");
        result.add("-r");
        result.add(olderRevision.number);
        if (newerRevision != Revision.LOCAL_REVISION) {
            result.add("-r");
            result.add(newerRevision.number);
        }
        result.add(filename);
        return (String[]) result.toArray(new String[result.size()]);
    }

    public String[] getLogCommand(String filename) {
        return new String[] { "cvs", "log", filename };
    }

    public AnnotationModel parseAnnotations(RevisionListModel revisions, List lines) {
        AnnotationModel result = new AnnotationModel();
        for (int i = 0; i < lines.size(); ++i) {
            result.add(AnnotatedLine.fromCvsAnnotatedLine(revisions, (String) lines.get(i)));
        }
        return result;
    }

    public RevisionListModel parseLog(List linesList) {
        String[] lines = (String[]) linesList.toArray(new String[linesList.size()]);

        String separator = "----------------------------";
        String endMarker = "=============================================================================";
        int i = 0;
        for (; i < lines.length && lines[i].equals(separator) == false; ++i) {
            // Skip header.
        }
        
        RevisionListModel result = new RevisionListModel();
        while (i < lines.length && lines[i].equals(endMarker) == false) {
            if (lines[i].equals(separator)) {
                i++;
                continue;
            }
            String number = lines[i++];
            number = number.substring("revision ".length());
            String info = lines[i++];
            StringBuffer comment = new StringBuffer();
            while (i < lines.length && lines[i].equals(endMarker) == false && lines[i].equals(separator) == false) {
                comment.append(lines[i++]);
                comment.append("\n");
            }
            result.add(new Revision(number, info, comment.toString()));
        }
        return result;
    }
}
