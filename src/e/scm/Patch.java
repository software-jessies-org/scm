package e.scm;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import e.util.*;

public class Patch {
    private ArrayList<String> lines;
    private ArrayList<String> errors;
    private LineMapper lineMapper;
    
    public static final class HunkRange {
        /**
         * Matches the "@@ -111,41 +113,41 @@" lines at the start of a hunk.
         * We allow anything after the trailing "@@" because that gap is used for annotations, both by our annotate-patch.rb script and by "diff -p".
         * GNU Diff's "print_unidiff_number_range" has a special case when outputting a,b where if a and b are equal, only a is output.
         */
        private static final Pattern AT_AT_PATTERN = Pattern.compile("^@@ -(\\d+)(,\\d+)? \\+(\\d+)(,\\d+)? @@(.*)$");
        
        private boolean matches;
        
        // "from" is the first comma-separated pair. "to" is the second comma-separated pair.
        // "begin" is the first number in each pair. "end" is the second number in each pair.
        private int fromBegin;
        private int fromEnd;
        private int toBegin;
        private int toEnd;
        
        public HunkRange(String atAtLine) {
            Matcher matcher = AT_AT_PATTERN.matcher(atAtLine);
            matches = matcher.matches();
            if (matches) {
                fromBegin = parseInt(matcher.group(1), 0);
                fromEnd = parseInt(matcher.group(2), fromBegin);
                toBegin = parseInt(matcher.group(3), 0);
                toEnd = parseInt(matcher.group(4), toBegin);
            }
        }
        
        private int parseInt(String value, int fallback) {
            if (value == null) {
                return fallback;
            }
            if (value.startsWith(",")) {
                value = value.substring(1);
            }
            int result = Integer.parseInt(value);
            return result;
        }
        
        public boolean matches() {
            return matches;
        }
        
        public int fromBegin() {
            return fromBegin;
        }
        
        public int toBegin() {
            return toBegin;
        }
    }
    
    private interface PatchLineParser {
        public void parseHunkHeader(int fromBegin, int toBegin);
        public void parseFromLine(String sourceLine);
        public void parseToLine(String sourceLine);
        public void parseContextLine(String sourceLine);
    }
    
    private void parsePatch(boolean isPatchReversed, PatchLineParser patchLineParser) {
        String fromPrefix = isPatchReversed ? "+" : "-";
        String toPrefix = isPatchReversed ? "-" : "+";
        for (int lineNumberWithinPatch = 0; lineNumberWithinPatch < lines.size(); ++lineNumberWithinPatch) {
            String patchLine = lines.get(lineNumberWithinPatch);
            if (patchLine.startsWith("@@")) {
                HunkRange hunkRange = new HunkRange(patchLine);
                if (isPatchReversed) {
                    patchLineParser.parseHunkHeader(hunkRange.toBegin(), hunkRange.fromBegin());
                } else {
                    patchLineParser.parseHunkHeader(hunkRange.fromBegin(), hunkRange.toBegin());
                }
            } else if (patchLine.length() > 0) {
                String sourceLine = patchLine.substring(1);
                if (patchLine.startsWith(fromPrefix)) {
                    patchLineParser.parseFromLine(sourceLine);
                } else if (patchLine.startsWith(toPrefix)) {
                    patchLineParser.parseToLine(sourceLine);
                } else if (patchLine.startsWith(" ")) {
                    patchLineParser.parseContextLine(sourceLine);
                } else if (patchLine.startsWith("=====") || patchLine.startsWith("Index: ") || patchLine.startsWith("RCS file: ") || patchLine.startsWith("retrieving revision ") || patchLine.startsWith("diff ") || patchLine.startsWith("new mode ") || patchLine.startsWith("old mode ") || patchLine.startsWith("index ") || patchLine.startsWith("\\ No newline at end of file")) {
                    // Ignore lines like this for the minute:
                    // bk:
                    // ===== makerules/vars-not-previously-included.make 1.33 vs 1.104 =====
                    // Index: src/e/scm/RevisionWindow.java
                    // svn:
                    // ===================================================================
                    // cvs:
                    // RCS file: /home/repositories/cvsroot/edit/src/e/edit/InsertNewlineAction.java,v
                    // retrieving revision 1.7
                    // diff -u -r1.7 -r1.9
                } else {
              //      throw new RuntimeException("Malformed patch line \"" + patchLine + "\"");
                }
            }
        }
    }
    
    public Patch(RevisionControlSystem backEnd, String filePath, Revision olderRevision, Revision newerRevision, boolean isPatchReversed, boolean ignoreWhiteSpace) {
        Path directory = backEnd.getRoot();
        String[] command = backEnd.getDifferencesCommand(olderRevision, newerRevision, filePath, ignoreWhiteSpace);
        this.lines = new ArrayList<String>();
        this.errors = new ArrayList<String>();
        ProcessUtilities.backQuote(directory, command, lines, errors);
        // CVS returns the number of differences as the status or some such idiocy.
        if (errors.size() > 0) {
            lines.addAll(errors);
        }
        initLineMapper(isPatchReversed);
    }
    
    private void initLineMapper(boolean isPatchReversed) {
        lineMapper = new LineMapper();
        parsePatch(isPatchReversed, new PatchLineParser() {
            private int fromLine = 0;
            private int toLine = 0;
            
            public void parseHunkHeader(int fromBegin, int toBegin) {
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
        });
    }
    
    public int translateLineNumberInFromRevision(int fromLineNumber) {
        return lineMapper.translate(fromLineNumber);
    }
    
    public ArrayList<String> getPatchLines() {
        ArrayList<String> result = new ArrayList<>();
        result.addAll(lines);
        return result;
    }
}
