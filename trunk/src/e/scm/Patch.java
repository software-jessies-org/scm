package e.scm;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import e.util.*;

public class Patch {
    private LineMapper lineMapper;
    
    public abstract class PatchLineParser {
        public abstract void parseHunkHeader(int fromBegin, int fromEnd, int toBegin, int toEnd);
        public abstract void parseFromLine(String sourceLine);
        public abstract void parseToLine(String sourceLine);
        public abstract void parseContextLine(String sourceLine);        
    }

    /** Matches the "@@ -111,41 +113,41 @@" lines at the start of a hunk. */
    private static final Pattern AT_AT_PATTERN =
        Pattern.compile("^@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@$");
    
    public void parsePatch(boolean isPatchReversed, List lines, PatchLineParser patchLineParser) {
        String fromPrefix = isPatchReversed ? "+" : "-";
        String toPrefix = isPatchReversed ? "-" : "+";
        for (int lineNumberWithinPatch = 0; lineNumberWithinPatch < lines.size(); ++lineNumberWithinPatch) {
            String patchLine = (String) lines.get(lineNumberWithinPatch);
            Matcher matcher = AT_AT_PATTERN.matcher(patchLine);
            if (matcher.matches()) {
                int fromBegin = Integer.parseInt(matcher.group(1));
                int fromEnd = Integer.parseInt(matcher.group(2));
                int toBegin = Integer.parseInt(matcher.group(3));
                int toEnd = Integer.parseInt(matcher.group(3));
                if (isPatchReversed) {
                    patchLineParser.parseHunkHeader(toBegin, toEnd, fromBegin, fromEnd);
                } else {
                    patchLineParser.parseHunkHeader(fromBegin, fromEnd, toBegin, toEnd);
                }
            } else {
                String sourceLine = patchLine.substring(1);
                if (patchLine.startsWith(fromPrefix)) {
                    patchLineParser.parseFromLine(sourceLine);
                } else if (patchLine.startsWith(toPrefix)) {
                    patchLineParser.parseToLine(sourceLine);
                } else if (patchLine.startsWith(" ")) {
                    patchLineParser.parseContextLine(sourceLine);
                } else if (patchLine.startsWith("=")) {
                    // Ignore lines like this for the minute:
                    // ===== makerules/vars-not-previously-included.make 1.33 vs 1.104 =====
                } else {
                    throw new RuntimeException("Malformed patch line \"" + patchLine + "\"");
                }
            }
        }
    }
        
    public class LineCounter extends PatchLineParser {
        private int fromLine = 0;
        private int toLine = 0;
        
        public LineCounter() {
            lineMapper = new LineMapper();
        }
        
        public void parseHunkHeader(int fromBegin, int fromEnd, int toBegin, int toEnd) {
            fromLine = fromBegin;
            toLine = toBegin;
        }
        
        public void parseFromLine(String sourceLine) {
            ++fromLine;
        }
        public void parseToLine(String sourceLine) {
            ++toLine;
        }
        public void parseContextLine(String sourceLine) {
            lineMapper.addMapping(fromLine, toLine);
            // Context lines count for both revisions.
            ++fromLine;
            ++toLine;
        }
    }

    public Patch(Component revisionWindow, RevisionControlSystem backEnd, String filePath, Revision fromRevision, Revision toRevision) {
        File directory = backEnd.getRoot();
        // Elliott reckons that the back-end insists on the arguments being olderRevision, newerRevision.
        // At the moment, we know that we're always called with fromRevision newer than toRevision.
        boolean isPatchReversed = true;
        String[] command = backEnd.getDifferencesCommand(toRevision, fromRevision, filePath);
        ArrayList lines = new ArrayList();
        ArrayList errors = new ArrayList();
        int status = ProcessUtilities.backQuote(directory, command, lines, errors);
        if (status != 0 || errors.size() > 0) {
            System.err.println("Differences command failed - TODO: provide feedback");
        }
        parsePatch(isPatchReversed, lines, new LineCounter());
    }

    public int translateLineNumberInFromRevision(int fromLineNumber) {
        return lineMapper.translate(fromLineNumber);
    }
}
